package com.photoprint.pro.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.FitScreen
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.photoprint.pro.core.ui.theme.Mat
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.domain.layout.LayoutPlan
import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.pdf.ImageQuality
import com.photoprint.pro.domain.printing.ReadinessItem
import com.photoprint.pro.presentation.format.Formatting
import com.photoprint.pro.presentation.preview.SheetViewport
import com.photoprint.pro.presentation.preview.SheetViewportMath
import com.photoprint.pro.presentation.session.SessionState
import com.photoprint.pro.ui.Strings
import com.photoprint.pro.ui.components.BannerKind
import com.photoprint.pro.ui.components.BottomActionBar
import com.photoprint.pro.ui.components.PhotoThumb
import com.photoprint.pro.ui.components.PrimaryButton
import com.photoprint.pro.ui.components.ScreenScaffold
import com.photoprint.pro.ui.components.SecondaryButton
import com.photoprint.pro.ui.components.StatusLine
import com.photoprint.pro.ui.components.drawSheet
import com.photoprint.pro.ui.rememberPhotoBitmaps

/** Geometry the long-lived gesture handlers need; read through rememberUpdatedState. */
private class PreviewGeometry(val sheetW: Double, val sheetH: Double, val viewW: Double, val viewH: Double) {
    fun fit() = SheetViewportMath.fit(sheetW, sheetH, viewW, viewH, paddingPx = 24.0)
}

/**
 * The hero screen: the exact sheet, drawn from the same layout the PDF uses. Pinching or dragging changes
 * only the on-screen scale; positions in millimetres never move. Photos are only edited deliberately: tap one,
 * then choose Edit. Reordering is a separate, explicit mode.
 */
