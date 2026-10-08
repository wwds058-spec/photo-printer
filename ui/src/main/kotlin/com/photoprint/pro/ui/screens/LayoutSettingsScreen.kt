package com.photoprint.pro.ui.screens

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.photoprint.pro.core.ui.theme.Mat
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.presentation.input.SizeForm
import com.photoprint.pro.presentation.layout.LayoutFeedback
import com.photoprint.pro.presentation.preview.SheetViewportMath
import com.photoprint.pro.presentation.session.PrintSession
import com.photoprint.pro.presentation.session.SessionState
import com.photoprint.pro.presentation.settings.AppSettings
import com.photoprint.pro.ui.Strings
import com.photoprint.pro.ui.components.BannerKind
import com.photoprint.pro.ui.components.BottomActionBar
import com.photoprint.pro.ui.components.InfoBanner
import com.photoprint.pro.ui.components.LabeledSlider
import com.photoprint.pro.ui.components.ScreenScaffold
import com.photoprint.pro.ui.components.Stepper
import com.photoprint.pro.ui.components.SwitchRow
import com.photoprint.pro.ui.components.drawSheet

fun lengthText(mm: Double, unit: LengthUnit): String = "${SizeForm.format(unit.fromMm(mm))} ${unit.symbol}"

@Composable
fun LayoutSettingsScreen(
    state: SessionState,
    unit: LengthUnit,
    step: Pair<Int, Int>,
    onCopies: (Int) -> Unit,
    onAutoFill: (Boolean) -> Unit,
    onFillSheets: (Int) -> Unit,
    onSpacing: (Double) -> Unit,
    onMargin: (Double) -> Unit,
    onRotation: (Boolean) -> Unit,
    onCutLines: (Boolean) -> Unit,
    onPreview: () -> Unit,
    onBack: () -> Unit,
) {
    val feedback = LayoutFeedback.from(state)
    ScreenScaffold(
        title = Strings.layoutTitle,
        onBack = onBack,
        step = step,
        bottomBar = { BottomActionBar(Strings.preview, onPreview, primaryEnabled = feedback is LayoutFeedback.Ok) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.md),
        ) {
            when (feedback) {
                is LayoutFeedback.Ok -> EstimateCard(feedback, state)
                is LayoutFeedback.Problem -> InfoBanner(
                    Strings.layoutProblem(feedback.problem.error),
                    BannerKind.ERROR,
                    detail = feedback.problem.suggestions.joinToString("\n") { "• ${Strings.suggestion(it)}" },
                )
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.cardElevation),
            ) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(Strings.copiesPerPhoto, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                        Stepper(state.copies, onCopies, 1, PrintSession.MAX_COPIES, Strings.copies)
                    }
                    SwitchRow(Strings.autoFill, state.autoFill, onAutoFill, hint = Strings.autoFillHint)
                    if (state.autoFill) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                            Text(Strings.sheetsToFill, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                            Stepper(state.fillSheets, onFillSheets, 1, PrintSession.MAX_FILL_SHEETS, Strings.sheetsToFill)
                        }
                    }
                }
            }

            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = Spacing.cardElevation),
            ) {
                Column(Modifier.padding(Spacing.md), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                    LabeledSlider(
                        Strings.spacing, lengthText(state.spacing.horizontalMm, unit), state.spacing.horizontalMm.toFloat(),
                        { onSpacing(snap(it.toDouble(), 0.5)) }, 0f..AppSettings.MAX_SPACING_MM.toFloat(), steps = 19,
                    )
                    LabeledSlider(
                        Strings.margins, lengthText(state.margin.leftMm, unit), state.margin.leftMm.toFloat(),
                        { onMargin(snap(it.toDouble(), 0.5)) }, 0f..AppSettings.MAX_MARGIN_MM.toFloat(), steps = 39,
                    )
                    SwitchRow(Strings.autoRotate, state.allowRotation, onRotation, hint = Strings.autoRotateHint)
                    SwitchRow(Strings.cutLines, state.showCutLines, onCutLines, hint = Strings.cutLinesHint)
                }
            }
        }
    }
}

private fun snap(v: Double, step: Double) = Math.round(v / step) * step

@Composable
private fun EstimateCard(ok: LayoutFeedback.Ok, state: SessionState) {
    val s = ok.summary
    val sheet = state.plan?.sheets?.firstOrNull()
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Row(Modifier.padding(Spacing.md), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            if (sheet != null) {
                val sheetPlaceholder = MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)
                val selection = MaterialTheme.colorScheme.primary
                Canvas(
                    Modifier.height(132.dp).weight(0.45f).semantics {
                        contentDescription = "${Strings.estimate}: ${Strings.perSheet(s.photosPerSheet)}, ${Strings.grid(s.columns, s.rows)}"
                    },
                ) {
                    drawRect(Mat)
                    val vp = SheetViewportMath.fit(sheet.paperWidthMm, sheet.paperHeightMm, size.width.toDouble(), size.height.toDouble(), 6.0)
                    drawSheet(sheet, vp, emptyMap(), null, sheetPlaceholder, selection)
                }
            }
            Column(Modifier.weight(0.55f), verticalArrangement = Arrangement.spacedBy(Spacing.xs)) {
                Text(Strings.estimate, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Strings.perSheet(s.photosPerSheet), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Strings.grid(s.columns, s.rows), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(Strings.totals(s.totalPhotos, s.sheets), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                if (s.sheets > 1 && s.photosOnLastSheet < s.photosPerSheet) {
                    Text(Strings.lastSheet(s.photosOnLastSheet), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                }
                if (s.rotated) Text(Strings.photosTurned, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
            }
        }
    }
}

