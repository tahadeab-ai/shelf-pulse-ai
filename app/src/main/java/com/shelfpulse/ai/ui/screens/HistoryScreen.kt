package com.shelfpulse.ai.ui.screens

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfpulse.ai.R
import com.shelfpulse.ai.data.storage.SessionPhotoStore
import com.shelfpulse.ai.data.sync.ConnectorPreset
import com.shelfpulse.ai.data.sync.SyncConfig
import com.shelfpulse.ai.data.sync.SyncPreferences
import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.ui.components.LanguageSwitcherSegmented
import com.shelfpulse.ai.ui.components.LanguageSwitcherTopBarButton
import com.shelfpulse.ai.ui.theme.*
import com.shelfpulse.ai.ui.viewmodel.ExportFormat
import com.shelfpulse.ai.ui.viewmodel.HistoryViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    viewModel: HistoryViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val sessions by viewModel.sessions.collectAsState()
    var selectedSessionForExport by remember { mutableStateOf<InventorySession?>(null) }
    var selectedPhotoSessionForViewer by remember { mutableStateOf<InventorySession?>(null) }
    var showIntegrationsSheet by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Header Row: Title, Language Switcher & Settings Gear Button
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.history_title),
                style = MaterialTheme.typography.headlineMedium,
                color = Slate100,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                LanguageSwitcherTopBarButton()

                IconButton(
                    onClick = { showIntegrationsSheet = true },
                    modifier = Modifier
                        .size(40.dp)
                        .background(Slate800, RoundedCornerShape(10.dp))
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = stringResource(R.string.sheet_integrations_title),
                        tint = Emerald400,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }

        if (sessions.isEmpty()) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Inventory2,
                        contentDescription = null,
                        tint = Slate600,
                        modifier = Modifier.size(64.dp)
                    )
                    Text(
                        text = stringResource(R.string.txt_no_records),
                        color = Slate400,
                        fontSize = 14.sp
                    )
                }
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(sessions, key = { it.id }) { session ->
                    SessionCard(
                        session = session,
                        onExportClick = { selectedSessionForExport = session },
                        onPhotoClick = { selectedPhotoSessionForViewer = session },
                        onDelete = { viewModel.deleteSession(session.id) },
                        onSyncClick = { viewModel.triggerSessionSync(context, session) }
                    )
                }
            }
        }
    }

    // Full-Screen / Dialog Shelf Photo Viewer
    selectedPhotoSessionForViewer?.let { session ->
        var fullAnnotatedBmp by remember(session.id) { mutableStateOf<Bitmap?>(null) }
        LaunchedEffect(session.id) {
            fullAnnotatedBmp = viewModel.getAnnotatedPhoto(session)
        }

        AlertDialog(
            onDismissRequest = { selectedPhotoSessionForViewer = null },
            confirmButton = {
                TextButton(onClick = { selectedPhotoSessionForViewer = null }) {
                    Text(text = stringResource(R.string.btn_close), color = Emerald400, fontWeight = FontWeight.Bold)
                }
            },
            title = {
                Text(
                    text = "${session.shelfLocation} (${session.totalCount} items)",
                    color = Slate100,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 200.dp, max = 450.dp),
                    contentAlignment = Alignment.Center
                ) {
                    val bmp = fullAnnotatedBmp
                    if (bmp != null) {
                        Image(
                            bitmap = bmp.asImageBitmap(),
                            contentDescription = "Annotated Shelf Photo",
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                        )
                    } else {
                        CircularProgressIndicator(color = Emerald500)
                    }
                }
            },
            containerColor = Slate900,
            shape = RoundedCornerShape(16.dp)
        )
    }

    // Multi-Format Export Bottom Sheet
    selectedSessionForExport?.let { session ->
        ModalBottomSheet(
            onDismissRequest = { selectedSessionForExport = null },
            containerColor = Slate900,
            dragHandle = { BottomSheetDefaults.DragHandle(color = Slate600) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp)
                    .padding(bottom = 32.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(
                    text = stringResource(R.string.sheet_export_title),
                    color = Slate100,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.sheet_export_subtitle),
                    color = Slate400,
                    fontSize = 12.sp
                )

                Spacer(modifier = Modifier.height(4.dp))

                ExportFormatRow(
                    icon = Icons.Default.PictureAsPdf,
                    title = stringResource(R.string.export_pdf_title),
                    description = stringResource(R.string.export_pdf_desc),
                    onClick = {
                        viewModel.exportAndShare(context, session, ExportFormat.PDF)
                        selectedSessionForExport = null
                    }
                )

                ExportFormatRow(
                    icon = Icons.Default.Image,
                    title = stringResource(R.string.export_image_title),
                    description = stringResource(R.string.export_image_desc),
                    onClick = {
                        viewModel.exportAndShare(context, session, ExportFormat.ANNOTATED_IMAGE)
                        selectedSessionForExport = null
                    }
                )

                ExportFormatRow(
                    icon = Icons.Default.TableChart,
                    title = stringResource(R.string.export_csv_title),
                    description = stringResource(R.string.export_csv_desc),
                    onClick = {
                        viewModel.exportAndShare(context, session, ExportFormat.CSV)
                        selectedSessionForExport = null
                    }
                )

                ExportFormatRow(
                    icon = Icons.Default.Code,
                    title = stringResource(R.string.export_json_title),
                    description = stringResource(R.string.export_json_desc),
                    onClick = {
                        viewModel.exportAndShare(context, session, ExportFormat.JSON)
                        selectedSessionForExport = null
                    }
                )
            }
        }
    }

    // ERP & Webhook Integrations Settings Sheet
    if (showIntegrationsSheet) {
        val currentConfig = remember { SyncPreferences(context).getSyncConfig() }
        IntegrationsSettingsSheet(
            initialConfig = currentConfig,
            onDismiss = { showIntegrationsSheet = false },
            onSave = { config ->
                viewModel.saveSyncConfig(context, config)
                showIntegrationsSheet = false
            },
            onTestConnection = { config, callback ->
                viewModel.testConnection(config, callback)
            }
        )
    }
}

