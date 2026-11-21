package com.shelfpulse.ai.ui.components

import androidx.compose.ui.geometry.Rect

data class ImageViewport(
    val offsetX: Float,
    val offsetY: Float,
    val drawnWidth: Float,
    val drawnHeight: Float
) {
    val left: Float get() = offsetX
    val top: Float get() = offsetY
    val right: Float get() = offsetX + drawnWidth
    val bottom: Float get() = offsetY + drawnHeight

    fun contains(x: Float, y: Float): Boolean {
        return x in left..right && y in top..bottom
    }
}

object CoordinateMapper {

    fun computeFitViewport(
        imageWidth: Int,
        imageHeight: Int,
        canvasWidth: Float,
        canvasHeight: Float
    ): ImageViewport {
        if (imageWidth <= 0 || imageHeight <= 0 || canvasWidth <= 0f || canvasHeight <= 0f) {
            return ImageViewport(0f, 0f, 0f, 0f)
        }
        val scale = minOf(canvasWidth / imageWidth.toFloat(), canvasHeight / imageHeight.toFloat())
        val drawnWidth = imageWidth.toFloat() * scale
        val drawnHeight = imageHeight.toFloat() * scale
        val offsetX = (canvasWidth - drawnWidth) / 2f
        val offsetY = (canvasHeight - drawnHeight) / 2f
        return ImageViewport(offsetX, offsetY, drawnWidth, drawnHeight)
    }

    fun mapBoxToCanvas(
        x1: Float,
        y1: Float,
        x2: Float,
        y2: Float,
        viewport: ImageViewport
    ): Rect {
        val left = viewport.offsetX + x1.coerceIn(0f, 1f) * viewport.drawnWidth
        val top = viewport.offsetY + y1.coerceIn(0f, 1f) * viewport.drawnHeight
        val right = viewport.offsetX + x2.coerceIn(0f, 1f) * viewport.drawnWidth
        val bottom = viewport.offsetY + y2.coerceIn(0f, 1f) * viewport.drawnHeight
        return Rect(left, top, right, bottom)
    }

    fun mapTapToNormalizedImage(
        tapX: Float,
        tapY: Float,
        viewport: ImageViewport
    ): Pair<Float, Float>? {
        if (viewport.drawnWidth <= 0f || viewport.drawnHeight <= 0f) return null
        if (!viewport.contains(tapX, tapY)) {
            return null
        }
        val normX = ((tapX - viewport.offsetX) / viewport.drawnWidth).coerceIn(0f, 1f)
        val normY = ((tapY - viewport.offsetY) / viewport.drawnHeight).coerceIn(0f, 1f)
        return Pair(normX, normY)
    }
}

// Top-level convenience wrappers matching function-level access
fun computeFitViewport(
    imageWidth: Int,
    imageHeight: Int,
    canvasWidth: Float,
    canvasHeight: Float
): ImageViewport = CoordinateMapper.computeFitViewport(imageWidth, imageHeight, canvasWidth, canvasHeight)

fun mapBoxToCanvas(
    x1: Float,
    y1: Float,
    x2: Float,
    y2: Float,
    viewport: ImageViewport
): Rect = CoordinateMapper.mapBoxToCanvas(x1, y1, x2, y2, viewport)

fun mapTapToNormalizedImage(
    tapX: Float,
    tapY: Float,
    viewport: ImageViewport
): Pair<Float, Float>? = CoordinateMapper.mapTapToNormalizedImage(tapX, tapY, viewport)
