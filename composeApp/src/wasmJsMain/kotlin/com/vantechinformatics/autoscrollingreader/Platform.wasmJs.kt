package com.vantechinformatics.autoscrollingreader

class WasmPlatform: Platform {
    override val name: String = "Web with Kotlin/Wasm"
}

actual fun getPlatform(): Platform = WasmPlatform()

class WasmReadingPositionStore : ReadingPositionStore {
    private val positions = mutableMapOf<String, Pair<Int, Int>>()

    override fun savePosition(uri: String, firstVisibleIndex: Int, scrollOffset: Int) {
        positions[uri] = Pair(firstVisibleIndex, scrollOffset)
    }

    override fun getPosition(uri: String): Pair<Int, Int>? = positions[uri]
}

actual fun getReadingPositionStore(): ReadingPositionStore = WasmReadingPositionStore()

class WasmPdfTextExtractor : PdfTextExtractor {
    override suspend fun extractTextByPage(data: Any): List<String> = emptyList()
}

class WasmTtsEngine : TextToSpeechEngine {
    override fun speak(text: String, onDone: () -> Unit) { onDone() }
    override fun stop() {}
    override fun pause() {}
    override fun resume() {}
    override fun setSpeechRate(rate: Float) {}
    override fun isSpeaking(): Boolean = false
}

actual fun getPdfTextExtractor(): PdfTextExtractor = WasmPdfTextExtractor()
actual fun getTextToSpeechEngine(): TextToSpeechEngine = WasmTtsEngine()