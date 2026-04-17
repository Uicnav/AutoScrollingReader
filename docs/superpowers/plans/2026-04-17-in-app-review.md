# In-App Review Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add a native in-app review prompt (Google Play In-App Review on Android, `SKStoreReviewController` on iOS) that fires when a user returns to the library after their third qualifying reading session across at least two calendar days, with a 90-day cooldown between prompts.

**Architecture:** Follows the existing `expect/actual` pattern. All eligibility logic lives in `commonMain` as a pure-Kotlin `ReviewEligibilityTracker`, persisting via a new `ReviewStateStore` interface. Android and iOS provide actual platform-specific stores and a `ReviewPromptManager` that calls the native API. JVM / JS / wasmJs ship no-op implementations.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, Google Play In-App Review API (`com.google.android.play:review-ktx:2.0.2`), StoreKit (`SKStoreReviewController`), `SharedPreferences` (Android), `NSUserDefaults` (iOS).

**Reference spec:** `docs/superpowers/specs/2026-04-17-in-app-review-design.md`

---

## File Structure

**Created:**
- `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/ReviewEligibilityTracker.kt` — pure-Kotlin tracker with three public methods (`recordSession`, `isEligible`, `markPromptRequested`).
- `composeApp/src/commonTest/kotlin/com/vantechinformatics/autoscrollingreader/ReviewEligibilityTrackerTest.kt` — unit tests using an in-memory fake store.

**Modified:**
- `gradle/libs.versions.toml` — add version + library entry for Google Play review-ktx.
- `composeApp/build.gradle.kts` — add the library to `androidMain.dependencies`.
- `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.kt` — add `ReviewStateStore` and `ReviewPromptManager` interfaces, plus their `expect fun` factories.
- `composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.android.kt` — add `currentActivity` holder, `AndroidReviewStateStore`, `AndroidReviewPromptManager` and their actuals.
- `composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/MainActivity.kt` — set/clear `currentActivity` in `onCreate` / `onDestroy`.
- `composeApp/src/iosMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.ios.kt` — add `IOSReviewStateStore`, `IOSReviewPromptManager` and their actuals.
- `composeApp/src/jvmMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.jvm.kt` — add no-op actuals.
- `composeApp/src/jsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.js.kt` — add no-op actuals.
- `composeApp/src/wasmJsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.wasmJs.kt` — add no-op actuals.
- `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/App.kt` — record session duration in `PdfReaderScreen`; trigger the prompt when `currentFileUri` transitions from non-null to null in `MainContent`.

---

## Task 1: Add the common `ReviewStateStore` and `ReviewPromptManager` interfaces

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.kt` (append at the bottom)

- [ ] **Step 1: Add the interfaces and expect factories**

Append to `Platform.kt`:

```kotlin
// --- IN-APP REVIEW ---

interface ReviewStateStore {
    fun getSessionCount(): Int
    fun setSessionCount(value: Int)
    fun getDistinctDays(): List<Long>
    fun setDistinctDays(days: List<Long>)
    fun getLastPromptMs(): Long
    fun setLastPromptMs(value: Long)
}

expect fun getReviewStateStore(): ReviewStateStore

interface ReviewPromptManager {
    suspend fun requestReviewIfAppropriate()
}

expect fun getReviewPromptManager(): ReviewPromptManager
```

- [ ] **Step 2: Compile the common module (will fail because actuals are missing)**

Run: `./gradlew :composeApp:compileKotlinMetadata`
Expected: FAIL with `expected declaration has no actual declaration in module` for `getReviewStateStore` and `getReviewPromptManager`. This is the expected intermediate state; we will add actuals in later tasks.

(Do not commit yet — the project is in a broken state until all platform actuals are added. Tasks 2 through 7 fix that before any commit.)

---

## Task 2: Add no-op actuals for JVM, JS, wasmJs

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.jvm.kt` (append)
- Modify: `composeApp/src/jsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.js.kt` (append)
- Modify: `composeApp/src/wasmJsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.wasmJs.kt` (append)

