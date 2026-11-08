package com.lovastr.photocrop.edit

import android.content.ContentResolver
import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrixColorFilter
import android.graphics.ImageDecoder
import android.graphics.Paint
import android.graphics.Rect
import android.net.Uri
import android.os.Environment
import android.provider.MediaStore
import kotlin.math.max
import kotlin.math.roundToInt

private const val MAX_EDGE = 4096

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

/** Applies the crop and color adjustments to produce the final bitmap. */
fun renderEdited(src: Bitmap, crop: NormRect, matrix: FloatArray): Bitmap {
    val x = (crop.left * src.width).roundToInt().coerceIn(0, src.width - 1)
    val y = (crop.top * src.height).roundToInt().coerceIn(0, src.height - 1)
    val w = (crop.width * src.width).roundToInt().coerceIn(1, src.width - x)
    val h = (crop.height * src.height).roundToInt().coerceIn(1, src.height - y)

    val out = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val paint = Paint(Paint.FILTER_BITMAP_FLAG).apply {
        colorFilter = ColorMatrixColorFilter(android.graphics.ColorMatrix(matrix))
    }
    Canvas(out).drawBitmap(src, Rect(x, y, x + w, y + h), Rect(0, 0, w, h), paint)
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
