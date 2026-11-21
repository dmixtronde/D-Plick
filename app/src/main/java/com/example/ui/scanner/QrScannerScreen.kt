package com.example.ui.scanner

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import android.util.Size
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.R
import com.example.scanner.QrCodeImageAnalyzer
import com.example.ui.ScannerUiState
import com.example.ui.theme.MonoUrlTextStyle
import java.util.concurrent.Executors

@Composable
fun QrScannerScreen(
    uiState: ScannerUiState,
    onQrCodeScanned: (String) -> Boolean,
    onConnectLastServer: () -> Unit,
    onOpenManualSheet: () -> Unit,
    onDismissManualSheet: () -> Unit,
    onManualInputChanged: (String) -> Unit,
    onSubmitManualUrl: () -> Unit,
    onToggleFlash: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val haptic = LocalHapticFeedback.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED
        )
    }
    var permissionRequestedOnce by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        hasCameraPermission = granted
        permissionRequestedOnce = true
    }

    // Request CAMERA permission immediately on first launch if not granted
    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    // Re-check permission whenever returning from Android App Settings
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                hasCameraPermission = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.CAMERA
                ) == PackageManager.PERMISSION_GRANTED
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val isPermanentlyDenied = remember(hasCameraPermission, permissionRequestedOnce) {
        if (hasCameraPermission || !permissionRequestedOnce) {
            false
        } else {
            val activity = context as? Activity
            activity != null && !ActivityCompat.shouldShowRequestPermissionRationale(
                activity,
                Manifest.permission.CAMERA
            )
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (hasCameraPermission) {
            CameraXQrPreview(
                isFlashEnabled = uiState.isFlashEnabled,
                onQrDetected = { raw ->
                    val accepted = onQrCodeScanned(raw)
                    if (accepted) {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    }
                    accepted
                }
            )
            ScannerViewfinderOverlay()
        } else {
            CameraPermissionDeniedContent(
                isPermanentlyDenied = isPermanentlyDenied,
                onRequestPermission = {
                    permissionLauncher.launch(Manifest.permission.CAMERA)
                },
                onOpenSettings = {
                    val intent = Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.fromParts("package", context.packageName, null)
                    )
                    context.startActivity(intent)
                }
            )
        }

        // Top Bar with Branding & Torch Toggle
        ScannerTopBar(
            showFlashButton = hasCameraPermission,
            isFlashEnabled = uiState.isFlashEnabled,
            onToggleFlash = onToggleFlash,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
        )

        // Bottom Controls: Invalid QR banner, Last IP Quick Reconnect, Manual IP Button
        Column(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .widthIn(max = 560.dp)
                .navigationBarsPadding()
                .padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            AnimatedVisibility(
                visible = uiState.showInvalidQrBanner,
                enter = fadeIn() + slideInVertically { it / 2 },
                exit = fadeOut() + slideOutVertically { it / 2 }
            ) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.94f),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.6f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            text = stringResource(R.string.scanner_invalid_qr_error),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onError
                        )
                    }
                }
            }

            if (hasCameraPermission) {
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.72f)
                ) {
                    Text(
                        text = stringResource(R.string.scanner_hint_align),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )
                }
            }

            uiState.lastServerUrl?.let { lastUrl ->
                QuickReconnectCard(
                    serverUrl = lastUrl,
                    onClick = onConnectLastServer
                )
            }

            OutlinedButton(
                onClick = onOpenManualSheet,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .testTag("manual_ip_button"),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.85f)),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Rounded.Edit,
                    contentDescription = stringResource(R.string.cd_manual_ip),
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.scanner_manual_ip_button),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }

    if (uiState.isManualSheetOpen) {
        ManualIpBottomSheet(
            inputText = uiState.manualInputText,
            isError = uiState.manualInputError,
            onInputChanged = onManualInputChanged,
            onSubmit = onSubmitManualUrl,
            onDismiss = onDismissManualSheet
        )
    }
}

