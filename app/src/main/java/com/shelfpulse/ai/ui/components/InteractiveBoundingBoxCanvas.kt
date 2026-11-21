package com.shelfpulse.ai.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
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
    showLabels: Boolean = false,
    selectedBoxIndex: Int? = null,
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

                            val viewport = CoordinateMapper.computeFitViewport(
                                imageWidth = frameWidth,
                                imageHeight = frameHeight,
                                canvasWidth = size.width.toFloat(),
                                canvasHeight = size.height.toFloat()
                            )

                            val normTap = CoordinateMapper.mapTapToNormalizedImage(
                                tapX = tapOffset.x,
                                tapY = tapOffset.y,
                                viewport = viewport
                            ) ?: return@detectTapGestures

                            val (imgX, imgY) = normTap

                            // Check if tapped inside an existing box (reverse order for top-most)
                            var hitIndex = -1
                            for (i in detections.indices.reversed()) {
                                if (detections[i].containsPoint(imgX, imgY, tolerance = 0.025f)) {
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

        val viewport = CoordinateMapper.computeFitViewport(
            imageWidth = frameWidth,
            imageHeight = frameHeight,
            canvasWidth = size.width,
            canvasHeight = size.height
        )

        if (viewport.drawnWidth <= 0f || viewport.drawnHeight <= 0f) return@Canvas

        for ((index, det) in detections.withIndex()) {
            val boxRect = CoordinateMapper.mapBoxToCanvas(det.x1, det.y1, det.x2, det.y2, viewport)
            val left = boxRect.left
            val top = boxRect.top
            val right = boxRect.right
            val bottom = boxRect.bottom
            val width = boxRect.width
            val height = boxRect.height

            val isSelected = (index == selectedBoxIndex)

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
                    style = Stroke(width = 1.5.dp.toPx())
                )
                // Draw 'X' across excluded box
                drawLine(
                    color = Rose500,
                    start = Offset(left, top),
                    end = Offset(right, bottom),
                    strokeWidth = 1.5.dp.toPx()
                )
            } else if (det.isManualMarker) {
                // Manual Marker (+1): Cyan Circle + Crosshair (or Amber if selected)
                val cx = (left + right) / 2f
                val cy = (top + bottom) / 2f
                val r = (minOf(width, height) / 2f).coerceIn(12.dp.toPx(), 22.dp.toPx())

                val markerColor = if (isSelected) Amber400 else Cyan400

                drawCircle(
                    color = markerColor.copy(alpha = 0.25f),
                    center = Offset(cx, cy),
                    radius = r
                )
                drawCircle(
                    color = markerColor,
                    center = Offset(cx, cy),
                    radius = r,
                    style = Stroke(width = 1.5.dp.toPx())
                )
                drawLine(
                    color = White,
                    start = Offset(cx - r * 0.5f, cy),
                    end = Offset(cx + r * 0.5f, cy),
                    strokeWidth = 1.5.dp.toPx()
                )
                drawLine(
                    color = White,
                    start = Offset(cx, cy - r * 0.5f),
                    end = Offset(cx, cy + r * 0.5f),
                    strokeWidth = 1.5.dp.toPx()
                )

                if (showLabels || isSelected) {
                    val labelText = "+1"
                    val textLayoutResult = textMeasurer.measure(
                        text = labelText,
                        style = TextStyle(
                            color = White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    )
                    val badgeW = textLayoutResult.size.width + 8.dp.toPx()
                    val badgeH = textLayoutResult.size.height + 4.dp.toPx()
                    val badgeTop = (cy - r - badgeH).coerceAtLeast(viewport.top)

                    drawRoundRect(
                        color = Slate900.copy(alpha = 0.85f),
                        topLeft = Offset(cx - badgeW / 2f, badgeTop),
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )
                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(cx - textLayoutResult.size.width / 2f, badgeTop + 2.dp.toPx())
                    )
                }
            } else {
                // Active auto-detected box: Emerald for standard, Cyan for tracked, Amber for selected
                val boxColor = if (isSelected) Amber400 else if (det.trackId >= 0) Cyan400 else Emerald400

                // Fill with subtle translucent tint
                drawRect(
                    color = boxColor.copy(alpha = if (isSelected) 0.18f else 0.08f),
                    topLeft = Offset(left, top),
                    size = Size(width, height)
                )

                // Crisp 1.5dp corner brackets
                drawCornerBrackets(
                    left = left,
                    top = top,
                    right = right,
                    bottom = bottom,
                    color = boxColor,
                    bracketLength = 10.dp.toPx(),
                    strokeWidth = 1.5.dp.toPx()
                )

                // Draw subtle thin outline when selected
                if (isSelected) {
                    drawRect(
                        color = Amber400.copy(alpha = 0.5f),
                        topLeft = Offset(left, top),
                        size = Size(width, height),
                        style = Stroke(width = 1.dp.toPx())
                    )
                }

                // Show confidence badge only when labels enabled or box is selected
                if (showLabels || isSelected) {
                    val badgeText = if (det.trackId >= 0) {
                        "#${det.trackId} ${(det.score * 100).toInt()}%"
                    } else {
                        "${(det.score * 100).toInt()}%"
                    }

                    val textLayoutResult = textMeasurer.measure(
                        text = badgeText,
                        style = TextStyle(
                            color = if (isSelected) Slate950 else White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    )

                    val badgeW = textLayoutResult.size.width + 8.dp.toPx()
                    val badgeH = textLayoutResult.size.height + 4.dp.toPx()
                    val badgeTop = (top - badgeH).coerceAtLeast(viewport.top)

                    drawRoundRect(
                        color = if (isSelected) Amber400 else Slate900.copy(alpha = 0.85f),
                        topLeft = Offset(left, badgeTop),
                        size = Size(badgeW, badgeH),
                        cornerRadius = CornerRadius(4.dp.toPx(), 4.dp.toPx())
                    )

                    drawText(
                        textLayoutResult = textLayoutResult,
                        topLeft = Offset(left + 4.dp.toPx(), badgeTop + 2.dp.toPx())
                    )
                }
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
