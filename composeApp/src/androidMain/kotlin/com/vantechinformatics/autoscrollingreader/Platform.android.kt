package com.vantechinformatics.autoscrollingreader

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.ParcelFileDescriptor
import android.provider.MediaStore
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import androidx.core.net.toUri

import android.widget.Toast

class AndroidFileImporter : FileImporter {
    override fun pickFile(onResult: (Boolean) -> Unit) {
        // Pe Android scanarea e automată.
        // Aici ai putea implementa și System Picker dacă vrei, dar momentan doar anunțăm.
        Toast.makeText(appContext, "Pe Android scanarea este automată!", Toast.LENGTH_SHORT).show()
        onResult(false)
    }
}

actual fun getFileImporter(): FileImporter = AndroidFileImporter()

// Context global
lateinit var appContext: Context

// --- PLATFORM ---
class AndroidPlatform : Platform {
    override val name: String = "Android ${Build.VERSION.SDK_INT}"
}

actual fun getPlatform(): Platform = AndroidPlatform()

// --- PERMISSIONS ---

actual fun checkStoragePermission(): Boolean {
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        Environment.isExternalStorageManager()
    } else {
        ContextCompat.checkSelfPermission(
            appContext,
            Manifest.permission.READ_EXTERNAL_STORAGE
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
actual fun PermissionWrapper(
    content: @Composable () -> Unit
) {
    val context = LocalContext.current
    var hasPermission by remember { mutableStateOf(checkStoragePermission()) }

    // Launcher pentru Android 11+ (Settings Intent)
    val android11Launcher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) {
        // La întoarcerea din setări, verificăm din nou
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            hasPermission = Environment.isExternalStorageManager()
        }
    }

    // Launcher pentru Android 10 și mai vechi (Standard Permission)
    val legacyLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
        onResult = { isGranted ->
            hasPermission = isGranted
        }
    )

    if (hasPermission) {
        content()
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(24.dp)
            ) {
                Text(
                    "Acces Total Necesar",
                    style = MaterialTheme.typography.headlineSmall
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "Pentru a scana memoria internă și Cardul SD, aplicația are nevoie de permisiunea 'All Files Access'.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(24.dp))

                Button(onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        try {
                            val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                            intent.addCategory("android.intent.category.DEFAULT")
                            intent.data = String.format("package:%s", context.packageName).toUri()
                            android11Launcher.launch(intent)
                        } catch (e: Exception) {
                            e.printStackTrace()
                            val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                            android11Launcher.launch(intent)
                        }
                    } else {
                        legacyLauncher.launch(Manifest.permission.READ_EXTERNAL_STORAGE)
                    }
                }) {
                    Text("Acordă Acces Fișiere")
                }
            }
        }
    }
}

// --- SCANNER (Internal + External SD) ---

class AndroidPdfScanner(private val context: Context) : PdfScanner {
    override suspend fun getAllPdfs(): List<PdfDocument> = withContext(Dispatchers.IO) {
        val pdfList = mutableListOf<PdfDocument>()

        // Determinăm ce volume scanăm (Internal + External SD Card)
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
                MediaStore.Files.FileColumns.MIME_TYPE,
                MediaStore.Files.FileColumns.SIZE // Opțional, bun pentru filtrare fișiere corupte (0kb)
            )

            // Căutăm PDF-uri
            val selection = "${MediaStore.Files.FileColumns.MIME_TYPE} = ?"
            val selectionArgs = arrayOf("application/pdf")
            val sortOrder = "${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

            try {
                context.contentResolver.query(
                    collection, projection, selection, selectionArgs, sortOrder
                )?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                    val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)

                    while (cursor.moveToNext()) {
                        val id = cursor.getLong(idColumn)
                        val name = cursor.getString(nameColumn) ?: "Unknown"

                        // Creăm URI-ul complet
                        val contentUri = ContentUris.withAppendedId(collection, id)

                        pdfList.add(PdfDocument(name, contentUri.toString()))
                    }
                }
            } catch (e: Exception) {
                // Unele volume (ex: de sistem) pot arunca erori, le ignorăm și continuăm
                e.printStackTrace()
            }
        }

        return@withContext pdfList
    }
}

actual fun getPdfScanner(): PdfScanner = AndroidPdfScanner(appContext)

// --- LOADER (Render PDF) ---

