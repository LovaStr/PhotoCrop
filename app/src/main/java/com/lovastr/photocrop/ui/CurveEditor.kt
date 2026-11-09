package com.lovastr.photocrop.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.lovastr.photocrop.edit.Channel
import com.lovastr.photocrop.edit.CurvePoint
import com.lovastr.photocrop.edit.Curves
import com.lovastr.photocrop.edit.insertPoint
import com.lovastr.photocrop.edit.movePoint
import com.lovastr.photocrop.edit.removePoint

val Channel.color: Color
    get() = when (this) {
        Channel.Red -> Color(0xFFE53935)
        Channel.Green -> Color(0xFF43A047)
        Channel.Blue -> Color(0xFF1E88E5)
    }

/**
 * Square graph for editing tone curves. Touch near a point to drag it, touch empty space to add a point,
 * double-tap a point to remove it. Edits apply to every channel in [selected].
 */
@Composable
fun CurveEditor(
    curves: Curves,
    selected: Set<Channel>,
    onCurvesChange: (Curves) -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentCurves by rememberUpdatedState(curves)
    val currentSelected by rememberUpdatedState(selected)
    val currentOnChange by rememberUpdatedState(onCurvesChange)

    val density = LocalDensity.current
    val touchRadius = with(density) { 28.dp.toPx() }
    val pointRadius = with(density) { 6.dp.toPx() }
    val thin = with(density) { 2.dp.toPx() }
    val thick = with(density) { 3.dp.toPx() }
    val background = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f)

    Canvas(
        modifier = modifier
            .aspectRatio(1f)
            .pointerInput(Unit) {
                var lastTapTime = 0L
                var lastTapPos = Offset.Zero
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val w = size.width.toFloat()
                    val h = size.height.toFloat()
                    val channels = currentSelected
                    if (channels.isEmpty()) return@awaitEachGesture

                    fun toPoint(o: Offset) = CurvePoint(
                        (o.x / w).coerceIn(0f, 1f),
                        (1f - o.y / h).coerceIn(0f, 1f),
                    )
                    fun toOffset(p: CurvePoint) = Offset(p.x * w, (1f - p.y) * h)

                    var c = currentCurves
                    val doubleTap = down.uptimeMillis - lastTapTime < 300 &&
                        (down.position - lastTapPos).getDistance() < touchRadius

                    // Nearest existing point per selected channel within reach.
                    val grabbed = mutableMapOf<Channel, Int>()
                    for (ch in channels) {
                        val pts = c[ch]
                        val idx = pts.indices.minByOrNull { (toOffset(pts[it]) - down.position).getDistance() }
                        if (idx != null && (toOffset(pts[idx]) - down.position).getDistance() <= touchRadius) {
                            grabbed[ch] = idx
                        }
                    }

                    if (doubleTap) {
                        for ((ch, idx) in grabbed) c = c.with(ch, c[ch].removePoint(idx))
                        currentOnChange(c)
                        lastTapTime = 0L
                        drag(down.id) { it.consume() }
                        return@awaitEachGesture
                    }

                    if (grabbed.isEmpty()) {
                        val p = toPoint(down.position)
                        for (ch in channels) {
                            c[ch].insertPoint(p)?.let { (pts, idx) ->
                                c = c.with(ch, pts)
                                grabbed[ch] = idx
                            }
                        }
                        currentOnChange(c)
                    }

                    var moved = false
                    drag(down.id) { change ->
                        if ((change.position - down.position).getDistance() > touchRadius / 3f) moved = true
                        val p = toPoint(change.position)
                        for ((ch, idx) in grabbed) c = c.with(ch, c[ch].movePoint(idx, p))
                        currentOnChange(c)
                        change.consume()
                    }

                    if (moved) {
                        lastTapTime = 0L
                    } else {
                        lastTapTime = down.uptimeMillis
                        lastTapPos = down.position
                    }
                }
            },
    ) {
        drawRect(background)
        for (i in 1..3) {
            val f = i / 4f
            drawLine(gridColor, Offset(size.width * f, 0f), Offset(size.width * f, size.height), thin / 2)
            drawLine(gridColor, Offset(0f, size.height * f), Offset(size.width, size.height * f), thin / 2)
        }
        drawLine(gridColor, Offset(0f, size.height), Offset(size.width, 0f), thin / 2)

        // Unselected channels first (dimmed), selected on top.
        val order = Channel.entries.sortedBy { it in selected }
        for (ch in order) {
            val isSel = ch in selected
            val lut = curves.lut(ch)
            val path = Path()
            for (v in 0..255) {
                val x = v / 255f * size.width
                val y = size.height - lut[v] / 255f * size.height
                if (v == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path,
                ch.color.copy(alpha = if (isSel) 1f else 0.3f),
                style = Stroke(if (isSel) thick else thin),
            )
        }
        for (ch in Channel.entries.filter { it in selected }) {
            for (p in curves[ch]) {
                val o = Offset(p.x * size.width, (1f - p.y) * size.height)
                drawCircle(Color.White, pointRadius + thin / 2, o)
                drawCircle(ch.color, pointRadius - thin / 2, o)
            }
        }
    }
}
