package com.shelfpulse.ai.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfpulse.ai.R
import com.shelfpulse.ai.ui.theme.*

@Composable
fun CompactDepthStepper(
    depth: Int,
    onDepthChange: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Slate800,
        shape = RoundedCornerShape(10.dp),
        border = BorderStroke(1.dp, Slate700),
        modifier = modifier
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        ) {
            IconButton(
                onClick = { if (depth > 1) onDepthChange(depth - 1) },
                enabled = depth > 1,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Remove,
                    contentDescription = "Decrease Depth",
                    tint = if (depth > 1) Emerald400 else Slate600,
                    modifier = Modifier.size(16.dp)
                )
            }

            Text(
                text = stringResource(R.string.depth_stepper, depth),
                color = Slate100,
                fontSize = 12.sp
            )

            IconButton(
                onClick = { if (depth < 20) onDepthChange(depth + 1) },
                enabled = depth < 20,
                modifier = Modifier.size(28.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Increase Depth",
                    tint = if (depth < 20) Emerald400 else Slate600,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
