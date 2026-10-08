package com.photoprint.pro.domain.pdf

import com.photoprint.pro.domain.measurement.Measurement
import com.photoprint.pro.domain.measurement.Rect
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.CutLine
import com.photoprint.pro.domain.model.SheetLayout
import kotlin.math.abs
import kotlin.math.roundToInt

/*
 * Three consumers need the same sheet in three coordinate systems:
 *   - on-screen preview  → pixels  (PreviewTransform)
 *   - PDF page / canvas  → points  (PdfPageSpec)
 *   - Android print job  → mils    (PrintMediaSpec)
 *
 * All three are pure linear scalings of the SAME SheetLayout (millimetres, origin top-left), so
 * a frame can never be at a different physical position in preview, PDF and print.
 */

data class PdfFrame(
    val photoId: String,
    val rectPt: Rect,
    val rotationDegrees: Int,
    val cropState: CropState,
)

data class PdfLine(val x1Pt: Double, val y1Pt: Double, val x2Pt: Double, val y2Pt: Double)

/**
 * Everything a PDF page renderer needs, in points (1/72 in), origin top-left, y down — the
 * same convention as `android.graphics.pdf.PdfDocument`'s canvas.
 */
data class PdfPageSpec(
    val pageWidthPt: Double,
    val pageHeightPt: Double,
    val frames: List<PdfFrame>,
    val cutLines: List<PdfLine>,
) {
    /**
     * `PdfDocument.PageInfo.Builder` only accepts whole points, so the page box is rounded.
     * Frames are drawn with float precision and stay exact; the page size can differ from the
     * true paper size by up to half a point (~0.18 mm). The calibration page (phase 11) measures
     * the real effect on printers.
     */
    val pageWidthPtRounded: Int get() = pageWidthPt.roundToInt()
    val pageHeightPtRounded: Int get() = pageHeightPt.roundToInt()

    val pageWidthRoundingErrorMm: Double
        get() = abs(Measurement.pointsToMm(pageWidthPtRounded - pageWidthPt))
    val pageHeightRoundingErrorMm: Double
        get() = abs(Measurement.pointsToMm(pageHeightPtRounded - pageHeightPt))
}

fun SheetLayout.toPdfPageSpec(): PdfPageSpec {
    val k = Measurement.mmToPoints(1.0)
    return PdfPageSpec(
        pageWidthPt = paperWidthMm * k,
        pageHeightPt = paperHeightMm * k,
        frames = placements.map { PdfFrame(it.photoId, it.rectMm.scaled(k), it.rotation, it.cropState) },
        cutLines = cutLines.map { it.scaled(k) },
    )
}

private fun CutLine.scaled(k: Double) = PdfLine(x1Mm * k, y1Mm * k, x2Mm * k, y2Mm * k)

/** Media size to hand to `android.print.PrintAttributes.MediaSize` (mils = 1/1000 in). */
data class PrintMediaSpec(val widthMils: Int, val heightMils: Int)

fun SheetLayout.toPrintMediaSpec() = PrintMediaSpec(
    widthMils = Measurement.mmToMils(paperWidthMm).roundToInt(),
    heightMils = Measurement.mmToMils(paperHeightMm).roundToInt(),
)

/**
 * Maps the sheet to on-screen pixels. [pxPerMm] is only the VIEW scale (fit-to-screen, pinch
 * zoom); it never alters the layout in millimetres.
 */
class PreviewTransform(val pxPerMm: Double) {
    init {
        require(pxPerMm > 0 && pxPerMm.isFinite()) { "pxPerMm must be positive" }
    }

    fun rect(mmRect: Rect): Rect = mmRect.scaled(pxPerMm)

    fun line(l: CutLine) = PdfLine(l.x1Mm * pxPerMm, l.y1Mm * pxPerMm, l.x2Mm * pxPerMm, l.y2Mm * pxPerMm)

    fun toMm(px: Double): Double = px / pxPerMm

    /** Scale that fits the whole sheet inside a viewport of the given pixel size. */
    companion object {
        fun fit(sheet: SheetLayout, viewportWidthPx: Double, viewportHeightPx: Double) =
            PreviewTransform(minOf(viewportWidthPx / sheet.paperWidthMm, viewportHeightPx / sheet.paperHeightMm))
    }
}
