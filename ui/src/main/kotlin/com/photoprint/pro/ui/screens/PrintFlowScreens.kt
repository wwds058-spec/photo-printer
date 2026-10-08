package com.photoprint.pro.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.photoprint.pro.core.ui.theme.ErrorRed
import com.photoprint.pro.core.ui.theme.Spacing
import com.photoprint.pro.core.ui.theme.SuccessGreen
import com.photoprint.pro.core.ui.theme.WarningAmber
import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.printing.Availability
import com.photoprint.pro.domain.printing.ColorMode
import com.photoprint.pro.domain.printing.PrintQuality
import com.photoprint.pro.domain.printing.PrintSettings
import com.photoprint.pro.domain.printing.PrinterCapabilities
import com.photoprint.pro.domain.printing.PrinterStatus
import com.photoprint.pro.domain.printing.ReadinessReport
import com.photoprint.pro.domain.printing.ScalingMode
import com.photoprint.pro.domain.printing.Severity
import com.photoprint.pro.presentation.app.DiscoveredPrinter
import com.photoprint.pro.presentation.app.GatewayFailure
import com.photoprint.pro.presentation.app.PrinterListState
import com.photoprint.pro.presentation.format.Formatting
import com.photoprint.pro.presentation.session.SessionState
import com.photoprint.pro.ui.Strings
import com.photoprint.pro.ui.components.BannerKind
import com.photoprint.pro.ui.components.BottomActionBar
import com.photoprint.pro.ui.components.InfoBanner
import com.photoprint.pro.ui.components.ScreenScaffold
import com.photoprint.pro.ui.components.SecondaryButton
import com.photoprint.pro.ui.components.SectionHeader
import com.photoprint.pro.ui.components.StatusLine
import com.photoprint.pro.ui.components.SwitchRow

// ---- Printer --------------------------------------------------------------------------------

@Composable
fun PrintersScreen(
    printers: PrinterListState,
    selectedId: String?,
    step: Pair<Int, Int>,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
    onAddPrinter: () -> Unit,
    onContinue: () -> Unit,
    onBack: () -> Unit,
) {
    val canContinue = when (printers) {
        is PrinterListState.Loaded -> selectedId != null
        PrinterListState.Loading, PrinterListState.Idle -> false
        else -> true
    }
    ScreenScaffold(
        title = Strings.printersTitle,
        onBack = onBack,
        step = step,
        bottomBar = { BottomActionBar(Strings.continueLabel, onContinue, primaryEnabled = canContinue) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                SecondaryButton(Strings.refresh, onRefresh, Modifier.weight(1f), icon = Icons.Filled.Refresh)
                SecondaryButton(Strings.addPrinter, onAddPrinter, Modifier.weight(1f), icon = Icons.Filled.Add)
            }
            when (printers) {
                PrinterListState.Idle, PrinterListState.Loading -> Row(
                    Modifier.fillMaxWidth().padding(Spacing.lg),
                    horizontalArrangement = Arrangement.spacedBy(Spacing.md),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(Modifier.padding(end = Spacing.xs))
                    Text(Strings.searching, style = MaterialTheme.typography.bodyLarge)
                }
                is PrinterListState.Loaded -> {
                    if (printers.printers.isEmpty()) InfoBanner(Strings.noPrintService, BannerKind.WARNING)
                    printers.printers.forEach { PrinterCard(it, it.info.id == selectedId) { onSelect(it.info.id) } }
                }
                PrinterListState.SystemDialogOnly -> {
                    Text(Strings.chooseInPrintDialog, style = MaterialTheme.typography.titleMedium)
                    InfoBanner(Strings.systemDialogExplain, BannerKind.INFO)
                }
                is PrinterListState.Failed -> InfoBanner(
                    Strings.printerListFailed,
                    BannerKind.ERROR,
                    detail = if (printers.reason == GatewayFailure.PERMISSION_DENIED) Strings.message(com.photoprint.pro.presentation.app.MessageKind.PERMISSION_DENIED) else Strings.tryAgain,
                )
            }
        }
    }
}

@Composable
private fun PrinterCard(p: DiscoveredPrinter, selected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(Spacing.md).heightIn(min = 56.dp), horizontalArrangement = Arrangement.spacedBy(Spacing.md), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(p.info.name, style = MaterialTheme.typography.titleMedium)
                val connection = p.info.connection?.let { "$it • " } ?: ""
                Text("$connection${Strings.printerStatus(p.info.status)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            // Icon + word: status is never colour alone.
            val (icon, tint) = when (p.info.status) {
                PrinterStatus.READY -> Icons.Filled.CheckCircle to SuccessGreen
                PrinterStatus.BUSY, PrinterStatus.UNKNOWN -> Icons.Filled.Warning to WarningAmber
                PrinterStatus.OFFLINE -> Icons.Filled.Error to ErrorRed
            }
            Icon(icon, contentDescription = Strings.printerStatus(p.info.status), tint = tint)
        }
    }
}

// ---- Print settings -------------------------------------------------------------------------

