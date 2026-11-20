package com.shelfpulse.ai.ui.screens

import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.shelfpulse.ai.R
import com.shelfpulse.ai.ui.components.InteractiveBoundingBoxCanvas
import com.shelfpulse.ai.ui.components.TelemetryChip
import com.shelfpulse.ai.ui.theme.*
import com.shelfpulse.ai.ui.viewmodel.LiveScanViewModel
import java.util.concurrent.Executors

@Composable
fun LiveScanScreen(
    viewModel: LiveScanViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val uiState by viewModel.uiState.collectAsState()

    var cameraControl: CameraControl? by remember { mutableStateOf(null) }
    var showSliders by remember { mutableStateOf(false) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(uiState.isTorchOn) {
        cameraControl?.enableTorch(uiState.isTorchOn)
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        // Live Camera Stream
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()
                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val resolutionSelector = ResolutionSelector.Builder()
                        .setResolutionStrategy(
                            ResolutionStrategy(
                                Size(640, 480),
                                ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER
                            )
                        )
                        .build()

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setResolutionSelector(resolutionSelector)
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                        .build()

                    imageAnalysis.setAnalyzer(analysisExecutor) { imageProxy ->
                        val plane = imageProxy.planes[0]
                        val rotation = imageProxy.imageInfo.rotationDegrees

                        viewModel.processRgbaFrame(
                            rgbaBuffer = plane.buffer,
                            width = imageProxy.width,
                            height = imageProxy.height,
                            rowStride = plane.rowStride,
                            rotationDegrees = rotation
                        )
                        imageProxy.close()
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        cameraProvider.unbindAll()
                        val camera = cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                        cameraControl = camera.cameraControl
                    } catch (e: Exception) {
                        android.util.Log.e("LiveScan", "Camera binding failed", e)
                    }
                }, ContextCompat.getMainExecutor(ctx))
                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // Real-Time Bounding Box & Track Overlay
        InteractiveBoundingBoxCanvas(
            detections = uiState.activeDetections,
            frameWidth = uiState.frameWidth,
            frameHeight = uiState.frameHeight,
            isInteractive = false,
            modifier = Modifier.fillMaxSize()
        )

        // Top HUD: Counts & Telemetry
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
                .align(Alignment.TopCenter),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Live Unique Count Badge
                Surface(
                    color = Slate900.copy(alpha = 0.85f),
                    shape = RoundedCornerShape(20.dp),
                    border = androidx.compose.foundation.BorderStroke(1.5.dp, Cyan500)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = "${uiState.cumulativeTrackCount}",
                            color = Cyan400,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.txt_unique_skus),
                            color = Slate100,
                            fontSize = 13.sp
                        )
                    }
                }

                // Torch Toggle Button
                IconButton(
                    onClick = { viewModel.toggleTorch() },
                    modifier = Modifier
                        .clip(CircleShape)
                        .background(if (uiState.isTorchOn) Amber500 else Slate900.copy(alpha = 0.85f))
                ) {
                    Icon(
                        imageVector = if (uiState.isTorchOn) Icons.Default.FlashOn else Icons.Default.FlashOff,
                        contentDescription = stringResource(R.string.btn_torch),
                        tint = if (uiState.isTorchOn) Slate900 else Slate100
                    )
                }
            }

            TelemetryChip(
                telemetry = uiState.telemetry,
                onToggleBackend = { viewModel.toggleBackend() },
                onToggleResolution = { viewModel.toggleResolution() }
            )
        }

        // Bottom Controls HUD
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .background(Slate900.copy(alpha = 0.90f))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Threshold Sliders (collapsible)
            AnimatedVisibility(visible = showSliders) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = stringResource(R.string.lbl_conf_thresh, uiState.confThresh),
                        color = Slate400,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = uiState.confThresh,
                        onValueChange = { viewModel.setConfidence(it) },
                        valueRange = 0.15f..0.85f,
                        colors = SliderDefaults.colors(
                            thumbColor = Emerald500,
                            activeTrackColor = Emerald500
                        )
                    )

                    Text(
                        text = stringResource(R.string.lbl_nms_thresh, uiState.nmsThresh),
                        color = Slate400,
                        fontSize = 12.sp
                    )
                    Slider(
                        value = uiState.nmsThresh,
                        onValueChange = { viewModel.setNms(it) },
                        valueRange = 0.20f..0.80f,
                        colors = SliderDefaults.colors(
                            thumbColor = Cyan500,
                            activeTrackColor = Cyan500
                        )
                    )
                }
            }

            // Action Buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Button(
                    onClick = { viewModel.togglePause() },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (uiState.isPaused) Amber500 else Slate700
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (uiState.isPaused) Icons.Default.PlayArrow else Icons.Default.Pause,
                        contentDescription = null,
                        tint = if (uiState.isPaused) Slate900 else Slate100
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (uiState.isPaused) stringResource(R.string.btn_resume) else stringResource(R.string.btn_pause),
                        color = if (uiState.isPaused) Slate900 else Slate100
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.resetCounter() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = stringResource(R.string.btn_reset_counter), color = Slate100)
                }

                IconButton(
                    onClick = { showSliders = !showSliders },
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (showSliders) Cyan500 else Slate800)
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = if (showSliders) Slate900 else Slate100
                    )
                }
            }
        }
    }
}