- [ ] **Step 1: Append to `Platform.jvm.kt`**

```kotlin
// --- IN-APP REVIEW (no-op on desktop) ---

class JvmReviewStateStore : ReviewStateStore {
    override fun getSessionCount(): Int = 0
    override fun setSessionCount(value: Int) {}
    override fun getDistinctDays(): List<Long> = emptyList()
    override fun setDistinctDays(days: List<Long>) {}
    override fun getLastPromptMs(): Long = 0L
    override fun setLastPromptMs(value: Long) {}
}

actual fun getReviewStateStore(): ReviewStateStore = JvmReviewStateStore()

class JvmReviewPromptManager : ReviewPromptManager {
    override suspend fun requestReviewIfAppropriate() {}
}

actual fun getReviewPromptManager(): ReviewPromptManager = JvmReviewPromptManager()
```

- [ ] **Step 2: Append to `Platform.js.kt`**

```kotlin
// --- IN-APP REVIEW (no-op on web) ---

class JsReviewStateStore : ReviewStateStore {
    override fun getSessionCount(): Int = 0
    override fun setSessionCount(value: Int) {}
    override fun getDistinctDays(): List<Long> = emptyList()
    override fun setDistinctDays(days: List<Long>) {}
    override fun getLastPromptMs(): Long = 0L
    override fun setLastPromptMs(value: Long) {}
}

actual fun getReviewStateStore(): ReviewStateStore = JsReviewStateStore()

class JsReviewPromptManager : ReviewPromptManager {
    override suspend fun requestReviewIfAppropriate() {}
}

actual fun getReviewPromptManager(): ReviewPromptManager = JsReviewPromptManager()
```

- [ ] **Step 3: Append to `Platform.wasmJs.kt`**

```kotlin
// --- IN-APP REVIEW (no-op on web) ---

class WasmReviewStateStore : ReviewStateStore {
    override fun getSessionCount(): Int = 0
    override fun setSessionCount(value: Int) {}
    override fun getDistinctDays(): List<Long> = emptyList()
    override fun setDistinctDays(days: List<Long>) {}
    override fun getLastPromptMs(): Long = 0L
    override fun setLastPromptMs(value: Long) {}
}

actual fun getReviewStateStore(): ReviewStateStore = WasmReviewStateStore()

class WasmReviewPromptManager : ReviewPromptManager {
    override suspend fun requestReviewIfAppropriate() {}
}

actual fun getReviewPromptManager(): ReviewPromptManager = WasmReviewPromptManager()
```

- [ ] **Step 4: Verify JVM compiles**

Run: `./gradlew :composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL.

(Do not commit yet.)

---

## Task 3: Write the failing `ReviewEligibilityTracker` test suite

We write the full test suite first, then implement the tracker to make it pass (TDD).

**Files:**
- Create: `composeApp/src/commonTest/kotlin/com/vantechinformatics/autoscrollingreader/ReviewEligibilityTrackerTest.kt`

- [ ] **Step 1: Create the test file with an in-memory fake store and nine test cases**

```kotlin
package com.vantechinformatics.autoscrollingreader

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FakeReviewStateStore : ReviewStateStore {
    var sessionCount: Int = 0
    var days: List<Long> = emptyList()
    var lastPromptMs: Long = 0L

    override fun getSessionCount(): Int = sessionCount
    override fun setSessionCount(value: Int) { sessionCount = value }
    override fun getDistinctDays(): List<Long> = days
    override fun setDistinctDays(days: List<Long>) { this.days = days }
    override fun getLastPromptMs(): Long = lastPromptMs
    override fun setLastPromptMs(value: Long) { lastPromptMs = value }
}

class ReviewEligibilityTrackerTest {

    private val msPerDay = ReviewEligibilityTracker.MS_PER_DAY

