# Bookmarks & Text Highlights Implementation Plan

> **For Claude:** REQUIRED SUB-SKILL: Use superpowers:executing-plans to implement this plan task-by-task.

**Goal:** Add page bookmarks and text highlights with an annotations panel to the PDF reader.

**Architecture:** Overlay-based highlights drawn on rendered PDF images. Word-level text extraction maps touch coordinates to text positions. Annotations persisted locally via platform-native storage with kotlinx.serialization JSON. New `AnnotationStore` and `PdfTextExtractor` interfaces follow the existing `expect`/`actual` KMP pattern.

**Tech Stack:** Kotlin Multiplatform, Compose Multiplatform, kotlinx-serialization-json, pdfbox-android (Android text extraction), Apache PDFBox 3.x (JVM), PDFKit (iOS)

**Design doc:** `docs/plans/2026-02-16-bookmarks-and-highlights-design.md`

---

### Task 1: Add Build Dependencies

**Files:**
- Modify: `gradle/libs.versions.toml`
- Modify: `composeApp/build.gradle.kts`

**Step 1: Add version catalog entries**

In `gradle/libs.versions.toml`, add the serialization version and pdfbox-android version:

```toml
# In [versions] section, add:
kotlinx-serialization = "1.7.3"
pdfbox-android = "2.0.27.0"

# In [libraries] section, add:
kotlinx-serialization-json = { module = "org.jetbrains.kotlinx:kotlinx-serialization-json", version.ref = "kotlinx-serialization" }
pdfbox-android = { module = "com.tom-roush:pdfbox-android", version.ref = "pdfbox-android" }

# In [plugins] section, add:
kotlinSerialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
```

**Step 2: Apply plugin and add dependencies in build.gradle.kts**

In `composeApp/build.gradle.kts`:

Add plugin:
```kotlin
plugins {
    // ... existing plugins
    alias(libs.plugins.kotlinSerialization)
}
```

Add dependencies:
```kotlin
commonMain.dependencies {
    // ... existing deps
    implementation(libs.kotlinx.serialization.json)
}

androidMain.dependencies {
    // ... existing deps
    implementation(libs.pdfbox.android)
}
```

**Step 3: Verify build compiles**

Run: `./gradlew composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

**Step 4: Commit**

```bash
git add gradle/libs.versions.toml composeApp/build.gradle.kts
git commit -m "feat: add kotlinx-serialization and pdfbox-android dependencies"
```

---

### Task 2: Common Data Model and Interfaces

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.kt`

**Step 1: Add serialization import and data classes**

Add to the top of `Platform.kt`:
```kotlin
import kotlinx.serialization.Serializable
```

Add after the existing `ReadingPositionStore` interface:

```kotlin
// --- ANNOTATIONS ---

@Serializable
data class TextRect(
    val x: Float, val y: Float,
    val width: Float, val height: Float
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

interface AnnotationStore {
    fun saveAnnotations(uri: String, annotations: PdfAnnotations)
    fun getAnnotations(uri: String): PdfAnnotations
}

expect fun getAnnotationStore(): AnnotationStore

// --- TEXT EXTRACTION ---

data class PositionedWord(
    val text: String,
    val rect: TextRect
)

interface PdfTextExtractor {
    suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord>
}

expect fun getPdfTextExtractor(): PdfTextExtractor
```

**Step 2: Verify common code compiles**

Run: `./gradlew composeApp:compileKotlinMetadata`
Expected: FAIL (expected — `actual` implementations not yet provided)

**Step 3: Commit**

```bash
git add composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.kt
git commit -m "feat: add annotation data model, AnnotationStore and PdfTextExtractor interfaces"
```

---

### Task 3: Platform AnnotationStore Implementations

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.android.kt`
- Modify: `composeApp/src/iosMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.ios.kt`
- Modify: `composeApp/src/jvmMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.jvm.kt`
- Modify: `composeApp/src/jsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.js.kt`
- Modify: `composeApp/src/wasmJsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.wasmJs.kt`

**Step 1: Android AnnotationStore**

Add import at top of `Platform.android.kt`:
```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
```

Add before the `// --- READING POSITION STORE ---` comment:

```kotlin
// --- ANNOTATION STORE ---

class AndroidAnnotationStore(private val context: Context) : AnnotationStore {
    private val prefs = context.getSharedPreferences("pdf_annotations", Context.MODE_PRIVATE)

    override fun saveAnnotations(uri: String, annotations: PdfAnnotations) {
        val json = Json.encodeToString(annotations)
        prefs.edit().putString("annot_$uri", json).apply()
    }

    override fun getAnnotations(uri: String): PdfAnnotations {
        val json = prefs.getString("annot_$uri", null) ?: return PdfAnnotations()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            PdfAnnotations()
        }
    }
}

actual fun getAnnotationStore(): AnnotationStore = AndroidAnnotationStore(appContext)
```

**Step 2: iOS AnnotationStore**