@Composable
private fun ScannerTopBar(
    showFlashButton: Boolean,
    isFlashEnabled: Boolean,
    onToggleFlash: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.88f),
                        MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.45f),
                        Color.Transparent
                    )
                )
            )
            .statusBarsPadding()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.weight(1f)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.QrCodeScanner,
                    contentDescription = stringResource(R.string.cd_app_logo),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier
                        .padding(9.dp)
                        .size(22.dp)
                )
            }
            Column {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = stringResource(R.string.scanner_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        if (showFlashButton) {
            Surface(
                shape = CircleShape,
                color = if (isFlashEnabled) {
                    MaterialTheme.colorScheme.tertiary
                } else {
                    MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.85f)
                },
                border = BorderStroke(
                    width = 1.dp,
                    color = if (isFlashEnabled) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.outline.copy(alpha = 0.6f)
                    }
                )
            ) {
                IconButton(
                    onClick = onToggleFlash,
                    modifier = Modifier
                        .size(48.dp)
                        .testTag("flash_toggle_button")
                ) {
                    Icon(
                        imageVector = if (isFlashEnabled) {
                            Icons.Rounded.FlashOn
                        } else {
                            Icons.Rounded.FlashOff
                        },
                        contentDescription = stringResource(
                            if (isFlashEnabled) R.string.scanner_flash_on else R.string.scanner_flash_off
                        ),
                        tint = if (isFlashEnabled) {
                            MaterialTheme.colorScheme.onTertiary
                        } else {
                            MaterialTheme.colorScheme.onSurface
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickReconnectCard(
    serverUrl: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.94f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.55f)),
        tonalElevation = 4.dp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .testTag("quick_reconnect_card")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.secondaryContainer
            ) {
                Icon(
                    imageVector = Icons.Rounded.History,
                    contentDescription = stringResource(R.string.cd_quick_reconnect),
                    tint = MaterialTheme.colorScheme.secondary,
                    modifier = Modifier
                        .padding(10.dp)
                        .size(20.dp)
                )
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = stringResource(R.string.scanner_last_server_label),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.secondary
                )
                Text(
                    text = serverUrl,
                    style = MonoUrlTextStyle,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Surface(
                shape = CircleShape,
                color = MaterialTheme.colorScheme.secondary
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                    contentDescription = stringResource(R.string.scanner_reconnect_action),
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier
                        .padding(8.dp)
                        .size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun CameraXQrPreview(
    isFlashEnabled: Boolean,
    onQrDetected: (String) -> Boolean
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val latestOnQrDetected by rememberUpdatedState(onQrDetected)

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var cameraProviderRef by remember { mutableStateOf<ProcessCameraProvider?>(null) }

    val mainExecutor = remember(context) { ContextCompat.getMainExecutor(context) }

    LaunchedEffect(boundCamera, isFlashEnabled) {
        boundCamera?.let { cam ->
            if (cam.cameraInfo.hasFlashUnit()) {
                cam.cameraControl.enableTorch(isFlashEnabled)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                cameraProviderRef?.unbindAll()
            } catch (_: Exception) {
            }
            boundCamera = null
            analysisExecutor.shutdown()
        }
    }

    AndroidView(
        modifier = Modifier
            .fillMaxSize()
            .testTag("qr_camera_preview"),
        factory = { ctx ->
            val previewView = PreviewView(ctx).apply {
                scaleType = PreviewView.ScaleType.FILL_CENTER
                implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            }

            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
            cameraProviderFuture.addListener(
                {
                    try {
                        val cameraProvider = cameraProviderFuture.get()
                        cameraProviderRef = cameraProvider

                        val preview = Preview.Builder()
                            .build()
                            .also { it.surfaceProvider = previewView.surfaceProvider }

                        lateinit var analyzer: QrCodeImageAnalyzer
                        analyzer = QrCodeImageAnalyzer { rawQr ->
                            mainExecutor.execute {
                                val accepted = latestOnQrDetected(rawQr)
                                if (accepted) {
                                    // Immediately release camera hardware before WebView getUserMedia starts
                                    analyzer.pauseAnalysis()
                                    try {
                                        cameraProvider.unbindAll()
                                    } catch (_: Exception) {
                                    }
                                    boundCamera = null
                                }
                            }
                        }

                        @Suppress("DEPRECATION")
                        val imageAnalysis = ImageAnalysis.Builder()
                            .setTargetResolution(Size(1280, 720))
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { it.setAnalyzer(analysisExecutor, analyzer) }

                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            CameraSelector.DEFAULT_BACK_CAMERA,
                            preview,
                            imageAnalysis
                        )
                        boundCamera = camera
                        if (camera.cameraInfo.hasFlashUnit()) {
                            camera.cameraControl.enableTorch(isFlashEnabled)
                        }
                    } catch (_: Exception) {
                        // Camera may be unavailable on emulators without rear camera hardware
                    }
                },
                mainExecutor
            )

            previewView
        }
    )
}

@Composable
private fun ScannerViewfinderOverlay() {
    val scrimColor = MaterialTheme.colorScheme.surfaceContainerLowest.copy(alpha = 0.62f)
    val bracketColor = MaterialTheme.colorScheme.primary
    val laserColor = MaterialTheme.colorScheme.secondary
    val laserGlow = MaterialTheme.colorScheme.tertiary

    val infiniteTransition = rememberInfiniteTransition(label = "qr_laser_beam")
    val beamProgress by infiniteTransition.animateFloat(
        initialValue = 0.08f,
        targetValue = 0.92f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "beam_y"
    )

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val frameSide = minOf(maxWidth, maxHeight) * 0.66f
        val clampedSide = frameSide.coerceIn(210.dp, 290.dp)

        Canvas(modifier = Modifier.fillMaxSize()) {
            val boxPx = clampedSide.toPx()
            val left = (size.width - boxPx) / 2f
            val top = (size.height - boxPx) / 2f - 28.dp.toPx()
            val right = left + boxPx
            val bottom = top + boxPx
            val cornerRadiusPx = 22.dp.toPx()

            // Dark scrim with transparent rounded cutout
            val maskPath = Path().apply {
                fillType = PathFillType.EvenOdd
                addRect(Rect(0f, 0f, size.width, size.height))
                addRoundRect(
                    RoundRect(
                        left = left,
                        top = top,
                        right = right,
                        bottom = bottom,
                        cornerRadius = CornerRadius(cornerRadiusPx, cornerRadiusPx)
                    )
                )
            }
            drawPath(path = maskPath, color = scrimColor)

            // Glowing corner brackets
            val stroke = 4.5.dp.toPx()
            val arm = 34.dp.toPx()

            // Top-left
            drawLine(bracketColor, Offset(left, top + arm), Offset(left, top + cornerRadiusPx * 0.5f), stroke, StrokeCap.Round)
            drawLine(bracketColor, Offset(left + cornerRadiusPx * 0.5f, top), Offset(left + arm, top), stroke, StrokeCap.Round)

            // Top-right
            drawLine(bracketColor, Offset(right - arm, top), Offset(right - cornerRadiusPx * 0.5f, top), stroke, StrokeCap.Round)
            drawLine(bracketColor, Offset(right, top + cornerRadiusPx * 0.5f), Offset(right, top + arm), stroke, StrokeCap.Round)

            // Bottom-left
            drawLine(bracketColor, Offset(left, bottom - arm), Offset(left, bottom - cornerRadiusPx * 0.5f), stroke, StrokeCap.Round)
            drawLine(bracketColor, Offset(left + cornerRadiusPx * 0.5f, bottom), Offset(left + arm, bottom), stroke, StrokeCap.Round)

            // Bottom-right
            drawLine(bracketColor, Offset(right - arm, bottom), Offset(right - cornerRadiusPx * 0.5f, bottom), stroke, StrokeCap.Round)
            drawLine(bracketColor, Offset(right, bottom - arm), Offset(right, bottom - cornerRadiusPx * 0.5f), stroke, StrokeCap.Round)

            // Animated horizontal scan beam
            val beamY = top + (boxPx * beamProgress)
            val beamInset = 18.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(Color.Transparent, laserColor, laserGlow, laserColor, Color.Transparent),
                    startX = left + beamInset,
                    endX = right - beamInset
                ),
                start = Offset(left + beamInset, beamY),
                end = Offset(right - beamInset, beamY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CameraPermissionDeniedContent(
    isPermanentlyDenied: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            modifier = Modifier
                .fillMaxWidth()
                .widthIn(max = 440.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Surface(
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.25f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = stringResource(R.string.cd_camera_permission),
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(18.dp)
                            .size(36.dp)
                    )
                }

                Text(
                    text = stringResource(R.string.permission_camera_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.permission_camera_rationale),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )

                Text(
                    text = stringResource(R.string.permission_denied_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.tertiary,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(4.dp))

                if (isPermanentlyDenied) {
                    Button(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("open_settings_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Settings,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.permission_open_settings_button))
                    }
                } else {
                    Button(
                        onClick = onRequestPermission,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("grant_camera_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primaryContainer,
                            contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CameraAlt,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.permission_grant_button))
                    }
                }
            }
        }
    }
}