    @Test
    fun shortSessionIsIgnored() {
        val store = FakeReviewStateStore()
        val tracker = ReviewEligibilityTracker(store) { 0L }
        val start = 10L * msPerDay
        tracker.recordSession(start, start + 20_000L) // 20 seconds
        assertEquals(0, store.sessionCount)
        assertEquals(emptyList(), store.days)
    }

    @Test
    fun qualifyingSessionIncrementsCountAndRecordsDay() {
        val store = FakeReviewStateStore()
        val tracker = ReviewEligibilityTracker(store) { 0L }
        val start = 10L * msPerDay
        tracker.recordSession(start, start + 30_000L)
        assertEquals(1, store.sessionCount)
        assertEquals(listOf(10L), store.days)
    }

    @Test
    fun sameDaySessionDoesNotAddNewDay() {
        val store = FakeReviewStateStore().apply {
            sessionCount = 1
            days = listOf(10L)
        }
        val tracker = ReviewEligibilityTracker(store) { 0L }
        val start = 10L * msPerDay + 3_600_000L // 1 hour later same day
        tracker.recordSession(start, start + 40_000L)
        assertEquals(2, store.sessionCount)
        assertEquals(listOf(10L), store.days)
    }

    @Test
    fun distinctDaysCapsAtTwo() {
        val store = FakeReviewStateStore().apply {
            sessionCount = 2
            days = listOf(10L, 11L)
        }
        val tracker = ReviewEligibilityTracker(store) { 0L }
        val start = 12L * msPerDay
        tracker.recordSession(start, start + 40_000L)
        assertEquals(3, store.sessionCount)
        assertEquals(listOf(10L, 11L), store.days) // not appended
    }

    @Test
    fun notEligibleWhenSessionCountUnderThree() {
        val store = FakeReviewStateStore().apply {
            sessionCount = 2
            days = listOf(10L, 11L)
        }
        val tracker = ReviewEligibilityTracker(store) { 100L * msPerDay }
        assertFalse(tracker.isEligible())
    }

    @Test
    fun notEligibleWhenDistinctDaysUnderTwo() {
        val store = FakeReviewStateStore().apply {
            sessionCount = 5
            days = listOf(10L)
        }
        val tracker = ReviewEligibilityTracker(store) { 100L * msPerDay }
        assertFalse(tracker.isEligible())
    }

    @Test
    fun notEligibleWhenWithinCooldown() {
        val now = 100L * msPerDay
        val store = FakeReviewStateStore().apply {
            sessionCount = 5
            days = listOf(10L, 11L)
            lastPromptMs = now - 30L * msPerDay // 30 days ago
        }
        val tracker = ReviewEligibilityTracker(store) { now }
        assertFalse(tracker.isEligible())
    }

    @Test
    fun eligibleWhenAllConditionsMetAndNeverPrompted() {
        val store = FakeReviewStateStore().apply {
            sessionCount = 3
            days = listOf(10L, 11L)
            lastPromptMs = 0L
        }
        val tracker = ReviewEligibilityTracker(store) { 100L * msPerDay }
        assertTrue(tracker.isEligible())
    }

    @Test
    fun eligibleWhenCooldownExpired() {
        val now = 200L * msPerDay
        val store = FakeReviewStateStore().apply {
            sessionCount = 3
            days = listOf(10L, 11L)
            lastPromptMs = now - 91L * msPerDay
        }
        val tracker = ReviewEligibilityTracker(store) { now }
        assertTrue(tracker.isEligible())
    }

    @Test
    fun markPromptRequestedStoresClockValue() {
        val store = FakeReviewStateStore()
        val now = 500L * msPerDay
        val tracker = ReviewEligibilityTracker(store) { now }
        tracker.markPromptRequested()
        assertEquals(now, store.lastPromptMs)
    }
}
```

- [ ] **Step 2: Run the test and confirm it fails to compile**

Run: `./gradlew :composeApp:compileTestKotlinJvm`
Expected: FAIL — `ReviewEligibilityTracker` unresolved reference.

(Do not commit yet.)

---

## Task 4: Implement `ReviewEligibilityTracker` to make tests pass

**Files:**
- Create: `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/ReviewEligibilityTracker.kt`

- [ ] **Step 1: Create the tracker**

```kotlin
package com.vantechinformatics.autoscrollingreader