Add import at top of `Platform.ios.kt`:
```kotlin
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
```

Add before the `// --- READING POSITION STORE ---` comment:

```kotlin
// --- ANNOTATION STORE ---

class IOSAnnotationStore : AnnotationStore {
    private val defaults = platform.Foundation.NSUserDefaults.standardUserDefaults

    override fun saveAnnotations(uri: String, annotations: PdfAnnotations) {
        val json = Json.encodeToString(annotations)
        defaults.setObject(json, forKey = "annot_$uri")
    }

    override fun getAnnotations(uri: String): PdfAnnotations {
        val json = defaults.stringForKey("annot_$uri") ?: return PdfAnnotations()
        return try {
            Json.decodeFromString(json)
        } catch (e: Exception) {
            PdfAnnotations()
        }
    }
}

actual fun getAnnotationStore(): AnnotationStore = IOSAnnotationStore()
```

**Step 3: JVM AnnotationStore**

Add to `Platform.jvm.kt` before the closing of the file:

```kotlin
// --- ANNOTATION STORE ---

class JvmAnnotationStore : AnnotationStore {
    private val store = mutableMapOf<String, PdfAnnotations>()

    override fun saveAnnotations(uri: String, annotations: PdfAnnotations) {
        store[uri] = annotations
    }

    override fun getAnnotations(uri: String): PdfAnnotations {
        return store[uri] ?: PdfAnnotations()
    }
}

actual fun getAnnotationStore(): AnnotationStore = JvmAnnotationStore()
```

**Step 4: JS stub**

Add to `Platform.js.kt`:

```kotlin
class JsAnnotationStore : AnnotationStore {
    private val store = mutableMapOf<String, PdfAnnotations>()
    override fun saveAnnotations(uri: String, annotations: PdfAnnotations) { store[uri] = annotations }
    override fun getAnnotations(uri: String): PdfAnnotations = store[uri] ?: PdfAnnotations()
}

actual fun getAnnotationStore(): AnnotationStore = JsAnnotationStore()
```

**Step 5: WASM stub**

Add to `Platform.wasmJs.kt`:

```kotlin
class WasmAnnotationStore : AnnotationStore {
    private val store = mutableMapOf<String, PdfAnnotations>()
    override fun saveAnnotations(uri: String, annotations: PdfAnnotations) { store[uri] = annotations }
    override fun getAnnotations(uri: String): PdfAnnotations = store[uri] ?: PdfAnnotations()
}

actual fun getAnnotationStore(): AnnotationStore = WasmAnnotationStore()
```

**Step 6: Verify Android compiles**

