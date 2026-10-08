package com.photoprint.pro.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.presentation.format.Formatting
import com.photoprint.pro.presentation.input.SizeForm
import com.photoprint.pro.presentation.input.SizeKind
import com.photoprint.pro.ui.Strings
import com.photoprint.pro.ui.components.BannerKind
import com.photoprint.pro.ui.components.BottomActionBar
import com.photoprint.pro.ui.components.InfoBanner
import com.photoprint.pro.ui.components.PrimaryButton
import com.photoprint.pro.ui.components.ScreenScaffold
import com.photoprint.pro.ui.components.SectionHeader
import com.photoprint.pro.ui.components.SizeCard

@Composable
fun PhotoSizeScreen(
    selected: PhotoSize,
    unit: LengthUnit,
    step: Pair<Int, Int>,
    onSelect: (PhotoSize) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val isPreset = PhotoSizePresets.all.any { it == selected }
    ScreenScaffold(
        title = Strings.photoSizeTitle,
        onBack = onBack,
        step = step,
        bottomBar = { BottomActionBar(Strings.next, onNext) },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screenEdge, end = Spacing.screenEdge, top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { SectionHeader(Strings.popular) }
            PhotoSizePresets.popular.forEach { p ->
                item(key = "p-${p.name}") { SizeCard(p.name, Formatting.sizeBoth(p.widthMm, p.heightMm), p.widthMm, p.heightMm, selected == p, { onSelect(p) }) }
            }
            item { SectionHeader(Strings.international) }
            PhotoSizePresets.international.forEach { p ->
                item(key = "i-${p.name}") { SizeCard(p.name, Formatting.sizeBoth(p.widthMm, p.heightMm), p.widthMm, p.heightMm, selected == p, { onSelect(p) }) }
            }
            item { SectionHeader(Strings.custom) }
            item {
                CustomSizeSection(
                    kind = SizeKind.PHOTO,
                    name = Strings.customSize,
                    initialMm = if (isPreset) null else selected.widthMm to selected.heightMm,
                    initialUnit = unit,
                    selected = !isPreset,
                    onUse = { w, h -> onSelect(PhotoSize(Strings.customSize, w, h)) },
                )
            }
            item { InfoBanner(Strings.sizePresetDisclaimer, BannerKind.INFO, Modifier.padding(top = Spacing.md)) }
        }
    }
}

@Composable
fun PaperSizeScreen(
    selected: PaperSize,
    unit: LengthUnit,
    step: Pair<Int, Int>,
    onSelect: (PaperSize) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val isPreset = PaperSizePresets.all.any { it == selected }
    ScreenScaffold(
        title = Strings.paperSizeTitle,
        onBack = onBack,
        step = step,
        bottomBar = { BottomActionBar(Strings.next, onNext) },
    ) { inner ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = Spacing.screenEdge, end = Spacing.screenEdge, top = inner.calculateTopPadding(), bottom = inner.calculateBottomPadding() + Spacing.lg),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            item { SectionHeader(Strings.photoPaper) }
            PaperSizePresets.photoPaper.forEach { p ->
                item(key = "pp-${p.name}") {
                    SizeCard(
                        p.name, Formatting.sizeBoth(p.widthMm, p.heightMm), p.widthMm, p.heightMm, selected == p, { onSelect(p) },
                        badge = if (p == PaperSizePresets.Photo4x6) Strings.mostCommon else null,
                    )
                }
            }
            item { SectionHeader(Strings.standardPaper) }
            PaperSizePresets.standardPaper.forEach { p ->
                item(key = "sp-${p.name}") { SizeCard(p.name, Formatting.sizeBoth(p.widthMm, p.heightMm), p.widthMm, p.heightMm, selected == p, { onSelect(p) }) }
            }
            item { SectionHeader(Strings.custom) }
            item {
                CustomSizeSection(
                    kind = SizeKind.PAPER,
                    name = Strings.customSize,
                    initialMm = if (isPreset) null else selected.widthMm to selected.heightMm,
                    initialUnit = unit,
                    selected = !isPreset,
                    onUse = { w, h -> onSelect(PaperSize(Strings.customSize, w, h)) },
                )
            }
        }
    }
}

/** Width, height and unit entry. Typed text is kept as typed; millimetres only come out when valid. */
@Composable
private fun CustomSizeSection(
    kind: SizeKind,
    name: String,
    initialMm: Pair<Double, Double>?,
    initialUnit: LengthUnit,
    selected: Boolean,
    onUse: (Double, Double) -> Unit,
) {
    var form by remember(initialMm, kind) {
        mutableStateOf(initialMm?.let { SizeForm.fromMm(kind, it.first, it.second, initialUnit) } ?: SizeForm(kind, unit = initialUnit))
    }
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
        Text(
            if (selected) "$name ✓" else name,
            style = MaterialTheme.typography.titleMedium,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            LengthUnit.entries.forEach { u ->
                FilterChip(
                    selected = form.unit == u,
                    onClick = { form = form.withUnit(u) },
                    label = { Text(u.symbol) },
                    modifier = Modifier.heightIn(min = Spacing.minTouchTarget),
                )
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
            SizeField(Strings.width, form.widthText, form.width, form.unit, kind, { form = form.copy(widthText = it) }, Modifier.weight(1f))
            SizeField(Strings.height, form.heightText, form.height, form.unit, kind, { form = form.copy(heightText = it) }, Modifier.weight(1f))
        }
        PrimaryButton(
            Strings.useThisSize,
            onClick = { form.sizeMm?.let { (w, h) -> onUse(w, h) } },
            enabled = form.isValid,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

@Composable
private fun SizeField(
    label: String,
    text: String,
    field: SizeForm.Field,
    unit: LengthUnit,
    kind: SizeKind,
    onChange: (String) -> Unit,
    modifier: Modifier,
) {
    val showError = text.isNotEmpty() && field.error != null
    OutlinedTextField(
        value = text,
        onValueChange = onChange,
        label = { Text("$label (${unit.symbol})") },
        singleLine = true,
        isError = showError,
        supportingText = {
            if (showError) {
                Text(
                    Strings.fieldError(
                        field.error!!,
                        Formatting.size(kind.minMm, kind.minMm, unit).substringBefore(" ×"),
                        Formatting.size(kind.maxMm, kind.maxMm, unit).substringBefore(" ×"),
                    ),
                )
            }
        },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        modifier = modifier,
    )
}
