package com.photoprint.pro.domain.printing

import com.photoprint.pro.domain.measurement.Measurement
import com.photoprint.pro.domain.model.PaperSize
import kotlin.math.abs

enum class PrinterStatus { READY, BUSY, OFFLINE, UNKNOWN }

/** A printer as exposed by Android's print services. Never assume more than the service reports. */
data class PrinterInfo(
    val id: String,
    val name: String,
    val status: PrinterStatus,
    /** Free-form, e.g. "Wi-Fi", when the print service reports it. */
    val connection: String? = null,
)

/** "Not every printer exposes every setting": unknown is different from unsupported. */
enum class Availability { SUPPORTED, UNSUPPORTED, UNKNOWN }

/** Paper size in mils (1/1000 in), as `PrintAttributes.MediaSize` reports. */
data class MediaSpec(val widthMils: Int, val heightMils: Int) {
    val widthMm: Double get() = Measurement.milsToMm(widthMils.toDouble())
    val heightMm: Double get() = Measurement.milsToMm(heightMils.toDouble())
}

data class PrinterCapabilities(
    /** Null = the print service did not report a list. */
    val media: Set<MediaSpec>? = null,
    val borderless: Availability = Availability.UNKNOWN,
    val colorChoice: Availability = Availability.UNKNOWN,
    /** Whether the service lets the app request/verify 100 % scaling. Usually UNKNOWN. */
    val scalingControl: Availability = Availability.UNKNOWN,
) {
    /** Within [toleranceMm] on both sides, in either orientation. */
    fun advertisesPaper(paper: PaperSize, toleranceMm: Double = 1.0): Boolean? = media?.any { m ->
        fun near(a: Double, b: Double) = abs(a - b) <= toleranceMm
        (near(m.widthMm, paper.widthMm) && near(m.heightMm, paper.heightMm)) ||
            (near(m.widthMm, paper.heightMm) && near(m.heightMm, paper.widthMm))
    }
}

enum class ScalingMode { ACTUAL_SIZE, FIT_TO_PAGE }
enum class ColorMode { COLOR, MONOCHROME }
enum class PrintQuality { DRAFT, NORMAL, BEST }

data class PrintSettings(
    val copies: Int = 1,
    val scaling: ScalingMode = ScalingMode.ACTUAL_SIZE,
    val colorMode: ColorMode = ColorMode.COLOR,
    val quality: PrintQuality = PrintQuality.BEST,
    val borderless: Boolean = false,
)
