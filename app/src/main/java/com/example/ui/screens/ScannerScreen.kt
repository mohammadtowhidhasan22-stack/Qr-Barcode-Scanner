package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.view.MotionEvent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
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
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.CenterFocusStrong
import androidx.compose.material.icons.rounded.FlashlightOff
import androidx.compose.material.icons.rounded.FlashlightOn
import androidx.compose.material.icons.rounded.ImageSearch
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.data.model.ScannerMode
import com.example.data.preferences.UserSettings
import com.example.ui.theme.BlueAccent
import com.example.ui.theme.SkyScanLaser
import com.example.util.ActionLauncher
import com.example.util.FeedbackHelper
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

@androidx.annotation.OptIn(androidx.camera.core.ExperimentalGetImage::class)
@Composable
fun ScannerScreen(
    initialMode: ScannerMode,
    settings: UserSettings,
    onCodeDetected: (rawValue: String, formatCode: Int) -> Unit,
    onGalleryScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var scannerMode by remember(initialMode) { mutableStateOf(initialMode) }
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

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    if (!hasCameraPermission) {
        CameraPermissionFallback(
            showSettingsButton = permissionRequestedOnce,
            onRequestPermission = { permissionLauncher.launch(Manifest.permission.CAMERA) },
            onOpenSettings = { ActionLauncher.openAppSettings(context) },
            onGalleryScanClick = onGalleryScanClick,
            modifier = modifier
        )
        return
    }

    var isTorchOn by remember { mutableStateOf(false) }
    var useFrontCamera by remember { mutableStateOf(false) }
    var canSwitchCamera by remember { mutableStateOf(false) }
    var hasFlashUnit by remember { mutableStateOf(false) }
    var isPaused by remember { mutableStateOf(false) }
    var linearZoom by remember { mutableFloatStateOf(0f) }
    var cameraError by remember { mutableStateOf<String?>(null) }
    var cameraInstance by remember { mutableStateOf<Camera?>(null) }
    var manualCaptureTrigger by remember { mutableStateOf(false) }

    // Duplicate prevention state
    var lastDetectedKey by remember { mutableStateOf("") }
    var lastDetectedTimestamp by remember { mutableLongStateOf(0L) }
    var handlingDetection by remember { mutableStateOf(false) }

    val currentSettings by rememberUpdatedState(settings)
    val currentPaused by rememberUpdatedState(isPaused)
    val currentManualTrigger by rememberUpdatedState(manualCaptureTrigger)
    val currentOnCodeDetected by rememberUpdatedState(onCodeDetected)

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    val barcodeScanner = remember(scannerMode) {
        val options = when (scannerMode) {
            ScannerMode.QR_ONLY -> BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()
            ScannerMode.BARCODE_ONLY -> BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93,
                    Barcode.FORMAT_CODABAR,
                    Barcode.FORMAT_ITF,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX
                )
                .build()
            ScannerMode.ALL -> BarcodeScannerOptions.Builder()
                .setBarcodeFormats(
                    Barcode.FORMAT_QR_CODE,
                    Barcode.FORMAT_EAN_13,
                    Barcode.FORMAT_EAN_8,
                    Barcode.FORMAT_UPC_A,
                    Barcode.FORMAT_UPC_E,
                    Barcode.FORMAT_CODE_128,
                    Barcode.FORMAT_CODE_39,
                    Barcode.FORMAT_CODE_93,
                    Barcode.FORMAT_CODABAR,
                    Barcode.FORMAT_ITF,
                    Barcode.FORMAT_PDF417,
                    Barcode.FORMAT_AZTEC,
                    Barcode.FORMAT_DATA_MATRIX
                )
                .build()
        }
        BarcodeScanning.getClient(options)
    }

    DisposableEffect(barcodeScanner) {
        onDispose {
            barcodeScanner.close()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    val previewView = remember {
        PreviewView(context).apply {
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    // Bind / re-bind camera whenever lens or scanner mode changes
    LaunchedEffect(useFrontCamera, barcodeScanner, lifecycleOwner) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            runCatching {
                val provider = cameraProviderFuture.get()
                val hasBack = provider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)
                val hasFront = provider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)
                canSwitchCamera = hasBack && hasFront

                val selector = when {
                    useFrontCamera && hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    hasBack -> CameraSelector.DEFAULT_BACK_CAMERA
                    hasFront -> CameraSelector.DEFAULT_FRONT_CAMERA
                    else -> {
                        cameraError = "No camera hardware is available on this device."
                        return@addListener
                    }
                }

                val preview = Preview.Builder().build().also {
                    it.surfaceProvider = previewView.surfaceProvider
                }

                val imageAnalysis = ImageAnalysis.Builder()
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()

                imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                    val shouldAnalyze = !currentPaused &&
                        !handlingDetection &&
                        (currentSettings.autoScanEnabled || currentManualTrigger)

                    val mediaImage = imageProxy.image
                    if (!shouldAnalyze || mediaImage == null) {
                        imageProxy.close()
                        return@setAnalyzer
                    }

                    if (!currentSettings.autoScanEnabled && currentManualTrigger) {
                        manualCaptureTrigger = false
                    }

                    val inputImage = InputImage.fromMediaImage(
                        mediaImage,
                        imageProxy.imageInfo.rotationDegrees
                    )

                    barcodeScanner.process(inputImage)
                        .addOnSuccessListener { barcodes ->
                            val match = barcodes.firstOrNull { !it.rawValue.isNullOrBlank() }
                            val raw = match?.rawValue
                            if (match != null && !raw.isNullOrBlank() && !handlingDetection) {
                                val now = System.currentTimeMillis()
                                val dedupeKey = "${match.format}:$raw"
                                val isDuplicate = dedupeKey == lastDetectedKey &&
                                    (now - lastDetectedTimestamp) < 2500L

                                if (!isDuplicate) {
                                    handlingDetection = true
                                    lastDetectedKey = dedupeKey
                                    lastDetectedTimestamp = now
                                    FeedbackHelper.triggerScanFeedback(
                                        context = context,
                                        vibrate = currentSettings.vibrationEnabled,
                                        playSound = currentSettings.soundEnabled
                                    )
                                    currentOnCodeDetected(raw, match.format)
                                }
                            }
                        }
                        .addOnCompleteListener {
                            imageProxy.close()
                        }
                }

                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    selector,
                    preview,
                    imageAnalysis
                )
                cameraInstance = camera
                hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                if (!hasFlashUnit) isTorchOn = false
                camera.cameraControl.enableTorch(isTorchOn && hasFlashUnit)
                camera.cameraControl.setLinearZoom(linearZoom)
                cameraError = null
            }.onFailure { err ->
                cameraError = err.localizedMessage ?: "Unable to initialize camera preview."
            }
        }, ContextCompat.getMainExecutor(context))
    }

    // Tap-to-focus setup on PreviewView
    DisposableEffect(previewView, cameraInstance) {
        previewView.setOnTouchListener { view, event ->
            if (event.action == MotionEvent.ACTION_UP) {
                val camera = cameraInstance ?: return@setOnTouchListener false
                val factory = previewView.meteringPointFactory
                val point = factory.createPoint(event.x, event.y)
                val action = FocusMeteringAction.Builder(point)
                    .setAutoCancelDuration(3, TimeUnit.SECONDS)
                    .build()
                camera.cameraControl.startFocusAndMetering(action)
                view.performClick()
            }
            true
        }
        onDispose {
            previewView.setOnTouchListener(null)
        }
    }

    Box(modifier = modifier.fillMaxSize().background(Color.Black)) {
        if (cameraError != null) {
            CameraUnavailableFallback(
                errorMessage = cameraError ?: "Camera unavailable",
                onRetry = {
                    cameraError = null
                    useFrontCamera = !useFrontCamera
                    useFrontCamera = !useFrontCamera
                },
                onGalleryScanClick = onGalleryScanClick
            )
        } else {
            AndroidView(
                factory = { previewView },
                modifier = Modifier.fillMaxSize()
            )

            // Blue Scanning Frame + Moving Laser Overlay
            ScannerViewfinderOverlay(
                isPaused = isPaused,
                isBarcodeMode = scannerMode == ScannerMode.BARCODE_ONLY,
                modifier = Modifier.fillMaxSize()
            )

            // Top Mode Switcher & Status
            Column(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Color.Black.copy(alpha = 0.58f)
                ) {
                    Row(
                        modifier = Modifier.padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        ScannerMode.entries.forEach { mode ->
                            val selected = scannerMode == mode
                            Surface(
                                shape = RoundedCornerShape(50),
                                color = if (selected) BlueAccent else Color.Transparent,
                                modifier = Modifier
                                    .testTag("scanner_mode_${mode.name.lowercase()}")
                                    .clip(RoundedCornerShape(50))
                                    .clickable { scannerMode = mode }
                            ) {
                                Text(
                                    text = mode.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = Color.White,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                                )
                            }
                        }
                    }
                }

                AnimatedVisibility(
                    visible = isPaused,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color(0xFFF59E0B).copy(alpha = 0.92f)
                    ) {
                        Text(
                            text = "Scanning Paused",
                            style = MaterialTheme.typography.labelMedium,
                            color = Color.Black,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }
            }

            // Bottom Controls Panel
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Manual scan button if Auto Scan setting is OFF
                if (!settings.autoScanEnabled && !isPaused) {
                    Button(
                        onClick = { manualCaptureTrigger = true },
                        shape = RoundedCornerShape(50),
                        modifier = Modifier.testTag("scanner_manual_capture_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.CenterFocusStrong,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Tap to Scan Code")
                    }
                } else {
                    Surface(
                        shape = RoundedCornerShape(50),
                        color = Color.Black.copy(alpha = 0.55f)
                    ) {
                        Text(
                            text = when (scannerMode) {
                                ScannerMode.QR_ONLY -> "Align QR code inside the blue frame"
                                ScannerMode.BARCODE_ONLY -> "Align product barcode horizontally"
                                ScannerMode.ALL -> "Point camera at any QR code or barcode"
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.92f),
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                        )
                    }
                }

                // Zoom toggle + Action icons row
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color.Black.copy(alpha = 0.65f)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Flashlight
                        FilledIconButton(
                            onClick = {
                                if (hasFlashUnit) {
                                    val next = !isTorchOn
                                    isTorchOn = next
                                    cameraInstance?.cameraControl?.enableTorch(next)
                                }
                            },
                            enabled = hasFlashUnit,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isTorchOn) BlueAccent else Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = Color.White.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("scanner_flashlight_button")
                        ) {
                            Icon(
                                imageVector = if (isTorchOn) Icons.Rounded.FlashlightOn else Icons.Rounded.FlashlightOff,
                                contentDescription = if (isTorchOn) "Turn flashlight off" else "Turn flashlight on"
                            )
                        }

                        // Pause / Resume
                        FilledIconButton(
                            onClick = { isPaused = !isPaused },
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = if (isPaused) Color(0xFFF59E0B) else Color.White.copy(alpha = 0.16f),
                                contentColor = if (isPaused) Color.Black else Color.White
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("scanner_pause_button")
                        ) {
                            Icon(
                                imageVector = if (isPaused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause,
                                contentDescription = if (isPaused) "Resume scanning" else "Pause scanning"
                            )
                        }

                        // 1x / 2x Zoom toggle
                        Surface(
                            shape = CircleShape,
                            color = if (linearZoom > 0.2f) BlueAccent else Color.White.copy(alpha = 0.16f),
                            modifier = Modifier
                                .size(48.dp)
                                .clip(CircleShape)
                                .clickable {
                                    val next = if (linearZoom > 0.2f) 0f else 0.45f
                                    linearZoom = next
                                    cameraInstance?.cameraControl?.setLinearZoom(next)
                                }
                                .testTag("scanner_zoom_button")
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = if (linearZoom > 0.2f) "2x" else "1x",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        // Switch Camera
                        FilledIconButton(
                            onClick = { useFrontCamera = !useFrontCamera },
                            enabled = canSwitchCamera,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White,
                                disabledContainerColor = Color.White.copy(alpha = 0.08f),
                                disabledContentColor = Color.White.copy(alpha = 0.35f)
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("scanner_switch_camera_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Cameraswitch,
                                contentDescription = "Switch front or rear camera"
                            )
                        }

                        // Gallery Scan
                        FilledIconButton(
                            onClick = onGalleryScanClick,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Color.White.copy(alpha = 0.16f),
                                contentColor = Color.White
                            ),
                            modifier = Modifier
                                .size(48.dp)
                                .testTag("scanner_gallery_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.ImageSearch,
                                contentDescription = "Scan image from gallery"
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScannerViewfinderOverlay(
    isPaused: Boolean,
    isBarcodeMode: Boolean,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "scan_laser")
    val laserProgress by infiniteTransition.animateFloat(
        initialValue = 0.06f,
        targetValue = 0.94f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1900, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laser_y"
    )

    Canvas(
        modifier = modifier.graphicsLayer {
            compositingStrategy = CompositingStrategy.Offscreen
        }
    ) {
        val boxWidth = if (isBarcodeMode) {
            minOf(size.width * 0.84f, 340.dp.toPx())
        } else {
            minOf(size.width * 0.72f, 270.dp.toPx())
        }
        val boxHeight = if (isBarcodeMode) {
            boxWidth * 0.56f
        } else {
            boxWidth
        }

        val left = (size.width - boxWidth) / 2f
        val top = (size.height - boxHeight) / 2f - 16.dp.toPx()
        val cornerRadius = 22.dp.toPx()

        // Dimmed background
        drawRect(color = Color.Black.copy(alpha = 0.52f))

        // Cutout transparent viewfinder window
        drawRoundRect(
            color = Color.Transparent,
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            blendMode = BlendMode.Clear
        )

        // Subtle inner guide border
        drawRoundRect(
            color = Color.White.copy(alpha = 0.25f),
            topLeft = Offset(left, top),
            size = Size(boxWidth, boxHeight),
            cornerRadius = CornerRadius(cornerRadius, cornerRadius),
            style = Stroke(width = 1.dp.toPx())
        )

        // Blue Scanning Corner Brackets
        val bracketLen = 30.dp.toPx()
        val strokeWidth = 4.5.dp.toPx()
        val bracketColor = if (isPaused) Color(0xFFF59E0B) else SkyScanLaser

        val cornersPath = Path().apply {
            // Top-left
            moveTo(left, top + bracketLen)
            lineTo(left, top + cornerRadius * 0.6f)
            quadraticTo(left, top, left + cornerRadius * 0.6f, top)
            lineTo(left + bracketLen, top)

            // Top-right
            moveTo(left + boxWidth - bracketLen, top)
            lineTo(left + boxWidth - cornerRadius * 0.6f, top)
            quadraticTo(left + boxWidth, top, left + boxWidth, top + cornerRadius * 0.6f)
            lineTo(left + boxWidth, top + bracketLen)

            // Bottom-left
            moveTo(left, top + boxHeight - bracketLen)
            lineTo(left, top + boxHeight - cornerRadius * 0.6f)
            quadraticTo(left, top + boxHeight, left + cornerRadius * 0.6f, top + boxHeight)
            lineTo(left + bracketLen, top + boxHeight)

            // Bottom-right
            moveTo(left + boxWidth - bracketLen, top + boxHeight)
            lineTo(left + boxWidth - cornerRadius * 0.6f, top + boxHeight)
            quadraticTo(left + boxWidth, top + boxHeight, left + boxWidth, top + boxHeight - cornerRadius * 0.6f)
            lineTo(left + boxWidth, top + boxHeight - bracketLen)
        }
        drawPath(
            path = cornersPath,
            color = bracketColor,
            style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
        )

        // Moving Blue Laser Line
        if (!isPaused) {
            val laserY = top + (boxHeight * laserProgress)
            val laserMargin = 14.dp.toPx()
            drawLine(
                brush = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        BlueAccent,
                        SkyScanLaser,
                        BlueAccent,
                        Color.Transparent
                    ),
                    startX = left + laserMargin,
                    endX = left + boxWidth - laserMargin
                ),
                start = Offset(left + laserMargin, laserY),
                end = Offset(left + boxWidth - laserMargin, laserY),
                strokeWidth = 3.dp.toPx(),
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
private fun CameraPermissionFallback(
    showSettingsButton: Boolean,
    onRequestPermission: () -> Unit,
    onOpenSettings: () -> Unit,
    onGalleryScanClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.widthIn(max = 440.dp),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CameraAlt,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(32.dp)
                    )
                }
                Text(
                    text = "Camera Permission Needed",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "To scan QR codes and barcodes in real time, grant camera access. All scanning happens locally on your device.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onRequestPermission,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("grant_camera_permission_button")
                ) {
                    Text("Allow Camera Access")
                }
                if (showSettingsButton) {
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("open_app_settings_button")
                    ) {
                        Icon(Icons.Rounded.Settings, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Open App Settings")
                    }
                }
                OutlinedButton(
                    onClick = onGalleryScanClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("permission_fallback_gallery_button")
                ) {
                    Icon(Icons.Rounded.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan Image from Gallery")
                }
            }
        }
    }
}

@Composable
private fun CameraUnavailableFallback(
    errorMessage: String,
    onRetry: () -> Unit,
    onGalleryScanClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(24.dp)),
            shape = RoundedCornerShape(24.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = Icons.Rounded.CameraAlt,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(44.dp)
                )
                Text(
                    text = "Camera Unavailable",
                    style = MaterialTheme.typography.titleLarge,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = onGalleryScanClick,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("camera_error_gallery_button")
                ) {
                    Icon(Icons.Rounded.ImageSearch, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Scan from Gallery Instead")
                }
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("camera_error_retry_button")
                ) {
                    Text("Retry Camera")
                }
            }
        }
    }
}
