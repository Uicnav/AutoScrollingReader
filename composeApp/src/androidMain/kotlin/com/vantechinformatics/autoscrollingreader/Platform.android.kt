package com.vantechinformatics.autoscrollingreader

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.io.FileOutputStream

// Context global
lateinit var appContext: Context

// --- SAVED URI STORE ---
// Stores user-picked PDF URIs so they persist across app restarts

class SavedUriStore(private val context: Context) {
    private val prefs = context.getSharedPreferences("saved_pdfs", Context.MODE_PRIVATE)

    fun save(uri: String, name: String) {
        val saved = getAll().toMutableMap()
        saved[uri] = name
        prefs.edit().putStringSet("uris", saved.keys.toSet()).apply()
        prefs.edit().putString("name_$uri", name).apply()
    }

    fun getAll(): Map<String, String> {
        val uris = prefs.getStringSet("uris", emptySet()) ?: emptySet()
        return uris.associateWith { uri -> prefs.getString("name_$uri", "PDF Document") ?: "PDF Document" }
    }
}

// --- FILE IMPORTER (SAF-based) ---

class AndroidFileImporter(private val context: Context) : FileImporter {
    override val isManualImportSupported: Boolean = true

    // Shared state to trigger the SAF launcher from Compose
    var launchPicker: (() -> Unit)? = null
    var pendingCallback: ((Boolean) -> Unit)? = null

    override fun pickFile(onResult: (Boolean) -> Unit) {
        pendingCallback = onResult
        launchPicker?.invoke()
    }

    fun handleResult(uri: Uri?) {
        if (uri != null) {
            // Take persistable permission so we can access this file later
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION
                )
            } catch (_: Exception) {
                // Some providers don't support persistable permissions
            }

            // Get display name
            val name = getFileName(uri)

            // Save to our store
            val store = SavedUriStore(context)
            store.save(uri.toString(), name)

            pendingCallback?.invoke(true)
        } else {
            pendingCallback?.invoke(false)
        }
        pendingCallback = null
    }

    private fun getFileName(uri: Uri): String {
        var name = "PDF Document"
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) name = cursor.getString(idx) ?: name
                }
            }
        } catch (_: Exception) {}
        return name
    }
}

private var _fileImporter: AndroidFileImporter? = null

actual fun getFileImporter(): FileImporter {
    if (_fileImporter == null) {
        _fileImporter = AndroidFileImporter(appContext)
    }
    return _fileImporter!!
}

// --- PLATFORM ---
class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

// --- PERMISSIONS (no longer needed — SAF handles everything) ---

actual fun checkStoragePermission(): Boolean = true

@Composable
actual fun PermissionWrapper(
    content: @Composable () -> Unit
) {
    // Set up the SAF launcher here (Composable context required)
    val importer = remember { _fileImporter ?: AndroidFileImporter(appContext).also { _fileImporter = it } }

    val launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        importer.handleResult(uri)
    }

    // Connect the launcher to the importer
    LaunchedEffect(Unit) {
        importer.launchPicker = {
            launcher.launch(arrayOf("application/pdf"))
        }
    }

    content()
}

// --- SCANNER (MediaStore + Saved URIs) ---

class AndroidPdfScanner(private val context: Context) : PdfScanner {
    override suspend fun getAllPdfs(): List<PdfDocument> = withContext(Dispatchers.IO) {
        val pdfList = mutableListOf<PdfDocument>()
        val seenUris = mutableSetOf<String>()

        // 1. Load user-picked URIs from saved store (these always work)
        val savedStore = SavedUriStore(context)
        for ((uri, name) in savedStore.getAll()) {
            // Verify the URI is still accessible
            try {
                context.contentResolver.openInputStream(Uri.parse(uri))?.close()
                pdfList.add(PdfDocument(name, uri))
                seenUris.add(uri)
            } catch (_: Exception) {
                // URI no longer accessible, skip it
            }
        }

        // 2. Query MediaStore for PDFs (may return results without permissions on some devices)
        try {
            val volumes = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.getExternalVolumeNames(context)
            } else {
                setOf("external")
            }

            for (volumeName in volumes) {
                val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    MediaStore.Files.getContentUri(volumeName)
                } else {
                    MediaStore.Files.getContentUri("external")
                }

                val projection = arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                    MediaStore.Files.FileColumns.MIME_TYPE
                )

                val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
                val selectionArgs = arrayOf("application/pdf")
                val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

