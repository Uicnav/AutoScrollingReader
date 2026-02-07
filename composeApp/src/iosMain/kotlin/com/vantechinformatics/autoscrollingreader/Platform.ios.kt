package com.vantechinformatics.autoscrollingreader

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import org.jetbrains.skia.Image
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.CoreGraphics.CGContextDrawPDFPage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGPDFDocumentCreateWithURL
import platform.CoreGraphics.CGPDFDocumentGetNumberOfPages
import platform.CoreGraphics.CGPDFDocumentGetPage
import platform.CoreGraphics.CGPDFPageGetBoxRect
import platform.CoreGraphics.CGSizeMake
import platform.CoreGraphics.kCGPDFMediaBox
import platform.Foundation.CFBridgingRetain
import platform.Foundation.NSBundle
import platform.Foundation.NSDocumentDirectory
import platform.Foundation.NSFileManager
import platform.Foundation.NSURL
import platform.Foundation.NSUserDomainMask
import platform.UIKit.UIApplication
import platform.UIKit.UIDevice
import platform.UIKit.UIDocumentPickerDelegateProtocol
import platform.UIKit.UIDocumentPickerViewController
import platform.UIKit.UIGraphicsBeginImageContext
import platform.UIKit.UIGraphicsEndImageContext
import platform.UIKit.UIGraphicsGetCurrentContext
import platform.UIKit.UIGraphicsGetImageFromCurrentImageContext
import platform.UIKit.UIImage
import platform.UIKit.UIImagePNGRepresentation
import platform.UniformTypeIdentifiers.UTTypePDF
import platform.AVFAudio.AVSpeechBoundary
import platform.AVFAudio.AVSpeechSynthesizer
import platform.AVFAudio.AVSpeechSynthesizerDelegateProtocol
import platform.AVFAudio.AVSpeechUtterance
import platform.darwin.NSObject

// 1. Clasa principală: Implementează DOAR interfața Kotlin
class IosFileImporter : FileImporter {

    // IMPORTANT: Trebuie să păstrăm o referință puternică la delegat,
    // altfel garbage collector-ul îl șterge înainte ca userul să aleagă fișierul
    // deoarece proprietatea .delegate din iOS este 'weak'.
    private var delegate: PickerDelegate? = null

    override fun pickFile(onResult: (Boolean) -> Unit) {
        // Inițializăm delegatul care moștenește NSObject
        val newDelegate = PickerDelegate(onResult)
        this.delegate = newDelegate // Îl ținem în viață

        val documentPicker = UIDocumentPickerViewController(
            forOpeningContentTypes = listOf(UTTypePDF), asCopy = true
        )

        documentPicker.delegate = newDelegate
        documentPicker.allowsMultipleSelection = true

        val window = UIApplication.sharedApplication.keyWindow
        val rootViewController = window?.rootViewController
        rootViewController?.presentViewController(
            documentPicker,
            animated = true,
            completion = null
        )
    }

    // 2. Clasa Ajutătoare: Moștenește NSObject și se ocupă de iOS (Native)
    private class PickerDelegate(
        private val callback: (Boolean) -> Unit
    ) : NSObject(), UIDocumentPickerDelegateProtocol {

        @OptIn(ExperimentalForeignApi::class)
        override fun documentPicker(
            controller: UIDocumentPickerViewController,
            didPickDocumentsAtURLs: List<*>
        ) {
            val urls = didPickDocumentsAtURLs as? List<NSURL> ?: return
            val fileManager = NSFileManager.defaultManager

            val docsUrl = fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask)
                .first() as? NSURL

            if (docsUrl == null) {
                callback(false)
                return
            }

            var success = false

            urls.forEach { sourceUrl ->
                val fileName = sourceUrl.lastPathComponent ?: "imported.pdf"
                val destUrl = docsUrl.URLByAppendingPathComponent(fileName)

                if (destUrl != null && destUrl.path != null) {
                    try {
                        if (fileManager.fileExistsAtPath(destUrl.path!!)) {
                            fileManager.removeItemAtURL(destUrl, null)
                        }

                        fileManager.copyItemAtURL(sourceUrl, destUrl, null)
                        success = true
                    } catch (e: Exception) {
                        println("Eroare la copiere fișier iOS: ${e.message}")
                    }
                }
            }

            callback(success)
        }

        override fun documentPickerWasCancelled(controller: UIDocumentPickerViewController) {
            callback(false)
        }
    }
}

// Singleton
val importer = IosFileImporter()

actual fun getFileImporter(): FileImporter = importer
class IOSPdfScanner : PdfScanner {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun getAllPdfs(): List<PdfDocument> {
        val fileManager = NSFileManager.defaultManager