class ReviewEligibilityTracker(
    private val store: ReviewStateStore,
    private val clock: () -> Long = { currentTimeMillis() },
) {

    fun recordSession(sessionStartMs: Long, sessionEndMs: Long) {
        val duration = sessionEndMs - sessionStartMs
        if (duration < MIN_SESSION_DURATION_MS) return

        val newCount = (store.getSessionCount() + 1).coerceAtMost(MAX_SESSION_COUNT)
        store.setSessionCount(newCount)

        val epochDay = sessionEndMs / MS_PER_DAY
        val days = store.getDistinctDays()
        if (epochDay !in days && days.size < MIN_DISTINCT_DAYS) {
            store.setDistinctDays(days + epochDay)
        }
    }

    fun isEligible(): Boolean {
        if (store.getSessionCount() < MIN_SESSIONS) return false
        if (store.getDistinctDays().size < MIN_DISTINCT_DAYS) return false
        val last = store.getLastPromptMs()
        if (last != 0L && clock() - last < COOLDOWN_MS) return false
        return true
    }

    fun markPromptRequested() {
        store.setLastPromptMs(clock())
    }

    companion object {
        const val MIN_SESSION_DURATION_MS: Long = 30_000L
        const val MIN_SESSIONS: Int = 3
        const val MIN_DISTINCT_DAYS: Int = 2
        const val MS_PER_DAY: Long = 24L * 60 * 60 * 1000
        const val COOLDOWN_MS: Long = 90L * MS_PER_DAY
        const val MAX_SESSION_COUNT: Int = 1000
    }
}
```

- [ ] **Step 2: Run the tracker tests on JVM**

Run: `./gradlew :composeApp:jvmTest --tests "com.vantechinformatics.autoscrollingreader.ReviewEligibilityTrackerTest"`
Expected: BUILD SUCCESSFUL, 10 tests completed, 0 failed.

(Do not commit yet — we still need Android and iOS actuals before the whole build passes.)

---

## Task 5: Add Google Play review-ktx dependency

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`

- [ ] **Step 1: Add version and library entry to `libs.versions.toml`**

Under `[versions]`, append:

```toml
google-play-review = "2.0.2"
```

Under `[libraries]`, append:

```toml
google-play-review = { module = "com.google.android.play:review-ktx", version.ref = "google-play-review" }
```

- [ ] **Step 2: Add the dependency to `androidMain.dependencies` in `composeApp/build.gradle.kts`**

In the `androidMain.dependencies { ... }` block (currently containing `pdfbox-android`), add:

```kotlin
implementation(libs.google.play.review)
```

So the final block reads:

```kotlin
androidMain.dependencies {
    implementation(libs.compose.uiToolingPreview)

    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.pdfbox.android)
    implementation(libs.google.play.review)
}
```

- [ ] **Step 3: Sync dependencies**

Run: `./gradlew :composeApp:dependencies --configuration releaseRuntimeClasspath | grep review`
Expected: output contains `com.google.android.play:review-ktx:2.0.2` (or a resolved version).

(Do not commit yet.)

---

## Task 6: Implement Android actuals

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.android.kt` (append at bottom)
- Modify: `composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/MainActivity.kt`

- [ ] **Step 1: Add a `currentActivity` holder and the Android actuals in `Platform.android.kt`**

Add these imports at the top of the file (near the other imports):

```kotlin
import androidx.activity.ComponentActivity
import com.google.android.play.core.ktx.launchReview
import com.google.android.play.core.ktx.requestReview
import com.google.android.play.core.review.ReviewManagerFactory
```

Append to the bottom of `Platform.android.kt`:

```kotlin
// --- IN-APP REVIEW ---

