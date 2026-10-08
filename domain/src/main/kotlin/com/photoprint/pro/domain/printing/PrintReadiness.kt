package com.photoprint.pro.domain.printing

import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.layout.LayoutError
import com.photoprint.pro.domain.layout.LayoutOutcome
import com.photoprint.pro.domain.layout.LayoutPlan
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.toLayoutRequest
import com.photoprint.pro.domain.pdf.ImageFit
import com.photoprint.pro.domain.pdf.ImageQuality

/** INFO = neutral, not verified and not a problem (e.g. the printer is picked later in Android's print dialog). */
enum class Severity { OK, INFO, WARNING, BLOCKER }

/**
 * One line of the READY TO PRINT screen. Structured values, no English: the UI formats and
 * localises. Anything that is not a verified fact is a WARNING, never an OK.
 */
sealed interface ReadinessItem {
    val severity: Severity

    data class LayoutFailed(val error: LayoutError) : ReadinessItem {
        override val severity = Severity.BLOCKER
    }

    data class Photo(val size: PhotoSize) : ReadinessItem {
        override val severity = Severity.OK
    }

    data class Paper(val size: PaperSize) : ReadinessItem {
        override val severity = Severity.OK
    }

    data class Copies(val photos: Int, val sheets: Int, val photosPerSheet: Int) : ReadinessItem {
        override val severity = Severity.OK
    }

    data object NoPrinter : ReadinessItem {
        override val severity = Severity.BLOCKER
    }

    /**
     * Android's public print API has no printer list for apps: the system print dialog asks for the printer.
     * Printer state and paper support are therefore unknown until then — neutral, not a pass.
     */
    data object PrinterChosenInPrintDialog : ReadinessItem {
        override val severity = Severity.INFO
    }

    data class Printer(val printer: PrinterInfo) : ReadinessItem {
        override val severity = when (printer.status) {
            PrinterStatus.READY -> Severity.OK
            PrinterStatus.OFFLINE -> Severity.BLOCKER
            PrinterStatus.BUSY, PrinterStatus.UNKNOWN -> Severity.WARNING
        }
    }

    /** The printer reported a paper list and this size is not in it. */
    data class PaperNotAdvertised(val paper: PaperSize) : ReadinessItem {
        override val severity = Severity.WARNING
    }

    /** User chose Fit to Page: output will be scaled, physical sizes are not guaranteed. */
    data object ScalingWillResize : ReadinessItem {
        override val severity = Severity.WARNING
    }

    /** Actual size requested, but the print service cannot confirm it is honoured. */
    data object ScalingUnverified : ReadinessItem {
        override val severity = Severity.WARNING
    }

    data object ScalingActualSize : ReadinessItem {
        override val severity = Severity.OK
    }

    /** Borderless requested but unsupported or unconfirmed: printable margins may remain. */
    data class BorderlessNotGuaranteed(val availability: Availability) : ReadinessItem {
        override val severity = Severity.WARNING
    }

    data class ImageQualityResult(val quality: ImageQuality, val lowestDpi: Double, val photoIds: List<String>) : ReadinessItem {
        override val severity = if (quality == ImageQuality.GOOD) Severity.OK else Severity.WARNING
    }
}

data class ReadinessReport(val items: List<ReadinessItem>) {
    val canPrint: Boolean get() = items.none { it.severity == Severity.BLOCKER }
    val hasWarnings: Boolean get() = items.any { it.severity == Severity.WARNING }
}

object PrintReadiness {

    fun evaluate(
        project: PrintProject,
        printer: PrinterInfo?,
        capabilities: PrinterCapabilities,
        settings: PrintSettings,
        engine: LayoutEngine = LayoutEngine(),
        printerChosenBySystem: Boolean = false,
    ): ReadinessReport {
        val items = mutableListOf<ReadinessItem>()

        val plan = when (val out = engine.calculate(project.toLayoutRequest())) {
            is LayoutOutcome.Failure -> {
                items += ReadinessItem.LayoutFailed(out.error)
                null
            }
            is LayoutOutcome.Success -> out.value
        }
        if (plan != null) {
            items += ReadinessItem.Photo(project.photoSize)
            items += ReadinessItem.Paper(project.paperSize)
            items += ReadinessItem.Copies(plan.totalPhotos, plan.sheetCount, plan.photosPerSheet)
        }

        if (printer == null) {
            items += if (printerChosenBySystem) ReadinessItem.PrinterChosenInPrintDialog else ReadinessItem.NoPrinter
        } else {
            items += ReadinessItem.Printer(printer)
            if (capabilities.advertisesPaper(project.paperSize) == false) {
                items += ReadinessItem.PaperNotAdvertised(project.paperSize)
            }
        }

        items += when {
            settings.scaling == ScalingMode.FIT_TO_PAGE -> ReadinessItem.ScalingWillResize
            capabilities.scalingControl == Availability.SUPPORTED -> ReadinessItem.ScalingActualSize
            else -> ReadinessItem.ScalingUnverified
        }

        if (settings.borderless && capabilities.borderless != Availability.SUPPORTED) {
            items += ReadinessItem.BorderlessNotGuaranteed(capabilities.borderless)
        }

        imageQuality(project)?.let { items += it }
        return ReadinessReport(items)
    }

    /** Worst resolution among the project's photos at their current crop/zoom. */
    fun imageQuality(project: PrintProject): ReadinessItem.ImageQualityResult? {
        if (project.photos.isEmpty()) return null
        val dpis = project.photos.associate {
            it.photoId to ImageFit.effectiveDpi(it.widthPx, it.heightPx, it.crop, project.photoSize.widthMm, project.photoSize.heightMm)
        }
        val lowest = dpis.values.min()
        val quality = ImageFit.quality(lowest)
        val offenders = if (quality == ImageQuality.GOOD) emptyList() else dpis.filterValues { ImageFit.quality(it) == quality }.keys.toList()
        return ReadinessItem.ImageQualityResult(quality, lowest, offenders)
    }

    /** "Print 1 test copy": just the first sheet of the real layout, so dimensions can be checked before a big batch. */
    fun testSheet(plan: LayoutPlan): LayoutPlan {
        val first = plan.sheets.first().copy(sheetIndex = 0, totalSheets = 1)
        return LayoutPlan(plan.grid, first.placements.size, listOf(first))
    }
}
