# In-App Review for Android and iOS — Design

**Date:** 2026-04-17
**Status:** Approved for implementation
**Scope:** Native in-app review prompt on Android (Google Play In-App Review API) and iOS (StoreKit `SKStoreReviewController`), wired into the existing Kotlin Multiplatform Compose app.

## Goal

Ask engaged users to rate the app from inside the app, without navigating them to the store, and without asking too early or too often.

## Non-goals

- No in-app rating UI of our own — we use only the native OS prompts.
- No review prompts on desktop (JVM), web (JS/wasmJs). Feature only ships on Android and iOS.
- No feedback/contact form, no "rate later" reminder, no "don't ask again" button — the OS-level prompt handles the user experience.
- No analytics on whether the user actually left a review — neither Apple nor Google expose that.

## Trigger model

A single natural moment: **when the user returns to the library after a qualifying reading session.**

### Eligibility rules (all must hold)

1. `sessionCount ≥ 3` — user has completed at least 3 qualifying reading sessions.
2. `distinctReadingDays ≥ 2` — those sessions happened across at least 2 different calendar days (local device time, based on epoch-day bucket).
3. Cooldown: either `lastPromptMs == 0L` (never prompted) OR `now - lastPromptMs ≥ 90 days`.

### Qualifying session

A session starts when `PdfReaderScreen` opens and ends when the user returns to the library (either via back button, close button, or end-of-document). A session qualifies if its duration is ≥ 30 seconds. Shorter sessions are ignored (protects against accidental taps).

### Why these numbers

- 3 sessions / 2 days: enough to indicate genuine use without being impatient.
- 30s minimum: filters misclicks; the shortest real reading interaction.
- 90-day cooldown: aligns with Apple's own 3-prompts-per-365-days quota, and avoids annoying users who dismissed the prompt.

After calling the native review API (whether or not the OS actually shows a dialog), we set `lastPromptMs = now`. The OS may throttle and silently skip; we treat that as "prompted" anyway.

## Architecture

Follows the existing `expect/actual` pattern used by `ReadingPositionStore`, `AnnotationStore`, etc. All decision logic lives in common code; only the native API calls and persistence backends are platform-specific.

### New types in `commonMain/Platform.kt`

```kotlin
interface ReviewStateStore {
    fun getSessionCount(): Int
    fun setSessionCount(value: Int)
    fun getDistinctDays(): List<Long>      // epoch-day values, max 2
    fun setDistinctDays(days: List<Long>)
    fun getLastPromptMs(): Long
    fun setLastPromptMs(value: Long)
}
expect fun getReviewStateStore(): ReviewStateStore

interface ReviewPromptManager {
    // Idempotent: checks eligibility, fires native prompt if eligible, records timestamp.
    suspend fun requestReviewIfAppropriate()
}
expect fun getReviewPromptManager(): ReviewPromptManager
```

### New common class: `ReviewEligibilityTracker`

Single-responsibility helper used by both platforms. Pure Kotlin — no platform types.

```kotlin
class ReviewEligibilityTracker(
    private val store: ReviewStateStore,
    private val clock: () -> Long = { currentTimeMillis() },
) {
    // Called by App.kt when a qualifying session completes.
    fun recordSession(sessionStartMs: Long, sessionEndMs: Long)

    // Called by the platform ReviewPromptManager before invoking the native API.
    fun isEligible(): Boolean

    // Called by the platform ReviewPromptManager after invoking the native API.
    fun markPromptRequested()

    companion object {
        const val MIN_SESSION_DURATION_MS = 30_000L
        const val MIN_SESSIONS = 3
        const val MIN_DISTINCT_DAYS = 2
        const val COOLDOWN_MS = 90L * 24 * 60 * 60 * 1000  // 90 days
        const val MS_PER_DAY = 24L * 60 * 60 * 1000
    }
}
```

`recordSession` logic:
1. If `sessionEndMs - sessionStartMs < MIN_SESSION_DURATION_MS`, return.
2. Increment `sessionCount` (capped at a sane max like 1000).
3. Compute `epochDay = sessionEndMs / MS_PER_DAY`. If it is not already in `distinctDays` and `distinctDays.size < MIN_DISTINCT_DAYS`, append and save.

`isEligible` logic:
- All three rules from above.

`markPromptRequested`: `store.setLastPromptMs(clock())`.

### Trigger wiring in `App.kt`

`MainContent` already owns the `currentFileUri` state. We add:

1. In `PdfReaderScreen`: record `val sessionStartMs = remember { currentTimeMillis() }` on first composition. Pass it to `onClose` when closing.
2. Change `onClose: () -> Unit` to `onClose: (sessionStartMs: Long) -> Unit` OR keep it simple: make `PdfReaderScreen` itself call `tracker.recordSession(sessionStartMs, currentTimeMillis())` via a `DisposableEffect(uri) { onDispose { ... } }` hook (preferred — no API change).
3. In `MainContent`, watch `currentFileUri` transitions from non-null to null. When it flips to null, launch `coroutineScope.launch { reviewPromptManager.requestReviewIfAppropriate() }`. The manager itself is a no-op if not eligible, so this is safe to call every time.

### Android actual (`Platform.android.kt`)

- New dependency in `composeApp/build.gradle.kts` under `androidMain.dependencies`: `com.google.android.play:review-ktx:2.0.2`.
- New version catalog entry in `gradle/libs.versions.toml`:
  - `[versions]` → `google-play-review = "2.0.2"`
  - `[libraries]` → `google-play-review = { module = "com.google.android.play:review-ktx", version.ref = "google-play-review" }`
