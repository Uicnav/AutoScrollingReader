package com.vantechinformatics.autoscrollingreader
// Asigură-te că pui pachetul corect dacă e cazul, ex: package com.vantechinformatics.autoscrollingreader

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

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
    var pages by remember { mutableStateOf<List<ImageBitmap>>(emptyList()) }
    val listState = rememberLazyListState()

    // Control Scroll
    var isScrolling by remember { mutableStateOf(false) }
    var scrollSpeed by remember { mutableFloatStateOf(1f) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    // Încărcare
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

    // Buclă Scroll
    LaunchedEffect(isScrolling, scrollSpeed) {
        if (isScrolling && pages.isNotEmpty()) {
            while (isActive) {
                listState.scrollBy(scrollSpeed)
                delay(100) // ~60 FPS
            }
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
            // Lista cu pagini
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

        // --- Controale Suprapuse ---

        // Buton Back
        IconButton(
            onClick = onClose,
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(16.dp)
                .background(Color.Black.copy(alpha = 0.5f), shape = MaterialTheme.shapes.small)
        ) {
            Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
        }

        // Bara de Control (Viteză & Play)
        if (!isLoading && errorMessage == null) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
                    .padding(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Button(onClick = { isScrolling = !isScrolling }) {
                        Icon(
                            imageVector = if (isScrolling) Icons.Default.Close else Icons.Default.PlayArrow,
                            contentDescription = null
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(if (isScrolling) "Stop" else "Start")
                    }

                    Text(
                        "Viteză: ${scrollSpeed.toInt()}",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(Modifier.height(8.dp))

                Slider(
                    value = scrollSpeed,
                    onValueChange = { scrollSpeed = it },
                    valueRange = 1f..10f
                )
            }
        }
    }
}