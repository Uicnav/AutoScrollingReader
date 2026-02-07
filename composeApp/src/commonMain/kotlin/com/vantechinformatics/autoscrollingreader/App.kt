package com.vantechinformatics.autoscrollingreader
// Asigură-te că pui pachetul corect dacă e cazul, ex: package com.vantechinformatics.autoscrollingreader

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

@Composable
fun App(externalData: Any? = null) {
    MaterialTheme {
        PermissionWrapper {
            MainContent(externalData)
        }
    }
}

@Composable
fun MainContent(externalData: Any?) {
    // Starea de navigație
    var currentFileUri by remember { mutableStateOf(externalData as? String) }

    // Dacă primim un fișier din "Open With" (ex: WhatsApp), îl deschidem imediat
    LaunchedEffect(externalData) {
        if (externalData != null) {
            currentFileUri = externalData as String
        }
    }

    if (currentFileUri != null) {
        // --- ECRAN CITIRE ---
        PdfReaderScreen(
            uri = currentFileUri!!,
            onClose = {
                // Dacă e null (mod librărie), ne întoarcem la listă.
                // Dacă externalData există, aplicația se va închide oricum de la butonul Back din sistemul Android.
                currentFileUri = null
            }
        )
    } else {
        // --- ECRAN LIBRĂRIE ---
        LibraryScreen(onPdfSelected = { uri ->
            currentFileUri = uri
        })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LibraryScreen(onPdfSelected: (String) -> Unit) {
    val scanner = remember { getPdfScanner() }
    val importer = remember { getFileImporter() } // <--- 1. Initializam Importer-ul

    var pdfList by remember { mutableStateOf<List<PdfDocument>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }

    // Funcție pentru reîmprospătarea listei
    fun refreshList() {
        isLoading = true
        // Folosim un scope temporar sau LaunchedEffect e mai complicat la click,
        // dar pentru simplitate setăm isLoading care va triggerui LaunchedEffect-ul de jos
        // Nota: În producție folosește ViewModel, aici facem un truc rapid:
    }

    // Se re-execută când isLoading devine true
    LaunchedEffect(isLoading) {
        if (isLoading) {
            try {
                pdfList = scanner.getAllPdfs()
            } catch (e: Exception) {
                println("Err: ${e.message}")
            }
            isLoading = false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Librăria PDF") })
        },
        // --- 2. ADAUGĂM BUTONUL DE IMPORT AICI ---
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    // Când apăsăm butonul, deschidem picker-ul
                    importer.pickFile { success ->
                        if (success) {
                            // Dacă userul a ales fișiere pe iOS, reîncărcăm lista
                            isLoading = true
                        }
                    }
                }
            ) {
                Icon(Icons.Default.Add, contentDescription = "Import")
            }
        }
    ) { padding ->
        Box(modifier = Modifier.fillMaxSize().padding(padding)) {
            if (isLoading) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            } else if (pdfList.isEmpty()) {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("Nu am găsit PDF-uri.")
                    Spacer(Modifier.height(8.dp))
                    Text("Apasă + pentru a adăuga manual.", style = MaterialTheme.typography.bodySmall)
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    items(pdfList) { pdf ->
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(8.dp)
                                .clickable { onPdfSelected(pdf.uri) },
                            elevation = CardDefaults.cardElevation(4.dp)
                        ) {
                            Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.PictureAsPdf, contentDescription = null, tint = Color.Red)
                                Spacer(modifier = Modifier.width(16.dp))
                                Column {
                                    Text(pdf.name, fontWeight = FontWeight.Bold)
                                    // Afișăm calea (opțional, pentru debug)
                                    Text(pdf.uri, style = MaterialTheme.typography.labelSmall, maxLines = 1)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PdfReaderScreen(uri: String, onClose: () -> Unit) {
    val pdfLoader = remember { getPdfLoader() }
    val positionStore = remember { getReadingPositionStore() }
    var pages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll control state
    var isScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(2f) }
    var areControlsVisible by remember { mutableStateOf(true) }
    var showToggleIcon by remember { mutableStateOf(false) }
    var showEndMessage by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var positionRestored by remember { mutableStateOf(false) }

    // Track whether auto-scroll initiated the current scroll
    var autoScrollActive by remember { mutableStateOf(false) }

    // Controls auto-hide timer
    var hideJob by remember { mutableStateOf<Job?>(null) }

    // Effective scroll speed — eased from 0 to target and back
    val effectiveSpeed = remember { mutableFloatStateOf(0f) }

    // Smooth easing for scroll speed (drives the animation)
    val targetSpeed = if (isScrolling) scrollSpeed else 0f
    val animatedSpeed by animateFloatAsState(
        targetValue = targetSpeed,
        animationSpec = tween(durationMillis = 500),
        label = "scrollSpeed"
    )

    // Sync effective speed after each recomposition
    SideEffect {
        effectiveSpeed.floatValue = animatedSpeed
    }

    // Helper: start auto-hide timer for controls
    fun startHideTimer() {
        hideJob?.cancel()
        hideJob = coroutineScope.launch {
            delay(3000)
            areControlsVisible = false
        }
    }

    // Helper: save current reading position
    fun saveCurrentPosition() {
        if (pages.isNotEmpty()) {
            positionStore.savePosition(
                uri,
                listState.firstVisibleItemIndex,
                listState.firstVisibleItemScrollOffset
            )
        }
    }

    // Load PDF
    LaunchedEffect(uri) {
        isLoading = true
        try {
            pages = pdfLoader.loadPdf(uri)
        } catch (e: Exception) {
            errorMessage = "Nu pot deschide fișierul: ${e.message}"
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // Restore reading position after pages load
    LaunchedEffect(pages) {
        if (pages.isNotEmpty() && !positionRestored) {
            val saved = positionStore.getPosition(uri)
            if (saved != null) {
                val (index, offset) = saved
                if (index < pages.size) {
                    listState.scrollToItem(index, offset)
                }
            }
            positionRestored = true
        }
    }

    // Smooth scroll loop (~60fps) — runs when scrolling is active,
    // reads effectiveSpeed each frame for smooth easing
    LaunchedEffect(isScrolling) {
        if (isScrolling && pages.isNotEmpty()) {
            autoScrollActive = true
            while (isActive) {
                val speed = effectiveSpeed.floatValue
                if (speed > 0.01f) {
                    listState.scrollBy(speed * 0.3f)
                }
                delay(16)
            }
            autoScrollActive = false
        }
    }

    // Detect manual scroll → pause auto-scroll
    LaunchedEffect(listState) {
        snapshotFlow { listState.isScrollInProgress }
            .collect { inProgress ->
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

    // Auto-hide controls when scrolling starts
    LaunchedEffect(isScrolling) {
        if (isScrolling) {
            startHideTimer()
        } else {
            hideJob?.cancel()
            areControlsVisible = true
            saveCurrentPosition()
        }
    }

    // Save position on exit
    DisposableEffect(uri) {
        onDispose {
            saveCurrentPosition()
        }
    }

    // Toggle icon auto-dismiss
    LaunchedEffect(showToggleIcon) {
        if (showToggleIcon) {
            delay(800)
            showToggleIcon = false
        }
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        if (errorMessage != null) {
            Column(
                modifier = Modifier.align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(Icons.Default.Error, contentDescription = null, tint = Color.Red, modifier = Modifier.size(48.dp))
                Spacer(Modifier.height(8.dp))
                Text(errorMessage!!, color = Color.White)
                Button(onClick = onClose, modifier = Modifier.padding(top = 16.dp)) {
                    Text("Înapoi")
                }
            }
        } else if (isLoading) {
            CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
        } else {
            // PDF content with tap-to-toggle
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                // Single tap: toggle auto-scroll
                                isScrolling = !isScrolling
                                showToggleIcon = true
                            },
                            onDoubleTap = {
                                // Double tap: toggle controls without affecting scroll
                                areControlsVisible = !areControlsVisible
                                if (areControlsVisible && isScrolling) {
                                    startHideTimer()
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
                    items(pages) { pageBitmap ->
                        Image(
                            contentScale = ContentScale.Crop,
                            bitmap = pageBitmap,
                            contentDescription = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp)
                        )
                    }
                }
            }

            // --- Play/Pause toggle icon overlay ---
            AnimatedVisibility(
                visible = showToggleIcon,
                enter = fadeIn(animationSpec = tween(150)),
                exit = fadeOut(animationSpec = tween(300)),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .background(Color.Black.copy(alpha = 0.6f), shape = CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = if (isScrolling) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }

            // --- End of document message ---
            AnimatedVisibility(
                visible = showEndMessage,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.Center)
            ) {
                Text(
                    "Sfârșitul documentului",
                    color = Color.White,
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .background(Color.Black.copy(alpha = 0.6f), shape = MaterialTheme.shapes.medium)
                        .padding(horizontal = 24.dp, vertical = 12.dp)
                )
            }
        }

        // --- Back button (always visible) ---
        IconButton(
            onClick = {
                saveCurrentPosition()
                onClose()
            },
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
        ) {
            @Suppress("DEPRECATION")
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // --- Auto-hiding control bar ---
        if (!isLoading && errorMessage == null) {
            AnimatedVisibility(
                visible = areControlsVisible,
                enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                exit = slideOutVertically(targetOffsetY = { it }) + fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                        .padding(16.dp)
                        .pointerInput(Unit) {
                            // Any touch on controls resets the hide timer
                            detectTapGestures {
                                if (isScrolling) {
                                    startHideTimer()
                                }
                            }
                        }
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // Play/Pause button (48dp touch target)
                        IconButton(
                            onClick = {
                                isScrolling = !isScrolling
                                showToggleIcon = true
                            },
                            modifier = Modifier.size(48.dp)
                        ) {
                            Icon(
                                imageVector = if (isScrolling) Icons.Default.Pause else Icons.Default.PlayArrow,
                                contentDescription = if (isScrolling) "Pause" else "Play",
                                modifier = Modifier.size(32.dp)
                            )
                        }

                        // Speed label (tappable to reset to default)
                        Text(
                            text = "${(scrollSpeed * 10).toInt() / 10f}x",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier
                                .clickable {
                                    scrollSpeed = 2f
                                    if (isScrolling) startHideTimer()
                                }
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(4.dp))

                    // Speed slider
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            "Slow",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                        Slider(
                            value = scrollSpeed,
                            onValueChange = {
                                scrollSpeed = it
                                if (isScrolling) startHideTimer()
                            },
                            valueRange = 0.5f..15f,
                            steps = 28, // (15 - 0.5) / 0.5 - 1 = 28 steps for 0.5 increments
                            modifier = Modifier.weight(1f).padding(horizontal = 8.dp)
                        )
                        Text(
                            "Fast",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.6f)
                        )
                    }
                }
            }

            // Bottom edge tap zone (reveals controls when hidden)
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
    }
}