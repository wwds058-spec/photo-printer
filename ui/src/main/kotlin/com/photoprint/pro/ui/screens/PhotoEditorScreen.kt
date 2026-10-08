package com.photoprint.pro.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.automirrored.filled.RotateRight
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.photoprint.pro.core.ui.theme.Mat
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.domain.measurement.Rect
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.presentation.editor.AspectOption
import com.photoprint.pro.presentation.editor.CropEditing
import com.photoprint.pro.ui.Strings
import com.photoprint.pro.ui.components.BottomActionBar
import com.photoprint.pro.ui.components.LabeledSlider
import com.photoprint.pro.ui.components.ScreenScaffold
import com.photoprint.pro.ui.components.SecondaryButton
import com.photoprint.pro.ui.components.drawPhotoInFrame
import com.photoprint.pro.ui.rememberPhotoBitmap
import kotlin.math.min

/**
 * Crop / zoom / move / rotate one photo inside a FIXED frame. The frame's shape comes from [aspect];
 * when [aspectLocked] (editing from the sheet) it is the chosen photo size and cannot be changed.
 * Gestures call [CropEditing], which clamps against the same limits the PDF uses.
 */
@Composable
fun PhotoEditorScreen(
    title: String,
    photo: ProjectPhoto,
    aspect: AspectOption,
    aspectLocked: Boolean,
    frameCaption: String?,
    step: Pair<Int, Int>?,
    onCropChange: (CropState) -> Unit,
    onAspectChange: (AspectOption) -> Unit,
    onReplace: () -> Unit,
    onApply: () -> Unit,
    onBack: () -> Unit,
) {
    val bitmap = rememberPhotoBitmap(photo.sourceRef, maxSidePx = 1600).value
    val frameUnits = CropEditing.frameFor(aspect, photo.widthPx, photo.heightPx)
    var canvasSize by remember { mutableStateOf(IntSize.Zero) }
    val frameRect = remember(canvasSize, frameUnits) { fitFrame(canvasSize, frameUnits) }

    // Gestures run inside a long-lived pointerInput block, so read the latest values through these.
    val latestCrop by rememberUpdatedState(photo.crop)
    val latestFrameUnits by rememberUpdatedState(frameUnits)
    val latestOnChange by rememberUpdatedState(onCropChange)

    var showAdjust by remember { mutableStateOf(false) }
    var showCustomAspect by remember { mutableStateOf(false) }
    val crop = photo.crop
    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val gridColor = Color.White.copy(alpha = 0.55f)
    val frameColor = MaterialTheme.colorScheme.primary

    ScreenScaffold(
        title = title,
        onBack = onBack,
        step = step,
        bottomBar = { BottomActionBar(Strings.apply, onApply) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { canvasSize = it }
                    .semantics { contentDescription = "${Strings.editorTitle}. ${Strings.editorHint}" }
                    .pointerInput(frameRect) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            if (frameRect.width <= 0.0 || frameRect.height <= 0.0) return@detectTransformGestures
                            val fx = ((centroid.x - (frameRect.left + frameRect.width / 2)) / frameRect.width).coerceIn(-0.5, 0.5)
                            val fy = ((centroid.y - (frameRect.top + frameRect.height / 2)) / frameRect.height).coerceIn(-0.5, 0.5)
                            var c = CropEditing.zoomBy(latestCrop, zoom.toDouble(), fx, fy, latestFrameUnits, photo.widthPx, photo.heightPx)
                            c = CropEditing.panBy(c, pan.x / frameRect.width, pan.y / frameRect.height, latestFrameUnits, photo.widthPx, photo.heightPx)
                            latestOnChange(c)
                        }
                    },
            ) {
                drawRect(Mat)
                if (frameRect.width > 0) {
                    drawPhotoInFrame(bitmap, frameRect, 0, crop, placeholder)
                    val tl = Offset(frameRect.left.toFloat(), frameRect.top.toFloat())
                    val sz = Size(frameRect.width.toFloat(), frameRect.height.toFloat())
                    // Rule-of-thirds guides help to position faces; they are on screen only, never printed.
                    for (i in 1..2) {
                        drawLine(gridColor, Offset(tl.x + sz.width * i / 3, tl.y), Offset(tl.x + sz.width * i / 3, tl.y + sz.height), 1f)
                        drawLine(gridColor, Offset(tl.x, tl.y + sz.height * i / 3), Offset(tl.x + sz.width, tl.y + sz.height * i / 3), 1f)
                    }
                    drawRect(frameColor, tl, sz, style = Stroke(width = 3f))
                }
            }

            Column(
                Modifier.fillMaxWidth().heightIn(max = 280.dp).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                Text(frameCaption ?: Strings.editorHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                if (frameCaption != null) Text(Strings.frameStaysFixed, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)

                LabeledSlider(
                    label = Strings.zoom,
                    valueText = "${((crop.zoom * 10).toInt() / 10.0)}×",
                    value = crop.zoom.toFloat(),
                    onValueChange = { onCropChange(CropEditing.setZoom(crop, it.toDouble(), frameUnits, photo.widthPx, photo.heightPx)) },
                    range = CropEditing.MIN_ZOOM.toFloat()..CropEditing.MAX_ZOOM.toFloat(),
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    SecondaryButton(Strings.rotate, { onCropChange(CropEditing.rotateClockwise(crop, frameUnits, photo.widthPx, photo.heightPx)) }, Modifier.weight(1f), icon = Icons.AutoMirrored.Filled.RotateRight)
                    SecondaryButton(Strings.reset, { onCropChange(CropEditing.reset()) }, Modifier.weight(1f), enabled = !CropEditing.isDefault(crop), icon = Icons.Filled.RestartAlt)
                    SecondaryButton(Strings.replace, onReplace, Modifier.weight(1f), icon = Icons.Filled.SwapHoriz)
                }
                if (!aspectLocked) {
                    Text(Strings.aspect, style = MaterialTheme.typography.titleSmall)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                        items(AspectOption.presets) { option ->
                            FilterChip(
                                selected = option.label == aspect.label,
                                onClick = { onAspectChange(option) },
                                label = { Text(option.label) },
                                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
                            )
                        }
                        items(listOf(Strings.customAspect)) { label ->
                            FilterChip(
                                selected = aspect is AspectOption.Ratio && aspect.label == "Custom",
                                onClick = { showCustomAspect = true },
                                label = { Text(label) },
                                modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
                            )
                        }
                    }
                }
                TextButton(onClick = { showAdjust = !showAdjust }, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                    androidx.compose.material3.Icon(Icons.Filled.Tune, contentDescription = null)
                    Text("  ${Strings.adjust}")
                }
                if (showAdjust) {
                    LabeledSlider(
                        Strings.brightness, "${(crop.brightness * 100).toInt()}", crop.brightness.toFloat(),
                        { onCropChange(CropEditing.setBrightness(crop, it.toDouble())) }, -1f..1f,
                    )
                    LabeledSlider(
                        Strings.contrast, "${(crop.contrast * 100).toInt()}", crop.contrast.toFloat(),
                        { onCropChange(CropEditing.setContrast(crop, it.toDouble())) }, -1f..1f,
                    )
                }
            }
        }
    }

    if (showCustomAspect) {
        CustomAspectDialog(
            onDismiss = { showCustomAspect = false },
            onConfirm = { w, h ->
                AspectOption.custom(w, h)?.let(onAspectChange)
                showCustomAspect = false
            },
        )
    }
}

