package com.shelfpulse.ai.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfpulse.ai.R
import com.shelfpulse.ai.domain.model.Telemetry
import com.shelfpulse.ai.ui.theme.*

@Composable
fun TelemetryChip(
    telemetry: Telemetry,
    modifier: Modifier = Modifier,
    onToggleBackend: (() -> Unit)? = null,
    onToggleResolution: (() -> Unit)? = null
) {
    var expanded by remember { mutableStateOf(false) }

    val backendName = if (telemetry.isVulkanActive) {
        stringResource(R.string.vulkan_gpu)
    } else {
        stringResource(R.string.cpu_neon)
    }

    val displayLatency = if (telemetry.totalLatencyMs > 0f) telemetry.totalLatencyMs else telemetry.inferenceLatencyMs

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Slate900.copy(alpha = 0.85f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Pulse indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (telemetry.isVulkanActive) Cyan400 else Amber500)
                )

                // Primary display (Smoothed Wall-clock FPS + Total Wall Latency)
                Text(
                    text = String.format(
                        java.util.Locale.US,
                        "%.1f FPS | %.1f ms",
                        telemetry.fps,
                        displayLatency
                    ),
                    color = Slate100,
                    fontSize = 12.sp
                )

                Text(
                    text = "|",
                    color = Slate600,
                    fontSize = 12.sp
                )

                // Clickable backend badge
                Text(
                    text = backendName,
                    color = if (telemetry.isVulkanActive) Cyan400 else Amber500,
                    fontSize = 12.sp,
                    modifier = Modifier.clickable(enabled = onToggleBackend != null) {
                        onToggleBackend?.invoke()
                    }
                )
            }

            // Detailed Stage breakdown: Pre | Inf | Trk
            AnimatedVisibility(visible = expanded) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = String.format(
                            java.util.Locale.US,
                            "Pre: %.1fms | Inf: %.1fms | Trk: %.1fms",
                            telemetry.preprocessMs,
                            telemetry.inferenceLatencyMs,
                            telemetry.trackMs
                        ),
                        color = Slate400,
                        fontSize = 10.sp
                    )

                    if (onToggleResolution != null) {
                        Text(
                            text = "Target Size: ${telemetry.targetResolution}px (Tap to toggle)",
                            color = Cyan400,
                            fontSize = 10.sp,
                            modifier = Modifier.clickable { onToggleResolution() }
                        )
                    }
                }
            }
        }
    }
}