@Composable
fun PrintPreviewScreen(
    state: SessionState,
    plan: LayoutPlan,
    unit: LengthUnit,
    actualPxPerMm: Double,
    quality: ReadinessItem.ImageQualityResult?,
    step: Pair<Int, Int>,
    onSheetChange: (Int) -> Unit,
    onEditPhoto: (String) -> Unit,
    onReplace: (String) -> Unit,
    onMovePhoto: (String, Int) -> Unit,
    onEditLayout: () -> Unit,
    onSavePdf: () -> Unit,
    onSharePdf: () -> Unit,
    onSaveImage: () -> Unit,
    onShareImage: () -> Unit,
    onPrint: () -> Unit,
    onBack: () -> Unit,
) {
    val sheetIndex = state.currentSheet.coerceIn(0, plan.sheets.lastIndex)
    val sheet = plan.sheets[sheetIndex]
    val bitmaps by rememberPhotoBitmaps(state.photos.associate { it.photoId to it.sourceRef }, maxSidePx = 1400)

    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var viewport by remember { mutableStateOf<SheetViewport?>(null) }
    var selectedIndex by remember(sheetIndex) { mutableStateOf<Int?>(null) }
    var reorder by remember { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }

    val geometry = PreviewGeometry(sheet.paperWidthMm, sheet.paperHeightMm, viewSize.width.toDouble(), viewSize.height.toDouble())
    val latestGeometry by rememberUpdatedState(geometry)
    val latestViewport by rememberUpdatedState(viewport)
    val latestSheet by rememberUpdatedState(sheet)

    // Fit when the canvas gets its size, or when the paper changes. Moving between sheets keeps the zoom.
    LaunchedEffect(viewSize, sheet.paperWidthMm, sheet.paperHeightMm) {
        if (viewSize.width > 0 && viewSize.height > 0) viewport = geometry.fit()
    }

    fun zoomTo(vp: SheetViewport, scale: Double, fx: Double, fy: Double): SheetViewport {
        val g = latestGeometry
        return SheetViewportMath.zoomTo(vp, scale, fx, fy, g.sheetW, g.sheetH, g.viewW, g.viewH, actualPxPerMm, g.fit().scale)
    }

    val placeholder = MaterialTheme.colorScheme.surfaceVariant
    val selectionColor = MaterialTheme.colorScheme.primary
    val percent = viewport?.let { SheetViewportMath.percent(it, actualPxPerMm) } ?: 100.0

    ScreenScaffold(
        title = Strings.previewTitle,
        onBack = onBack,
        step = step,
        actions = {
            Box {
                IconButton(onClick = { menuOpen = true }, modifier = Modifier.size(Spacing.minTouchTarget)) {
                    Icon(Icons.Filled.MoreVert, contentDescription = Strings.exportMenu)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(text = { Text(Strings.savePdf) }, onClick = { menuOpen = false; onSavePdf() })
                    DropdownMenuItem(text = { Text(Strings.sharePdf) }, onClick = { menuOpen = false; onSharePdf() })
                    DropdownMenuItem(text = { Text(Strings.saveImage) }, onClick = { menuOpen = false; onSaveImage() })
                    DropdownMenuItem(text = { Text(Strings.shareImage) }, onClick = { menuOpen = false; onShareImage() })
                }
            }
        },
        bottomBar = {
            BottomActionBar(
                primaryLabel = Strings.print,
                onPrimary = onPrint,
                secondaryLabel = Strings.editLayout,
                onSecondary = onEditLayout,
            )
        },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner)) {
            // Physical dimensions are always visible.
            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs)) {
                Text(
                    "${state.paperSize.name} · ${Formatting.sizeBoth(state.paperSize.widthMm, state.paperSize.heightMm)}",
                    style = MaterialTheme.typography.titleSmall,
                )
                Text(
                    Strings.photoInfo(
                        Formatting.size(state.photoSize.widthMm, state.photoSize.heightMm, unit),
                        Formatting.size(state.paperSize.widthMm, state.paperSize.heightMm, unit),
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                if (quality != null) {
                    StatusLine(
                        if (quality.quality == ImageQuality.GOOD) Strings.goodQuality else "${Strings.lowResolution}: ${Strings.readiness(quality)}",
                        if (quality.quality == ImageQuality.GOOD) BannerKind.SUCCESS else BannerKind.WARNING,
                    )
                }
            }

            Canvas(
                Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .onSizeChanged { viewSize = it }
                    .semantics { contentDescription = Strings.sheetDescription(Formatting.sheetOf(sheetIndex, plan.sheetCount), Formatting.size(state.paperSize.widthMm, state.paperSize.heightMm, unit), sheet.placements.size) }
                    .pointerInput(Unit) {
                        detectTransformGestures { centroid, pan, zoom, _ ->
                            val vp = latestViewport ?: return@detectTransformGestures
                            val g = latestGeometry
                            var next = zoomTo(vp, vp.scale * zoom, centroid.x.toDouble(), centroid.y.toDouble())
                            next = SheetViewportMath.panBy(next, pan.x.toDouble(), pan.y.toDouble(), g.sheetW, g.sheetH, g.viewW, g.viewH)
                            viewport = next
                        }
                    }
                    .pointerInput(Unit) {
                        detectTapGestures { tap ->
                            val vp = latestViewport ?: return@detectTapGestures
                            val xMm = (tap.x - vp.offsetX) / vp.scale
                            val yMm = (tap.y - vp.offsetY) / vp.scale
                            val hit = latestSheet.placements.indexOfFirst { xMm >= it.xMm && xMm <= it.rightMm && yMm >= it.yMm && yMm <= it.bottomMm }
                            selectedIndex = if (hit < 0 || hit == selectedIndex) null else hit
                        }
                    },
            ) {
                drawRect(Mat)
                viewport?.let { drawSheet(sheet, it, bitmaps, selectedIndex, placeholder, selectionColor) }
            }

            val sel = selectedIndex?.let { sheet.placements.getOrNull(it) }
            if (sel != null) {
                SelectedPhotoBar(
                    label = "${Strings.editPhoto}: ${Formatting.size(state.photoSize.widthMm, state.photoSize.heightMm, unit)}",
                    onEdit = { onEditPhoto(sel.photoId) },
                    onReplace = { onReplace(sel.photoId) },
                )
            }
            if (reorder) ReorderStrip(state, onMovePhoto)

            Column(Modifier.fillMaxWidth().padding(horizontal = Spacing.sm), horizontalAlignment = Alignment.CenterHorizontally) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    IconButton(onClick = { onSheetChange(sheetIndex - 1) }, enabled = sheetIndex > 0, modifier = Modifier.size(Spacing.minTouchTarget)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowLeft, contentDescription = Strings.previousSheet)
                    }
                    Text(Formatting.sheetOf(sheetIndex, plan.sheetCount), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = Spacing.sm))
                    IconButton(onClick = { onSheetChange(sheetIndex + 1) }, enabled = sheetIndex < plan.sheetCount - 1, modifier = Modifier.size(Spacing.minTouchTarget)) {
                        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = Strings.nextSheet)
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    IconButton(
                        onClick = { latestViewport?.let { viewport = zoomTo(it, SheetViewportMath.stepScale(it, false, actualPxPerMm), geometry.viewW / 2, geometry.viewH / 2) } },
                        modifier = Modifier.size(Spacing.minTouchTarget),
                    ) { Icon(Icons.Filled.Remove, contentDescription = Strings.zoomOut) }
                    Text(
                        Formatting.percent(percent),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(horizontal = Spacing.xs).semantics { contentDescription = Strings.viewZoomNote(Formatting.percent(percent)) },
                    )
                    IconButton(
                        onClick = { latestViewport?.let { viewport = zoomTo(it, SheetViewportMath.stepScale(it, true, actualPxPerMm), geometry.viewW / 2, geometry.viewH / 2) } },
                        modifier = Modifier.size(Spacing.minTouchTarget),
                    ) { Icon(Icons.Filled.Add, contentDescription = Strings.zoomIn) }
                    TextButton(onClick = { if (viewSize.width > 0) viewport = geometry.fit() }, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) {
                        Icon(Icons.Filled.FitScreen, contentDescription = null)
                        Text("  ${Strings.fitToScreen}")
                    }
                    FilterChip(
                        selected = reorder,
                        onClick = { reorder = !reorder },
                        label = { Text(if (reorder) Strings.doneReordering else Strings.reorder) },
                        leadingIcon = { Icon(if (reorder) Icons.Filled.Done else Icons.Filled.SwapVert, contentDescription = null) },
                        modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
                    )
                }
                Text(
                    if (selectedIndex == null) Strings.tapPhotoHint else Strings.viewZoomNote(Formatting.percent(percent)),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = Spacing.md, vertical = Spacing.xs),
                )
            }
        }
    }
}

