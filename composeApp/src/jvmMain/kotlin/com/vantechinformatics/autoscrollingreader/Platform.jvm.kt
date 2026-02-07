package com.vantechinformatics.autoscrollingreader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import java.io.File

class DesktopPdfLoader : PdfLoader {
    override suspend fun loadPdf(data: Any): List<ImageBitmap> {
        val filePath = data as String
        // Presupunem că fișierul este în resurse sau un path absolut.
        // Pentru demo încărcăm din resources folderul proiectului (src/commonMain/resources)
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(filePath)
            ?: File(filePath).inputStream()

        val document = PDDocument()
        val renderer = PDFRenderer(document)
        val images = mutableListOf<ImageBitmap>()

        // Randăm fiecare pagină la 150 DPI (calitate medie spre bună)
        for (page in 0 until document.numberOfPages) {
            val bufferedImage = renderer.renderImageWithDPI(page, 150f)
            images.add(bufferedImage.toComposeImageBitmap())
        }

        document.close()
        return images
    }

    override suspend fun loadPdfProgressively(data: Any, onPageReady: (ImageBitmap) -> Unit) {
        loadPdf(data).forEach { onPageReady(it) }
    }
}

actual fun getPdfLoader(): PdfLoader = DesktopPdfLoader()
class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

class JvmReadingPositionStore : ReadingPositionStore {
    private val positions = mutableMapOf<String, Pair<Int, Int>>()
    private val lastOpened = mutableMapOf<String, Long>()

    override fun savePosition(uri: String, firstVisibleIndex: Int, scrollOffset: Int) {
        positions[uri] = Pair(firstVisibleIndex, scrollOffset)
    }

    override fun getPosition(uri: String): Pair<Int, Int>? = positions[uri]

    override fun saveLastOpened(uri: String) {
        lastOpened[uri] = System.currentTimeMillis()
    }

    override fun getLastOpened(uri: String): Long = lastOpened[uri] ?: 0L
}

actual fun getReadingPositionStore(): ReadingPositionStore = JvmReadingPositionStore()