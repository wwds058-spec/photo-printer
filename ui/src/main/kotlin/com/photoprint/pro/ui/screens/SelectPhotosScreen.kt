package com.photoprint.pro.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.presentation.format.Formatting
import com.photoprint.pro.ui.Strings
import com.photoprint.pro.ui.components.BottomActionBar
import com.photoprint.pro.ui.components.EmptyState
import com.photoprint.pro.ui.components.PhotoThumb
import com.photoprint.pro.ui.components.PrimaryButton
import com.photoprint.pro.ui.components.ScreenScaffold
import com.photoprint.pro.ui.components.SecondaryButton

@Composable
fun SelectPhotosScreen(
    photos: List<ProjectPhoto>,
    step: Pair<Int, Int>,
    onAddGallery: () -> Unit,
    onAddCamera: () -> Unit,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onEdit: (String) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    ScreenScaffold(
        title = Strings.selectPhotosTitle,
        onBack = onBack,
        step = step,
        bottomBar = { BottomActionBar(Strings.next, onNext, primaryEnabled = photos.isNotEmpty()) },
    ) { inner ->
        Column(Modifier.fillMaxSize().padding(inner).padding(horizontal = Spacing.screenEdge)) {
            Text(
                Formatting.selected(photos.size),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.semantics { contentDescription = Formatting.selected(photos.size) },
            )
            Row(Modifier.fillMaxWidth().padding(vertical = Spacing.sm), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                PrimaryButton(Strings.fromGallery, onAddGallery, Modifier.weight(1f), icon = Icons.Filled.PhotoLibrary)
                SecondaryButton(Strings.fromCamera, onAddCamera, Modifier.weight(1f), icon = Icons.Filled.CameraAlt)
            }
            if (photos.isEmpty()) {
                EmptyState(Strings.selectPhotosTitle, Strings.noPhotosHint)
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(bottom = Spacing.md),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalArrangement = Arrangement.spacedBy(Spacing.sm),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    item(span = { GridItemSpan(maxLineSpan) }) {
                        Text(Strings.photoOrder, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    itemsIndexed(photos, key = { _, p -> p.photoId }) { index, p ->
                        PhotoCell(p, index, photos.size, onRemove, onMove, onEdit)
                    }
                }
            }
        }
    }
}

@Composable
private fun PhotoCell(
    photo: ProjectPhoto,
    index: Int,
    total: Int,
    onRemove: (String) -> Unit,
    onMove: (String, Int) -> Unit,
    onEdit: (String) -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.cardElevation),
    ) {
        Column {
            Box {
                PhotoThumb(
                    photo.sourceRef,
                    Modifier.fillMaxWidth().aspectRatio(1f),
                    contentDescription = Strings.thumbDescription(index + 1, total),
                )
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.padding(Spacing.sm).clip(CircleShape).background(MaterialTheme.colorScheme.primary).padding(horizontal = 8.dp, vertical = 2.dp),
                )
                IconButton(
                    onClick = { onRemove(photo.photoId) },
                    modifier = Modifier.align(Alignment.TopEnd).size(Spacing.minTouchTarget),
                ) {
                    Icon(
                        Icons.Filled.Close,
                        contentDescription = "${Strings.removePhoto} ${index + 1}",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.clip(CircleShape).background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)).padding(4.dp),
                    )
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onMove(photo.photoId, -1) }, enabled = index > 0, modifier = Modifier.size(Spacing.minTouchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "${Strings.moveEarlier} ${index + 1}")
                }
                IconButton(onClick = { onEdit(photo.photoId) }, modifier = Modifier.size(Spacing.minTouchTarget)) {
                    Icon(Icons.Filled.Edit, contentDescription = "${Strings.editPhoto} ${index + 1}")
                }
                IconButton(onClick = { onMove(photo.photoId, 1) }, enabled = index < total - 1, modifier = Modifier.size(Spacing.minTouchTarget)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "${Strings.moveLater} ${index + 1}")
                }
            }
        }
    }
}