- New top-level `var currentActivity: ComponentActivity? = null` in `Platform.android.kt`, set from `MainActivity.onCreate` and cleared in `onDestroy` (mirrors `appContext` pattern).
- `AndroidReviewStateStore(context)` — backed by `SharedPreferences("review_state", MODE_PRIVATE)`. Distinct days are stored as a comma-separated string under key `distinct_days`.
- `AndroidReviewPromptManager`:
  ```kotlin
  override suspend fun requestReviewIfAppropriate() {
      val tracker = ReviewEligibilityTracker(store)
      if (!tracker.isEligible()) return
      val activity = currentActivity ?: return
      val reviewManager = ReviewManagerFactory.create(activity)
      try {
          val reviewInfo = reviewManager.requestReview()  // review-ktx suspend extension
          reviewManager.launchReview(activity, reviewInfo)
      } catch (_: Exception) {
          // Play Services missing / Play Core unavailable — skip silently.
      } finally {
          tracker.markPromptRequested()
      }
  }
  ```

### iOS actual (`Platform.ios.kt`)

- `IOSReviewStateStore` — backed by `NSUserDefaults`. Distinct days stored as comma-separated string under key `review_distinct_days`.
- `IOSReviewPromptManager`:
  ```kotlin
  override suspend fun requestReviewIfAppropriate() {
      val tracker = ReviewEligibilityTracker(store)
      if (!tracker.isEligible()) return
      val scene = UIApplication.sharedApplication.keyWindow?.windowScene
      if (scene != null) {
          SKStoreReviewController.requestReviewInScene(scene)
      }
      tracker.markPromptRequested()
  }
  ```
- Imports: `platform.StoreKit.SKStoreReviewController`. Kotlin/Native's Apple framework cinterop ships StoreKit — no Podfile or Xcode changes needed beyond what's already in place.

### JVM / JS / wasmJs actuals

No-op `ReviewStateStore` (all getters return defaults, setters do nothing) and no-op `ReviewPromptManager` (`requestReviewIfAppropriate` returns immediately). The feature is mobile-only.

## Persistence schema

### Android — `SharedPreferences("review_state")`

| Key | Type | Meaning |
|---|---|---|
| `session_count` | Int | Total qualifying sessions (0 default). |
| `distinct_days` | String | Comma-separated epoch-day values, max 2 entries. |
| `last_prompt_ms` | Long | Timestamp of last `requestReview` call, 0 default. |

### iOS — `NSUserDefaults`

Same three keys with the same semantics, prefixed `review_` to avoid collision: `review_session_count`, `review_distinct_days`, `review_last_prompt_ms`.

## Testing

### Unit tests (`commonTest/ReviewEligibilityTrackerTest.kt`)

Use a fake `ReviewStateStore` (in-memory) and a controllable clock function.

Required cases:
- Session under 30s is not counted.
- Session ≥ 30s increments count and records the epoch day.
- Recording a session on the same day does not add a new distinct-day entry.
- `isEligible()` returns false when `sessionCount < 3`.
- `isEligible()` returns false when `distinctDays.size < 2`.
- `isEligible()` returns false when `lastPromptMs` is within 90 days.
- `isEligible()` returns true when all three conditions are met and `lastPromptMs == 0L`.
- `isEligible()` returns true when `lastPromptMs` is > 90 days ago.
- `markPromptRequested()` sets `lastPromptMs` to clock value.

### Manual verification

- **Android:** Build a debug build, wipe `review_state` SharedPreferences, force eligibility by injecting values, return to library. Play Core shows a prompt if the build is signed and installed from a `.aab` uploaded to an internal testing track on Play Console. (Debug sideload builds will silently no-op — that's expected.)
- **iOS:** StoreKit's `requestReviewInScene` is unreliable in the Xcode simulator and does not show in dev builds on physical device either; it only reliably shows in TestFlight and App Store builds. For dev, verify the call path is reached (log) and that `lastPromptMs` is updated.

## Files touched

**Created:**
- `composeApp/src/commonTest/kotlin/com/vantechinformatics/autoscrollingreader/ReviewEligibilityTrackerTest.kt`

**Modified:**
- `gradle/libs.versions.toml` — add `google-play-review` version + library entry.
- `composeApp/build.gradle.kts` — add `libs.google.play.review` to `androidMain.dependencies`.
- `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.kt` — add interfaces, `expect` functions, and `ReviewEligibilityTracker` class.
- `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/App.kt` — session-start timestamp, `DisposableEffect` calling `recordSession`, post-close call to `requestReviewIfAppropriate`.
- `composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.android.kt` — `currentActivity` holder, `AndroidReviewStateStore`, `AndroidReviewPromptManager`.
- `composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/MainActivity.kt` — set/clear `currentActivity` in `onCreate`/`onDestroy`.
- `composeApp/src/iosMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.ios.kt` — `IOSReviewStateStore`, `IOSReviewPromptManager`.
- `composeApp/src/jvmMain/.../Platform.jvm.kt` — no-op actuals.
- `composeApp/src/jsMain/.../Platform.js.kt` — no-op actuals.
- `composeApp/src/wasmJsMain/.../Platform.wasmJs.kt` — no-op actuals.

## Risks and open questions

- **Play Core availability**: If the device lacks Google Play Services, `requestReview()` throws. We catch and move on. No user-visible impact.
- **StoreKit scene retrieval**: `keyWindow` is deprecated on iOS 13+. We use it because the codebase already uses it elsewhere; `windowScene` access from `keyWindow` still works on iOS 14+. If this becomes flaky, fall back to iterating `UIApplication.sharedApplication.connectedScenes`.
- **Clock changes**: A user manipulating device time could hit the eligibility criteria faster. Acceptable; this is not a security boundary.
