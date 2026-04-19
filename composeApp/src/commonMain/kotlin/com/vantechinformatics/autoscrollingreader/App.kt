package com.vantechinformatics.autoscrollingreader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

// === FUTURISTIC THEME: COLORS ===

private val NeonCyan = Color(0xFF00E5FF)
private val NeonCyanDim = Color(0xFF004D5A)
private val NeonPurple = Color(0xFFBB86FC)
private val NeonMagenta = Color(0xFFFF006E)
private val DeepVoid = Color(0xFF05080F)
private val DarkSurface = Color(0xFF0D1117)
private val DarkSurfaceAlt = Color(0xFF161B22)
private val SurfaceHighlight = Color(0xFF21262D)
private val TextPrimary = Color(0xFFE6EDF3)
private val TextSecondary = Color(0xFF8B949E)
private val TextDim = Color(0xFF484F58)
private val ErrorRed = Color(0xFFFF5252)
private val ErrorRedDim = Color(0xFF3D1418)

private val FuturisticDarkColorScheme = darkColorScheme(
    primary = NeonCyan,
    onPrimary = DeepVoid,
    primaryContainer = NeonCyanDim,
    onPrimaryContainer = NeonCyan,
    secondary = NeonPurple,
    onSecondary = DeepVoid,
    secondaryContainer = Color(0xFF2D1B4E),
    onSecondaryContainer = NeonPurple,
    tertiary = NeonMagenta,
    onTertiary = DeepVoid,
    background = DeepVoid,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceAlt,
    onSurfaceVariant = TextSecondary,
    error = ErrorRed,
    onError = DeepVoid,
    errorContainer = ErrorRedDim,
    onErrorContainer = ErrorRed,
    outline = NeonCyanDim,
    outlineVariant = Color(0xFF1A2332),
)

// === FUTURISTIC THEME: TYPOGRAPHY ===

private val FuturisticTypography = Typography(
    displayLarge = TextStyle(fontWeight = FontWeight.Thin, fontSize = 57.sp, lineHeight = 64.sp, letterSpacing = (-0.25).sp),
    headlineLarge = TextStyle(fontWeight = FontWeight.Light, fontSize = 32.sp, lineHeight = 40.sp, letterSpacing = 1.5.sp),
    headlineMedium = TextStyle(fontWeight = FontWeight.Light, fontSize = 28.sp, lineHeight = 36.sp, letterSpacing = 1.0.sp),
    headlineSmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 24.sp, lineHeight = 32.sp, letterSpacing = 0.8.sp),
    titleLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 22.sp, lineHeight = 28.sp, letterSpacing = 0.5.sp),
    titleMedium = TextStyle(fontWeight = FontWeight.SemiBold, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 1.2.sp),
    titleSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.8.sp),
    bodyLarge = TextStyle(fontWeight = FontWeight.Normal, fontSize = 16.sp, lineHeight = 24.sp, letterSpacing = 0.3.sp),
    bodyMedium = TextStyle(fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 0.25.sp),
    bodySmall = TextStyle(fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp),
    labelLarge = TextStyle(fontWeight = FontWeight.Medium, fontSize = 14.sp, lineHeight = 20.sp, letterSpacing = 1.5.sp),
    labelMedium = TextStyle(fontWeight = FontWeight.Medium, fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 1.2.sp),
    labelSmall = TextStyle(fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 1.0.sp),
)

// === FUTURISTIC THEME: GRADIENTS ===

private val AmbientGradient = Brush.verticalGradient(
    colors = listOf(Color(0xFF0A1628), DeepVoid, Color(0xFF0A0A14))
)

private val CyanPurpleGradient = Brush.horizontalGradient(
    colors = listOf(NeonCyan, NeonPurple)
)

private val CardBorderGradient = Brush.linearGradient(
    colors = listOf(NeonCyan.copy(alpha = 0.4f), NeonPurple.copy(alpha = 0.2f), Color.Transparent)
)

// === REUSABLE COMPOSABLES ===

@Composable
private fun FuturisticLoadingIndicator() {
    val infiniteTransition = rememberInfiniteTransition(label = "loading")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.2f, targetValue = 0.8f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "pulse"
    )
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.85f, targetValue = 1.15f,
        animationSpec = infiniteRepeatable(tween(1000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "scale"
    )
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Box(
            modifier = Modifier
                .size((60 * pulseScale).dp)
                .border(2.dp, NeonCyan.copy(alpha = pulseAlpha), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size((30 * pulseScale).dp)
                    .background(NeonCyan.copy(alpha = pulseAlpha * 0.3f), CircleShape)
            )
        }
    }
}

