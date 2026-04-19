package com.vantechinformatics.autoscrollingreader

import androidx.compose.ui.graphics.ImageBitmap

import androidx.compose.runtime.Composable
import kotlinx.serialization.Serializable
interface FileImporter {
    val isManualImportSupported: Boolean
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
    suspend fun loadPdfProgressively(data: Any, onPageReady: (ImageBitmap) -> Unit)
}

expect fun getPdfLoader(): PdfLoader

interface Platform {
    val name: String
}

expect fun getPlatform(): Platform

interface ReadingPositionStore {
    fun savePosition(uri: String, firstVisibleIndex: Int, scrollOffset: Int)
    fun getPosition(uri: String): Pair<Int, Int>?
    fun saveLastOpened(uri: String)
    fun getLastOpened(uri: String): Long
    fun saveScrollSpeed(uri: String, speed: Float)
    fun getScrollSpeed(uri: String): Float?
}

expect fun getReadingPositionStore(): ReadingPositionStore

// --- ANNOTATIONS ---

@Serializable
data class TextRect(
    val x: Float, val y: Float,
    val width: Float, val height: Float
)

@Serializable
data class Bookmark(
    val pageIndex: Int,
    val createdAt: Long
)

@Serializable
data class Highlight(
    val pageIndex: Int,
    val text: String,
    val rects: List<TextRect>,
    val createdAt: Long
)

@Serializable
data class PdfAnnotations(
    val bookmarks: List<Bookmark> = emptyList(),
    val highlights: List<Highlight> = emptyList()
)

interface AnnotationStore {
    fun saveAnnotations(uri: String, annotations: PdfAnnotations)
    fun getAnnotations(uri: String): PdfAnnotations
}

expect fun getAnnotationStore(): AnnotationStore

// --- TEXT EXTRACTION ---

data class PositionedWord(
    val text: String,
    val rect: TextRect
)

interface PdfTextExtractor {
    suspend fun extractWords(data: Any, pageIndex: Int): List<PositionedWord>
}

expect fun getPdfTextExtractor(): PdfTextExtractor

// --- CROSS-PLATFORM TIME ---

expect fun currentTimeMillis(): Long

// --- IN-APP REVIEW ---

interface ReviewStateStore {
    fun getSessionCount(): Int
    fun setSessionCount(value: Int)
    fun getDistinctDays(): List<Long>
    fun setDistinctDays(days: List<Long>)
    fun getLastPromptMs(): Long
    fun setLastPromptMs(value: Long)
}

expect fun getReviewStateStore(): ReviewStateStore

interface ReviewPromptManager {
    suspend fun requestReviewIfAppropriate()
}

expect fun getReviewPromptManager(): ReviewPromptManager