@Composable
private fun SelectedPhotoBar(label: String, onEdit: () -> Unit, onReplace: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().padding(horizontal = Spacing.screenEdge, vertical = Spacing.xs),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.cardElevation),
    ) {
        Row(Modifier.padding(Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm), verticalAlignment = Alignment.CenterVertically) {
            Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(start = Spacing.sm))
            SecondaryButton(Strings.replace, onReplace)
            PrimaryButton(Strings.editPhoto, onEdit)
        }
    }
}

/** Explicit reorder mode: move whole photos earlier or later in the print order. */
@Composable
private fun ReorderStrip(state: SessionState, onMove: (String, Int) -> Unit) {
    LazyRow(
        Modifier.fillMaxWidth().padding(vertical = Spacing.xs),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = Spacing.screenEdge),
        horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        items(state.photos, key = { it.photoId }) { p ->
            val index = state.photos.indexOf(p)
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                PhotoThumb(p.sourceRef, Modifier.size(64.dp), contentDescription = Strings.thumbDescription(index + 1, state.photos.size))
                Row {
                    IconButton(onClick = { onMove(p.photoId, -1) }, enabled = index > 0, modifier = Modifier.size(Spacing.minTouchTarget)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "${Strings.moveEarlier} ${index + 1}")
                    }
                    IconButton(onClick = { onMove(p.photoId, 1) }, enabled = index < state.photos.lastIndex, modifier = Modifier.size(Spacing.minTouchTarget)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "${Strings.moveLater} ${index + 1}")
                    }
                }
            }
        }
    }
}