                context.contentResolver.query(
                    collection, projection, selection, selectionArgs, sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Unknown"
                        val contentUri = ContentUris.withAppendedId(collection, id).toString()

                        if (contentUri !in seenUris) {
                            pdfList.add(PdfDocument(name, contentUri))
                            seenUris.add(contentUri)
                        }
                    }
                }
            }
        } catch (_: Exception) {
            // MediaStore query may fail without permissions — that's fine, we still have saved URIs
        }

        return@withContext pdfList
    }
}

actual fun getPdfScanner(): PdfScanner = AndroidPdfScanner(appContext)

// --- LOADER (Render PDF) ---

class AndroidPdfLoader(private val context: Context) : PdfLoader {

    private fun resolveFile(data: Any): File {
        val inputString = data.toString()
        if (inputString.startsWith("content://") || inputString.startsWith("file://")) {
            val uri = Uri.parse(inputString)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot open stream from URI")
            val fileToRender = File(context.cacheDir, "temp_view_${System.currentTimeMillis()}.pdf")
            FileOutputStream(fileToRender).use { output -> inputStream.copyTo(output) }
            return fileToRender
        } else {
            val fileToRender = File(context.cacheDir, inputString)
            if (!fileToRender.exists()) {
                try {
                    context.assets.open(inputString).use { input ->
                        FileOutputStream(fileToRender).use { output -> input.copyTo(output) }
                    }
                } catch (e: Exception) {
                    throw Exception("File not found in assets or URI: $inputString")
                }
            }
            return fileToRender
        }
    }

    private fun renderPage(pdfRenderer: PdfRenderer, index: Int): ImageBitmap {
        val page = pdfRenderer.openPage(index)
        val width = page.width * 2
        val height = page.height * 2
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        bitmap.eraseColor(android.graphics.Color.WHITE)
        page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
        page.close()
        return bitmap.asImageBitmap()
    }

    override suspend fun loadPdf(data: Any): List<ImageBitmap> = withContext(Dispatchers.IO) {
        val file = resolveFile(data)
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        val bitmaps = (0 until renderer.pageCount).map { renderPage(renderer, it) }
        renderer.close()
        fd.close()
        bitmaps
    }

    override suspend fun loadPdfProgressively(data: Any, onPageReady: (ImageBitmap) -> Unit) = withContext(Dispatchers.IO) {
        val file = resolveFile(data)
        val fd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
        val renderer = PdfRenderer(fd)
        for (i in 0 until renderer.pageCount) {
            val bitmap = renderPage(renderer, i)
            withContext(Dispatchers.Main) { onPageReady(bitmap) }
        }
        renderer.close()
        fd.close()
    }
}

actual fun getPdfLoader(): PdfLoader {
    return AndroidPdfLoader(appContext)
}

// --- READING POSITION STORE ---

class AndroidReadingPositionStore(private val context: Context) : ReadingPositionStore {
    private val prefs = context.getSharedPreferences("reading_positions", Context.MODE_PRIVATE)

    override fun savePosition(uri: String, firstVisibleIndex: Int, scrollOffset: Int) {
        prefs.edit().putString("pos_$uri", "$firstVisibleIndex,$scrollOffset").apply()
    }

    override fun getPosition(uri: String): Pair<Int, Int>? {
        val value = prefs.getString("pos_$uri", null) ?: return null
        val parts = value.split(",")
        if (parts.size != 2) return null
        return try {
            Pair(parts[0].toInt(), parts[1].toInt())
        } catch (e: NumberFormatException) {
            null
        }
    }

    override fun saveLastOpened(uri: String) {
        prefs.edit().putLong("opened_$uri", System.currentTimeMillis()).apply()
    }

    override fun getLastOpened(uri: String): Long {
        return prefs.getLong("opened_$uri", 0L)
    }

    override fun saveScrollSpeed(uri: String, speed: Float) {
        prefs.edit().putFloat("speed_$uri", speed).apply()
    }

    override fun getScrollSpeed(uri: String): Float? {
        return if (prefs.contains("speed_$uri")) prefs.getFloat("speed_$uri", 2f) else null
    }
}

actual fun getReadingPositionStore(): ReadingPositionStore = AndroidReadingPositionStore(appContext)

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

// --- PDF TEXT EXTRACTOR ---

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
            stripper.finish()
        } finally {
            document.close()
        }

        words
    }
}

actual fun getPdfTextExtractor(): PdfTextExtractor = AndroidPdfTextExtractor(appContext)

// --- CURRENT TIME ---

actual fun currentTimeMillis(): Long = System.currentTimeMillis()
