package com.photoprint.pro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.presentation.session.QuickPrint
import com.photoprint.pro.presentation.session.QuickPrints
import com.photoprint.pro.ui.Strings
import com.photoprint.pro.ui.components.EmptyState
import com.photoprint.pro.ui.components.ProjectRow
import com.photoprint.pro.ui.components.SectionHeader
import com.photoprint.pro.ui.components.ShapeOutline

@Composable
fun HomeScreen(
    projects: List<PrintProject>,
    nowMillis: Long,
    unit: LengthUnit,
    onNewPrint: () -> Unit,
    onQuickPrint: (QuickPrint) -> Unit,
    onOpenProject: (String) -> Unit,
    onSeeAll: () -> Unit,
    onSettings: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = Spacing.screenEdge,
            end = Spacing.screenEdge,
            top = contentPadding.calculateTopPadding() + Spacing.md,
            bottom = contentPadding.calculateBottomPadding() + Spacing.lg,
        ),
        verticalArrangement = Arrangement.spacedBy(Spacing.sm),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(Strings.appName, style = MaterialTheme.typography.headlineMedium)
                    Text(Strings.tagline, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                IconButton(onClick = onSettings, modifier = Modifier.size(Spacing.minTouchTarget)) {
                    Icon(Icons.Filled.Settings, contentDescription = Strings.settingsIcon)
                }
            }
            Spacer(Modifier.size(Spacing.md))
        }
        item { NewPrintCard(onNewPrint) }
        item { SectionHeader(Strings.quickPrint) }
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                QuickPrints.all.forEach { q -> QuickPrintTile(q, Modifier.weight(1f)) { onQuickPrint(q) } }
            }
        }
        item {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                SectionHeader(Strings.recentProjects)
                if (projects.isNotEmpty()) TextButton(onClick = onSeeAll) { Text(Strings.seeAll) }
            }
        }
        if (projects.isEmpty()) {
            item { EmptyState(Strings.noProjectsYet, "") }
        } else {
            items(projects.take(3), key = { it.id }) { p ->
                ProjectRow(p, nowMillis, unit, onClick = { onOpenProject(p.id) })
            }
        }
    }
}

@Composable
private fun NewPrintCard(onClick: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primary),
        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
    ) {
        Row(
            Modifier.padding(Spacing.lg).heightIn(min = 96.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Box(
                Modifier.size(64.dp),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Print, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(Strings.newPrint.uppercase(), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimary)
                Text(Strings.newPrintSubtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimary)
            }
        }
    }
}

@Composable
private fun QuickPrintTile(q: QuickPrint, modifier: Modifier, onClick: () -> Unit) {
    Card(
        modifier = modifier.heightIn(min = 96.dp).clickable(role = Role.Button, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.cardElevation),
    ) {
        Column(
            Modifier.padding(vertical = Spacing.md, horizontal = Spacing.xs).fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            ShapeOutline(q.photoSize.widthMm, q.photoSize.heightMm)
            Text(
                Strings.quickPrintName(q.id),
                style = MaterialTheme.typography.labelMedium,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
        }
    }
}