        // 1. Obținem calea către folderul "Documents" al aplicației (Sandbox)
        // Acesta este singurul loc unde avem drepturi de scriere/citire garantate fără picker
        val documentsUrl =
            fileManager.URLsForDirectory(NSDocumentDirectory, NSUserDomainMask).first() as? NSURL
        val documentsPath = documentsUrl?.path ?: return emptyList()

        val pdfList = mutableListOf<PdfDocument>()

        // 2. Citim conținutul folderului
        val fileNames = fileManager.contentsOfDirectoryAtPath(documentsPath, null) as? List<String>

        // 3. Filtrăm și construim lista
        fileNames?.forEach { fileName ->
            // Verificăm extensia (case insensitive)
            if (fileName.endsWith(".pdf", ignoreCase = true)) {

                // Construim calea absolută completă
                // Pe iOS avem nevoie de "file://" pentru ca Loader-ul să știe că e path local
                val fullUrl = documentsUrl.URLByAppendingPathComponent(fileName)
                val fullPathString = fullUrl?.absoluteString

                if (fullPathString != null) {
                    pdfList.add(PdfDocument(name = fileName, uri = fullPathString))
                }
            }
        }

        // (Opțional) Debugging: Să vezi în consolă ce cale scanează
        println("IOSPdfScanner: Scanned path $documentsPath, found ${pdfList.size} files.")

        return pdfList
    }
}

actual fun getPdfScanner(): PdfScanner = IOSPdfScanner()

// --- PLATFORM ---
class IOSPlatform : Platform {
    override val name: String =
        UIDevice.currentDevice.systemName() + " " + UIDevice.currentDevice.systemVersion
}

actual fun getPlatform(): Platform = IOSPlatform()

// --- PERMISSIONS ---
actual fun checkStoragePermission(): Boolean = true

@Composable
actual fun PermissionWrapper(
    content: @Composable () -> Unit
) {
    content()
}

// --- LOADER ---
class IOSPdfLoader : PdfLoader {
    @OptIn(ExperimentalForeignApi::class)
    override suspend fun loadPdf(data: Any): List<ImageBitmap> {
        val pathString = data as String
        val url: NSURL?

        if (pathString.startsWith("file://")) {
            url = NSURL.URLWithString(pathString)
        } else {
            val path =
                NSBundle.mainBundle.pathForResource(pathString.removeSuffix(".pdf"), ofType = "pdf")
            url = path?.let { NSURL.fileURLWithPath(it) }
        }

        if (url == null) throw Exception("PDF URL is null for: $data")

        // 1. Obținem un pointer generic (CFTypeRef)
        val cfUrlPtr = CFBridgingRetain(url)

        // 2. FIX: Lăsăm compilatorul să deducă tipul corect specificând explicit tipul variabilei (CFURLRef?)
        // Nu mai scriem .reinterpret<CFURL>(), ci doar .reinterpret()
        val cfUrlRef: CFURLRef? = cfUrlPtr?.reinterpret()

        // 3. Creăm documentul
        val document = CGPDFDocumentCreateWithURL(cfUrlRef)

        // 4. Eliberăm memoria pentru bridge
        if (cfUrlPtr != null) {
            CFRelease(cfUrlPtr)
        }

        if (document == null) throw Exception("Cannot create CGPDFDocument")

        val pageCount = CGPDFDocumentGetNumberOfPages(document)
        val images = mutableListOf<ImageBitmap>()

        for (i in 1..pageCount.toInt()) {
            val page = CGPDFDocumentGetPage(document, i.toULong()) ?: continue
            val pageRect = CGPDFPageGetBoxRect(page, kCGPDFMediaBox)

            val width = pageRect.useContents { size.width }
            val height = pageRect.useContents { size.height }

            UIGraphicsBeginImageContext(CGSizeMake(width, height))
            val context = UIGraphicsGetCurrentContext()

            CGContextSetRGBFillColor(context, 1.0, 1.0, 1.0, 1.0)
            CGContextFillRect(context, pageRect)

            CGContextTranslateCTM(context, 0.0, height)
            CGContextScaleCTM(context, 1.0, -1.0)

            CGContextDrawPDFPage(context, page)

            val uiImage = UIGraphicsGetImageFromCurrentImageContext()
            UIGraphicsEndImageContext()

            if (uiImage != null) {
                val skiaImage = uiImage.toSkiaImage()
                if (skiaImage != null) {
                    images.add(skiaImage.toComposeImageBitmap())
                }
            }
        }
        return images
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun UIImage.toSkiaImage(): Image? {
        val imageData = UIImagePNGRepresentation(this) ?: return null
        val length = imageData.length.toInt()

        val bytes = imageData.bytes?.reinterpret<ByteVar>()?.readBytes(length)

        return if (bytes != null) {
            Image.makeFromEncoded(bytes)
        } else {
            null
        }
    }
}

actual fun getPdfLoader(): PdfLoader = IOSPdfLoader()

// --- READING POSITION STORE ---

class IOSReadingPositionStore : ReadingPositionStore {
    private val defaults = platform.Foundation.NSUserDefaults.standardUserDefaults

