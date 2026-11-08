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
import com.lovastr.photocrop.edit.NormRect
import com.lovastr.photocrop.edit.decodeBitmap
import com.lovastr.photocrop.edit.renderEdited
import com.lovastr.photocrop.edit.saveToGallery
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class EditorViewModel(app: Application) : AndroidViewModel(app) {

    var source by mutableStateOf<Bitmap?>(null)
        private set
    var crop by mutableStateOf(NormRect.Full)
        private set
    var adjustments by mutableStateOf(Adjustments())
        private set
    var busy by mutableStateOf(false)
        private set
    var message by mutableStateOf<String?>(null)
        private set

    fun load(uri: Uri) {
        viewModelScope.launch {
            busy = true
            try {
                val bmp = withContext(Dispatchers.IO) { decodeBitmap(getApplication<Application>().contentResolver, uri) }
                source = bmp
                crop = NormRect.Full
                adjustments = Adjustments()
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

    fun updateAdjustments(value: Adjustments) {
        adjustments = value
    }

    fun reset() {
        crop = NormRect.Full
        adjustments = Adjustments()
    }

    fun consumeMessage() {
        message = null
    }

    fun save() {
        val src = source ?: return
        val currentCrop = crop
        val matrix = adjustments.toColorMatrixArray()
        viewModelScope.launch {
            busy = true
            try {
                withContext(Dispatchers.IO) {
                    val edited = renderEdited(src, currentCrop, matrix)
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
}
