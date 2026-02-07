package com.vantechinformatics.autoscrollingreader

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appContext = applicationContext

        // Verificăm dacă aplicația a fost deschisă dintr-un fișier
        val externalUri: String? = if (intent?.action == Intent.ACTION_VIEW) {
            intent.data?.toString()
        } else {
            null
        }

        setContent {
            App(externalData = externalUri)
        }
    }
}
@Preview
@Composable
fun AppAndroidPreview() {
    App()
}