@Composable
private fun ExportFormatRow(
    icon: ImageVector,
    title: String,
    description: String,
    onClick: () -> Unit
) {
    Surface(
        color = Slate800,
        shape = RoundedCornerShape(12.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                color = Emerald500.copy(alpha = 0.15f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Emerald400,
                        modifier = Modifier.size(22.dp)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = title,
                    color = Slate100,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = description,
                    color = Slate400,
                    fontSize = 11.sp
                )
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = Slate500,
                modifier = Modifier.size(18.dp)
            )
        }
    }
}

@Composable
private fun SessionCard(
    session: InventorySession,
    onExportClick: () -> Unit,
    onPhotoClick: (InventorySession) -> Unit,
    onDelete: () -> Unit,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val dateFormat = SimpleDateFormat("MMM dd, yyyy • HH:mm", Locale.getDefault())
    val formattedDate = dateFormat.format(Date(session.timestamp))

    Card(
        colors = CardDefaults.cardColors(containerColor = Slate800),
        shape = RoundedCornerShape(16.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Header Row: Shelf Location & Count Badge + Sync Status Pill
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = session.shelfLocation,
                        style = MaterialTheme.typography.titleMedium,
                        color = Slate100,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = formattedDate,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Slate400,
                        fontSize = 12.sp
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Sync Status Pill
                    val (syncColor, syncBg, syncText) = when (session.syncStatus) {
                        "SYNCED" -> Triple(Emerald400, Emerald500.copy(alpha = 0.15f), stringResource(R.string.sync_status_synced))
                        "QUEUED" -> Triple(Amber400, Amber500.copy(alpha = 0.15f), stringResource(R.string.sync_status_queued))
                        "FAILED" -> Triple(Rose500, Rose500.copy(alpha = 0.15f), stringResource(R.string.sync_status_failed))
                        else -> Triple(Slate400, Slate700, stringResource(R.string.sync_status_local))
                    }

                    Surface(
                        color = syncBg,
                        shape = RoundedCornerShape(8.dp),
                        border = androidx.compose.foundation.BorderStroke(0.5.dp, syncColor.copy(alpha = 0.5f)),
                        modifier = Modifier.clickable { onSyncClick() }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            val syncIcon = when (session.syncStatus) {
                                "SYNCED" -> Icons.Default.CloudDone
                                "QUEUED" -> Icons.Default.CloudSync
                                "FAILED" -> Icons.Default.SyncProblem
                                else -> Icons.Default.CloudOff
                            }
                            Icon(syncIcon, contentDescription = null, tint = syncColor, modifier = Modifier.size(11.dp))
                            Text(text = syncText, color = syncColor, fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }

                    // Count Badge
                    Surface(
                        color = Emerald500.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500)
                    ) {
                        val countText = if (session.depthMultiplier > 1) {
                            "${session.totalCount} items (${session.frontFacings}×${session.depthMultiplier})"
                        } else {
                            stringResource(R.string.txt_items_counted, session.totalCount)
                        }
                        Text(
                            text = countText,
                            color = Emerald400,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            // SKU & Variance Row (if linked)
            if (!session.productName.isNullOrBlank()) {
                Surface(
                    color = Slate900.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(10.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.productName ?: "",
                                color = Slate200,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            if (!session.barcode.isNullOrBlank()) {
                                Text(
                                    text = session.barcode ?: "",
                                    color = Slate500,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Variance Chip
                            session.variance?.let { variance ->
                                val (varColor, varBg, varText) = when {
                                    variance > 0 -> Triple(
                                        Emerald400,
                                        Emerald500.copy(alpha = 0.15f),
                                        stringResource(R.string.lbl_variance_surplus, variance)
                                    )
                                    variance < 0 -> Triple(
                                        Rose500,
                                        Rose500.copy(alpha = 0.15f),
                                        stringResource(R.string.lbl_variance_shortage, variance)
                                    )
                                    else -> Triple(
                                        Slate300,
                                        Slate700,
                                        stringResource(R.string.lbl_variance_match)
                                    )
                                }

                                Surface(
                                    color = varBg,
                                    shape = RoundedCornerShape(6.dp),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, varColor)
                                ) {
                                    Text(
                                        text = varText,
                                        color = varColor,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }

                            // Financial Value
                            session.totalFinancialValue?.let { value ->
                                Text(
                                    text = String.format(Locale.US, "$%.2f", value),
                                    color = Cyan400,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Saved Shelf Photo Thumbnail
            if (!session.photoPath.isNullOrBlank()) {
                var thumbnailBitmap by remember(session.photoPath) { mutableStateOf<Bitmap?>(null) }
                LaunchedEffect(session.photoPath) {
                    withContext(Dispatchers.IO) {
                        thumbnailBitmap = SessionPhotoStore.loadThumbnail(session.photoPath, 320, 180)
                    }
                }

                thumbnailBitmap?.let { bmp ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Slate700),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(130.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onPhotoClick(session) }
                    ) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Image(
                                bitmap = bmp.asImageBitmap(),
                                contentDescription = "Shelf Photo Thumbnail",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier.fillMaxSize()
                            )
                            Surface(
                                color = Slate900.copy(alpha = 0.75f),
                                shape = RoundedCornerShape(topStart = 8.dp),
                                modifier = Modifier.align(Alignment.BottomEnd)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Visibility,
                                        contentDescription = null,
                                        tint = Emerald400,
                                        modifier = Modifier.size(13.dp)
                                    )
                                    Text(
                                        text = stringResource(R.string.view_photo),
                                        color = Slate200,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }
                        }
                    }
                }
            }

            if (session.operatorNotes.isNotBlank()) {
                Text(
                    text = session.operatorNotes,
                    color = Slate400,
                    fontSize = 13.sp,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            HorizontalDivider(color = Slate700)

            // Actions Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onDelete) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Rose500,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Button(
                    onClick = onExportClick,
                    colors = ButtonDefaults.buttonColors(containerColor = Cyan500),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = Slate900,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = stringResource(R.string.btn_export_share),
                        color = Slate900,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun IntegrationsSettingsSheet(
    initialConfig: SyncConfig,
    onDismiss: () -> Unit,
    onSave: (SyncConfig) -> Unit,
    onTestConnection: (SyncConfig, (Result<String>) -> Unit) -> Unit
) {
    var enabled by remember { mutableStateOf(initialConfig.enabled) }
    var preset by remember { mutableStateOf(initialConfig.preset) }
    var endpointUrl by remember { mutableStateOf(initialConfig.endpointUrl) }
    var bearerToken by remember { mutableStateOf(initialConfig.bearerToken) }
    var autoSyncOnSave by remember { mutableStateOf(initialConfig.autoSyncOnSave) }

    var isTesting by remember { mutableStateOf(false) }
    var testResultMsg by remember { mutableStateOf<String?>(null) }
    var testResultIsSuccess by remember { mutableStateOf(false) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Slate900,
        dragHandle = { BottomSheetDefaults.DragHandle(color = Slate600) }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 36.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.sheet_integrations_title),
                color = Slate100,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.sheet_integrations_subtitle),
                color = Slate400,
                fontSize = 12.sp
            )

            // Language Switcher (Arabic / English)
            LanguageSwitcherSegmented()

            HorizontalDivider(color = Slate800)

            // Enable Sync Switch
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.lbl_enable_sync),
                    color = Slate200,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Switch(
                    checked = enabled,
                    onCheckedChange = { enabled = it },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Emerald400,
                        checkedTrackColor = Emerald500.copy(alpha = 0.5f)
                    )
                )
            }

            if (enabled) {
                // Preset Selector
                Text(
                    text = stringResource(R.string.lbl_preset),
                    color = Slate300,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    ConnectorPreset.entries.forEach { p ->
                        val isSelected = preset == p
                        Surface(
                            color = if (isSelected) Slate800 else Slate900,
                            shape = RoundedCornerShape(8.dp),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isSelected) Emerald500 else Slate700
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { preset = p }
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                RadioButton(
                                    selected = isSelected,
                                    onClick = { preset = p },
                                    colors = RadioButtonDefaults.colors(selectedColor = Emerald500)
                                )
                                val presetLabel = when (p) {
                                    ConnectorPreset.GENERIC_WEBHOOK -> stringResource(R.string.preset_generic_webhook)
                                    ConnectorPreset.INVENTREE_REST -> stringResource(R.string.preset_inventree)
                                    ConnectorPreset.ERPNEXT_STOCK_RECONCILIATION -> stringResource(R.string.preset_erpnext)
                                }
                                Text(text = presetLabel, color = if (isSelected) Slate100 else Slate400, fontSize = 12.sp)
                            }
                        }
                    }
                }

                // Endpoint URL
                OutlinedTextField(
                    value = endpointUrl,
                    onValueChange = { endpointUrl = it },
                    label = { Text(stringResource(R.string.lbl_endpoint_url)) },
                    placeholder = { Text(stringResource(R.string.hint_endpoint_url)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500,
                        unfocusedBorderColor = Slate600,
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate100
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Bearer Token
                OutlinedTextField(
                    value = bearerToken,
                    onValueChange = { bearerToken = it },
                    label = { Text(stringResource(R.string.lbl_bearer_token)) },
                    placeholder = { Text(stringResource(R.string.hint_bearer_token)) },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = Emerald500,
                        unfocusedBorderColor = Slate600,
                        focusedTextColor = Slate100,
                        unfocusedTextColor = Slate100
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Auto-sync Toggle
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.lbl_auto_sync),
                        color = Slate300,
                        fontSize = 13.sp
                    )
                    Switch(
                        checked = autoSyncOnSave,
                        onCheckedChange = { autoSyncOnSave = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Emerald400,
                            checkedTrackColor = Emerald500.copy(alpha = 0.5f)
                        )
                    )
                }

                // Test Connection Button & Status
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isTesting = true
                            testResultMsg = null
                            val currentConfig = SyncConfig(
                                enabled = enabled,
                                preset = preset,
                                endpointUrl = endpointUrl,
                                bearerToken = bearerToken,
                                autoSyncOnSave = autoSyncOnSave
                            )
                            onTestConnection(currentConfig) { res ->
                                isTesting = false
                                testResultIsSuccess = res.isSuccess
                                testResultMsg = if (res.isSuccess) {
                                    res.getOrNull() ?: "Success"
                                } else {
                                    res.exceptionOrNull()?.message ?: "Failed"
                                }
                            }
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Emerald400),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        if (isTesting) {
                            CircularProgressIndicator(color = Emerald400, modifier = Modifier.size(16.dp))
                        } else {
                            Icon(Icons.Default.CloudQueue, contentDescription = null, modifier = Modifier.size(16.dp))
                        }
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(text = stringResource(R.string.btn_test_connection), fontSize = 12.sp)
                    }

                    testResultMsg?.let { msg ->
                        Text(
                            text = msg,
                            color = if (testResultIsSuccess) Emerald400 else Rose500,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Save Configuration Button
            Button(
                onClick = {
                    val config = SyncConfig(
                        enabled = enabled,
                        preset = preset,
                        endpointUrl = endpointUrl,
                        bearerToken = bearerToken,
                        autoSyncOnSave = autoSyncOnSave
                    )
                    onSave(config)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = stringResource(R.string.btn_save_config),
                    color = Slate900,
                    fontWeight = FontWeight.Bold,
                    fontSize = 13.sp
                )
            }
        }
    }
}
