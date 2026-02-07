package com.vantechinformatics.autoscrollingreader

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readBytes
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.useContents
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.IO
import kotlinx.coroutines.withContext
import org.jetbrains.skia.ColorAlphaType
import org.jetbrains.skia.ColorType
import org.jetbrains.skia.Image
import org.jetbrains.skia.ImageInfo
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFURLRef
import platform.CoreGraphics.CGBitmapContextCreate
import platform.CoreGraphics.CGBitmapContextCreateImage
import platform.CoreGraphics.CGColorSpaceCreateDeviceRGB
import platform.CoreGraphics.CGColorSpaceRelease
import platform.CoreGraphics.CGContextDrawPDFPage
import platform.CoreGraphics.CGContextFillRect
import platform.CoreGraphics.CGContextScaleCTM
import platform.CoreGraphics.CGContextSetRGBFillColor
import platform.CoreGraphics.CGContextTranslateCTM
import platform.CoreGraphics.CGDataProviderCopyData
import platform.CoreGraphics.CGImageGetDataProvider
import platform.CoreGraphics.CGImageRelease
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
import platform.darwin.NSObject

// 1. Clasa principală: Implementează DOAR interfața Kotlin
class IosFileImporter : FileImporter {
    override val isManualImportSupported: Boolean = true

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
    override suspend fun loadPdfProgressively(data: Any, onPageReady: (ImageBitmap) -> Unit) {
        val pathString = data as String
        val url: NSURL? = if (pathString.startsWith("file://")) {
            NSURL.URLWithString(pathString)
        } else {
            NSBundle.mainBundle.pathForResource(pathString.removeSuffix(".pdf"), ofType = "pdf")
                ?.let { NSURL.fileURLWithPath(it) }
        }
        if (url == null) throw Exception("PDF URL is null for: $data")

        val cfUrlPtr = CFBridgingRetain(url)
        val cfUrlRef: CFURLRef? = cfUrlPtr?.reinterpret()
        val document = CGPDFDocumentCreateWithURL(cfUrlRef)
        if (cfUrlPtr != null) CFRelease(cfUrlPtr)
        if (document == null) throw Exception("Cannot create CGPDFDocument")

        val pageCount = CGPDFDocumentGetNumberOfPages(document)
        for (i in 1..pageCount.toInt()) {
            // Render on background thread using thread-safe CGBitmapContext
            val bitmap = withContext(Dispatchers.IO) {
                renderPageOffscreen(document, i)
            }
            if (bitmap != null) {
                onPageReady(bitmap)
            }
        }
    }

    @OptIn(ExperimentalForeignApi::class)
    private fun renderPageOffscreen(document: platform.CoreGraphics.CGPDFDocumentRef?, pageIndex: Int): ImageBitmap? {
        val page = CGPDFDocumentGetPage(document, pageIndex.toULong()) ?: return null
        val pageRect = CGPDFPageGetBoxRect(page, kCGPDFMediaBox)
        val width = pageRect.useContents { size.width }
        val height = pageRect.useContents { size.height }
        val widthInt = width.toInt()
        val heightInt = height.toInt()
        if (widthInt <= 0 || heightInt <= 0) return null

        val colorSpace = CGColorSpaceCreateDeviceRGB()
        val ctx = CGBitmapContextCreate(
            data = null,
            width = widthInt.toULong(),
            height = heightInt.toULong(),
            bitsPerComponent = 8u,
            bytesPerRow = (widthInt * 4).toULong(),
            space = colorSpace,
            bitmapInfo = 1u // kCGImageAlphaPremultipliedLast
        )
        CGColorSpaceRelease(colorSpace)
        if (ctx == null) return null

        CGContextSetRGBFillColor(ctx, 1.0, 1.0, 1.0, 1.0)
        CGContextFillRect(ctx, pageRect)
        CGContextDrawPDFPage(ctx, page)

        val cgImage = CGBitmapContextCreateImage(ctx)
        CFRelease(ctx)
        if (cgImage == null) return null

        // Read raw RGBA pixels directly — much faster than PNG encode/decode
        val dataProvider = CGImageGetDataProvider(cgImage)
        val cfData = if (dataProvider != null) CGDataProviderCopyData(dataProvider) else null
        CGImageRelease(cgImage)
        if (cfData == null) return null

        val ptr = CFDataGetBytePtr(cfData)
        val len = CFDataGetLength(cfData).toInt()
        val pixelBytes = ptr?.reinterpret<ByteVar>()?.readBytes(len)
        CFRelease(cfData)
        if (pixelBytes == null) return null

        val imageInfo = ImageInfo(widthInt, heightInt, ColorType.RGBA_8888, ColorAlphaType.PREMUL)
        return Image.makeRaster(imageInfo, pixelBytes, widthInt * 4).toComposeImageBitmap()
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

    @OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
    override fun saveLastOpened(uri: String) {
        defaults.setDouble(platform.posix.time(null).toDouble() * 1000, forKey = "opened_$uri")
    }

    override fun getLastOpened(uri: String): Long {
        return defaults.doubleForKey("opened_$uri").toLong()
    }
}

actual fun getReadingPositionStore(): ReadingPositionStore = IOSReadingPositionStore()