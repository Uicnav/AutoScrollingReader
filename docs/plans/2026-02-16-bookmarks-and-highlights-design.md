# Bookmarks & Text Highlights

**Date:** 2026-02-16
**Status:** Approved
**Scope:** All platforms (Android, iOS, JVM Desktop primary; JS/WASM stubs)

## Problem

The reader has no way to mark important pages or passages. Users want to bookmark pages for quick navigation and highlight text for reference.

## Solution

Add page bookmarks (tap a button in the control bar) and text highlights (long-press to select, overlay-based rendering on top of PDF images). Both are persisted locally per device. An annotations panel lists all bookmarks and highlights with tap-to-jump navigation.

## Approach

**Overlay-based highlights (Approach A).** Highlights are semi-transparent colored overlays drawn on top of rendered PDF page images. Text selection uses platform-native text extraction with word-level bounding boxes to map touch coordinates to text positions. PDF images stay untouched — highlights are a UI layer. This reuses text extraction infrastructure needed for TTS later.

## Data Model

```kotlin
@Serializable
data class TextRect(
    val x: Float, val y: Float,       // top-left, normalized 0..1
    val width: Float, val height: Float // normalized 0..1
)

@Serializable
data class Bookmark(
    val pageIndex: Int,
    val createdAt: Long
)

@Serializable
data class Highlight(
    val pageIndex: Int,
    val text: String,
    val rects: List<TextRect>,
    val createdAt: Long
)

@Serializable
data class PdfAnnotations(
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList()
)
```

Coordinates are normalized (0.0–1.0 relative to page dimensions) so highlights survive different screen sizes.

## Interfaces

### AnnotationStore

Persists annotations per PDF URI. Follows the same `expect`/`actual` pattern as `ReadingPositionStore`.

```kotlin
interface AnnotationStore {
    fun saveAnnotations(uri: String, annotations: PdfAnnotations)
    fun getAnnotations(uri: String): PdfAnnotations
}

expect fun getAnnotationStore(): AnnotationStore
```

Platform implementations:
- **Android**: SharedPreferences with kotlinx.serialization JSON
- **iOS**: NSUserDefaults with kotlinx.serialization JSON
- **JVM**: In-memory (matches existing JvmReadingPositionStore)
- **JS/WASM**: No-op stubs

### PdfTextExtractor

Extracts words with their positions from a PDF page for text selection.

```kotlin
data class PositionedWord(
    val text: String,
    val rect: TextRect  // normalized 0..1 coordinates
)

interface PdfTextExtractor {
    suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord>
}

expect fun getPdfTextExtractor(): PdfTextExtractor
```

Platform implementations normalize bounding boxes from native PDF coordinate space to 0..1 before returning.

- **Android**: `pdfbox-android` with TextPosition walking
- **iOS**: PDFKit — `PDFPage.selectionForRange` / `PDFSelection.bounds(for:)`
- **JVM**: Apache PDFBox 3.0.6 (already a dependency) with custom TextPosition processing
- **JS/WASM**: Stubs returning empty lists

Compatible with the TTS design's `PdfTextExtractor` — TTS can reuse this by concatenating word texts.

## UI Changes

### Bookmark Button (Control Bar)

Added between play/pause and speed display:

```
[ Play/Pause ] [ Bookmark ] [ 2.0x ]
[======= Speed Slider =======]
[======= Progress Bar =======]
```

- `Icons.Default.BookmarkBorder` when current page is not bookmarked
- `Icons.Default.Bookmark` (filled, NeonCyan) when bookmarked
- Tap toggles bookmark on/off for the currently visible page

### Page Indicators

- **Bookmarked pages**: Small neon bookmark icon in the top-right corner of the page image
- **Highlighted pages**: Thin NeonCyan left-edge bar (3dp) on the page item

### Text Selection Mode

Long-press enters selection mode. Coexists with existing gestures (single-tap = toggle scroll, double-tap = toggle controls).

1. **Long-press** on a word → auto-scroll pauses, word highlighted, drag handles appear
2. **Drag handles** to extend selection (snaps to word boundaries). Selection overlay: `NeonCyan.copy(alpha = 0.25f)`
3. **Floating toolbar** appears above selection: "Highlight" (saves) and "Cancel" (dismisses)
4. Tapping "Highlight" persists to `AnnotationStore`, overlay becomes permanent
5. Single-tap outside selection dismisses and resumes normal gesture behavior

Coordinate mapping: touch `(x, y)` → normalized page coordinates using displayed vs actual image dimensions → matched against `PositionedWord` list (loaded lazily, cached per page).

New state in `PdfReaderScreen`:
- `selectionMode: Boolean`
- `selectionPageIndex: Int`
- `selectionStart: Int` / `selectionEnd: Int` — indices into page's `PositionedWord` list
- `cachedWords: Map<Int, List<PositionedWord>>`

Gesture conflict: `pointerInput` checks `selectionMode` first. When active, taps/drags go to selection handler. When inactive, existing behavior applies unchanged.

### Annotations Panel

Bottom sheet triggered by a button in the top-right of the reader (`Icons.Default.CollectionsBookmark` with badge count). Uses Material3 `ModalBottomSheet`.

Layout:
- Two pill-shaped tab toggles: "BOOKMARKS" and "HIGHLIGHTS" (NeonCyan fill for active)
- Each item: page number, preview text (highlights only, ~50 chars truncated), delete icon
- Tap item → close panel, scroll to that page, pause auto-scroll
- Delete removes annotation immediately (no confirmation)
- Empty state: dim message ("No bookmarks yet" / "No highlights yet")
- Styled: DarkSurface background, CyanPurpleGradient accent, glassmorphic

## Files to Modify

| File | Change |
|------|--------|
| `commonMain/.../Platform.kt` | Add `AnnotationStore`, `PdfTextExtractor`, data classes |
| `commonMain/.../App.kt` | Selection mode, bookmark button, highlight overlays, annotations panel |
| `androidMain/.../Platform.android.kt` | `AndroidAnnotationStore`, `AndroidPdfTextExtractor` |
| `iosMain/.../Platform.ios.kt` | `IOSAnnotationStore`, `IOSPdfTextExtractor` |
| `jvmMain/.../Platform.jvm.kt` | `JvmAnnotationStore`, `JvmPdfTextExtractor` |
| `jsMain/.../Platform.js.kt` | Stubs for both interfaces |
| `wasmJsMain/.../Platform.wasmJs.kt` | Stubs for both interfaces |
| `composeApp/build.gradle.kts` | Add `kotlinx-serialization-json`, `pdfbox-android` |

## Dependencies

- `kotlinx-serialization-json` — cross-platform JSON for annotation persistence
- `pdfbox-android` — Android text extraction (also needed for future TTS)

## Out of Scope

- Export/share annotations
- Syncing across devices
- Multiple highlight colors
- Cross-page text selection
- Undo/redo for annotation edits
- Embedding annotations into the PDF file