var currentActivity: ComponentActivity? = null

class AndroidReviewStateStore(private val context: Context) : ReviewStateStore {
    private val prefs = context.getSharedPreferences("review_state", Context.MODE_PRIVATE)

    override fun getSessionCount(): Int = prefs.getInt("session_count", 0)
    override fun setSessionCount(value: Int) { prefs.edit().putInt("session_count", value).apply() }

    override fun getDistinctDays(): List<Long> {
        val csv = prefs.getString("distinct_days", "") ?: ""
        if (csv.isEmpty()) return emptyList()
        return csv.split(",").mapNotNull { it.toLongOrNull() }
    }

    override fun setDistinctDays(days: List<Long>) {
        prefs.edit().putString("distinct_days", days.joinToString(",")).apply()
    }

    override fun getLastPromptMs(): Long = prefs.getLong("last_prompt_ms", 0L)
    override fun setLastPromptMs(value: Long) { prefs.edit().putLong("last_prompt_ms", value).apply() }
}

actual fun getReviewStateStore(): ReviewStateStore = AndroidReviewStateStore(appContext)

class AndroidReviewPromptManager(private val context: Context) : ReviewPromptManager {
    override suspend fun requestReviewIfAppropriate() {
        val tracker = ReviewEligibilityTracker(AndroidReviewStateStore(context))
        if (!tracker.isEligible()) return
        val activity = currentActivity ?: return
        try {
            val reviewManager = ReviewManagerFactory.create(activity)
            val reviewInfo = reviewManager.requestReview()
            reviewManager.launchReview(activity, reviewInfo)
        } catch (_: Exception) {
            // Play Services missing / Play Core unavailable — skip silently.
        } finally {
            tracker.markPromptRequested()
        }
    }
}

actual fun getReviewPromptManager(): ReviewPromptManager = AndroidReviewPromptManager(appContext)
```

Note: `requestReview()` and `launchReview(activity, reviewInfo)` are suspend extension functions on `ReviewManager` provided by the `review-ktx` artifact. The imports above are required for them to resolve.

- [ ] **Step 2: Wire `currentActivity` in `MainActivity.kt`**

Replace the body of `onCreate` and add `onDestroy`. Final file:

```kotlin
package com.vantechinformatics.autoscrollingreader

import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContext = applicationContext
        currentActivity = this

        val externalUri: String? = if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.toString()
        } else {
            null
        }
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        setContent {
            App(externalData = externalUri)
        }
    }

    override fun onDestroy() {
        if (currentActivity === this) {
            currentActivity = null
        }
        super.onDestroy()
    }
}

@Preview
@Composable
fun AppAndroidPreview() {
    App()
}
```

- [ ] **Step 3: Compile Android**

Run: `./gradlew :composeApp:compileDebugKotlinAndroid`
Expected: BUILD SUCCESSFUL.

(Do not commit yet.)

---

## Task 7: Implement iOS actuals

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.ios.kt` (append at bottom)

- [ ] **Step 1: Append iOS actuals**

