package com.vantechinformatics.autoscrollingreader

import androidx.compose.ui.graphics.ImageBitmap

import androidx.compose.runtime.Composable
interface FileImporter {
    // Deschide fereastra de sistem și returnează true dacă s-a importat ceva
    fun pickFile(onResult: (Boolean) -> Unit)
}

expect fun getFileImporter(): FileImporter

// Definim un Composable "expect"
// Pe Android va verifica permisiunea. Pe iOS/Desktop va afișa direct conținutul.
expect fun checkStoragePermission(): Boolean

@Composable
expect fun PermissionWrapper(
    content: @Composable () -> Unit
)

data class PdfDocument(
    val name: String,
    val uri: String // Path sau Uri string
)

interface PdfScanner {
    suspend fun getAllPdfs(): List<PdfDocument>
}

expect fun getPdfScanner(): PdfScanner

interface PdfLoader {
    suspend fun loadPdf(data: Any): List<ImageBitmap>
}

expect fun getPdfLoader(): PdfLoader

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

interface ReadingPositionStore {
    fun savePosition(uri: String, firstVisibleIndex: Int, scrollOffset: Int)
    fun getPosition(uri: String): Pair<Int, Int>?
}

expect fun getReadingPositionStore(): ReadingPositionStore

interface PdfTextExtractor {
    suspend fun extractTextByPage(data: Any): List<String>
}

interface TextToSpeechEngine {
    fun speak(text: String, onDone: () -> Unit)
    fun stop()
    fun pause()
    fun resume()
    fun setSpeechRate(rate: Float)
    fun isSpeaking(): Boolean
}

expect fun getPdfTextExtractor(): PdfTextExtractor
expect fun getTextToSpeechEngine(): TextToSpeechEngine