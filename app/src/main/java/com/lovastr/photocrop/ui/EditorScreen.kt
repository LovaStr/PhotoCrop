package com.lovastr.photocrop.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Slider
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.lovastr.photocrop.edit.Channel
import com.lovastr.photocrop.edit.NormRect

private enum class EditTab(val label: String) { Crop("Crop"), Adjust("Adjust"), Curves("Curves") }

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

    var tabIndex by rememberSaveable { mutableIntStateOf(0) }
    val tab = EditTab.entries[tabIndex]

    // Holding the "Original" button shows the image without any color edits.
    val originalSource = remember { MutableInteractionSource() }
    val showOriginal by originalSource.collectIsPressedAsState()

    val preview = vm.preview
    val curved = vm.curved

    Scaffold(
        topBar = { TopAppBar(title = { Text("PhotoCrop") }) },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Box(Modifier.fillMaxSize().padding(padding)) {
            if (preview == null || curved == null) {
                Column(
                    Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Button(onClick = pickImage) { Text("Pick a photo") }
                }
            } else {
                Column(Modifier.fillMaxSize()) {
                    Box(
                        Modifier.weight(1f).fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        val shown = if (showOriginal) preview else curved
                        val image = remember(shown) { shown.asImageBitmap() }
                        val matrix = remember(vm.adjustments, showOriginal) {
                            if (showOriginal) null else vm.adjustments.toColorMatrixArray()
                        }
                        CropView(
                            image = image,
                            crop = vm.crop,
                            colorMatrix = matrix,
                            showCrop = tab == EditTab.Crop,
                            onCropChange = vm::updateCrop,
                        )
                    }

                    TabRow(selectedTabIndex = tabIndex) {
                        EditTab.entries.forEachIndexed { i, t ->
                            Tab(selected = i == tabIndex, onClick = { tabIndex = i }, text = { Text(t.label) })
                        }
                    }

                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(300.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                    ) {
                        when (tab) {
                            EditTab.Crop -> CropPanel(vm)
                            EditTab.Adjust -> AdjustPanel(vm)
                            EditTab.Curves -> CurvesPanel(vm)
                        }
                    }

                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        OutlinedButton(onClick = {}, interactionSource = originalSource) { Text("Hold: original") }
                        Box(Modifier.weight(1f))
                        TextButton(onClick = vm::cancel) { Text("Cancel") }
                        Button(onClick = vm::save, enabled = !vm.busy) { Text("Save") }
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
private fun CropPanel(vm: EditorViewModel) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(
            "Drag the corners to resize the box, or drag inside it to move it. Press Crop to apply.",
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = vm::applyCrop, enabled = vm.crop != NormRect.Full && !vm.busy) { Text("Crop") }
            OutlinedButton(onClick = vm::resetCropSelection, enabled = vm.crop != NormRect.Full) {
                Text("Reset selection")
            }
        }
    }
}

@Composable
private fun AdjustPanel(vm: EditorViewModel) {
    Column {
        AdjustmentSlider("Brightness", vm.adjustments.brightness, -1f..1f) {
            vm.updateAdjustments(vm.adjustments.copy(brightness = it))
        }
        AdjustmentSlider("Contrast", vm.adjustments.contrast, 0f..2f) {
            vm.updateAdjustments(vm.adjustments.copy(contrast = it))
        }
        AdjustmentSlider("Saturation", vm.adjustments.saturation, 0f..2f) {
            vm.updateAdjustments(vm.adjustments.copy(saturation = it))
        }
    }
}

@Composable
private fun CurvesPanel(vm: EditorViewModel) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val all = vm.selectedChannels.size == Channel.entries.size
            FilterChip(selected = all, onClick = vm::selectAllChannels, label = { Text("All") })
            Channel.entries.forEach { ch ->
                FilterChip(
                    selected = ch in vm.selectedChannels,
                    onClick = { vm.toggleChannel(ch) },
                    label = { Text(ch.name.first().toString()) },
                )
            }
            Box(Modifier.weight(1f))
            TextButton(onClick = vm::resetCurves, enabled = !vm.curves.isIdentity) { Text("Reset") }
        }
        CurveEditor(
            curves = vm.curves,
            selected = vm.selectedChannels,
            onCurvesChange = vm::updateCurves,
            modifier = Modifier.size(170.dp),
        )
        Text(
            "Tap to add a point, drag to shape, double-tap a point to remove it.",
            style = MaterialTheme.typography.labelSmall,
        )
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
        Text(label, style = MaterialTheme.typography.labelMedium)
        Slider(value = value, onValueChange = onChange, valueRange = range)
    }
}
