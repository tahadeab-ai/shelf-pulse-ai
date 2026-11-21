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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
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
import com.shelfpulse.ai.ui.components.TelemetryChip
import com.shelfpulse.ai.ui.theme.*
import com.shelfpulse.ai.ui.viewmodel.ScanMode
import com.shelfpulse.ai.ui.viewmodel.SnapVerifyViewModel
import java.util.concurrent.Executors

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

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        if (uiState.capturedBitmap == null) {
            // Live Camera Preview for Capture
            AndroidView(
                factory = { ctx ->
                    val previewView = PreviewView(ctx)
                    val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                    cameraProviderFuture.addListener({
                        val cameraProvider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.setSurfaceProvider(previewView.surfaceProvider)
                        }

                        imageCapture = ImageCapture.Builder()
                            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                            .build()

                        val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                cameraSelector,
                                preview,
                                imageCapture
                            )
                        } catch (exc: Exception) {
                            android.util.Log.e("SnapVerify", "Camera bind failed", exc)
                        }
                    }, ContextCompat.getMainExecutor(ctx))
                    previewView
                },
                modifier = Modifier.fillMaxSize()
            )

            // Top Mode Toggle Bar
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 16.dp),
                contentAlignment = Alignment.TopCenter
            ) {
                SegmentedModeToggle(
                    currentMode = uiState.scanMode,
                    onModeSelected = { viewModel.setScanMode(it) }
                )
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
        } else {
            // Captured Freeze Frame & Interactive Canvas
            val bitmap = uiState.capturedBitmap!!
            Box(modifier = Modifier.fillMaxSize()) {
                Image(
                    bitmap = bitmap.asImageBitmap(),
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Fit
                )

                InteractiveBoundingBoxCanvas(
                    detections = uiState.detections,
                    frameWidth = bitmap.width,
                    frameHeight = bitmap.height,
                    isInteractive = true,
                    onTapBox = { index -> viewModel.toggleBoxExclusion(index) },
                    onTapEmptyArea = { x, y -> viewModel.addManualMarker(x, y) },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Count & Instructions HUD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Segmented Scan Mode Toggle: [Single 640px | SAHI 2x2 HD]
                    SegmentedModeToggle(
                        currentMode = uiState.scanMode,
                        onModeSelected = { viewModel.setScanMode(it) }
                    )

                    // Count Badge
                    Surface(
                        color = Slate900.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(24.dp),
                        border = androidx.compose.foundation.BorderStroke(1.5.dp, Emerald500)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "${uiState.effectiveCount}",
                                color = Emerald400,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = stringResource(R.string.txt_products_detected),
                                color = Slate100,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    // Interactive Touch Guidance Chip
                    Surface(
                        color = Slate800.copy(alpha = 0.85f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            text = "${stringResource(R.string.hint_tap_to_add)} • ${stringResource(R.string.hint_tap_box_to_remove)}",
                            color = Slate400,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }

                    TelemetryChip(telemetry = uiState.telemetry)
                }

                // Bottom Action Bar: Retake, Import Photo, & Save
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Slate900.copy(alpha = 0.95f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = { viewModel.reset() },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.btn_retake), fontSize = 13.sp)
                    }

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
                        Icon(Icons.Default.AddPhotoAlternate, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.btn_import_photo), fontSize = 13.sp)
                    }

                    Button(
                        onClick = { showSaveDialog = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Save, contentDescription = null, tint = Slate900, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.btn_save_session), color = Slate900, fontWeight = FontWeight.Bold, fontSize = 13.sp)
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