Run: `./gradlew composeApp:compileKotlinAndroid`
Expected: FAIL (PdfTextExtractor actuals still missing — that's fine)

**Step 7: Commit**

```bash
git add composeApp/src/androidMain/ composeApp/src/iosMain/ composeApp/src/jvmMain/ composeApp/src/jsMain/ composeApp/src/wasmJsMain/
git commit -m "feat: add AnnotationStore implementations for all platforms"
```

---

### Task 4: Platform PdfTextExtractor Implementations — Android

**Files:**
- Modify: `composeApp/src/androidMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.android.kt`

**Step 1: Add Android text extractor**

Add imports:
```kotlin
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
```

Add after the `AndroidAnnotationStore`:

```kotlin
// --- TEXT EXTRACTOR ---

class AndroidPdfTextExtractor(private val context: Context) : PdfTextExtractor {
    private var initialized = false

    private fun ensureInitialized() {
        if (!initialized) {
            PDFBoxResourceLoader.init(context)
            initialized = true
        }
    }

    override suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord> = withContext(Dispatchers.IO) {
        ensureInitialized()
        val words = mutableListOf<PositionedWord>()
        val inputString = data.toString()

        val inputStream = if (inputString.startsWith("content://") || inputString.startsWith("file://")) {
            context.contentResolver.openInputStream(Uri.parse(inputString))
                ?: return@withContext emptyList()
        } else {
            context.assets.open(inputString)
        }

        val document = PDDocument.load(inputStream)
        try {
            val page = document.getPage(pageIndex)
            val pageWidth = page.mediaBox.width
            val pageHeight = page.mediaBox.height

            val stripper = object : PDFTextStripper() {
                private val currentWord = StringBuilder()
                private var wordMinX = Float.MAX_VALUE
                private var wordMinY = Float.MAX_VALUE
                private var wordMaxX = 0f
                private var wordMaxY = 0f

                init {
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                    sortByPosition = true
                }

                override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                    for (tp in textPositions) {
                        val char = tp.unicode
                        if (char.isBlank()) {
                            flushWord(pageWidth, pageHeight)
                        } else {
                            if (currentWord.isEmpty()) {
                                wordMinX = tp.xDirAdj
                                wordMinY = tp.yDirAdj - tp.heightDir
                                wordMaxX = tp.xDirAdj + tp.widthDirAdj
                                wordMaxY = tp.yDirAdj
                            } else {
                                wordMinX = minOf(wordMinX, tp.xDirAdj)
                                wordMinY = minOf(wordMinY, tp.yDirAdj - tp.heightDir)
                                wordMaxX = maxOf(wordMaxX, tp.xDirAdj + tp.widthDirAdj)
                                wordMaxY = maxOf(wordMaxY, tp.yDirAdj)
                            }
                            currentWord.append(char)
                        }
                    }
                }

                override fun writeLineSeparator() {
                    flushWord(pageWidth, pageHeight)
                }

                fun finish() {
                    flushWord(pageWidth, pageHeight)
                }

                private fun flushWord(pw: Float, ph: Float) {
                    if (currentWord.isNotEmpty()) {
                        words.add(PositionedWord(
                            text = currentWord.toString(),
                            rect = TextRect(
                                x = wordMinX / pw,
                                y = wordMinY / ph,
                                width = (wordMaxX - wordMinX) / pw,
                                height = (wordMaxY - wordMinY) / ph
                            )
                        ))
                        currentWord.clear()
                        wordMinX = Float.MAX_VALUE
                        wordMinY = Float.MAX_VALUE
                        wordMaxX = 0f
                        wordMaxY = 0f
                    }
                }
            }

            stripper.getText(document)
            // The custom stripper overrides writeString, so getText triggers it
            // but we need to call finish() to flush the last word
            // Actually getText calls writeString internally. We override it.
            // Call finish to flush remaining word:
            stripper.finish()
        } finally {
            document.close()
        }

        words
    }
}

actual fun getPdfTextExtractor(): PdfTextExtractor = AndroidPdfTextExtractor(appContext)
```

**Step 2: Verify Android compiles**

Run: `./gradlew composeApp:compileKotlinAndroid`
Expected: FAIL (other platform actuals still missing)

**Step 3: Commit**

```bash
git add composeApp/src/androidMain/
git commit -m "feat: add AndroidPdfTextExtractor with pdfbox-android"
```

---

### Task 5: Platform PdfTextExtractor Implementations — iOS

**Files:**
- Modify: `composeApp/src/iosMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.ios.kt`

**Step 1: Add iOS text extractor using CoreGraphics PDF scanner**

The iOS implementation uses `CGPDFPage` with the `CGPDFScanner` API to extract text and positions. This is lower-level than PDFKit but doesn't require additional framework linking.

Add after the `IOSAnnotationStore`:

```kotlin
// --- TEXT EXTRACTOR ---

class IOSPdfTextExtractor : PdfTextExtractor {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord> = withContext(Dispatchers.IO) {
        // iOS text extraction with positions requires PDFKit or Core Text.
        // For a first pass, we return an empty list — highlights will be
        // rectangle-based on iOS until PDFKit bindings are integrated.
        // TODO: Implement with PDFKit PDFPage.selectionForWord(at:) when available
        emptyList()
    }
}

actual fun getPdfTextExtractor(): PdfTextExtractor = IOSPdfTextExtractor()
```

> **Note to implementer:** iOS text extraction with word positions requires PDFKit's `PDFPage` class which provides `characterBounds(at:)` and `selectionForWord(at:)`. The Kotlin/Native interop for PDFKit may need the framework added to the build config. For the initial release, iOS returns empty word lists — bookmarks still work fully, and highlight support will be added in a follow-up when PDFKit bindings are confirmed. This is a deliberate scope cut to avoid blocking the entire feature on iOS interop complexity.

**Step 2: Commit**

```bash
git add composeApp/src/iosMain/
git commit -m "feat: add iOS PdfTextExtractor stub (PDFKit integration follow-up)"
```

---

### Task 6: Platform PdfTextExtractor Implementations — JVM + Web Stubs

**Files:**
- Modify: `composeApp/src/jvmMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.jvm.kt`
- Modify: `composeApp/src/jsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.js.kt`
- Modify: `composeApp/src/wasmJsMain/kotlin/com/vantechinformatics/autoscrollingreader/Platform.wasmJs.kt`

**Step 1: JVM text extractor**

Add imports at top of `Platform.jvm.kt`:
```kotlin
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
```

Add after `JvmAnnotationStore`:

```kotlin
// --- TEXT EXTRACTOR ---

class JvmPdfTextExtractor : PdfTextExtractor {
    override suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord> {
        val filePath = data as String
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(filePath)
            ?: java.io.File(filePath).inputStream()
        val document = PDDocument.load(stream)
        val words = mutableListOf<PositionedWord>()

        try {
            val page = document.getPage(pageIndex)
            val pageWidth = page.mediaBox.width
            val pageHeight = page.mediaBox.height

            val stripper = object : PDFTextStripper() {
                private val currentWord = StringBuilder()
                private var wordMinX = Float.MAX_VALUE
                private var wordMinY = Float.MAX_VALUE
                private var wordMaxX = 0f
                private var wordMaxY = 0f

                init {
                    startPage = pageIndex + 1
                    endPage = pageIndex + 1
                    sortByPosition = true
                }

                override fun writeString(text: String, textPositions: MutableList<TextPosition>) {
                    for (tp in textPositions) {
                        val char = tp.unicode
                        if (char.isBlank()) {
                            flushWord(pageWidth, pageHeight)
                        } else {
                            if (currentWord.isEmpty()) {
                                wordMinX = tp.xDirAdj
                                wordMinY = tp.yDirAdj - tp.heightDir
                                wordMaxX = tp.xDirAdj + tp.widthDirAdj
                                wordMaxY = tp.yDirAdj
                            } else {
                                wordMinX = minOf(wordMinX, tp.xDirAdj)
                                wordMinY = minOf(wordMinY, tp.yDirAdj - tp.heightDir)
                                wordMaxX = maxOf(wordMaxX, tp.xDirAdj + tp.widthDirAdj)
                                wordMaxY = maxOf(wordMaxY, tp.yDirAdj)
                            }
                            currentWord.append(char)
                        }
                    }
                }

                override fun writeLineSeparator() {
                    flushWord(pageWidth, pageHeight)
                }

                fun finish() {
                    flushWord(pageWidth, pageHeight)
                }

                private fun flushWord(pw: Float, ph: Float) {
                    if (currentWord.isNotEmpty()) {
                        words.add(PositionedWord(
                            text = currentWord.toString(),
                            rect = TextRect(
                                x = wordMinX / pw,
                                y = wordMinY / ph,
                                width = (wordMaxX - wordMinX) / pw,
                                height = (wordMaxY - wordMinY) / ph
                            )
                        ))
                        currentWord.clear()
                        wordMinX = Float.MAX_VALUE
                        wordMinY = Float.MAX_VALUE
                        wordMaxX = 0f
                        wordMaxY = 0f
                    }
                }
            }

            stripper.getText(document)
            stripper.finish()
        } finally {
            document.close()
        }

        return words
    }
}

actual fun getPdfTextExtractor(): PdfTextExtractor = JvmPdfTextExtractor()
```

> **Note:** PDFBox 3.x changed `PDDocument.load()` to `Loader.loadPDF()`. Check the actual API available — if using PDFBox 3.0.6, use `Loader.loadPDF(stream)` instead of `PDDocument.load(stream)`. Adjust imports: `import org.apache.pdfbox.Loader`.

**Step 2: JS stub**

Add to `Platform.js.kt`:

```kotlin
class JsPdfTextExtractor : PdfTextExtractor {
    override suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord> = emptyList()
}

actual fun getPdfTextExtractor(): PdfTextExtractor = JsPdfTextExtractor()
```

**Step 3: WASM stub**

Add to `Platform.wasmJs.kt`:

```kotlin
class WasmPdfTextExtractor : PdfTextExtractor {
    override suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord> = emptyList()
}

actual fun getPdfTextExtractor(): PdfTextExtractor = WasmPdfTextExtractor()
```

**Step 4: Verify full project compiles**

Run: `./gradlew composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

Run: `./gradlew composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

**Step 5: Commit**

```bash
git add composeApp/src/jvmMain/ composeApp/src/jsMain/ composeApp/src/wasmJsMain/
git commit -m "feat: add PdfTextExtractor for JVM, JS and WASM platforms"
```

---

### Task 7: Bookmark Button and State

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/App.kt`

**Context:** The control bar is in `PdfReaderScreen` (line ~735-865 of App.kt). The play/pause button is at line ~762, and the speed display is at line ~781. The bookmark button goes between them.

**Step 1: Add annotation state variables**

In `PdfReaderScreen`, after the existing state variables (around line 444), add:

```kotlin
val annotationStore = remember { getAnnotationStore() }
var annotations by remember { mutableStateOf(annotationStore.getAnnotations(uri)) }

// Helper to get current visible page index
val currentPageIndex by remember {
    derivedStateOf { listState.firstVisibleItemIndex }
}

val isCurrentPageBookmarked by remember {
    derivedStateOf { annotations.bookmarks.any { it.pageIndex == currentPageIndex } }
}
```

**Step 2: Add bookmark toggle function**

After `saveCurrentPosition()` function (~line 464), add:

```kotlin
fun toggleBookmark() {
    val page = currentPageIndex
    val updated = if (annotations.bookmarks.any { it.pageIndex == page }) {
        annotations.copy(bookmarks = annotations.bookmarks.filter { it.pageIndex != page })
    } else {
        annotations.copy(bookmarks = annotations.bookmarks + Bookmark(page, System.currentTimeMillis()))
    }
    annotations = updated
    annotationStore.saveAnnotations(uri, updated)
}
```

> **Note:** `System.currentTimeMillis()` is JVM/Android only. For KMP compatibility, use `kotlinx.datetime.Clock.System.now().toEpochMilliseconds()` or a simpler `expect fun currentTimeMillis(): Long` helper. Alternatively, since the existing code already uses platform-specific time (Android uses `System.currentTimeMillis()`, iOS uses `platform.posix.time()`), just use a simple helper in commonMain:

Add to `Platform.kt`:
```kotlin
expect fun currentTimeMillis(): Long
```

Add actual implementations:
- Android: `actual fun currentTimeMillis(): Long = System.currentTimeMillis()`
- iOS: `actual fun currentTimeMillis(): Long = (platform.posix.time(null) * 1000)`
- JVM: `actual fun currentTimeMillis(): Long = System.currentTimeMillis()`
- JS: `actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()`
- WASM: `actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()`

**Step 3: Add bookmark button to control bar**

In the control bar's `Row` (around line 756-798), after the play/pause button `Box` and before the speed display `Box`, add:

```kotlin
// Bookmark button
Box(
    modifier = Modifier
        .size(48.dp)
        .border(
            1.5.dp,
            if (isCurrentPageBookmarked) NeonCyan else TextDim,
            CircleShape
        )
        .clickable { toggleBookmark() },
    contentAlignment = Alignment.Center
) {
    Icon(
        imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
        contentDescription = if (isCurrentPageBookmarked) "Remove bookmark" else "Add bookmark",
        tint = if (isCurrentPageBookmarked) NeonCyan else TextSecondary,
        modifier = Modifier.size(24.dp)
    )
}
```

**Step 4: Verify it compiles and renders**

Run: `./gradlew composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

Manual test on device/emulator: Open a PDF, verify the bookmark icon appears in the control bar between play/pause and speed. Tap it — should toggle between filled/unfilled. Close and reopen the PDF — bookmark should persist.

**Step 5: Commit**

```bash
git add composeApp/src/commonMain/ composeApp/src/androidMain/ composeApp/src/iosMain/ composeApp/src/jvmMain/ composeApp/src/jsMain/ composeApp/src/wasmJsMain/
git commit -m "feat: add bookmark button to reader control bar with persistence"
```

---

### Task 8: Page Bookmark and Highlight Indicators

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/App.kt`

**Context:** PDF pages are rendered in a `LazyColumn` at line ~638-652. Each page is an `Image` composable. We need to wrap each page in a `Box` and add overlay indicators.

**Step 1: Replace page Image with annotated Box**

Replace the `itemsIndexed` block (lines ~643-651):

```kotlin
itemsIndexed(pageList) { index, pageBitmap ->
    Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
        Image(
            contentScale = ContentScale.Crop,
            bitmap = pageBitmap,
            contentDescription = null,
            modifier = Modifier.fillMaxWidth()
        )
        // Bookmark indicator
        if (annotations.bookmarks.any { it.pageIndex == index }) {
            Icon(
                Icons.Default.Bookmark,
                contentDescription = null,
                tint = NeonCyan,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(24.dp)
            )
        }
        // Highlight indicator (left accent bar)
        if (annotations.highlights.any { it.pageIndex == index }) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .width(3.dp)
                    .fillMaxHeight()
                    .background(
                        Brush.verticalGradient(listOf(NeonCyan, NeonPurple)),
                        RoundedCornerShape(2.dp)
                    )
            )
        }
    }
}
```

**Step 2: Verify on device**

Manual test: Open a PDF, bookmark a page, scroll away and back — bookmark icon should appear on that page. (Highlight indicator won't show yet since no highlights exist.)

**Step 3: Commit**

```bash
git add composeApp/src/commonMain/
git commit -m "feat: add bookmark and highlight visual indicators on PDF pages"
```

---

### Task 9: Text Selection Mode — Long-Press and Coordinate Mapping

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/App.kt`

