package com.lovastr.photocrop.edit

import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.sqrt

enum class Channel { Red, Green, Blue }

/** A control point of a tone curve; x = input level, y = output level, both 0..1. */
data class CurvePoint(val x: Float, val y: Float)

private const val MIN_GAP = 0.03f

val IdentityPoints = listOf(CurvePoint(0f, 0f), CurvePoint(1f, 1f))

/** Independent tone curves for the red, green and blue channels. */
data class Curves(
    val red: List<CurvePoint> = IdentityPoints,
    val green: List<CurvePoint> = IdentityPoints,
    val blue: List<CurvePoint> = IdentityPoints,
) {
    operator fun get(channel: Channel): List<CurvePoint> = when (channel) {
        Channel.Red -> red
        Channel.Green -> green
        Channel.Blue -> blue
    }

    fun with(channel: Channel, points: List<CurvePoint>): Curves = when (channel) {
        Channel.Red -> copy(red = points)
        Channel.Green -> copy(green = points)
        Channel.Blue -> copy(blue = points)
    }

    val isIdentity: Boolean
        get() = Channel.entries.all { ch -> this[ch].all { abs(it.x - it.y) < 0.002f } }

    /** 256-entry lookup table (input 0..255 -> output 0..255) for the channel. */
    fun lut(channel: Channel): IntArray = buildLut(this[channel])
}

/** Inserts a point keeping the list sorted. Returns the new list and the index, or null if too close to a neighbour. */
fun List<CurvePoint>.insertPoint(p: CurvePoint): Pair<List<CurvePoint>, Int>? {
    val i = indexOfFirst { it.x > p.x }
    if (i <= 0) return null
    if (p.x - this[i - 1].x < MIN_GAP || this[i].x - p.x < MIN_GAP) return null
    return (take(i) + p + drop(i)) to i
}

/** Moves a point. The first and last points keep their x (0 and 1); others stay between their neighbours. */
fun List<CurvePoint>.movePoint(index: Int, p: CurvePoint): List<CurvePoint> {
    val x = when (index) {
        0 -> 0f
        lastIndex -> 1f
        else -> p.x.coerceIn(this[index - 1].x + MIN_GAP, this[index + 1].x - MIN_GAP)
    }
    return toMutableList().also { it[index] = CurvePoint(x, p.y.coerceIn(0f, 1f)) }
}

/** Removes an interior point; the end points can't be removed. */
fun List<CurvePoint>.removePoint(index: Int): List<CurvePoint> =
    if (index <= 0 || index >= lastIndex) this else filterIndexed { i, _ -> i != index }

/** Monotone cubic (Fritsch–Carlson) interpolation through the points, sampled into a 256-entry LUT. */
private fun buildLut(points: List<CurvePoint>): IntArray {
    val n = points.size
    val xs = FloatArray(n) { points[it].x }
    val ys = FloatArray(n) { points[it].y }
    val d = FloatArray(n - 1) { (ys[it + 1] - ys[it]) / (xs[it + 1] - xs[it]) }
    val m = FloatArray(n)
    m[0] = d[0]
    m[n - 1] = d[n - 2]
    for (i in 1 until n - 1) {
        m[i] = if (d[i - 1] * d[i] <= 0f) 0f else (d[i - 1] + d[i]) / 2f
    }
    for (i in 0 until n - 1) {
        if (d[i] == 0f) {
            m[i] = 0f
            m[i + 1] = 0f
        } else {
            val a = m[i] / d[i]
            val b = m[i + 1] / d[i]
            val s = a * a + b * b
            if (s > 9f) {
                val t = 3f / sqrt(s)
                m[i] = t * a * d[i]
                m[i + 1] = t * b * d[i]
            }
        }
    }

    val lut = IntArray(256)
    var k = 0
    for (v in 0..255) {
        val x = v / 255f
        while (k < n - 2 && x > xs[k + 1]) k++
        val h = xs[k + 1] - xs[k]
        val t = ((x - xs[k]) / h).coerceIn(0f, 1f)
        val t2 = t * t
        val t3 = t2 * t
        val y = (2 * t3 - 3 * t2 + 1) * ys[k] +
            (t3 - 2 * t2 + t) * h * m[k] +
            (-2 * t3 + 3 * t2) * ys[k + 1] +
            (t3 - t2) * h * m[k + 1]
        lut[v] = (y.coerceIn(0f, 1f) * 255f).roundToInt()
    }
    return lut
}