```kotlin
// --- IN-APP REVIEW ---

class IOSReviewStateStore : ReviewStateStore {
    private val defaults = platform.Foundation.NSUserDefaults.standardUserDefaults

    override fun getSessionCount(): Int =
        defaults.integerForKey("review_session_count").toInt()

    override fun setSessionCount(value: Int) {
        defaults.setInteger(value.toLong(), forKey = "review_session_count")
    }

    override fun getDistinctDays(): List<Long> {
        val csv = defaults.stringForKey("review_distinct_days") ?: return emptyList()
        if (csv.isEmpty()) return emptyList()
        return csv.split(",").mapNotNull { it.toLongOrNull() }
    }

    override fun setDistinctDays(days: List<Long>) {
        defaults.setObject(days.joinToString(","), forKey = "review_distinct_days")
    }

    override fun getLastPromptMs(): Long =
        defaults.doubleForKey("review_last_prompt_ms").toLong()

    override fun setLastPromptMs(value: Long) {
        defaults.setDouble(value.toDouble(), forKey = "review_last_prompt_ms")
    }
}

actual fun getReviewStateStore(): ReviewStateStore = IOSReviewStateStore()

class IOSReviewPromptManager : ReviewPromptManager {
    override suspend fun requestReviewIfAppropriate() {
        val tracker = ReviewEligibilityTracker(IOSReviewStateStore())
        if (!tracker.isEligible()) return
        val scene = platform.UIKit.UIApplication.sharedApplication.keyWindow?.windowScene
        if (scene != null) {
            platform.StoreKit.SKStoreReviewController.requestReviewInScene(scene)
        }
        tracker.markPromptRequested()
    }
}

actual fun getReviewPromptManager(): ReviewPromptManager = IOSReviewPromptManager()
```

- [ ] **Step 2: Compile iOS**

Run: `./gradlew :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL.

If the `SKStoreReviewController` symbol is unresolved, it means the iOS target is not linking StoreKit automatically (Kotlin/Native's Apple framework cinterop usually links on demand). In that unlikely case, add to the iOS target in `composeApp/build.gradle.kts`:

```kotlin
iosTarget.binaries.all {
    linkerOpts("-framework", "StoreKit")
}
```

(Do not commit yet.)

---

## Task 8: Wire the trigger into `App.kt`

This change records a session when the reader closes and fires the prompt when the user returns to the library.

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/App.kt`

- [ ] **Step 1: Record session in `PdfReaderScreen`**

Locate the line near the top of `PdfReaderScreen` where state is declared (right after `var hideJob by remember { mutableStateOf<Job?>(null) }`). Add a session-start timestamp:

```kotlin
val sessionStartMs = remember(uri) { currentTimeMillis() }
```

Then find the existing `DisposableEffect(uri) { onDispose { saveCurrentPosition() } }` block and extend it so it also records the session:

```kotlin
DisposableEffect(uri) {
    onDispose {
        saveCurrentPosition()
        val endMs = currentTimeMillis()
        ReviewEligibilityTracker(getReviewStateStore()).recordSession(sessionStartMs, endMs)
    }
}
```

- [ ] **Step 2: Fire the prompt from `MainContent` when the user returns to the library**

In `MainContent`, replace the existing body with the following (the only additions are the `reviewPromptManager`, `coroutineScope`, and the `LaunchedEffect` block watching the transition; existing logic is preserved):

```kotlin
@Composable
fun MainContent(externalData: Any?) {
    var currentFileUri by rememberSaveable { mutableStateOf(externalData as? String) }
    val reviewPromptManager = remember { getReviewPromptManager() }
    val coroutineScope = rememberCoroutineScope()
    var wasReading by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(externalData) {
        if (externalData != null) {
            currentFileUri = externalData as String
        }
    }

    LaunchedEffect(currentFileUri) {
        if (currentFileUri != null) {
            wasReading = true
        } else if (wasReading) {
            wasReading = false
            coroutineScope.launch {
                reviewPromptManager.requestReviewIfAppropriate()
            }
        }
    }

    if (currentFileUri != null) {
        PdfReaderScreen(
            uri = currentFileUri!!,
            onClose = { currentFileUri = null }
        )
    } else {
        LibraryScreen(onPdfSelected = { uri -> currentFileUri = uri })
    }
}
```

Make sure these imports exist at the top of the file (most already do):

```kotlin
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import kotlinx.coroutines.launch
```

- [ ] **Step 3: Compile all targets**

Run: `./gradlew :composeApp:compileKotlinMetadata :composeApp:compileKotlinJvm :composeApp:compileDebugKotlinAndroid :composeApp:compileKotlinIosSimulatorArm64`
Expected: BUILD SUCCESSFUL for every target.

- [ ] **Step 4: Run the full test suite**

