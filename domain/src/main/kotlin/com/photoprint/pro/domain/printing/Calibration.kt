package com.photoprint.pro.domain.printing

import com.photoprint.pro.domain.measurement.Measurement
import com.photoprint.pro.domain.measurement.Rect
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.pdf.PdfLine
import com.photoprint.pro.domain.pdf.PdfOverlay
import com.photoprint.pro.domain.pdf.PdfPageSpec
import com.photoprint.pro.domain.pdf.PdfStrokeLine
import com.photoprint.pro.domain.pdf.PdfStrokeRect
import com.photoprint.pro.domain.pdf.PdfText
import kotlin.math.abs

sealed interface CalibrationOutcome {
    data class Page(
        val spec: PdfPageSpec,
        /** False when the selected photo frame is too big to draw next to the rulers without scaling it. */
        val includesPhotoFrame: Boolean,
        val referenceLengthsMm: List<Double>,
    ) : CalibrationOutcome

    /** The paper cannot hold a 100 mm reference line plus labels. */
    data class PaperTooSmall(val minLongSideMm: Double) : CalibrationOutcome
}

/**
 * A page of known-length reference lines (100 mm and 50 mm) and the selected photo frame drawn at
 * its true size. Print it at actual size, measure with a ruler, and compare — this isolates
 * printer/driver scaling from everything else.
 */
object CalibrationPage {
    const val LONG_LINE_MM = 100.0
    const val SHORT_LINE_MM = 50.0
    private const val MARGIN = 10.0
    private const val TOP_BLOCK = 24.0
    private const val BOTTOM_CLEARANCE = 8.0
    private const val TICK = 3.0

    fun build(paper: PaperSize, photo: PhotoSize): CalibrationOutcome {
        val w = paper.widthMm
        val h = paper.heightMm
        val portrait = h >= w
        val y0 = MARGIN + TOP_BLOCK
        val minLong = if (portrait) y0 + LONG_LINE_MM + BOTTOM_CLEARANCE else 2 * MARGIN + LONG_LINE_MM
        if ((if (portrait) h else w) < minLong || (if (portrait) w else h) < 2 * MARGIN + SHORT_LINE_MM + 6) {
            return CalibrationOutcome.PaperTooSmall(minLong)
        }

        val o = mutableListOf<PdfOverlay>()
        fun text(s: String, xMm: Double, yMm: Double, size: Double = 9.0) =
            o.add(PdfText(s, mm(xMm), mm(yMm), size))
        fun line(x1: Double, y1: Double, x2: Double, y2: Double) =
            o.add(PdfStrokeLine(PdfLine(mm(x1), mm(y1), mm(x2), mm(y2)), widthPt = 0.5))

        text("PHOTOPrint Pro – Calibration", MARGIN, MARGIN + 4, 12.0)
        text("Paper ${fmt(w)} × ${fmt(h)} mm. Print at ACTUAL SIZE / 100 % (not Fit to Page).", MARGIN, MARGIN + 10)
        text("Measure each line with a ruler. Expected: 100 mm and 50 mm.", MARGIN, MARGIN + 15)

        // Rulers: the 100 mm line runs along the long side, the 50 mm line along the short side.
        val rulerClearance: Double
        if (portrait) {
            val x = w - MARGIN
            line(x, y0, x, y0 + LONG_LINE_MM)
            line(x - TICK / 2, y0, x + TICK / 2, y0)
            line(x - TICK / 2, y0 + LONG_LINE_MM, x + TICK / 2, y0 + LONG_LINE_MM)
            text("100 mm", x - 16, y0 + 50)
            line(MARGIN, y0, MARGIN + SHORT_LINE_MM, y0)
            line(MARGIN, y0 - TICK / 2, MARGIN, y0 + TICK / 2)
            line(MARGIN + SHORT_LINE_MM, y0 - TICK / 2, MARGIN + SHORT_LINE_MM, y0 + TICK / 2)
            text("50 mm", MARGIN, y0 - 3)
            rulerClearance = x - 6
        } else {
            line(MARGIN, y0, MARGIN + LONG_LINE_MM, y0)
            line(MARGIN, y0 - TICK / 2, MARGIN, y0 + TICK / 2)
            line(MARGIN + LONG_LINE_MM, y0 - TICK / 2, MARGIN + LONG_LINE_MM, y0 + TICK / 2)
            text("100 mm", MARGIN + 40, y0 - 3)
            val x = w - MARGIN
            line(x, y0, x, y0 + SHORT_LINE_MM)
            line(x - TICK / 2, y0, x + TICK / 2, y0)
            line(x - TICK / 2, y0 + SHORT_LINE_MM, x + TICK / 2, y0 + SHORT_LINE_MM)
            text("50 mm", x - 14, y0 + 25)
            rulerClearance = x - 6
        }

        // The selected photo frame at its true size (never scaled to fit).
        val fx = MARGIN
        val fy = y0 + 14
        val fits = fx + photo.widthMm <= rulerClearance && fy + photo.heightMm + 6 <= h - BOTTOM_CLEARANCE
        if (fits) {
            o.add(PdfStrokeRect(Rect(mm(fx), mm(fy), mm(photo.widthMm), mm(photo.heightMm)), widthPt = 0.5))
            text("${photo.name}: ${fmt(photo.widthMm)} × ${fmt(photo.heightMm)} mm", fx, fy + photo.heightMm + 5)
        }

        val spec = PdfPageSpec(mm(w), mm(h), frames = emptyList(), cutLines = emptyList(), overlays = o)
        return CalibrationOutcome.Page(spec, fits, listOf(LONG_LINE_MM, SHORT_LINE_MM))
    }

    private fun mm(v: Double) = Measurement.mmToPoints(v)

    private fun fmt(v: Double) = if (v == Math.rint(v)) v.toLong().toString() else String.format(java.util.Locale.ROOT, "%.1f", v)
}

enum class ScaleVerdict { ACCURATE, PRINTED_SMALL, PRINTED_LARGE }

data class ScaleDiagnosis(
    val nominalMm: Double,
    val measuredMm: Double,
    /** 100 = exact. */
    val scalePercent: Double,
    val errorMm: Double,
    val verdict: ScaleVerdict,
) {
    companion object {
        /** Within 0.5 % (0.5 mm over 100 mm) counts as accurate: below ruler-reading precision. */
        const val TOLERANCE_PERCENT = 0.5

        /** Returns null if either number is not a positive finite length. */
        fun fromMeasured(nominalMm: Double, measuredMm: Double): ScaleDiagnosis? {
            if (!nominalMm.isFinite() || !measuredMm.isFinite() || nominalMm <= 0 || measuredMm <= 0) return null
            val pct = measuredMm / nominalMm * 100
            val verdict = when {
                abs(pct - 100) <= TOLERANCE_PERCENT -> ScaleVerdict.ACCURATE
                pct < 100 -> ScaleVerdict.PRINTED_SMALL
                else -> ScaleVerdict.PRINTED_LARGE
            }
            return ScaleDiagnosis(nominalMm, measuredMm, pct, measuredMm - nominalMm, verdict)
        }
    }
}
