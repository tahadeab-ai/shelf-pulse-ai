package com.shelfpulse.ai.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfpulse.ai.R
import com.shelfpulse.ai.data.sync.SyncAuditWorker
import com.shelfpulse.ai.data.sync.SyncPreferences
import com.shelfpulse.ai.ui.components.BarcodeScanSheet
import com.shelfpulse.ai.ui.components.CompactDepthStepper
import com.shelfpulse.ai.ui.components.InteractiveBoundingBoxCanvas
import com.shelfpulse.ai.ui.components.TelemetryChip
import com.shelfpulse.ai.ui.theme.*
import com.shelfpulse.ai.ui.viewmodel.VideoAuditViewModel

@Composable
fun VideoAuditScreen(
    viewModel: VideoAuditViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val uiState by viewModel.uiState.collectAsState()

    var showBarcodeSheet by remember { mutableStateOf(false) }
    var showSaveDialog by remember { mutableStateOf(false) }
    var shelfInput by remember { mutableStateOf("") }
    var notesInput by remember { mutableStateOf("") }

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setVideoUri(context, uri)
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
    ) {
        if (uiState.videoUri == null) {
            // Empty State: Select Video
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                verticalArrangement = Arrangement.Center,
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Surface(
                    color = Slate800,
                    shape = RoundedCornerShape(24.dp),
                    border = BorderStroke(1.5.dp, Slate700),
                    modifier = Modifier.padding(16.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(28.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Movie,
                            contentDescription = null,
                            tint = Emerald400,
                            modifier = Modifier.size(64.dp)
                        )
                        Text(
                            text = stringResource(R.string.video_audit_title),
                            color = Slate100,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = stringResource(R.string.txt_select_video_prompt),
                            color = Slate400,
                            fontSize = 14.sp,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center
                        )
                        Button(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                            shape = RoundedCornerShape(12.dp)
                        ) {
                            Icon(Icons.Default.Upload, contentDescription = null, tint = Slate900)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_import_video),
                                color = Slate900,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        } else {
            // Video Loaded Viewport & Audit Controls
            Box(modifier = Modifier.fillMaxSize()) {
                val previewBitmap = uiState.currentPreviewBitmap
                if (previewBitmap != null) {
                    Image(
                        bitmap = previewBitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Fit
                    )

                    InteractiveBoundingBoxCanvas(
                        detections = uiState.currentDetections,
                        frameWidth = previewBitmap.width,
                        frameHeight = previewBitmap.height,
                        isInteractive = false,
                        showLabels = true,
                        onTapBox = {},
                        onTapEmptyArea = { _, _ -> },
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Movie,
                                contentDescription = null,
                                tint = Slate600,
                                modifier = Modifier.size(48.dp)
                            )
                            Text(
                                text = stringResource(R.string.txt_video_ready),
                                color = Slate300,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = String.format(
                                    java.util.Locale.US,
                                    stringResource(R.string.txt_video_duration),
                                    uiState.videoDurationMs / 1000f,
                                    uiState.totalFrames
                                ),
                                color = Slate500,
                                fontSize = 13.sp
                            )
                        }
                    }
                }

                // Top Progress & Count HUD
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // Count Badges, Depth Stepper & Resolution Toggle Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Unique Products Tracked Badge
                        Surface(
                            color = Slate900.copy(alpha = 0.9f),
                            shape = RoundedCornerShape(20.dp),
                            border = BorderStroke(1.5.dp, Emerald500)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${uiState.frontFacings}",
                                    color = Emerald400,
                                    fontSize = 20.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = stringResource(R.string.txt_unique_products),
                                    color = Slate100,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Compact Depth Stepper
                        CompactDepthStepper(
                            depth = uiState.depthMultiplier,
                            onDepthChange = { viewModel.setDepthMultiplier(it) }
                        )

                        // Live Frame Detection Badge & Resolution Toggle
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Live Frame Detections Badge
                            Surface(
                                color = Slate900.copy(alpha = 0.9f),
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(1.dp, Cyan400)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Text(
                                        text = "${uiState.currentDetections.size}",
                                        color = Cyan400,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = stringResource(R.string.txt_live_frame, uiState.currentDetections.size),
                                        color = Slate300,
                                        fontSize = 11.sp
                                    )
                                }
                            }

                            // Resolution Toggle Button [320px Fast | 640px HD]
                            Surface(
                                color = if (uiState.targetResolution == 640) Emerald500.copy(alpha = 0.2f) else Slate800,
                                shape = RoundedCornerShape(16.dp),
                                border = BorderStroke(
                                    1.dp,
                                    if (uiState.targetResolution == 640) Emerald500 else Slate600
                                ),
                                modifier = Modifier.clickable {
                                    if (!uiState.isAuditing) {
                                        viewModel.toggleResolution()
                                    }
                                }
                            ) {
                                Text(
                                    text = if (uiState.targetResolution == 640) stringResource(R.string.res_640_hd) else stringResource(R.string.res_320_fast),
                                    color = if (uiState.targetResolution == 640) Emerald400 else Slate300,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
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

                    // Linear Frame Progress Bar
                    if (uiState.totalFrames > 0) {
                        val progress = if (uiState.totalFrames > 0) {
                            uiState.currentFrame.toFloat() / uiState.totalFrames.toFloat()
                        } else 0f

                        Column(
                            modifier = Modifier
                                .fillMaxWidth(0.85f)
                                .clip(RoundedCornerShape(8.dp))
                                .background(Slate800.copy(alpha = 0.85f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            LinearProgressIndicator(
                                progress = { progress.coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(6.dp)
                                    .clip(RoundedCornerShape(3.dp)),
                                color = Emerald500,
                                trackColor = Slate700
                            )
                            Text(
                                text = "${uiState.currentFrame} / ${uiState.totalFrames} frames (${(progress * 100).toInt()}%)",
                                color = Slate400,
                                fontSize = 11.sp
                            )
                        }
                    }

                    if (uiState.isAuditing) {
                        TelemetryChip(telemetry = uiState.telemetry)
                    }
                }

                // Bottom Action Bar
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .align(Alignment.BottomCenter)
                        .background(Slate900.copy(alpha = 0.95f))
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (uiState.isAuditing) {
                        Button(
                            onClick = { viewModel.cancelAudit() },
                            colors = ButtonDefaults.buttonColors(containerColor = Rose600),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Stop, contentDescription = null, tint = Slate100)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = stringResource(R.string.btn_cancel_audit),
                                color = Slate100,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    } else {
                        // Change Video Button
                        OutlinedButton(
                            onClick = {
                                videoPickerLauncher.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                                )
                            },
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate100),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Movie, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(text = stringResource(R.string.btn_import_video), fontSize = 13.sp)
                        }

                        if (!uiState.isComplete) {
                            // Start Audit Button
                            Button(
                                onClick = { viewModel.startAudit(context) },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Slate900, modifier = Modifier.size(20.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_start_audit),
                                    color = Slate900,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        } else {
                            // Save Completed Audit Button
                            Button(
                                onClick = { showSaveDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = null, tint = Slate900, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = stringResource(R.string.btn_save_session),
                                    color = Slate900,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Save Completed Video Audit Session Dialog
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