Run: `./gradlew :composeApp:jvmTest`
Expected: BUILD SUCCESSFUL — `ReviewEligibilityTrackerTest` (10 tests) and existing `ComposeAppCommonTest` (1 test) all pass.

- [ ] **Step 5: Commit**

```bash
git add gradle/libs.versions.toml \
        composeApp/build.gradle.kts \
        composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.kt \
        composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/ReviewEligibilityTracker.kt \
        composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/App.kt \
        composeApp/src/commonTest/kotlin/com/vantechinformatics/autoscrollingreader/ReviewEligibilityTrackerTest.kt \
        composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.android.kt \
        composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/MainActivity.kt \
        composeApp/src/iosMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.ios.kt \
        composeApp/src/jvmMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.jvm.kt \
        composeApp/src/jsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.js.kt \
        composeApp/src/wasmJsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.wasmJs.kt
git commit -m "$(cat <<'EOF'
feat: add in-app review prompt for Android and iOS

Fires the native Play Core / SKStoreReviewController prompt after 3
qualifying reading sessions spread across at least 2 calendar days,
with a 90-day cooldown. Eligibility logic is shared common Kotlin
with a unit-tested ReviewEligibilityTracker; platform actuals handle
persistence (SharedPreferences / NSUserDefaults) and the native API.
JVM/JS/wasmJs ship no-op implementations.
EOF
)"
```

---

## Task 9: Manual verification

- [ ] **Step 1: Android — force-eligible smoke test**

In `MainActivity.onCreate`, temporarily inject eligible state right after `currentActivity = this` (remove before merging):

```kotlin
AndroidReviewStateStore(applicationContext).apply {
    setSessionCount(5)
    setDistinctDays(listOf(0L, 1L))
    setLastPromptMs(0L)
}
```

Build a signed release and install via Google Play Internal Testing track. Open and close a PDF once. Return to the library. A system review dialog should appear (Google throttles, so it may still silently skip — that is Play behavior, not a bug).

Revert the temporary injection before committing the release build.

- [ ] **Step 2: iOS — verify call path**

`SKStoreReviewController.requestReviewInScene` does not reliably show a dialog in dev or simulator builds — only in TestFlight/App Store builds. For local verification, add a temporary `println("requestReview called")` inside `IOSReviewPromptManager.requestReviewIfAppropriate` immediately before `SKStoreReviewController.requestReviewInScene(scene)`. Force-eligibility via simulator `NSUserDefaults` the same way as Android. Confirm the println fires when returning to the library after a ≥ 30 s read.

Remove the temporary println after verifying.

- [ ] **Step 3: Wipe local state after testing**

On Android, clear the app data (or `pm clear com.vantechinformatics.autoscrollingreader`) to reset the `review_state` prefs. On iOS simulator, erase the device or delete the app.

---

## Spec coverage check (self-review)

Each numbered requirement in `docs/superpowers/specs/2026-04-17-in-app-review-design.md` maps to a task here:

- Trigger model / eligibility rules / 30s session floor / 90-day cooldown → Task 4 (tracker logic) and Task 3 (tests).
- New interfaces in `Platform.kt` → Task 1.
- `ReviewEligibilityTracker` class → Task 4.
- Android actuals (store + manager + Play Core dep + activity holder) → Tasks 5, 6.
- iOS actuals (store + manager + StoreKit call) → Task 7.
- JVM/JS/wasmJs no-op actuals → Task 2.
- Trigger wiring in `App.kt` (`PdfReaderScreen` records session, `MainContent` fires prompt on return to library) → Task 8.
- Unit tests: all 9 test cases listed in the spec are present in Task 3 (plus a 10th `distinctDaysCapsAtTwo` test, kept to lock in the cap behavior).
- Persistence schema (`session_count`, `distinct_days`, `last_prompt_ms` on Android; prefixed `review_*` on iOS) → Tasks 6, 7.
- Manual verification → Task 9.
