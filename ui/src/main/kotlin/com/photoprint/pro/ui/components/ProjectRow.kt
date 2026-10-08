package com.photoprint.pro.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.presentation.format.Formatting
import com.photoprint.pro.ui.Strings

/** A saved project: thumbnail, name, paper, photo size, count and date. [menu] is the overflow menu. */
@Composable
fun ProjectRow(
    project: PrintProject,
    nowMillis: Long,
    unit: LengthUnit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    menu: (@Composable () -> Unit)? = null,
) {
    Card(
        modifier = modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.cardElevation),
    ) {
        Row(
            Modifier.padding(Spacing.md).heightIn(min = 64.dp),
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val first = project.photos.firstOrNull()
            if (first != null) PhotoThumb(first.sourceRef, Modifier.size(64.dp)) else ShapeOutline(project.photoSize.widthMm, project.photoSize.heightMm)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(project.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Strings.projectLine(
                        Formatting.size(project.paperSize.widthMm, project.paperSize.heightMm, unit),
                        Formatting.size(project.photoSize.widthMm, project.photoSize.heightMm, unit),
                        project.requestedCopies,
                    ),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    Formatting.when_(project.modifiedAtMillis, nowMillis),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            menu?.invoke()
        }
    }
}
