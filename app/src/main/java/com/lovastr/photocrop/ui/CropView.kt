package com.lovastr.photocrop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.PointerInputChange
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.lovastr.photocrop.edit.NormRect

private enum class Drag { TopLeft, TopRight, BottomLeft, BottomRight, Move }

private const val MIN_SIZE = 0.1f

/**
 * Shows the image. When [showCrop] is true a draggable crop rectangle is overlaid
 * (corner handles resize, dragging inside moves). [colorMatrix] null means "no color filter".
 */
@Composable
fun CropView(
    image: ImageBitmap,
    crop: NormRect,
    colorMatrix: FloatArray?,
    showCrop: Boolean,
    onCropChange: (NormRect) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCrop by rememberUpdatedState(crop)
    val currentOnChange by rememberUpdatedState(onCropChange)
    val touchRadius = with(LocalDensity.current) { 40.dp.toPx() }
    val handleRadius = with(LocalDensity.current) { 8.dp.toPx() }
    val strokePx = with(LocalDensity.current) { 2.dp.toPx() }
    // Margin around the image so corner handles (and their touch areas) aren't cut off at the image edge.
    val pad = with(LocalDensity.current) { 24.dp.toPx() }

    // The outer box receives touches (including those in the margin); the canvas inside is the image.
    Box(
        modifier = modifier.pointerInput(showCrop) {
            if (!showCrop) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown()
                val w = size.width - 2 * pad
                val h = size.height - 2 * pad
                val local = down.position - Offset(pad, pad)
                val mode = hitTest(local, currentCrop, w, h, touchRadius) ?: return@awaitEachGesture
                drag(down.id) { change: PointerInputChange ->
                    val d = change.positionChange()
                    currentOnChange(applyDrag(currentCrop, mode, d.x / w, d.y / h))
                    change.consume()
                }
            }
        },
    ) {
    Canvas(
        modifier = Modifier
            .padding(with(LocalDensity.current) { pad.toDp() })
            .aspectRatio(image.width.toFloat() / image.height),
    ) {
        drawImage(
            image = image,
            dstSize = IntSize(size.width.toInt(), size.height.toInt()),
            colorFilter = colorMatrix?.let { ColorFilter.colorMatrix(ColorMatrix(it)) },
        )

        if (!showCrop) return@Canvas

        val l = crop.left * size.width
        val t = crop.top * size.height
        val r = crop.right * size.width
        val b = crop.bottom * size.height

        // Dim everything outside the crop.
        val dim = Color.Black.copy(alpha = 0.55f)
        drawRect(dim, Offset(0f, 0f), Size(size.width, t))
        drawRect(dim, Offset(0f, b), Size(size.width, size.height - b))
        drawRect(dim, Offset(0f, t), Size(l, b - t))
        drawRect(dim, Offset(r, t), Size(size.width - r, b - t))

        // Border and rule-of-thirds grid.
        drawRect(Color.White, Offset(l, t), Size(r - l, b - t), style = Stroke(strokePx))
        val grid = Color.White.copy(alpha = 0.4f)
        for (i in 1..2) {
            val x = l + (r - l) * i / 3f
            val y = t + (b - t) * i / 3f
            drawLine(grid, Offset(x, t), Offset(x, b), strokePx / 2)
            drawLine(grid, Offset(l, y), Offset(r, y), strokePx / 2)
        }

        // Corner handles.
        for (p in listOf(Offset(l, t), Offset(r, t), Offset(l, b), Offset(r, b))) {
            drawCircle(Color.White, handleRadius, p)
        }
    }
    }
}

private fun hitTest(p: Offset, c: NormRect, w: Float, h: Float, radius: Float): Drag? {
    val corners = listOf(
        Drag.TopLeft to Offset(c.left * w, c.top * h),
        Drag.TopRight to Offset(c.right * w, c.top * h),
        Drag.BottomLeft to Offset(c.left * w, c.bottom * h),
        Drag.BottomRight to Offset(c.right * w, c.bottom * h),
    )
    corners.minByOrNull { (_, o) -> (o - p).getDistance() }
        ?.takeIf { (_, o) -> (o - p).getDistance() <= radius }
        ?.let { return it.first }
    val inside = p.x in (c.left * w)..(c.right * w) && p.y in (c.top * h)..(c.bottom * h)
    return if (inside) Drag.Move else null
}

private fun applyDrag(c: NormRect, mode: Drag, dx: Float, dy: Float): NormRect = when (mode) {
    Drag.Move -> {
        val mx = dx.coerceIn(-c.left, 1f - c.right)
        val my = dy.coerceIn(-c.top, 1f - c.bottom)
        NormRect(c.left + mx, c.top + my, c.right + mx, c.bottom + my)
    }
    Drag.TopLeft -> c.copy(
        left = (c.left + dx).coerceIn(0f, c.right - MIN_SIZE),
        top = (c.top + dy).coerceIn(0f, c.bottom - MIN_SIZE),
    )
    Drag.TopRight -> c.copy(
        right = (c.right + dx).coerceIn(c.left + MIN_SIZE, 1f),
        top = (c.top + dy).coerceIn(0f, c.bottom - MIN_SIZE),
    )
    Drag.BottomLeft -> c.copy(
        left = (c.left + dx).coerceIn(0f, c.right - MIN_SIZE),
        bottom = (c.bottom + dy).coerceIn(c.top + MIN_SIZE, 1f),
    )
    Drag.BottomRight -> c.copy(
        right = (c.right + dx).coerceIn(c.left + MIN_SIZE, 1f),
        bottom = (c.bottom + dy).coerceIn(c.top + MIN_SIZE, 1f),
    )
}