@Composable
fun PrintSettingsScreen(
    state: SessionState,
    capabilities: PrinterCapabilities?,
    printerName: String?,
    unit: LengthUnit,
    step: Pair<Int, Int>,
    onSettings: (PrintSettings) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
) {
    val s = state.printSettings
    val caps = capabilities ?: PrinterCapabilities()
    ScreenScaffold(
        title = Strings.printSettingsTitle,
        onBack = onBack,
        step = step,
        bottomBar = { BottomActionBar(Strings.next, onNext) },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.sm),
        ) {
            KeyValue(Strings.printersTitle, printerName ?: Strings.chooseInPrintDialog)
            KeyValue(Strings.paper, "${state.paperSize.name} · ${Formatting.size(state.paperSize.widthMm, state.paperSize.heightMm, unit)}")
            KeyValue(Strings.orientation, Strings.orientationFollowsPaper)

            SectionHeader(Strings.quality)
            Row(horizontalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                listOf(PrintQuality.DRAFT to Strings.draft, PrintQuality.NORMAL to Strings.normal, PrintQuality.BEST to Strings.best).forEach { (q, label) ->
                    FilterChip(selected = s.quality == q, onClick = { onSettings(s.copy(quality = q)) }, label = { Text(label) }, modifier = Modifier.heightIn(min = Spacing.minTouchTarget))
                }
            }

            // A setting the printer says it doesn't have is not shown at all.
            if (caps.colorChoice != Availability.UNSUPPORTED) {
                SwitchRow(Strings.color, s.colorMode == ColorMode.COLOR, { onSettings(s.copy(colorMode = if (it) ColorMode.COLOR else ColorMode.MONOCHROME)) }, hint = if (s.colorMode == ColorMode.COLOR) null else Strings.blackAndWhite)
            }

            // Borderless: enabled when supported or unknown (with a caveat), disabled when the printer says no.
            val borderlessUnsupported = caps.borderless == Availability.UNSUPPORTED
            SwitchRow(
                Strings.borderless,
                s.borderless && !borderlessUnsupported,
                { if (!borderlessUnsupported) onSettings(s.copy(borderless = it)) },
                hint = when (caps.borderless) {
                    Availability.UNSUPPORTED -> Strings.borderlessUnsupported
                    Availability.UNKNOWN -> Strings.borderlessMayLeaveMargins
                    Availability.SUPPORTED -> null
                },
            )

            SectionHeader(Strings.scaling)
            ScalingOption(Strings.actualSize, s.scaling == ScalingMode.ACTUAL_SIZE) { onSettings(s.copy(scaling = ScalingMode.ACTUAL_SIZE)) }
            ScalingOption(Strings.fitToPage, s.scaling == ScalingMode.FIT_TO_PAGE) { onSettings(s.copy(scaling = ScalingMode.FIT_TO_PAGE)) }
            if (s.scaling == ScalingMode.FIT_TO_PAGE) InfoBanner(Strings.fitToPageWarning, BannerKind.WARNING)
            Text(Strings.scalingWarning, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(Strings.hiddenSettingsNote, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun KeyValue(key: String, value: String) {
    Column(Modifier.fillMaxWidth().padding(vertical = Spacing.xs)) {
        Text(key, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun ScalingOption(label: String, selected: Boolean, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().selectable(selected = selected, role = Role.RadioButton, onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(if (selected) 2.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Row(Modifier.padding(Spacing.md).heightIn(min = Spacing.minTouchTarget), verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.RadioButton(selected = selected, onClick = null)
            Text(label, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = Spacing.md))
        }
    }
}

// ---- Final check ----------------------------------------------------------------------------

private fun Severity.toBanner() = when (this) {
    Severity.OK -> BannerKind.SUCCESS
    Severity.INFO -> BannerKind.INFO
    Severity.WARNING -> BannerKind.WARNING
    Severity.BLOCKER -> BannerKind.ERROR
}

@Composable
fun FinalCheckScreen(
    report: ReadinessReport,
    step: Pair<Int, Int>,
    onTestPrint: () -> Unit,
    onPrint: () -> Unit,
    onCalibration: () -> Unit,
    onBack: () -> Unit,
) {
    val headline = when {
        !report.canPrint -> Strings.notReadyToPrint
        report.hasWarnings -> Strings.readyWithWarnings
        else -> Strings.readyToPrint
    }
    val headlineKind = when {
        !report.canPrint -> BannerKind.ERROR
        report.hasWarnings -> BannerKind.WARNING
        else -> BannerKind.SUCCESS
    }
    ScreenScaffold(
        title = Strings.finalCheckTitle,
        onBack = onBack,
        step = step,
        bottomBar = {
            BottomActionBar(
                primaryLabel = Strings.printNow,
                onPrimary = onPrint,
                primaryEnabled = report.canPrint,
                secondaryLabel = Strings.testPrint,
                onSecondary = onTestPrint,
                secondaryEnabled = report.canPrint,
            )
        },
    ) { inner ->
        Column(
            Modifier.fillMaxSize().padding(inner).verticalScroll(rememberScrollState()).padding(horizontal = Spacing.screenEdge, vertical = Spacing.sm),
            verticalArrangement = Arrangement.spacedBy(Spacing.xs),
        ) {
            StatusLine(headline.uppercase(), headlineKind)
            report.items.forEach { StatusLine(Strings.readiness(it), it.severity.toBanner()) }
            Text(Strings.scalingWarning, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = Spacing.md))
            Text(Strings.testPrintHint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(top = Spacing.sm))
            TextButton(onClick = onCalibration, modifier = Modifier.heightIn(min = Spacing.minTouchTarget)) { Text(Strings.calibrationLink) }
        }
    }
}