    override fun savePosition(uri: String, firstVisibleIndex: Int, scrollOffset: Int) {
        defaults.setObject("$firstVisibleIndex,$scrollOffset", forKey = "pos_$uri")
    }

    override fun getPosition(uri: String): Pair<Int, Int>? {
        val value = defaults.stringForKey("pos_$uri") ?: return null
        val parts = value.split(",")
        if (parts.size != 2) return null
        return try {
            Pair(parts[0].toInt(), parts[1].toInt())
        } catch (e: Exception) {
            null
        }
    }
}

actual fun getReadingPositionStore(): ReadingPositionStore = IOSReadingPositionStore()

// --- PDF TEXT EXTRACTION ---

class IOSPdfTextExtractor : PdfTextExtractor {
    override suspend fun extractTextByPage(data: Any): List<String> {
        val pathString = data as String
        val url: NSURL? = if (pathString.startsWith("file://")) {
            NSURL.URLWithString(pathString)
        } else {
            val path = NSBundle.mainBundle.pathForResource(pathString.removeSuffix(".pdf"), ofType = "pdf")
            path?.let { NSURL.fileURLWithPath(it) }
        }

        if (url == null) throw Exception("PDF URL is null for: $data")

        val pdfDoc = platform.PDFKit.PDFDocument(url)
            ?: throw Exception("Cannot create PDFDocument for: $data")

        val pageTexts = mutableListOf<String>()
        val pageCount = pdfDoc.pageCount().toInt()

        for (i in 0 until pageCount) {
            val page = pdfDoc.pageAtIndex(i.toULong())
            pageTexts.add(page?.string() ?: "")
        }

        return pageTexts
    }
}

actual fun getPdfTextExtractor(): PdfTextExtractor = IOSPdfTextExtractor()

// --- TEXT TO SPEECH ---

class IOSTtsEngine : TextToSpeechEngine {
    private val synthesizer = AVSpeechSynthesizer()
    private var currentText: String? = null
    private var currentOnDone: (() -> Unit)? = null
    private var speechRate: Float = 0.5f  // AVSpeechUtterance default rate
    private var speaking = false

    // Keep strong reference to delegate
    private var delegate: TtsDelegate? = null

    init {
        val newDelegate = TtsDelegate(
            onFinish = {
                speaking = false
                currentOnDone?.invoke()
            }
        )
        delegate = newDelegate
        synthesizer.delegate = newDelegate
    }

    override fun speak(text: String, onDone: () -> Unit) {
        if (text.isBlank()) {
            onDone()
            return
        }
        currentText = text
        currentOnDone = onDone
        speaking = true

        val utterance = AVSpeechUtterance.speechUtteranceWithString(text)
        utterance.rate = speechRate
        synthesizer.speakUtterance(utterance)
    }

    override fun stop() {
        speaking = false
        currentOnDone = null
        synthesizer.stopSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    override fun pause() {
        speaking = false
        synthesizer.pauseSpeakingAtBoundary(AVSpeechBoundary.AVSpeechBoundaryImmediate)
    }

    override fun resume() {
        speaking = true
        synthesizer.continueSpeaking()
    }

    override fun setSpeechRate(rate: Float) {
        // AVSpeechUtterance rate: 0.0 (slowest) to 1.0 (fastest), default ~0.5
        // Map our 0.5-2.0 input to 0.25-0.75 AVSpeech range
        speechRate = 0.25f + (rate - 0.5f) * (0.5f / 1.5f)
    }

    override fun isSpeaking(): Boolean = speaking

    private class TtsDelegate(
        private val onFinish: () -> Unit
    ) : NSObject(), AVSpeechSynthesizerDelegateProtocol {
        override fun speechSynthesizer(
            synthesizer: AVSpeechSynthesizer,
            didFinishSpeechUtterance: AVSpeechUtterance
        ) {
            onFinish()
        }
    }
}

actual fun getTextToSpeechEngine(): TextToSpeechEngine = IOSTtsEngine()