package com.vantechinformatics.autoscrollingreader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
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
}

actual fun getPdfLoader(): PdfLoader = DesktopPdfLoader()
class JVMPlatform: Platform {
    override val name: String = "Java ${System.getProperty("java.version")}"
}

actual fun getPlatform(): Platform = JVMPlatform()

class JvmReadingPositionStore : ReadingPositionStore {
    private val positions = mutableMapOf<String, Pair<Int, Int>>()

    override fun savePosition(uri: String, firstVisibleIndex: Int, scrollOffset: Int) {
        positions[uri] = Pair(firstVisibleIndex, scrollOffset)
    }

    override fun getPosition(uri: String): Pair<Int, Int>? = positions[uri]
}

actual fun getReadingPositionStore(): ReadingPositionStore = JvmReadingPositionStore()

// --- PDF TEXT EXTRACTION ---

class JvmPdfTextExtractor : PdfTextExtractor {
    override suspend fun extractTextByPage(data: Any): List<String> {
        val filePath = data as String
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(filePath)
            ?: File(filePath).inputStream()

        val document = PDDocument.load(stream)
        val stripper = PDFTextStripper()
        val pageTexts = mutableListOf<String>()

        for (i in 1..document.numberOfPages) {
            stripper.startPage = i
            stripper.endPage = i
            pageTexts.add(stripper.getText(document).trim())
        }

        document.close()
        return pageTexts
    }
}

actual fun getPdfTextExtractor(): PdfTextExtractor = JvmPdfTextExtractor()

// --- TEXT TO SPEECH ---

class JvmTtsEngine : TextToSpeechEngine {
    private var process: Process? = null
    private var speaking = false
    private var currentText: String? = null
    private var speechRate: Float = 1.0f

    override fun speak(text: String, onDone: () -> Unit) {
        if (text.isBlank()) {
            onDone()
            return
        }
        stop()
        currentText = text
        speaking = true

        val os = System.getProperty("os.name").lowercase()
        val command = when {
            os.contains("mac") -> listOf("say", "-r", "${(speechRate * 175).toInt()}", text)
            os.contains("linux") -> listOf("espeak", "-s", "${(speechRate * 175).toInt()}", text)
            else -> {
                speaking = false
                onDone()
                return
            }
        }

        Thread {
            try {
                val pb = ProcessBuilder(command)
                process = pb.start()
                process?.waitFor()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                speaking = false
                onDone()
            }
        }.start()
    }

    override fun stop() {
        speaking = false
        process?.destroyForcibly()
        process = null
    }

    override fun pause() {
        stop()
    }

    override fun resume() {
        val text = currentText ?: return
        speak(text) {}
    }

    override fun setSpeechRate(rate: Float) {
        speechRate = rate
    }

    override fun isSpeaking(): Boolean = speaking
}

actual fun getTextToSpeechEngine(): TextToSpeechEngine = JvmTtsEngine()