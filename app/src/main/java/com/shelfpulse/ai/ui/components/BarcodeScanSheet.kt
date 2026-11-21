package com.shelfpulse.ai.ui.components

import androidx.camera.core.*
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.shelfpulse.ai.R
import com.shelfpulse.ai.data.barcode.BarcodeScannerHelper
import com.shelfpulse.ai.data.remote.OpenFoodFactsClient
import com.shelfpulse.ai.ui.theme.*
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BarcodeScanSheet(
    initialBarcode: String? = null,
    initialProductName: String? = null,
    initialExpectedStock: Int? = null,
    initialUnitPrice: Double? = null,
    onDismiss: () -> Unit,
    onConfirm: (barcode: String, productName: String, brand: String, expectedStock: Int, unitPrice: Double) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()

    var barcode by remember { mutableStateOf(initialBarcode ?: "") }
    var productName by remember { mutableStateOf(initialProductName ?: "") }
    var brand by remember { mutableStateOf("") }
    var expectedStockStr by remember { mutableStateOf(initialExpectedStock?.toString() ?: "") }
    var unitPriceStr by remember { mutableStateOf(initialUnitPrice?.toString() ?: "") }

    var isLiveCameraActive by remember { mutableStateOf(false) }
    var isLookingUp by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isError by remember { mutableStateOf(false) }

    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            cameraExecutor.shutdown()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Slate600) },
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Title Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text(
                        text = stringResource(R.string.sheet_barcode_title),
                        color = Slate100,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(R.string.sheet_barcode_subtitle),
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }

                // Camera Scan Toggle Button
                IconButton(
                    onClick = { isLiveCameraActive = !isLiveCameraActive },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isLiveCameraActive) Emerald500 else Slate800,
                        contentColor = if (isLiveCameraActive) Slate900 else Emerald400
                    )
                ) {
                    Icon(
                        imageVector = if (isLiveCameraActive) Icons.Default.Close else Icons.Default.QrCodeScanner,
                        contentDescription = stringResource(R.string.btn_toggle_camera)
                    )
                }
            }

            // Live CameraX Mini Scanner
            AnimatedVisibility(visible = isLiveCameraActive) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .border(1.5.dp, Emerald500, RoundedCornerShape(16.dp))
                ) {
                    AndroidView(
                        factory = { ctx ->
                            val previewView = PreviewView(ctx).apply {
                                scaleType = PreviewView.ScaleType.FILL_CENTER
                            }
                            val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                            cameraProviderFuture.addListener({
                                val cameraProvider = cameraProviderFuture.get()
                                val preview = Preview.Builder().build().also {
                                    it.setSurfaceProvider(previewView.surfaceProvider)
                                }

                                val imageAnalysis = ImageAnalysis.Builder()
                                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                    .build()

                                imageAnalysis.setAnalyzer(cameraExecutor) { imageProxy ->
                                    try {
                                        val detected = BarcodeScannerHelper.scanImage(imageProxy)
                                        if (detected != null && detected.isNotBlank()) {
                                            coroutineScope.launch {
                                                barcode = detected
                                                isLiveCameraActive = false
                                                statusMessage = context.getString(R.string.msg_barcode_detected, detected)
                                                isError = false
                                            }
                                        }
                                    } finally {
                                        imageProxy.close()
                                    }
                                }

                                try {
                                    cameraProvider.unbindAll()
                                    cameraProvider.bindToLifecycle(
                                        lifecycleOwner,
                                        CameraSelector.DEFAULT_BACK_CAMERA,
                                        preview,
                                        imageAnalysis
                                    )
                                } catch (e: Exception) {
                                    android.util.Log.e("BarcodeScanSheet", "Camera bind error", e)
                                }
                            }, ContextCompat.getMainExecutor(ctx))
                            previewView
                        },
                        modifier = Modifier.fillMaxSize()
                    )

                    // Target scanning reticle
                    Surface(
                        color = Emerald500.copy(alpha = 0.2f),
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(2.dp, Emerald400),
                        modifier = Modifier
                            .size(width = 220.dp, height = 100.dp)
                            .align(Alignment.Center)
                    ) {}
                }
            }

            // Barcode Input + Open Food Facts Lookup Button
            OutlinedTextField(
                value = barcode,
                onValueChange = { barcode = it },
                label = { Text(stringResource(R.string.lbl_barcode_sku)) },
                placeholder = { Text("e.g., 5449000000996") },
                trailingIcon = {
                    IconButton(
                        onClick = {
                            if (barcode.isNotBlank()) {
                                isLookingUp = true
                                statusMessage = null
                                coroutineScope.launch {
                                    val result = OpenFoodFactsClient.lookupProduct(barcode)
                                    isLookingUp = false
                                    result.onSuccess { lookup ->
                                        productName = lookup.productName
                                        brand = lookup.brand
                                        statusMessage = context.getString(R.string.msg_product_found, lookup.productName)
                                        isError = false
                                    }.onFailure { err ->
                                        statusMessage = err.message ?: context.getString(R.string.msg_product_not_found)
                                        isError = true
                                    }
                                }
                            }
                        },
                        enabled = barcode.isNotBlank() && !isLookingUp
                    ) {
                        if (isLookingUp) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Emerald400)
                        } else {
                            Icon(
                                imageVector = Icons.Default.CloudDownload,
                                contentDescription = stringResource(R.string.btn_lookup_online),
                                tint = Emerald400
                            )
                        }
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald500,
                    unfocusedBorderColor = Slate700,
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Status message
            if (statusMessage != null) {
                Text(
                    text = statusMessage!!,
                    color = if (isError) Rose500 else Emerald400,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }

            // Product Name & Brand
            OutlinedTextField(
                value = productName,
                onValueChange = { productName = it },
                label = { Text(stringResource(R.string.lbl_product_name)) },
                placeholder = { Text("e.g., Coca-Cola 330ml Can") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald500,
                    unfocusedBorderColor = Slate700,
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = brand,
                onValueChange = { brand = it },
                label = { Text(stringResource(R.string.lbl_product_brand)) },
                placeholder = { Text("e.g., The Coca-Cola Company") },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Emerald500,
                    unfocusedBorderColor = Slate700,
                    focusedTextColor = Slate100,
                    unfocusedTextColor = Slate100
                ),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            // Expected Stock & Unit Price
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = expectedStockStr,
                    onValueChange = { expectedStockStr = it.filter { char -> char.isDigit() } },
                    label = { Text(stringResource(R.string.lbl_expected_stock)) },
                    placeholder = { Text("24") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate100
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )

                OutlinedTextField(
                    value = unitPriceStr,
                    onValueChange = { unitPriceStr = it.filter { char -> char.isDigit() || char == '.' } },
                    label = { Text(stringResource(R.string.lbl_unit_price)) },
                    placeholder = { Text("1.50") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500,
                        unfocusedBorderColor = Slate700,
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate100
                    ),
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
            }

            // Bottom Actions
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onDismiss,
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Slate300),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.btn_cancel))
                }

                Button(
                    onClick = {
                        val stock = expectedStockStr.toIntOrNull() ?: 0
                        val price = unitPriceStr.toDoubleOrNull() ?: 0.0
                        onConfirm(barcode.trim(), productName.trim(), brand.trim(), stock, price)
                    },
                    enabled = barcode.isNotBlank() || productName.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = stringResource(R.string.btn_link_sku_confirm),
                        color = Slate900,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
