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
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val backendName = if (telemetry.isVulkanActive) {
        stringResource(R.string.vulkan_gpu)
    } else {
        stringResource(R.string.cpu_neon)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(20.dp))
            .background(Slate900.copy(alpha = 0.85f))
            .clickable { expanded = !expanded }
            .padding(horizontal = 12.dp, vertical = 6.dp),
        contentAlignment = Alignment.Center
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

            // Primary display
            Text(
                text = String.format(
                    java.util.Locale.US,
                    "%.1f FPS | %.0f ms",
                    telemetry.fps,
                    telemetry.inferenceLatencyMs
                ),
                color = Slate100,
                fontSize = 12.sp
            )

            AnimatedVisibility(visible = expanded) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "|", color = Slate600, fontSize = 12.sp)
                    Text(
                        text = backendName,
                        color = if (telemetry.isVulkanActive) Cyan400 else Amber500,
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}