**Context:** This is the most complex UI task. The existing `pointerInput` on the main PDF `Box` (line ~625-636) uses `detectTapGestures` for single-tap (toggle scroll) and double-tap (toggle controls). We need to add long-press to enter selection mode.

**Step 1: Add selection state variables**

After the annotation state variables added in Task 7, add:

```kotlin
var selectionMode by remember { mutableStateOf(false) }
var selectionPageIndex by remember { mutableIntStateOf(-1) }
var selectionStartIdx by remember { mutableIntStateOf(-1) }
var selectionEndIdx by remember { mutableIntStateOf(-1) }
var cachedWords by remember { mutableStateOf<Map<Int, List<PositionedWord>>>(emptyMap()) }
val textExtractor = remember { getPdfTextExtractor() }
```

**Step 2: Add word lookup helper**

After `toggleBookmark()`, add:

```kotlin
fun findWordAtPosition(words: List<PositionedWord>, normX: Float, normY: Float): Int {
    // Find the closest word to the tap position
    var bestIdx = -1
    var bestDist = Float.MAX_VALUE
    words.forEachIndexed { idx, word ->
        val cx = word.rect.x + word.rect.width / 2
        val cy = word.rect.y + word.rect.height / 2
        val dist = (normX - cx) * (normX - cx) + (normY - cy) * (normY - cy)
        if (dist < bestDist) {
            bestDist = dist
            bestIdx = idx
        }
    }
    // Only match if within reasonable distance (20% of page dimension)
    return if (bestDist < 0.04f) bestIdx else -1
}
```

