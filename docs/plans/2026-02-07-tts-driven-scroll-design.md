# Text-to-Speech with TTS-Driven Scrolling

**Date:** 2026-02-07
**Status:** Approved
**Scope:** All platforms (Android, iOS, JVM Desktop primary; JS/WASM stubs)

## Problem

The auto-scrolling reader is hands-free but silent. Users want the app to read PDFs aloud while automatically scrolling to follow along, creating a true hands-free reading experience.

## Solution

Add text-to-speech that extracts text from PDF pages and speaks it aloud, with scroll automatically advancing to the next page as each page's text finishes. TTS integrates into the existing control bar as an additional button alongside play/pause and speed controls.

## Design

### Interaction Model

When the user taps the TTS button in the control bar:

1. Text is extracted from all pages (once, cached in memory for the session).
2. TTS begins speaking the current page's text.
3. When a page's text finishes, the reader scrolls to the next page and continues.
4. Single tap still pauses/resumes (pauses both TTS and scroll together).
5. Tapping the TTS button again deactivates TTS, stops speech, and reverts to manual speed-based auto-scroll (paused).

When TTS is active, the speed slider controls **speech rate** (0.5x to 2.0x) instead of scroll speed. The speed label reflects speech rate. Auto-scroll stops at the last page when all text has been spoken.

### Control Bar Layout (updated)

```
[ Play/Pause ] [ TTS speaker icon ] [ 1.0x speed label ]
[====== Speed / Speech Rate Slider ======]
```

- TTS button: speaker icon, fills/highlights when active
- Speed slider: range switches to 0.5-2.0 with 0.1 steps when TTS is active

### Interfaces (commonMain)

```kotlin
interface PdfTextExtractor {
    suspend fun extractTextByPage(data: Any): List<String>
}

interface TextToSpeechEngine {
    fun speak(text: String, onPageDone: () -> Unit)
    fun stop()
    fun setSpeechRate(rate: Float)  // 0.5 to 2.0
    fun isSpeaking(): Boolean
}

expect fun getPdfTextExtractor(): PdfTextExtractor
expect fun getTextToSpeechEngine(): TextToSpeechEngine
```

### State Additions in PdfReaderScreen

- `ttsActive: Boolean` -- TTS mode on/off
- `pageTexts: List<String>` -- cached extracted text per page
- `currentTtsPage: Int` -- which page TTS is currently reading
- `speechRate: Float` -- 0.5 to 2.0, default 1.0
- `isExtractingText: Boolean` -- loading indicator during first extraction

### Data Flow

1. User taps TTS button -> `ttsActive = true`, `isExtractingText = true`
2. `extractTextByPage(uri)` runs (suspend, off main thread) -> returns `List<String>`
3. `isExtractingText = false`, TTS starts: `engine.speak(pageTexts[currentTtsPage])`
4. `onPageDone` callback fires -> scroll to next page, speak next page's text
5. User tap pauses -> `engine.stop()`, scroll pauses
6. User tap resumes -> `engine.speak(pageTexts[currentTtsPage])` from current page
7. User deactivates TTS -> `engine.stop()`, revert to manual scroll mode

### Platform Implementations

**Android:**
- Text extraction: `com.tom-roush:pdfbox-android:2.0.27.0` with `PDFTextStripper`
- TTS: `android.speech.tts.TextToSpeech` with `UtteranceProgressListener`

**iOS:**
- Text extraction: `PDFKit` framework, `PDFPage.string` property
- TTS: `AVSpeechSynthesizer` with `AVSpeechSynthesizerDelegate`

**JVM Desktop:**
- Text extraction: Apache PDFBox 3.0.6 (already a dependency), `PDFTextStripper`
- TTS: System command via `ProcessBuilder` (`say` on macOS, `espeak` on Linux)

**JS/WASM:**
- Stubs: text extraction returns empty strings, TTS engine is no-op

## Files to Modify

| File | Change |
|------|--------|
| `commonMain/.../Platform.kt` | Add `PdfTextExtractor` + `TextToSpeechEngine` interfaces, expect functions |
| `commonMain/.../App.kt` | Add TTS button to control bar, TTS-driven scroll logic, new state variables |
| `androidMain/.../Platform.android.kt` | `AndroidPdfTextExtractor` + `AndroidTtsEngine` |
| `iosMain/.../Platform.ios.kt` | `IOSPdfTextExtractor` + `IOSTtsEngine` |
| `jvmMain/.../Platform.jvm.kt` | `JvmPdfTextExtractor` + `JvmTtsEngine` |
| `jsMain/.../Platform.js.kt` | Stubs for both interfaces |
| `wasmJsMain/.../Platform.wasmJs.kt` | Stubs for both interfaces |
| `composeApp/build.gradle.kts` | Add pdfbox-android dependency to androidMain |
| `gradle/libs.versions.toml` | Add pdfbox-android version entry |

## Out of Scope

- Voice selection / language picker
- Word-level text highlight/tracking
- Offline voice downloads
- TTS for bookmarked sections only
- Background TTS (when app is minimized)
