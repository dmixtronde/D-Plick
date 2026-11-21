package com.example

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.ServerPreferencesRepository
import com.example.ui.AppDestination
import com.example.ui.ScannerViewModel
import com.example.ui.scanner.QrScannerScreen
import com.example.ui.theme.DPlickScannerTheme
import com.example.ui.webview.DPlickWebViewScreen

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT)
        )
        setContent {
            DPlickScannerTheme {
                DPlickScannerApp()
            }
        }
    }
}

@Composable
fun DPlickScannerApp() {
    val context = LocalContext.current.applicationContext
    val repository = remember(context) { ServerPreferencesRepository(context) }
    val viewModel: ScannerViewModel = viewModel(
        factory = ScannerViewModel.Factory(repository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    AnimatedContent(
        targetState = uiState.destination,
        transitionSpec = {
            fadeIn(animationSpec = tween(220)) togetherWith fadeOut(animationSpec = tween(180))
        },
        label = "dplick_screen_transition"
    ) { destination ->
        when (destination) {
            is AppDestination.QrScanner -> {
                QrScannerScreen(
                    uiState = uiState,
                    onQrCodeScanned = viewModel::onQrCodeScanned,
                    onConnectLastServer = viewModel::onConnectLastServer,
                    onOpenManualSheet = viewModel::onOpenManualSheet,
                    onDismissManualSheet = viewModel::onDismissManualSheet,
                    onManualInputChanged = viewModel::onManualInputChanged,
                    onSubmitManualUrl = viewModel::onSubmitManualUrl,
                    onToggleFlash = viewModel::onToggleFlash
                )
            }

            is AppDestination.WebViewSession -> {
                DPlickWebViewScreen(
                    url = destination.url,
                    onReturnToScanner = viewModel::onReturnToScanner
                )
            }
        }
    }
}