@Composable
private fun FuturisticEmptyState() {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .border(1.dp, CyanPurpleGradient, CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .background(NeonCyan.copy(alpha = 0.05f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.Default.FolderOpen, contentDescription = null, tint = NeonCyan.copy(alpha = 0.5f), modifier = Modifier.size(40.dp))
            }
        }
        Spacer(Modifier.height(24.dp))
        Text("NO DOCUMENTS FOUND", style = MaterialTheme.typography.labelLarge, color = TextSecondary)
        Spacer(Modifier.height(8.dp))
        Text("Tap + to import your first PDF", style = MaterialTheme.typography.bodySmall, color = TextDim)
    }
}

@Composable
private fun FuturisticTopBar(title: String) {
    Column {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurface.copy(alpha = 0.85f))
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp)
        ) {
            Text(
                text = title.uppercase(),
                style = MaterialTheme.typography.headlineMedium,
                color = NeonCyan,
                letterSpacing = 3.sp
            )
        }
        Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CyanPurpleGradient))
    }
}

@Composable
private fun StaggeredPdfCard(index: Int, pdf: PdfDocument, onClick: () -> Unit) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) {
        delay(minOf(index, 10) * 60L)
        visible = true
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(tween(300)) + slideInVertically(
            initialOffsetY = { 40 },
            animationSpec = tween(300)
        )
    ) {
        FuturisticPdfCard(pdf = pdf, onClick = onClick)
    }
}

@Composable
private fun FuturisticPdfCard(pdf: PdfDocument, onClick: () -> Unit) {
    Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(DarkSurfaceAlt, RoundedCornerShape(12.dp))
                .border(1.dp, CardBorderGradient, RoundedCornerShape(12.dp))
                .clickable(onClick = onClick)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left accent bar
            Box(
                modifier = Modifier
                    .width(3.dp)
                    .height(40.dp)
                    .background(
                        Brush.verticalGradient(listOf(NeonCyan, NeonPurple)),
                        RoundedCornerShape(2.dp)
                    )
            )
            Spacer(Modifier.width(16.dp))
            // PDF Icon with glow box
            Box(modifier = Modifier.size(44.dp), contentAlignment = Alignment.Center) {
                Box(modifier = Modifier.size(44.dp).background(NeonCyan.copy(alpha = 0.1f), RoundedCornerShape(10.dp)))
                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(pdf.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, color = TextPrimary, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text(pdf.uri, style = MaterialTheme.typography.labelSmall, color = TextDim, maxLines = 1)
            }
            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextSecondary, modifier = Modifier.size(20.dp))
        }
    }
}

// === APP ENTRY POINT ===

@Composable
fun App(externalData: Any? = null) {
    MaterialTheme(
        colorScheme = FuturisticDarkColorScheme,
        typography = FuturisticTypography
    ) {
        PermissionWrapper {
            MainContent(externalData)
        }
    }
}

@Composable
fun MainContent(externalData: Any?) {
    var currentFileUri by rememberSaveable { mutableStateOf(externalData as? String) }
    val reviewPromptManager = remember { getReviewPromptManager() }
    val coroutineScope = rememberCoroutineScope()
    var wasReading by rememberSaveable { mutableStateOf(false) }

    LaunchedEffect(externalData) {
        if (externalData != null) {
            currentFileUri = externalData as String
        }
    }

    LaunchedEffect(currentFileUri) {
        if (currentFileUri != null) {
            wasReading = true
        } else if (wasReading) {
            wasReading = false
            coroutineScope.launch {
                reviewPromptManager.requestReviewIfAppropriate()
            }
        }
    }

    if (currentFileUri != null) {
        PdfReaderScreen(
            uri = currentFileUri!!,
            onClose = { currentFileUri = null }
        )
    } else {
        LibraryScreen(onPdfSelected = { uri -> currentFileUri = uri })
    }
}

// === LIBRARY SCREEN ===

