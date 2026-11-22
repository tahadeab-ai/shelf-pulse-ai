package com.shelfpulse.ai.ui.screens

import android.util.Size
import androidx.camera.core.*
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.shelfpulse.ai.R
import com.shelfpulse.ai.data.sync.SyncAuditWorker
import com.shelfpulse.ai.data.sync.SyncPreferences
import com.shelfpulse.ai.ui.components.BarcodeScanSheet
import com.shelfpulse.ai.ui.components.CompactDepthStepper
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
    var hasFlashUnit by remember { mutableStateOf(false) }
    var showSliders by remember { mutableStateOf(false) }

    var showBarcodeSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var shelfInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    var cameraProvider: ProcessCameraProvider? by remember { mutableStateOf(null) }
    var previewViewRef: PreviewView? by remember { mutableStateOf(null) }

    val analysisExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analysisExecutor.shutdown()
        }
    }

    LaunchedEffect(uiState.isTorchOn) {
        cameraControl?.enableTorch(uiState.isTorchOn)
    }

    val bindCamera: () -> Unit = {
        val provider = cameraProvider
        val pv = previewViewRef
        if (provider != null && pv != null && !showBarcodeSheet) {
            val preview = Preview.Builder().build().also {
                it.setSurfaceProvider(pv.surfaceProvider)
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
                try {
                    if (!showBarcodeSheet && !uiState.isPaused) {
                        val plane = imageProxy.planes[0]
                        val rotation = imageProxy.imageInfo.rotationDegrees

                        viewModel.processRgbaFrame(
                            rgbaBuffer = plane.buffer,
                            width = imageProxy.width,
                            height = imageProxy.height,
                            rowStride = plane.rowStride,
                            rotationDegrees = rotation
                        )
                    }
                } finally {
                    imageProxy.close()
                }
            }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            try {
                provider.unbindAll()
                val camera = provider.bindToLifecycle(
                    lifecycleOwner,
                    cameraSelector,
                    preview,
                    imageAnalysis
                )
                cameraControl = camera.cameraControl
                hasFlashUnit = camera.cameraInfo.hasFlashUnit()
            } catch (e: Exception) {
                android.util.Log.e("LiveScan", "Camera binding failed", e)
            }
        }
    }

    // CameraX Lifecycle Guard: Temporarily unbind camera when BarcodeScanSheet opens
    LaunchedEffect(showBarcodeSheet) {
        if (showBarcodeSheet) {
            cameraProvider?.unbindAll()
        } else {
            bindCamera()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        // Live Camera Stream
        AndroidView(
            factory = { ctx ->
                val pv = PreviewView(ctx).apply {
                    scaleType = PreviewView.ScaleType.FIT_CENTER
                }
                previewViewRef = pv
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)

                cameraProviderFuture.addListener({
                    cameraProvider = cameraProviderFuture.get()
                    bindCamera()
                }, ContextCompat.getMainExecutor(ctx))
                pv
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

        // Top HUD: Counts, Depth, SKU & Telemetry
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
                    border = BorderStroke(1.5.dp, Cyan500)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "${uiState.frontFacings}",
                            color = Cyan400,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.txt_unique_skus),
                            color = Slate100,
                            fontSize = 12.sp
                        )
                    }
                }

                // Compact Depth Stepper
                CompactDepthStepper(
                    depth = uiState.depthMultiplier,
                    onDepthChange = { viewModel.setDepthMultiplier(it) }
                )

                // Torch Toggle Button (guarded by hardware flash unit availability)
                if (hasFlashUnit) {
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
            }

            // Link SKU Chip / Button Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Start,
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (uiState.linkedBarcode != null) {
                    Surface(
                        color = Slate800.copy(alpha = 0.9f),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Emerald500),
                        modifier = Modifier.clickable { showBarcodeSheet = true }
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                        ) {
                            Icon(Icons.Default.QrCode, contentDescription = null, tint = Emerald400, modifier = Modifier.size(16.dp))
                            Text(
                                text = "${uiState.linkedProductName ?: uiState.linkedBarcode} (${uiState.linkedBarcode})",
                                color = Emerald400,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            IconButton(
                                onClick = { viewModel.clearLinkedProduct() },
                                modifier = Modifier.size(18.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = Slate400, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                } else {
                    OutlinedButton(
                        onClick = { showBarcodeSheet = true },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300),
                        border = BorderStroke(1.dp, Slate700),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Default.QrCodeScanner, contentDescription = null, tint = Cyan400, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.btn_link_barcode_sku), fontSize = 12.sp, color = Slate200)
                    }
                }
            }

            // Visible Segmented Controls: [CPU NEON | GPU Vulkan] & [320px Fast | 640px HD]
            Surface(
                color = Slate900.copy(alpha = 0.90f),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, Slate700)
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Backend Segmented Control
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate800)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (!uiState.isVulkanBackend) Amber500 else Color.Transparent)
                                .clickable { if (uiState.isVulkanBackend) viewModel.toggleBackend() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "CPU NEON",
                                color = if (!uiState.isVulkanBackend) Slate900 else Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (uiState.isVulkanBackend) Cyan500 else Color.Transparent)
                                .clickable { if (!uiState.isVulkanBackend) viewModel.toggleBackend() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "GPU Vulkan",
                                color = if (uiState.isVulkanBackend) Slate900 else Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Resolution Segmented Control
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .background(Slate800)
                            .padding(2.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (uiState.targetResolution == 320) Emerald500 else Color.Transparent)
                                .clickable { if (uiState.targetResolution != 320) viewModel.toggleResolution() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "320px Fast",
                                color = if (uiState.targetResolution == 320) Slate900 else Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (uiState.targetResolution == 640) Emerald500 else Color.Transparent)
                                .clickable { if (uiState.targetResolution != 640) viewModel.toggleResolution() }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                text = "640px HD",
                                color = if (uiState.targetResolution == 640) Slate900 else Slate400,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
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
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (uiState.isPaused) stringResource(R.string.btn_resume) else stringResource(R.string.btn_pause),
                        color = if (uiState.isPaused) Slate900 else Slate100,
                        fontSize = 12.sp
                    )
                }

                OutlinedButton(
                    onClick = { viewModel.resetCounter() },
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.btn_reset_counter), color = Slate100, fontSize = 12.sp)
                }

                Button(
                    onClick = {
                        viewModel.updateCapturedBitmap(previewViewRef?.bitmap)
                        showSaveDialog = true
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Default.Save, contentDescription = null, tint = Slate900, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(text = stringResource(R.string.btn_save), color = Slate900, fontWeight = FontWeight.Bold, fontSize = 12.sp)
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

        // Save Live Scan Session Dialog
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
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        // SKU & Variance Details (if SKU is linked)
                        if (uiState.linkedBarcode != null) {
                            Surface(
                                color = Slate900,
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, Emerald500.copy(alpha = 0.5f)),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = uiState.linkedProductName ?: uiState.linkedBarcode!!,
                                        color = Emerald400,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 13.sp
                                    )
                                    Text(
                                        text = "${stringResource(R.string.lbl_barcode_sku)}: ${uiState.linkedBarcode}",
                                        color = Slate400,
                                        fontSize = 11.sp
                                    )
                                    uiState.linkedExpectedStock?.let { expected ->
                                        val variance = uiState.variance ?: (uiState.totalCountWithDepth - expected)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${stringResource(R.string.lbl_expected_stock)}: $expected",
                                                color = Slate300,
                                                fontSize = 11.sp
                                            )
                                            Text(
                                                text = "${stringResource(R.string.lbl_variance)}: ${if (variance >= 0) "+$variance" else "$variance"}",
                                                color = if (variance >= 0) Emerald400 else Rose500,
                                                fontWeight = FontWeight.Bold,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                    uiState.totalFinancialValue?.let { value ->
                                        Text(
                                            text = "${stringResource(R.string.lbl_total_value)}: $${String.format(java.util.Locale.US, "%.2f", value)}",
                                            color = Cyan400,
                                            fontWeight = FontWeight.SemiBold,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        // Shelf Depth Multiplier Stepper & Total Units Summary
                        Surface(
                            color = Slate900,
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.dp, Slate700),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = stringResource(R.string.lbl_shelf_depth),
                                        color = Slate300,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    CompactDepthStepper(
                                        depth = uiState.depthMultiplier,
                                        onDepthChange = { viewModel.setDepthMultiplier(it) }
                                    )
                                }
                                val summaryText = if (uiState.depthMultiplier > 1) {
                                    stringResource(
                                        R.string.calc_summary,
                                        uiState.frontFacings,
                                        uiState.depthMultiplier,
                                        uiState.totalCountWithDepth
                                    )
                                } else {
                                    stringResource(R.string.txt_total_units_simple, uiState.frontFacings)
                                }
                                Text(
                                    text = summaryText,
                                    color = Emerald400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

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
                            val syncPrefs = SyncPreferences(context)
                            val syncConfig = syncPrefs.getSyncConfig()
                            val initialStatus = if (syncConfig.enabled && syncConfig.autoSyncOnSave) "QUEUED" else "LOCAL_ONLY"

                            viewModel.saveSession(
                                shelfLocation = shelfInput,
                                notes = notesInput,
                                initialSyncStatus = initialStatus,
                                context = context
                            ) {
                                if (initialStatus == "QUEUED") {
                                    SyncAuditWorker.enqueueOneTimeSync(context)
                                }
                                showSaveDialog = false
                                viewModel.resetCounter()
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

        // Barcode / SKU Scan & Lookup Sheet
        if (showBarcodeSheet) {
            BarcodeScanSheet(
                initialBarcode = uiState.linkedBarcode,
                initialProductName = uiState.linkedProductName,
                initialExpectedStock = uiState.linkedExpectedStock,
                initialUnitPrice = uiState.linkedUnitPrice,
                onDismiss = { showBarcodeSheet = false },
                onConfirm = { barcode, productName, brand, stock, price ->
                    viewModel.linkSku(barcode, productName, brand, stock, price)
                    showBarcodeSheet = false
                }
            )
        }
    }
}
