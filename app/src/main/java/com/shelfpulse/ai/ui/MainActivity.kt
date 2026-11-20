package com.shelfpulse.ai.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.shelfpulse.ai.R
import com.shelfpulse.ai.ShelfPulseApplication
import com.shelfpulse.ai.nativebridge.ModelAssetValidator
import com.shelfpulse.ai.nativebridge.ModelStatus
import com.shelfpulse.ai.ui.screens.*
import com.shelfpulse.ai.ui.theme.Emerald500
import com.shelfpulse.ai.ui.theme.ShelfPulseTheme
import com.shelfpulse.ai.ui.theme.Slate800
import com.shelfpulse.ai.ui.theme.Slate900
import com.shelfpulse.ai.ui.viewmodel.HistoryViewModel
import com.shelfpulse.ai.ui.viewmodel.LiveScanViewModel
import com.shelfpulse.ai.ui.viewmodel.SnapVerifyViewModel

enum class NavigationTab(val titleRes: Int, val icon: ImageVector) {
    SNAP_VERIFY(R.string.tab_snap_verify, Icons.Default.CameraAlt),
    LIVE_SCAN(R.string.tab_live_scan, Icons.Default.Videocam),
    HISTORY(R.string.tab_history, Icons.Default.History)
}

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as ShelfPulseApplication

        setContent {
            ShelfPulseTheme {
                var selectedTab by remember { mutableStateOf(NavigationTab.SNAP_VERIFY) }
                var modelStatus by remember { mutableStateOf(ModelAssetValidator.prepareModels(this)) }
                var hasCameraPermission by remember {
                    mutableStateOf(
                        ContextCompat.checkSelfPermission(
                            this,
                            Manifest.permission.CAMERA
                        ) == PackageManager.PERMISSION_GRANTED
                    )
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted ->
                    hasCameraPermission = isGranted
                }

                LaunchedEffect(Unit) {
                    if (!hasCameraPermission) {
                        permissionLauncher.launch(Manifest.permission.CAMERA)
                    }
                }

                LaunchedEffect(modelStatus) {
                    if (modelStatus.isReady) {
                        app.nativeEngine.initialize(
                            paramPath = modelStatus.paramPath!!,
                            binPath = modelStatus.binPath!!,
                            useGpu = false // Default to CPU (4 big cores, ARM NEON FP16) on Adreno 640
                        )
                    }
                }

                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!hasCameraPermission) {
                        CameraPermissionFallback(onGrant = {
                            permissionLauncher.launch(Manifest.permission.CAMERA)
                        })
                    } else if (!modelStatus.isReady) {
                        ModelDiagnosticScreen(
                            errorMessage = modelStatus.errorMessage,
                            onRetry = {
                                modelStatus = ModelAssetValidator.prepareModels(this)
                            }
                        )
                    } else {
                        val snapViewModel = remember {
                            SnapVerifyViewModel(app.nativeEngine, app.repository)
                        }
                        val liveViewModel = remember {
                            LiveScanViewModel(app.nativeEngine, app.repository)
                        }
                        val historyViewModel = remember {
                            HistoryViewModel(app.repository)
                        }

                        Scaffold(
                            bottomBar = {
                                NavigationBar(
                                    containerColor = Slate900,
                                    tonalElevation = 8.dp
                                ) {
                                    NavigationTab.values().forEach { tab ->
                                        NavigationBarItem(
                                            selected = selectedTab == tab,
                                            onClick = { selectedTab = tab },
                                            icon = {
                                                Icon(
                                                    imageVector = tab.icon,
                                                    contentDescription = stringResource(tab.titleRes)
                                                )
                                            },
                                            label = { Text(stringResource(tab.titleRes)) },
                                            colors = NavigationBarItemDefaults.colors(
                                                selectedIconColor = Slate900,
                                                indicatorColor = Emerald500
                                            )
                                        )
                                    }
                                }
                            }
                        ) { innerPadding ->
                            Box(modifier = Modifier.padding(innerPadding)) {
                                when (selectedTab) {
                                    NavigationTab.SNAP_VERIFY -> SnapVerifyScreen(snapViewModel)
                                    NavigationTab.LIVE_SCAN -> LiveScanScreen(liveViewModel)
                                    NavigationTab.HISTORY -> HistoryScreen(historyViewModel)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CameraPermissionFallback(onGrant: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Slate900)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                imageVector = Icons.Default.CameraAlt,
                contentDescription = null,
                tint = Emerald500,
                modifier = Modifier.size(64.dp)
            )
            Text(
                text = stringResource(R.string.camera_permission_required),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.bodyLarge
            )
            Button(
                onClick = onGrant,
                colors = ButtonDefaults.buttonColors(containerColor = Emerald500)
            ) {
                Text(text = stringResource(R.string.btn_grant_permission), color = Slate900)
            }
        }
    }
}
