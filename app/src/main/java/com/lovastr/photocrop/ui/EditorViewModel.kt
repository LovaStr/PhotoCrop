package com.lovastr.photocrop.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.lovastr.photocrop.edit.Adjustments
import com.lovastr.photocrop.edit.Channel
import com.lovastr.photocrop.edit.Curves
import com.lovastr.photocrop.edit.NormRect
import com.lovastr.photocrop.edit.applyCurves
import com.lovastr.photocrop.edit.cropBitmap
import com.lovastr.photocrop.edit.decodeBitmap
import com.lovastr.photocrop.edit.makePreview
import com.lovastr.photocrop.edit.renderEdited
import com.lovastr.photocrop.edit.saveToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    /** Full-resolution image (already cropped if the crop was applied). */
    var source by mutableStateOf<Bitmap?>(null)
        private set

    /** Downscaled [source], used for fast previews. This is the "original" shown by the compare button. */
    var preview by mutableStateOf<Bitmap?>(null)
        private set

    /** [preview] with the curves applied. */
    var curved by mutableStateOf<Bitmap?>(null)
        private set

    var crop by mutableStateOf(NormRect.Full)
        private set
    var adjustments by mutableStateOf(Adjustments())
        private set
    var curves by mutableStateOf(Curves())
        private set
    var selectedChannels by mutableStateOf(Channel.entries.toSet())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    private var curveJob: Job? = null

    fun load(uri: Uri) {
        viewModelScope.launch {
            busy = true
            try {
                val bmp = withContext(Dispatchers.IO) {
                    decodeBitmap(getApplication<Application>().contentResolver, uri)
                }
                resetEdits()
                install(bmp)
            } catch (e: Exception) {
                message = "Could not open image: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun updateCrop(rect: NormRect) {
        crop = rect
    }

    fun resetCropSelection() {
        crop = NormRect.Full
    }

    /** Commits the current crop rectangle: the image becomes the cropped region. */
    fun applyCrop() {
        val src = source ?: return
        val rect = crop
        if (rect == NormRect.Full) return
        viewModelScope.launch {
            busy = true
            try {
                val cropped = withContext(Dispatchers.Default) { cropBitmap(src, rect) }
                crop = NormRect.Full
                install(cropped)
            } catch (e: Exception) {
                message = "Crop failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    fun updateAdjustments(value: Adjustments) {
        adjustments = value
    }

    fun updateCurves(value: Curves) {
        curves = value
        refreshCurved()
    }

    fun resetCurves() = updateCurves(Curves())

    fun toggleChannel(channel: Channel) {
        val next = if (channel in selectedChannels) selectedChannels - channel else selectedChannels + channel
        if (next.isNotEmpty()) selectedChannels = next
    }

    fun selectAllChannels() {
        selectedChannels = Channel.entries.toSet()
    }

    /** Discards the photo and all edits, returning to the start screen. */
    fun cancel() {
        curveJob?.cancel()
        source = null
        preview = null
        curved = null
        resetEdits()
    }

    fun consumeMessage() {
        message = null
    }

    fun save() {
        val src = source ?: return
        val currentCurves = curves
        val matrix = adjustments.toColorMatrixArray()
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val edited = renderEdited(src, currentCurves, matrix)
                    try {
                        saveToGallery(getApplication(), edited)
                    } finally {
                        edited.recycle()
                    }
                }
                message = "Saved to Pictures/PhotoCrop"
            } catch (e: Exception) {
                message = "Save failed: ${e.message}"
            } finally {
                busy = false
            }
        }
    }

    private fun resetEdits() {
        crop = NormRect.Full
        adjustments = Adjustments()
        curves = Curves()
        selectedChannels = Channel.entries.toSet()
    }

    private suspend fun install(bmp: Bitmap) {
        val prev = withContext(Dispatchers.Default) { makePreview(bmp) }
        source = bmp
        preview = prev
        curved = prev
        refreshCurved()
    }

    /** Recomputes the curved preview on a background thread; newer edits cancel older work. */
    private fun refreshCurved() {
        val base = preview ?: return
        val c = curves
        curveJob?.cancel()
        if (c.isIdentity) {
            curved = base
            return
        }
        curveJob = viewModelScope.launch(Dispatchers.Default) {
            val out = applyCurves(base, c) { ensureActive() }
            withContext(Dispatchers.Main) { curved = out }
        }
    }
}
