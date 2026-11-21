package com.shelfpulse.ai.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfpulse.ai.R
import com.shelfpulse.ai.domain.model.InventorySession
import com.shelfpulse.ai.ui.theme.*
import com.shelfpulse.ai.ui.viewmodel.ExportFormat
import com.shelfpulse.ai.ui.viewmodel.HistoryViewModel
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = stringResource(R.string.history_title),
            style = MaterialTheme.typography.headlineMedium,
            color = Slate100,
            fontWeight = FontWeight.Bold
        )

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
                        onDelete = { viewModel.deleteSession(session.id) }
                    )
                }
            }
        }
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
    onDelete: () -> Unit,
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
            // Header Row: Shelf Location & Count Badge
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

                Surface(
                    color = Emerald500.copy(alpha = 0.15f),
                    shape = RoundedCornerShape(12.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Emerald500)
                ) {
                    Text(
                        text = stringResource(R.string.txt_items_counted, session.totalCount),
                        color = Emerald400,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
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
