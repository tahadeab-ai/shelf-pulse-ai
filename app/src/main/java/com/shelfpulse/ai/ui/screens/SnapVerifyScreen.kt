package com.shelfpulse.ai.ui.screens

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.shelfpulse.ai.R
import com.shelfpulse.ai.ui.components.ImageImportHelper
import com.shelfpulse.ai.ui.components.InteractiveBoundingBoxCanvas
import com.shelfpulse.ai.ui.theme.*
import com.shelfpulse.ai.ui.viewmodel.ScanMode
import com.shelfpulse.ai.ui.viewmodel.SnapVerifyViewModel
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SnapVerifyScreen(
    viewModel: SnapVerifyViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var showSaveDialog by remember { mutableStateOf(false) }
    var shelfInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var imageCapture: ImageCapture? by remember { mutableStateOf(null) }
    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var hasFlashUnit by remember { mutableStateOf(false) }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            val cappedBitmap = ImageImportHelper.loadCappedBitmap(context, uri, maxDimension = 2048)
            if (cappedBitmap != null) {
                viewModel.processImportedBitmap(cappedBitmap)
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    // Synchronize flash mode & torch assist
    LaunchedEffect(uiState.flashMode) {
        imageCapture?.flashMode = uiState.flashMode
    }

    LaunchedEffect(uiState.isTorchOn) {
        cameraControl?.enableTorch(uiState.isTorchOn)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        if (uiState.capturedBitmap == null) {
            // Live Camera Preview for Capture
            Box(modifier = Modifier.fillMaxSize()) {
                AndroidView(
                    factory = { ctx ->
                        val previewView = PreviewView(ctx).apply {
                            scaleType = PreviewView.ScaleType.FIT_CENTER
                        }
                        val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                        cameraProviderFuture.addListener({
                            val cameraProvider = cameraProviderFuture.get()
                            val preview = Preview.Builder().build().also {
                                it.setSurfaceProvider(previewView.surfaceProvider)
                            }

                            val capture = ImageCapture.Builder()
                                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                                .setFlashMode(uiState.flashMode)
                                .build()
                            imageCapture = capture

                            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                            try {
                                cameraProvider.unbindAll()
                                val camera = cameraProvider.bindToLifecycle(
                                    lifecycleOwner,
                                    cameraSelector,
                                    preview,
                                    capture
                                )
                                cameraControl = camera.cameraControl
                                hasFlashUnit = camera.cameraInfo.hasFlashUnit()
                            } catch (exc: Exception) {
                                android.util.Log.e("SnapVerify", "Camera bind failed", exc)
                            }
                        }, ContextCompat.getMainExecutor(ctx))
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Controls: Flash Toggle + Mode Selector
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 16.dp)
                        .align(Alignment.TopCenter),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (hasFlashUnit) {
                        IconButton(
                            onClick = { viewModel.cycleFlashMode() },
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(Slate900.copy(alpha = 0.8f))
                                .border(1.5.dp, if (uiState.flashMode != 2) Amber500 else Slate600, CircleShape)
                        ) {
                            val (icon, desc) = when (uiState.flashMode) {
                                0 -> Pair(Icons.Default.FlashAuto, stringResource(R.string.flash_auto))
                                1 -> Pair(Icons.Default.FlashOn, stringResource(R.string.flash_on))
                                else -> Pair(Icons.Default.FlashOff, stringResource(R.string.flash_off))
                            }
                            Icon(
                                imageVector = icon,
                                contentDescription = desc,
                                tint = if (uiState.flashMode != 2) Amber400 else Slate300,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    } else {
                        Spacer(modifier = Modifier.size(44.dp))
                    }

                    SegmentedModeToggle(
                        currentMode = uiState.scanMode,
                        onModeSelected = { viewModel.setScanMode(it) }
                    )

                    Spacer(modifier = Modifier.size(44.dp))
                }

                // Bottom Shutter & Import Action Bar
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(bottom = 36.dp, start = 24.dp, end = 24.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Import Photo from Gallery Button
                        IconButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier
                                .size(56.dp)
                                .clip(CircleShape)
                                .background(Slate900.copy(alpha = 0.8f))
                                .border(2.dp, Slate600, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AddPhotoAlternate,
                                contentDescription = stringResource(R.string.btn_import_photo),
                                tint = Emerald400,
                                modifier = Modifier.size(28.dp)
                            )
                        }

                        // Camera Shutter Button
                        IconButton(
                            onClick = {
                                val capture = imageCapture ?: return@IconButton
                                capture.takePicture(
                                    cameraExecutor,
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(imageProxy: ImageProxy) {
                                            val rotation = imageProxy.imageInfo.rotationDegrees
                                            val plane = imageProxy.planes[0].buffer
                                            val bytes = ByteArray(plane.remaining())
                                            plane.get(bytes)
                                            val rawBitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)

                                            val finalBitmap = if (rotation != 0 && rawBitmap != null) {
                                                val matrix = Matrix().apply { postRotate(rotation.toFloat()) }
                                                Bitmap.createBitmap(rawBitmap, 0, 0, rawBitmap.width, rawBitmap.height, matrix, true)
                                            } else {
                                                rawBitmap
                                            }
                                            imageProxy.close()

                                            if (finalBitmap != null) {
                                                viewModel.processCapturedBitmap(finalBitmap)
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            android.util.Log.e("SnapVerify", "Photo capture failed", exception)
                                        }
                                    }
                                )
                            },
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(Slate900.copy(alpha = 0.7f))
                                .border(4.dp, Emerald500, CircleShape)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Camera,
                                contentDescription = stringResource(R.string.btn_capture),
                                tint = Emerald400,
                                modifier = Modifier.size(40.dp)
                            )
                        }

                        // Spacer to keep Shutter centered
                        Spacer(modifier = Modifier.size(56.dp))
                    }
                }
            }
        } else {
            // Captured Freeze Frame: Clean 3-Zone Column Layout
            val bitmap = uiState.capturedBitmap!!

            Column(modifier = Modifier.fillMaxSize()) {
                // ZONE A: TOP DEDICATED HUD BAR
                Surface(
                    color = Slate900,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Slate800),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // Row 1: Mode Toggle & Product Count Badge
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            SegmentedModeToggle(
                                currentMode = uiState.scanMode,
                                onModeSelected = { viewModel.setScanMode(it) }
                            )

                            // Products Detected Badge
                            Surface(
                                color = Slate800,
                                shape = RoundedCornerShape(16.dp),
                                border = androidx.compose.foundation.BorderStroke(1.5.dp, Emerald500)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text(
                                        text = "${uiState.visibleCount}",
                                        color = Emerald400,
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.txt_products_detected),
                                        color = Slate200,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }

                        // Row 2: [Labels] Compact Toggle & Confidence Threshold Live Slider
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Compact Labels Toggle Pill
                            Surface(
                                color = if (uiState.showLabels) Emerald500 else Slate800,
                                shape = RoundedCornerShape(12.dp),
                                border = androidx.compose.foundation.BorderStroke(
                                    1.dp,
                                    if (uiState.showLabels) Emerald400 else Slate700
                                ),
                                modifier = Modifier.clickable { viewModel.toggleLabels() }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = if (uiState.showLabels) Icons.Default.Label else Icons.Default.LabelOff,
                                        contentDescription = null,
                                        tint = if (uiState.showLabels) Slate900 else Slate400,
                                        modifier = Modifier.size(14.dp)
                                    )
                                    Text(
                                        text = if (uiState.showLabels) {
                                            stringResource(R.string.btn_labels)
                                        } else {
                                            stringResource(R.string.btn_labels_off)
                                        },
                                        color = if (uiState.showLabels) Slate900 else Slate300,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            // Live Confidence Threshold Slider (0.25 .. 0.75)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = stringResource(
                                        R.string.lbl_confidence_filter,
                                        (uiState.liveConfFilter * 100).toInt()
                                    ),
                                    color = Slate300,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold
                                )
                                Slider(
                                    value = uiState.liveConfFilter,
                                    onValueChange = { viewModel.setLiveConfFilter(it) },
                                    valueRange = 0.25f..0.75f,
                                    steps = 9,
                                    modifier = Modifier.width(130.dp),
                                    colors = SliderDefaults.colors(
                                        thumbColor = Emerald400,
                                        activeTrackColor = Emerald500,
                                        inactiveTrackColor = Slate700
                                    )
                                )
                            }
                        }
                    }
                }

                // ZONE B: CENTRAL ZOOMABLE IMAGE + OVERLAY VIEWPORT
                var scale by remember { mutableFloatStateOf(1f) }
                var offsetX by remember { mutableFloatStateOf(0f) }
                var offsetY by remember { mutableFloatStateOf(0f) }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clipToBounds()
                        .pointerInput(Unit) {
                            detectTransformGestures { _, pan, zoom, _ ->
                                scale = (scale * zoom).coerceIn(1f, 4f)
                                if (scale > 1f) {
                                    val maxOffsetX = (size.width * (scale - 1f)) / 2f
                                    val maxOffsetY = (size.height * (scale - 1f)) / 2f
                                    offsetX = (offsetX + pan.x).coerceIn(-maxOffsetX, maxOffsetX)
                                    offsetY = (offsetY + pan.y).coerceIn(-maxOffsetY, maxOffsetY)
                                } else {
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                            }
                        }
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                                translationX = offsetX
                                translationY = offsetY
                            }
                    ) {
                        Image(
                            bitmap = bitmap.asImageBitmap(),
                            contentDescription = null,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Fit
                        )

                        InteractiveBoundingBoxCanvas(
                            detections = uiState.visibleDetections,
                            frameWidth = bitmap.width,
                            frameHeight = bitmap.height,
                            isInteractive = true,
                            showLabels = uiState.showLabels,
                            selectedBoxIndex = uiState.selectedBoxIndex,
                            onTapBox = { index -> viewModel.toggleBoxExclusion(index) },
                            onTapEmptyArea = { x, y -> viewModel.addManualMarker(x, y) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    // Reset Zoom Button (appears when zoomed in)
                    if (scale > 1.05f) {
                        Surface(
                            color = Slate900.copy(alpha = 0.85f),
                            shape = RoundedCornerShape(16.dp),
                            border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .padding(12.dp)
                                .clickable {
                                    scale = 1f
                                    offsetX = 0f
                                    offsetY = 0f
                                }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ZoomOutMap,
                                    contentDescription = null,
                                    tint = Emerald400,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.btn_reset_zoom),
                                    color = Slate200,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // ZONE C: BOTTOM DEDICATED ACTION BAR
                Surface(
                    color = Slate900,
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, Slate800),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Retake
                        OutlinedButton(
                            onClick = { viewModel.reset() },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(R.string.btn_retake), fontSize = 12.sp)
                        }

                        // Import Photo
                        OutlinedButton(
                            onClick = {
                                photoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald400),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(text = stringResource(R.string.btn_import_photo), fontSize = 12.sp)
                        }

                        // Save Session
                        Button(
                            onClick = { showSaveDialog = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Save, contentDescription = null, tint = Slate900, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = stringResource(R.string.btn_save_session),
                                color = Slate900,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Processing Overlay
        if (uiState.isProcessing) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Slate900.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Emerald500)
            }
        }

        // Save Session Dialog
        if (showSaveDialog) {
            AlertDialog(
                onDismissRequest = { showSaveDialog = false },
                containerColor = Slate800,
                title = {
                    Text(
                        text = stringResource(R.string.dialog_session_title),
                        color = Slate100,
                        fontWeight = FontWeight.Bold
                    )
                },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = shelfInput,
                            onValueChange = { shelfInput = it },
                            label = { Text(stringResource(R.string.lbl_shelf_aisle)) },
                            placeholder = { Text(stringResource(R.string.hint_shelf_aisle)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate600,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate100
                            ),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )

                        OutlinedTextField(
                            value = notesInput,
                            onValueChange = { notesInput = it },
                            label = { Text(stringResource(R.string.lbl_notes)) },
                            placeholder = { Text(stringResource(R.string.hint_notes)) },
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = Emerald500,
                                unfocusedBorderColor = Slate600,
                                focusedTextColor = Slate100,
                                unfocusedTextColor = Slate100
                            ),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            viewModel.saveSession(shelfInput, notesInput) {
                                showSaveDialog = false
                                viewModel.reset()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
                    ) {
                        Text(text = stringResource(R.string.btn_save), color = Slate900)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showSaveDialog = false }) {
                        Text(text = stringResource(R.string.btn_cancel), color = Slate400)
                    }
                }
            )
        }
    }
}

@Composable
private fun SegmentedModeToggle(
    currentMode: ScanMode,
    onModeSelected: (ScanMode) -> Unit
) {
    Surface(
        color = Slate900.copy(alpha = 0.9f),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700)
    ) {
        Row(
            modifier = Modifier.padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Surface(
                color = if (currentMode == ScanMode.SINGLE_640) Emerald500 else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onModeSelected(ScanMode.SINGLE_640) }
            ) {
                Text(
                    text = stringResource(R.string.mode_single_640),
                    color = if (currentMode == ScanMode.SINGLE_640) Slate900 else Slate400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }

            Surface(
                color = if (currentMode == ScanMode.SAHI_2X2) Emerald500 else Color.Transparent,
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.clickable { onModeSelected(ScanMode.SAHI_2X2) }
            ) {
                Text(
                    text = stringResource(R.string.mode_sahi_2x2),
                    color = if (currentMode == ScanMode.SAHI_2X2) Slate900 else Slate400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp)
                )
            }
        }
    }
}
