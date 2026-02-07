# Reader UX Redesign: Tap-to-Toggle Auto-Scroll with Auto-Hiding Controls

**Date:** 2026-02-07
**Status:** Approved
**Scope:** Android & iOS reader experience

## Problem

The current auto-scroll controls are clunky for hands-free reading. The play/stop button requires precise targeting, the control bar takes up permanent screen space, the speed slider has coarse integer steps, and scroll start/stop is abrupt. Reading position is lost when closing a PDF.

## Solution

Redesign the `PdfReaderScreen` with three improvements:

1. Tap-to-toggle auto-scroll (tap anywhere to pause/resume)
2. Auto-hiding control bar (maximizes reading area)
3. Reading position memory (resume where you left off)

## Design

### Tap-to-Toggle Interaction

- Single tap anywhere on PDF content toggles auto-scroll pause/resume.
- A semi-transparent play/pause icon appears at screen center for ~0.8s on toggle (like YouTube).
- Scroll eases in over ~0.5s on resume, decelerates on pause (no abrupt start/stop).
- Speed range: 0.5 to 15.0 in 0.5 increments (replaces integer 1-10).
- Scroll loop: `delay(16)` (~60fps) with `scrollBy(scrollSpeed * 0.3)` per frame.
- Manual scroll gestures (drag/fling) automatically pause auto-scroll.
- Auto-scroll stops when last page is fully visible, with a subtle "End of document" indicator.

**Tap exclusion zones:**
- Control bar area (when visible) interacts with controls normally.
- Back button area is excluded from tap-to-toggle.

### Auto-Hiding Control Bar

**Visibility rules:**
- Visible by default when PDF opens.
- Auto-hides 3 seconds after auto-scroll starts (slides down, ~300ms fade).
- Reappears on: double-tap anywhere, tap bottom 60dp edge zone, or auto-scroll pause.
- Hide timer resets on any control interaction.

**Layout (bottom bar):**
- Left: Large play/pause icon button (48dp touch target).
- Center: Speed label (e.g., "2.5x"), tappable to reset to default (2.0).
- Bottom: Full-width slider with large thumb, "Slow" / "Fast" labels.
- Semi-transparent background.

**Back button:**
- Permanently visible in top-left with semi-transparent background.

### Reading Position Memory

**Storage:**
- Saves `firstVisibleItemIndex` and `scrollOffset` from `LazyListState`.
- Keyed by PDF URI string.
- Android: `SharedPreferences`. iOS: `NSUserDefaults`.

**Behavior:**
- Saves on scroll pause (auto or manual) and on screen exit (`DisposableEffect` onDispose).
- Restores silently on reopen via `listState.scrollToItem` before any auto-scroll starts.

**Interface:**
```kotlin
interface ReadingPositionStore {
    fun savePosition(uri: String, firstVisibleIndex: Int, scrollOffset: Int)
    fun getPosition(uri: String): Pair<Int, Int>?
}
expect fun getReadingPositionStore(): ReadingPositionStore
```

## State Model

All state in `PdfReaderScreen` composable:

- `isScrolling: Boolean` -- auto-scroll active
- `scrollSpeed: Float` -- current speed (0.5-15.0)
- `areControlsVisible: Boolean` -- control bar visibility
- `controlsHideJob: Job?` -- coroutine for auto-hide timer
- `animatedSpeed: Float` via `animateFloatAsState` -- eased effective speed

## Files to Modify

| File | Change |
|------|--------|
| `commonMain/.../App.kt` | Rewrite `PdfReaderScreen` with new interaction model |
| `commonMain/.../Platform.kt` | Add `ReadingPositionStore` interface + expect function |
| `androidMain/.../Platform.android.kt` | `SharedPreferences` implementation of `ReadingPositionStore` |
| `iosMain/.../Platform.ios.kt` | `NSUserDefaults` implementation of `ReadingPositionStore` |

## Out of Scope

- Page number indicator
- Zoom/pinch support
- Night/dark mode in reader
- Gesture-based speed control
- JVM Desktop / Web platform changes
