package com.lovastr.photocrop.edit

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val MAX_EDGE = 4096
private const val PREVIEW_EDGE = 1024

/** Decodes an image (EXIF rotation handled by ImageDecoder), downscaling very large images. */
fun decodeBitmap(resolver: ContentResolver, uri: Uri): Bitmap {
    val source = ImageDecoder.createSource(resolver, uri)
    return ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        val longest = max(info.size.width, info.size.height)
        if (longest > MAX_EDGE) {
            val scale = MAX_EDGE.toFloat() / longest
            decoder.setTargetSize(
                (info.size.width * scale).roundToInt().coerceAtLeast(1),
                (info.size.height * scale).roundToInt().coerceAtLeast(1),
            )
        }
    }
}

/** A small copy of the image used for fast live previews. */
fun makePreview(src: Bitmap): Bitmap {
    val longest = max(src.width, src.height)
    if (longest <= PREVIEW_EDGE) return src
    val scale = PREVIEW_EDGE.toFloat() / longest
    return Bitmap.createScaledBitmap(
        src,
        (src.width * scale).roundToInt().coerceAtLeast(1),
        (src.height * scale).roundToInt().coerceAtLeast(1),
        true,
    )
}

/** Returns the part of the image inside the normalized crop rectangle. */
fun cropBitmap(src: Bitmap, crop: NormRect): Bitmap {
    val x = (crop.left * src.width).roundToInt().coerceIn(0, src.width - 1)
    val y = (crop.top * src.height).roundToInt().coerceIn(0, src.height - 1)
    val w = (crop.width * src.width).roundToInt().coerceIn(1, src.width - x)
    val h = (crop.height * src.height).roundToInt().coerceIn(1, src.height - y)
    return Bitmap.createBitmap(src, x, y, w, h)
}

/** Applies the per-channel curves. [checkCancelled] is called between row chunks so callers can abort. */
fun applyCurves(src: Bitmap, curves: Curves, checkCancelled: () -> Unit = {}): Bitmap {
    val w = src.width
    val h = src.height
    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val lr = curves.lut(Channel.Red)
    val lg = curves.lut(Channel.Green)
    val lb = curves.lut(Channel.Blue)
    val rows = max(1, 1_000_000 / w)
    val buf = IntArray(w * rows)
    var y = 0
    while (y < h) {
        checkCancelled()
        val n = min(rows, h - y)
        src.getPixels(buf, 0, w, 0, y, w, n)
        for (i in 0 until w * n) {
            val p = buf[i]
            buf[i] = (p and 0xFF000000.toInt()) or
                (lr[(p shr 16) and 255] shl 16) or
                (lg[(p shr 8) and 255] shl 8) or
                lb[p and 255]
        }
        out.setPixels(buf, 0, w, 0, y, w, n)
        y += n
    }
    return out
}

/** Produces the final bitmap: curves first, then the brightness/contrast/saturation matrix. */
fun renderEdited(src: Bitmap, curves: Curves, matrix: FloatArray): Bitmap {
    val curved = if (curves.isIdentity) src else applyCurves(src, curves)
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(matrix))
    }
    Canvas(out).drawBitmap(curved, 0f, 0f, paint)
    if (curved !== src) curved.recycle()
    return out
}

/** Saves the bitmap as a JPEG in Pictures/PhotoCrop. Returns the new item's Uri. */
fun saveToGallery(context: Context, bitmap: Bitmap): Uri {
    val resolver = context.contentResolver
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "PhotoCrop_${System.currentTimeMillis()}.jpg")
        put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/PhotoCrop")
        put(MediaStore.Images.Media.IS_PENDING, 1)
    }
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
        ?: error("Could not create MediaStore entry")
    try {
        resolver.openOutputStream(uri)?.use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.JPEG, 95, stream)) { "Compression failed" }
        } ?: error("Could not open output stream")
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
    } catch (e: Exception) {
        resolver.delete(uri, null, null)
        throw e
    }
    return uri
}