**Step 3: Modify the pointerInput to handle long-press**

Replace the existing `pointerInput` modifier on the main PDF Box:

```kotlin
.pointerInput(selectionMode) {
    detectTapGestures(
        onTap = {
            if (selectionMode) {
                // Tap outside selection dismisses it
                selectionMode = false
                selectionPageIndex = -1
                selectionStartIdx = -1
                selectionEndIdx = -1
            } else {
                isScrolling = !isScrolling
                showToggleIcon = true
            }
        },
        onDoubleTap = {
            if (!selectionMode) {
                areControlsVisible = !areControlsVisible
                if (areControlsVisible && isScrolling) startHideTimer()
            }
        },
        onLongPress = { offset ->
            if (!selectionMode) {
                // Determine which page was long-pressed
                val layoutInfo = listState.layoutInfo
                var targetPage = -1
                var pageOffset = offset

                for (item in layoutInfo.visibleItemsInfo) {
                    val itemTop = item.offset.toFloat()
                    val itemBottom = (item.offset + item.size).toFloat()
                    if (offset.y >= itemTop && offset.y < itemBottom) {
                        targetPage = item.index
                        pageOffset = offset.copy(y = offset.y - itemTop)
                        break
                    }
                }

                if (targetPage >= 0 && targetPage < pages.size) {
                    // Normalize coordinates to 0..1
                    val pageItem = layoutInfo.visibleItemsInfo.find { it.index == targetPage }
                    if (pageItem != null) {
                        val normX = offset.x / pageItem.size.toFloat()  // approximate
                        val normY = pageOffset.y / pageItem.size.toFloat()

                        // Load words for this page if not cached
                        coroutineScope.launch {
                            val words = cachedWords[targetPage]
                                ?: textExtractor.extractWords(uri, targetPage).also {
                                    cachedWords = cachedWords + (targetPage to it)
                                }

                            if (words.isNotEmpty()) {
                                // Use proper normalization based on image aspect ratio
                                val bitmap = pages[targetPage]
                                val displayWidth = pageItem.size  // LazyColumn item uses full width
                                val scaleX = offset.x / displayWidth.toFloat()
                                // The item height is the actual rendered height
                                val scaleY = pageOffset.y / pageItem.size.toFloat()

                                val wordIdx = findWordAtPosition(words, scaleX, scaleY)
                                if (wordIdx >= 0) {
                                    isScrolling = false
                                    selectionMode = true
                                    selectionPageIndex = targetPage
                                    selectionStartIdx = wordIdx
                                    selectionEndIdx = wordIdx
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}
```

