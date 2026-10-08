package com.photoprint.pro.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.PrintTemplate
import com.photoprint.pro.domain.printing.PrintQuality
import com.photoprint.pro.domain.printing.ScaleDiagnosis
import com.photoprint.pro.domain.printing.ScaleVerdict
import com.photoprint.pro.presentation.format.Formatting
import com.photoprint.pro.presentation.settings.AppSettings
import com.photoprint.pro.presentation.settings.ThemeMode
import com.photoprint.pro.ui.Strings
import com.photoprint.pro.ui.components.BannerKind
import com.photoprint.pro.ui.components.EmptyState
import com.photoprint.pro.ui.components.InfoBanner
import com.photoprint.pro.ui.components.LabeledSlider
import com.photoprint.pro.ui.components.PrimaryButton
import com.photoprint.pro.ui.components.ProjectRow
import com.photoprint.pro.ui.components.ScreenScaffold
import com.photoprint.pro.ui.components.SecondaryButton
import com.photoprint.pro.ui.components.SectionHeader
import com.photoprint.pro.ui.components.ShapeOutline

// ---- Projects -------------------------------------------------------------------------------

@Composable
fun ProjectsScreen(
    projects: List<PrintProject>,
    nowMillis: Long,
    unit: LengthUnit,
    onOpen: (String) -> Unit,
    onRename: (String, String) -> Unit,
    onDuplicate: (String) -> Unit,
    onDelete: (String) -> Unit,
    onReprint: (String) -> Unit,
) {
    var renaming by remember { mutableStateOf<PrintProject?>(null) }
    var deleting by remember { mutableStateOf<PrintProject?>(null) }
    ScreenScaffold(title = Strings.projectsTitle, onBack = null) { inner ->
        if (projects.isEmpty()) {
            Box(Modifier.fillMaxSize().padding(inner), contentAlignment = Alignment.Center) { EmptyState(Strings.projectsTitle, Strings.noProjectsYet) }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = Spacing.screenEdge, end = Spacing.screenEdge, top = inner.calculateTopPadding() + Spacing.sm, bottom = inner.calculateBottomPadding() + Spacing.lg),
                verticalArrangement = Arrangement.spacedBy(Spacing.sm),
            ) {
                items(projects, key = { it.id }) { p ->
                    ProjectRow(p, nowMillis, unit, onClick = { onOpen(p.id) }, menu = {
                        ProjectMenu(
                            onOpen = { onOpen(p.id) },
                            onRename = { renaming = p },
                            onDuplicate = { onDuplicate(p.id) },
                            onReprint = { onReprint(p.id) },
                            onDelete = { deleting = p },
                        )
                    })
                }
            }
        }
    }
    renaming?.let { p ->
        TextInputDialog(
            title = Strings.renameProject,
            label = Strings.projectName,
            initial = p.name,
            confirmLabel = Strings.rename,
            onDismiss = { renaming = null },
            onConfirm = { onRename(p.id, it); renaming = null },
        )
    }
    deleting?.let { p ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(Strings.deleteProjectTitle) },
            text = { Text("${p.name}\n\n${Strings.deleteProjectBody}") },
            confirmButton = { TextButton(onClick = { onDelete(p.id); deleting = null }) { Text(Strings.delete, color = MaterialTheme.colorScheme.error) } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(Strings.cancel) } },
        )
    }
}

@Composable
private fun ProjectMenu(onOpen: () -> Unit, onRename: () -> Unit, onDuplicate: () -> Unit, onReprint: () -> Unit, onDelete: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }, modifier = Modifier.size(Spacing.minTouchTarget)) {
            Icon(Icons.Filled.MoreVert, contentDescription = Strings.more)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text(Strings.open) }, onClick = { open = false; onOpen() })
            DropdownMenuItem(text = { Text(Strings.reprint) }, onClick = { open = false; onReprint() })
            DropdownMenuItem(text = { Text(Strings.rename) }, onClick = { open = false; onRename() })
            DropdownMenuItem(text = { Text(Strings.duplicate) }, onClick = { open = false; onDuplicate() })
            HorizontalDivider()
            DropdownMenuItem(text = { Text(Strings.delete, color = MaterialTheme.colorScheme.error) }, onClick = { open = false; onDelete() })
        }
    }
}

@Composable
fun TextInputDialog(
    title: String,
    label: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = { OutlinedTextField(text, { text = it }, label = { Text(label) }, singleLine = true, modifier = Modifier.fillMaxWidth()) },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirmLabel) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } },
    )
}

// ---- Templates ------------------------------------------------------------------------------

