package com.vantechinformatics.autoscrollingreader

class JsPlatform: Platform {
    override val name: String = "Web with Kotlin/JS"
}

actual fun getPlatform(): Platform = JsPlatform()

class JsReadingPositionStore : ReadingPositionStore {
    private val positions = mutableMapOf<String, Pair<Int, Int>>()
    private val lastOpened = mutableMapOf<String, Long>()

    override fun savePosition(uri: String, firstVisibleIndex: Int, scrollOffset: Int) {
        positions[uri] = Pair(firstVisibleIndex, scrollOffset)
    }

    override fun getPosition(uri: String): Pair<Int, Int>? = positions[uri]

    override fun saveLastOpened(uri: String) {
        lastOpened[uri] = kotlin.js.Date.now().toLong()
    }

    override fun getLastOpened(uri: String): Long = lastOpened[uri] ?: 0L

    private val speeds = mutableMapOf<String, Float>()
    override fun saveScrollSpeed(uri: String, speed: Float) { speeds[uri] = speed }
    override fun getScrollSpeed(uri: String): Float? = speeds[uri]
}

actual fun getReadingPositionStore(): ReadingPositionStore = JsReadingPositionStore()

// --- ANNOTATION STORE ---

class JsAnnotationStore : AnnotationStore {
    private val store = mutableMapOf<String, PdfAnnotations>()
    override fun saveAnnotations(uri: String, annotations: PdfAnnotations) { store[uri] = annotations }
    override fun getAnnotations(uri: String): PdfAnnotations = store[uri] ?: PdfAnnotations()
}

actual fun getAnnotationStore(): AnnotationStore = JsAnnotationStore()

// --- PDF TEXT EXTRACTOR ---

class JsPdfTextExtractor : PdfTextExtractor {
    override suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord> = emptyList()
}

actual fun getPdfTextExtractor(): PdfTextExtractor = JsPdfTextExtractor()

// --- CURRENT TIME ---

actual fun currentTimeMillis(): Long = kotlin.js.Date.now().toLong()