@Composable
fun LibraryScreen(onPdfSelected: (String) -> Unit) {
    val scanner = remember { getPdfScanner() }
    val importer = remember { getFileImporter() }
    val positionStore = remember { getReadingPositionStore() }
    var pdfList by remember { mutableStateOf<List<PdfDocument>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }

    LaunchedEffect(isLoading) {
        if (isLoading) {
            try {
                val allPdfs = scanner.getAllPdfs()
                pdfList = allPdfs.sortedByDescending { positionStore.getLastOpened(it.uri) }
            } catch (e: Exception) {
                println("Err: ${e.message}")
            }
            isLoading = false
        }
    }

    val filteredList = remember(pdfList, searchQuery) {
        if (searchQuery.isBlank()) pdfList
        else pdfList.filter { it.name.contains(searchQuery, ignoreCase = true) }
    }

    Box(modifier = Modifier.fillMaxSize().background(AmbientGradient)) {
        Column(modifier = Modifier.fillMaxSize()) {
            FuturisticTopBar("PDF Library")

            // Search bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface.copy(alpha = 0.6f))
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = {
                        Text("Search PDFs...", color = TextDim, style = MaterialTheme.typography.bodyMedium)
                    },
                    leadingIcon = {
                        Icon(Icons.Default.Search, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                    },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear", tint = TextSecondary, modifier = Modifier.size(20.dp))
                            }
                        }
                    },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        cursorColor = NeonCyan,
                        focusedContainerColor = DarkSurfaceAlt,
                        unfocusedContainerColor = DarkSurfaceAlt,
                        focusedIndicatorColor = NeonCyan,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Box(modifier = Modifier.fillMaxSize().weight(1f)) {
                if (isLoading) {
                    FuturisticLoadingIndicator()
                } else if (filteredList.isEmpty()) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        if (searchQuery.isNotEmpty()) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.SearchOff, contentDescription = null, tint = TextDim, modifier = Modifier.size(48.dp))
                                Spacer(Modifier.height(12.dp))
                                Text("No results for \"$searchQuery\"", style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                        } else {
                            FuturisticEmptyState()
                        }
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(top = 12.dp, bottom = 88.dp)
                    ) {
                        itemsIndexed(filteredList) { _, pdf ->
                            FuturisticPdfCard(pdf = pdf, onClick = {
                                positionStore.saveLastOpened(pdf.uri)
                                onPdfSelected(pdf.uri)
                            })
                        }
                    }
                }
            }
        }

        // FAB — scan storage on Android, import files on iOS
        FloatingActionButton(
            onClick = {
                if (importer.isManualImportSupported) {
                    importer.pickFile { success ->
                        if (success) isLoading = true
                    }
                } else {
                    isLoading = true
                }
            },
            containerColor = NeonCyan,
            contentColor = DeepVoid,
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(24.dp)
                .navigationBarsPadding()
        ) {
            Icon(
                if (importer.isManualImportSupported) Icons.Default.Add else Icons.Default.DocumentScanner,
                contentDescription = if (importer.isManualImportSupported) "Import" else "Scan"
            )
        }
    }
}

// === PDF READER SCREEN ===