@Composable
fun TemplatesScreen(
    builtIn: List<PrintTemplate>,
    mine: List<PrintTemplate>,
    unit: LengthUnit,
    canSaveCurrent: Boolean,
    onUse: (PrintTemplate) -> Unit,
    onDeleteMine: (String) -> Unit,
    onSaveCurrent: (String) -> Unit,
) {
    var naming by remember { mutableStateOf(false) }
    ScreenScaffold(title = Strings.templatesTitle, onBack = null) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screenEdge, end = Spacing.screenEdge, top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { SectionHeader(Strings.builtInTemplates) }
            items(builtIn, key = { it.id }) { t -> TemplateCard(t, unit, onUse = { onUse(t) }, onDelete = null) }
            item { SectionHeader(Strings.myTemplates) }
            if (mine.isEmpty()) item { Text(Strings.noUserTemplates, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(mine, key = { it.id }) { t -> TemplateCard(t, unit, onUse = { onUse(t) }, onDelete = { onDeleteMine(t.id) }) }
            item {
                Column(Modifier.padding(top = Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    SecondaryButton(Strings.saveAsTemplate, { naming = true }, Modifier.fillMaxWidth(), enabled = canSaveCurrent)
                    Text(Strings.templateNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
    if (naming) {
        TextInputDialog(
            title = Strings.saveAsTemplate,
            label = Strings.templateName,
            initial = "",
            confirmLabel = Strings.save,
            onDismiss = { naming = false },
            onConfirm = { onSaveCurrent(it); naming = false },
        )
    }
}

@Composable
private fun TemplateCard(t: PrintTemplate, unit: LengthUnit, onUse: () -> Unit, onDelete: (() -> Unit)?) {
    Card(
        Modifier.fillMaxWidth().clickable(role = Role.Button, onClick = onUse),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = Spacing.cardElevation),
    ) {
        Row(Modifier.padding(Spacing.md).heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            ShapeOutline(t.photoSize.widthMm, t.photoSize.heightMm)
            Column(Modifier.weight(1f)) {
                Text(t.name, style = MaterialTheme.typography.titleMedium)
                Text(
                    "${Formatting.size(t.photoSize.widthMm, t.photoSize.heightMm, unit)} → ${Formatting.size(t.paperSize.widthMm, t.paperSize.heightMm, unit)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete, modifier = Modifier.size(Spacing.minTouchTarget)) {
                    Icon(Icons.Filled.Delete, contentDescription = "${Strings.delete} ${t.name}")
                }
            }
            TextButton(onClick = onUse, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) { Text(Strings.useTemplate) }
        }
    }
}

// ---- Settings -------------------------------------------------------------------------------

private enum class Picker { PHOTO, PAPER }

@Composable
fun SettingsScreen(
    settings: AppSettings,
    onChange: ((AppSettings) -> AppSettings) -> Unit,
    onCalibration: () -> Unit,
) {
    var picker by remember { mutableStateOf<Picker?>(null) }
    var info by remember { mutableStateOf<String?>(null) }
    val unit = settings.unit
    ScreenScaffold(title = Strings.settingsTitle, onBack = null) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            ValueRow(Strings.defaultPhotoSize, "${settings.defaultPhotoSize.name} · ${Formatting.size(settings.defaultPhotoSize.widthMm, settings.defaultPhotoSize.heightMm, unit)}") { picker = Picker.PHOTO }
            ValueRow(Strings.defaultPaperSize, "${settings.defaultPaperSize.name} · ${Formatting.size(settings.defaultPaperSize.widthMm, settings.defaultPaperSize.heightMm, unit)}") { picker = Picker.PAPER }
            LabeledSlider(
                Strings.defaultSpacing, lengthText(settings.defaultSpacingMm, unit), settings.defaultSpacingMm.toFloat(),
                { v -> onChange { it.copy(defaultSpacingMm = Math.round(v * 2) / 2.0) } }, 0f..AppSettings.MAX_SPACING_MM.toFloat(), steps = 19,
            )
            LabeledSlider(
                Strings.defaultMargins, lengthText(settings.defaultMarginMm, unit), settings.defaultMarginMm.toFloat(),
                { v -> onChange { it.copy(defaultMarginMm = Math.round(v * 2) / 2.0) } }, 0f..AppSettings.MAX_MARGIN_MM.toFloat(), steps = 39,
            )
            SectionHeader(Strings.units)
            ChipRow(LengthUnit.entries.map { it to it.symbol }, unit) { u -> onChange { it.copy(unit = u) } }
            SectionHeader(Strings.quality)
            ChipRow(listOf(PrintQuality.DRAFT to Strings.draft, PrintQuality.NORMAL to Strings.normal, PrintQuality.BEST to Strings.best), settings.quality) { q -> onChange { it.copy(quality = q) } }
            SectionHeader(Strings.theme)
            ChipRow(listOf(ThemeMode.SYSTEM to Strings.themeSystem, ThemeMode.LIGHT to Strings.themeLight, ThemeMode.DARK to Strings.themeDark), settings.theme) { t -> onChange { it.copy(theme = t) } }
            SectionHeader(Strings.language)
            ChipRow(listOf<Pair<String?, String>>(null to Strings.languageSystem, "en" to "English"), settings.language) { l -> onChange { it.copy(language = l) } }
            SectionHeader(Strings.about)
            ValueRow(Strings.calibration, Strings.calibrationLink, onCalibration)
            ValueRow(Strings.privacy, Strings.privacyBody.take(60) + "…") { info = Strings.privacyBody }
            ValueRow(Strings.about, Strings.appName) { info = Strings.aboutBody }
        }
    }

    when (picker) {
        Picker.PHOTO -> ChoiceDialog(
            Strings.defaultPhotoSize,
            PhotoSizePresets.all.map<PhotoSize, Pair<PhotoSize, String>> { it to "${it.name} · ${Formatting.size(it.widthMm, it.heightMm, unit)}" },
            settings.defaultPhotoSize,
            onDismiss = { picker = null },
            onPick = { p -> onChange { it.copy(defaultPhotoSize = p) }; picker = null },
        )
        Picker.PAPER -> ChoiceDialog(
            Strings.defaultPaperSize,
            PaperSizePresets.all.map<PaperSize, Pair<PaperSize, String>> { it to "${it.name} · ${Formatting.size(it.widthMm, it.heightMm, unit)}" },
            settings.defaultPaperSize,
            onDismiss = { picker = null },
            onPick = { p -> onChange { it.copy(defaultPaperSize = p) }; picker = null },
        )
        null -> Unit
    }
    info?.let { text ->
        AlertDialog(onDismissRequest = { info = null }, text = { Text(text) }, confirmButton = { TextButton(onClick = { info = null }) { Text(Strings.ok) } })
    }
}

@Composable
private fun ValueRow(title: String, value: String, onClick: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget + 8.dp).clickable(role = Role.Button, onClick = onClick).padding(vertical = Spacing.sm),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun <T> ChipRow(options: List<Pair<T, String>>, selected: T, onSelect: (T) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        options.forEach { (value, label) ->
            FilterChip(selected = value == selected, onClick = { onSelect(value) }, label = { Text(label) }, modifier = Modifier.heightIn(min = Spacing.minTouchTarget))
        }
    }
}

@Composable
private fun <T> ChoiceDialog(title: String, options: List<Pair<T, String>>, selected: T, onDismiss: () -> Unit, onPick: (T) -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                options.forEach { (value, label) ->
                    Row(
                        Modifier.fillMaxWidth().heightIn(min = Spacing.minTouchTarget).clickable(role = Role.RadioButton) { onPick(value) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        androidx.compose.material3.RadioButton(selected = value == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = Spacing.md))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text(Strings.cancel) } },
    )
}

// ---- Calibration ----------------------------------------------------------------------------

@Composable
fun CalibrationScreen(onPrint: () -> Unit, onBack: () -> Unit) {
    var text by remember { mutableStateOf("") }
    val measured = text.trim().replace(',', '.').toDoubleOrNull()
    val diagnosis = measured?.let { ScaleDiagnosis.fromMeasured(100.0, it) }
    ScreenScaffold(title = Strings.calibrationTitle, onBack = onBack) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            Text(Strings.calibrationIntro, style = MaterialTheme.typography.bodyLarge)
            Text(Strings.calibrationSteps, style = MaterialTheme.typography.bodyMedium)
            PrimaryButton(Strings.printCalibration, onPrint, Modifier.fillMaxWidth())
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(Strings.measuredLength) },
                singleLine = true,
                isError = text.isNotBlank() && diagnosis == null,
                supportingText = { if (text.isNotBlank() && diagnosis == null) Text(Strings.calibrationInvalid) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth(),
            )
            if (diagnosis != null) {
                when (diagnosis.verdict) {
                    ScaleVerdict.ACCURATE -> InfoBanner(Strings.calibrationAccurate, BannerKind.SUCCESS)
                    ScaleVerdict.PRINTED_SMALL -> InfoBanner(Strings.calibrationSmall(diagnosis.scalePercent), BannerKind.WARNING)
                    ScaleVerdict.PRINTED_LARGE -> InfoBanner(Strings.calibrationLarge(diagnosis.scalePercent), BannerKind.WARNING)
                }
            }
        }
    }
}