class AndroidPdfLoader(private val context: Context) : PdfLoader {
    override suspend fun loadPdf(data: Any): List<ImageBitmap> = withContext(Dispatchers.IO) {
        val inputString = data.toString()
        val fileToRender: File

        // Logica pentru fișiere externe (Uri) vs Assets
        if (inputString.startsWith("content://") || inputString.startsWith("file://")) {
            val uri = Uri.parse(inputString)
            val inputStream = context.contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot open stream form URI")

            // Copiem într-un fișier temporar rapid (Cache)
            fileToRender = File(context.cacheDir, "temp_view_${System.currentTimeMillis()}.pdf")
            FileOutputStream(fileToRender).use { output ->
                inputStream.copyTo(output)
            }
        } else {
            // Caz: Fișier din Assets (sample.pdf) - dacă e folosit ca fallback
            fileToRender = File(context.cacheDir, inputString)
            if (!fileToRender.exists()) {
                try {
                    context.assets.open(inputString).use { input ->
                        FileOutputStream(fileToRender).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (e: Exception) {
                    throw Exception("File not found in assets or URI: $inputString")
                }
            }
        }

        val fileDescriptor = ParcelFileDescriptor.open(fileToRender, ParcelFileDescriptor.MODE_READ_ONLY)
        val pdfRenderer = PdfRenderer(fileDescriptor)
        val bitmaps = mutableListOf<ImageBitmap>()

        // Randăm paginile
        for (i in 0 until pdfRenderer.pageCount) {
            val page = pdfRenderer.openPage(i)

            // Rezoluție X2 pentru claritate
            val width = page.width * 2
            val height = page.height * 2

            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)

            // Render cu fundal alb (PDF-urile sunt transparente default uneori)
            bitmap.eraseColor(android.graphics.Color.WHITE)

            page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            bitmaps.add(bitmap.asImageBitmap())
            page.close()
        }

        pdfRenderer.close()
        fileDescriptor.close()
        // Curățăm fișierul temporar pentru a nu umple memoria
        if (inputString.startsWith("content://")) {
            // fileToRender.delete() // Decomentează dacă vrei să ștergi imediat, dar atenție la re-render
        }

        bitmaps
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
}

actual fun getReadingPositionStore(): ReadingPositionStore = AndroidReadingPositionStore(appContext)

// --- PDF TEXT EXTRACTION ---

class AndroidPdfTextExtractor(private val context: Context) : PdfTextExtractor {
    private var initialized = false

    override suspend fun extractTextByPage(data: Any): List<String> = withContext(Dispatchers.IO) {
        if (!initialized) {
            com.tom_roush.pdfbox.android.PDFBoxResourceLoader.init(context)
            initialized = true
        }

        val inputString = data.toString()
        val inputStream = if (inputString.startsWith("content://") || inputString.startsWith("file://")) {
            val uri = Uri.parse(inputString)
            context.contentResolver.openInputStream(uri)
                ?: throw Exception("Cannot open stream from URI")
        } else {
            try {
                context.assets.open(inputString)
            } catch (e: Exception) {
                throw Exception("File not found: $inputString")
            }
        }

        val document = com.tom_roush.pdfbox.pdmodel.PDDocument.load(inputStream)
        val stripper = com.tom_roush.pdfbox.text.PDFTextStripper()
        val pageTexts = mutableListOf<String>()

        for (i in 1..document.numberOfPages) {
            stripper.startPage = i
            stripper.endPage = i
            pageTexts.add(stripper.getText(document).trim())
        }

        document.close()
        pageTexts
    }
}

actual fun getPdfTextExtractor(): PdfTextExtractor = AndroidPdfTextExtractor(appContext)

// --- TEXT TO SPEECH ---

class AndroidTtsEngine(context: Context) : TextToSpeechEngine {
    private var tts: android.speech.tts.TextToSpeech? = null
    private var isReady = false
    private var currentText: String? = null
    private var currentOnDone: (() -> Unit)? = null
    private var speaking = false

    init {
        tts = android.speech.tts.TextToSpeech(context) { status ->
            isReady = status == android.speech.tts.TextToSpeech.SUCCESS
        }
        tts?.setOnUtteranceProgressListener(object : android.speech.tts.UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {
                speaking = true
            }

            override fun onDone(utteranceId: String?) {
                speaking = false
                currentOnDone?.invoke()
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                speaking = false
            }
        })
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (!isReady || text.isBlank()) {
            onDone()
            return
        }
        currentText = text
        currentOnDone = onDone
        speaking = true
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tts_page")
    }

    override fun stop() {
        speaking = false
        currentOnDone = null
        tts?.stop()
    }

    override fun pause() {
        speaking = false
        tts?.stop()
    }

    override fun resume() {
        val text = currentText ?: return
        speaking = true
        tts?.speak(text, android.speech.tts.TextToSpeech.QUEUE_FLUSH, null, "tts_page")
    }

    override fun setSpeechRate(rate: Float) {
        tts?.setSpeechRate(rate)
    }

    override fun isSpeaking(): Boolean = speaking
}

actual fun getTextToSpeechEngine(): TextToSpeechEngine = AndroidTtsEngine(appContext)