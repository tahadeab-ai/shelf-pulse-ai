package com.shelfpulse.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.shelfpulse.ai.domain.model.DetectionResult
import com.shelfpulse.ai.ui.theme.*

@OptIn(ExperimentalTextApi::class)
@Composable
fun InteractiveBoundingBoxCanvas(
    detections: List<DetectionResult>,
    frameWidth: Int,
    frameHeight: Int,
    isInteractive: Boolean = false,
    onTapBox: (index: Int) -> Unit = {},
    onTapEmptyArea: (imgX: Float, imgY: Float) -> Unit = { _, _ -> },
    modifier: Modifier = Modifier
) {
    val textMeasurer = rememberTextMeasurer()

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (isInteractive) {
                    Modifier.pointerInput(detections, frameWidth, frameHeight) {
                        detectTapGestures { tapOffset ->
                            if (frameWidth <= 0 || frameHeight <= 0) return@detectTapGestures

                            val scaleX = size.width / frameWidth.toFloat()
                            val scaleY = size.height / frameHeight.toFloat()

                            val imgX = tapOffset.x / scaleX
                            val imgY = tapOffset.y / scaleY

                            // Check if tapped inside an existing box (reverse order for top-most)
                            var hitIndex = -1
                            for (i in detections.indices.reversed()) {
                                if (detections[i].containsPoint(imgX, imgY, tolerance = 25f)) {
                                    hitIndex = i
                                    break
                                }
                            }

                            if (hitIndex != -1) {
                                onTapBox(hitIndex)
                            } else {
                                onTapEmptyArea(imgX, imgY)
                            }
                        }
                    }
                } else Modifier
            )
    ) {
        if (frameWidth <= 0 || frameHeight <= 0) return@Canvas

        val scaleX = size.width / frameWidth.toFloat()
        val scaleY = size.height / frameHeight.toFloat()

        for ((index, det) in detections.withIndex()) {
            val left = det.x1 * scaleX
            val top = det.y1 * scaleY
            val right = det.x2 * scaleX
            val bottom = det.y2 * scaleY
            val width = (right - left).coerceAtLeast(0f)
            val height = (bottom - top).coerceAtLeast(0f)

            if (det.isExcluded) {
                // Dimmed / Rose border for removed items (-1)
                drawRect(
                    color = Rose500.copy(alpha = 0.2f),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )
                drawRect(
                    color = Rose500,
                    topLeft = Offset(left, top),
                    size = Size(width, height),
                    style = Stroke(width = 2.dp.toPx())
                )
                // Draw 'X' across excluded box
                drawLine(
                    color = Rose500,
                    start = Offset(left, top),
                    end = Offset(right, bottom),
                    strokeWidth = 1.5.dp.toPx()
                )
            } else if (det.isManualMarker) {
                // Manual Marker (+1): Emerald Circle + Crosshair
                val cx = det.centerX * scaleX
                val cy = det.centerY * scaleY
                val r = 16.dp.toPx()

                drawCircle(
                    color = Emerald500.copy(alpha = 0.35f),
                    center = Offset(cx, cy),
                    radius = r
                )
                drawCircle(
                    color = Emerald500,
                    center = Offset(cx, cy),
                    radius = r,
                    style = Stroke(width = 2.5.dp.toPx())
                )
                drawLine(
                    color = White,
                    start = Offset(cx - r * 0.6f, cy),
                    end = Offset(cx + r * 0.6f, cy),
                    strokeWidth = 2.dp.toPx()
                )
                drawLine(
                    color = White,
                    start = Offset(cx, cy - r * 0.6f),
                    end = Offset(cx, cy + r * 0.6f),
                    strokeWidth = 2.dp.toPx()
                )
            } else {
                // Active auto-detected box: Cyan / Emerald
                val boxColor = if (det.trackId >= 0) Cyan400 else Emerald400

                // Fill with subtle translucent glow
                drawRect(
                    color = boxColor.copy(alpha = 0.12f),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )

                // Corner bracket accents
                drawCornerBrackets(left, top, right, bottom, boxColor, 12.dp.toPx(), 2.5.dp.toPx())

                // Label badge (Track ID or Conf)
                val badgeText = if (det.trackId >= 0) {
                    "#${det.trackId}"
                } else {
                    "${(det.score * 100).toInt()}%"
                }

                val textLayoutResult = textMeasurer.measure(
                    text = badgeText,
                    style = TextStyle(
                        color = Slate900,
                        fontSize = 11.sp,
                        fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                    )
                )

                val badgeW = textLayoutResult.size.width + 12.dp.toPx()
                val badgeH = textLayoutResult.size.height + 4.dp.toPx()
                val badgeTop = (top - badgeH).coerceAtLeast(0f)

                drawRect(
                    color = boxColor,
                    topLeft = Offset(left, badgeTop),
                    size = Size(badgeW, badgeH)
                )

                drawText(
                    textLayoutResult = textLayoutResult,
                    topLeft = Offset(left + 6.dp.toPx(), badgeTop + 2.dp.toPx())
                )
            }
        }
    }
}

private fun DrawScope.drawCornerBrackets(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    color: Color,
    bracketLength: Float,
    strokeWidth: Float
) {
    val len = bracketLength.coerceAtMost((right - left) / 2f).coerceAtMost((bottom - top) / 2f)

    // Top-Left
    drawLine(color, Offset(left, top), Offset(left + len, top), strokeWidth)
    drawLine(color, Offset(left, top), Offset(left, top + len), strokeWidth)

    // Top-Right
    drawLine(color, Offset(right, top), Offset(right - len, top), strokeWidth)
    drawLine(color, Offset(right, top), Offset(right, top + len), strokeWidth)

    // Bottom-Left
    drawLine(color, Offset(left, bottom), Offset(left + len, bottom), strokeWidth)
    drawLine(color, Offset(left, bottom), Offset(left, bottom - len), strokeWidth)

    // Bottom-Right
    drawLine(color, Offset(right, bottom), Offset(right - len, bottom), strokeWidth)
    drawLine(color, Offset(right, bottom), Offset(right, bottom - len), strokeWidth)
}
