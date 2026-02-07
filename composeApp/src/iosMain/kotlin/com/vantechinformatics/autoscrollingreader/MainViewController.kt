package com.vantechinformatics.autoscrollingreader

import androidx.compose.ui.window.ComposeUIViewController

// 1. Adăugăm parametrul opțional fileUrl
fun MainViewController(fileUrl: String? = null) = ComposeUIViewController {

    // 2. Îl trimitem către App pentru a deschide direct fișierul (dacă există)
    App(externalData = fileUrl)
}