/** The largest rectangle of the frame's shape that fits the canvas, centred, with a margin. */
private fun fitFrame(canvas: IntSize, frame: CropEditing.Frame, marginPx: Double = 32.0): Rect {
    val availW = canvas.width - 2 * marginPx
    val availH = canvas.height - 2 * marginPx
    if (availW <= 0 || availH <= 0 || frame.widthUnits <= 0 || frame.heightUnits <= 0) return Rect(0.0, 0.0, 0.0, 0.0)
    val s = min(availW / frame.widthUnits, availH / frame.heightUnits)
    val w = frame.widthUnits * s
    val h = frame.heightUnits * s
    return Rect((canvas.width - w) / 2, (canvas.height - h) / 2, w, h)
}

@Composable
private fun CustomAspectDialog(onDismiss: () -> Unit, onConfirm: (Double, Double) -> Unit) {
    var w by remember { mutableStateOf("") }
    var h by remember { mutableStateOf("") }
    val wv = w.replace(',', '.').toDoubleOrNull()
    val hv = h.replace(',', '.').toDoubleOrNull()
    val valid = wv != null && hv != null && AspectOption.custom(wv, hv) != null
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(Strings.customAspect) },
        text = {
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                OutlinedTextField(w, { w = it }, label = { Text(Strings.width) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
                OutlinedTextField(h, { h = it }, label = { Text(Strings.height) }, singleLine = true, modifier = Modifier.weight(1f), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal))
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(wv!!, hv!!) }, enabled = valid) { Text(Strings.apply) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } },
    )
}