@Composable
fun PdfReaderScreen(uri: String, onClose: () -> Unit) {
    val pdfLoader = remember { getPdfLoader() }
    val positionStore = remember { getReadingPositionStore() }
    var pages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    var isScrolling by remember { mutableStateOf(false) }
    val savedSpeed = remember { positionStore.getScrollSpeed(uri) }
    var scrollSpeed by remember { mutableFloatStateOf(savedSpeed ?: 2f) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var showToggleIcon by remember { mutableStateOf(false) }
    var showEndMessage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var positionRestored by remember { mutableStateOf(false) }
    var autoScrollActive by remember { mutableStateOf(false) }
    var hideJob by remember { mutableStateOf<Job?>(null) }
    val sessionStartMs = remember(uri) { currentTimeMillis() }

    // Annotation state
    val annotationStore = remember { getAnnotationStore() }
    var annotations by remember { mutableStateOf(annotationStore.getAnnotations(uri)) }
    val currentPageIndex by remember {
        derivedStateOf { listState.firstVisibleItemIndex }
    }
    val isCurrentPageBookmarked by remember {
        derivedStateOf { annotations.bookmarks.any { it.pageIndex == currentPageIndex } }
    }

    // Selection state
    var selectionMode by remember { mutableStateOf(false) }
    var selectionPageIndex by remember { mutableIntStateOf(-1) }
    var selectionStartIdx by remember { mutableIntStateOf(-1) }
    var selectionEndIdx by remember { mutableIntStateOf(-1) }
    var cachedWords by remember { mutableStateOf<Map<Int, List<PositionedWord>>>(emptyMap()) }
    val textExtractor = remember { getPdfTextExtractor() }

    // Annotations panel
    var showAnnotationsPanel by remember { mutableStateOf(false) }

    val effectiveSpeed = remember { mutableFloatStateOf(0f) }
    val targetSpeed = if (isScrolling) scrollSpeed else 0f
    val animatedSpeed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = tween(durationMillis = 500),
        label = "scrollSpeed"
    )

    SideEffect { effectiveSpeed.floatValue = animatedSpeed }

    fun startHideTimer() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(3000)
            areControlsVisible = false
        }
    }

    fun saveCurrentPosition() {
        if (pages.isNotEmpty()) {
            positionStore.savePosition(uri, listState.firstVisibleItemIndex, listState.firstVisibleItemScrollOffset)
        }
    }

    fun toggleBookmark() {
        val page = currentPageIndex
        val updated = if (annotations.bookmarks.any { it.pageIndex == page }) {
            annotations.copy(bookmarks = annotations.bookmarks.filter { it.pageIndex != page })
        } else {
            annotations.copy(bookmarks = annotations.bookmarks + Bookmark(page, currentTimeMillis()))
        }
        annotations = updated
        annotationStore.saveAnnotations(uri, updated)
    }

    fun findWordAtPosition(words: List<PositionedWord>, normX: Float, normY: Float): Int {
        var bestIdx = -1
        var bestDist = Float.MAX_VALUE
        words.forEachIndexed { idx, word ->
            val cx = word.rect.x + word.rect.width / 2
            val cy = word.rect.y + word.rect.height / 2
            val dist = (normX - cx) * (normX - cx) + (normY - cy) * (normY - cy)
            if (dist < bestDist) {
                bestDist = dist
                bestIdx = idx
            }
        }
        return if (bestDist < 0.04f) bestIdx else -1
    }

    // Load PDF progressively — pages appear as they're rendered
    LaunchedEffect(uri) {
        isLoading = true
        try {
            pdfLoader.loadPdfProgressively(uri) { bitmap ->
                pages = pages + bitmap
                if (isLoading) isLoading = false
            }
        } catch (e: Exception) {
            errorMessage = "Cannot open file: ${e.message}"
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // Restore reading position, then auto-start scrolling
    LaunchedEffect(pages) {
        if (pages.isNotEmpty() && !positionRestored) {
            val saved = positionStore.getPosition(uri)
            if (saved != null) {
                val (index, offset) = saved
                if (index < pages.size) listState.scrollToItem(index, offset)
            }
            positionRestored = true
            isScrolling = true
        }
    }

    // Smooth scroll loop
    LaunchedEffect(isScrolling) {
        if (isScrolling && pages.isNotEmpty()) {
            autoScrollActive = true
            while (isActive) {
                val speed = effectiveSpeed.floatValue
                if (speed > 0.01f) listState.scrollBy(speed * 0.3f)
                delay(16)
            }
            autoScrollActive = false
        }
    }

    // Detect manual scroll
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }.collect { inProgress ->
            if (inProgress && isScrolling && !autoScrollActive) {
                isScrolling = false
                areControlsVisible = true
                saveCurrentPosition()
            }
        }
    }

    // End-of-document detection
    LaunchedEffect(listState) {
        snapshotFlow {
            val layoutInfo = listState.layoutInfo
            val lastVisible = layoutInfo.visibleItemsInfo.lastOrNull()
            lastVisible != null && lastVisible.index == layoutInfo.totalItemsCount - 1 &&
                lastVisible.offset + lastVisible.size <= layoutInfo.viewportEndOffset
        }.collect { atEnd ->
            if (atEnd && isScrolling) {
                isScrolling = false
                showEndMessage = true
                areControlsVisible = true
                saveCurrentPosition()
                delay(3000)
                showEndMessage = false
            }
        }
    }

    // Auto-hide controls
    LaunchedEffect(isScrolling) {
        if (isScrolling) startHideTimer()
        else {
            hideJob?.cancel()
            areControlsVisible = true
            saveCurrentPosition()
        }
    }

    DisposableEffect(uri) {
        onDispose {
            saveCurrentPosition()
            val endMs = currentTimeMillis()
            ReviewEligibilityTracker(getReviewStateStore()).recordSession(sessionStartMs, endMs)
        }
    }

    LaunchedEffect(showToggleIcon) {
        if (showToggleIcon) { delay(800); showToggleIcon = false }
    }

    // Progress state
    val progress by remember {
        derivedStateOf {
            if (pages.isEmpty()) 0f
            else (listState.firstVisibleItemIndex.toFloat() / pages.size.toFloat()).coerceIn(0f, 1f)
        }
    }
    val animatedProgress by animateFloatAsState(targetValue = progress, animationSpec = tween(300), label = "progress")

    // === READER UI ===
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DeepVoid)
            .drawBehind {
                // Top vignette
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color(0xFF0A1628).copy(alpha = 0.8f), Color.Transparent),
                        startY = 0f, endY = size.height * 0.08f
                    )
                )
                // Bottom vignette
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color(0xFF0A0A14).copy(alpha = 0.8f)),
                        startY = size.height * 0.92f, endY = size.height
                    )
                )
            }
    ) {
        if (errorMessage != null) {
            // Error state
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier.size(64.dp).border(1.dp, ErrorRed.copy(alpha = 0.5f), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(Icons.Default.ErrorOutline, contentDescription = null, tint = ErrorRed, modifier = Modifier.size(36.dp))
                }
                Spacer(Modifier.height(16.dp))
                Text(
                    errorMessage!!,
                    color = TextSecondary,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(horizontal = 32.dp)
                )
                Spacer(Modifier.height(20.dp))
                OutlinedButton(
                    onClick = onClose,
                    border = androidx.compose.foundation.BorderStroke(1.dp, NeonCyan.copy(alpha = 0.5f)),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = NeonCyan)
                ) {
                    Text("BACK TO LIBRARY")
                }
            }
        } else if (isLoading) {
            FuturisticLoadingIndicator()
        } else {
            // PDF content with tap-to-toggle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(selectionMode) {
                        detectTapGestures(
                            onTap = {
                                if (selectionMode) {
                                    selectionMode = false
                                    selectionPageIndex = -1
                                    selectionStartIdx = -1
                                    selectionEndIdx = -1
                                } else {
                                    isScrolling = !isScrolling
                                    showToggleIcon = true
                                }
                            },
                            onDoubleTap = {
                                if (!selectionMode) {
                                    areControlsVisible = !areControlsVisible
                                    if (areControlsVisible && isScrolling) startHideTimer()
                                }
                            },
                            onLongPress = { offset ->
                                if (!selectionMode) {
                                    val layoutInfo = listState.layoutInfo
                                    var targetPage = -1
                                    var localY = 0f

                                    for (item in layoutInfo.visibleItemsInfo) {
                                        val itemTop = item.offset.toFloat()
                                        val itemBottom = (item.offset + item.size).toFloat()
                                        if (offset.y >= itemTop && offset.y < itemBottom) {
                                            targetPage = item.index
                                            localY = offset.y - itemTop
                                            break
                                        }
                                    }

                                    if (targetPage >= 0 && targetPage < pages.size) {
                                        val pageItem = layoutInfo.visibleItemsInfo.find { it.index == targetPage }
                                        if (pageItem != null) {
                                            val viewportWidth = layoutInfo.viewportSize.width.toFloat()
                                            val normX = offset.x / viewportWidth
                                            val normY = localY / pageItem.size.toFloat()

                                            coroutineScope.launch {
                                                val words = cachedWords[targetPage]
                                                    ?: textExtractor.extractWords(uri, targetPage).also {
                                                        cachedWords = cachedWords + (targetPage to it)
                                                    }

                                                if (words.isNotEmpty()) {
                                                    val wordIdx = findWordAtPosition(words, normX, normY)
                                                    if (wordIdx >= 0) {
                                                        isScrolling = false
                                                        selectionMode = true
                                                        selectionPageIndex = targetPage
                                                        selectionStartIdx = wordIdx
                                                        selectionEndIdx = wordIdx
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        )
                    }
            ) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 140.dp, top = 60.dp)
                ) {
                    val pageList = pages
                    itemsIndexed(pageList) { index, pageBitmap ->
                        Box(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Image(
                                contentScale = ContentScale.Crop,
                                bitmap = pageBitmap,
                                contentDescription = null,
                                modifier = Modifier.fillMaxWidth()
                            )
                            // Saved highlight overlays
                            val pageHighlights = annotations.highlights.filter { it.pageIndex == index }
                            if (pageHighlights.isNotEmpty()) {
                                Canvas(modifier = Modifier.matchParentSize()) {
                                    for (highlight in pageHighlights) {
                                        for (rect in highlight.rects) {
                                            drawRect(
                                                color = NeonCyan.copy(alpha = 0.2f),
                                                topLeft = Offset(rect.x * size.width, rect.y * size.height),
                                                size = Size(rect.width * size.width, rect.height * size.height)
                                            )
                                        }
                                    }
                                }
                            }
                            // Selection overlay
                            if (selectionMode && selectionPageIndex == index && selectionStartIdx >= 0) {
                                val words = cachedWords[index] ?: emptyList()
                                if (words.isNotEmpty()) {
                                    val startIdx = minOf(selectionStartIdx, selectionEndIdx).coerceAtLeast(0)
                                    val endIdx = maxOf(selectionStartIdx, selectionEndIdx).coerceAtMost(words.size - 1)
                                    val selectedWords = words.subList(startIdx, endIdx + 1)
                                    Canvas(modifier = Modifier.matchParentSize()) {
                                        for (word in selectedWords) {
                                            drawRect(
                                                color = NeonCyan.copy(alpha = 0.25f),
                                                topLeft = Offset(word.rect.x * size.width, word.rect.y * size.height),
                                                size = Size(word.rect.width * size.width, word.rect.height * size.height)
                                            )
                                        }
                                    }
                                }
                            }
                            // Bookmark indicator
                            if (annotations.bookmarks.any { it.pageIndex == index }) {
                                Icon(
                                    Icons.Default.Bookmark,
                                    contentDescription = null,
                                    tint = NeonCyan,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                        .size(24.dp)
                                )
                            }
                            // Highlight indicator (left accent bar)
                            if (pageHighlights.isNotEmpty()) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.CenterStart)
                                        .width(3.dp)
                                        .fillMaxHeight()
                                        .background(
                                            Brush.verticalGradient(listOf(NeonCyan, NeonPurple)),
                                            RoundedCornerShape(2.dp)
                                        )
                                )
                            }
                        }
                    }
                }
            }

            // Selection floating toolbar
            if (selectionMode && selectionPageIndex >= 0) {
                val words = cachedWords[selectionPageIndex] ?: emptyList()
                if (words.isNotEmpty()) {
                    val startIdx = minOf(selectionStartIdx, selectionEndIdx).coerceAtLeast(0)
                    val endIdx = maxOf(selectionStartIdx, selectionEndIdx).coerceAtMost(words.size - 1)

                    Box(
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .padding(top = 80.dp)
                            .statusBarsPadding()
                    ) {
                        Row(
                            modifier = Modifier
                                .background(DarkSurface.copy(alpha = 0.95f), RoundedCornerShape(12.dp))
                                .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = {
                                if (startIdx >= 0 && endIdx < words.size) {
                                    val selectedText = words.subList(startIdx, endIdx + 1).joinToString(" ") { it.text }
                                    val rects = words.subList(startIdx, endIdx + 1).map { it.rect }
                                    val highlight = Highlight(
                                        pageIndex = selectionPageIndex,
                                        text = selectedText,
                                        rects = rects,
                                        createdAt = currentTimeMillis()
                                    )
                                    val updated = annotations.copy(highlights = annotations.highlights + highlight)
                                    annotations = updated
                                    annotationStore.saveAnnotations(uri, updated)
                                }
                                selectionMode = false
                                selectionPageIndex = -1
                                selectionStartIdx = -1
                                selectionEndIdx = -1
                            }) {
                                Icon(Icons.Default.Highlight, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                Spacer(Modifier.width(4.dp))
                                Text("HIGHLIGHT", color = NeonCyan, style = MaterialTheme.typography.labelMedium)
                            }
                            Box(Modifier.width(1.dp).height(24.dp).background(TextDim))
                            TextButton(onClick = {
                                selectionMode = false
                                selectionPageIndex = -1
                                selectionStartIdx = -1
                                selectionEndIdx = -1
                            }) {
                                Text("CANCEL", color = TextSecondary, style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                }
            }

            // Play/Pause toggle icon overlay — neon ring
            AnimatedVisibility(
                visible = showToggleIcon,
                enter = fadeIn(tween(150)) + scaleIn(initialScale = 0.7f, animationSpec = tween(200, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(400)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(modifier = Modifier.size(100.dp), contentAlignment = Alignment.Center) {
                    // Outer neon ring
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .border(
                                2.dp,
                                Brush.linearGradient(listOf(NeonCyan, NeonPurple, NeonCyan)),
                                CircleShape
                            )
                    )
                    // Inner dark circle
                    Box(
                        modifier = Modifier
                            .size(80.dp)
                            .background(DeepVoid.copy(alpha = 0.85f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isScrolling) Icons.Default.PlayArrow else Icons.Default.Pause,
                            contentDescription = null,
                            tint = NeonCyan,
                            modifier = Modifier.size(40.dp)
                        )
                    }
                }
            }

            // End of document message
            AnimatedVisibility(
                visible = showEndMessage,
                enter = fadeIn() + scaleIn(initialScale = 0.9f),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .background(DarkSurface.copy(alpha = 0.9f), RoundedCornerShape(16.dp))
                        .border(1.dp, CyanPurpleGradient, RoundedCornerShape(16.dp))
                        .padding(horizontal = 32.dp, vertical = 20.dp)
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(Icons.Default.AutoStories, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(32.dp))
                        Spacer(Modifier.height(8.dp))
                        Text("END OF DOCUMENT", style = MaterialTheme.typography.labelLarge, color = TextPrimary)
                    }
                }
            }
        }

        // Glassmorphic back button
        Box(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .statusBarsPadding()
                .background(DarkSurface.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable {
                    saveCurrentPosition()
                    onClose()
                }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                @Suppress("DEPRECATION")
                Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = NeonCyan, modifier = Modifier.size(20.dp))
                Spacer(Modifier.width(6.dp))
                Text("BACK", style = MaterialTheme.typography.labelMedium, color = NeonCyan)
            }
        }

        // Annotations panel button
        val annotationCount = annotations.bookmarks.size + annotations.highlights.size
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
                .statusBarsPadding()
                .background(DarkSurface.copy(alpha = 0.7f), RoundedCornerShape(12.dp))
                .border(1.dp, NeonCyan.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                .clickable { showAnnotationsPanel = true }
                .padding(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.CollectionsBookmark, contentDescription = "Annotations", tint = NeonCyan, modifier = Modifier.size(20.dp))
                if (annotationCount > 0) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        "$annotationCount",
                        style = MaterialTheme.typography.labelSmall,
                        color = NeonCyan
                    )
                }
            }
        }

        // Auto-hiding control bar
        if (!isLoading && errorMessage == null) {
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    // Top accent line
                    Box(modifier = Modifier.fillMaxWidth().height(1.dp).background(CyanPurpleGradient))

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(DarkSurface.copy(alpha = 0.88f))
                            .navigationBarsPadding()
                            .padding(horizontal = 20.dp, vertical = 16.dp)
                            .pointerInput(Unit) {
                                detectTapGestures { if (isScrolling) startHideTimer() }
                            }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            // Play/Pause with neon circle border
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(1.5.dp, if (isScrolling) NeonCyan else TextDim, CircleShape)
                                    .clickable {
                                        isScrolling = !isScrolling
                                        showToggleIcon = true
                                    },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = if (isScrolling) "Pause" else "Play",
                                    tint = if (isScrolling) NeonCyan else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Bookmark button
                            Box(
                                modifier = Modifier
                                    .size(48.dp)
                                    .border(
                                        1.5.dp,
                                        if (isCurrentPageBookmarked) NeonCyan else TextDim,
                                        CircleShape
                                    )
                                    .clickable { toggleBookmark() },
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = if (isCurrentPageBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                                    contentDescription = if (isCurrentPageBookmarked) "Remove bookmark" else "Add bookmark",
                                    tint = if (isCurrentPageBookmarked) NeonCyan else TextSecondary,
                                    modifier = Modifier.size(24.dp)
                                )
                            }

                            // Digital speed display
                            Box(
                                modifier = Modifier
                                    .background(SurfaceHighlight, RoundedCornerShape(8.dp))
                                    .border(1.dp, NeonCyan.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        scrollSpeed = 2f
                                        positionStore.saveScrollSpeed(uri, 2f)
                                        if (isScrolling) startHideTimer()
                                    }
                                    .padding(horizontal = 16.dp, vertical = 8.dp)
                            ) {
                                Text(
                                    text = "${(scrollSpeed * 10).toInt() / 10f}x",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = NeonCyan
                                )
                            }
                        }

                        Spacer(Modifier.height(12.dp))

                        // Speed slider
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("SLOW", style = MaterialTheme.typography.labelSmall, color = TextDim)
                            Slider(
                                value = scrollSpeed,
                                onValueChange = {
                                    scrollSpeed = it
                                    positionStore.saveScrollSpeed(uri, it)
                                    if (isScrolling) startHideTimer()
                                },
                                valueRange = 0.5f..15f,
                                steps = 28,
                                modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
                                colors = SliderDefaults.colors(
                                    thumbColor = NeonCyan,
                                    activeTrackColor = NeonCyan,
                                    activeTickColor = NeonCyan.copy(alpha = 0.6f),
                                    inactiveTrackColor = SurfaceHighlight,
                                    inactiveTickColor = TextDim.copy(alpha = 0.3f)
                                )
                            )
                            Text("FAST", style = MaterialTheme.typography.labelSmall, color = TextDim)
                        }

                        Spacer(Modifier.height(8.dp))

                        // Progress bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(3.dp)
                                .background(SurfaceHighlight, RoundedCornerShape(2.dp))
                        ) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(animatedProgress)
                                    .height(3.dp)
                                    .background(CyanPurpleGradient, RoundedCornerShape(2.dp))
                            )
                        }
                    }
                }
            }

            // Bottom edge tap zone
            if (!areControlsVisible) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(60.dp)
                        .pointerInput(Unit) {
                            detectTapGestures {
                                areControlsVisible = true
                                if (isScrolling) startHideTimer()
                            }
                        }
                )
            }
        }

        // Annotations panel bottom sheet
        if (showAnnotationsPanel) {
            @OptIn(ExperimentalMaterial3Api::class)
            ModalBottomSheet(
                onDismissRequest = { showAnnotationsPanel = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = DarkSurface,
                contentColor = TextPrimary,
                dragHandle = {
                    Box(Modifier.fillMaxWidth().height(1.dp).background(CyanPurpleGradient))
                }
            ) {
                var activeTab by remember { mutableIntStateOf(0) }

                Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("ANNOTATIONS", style = MaterialTheme.typography.titleMedium, color = NeonCyan)
                        IconButton(onClick = { showAnnotationsPanel = false }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                        }
                    }

                    Spacer(Modifier.height(12.dp))

                    // Tabs
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("BOOKMARKS" to annotations.bookmarks.size, "HIGHLIGHTS" to annotations.highlights.size).forEachIndexed { idx, (label, count) ->
                            val isActive = activeTab == idx
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .background(
                                        if (isActive) NeonCyan.copy(alpha = 0.15f) else SurfaceHighlight,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .border(
                                        1.dp,
                                        if (isActive) NeonCyan.copy(alpha = 0.5f) else Color.Transparent,
                                        RoundedCornerShape(20.dp)
                                    )
                                    .clickable { activeTab = idx }
                                    .padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("$label ($count)", style = MaterialTheme.typography.labelMedium, color = if (isActive) NeonCyan else TextSecondary)
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))

                    // Content
                    LazyColumn(modifier = Modifier.fillMaxWidth().heightIn(max = 400.dp)) {
                        if (activeTab == 0) {
                            val sortedBookmarks = annotations.bookmarks.sortedBy { it.pageIndex }
                            if (sortedBookmarks.isEmpty()) {
                                item {
                                    Text("No bookmarks yet", style = MaterialTheme.typography.bodyMedium, color = TextDim,
                                        modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                                }
                            } else {
                                items(sortedBookmarks.size) { idx ->
                                    val bookmark = sortedBookmarks[idx]
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch { listState.scrollToItem(bookmark.pageIndex) }
                                                isScrolling = false
                                                showAnnotationsPanel = false
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Bookmark, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Text("Page ${bookmark.pageIndex + 1}", style = MaterialTheme.typography.bodyLarge, color = TextPrimary, modifier = Modifier.weight(1f))
                                        IconButton(onClick = {
                                            val updated = annotations.copy(bookmarks = annotations.bookmarks.filter { it.pageIndex != bookmark.pageIndex })
                                            annotations = updated
                                            annotationStore.saveAnnotations(uri, updated)
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextDim, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        } else {
                            val sortedHighlights = annotations.highlights.sortedBy { it.pageIndex }
                            if (sortedHighlights.isEmpty()) {
                                item {
                                    Text("No highlights yet", style = MaterialTheme.typography.bodyMedium, color = TextDim,
                                        modifier = Modifier.padding(vertical = 24.dp).fillMaxWidth(), textAlign = TextAlign.Center)
                                }
                            } else {
                                items(sortedHighlights.size) { idx ->
                                    val highlight = sortedHighlights[idx]
                                    Row(
                                        modifier = Modifier.fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch { listState.scrollToItem(highlight.pageIndex) }
                                                isScrolling = false
                                                showAnnotationsPanel = false
                                            }
                                            .padding(vertical = 8.dp, horizontal = 4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Icon(Icons.Default.Highlight, contentDescription = null, tint = NeonCyan, modifier = Modifier.size(20.dp))
                                        Spacer(Modifier.width(12.dp))
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text("Page ${highlight.pageIndex + 1}", style = MaterialTheme.typography.labelSmall, color = TextSecondary)
                                            Text(
                                                highlight.text.take(60) + if (highlight.text.length > 60) "..." else "",
                                                style = MaterialTheme.typography.bodyMedium, color = TextPrimary, maxLines = 2
                                            )
                                        }
                                        IconButton(onClick = {
                                            val updated = annotations.copy(highlights = annotations.highlights.filter { it != highlight })
                                            annotations = updated
                                            annotationStore.saveAnnotations(uri, updated)
                                        }) {
                                            Icon(Icons.Default.Delete, contentDescription = "Remove", tint = TextDim, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    }
}
