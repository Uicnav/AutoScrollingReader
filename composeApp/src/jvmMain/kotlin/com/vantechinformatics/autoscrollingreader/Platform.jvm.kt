package com.vantechinformatics.autoscrollingreader

import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.toComposeImageBitmap
import org.apache.pdfbox.Loader
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import org.apache.pdfbox.text.TextPosition
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

    private val speeds = mutableMapOf<String, Float>()
    override fun saveScrollSpeed(uri: String, speed: Float) { speeds[uri] = speed }
    override fun getScrollSpeed(uri: String): Float? = speeds[uri]
}

actual fun getReadingPositionStore(): ReadingPositionStore = JvmReadingPositionStore()

// --- ANNOTATION STORE ---

class JvmAnnotationStore : AnnotationStore {
    private val store = mutableMapOf<String, PdfAnnotations>()
    override fun saveAnnotations(uri: String, annotations: PdfAnnotations) { store[uri] = annotations }
    override fun getAnnotations(uri: String): PdfAnnotations = store[uri] ?: PdfAnnotations()
}

actual fun getAnnotationStore(): AnnotationStore = JvmAnnotationStore()

// --- PDF TEXT EXTRACTOR ---

class JvmPdfTextExtractor : PdfTextExtractor {
    override suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord> {
        val filePath = data as String
        val stream = Thread.currentThread().contextClassLoader.getResourceAsStream(filePath)
            ?: java.io.File(filePath).inputStream()
        val document = Loader.loadPDF(stream.readAllBytes())
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

// --- CURRENT TIME ---

actual fun currentTimeMillis(): Long = System.currentTimeMillis()