> **Note:** The coordinate mapping (touch coords -> normalized page coords) needs careful calibration. The `LazyColumn` items may have padding, and `ContentScale.Crop` affects the mapping. During implementation, test with a known PDF and adjust the normalization. The key insight: `pageItem.size` from `LazyListItemInfo` gives the actual displayed height of the item in pixels. The displayed width is the viewport width. Divide touch x/y by displayed width/height to get normalized coords.

**Step 4: Verify long-press triggers selection state**

Manual test on Android: Open a PDF with text. Long-press on text. Verify that `selectionMode` becomes true (add a temporary debug overlay text showing "SELECTION MODE" to confirm). No visual selection overlay yet — that's the next task.

**Step 5: Commit**

```bash
git add composeApp/src/commonMain/
git commit -m "feat: add text selection mode with long-press detection and word lookup"
```

---

### Task 10: Selection Overlay, Drag Handles, and Floating Toolbar

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/App.kt`

**Step 1: Add selection overlay to page rendering**

Inside the `itemsIndexed` block (from Task 8), after the highlight indicator, add a selection overlay for the page being selected:

```kotlin
// Selection overlay
if (selectionMode && selectionPageIndex == index && selectionStartIdx >= 0) {
    val words = cachedWords[index] ?: emptyList()
    if (words.isNotEmpty()) {
        val startIdx = minOf(selectionStartIdx, selectionEndIdx)
        val endIdx = maxOf(selectionStartIdx, selectionEndIdx)
        val selectedWords = words.subList(startIdx.coerceAtLeast(0), (endIdx + 1).coerceAtMost(words.size))

        Canvas(modifier = Modifier.matchParentSize()) {
            for (word in selectedWords) {
                drawRect(
                    color = NeonCyan.copy(alpha = 0.25f),
                    topLeft = Offset(word.rect.x * size.width, word.rect.y * size.height),
                    size = Size(word.rect.width * size.width, word.rect.height * size.height)
                )
            }
        }
    }
}
```

Add required imports at top of App.kt:
```kotlin
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
```

**Step 2: Add floating toolbar above selection**

After the `LazyColumn` block but still inside the main content `Box`, add:

```kotlin
// Selection floating toolbar
if (selectionMode && selectionPageIndex >= 0) {
    val words = cachedWords[selectionPageIndex] ?: emptyList()
    val startIdx = minOf(selectionStartIdx, selectionEndIdx).coerceAtLeast(0)
    val endIdx = maxOf(selectionStartIdx, selectionEndIdx).coerceAtMost(words.size - 1)

    Box(
        modifier = Modifier
            .align(Alignment.TopCenter)
            .padding(top = 80.dp)
            .statusBarsPadding()
    ) {
        Row(
            modifier = Modifier
                .background(DarkSurface.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = {
                // Save highlight
                if (words.isNotEmpty() && startIdx >= 0 && endIdx < words.size) {
                    val selectedText = words.subList(startIdx, endIdx + 1).joinToString(" ") { it.text }
                    val rects = words.subList(startIdx, endIdx + 1).map { it.rect }
                    val highlight = Highlight(
                        pageIndex = selectionPageIndex,
                        text = selectedText,
                        rects = rects,
                        createdAt = currentTimeMillis()
                    )
                    val updated = annotations.copy(highlights = annotations.highlights + highlight)
                    annotations = updated
                    annotationStore.saveAnnotations(uri, updated)
                }
                selectionMode = false
                selectionStartIdx = -1
                selectionEndIdx = -1
            }) {
                Icon(Icons.Default.Highlight, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text("HIGHLIGHT", color = NeonCyan, style = MaterialTheme.typography.labelMedium)
            }
            Box(Modifier.width(1.dp).height(24.dp).background(TextDim))
            TextButton(onClick = {
                selectionMode = false
                selectionStartIdx = -1
                selectionEndIdx = -1
            }) {
                Text("CANCEL", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
            }
        }
    }
}
```

**Step 3: Add permanent highlight overlay rendering**

In the `itemsIndexed` block, after the bookmark indicator and before the selection overlay, add rendering for saved highlights:

```kotlin
// Saved highlight overlays
val pageHighlights = annotations.highlights.filter { it.pageIndex == index }
if (pageHighlights.isNotEmpty()) {
    Canvas(modifier = Modifier.matchParentSize()) {
        for (highlight in pageHighlights) {
            for (rect in highlight.rects) {
                drawRect(
                    color = NeonCyan.copy(alpha = 0.2f),
                    topLeft = Offset(rect.x * size.width, rect.y * size.height),
                    size = Size(rect.width * size.width, rect.height * size.height)
                )
            }
        }
    }
}
```

**Step 4: Verify full selection flow**

Manual test on Android: Open a PDF with text. Long-press a word → selection overlay appears (cyan highlight on the word). Floating toolbar appears. Tap "HIGHLIGHT" → highlight is saved, overlay persists. Close and reopen PDF → highlight still visible.

**Step 5: Commit**

```bash
git add composeApp/src/commonMain/
git commit -m "feat: add text selection overlay, floating toolbar, and persistent highlight rendering"
```

---

### Task 11: Annotations Panel (Bottom Sheet)

**Files:**
- Modify: `composeApp/src/commonMain/kotlin/com/vantechinformatics/autoscrollingreader/App.kt`

**Step 1: Add panel state and trigger button**

Add state variable with the others:
```kotlin
var showAnnotationsPanel by remember { mutableStateOf(false) }
```

Add an annotations button in the top-right area. After the glassmorphic back button (around line 713-732), add:

```kotlin
// Annotations panel button
val annotationCount = annotations.bookmarks.size + annotations.highlights.size
Box(
    modifier = Modifier
        .align(Alignment.TopEnd)
        .padding(16.dp)
        .statusBarsPadding()
        .background(DarkSurface.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
        .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
        .clickable { showAnnotationsPanel = true }
        .padding(horizontal = 12.dp, vertical = 8.dp)
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Default.CollectionsBookmark, contentDescription = "Annotations", tint = NeonCyan, modifier = Modifier.size(20.dp))
        if (annotationCount > 0) {
            Spacer(Modifier.width(6.dp))
            Text(
                "$annotationCount",
                style = MaterialTheme.typography.labelSmall,
                color = NeonCyan
            )
        }
    }
}
```

**Step 2: Add ModalBottomSheet**

Add import:
```kotlin
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
```

At the end of the `PdfReaderScreen` composable (just before the final closing `}`), add:

```kotlin
// Annotations panel bottom sheet
if (showAnnotationsPanel) {
    @OptIn(ExperimentalMaterial3Api::class)
    ModalBottomSheet(
        onDismissRequest = { showAnnotationsPanel = false },
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = DarkSurface,
        contentColor = TextPrimary,
        dragHandle = {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(CyanPurpleGradient)
            )
        }
    ) {
        var activeTab by remember { mutableIntStateOf(0) }

        Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("ANNOTATIONS", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                IconButton(onClick = { showAnnotationsPanel = false }) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Spacer(Modifier.height(12.dp))

            // Tabs
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("BOOKMARKS" to annotations.bookmarks.size, "HIGHLIGHTS" to annotations.highlights.size).forEachIndexed { idx, (label, count) ->
                    val isActive = activeTab == idx
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .background(
                                if (isActive) NeonCyan.copy(alpha = 0.15f) else SurfaceHighlight,
                                RoundedCornerShape(20.dp)
                            )
                            .border(
                                1.dp,
                                if (isActive) NeonCyan.copy(alpha = 0.5f) else Color.Transparent,
                                RoundedCornerShape(20.dp)
                            )
                            .clickable { activeTab = idx }
                            .padding(vertical = 10.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "$label ($count)",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (isActive) NeonCyan else TextSecondary
                        )
                    }
                }
            }

            Spacer(Modifier.height(16.dp))

            // Content
            LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                if (activeTab == 0) {
                    // Bookmarks tab
                    val sortedBookmarks = annotations.bookmarks.sortedBy { it.pageIndex }
                    if (sortedBookmarks.isEmpty()) {
                        item {
                            Text(
                                "No bookmarks yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextDim,
                                modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(sortedBookmarks.size) { idx ->
                            val bookmark = sortedBookmarks[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            listState.scrollToItem(bookmark.pageIndex)
                                        }
                                        isScrolling = false
                                        showAnnotationsPanel = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Bookmark, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Text("Page ${bookmark.pageIndex + 1}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    val updated = annotations.copy(bookmarks = annotations.bookmarks.filter { it.pageIndex != bookmark.pageIndex })
                                    annotations = updated
                                    annotationStore.saveAnnotations(uri, updated)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextDim, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                } else {
                    // Highlights tab
                    val sortedHighlights = annotations.highlights.sortedBy { it.pageIndex }
                    if (sortedHighlights.isEmpty()) {
                        item {
                            Text(
                                "No highlights yet",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextDim,
                                modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(),
                                textAlign = TextAlign.Center
                            )
                        }
                    } else {
                        items(sortedHighlights.size) { idx ->
                            val highlight = sortedHighlights[idx]
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        coroutineScope.launch {
                                            listState.scrollToItem(highlight.pageIndex)
                                        }
                                        isScrolling = false
                                        showAnnotationsPanel = false
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Highlight, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Page ${highlight.pageIndex + 1}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                    Text(
                                        highlight.text.take(60) + if (highlight.text.length > 60) "..." else "",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = TextPrimary,
                                        maxLines = 2
                                    )
                                }
                                IconButton(onClick = {
                                    val updated = annotations.copy(highlights = annotations.highlights.filter { it !== highlight })
                                    annotations = updated
                                    annotationStore.saveAnnotations(uri, updated)
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextDim, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }

            Spacer(Modifier.height(16.dp))
        }
    }
}
```

**Step 3: Add missing imports for LazyColumn items**

Add to imports:
```kotlin
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.heightIn
```

**Step 4: Verify annotations panel**

Manual test: Open a PDF, bookmark 2-3 pages. Tap the annotations button (top-right). Bottom sheet should appear with BOOKMARKS tab showing the bookmarked pages. Tap a bookmark → scrolls to that page. Switch to HIGHLIGHTS tab → shows "No highlights yet". Delete a bookmark using the trash icon → it disappears.

**Step 5: Commit**

```bash
git add composeApp/src/commonMain/
git commit -m "feat: add annotations panel with bookmarks and highlights tabs"
```

---

### Task 12: Final Verification and Cleanup

**Step 1: Full build verification**

Run: `./gradlew composeApp:compileKotlinAndroid`
Expected: BUILD SUCCESSFUL

Run: `./gradlew composeApp:compileKotlinJvm`
Expected: BUILD SUCCESSFUL

**Step 2: End-to-end manual test on Android**

Test checklist:
- [ ] Open a PDF → control bar shows bookmark button
- [ ] Tap bookmark button → icon fills, page gets bookmark indicator
- [ ] Tap bookmark button again → bookmark removed
- [ ] Long-press on text → selection mode activates, word highlighted
- [ ] Tap "HIGHLIGHT" → highlight persists
- [ ] Tap "CANCEL" → selection dismissed
- [ ] Tap annotations button (top-right) → panel opens
- [ ] Bookmarks tab shows bookmarked pages, tap navigates
- [ ] Highlights tab shows highlighted text, tap navigates
- [ ] Delete bookmark/highlight from panel → removed
- [ ] Close and reopen PDF → annotations persist
- [ ] Auto-scroll still works (tap to toggle)
- [ ] Speed slider still works
- [ ] Back button still works

**Step 3: Use @superpowers:verification-before-completion before claiming done**

**Step 4: Final commit if any cleanup needed**

```bash
git add -A
git commit -m "feat: bookmarks and text highlights - cleanup and fixes"
```
