package com.lovastr.photocrop.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovastr.photocrop.edit.Adjustments

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditorScreen(vm: EditorViewModel = viewModel()) {
    val snackbar = remember { SnackbarHostState() }
    val picker = rememberLauncherForActivityResult(PickVisualMedia()) { uri ->
        if (uri != null) vm.load(uri)
    }
    val pickImage = { picker.launch(PickVisualMediaRequest(PickVisualMedia.ImageOnly)) }

    LaunchedEffect(vm.message) {
        vm.message?.let {
            snackbar.showSnackbar(it)
            vm.consumeMessage()
        }
    }

    val source = vm.source

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("PhotoCrop") },
                actions = {
                    if (source != null) {
                        TextButton(onClick = pickImage) { Text("Open") }
                        TextButton(onClick = vm::save, enabled = !vm.busy) { Text("Save") }
                    }
                },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (source == null) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(onClick = pickImage) { Text("Pick a photo") }
                }
            } else {
                val image = remember(source) { source.asImageBitmap() }
                val matrix = remember(vm.adjustments) { vm.adjustments.toColorMatrixArray() }

                Column(Modifier.fillMaxSize().padding(16.dp)) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth(),
                        contentAlignment = Alignment.Center,
                    ) {
                        CropView(
                            image = image,
                            crop = vm.crop,
                            colorMatrix = matrix,
                            onCropChange = vm::updateCrop,
                        )
                    }

                    AdjustmentSlider("Brightness", vm.adjustments.brightness, -1f..1f) {
                        vm.updateAdjustments(vm.adjustments.copy(brightness = it))
                    }
                    AdjustmentSlider("Contrast", vm.adjustments.contrast, 0f..2f) {
                        vm.updateAdjustments(vm.adjustments.copy(contrast = it))
                    }
                    AdjustmentSlider("Saturation", vm.adjustments.saturation, 0f..2f) {
                        vm.updateAdjustments(vm.adjustments.copy(saturation = it))
                    }

                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                        OutlinedButton(onClick = vm::reset) { Text("Reset") }
                    }
                }
            }

            if (vm.busy) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun AdjustmentSlider(
    label: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onChange: (Float) -> Unit,
) {
    Column {
        Text(label, style = androidx.compose.material3.MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
