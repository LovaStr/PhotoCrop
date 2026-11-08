package com.lovastr.photocrop.edit

import android.graphics.ColorMatrix

/** Crop rectangle in normalized image coordinates (0..1). */
data class NormRect(
    val left: Float = 0f,
    val top: Float = 0f,
    val right: Float = 1f,
    val bottom: Float = 1f,
) {
    val width get() = right - left
    val height get() = bottom - top

    companion object {
        val Full = NormRect()
    }
}

/**
 * Color adjustments.
 * brightness: -1..1 (0 = unchanged), contrast: 0..2 (1 = unchanged), saturation: 0..2 (1 = unchanged).
 */
data class Adjustments(
    val brightness: Float = 0f,
    val contrast: Float = 1f,
    val saturation: Float = 1f,
) {
    /** 4x5 color matrix (row-major) usable by both android.graphics and Compose. */
    fun toColorMatrixArray(): FloatArray {
        val sat = ColorMatrix().apply { setSaturation(saturation) }
        val c = contrast
        val t = 128f * (1f - c) + brightness * 255f
        val con = ColorMatrix(
            floatArrayOf(
                c, 0f, 0f, 0f, t,
                0f, c, 0f, 0f, t,
                0f, 0f, c, 0f, t,
                0f, 0f, 0f, 1f, 0f,
            )
        )
        sat.postConcat(con)
        return sat.array.copyOf()
    }
}
