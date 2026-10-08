package com.photoprint.pro.domain.pdf

import com.photoprint.pro.domain.layout.FillStrategy
import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.layout.LayoutOutcome
import com.photoprint.pro.domain.layout.LayoutPlan
import com.photoprint.pro.domain.layout.LayoutRequest
import com.photoprint.pro.domain.measurement.Measurement
import com.photoprint.pro.domain.model.Margin
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.model.Spacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * The core promise: preview coordinate = PDF coordinate = print coordinate. All three are derived
 * from the same SheetLayout, so converting each back to millimetres must return the identical value.
 */
class SheetGeometryConsistencyTest {
    private val engine = LayoutEngine()

    private fun plan(paper: com.photoprint.pro.domain.model.PaperSize) = assertIs<LayoutOutcome.Success<LayoutPlan>>(
        engine.calculate(
            LayoutRequest(
                paper = paper,
                photo = PhotoSizePresets.Passport,
                fill = FillStrategy.Copies.of("p", 20),
                margin = Margin.uniform(3.0),
                spacing = Spacing.uniform(2.0),
                showCutLines = true,
            ),
        ),
    ).value

    @Test
    fun `preview pdf and print coordinates agree in millimetres`() {
        for (paper in PaperSizePresets.all) {
            val plan = plan(paper)
            for (sheet in plan.sheets) {
                val pdf = sheet.toPdfPageSpec()
                val preview = PreviewTransform(3.7) // arbitrary view scale, e.g. after pinch-zoom
                val media = sheet.toPrintMediaSpec()

                assertEquals(sheet.placements.size, pdf.frames.size)
                assertEquals(sheet.cutLines.size, pdf.cutLines.size)

                // Page = paper.
                assertEquals(paper.widthMm, Measurement.pointsToMm(pdf.pageWidthPt), 1e-9)
                assertEquals(paper.heightMm, Measurement.pointsToMm(pdf.pageHeightPt), 1e-9)
                assertEquals(paper.widthMm, Measurement.milsToMm(media.widthMils.toDouble()), 0.0127) // ≤ 0.5 mil
                assertEquals(paper.heightMm, Measurement.milsToMm(media.heightMils.toDouble()), 0.0127)

                sheet.placements.forEachIndexed { i, p ->
                    val inPdf = pdf.frames[i]
                    val inPreview = preview.rect(p.rectMm)
                    assertEquals(p.photoId, inPdf.photoId)
                    assertEquals(p.rotation, inPdf.rotationDegrees)

                    assertEquals(p.xMm, Measurement.pointsToMm(inPdf.rectPt.left), 1e-9)
                    assertEquals(p.yMm, Measurement.pointsToMm(inPdf.rectPt.top), 1e-9)
                    assertEquals(p.widthMm, Measurement.pointsToMm(inPdf.rectPt.width), 1e-9)
                    assertEquals(p.heightMm, Measurement.pointsToMm(inPdf.rectPt.height), 1e-9)

                    assertEquals(p.xMm, preview.toMm(inPreview.left), 1e-9)
                    assertEquals(p.yMm, preview.toMm(inPreview.top), 1e-9)
                    assertEquals(p.widthMm, preview.toMm(inPreview.width), 1e-9)
                    assertEquals(p.heightMm, preview.toMm(inPreview.height), 1e-9)
                }
                sheet.cutLines.forEachIndexed { i, l ->
                    val pl = pdf.cutLines[i]
                    assertEquals(l.x1Mm, Measurement.pointsToMm(pl.x1Pt), 1e-9)
                    assertEquals(l.y2Mm, Measurement.pointsToMm(pl.y2Pt), 1e-9)
                }
            }
        }
    }

    @Test
    fun `view zoom never changes layout millimetres`() {
        val placement = plan(PaperSizePresets.Photo4x6).sheets[0].placements[0]
        val before = placement.rectMm
        for (scale in listOf(0.5, 2.0, 8.0)) {
            val t = PreviewTransform(scale)
            assertEquals(placement.widthMm * scale, t.rect(before).width, 1e-9)
            assertEquals(placement.widthMm, t.toMm(t.rect(before).width), 1e-9)
        }
        assertEquals(before, placement.rectMm, "zooming the view must not mutate the layout")
    }

    @Test
    fun `fit transform fits the whole sheet in the viewport`() {
        val sheet = plan(PaperSizePresets.Photo4x6).sheets[0]
        val t = PreviewTransform.fit(sheet, 1080.0, 1500.0)
        assertTrue(sheet.paperWidthMm * t.pxPerMm <= 1080.0 + 1e-9)
        assertTrue(sheet.paperHeightMm * t.pxPerMm <= 1500.0 + 1e-9)
    }

    @Test
    fun `4x6 media and pdf page sizes`() {
        val sheet = plan(PaperSizePresets.Photo4x6).sheets[0]
        val media = sheet.toPrintMediaSpec()
        assertEquals(4016, media.widthMils) // 102 mm
        assertEquals(5984, media.heightMils) // 152 mm
        val pdf = sheet.toPdfPageSpec()
        assertEquals(289, pdf.pageWidthPtRounded)
        assertEquals(431, pdf.pageHeightPtRounded)
        // Whole-point page boxes are the one place physical size is quantised; bound the error.
        assertTrue(pdf.pageWidthRoundingErrorMm <= 0.1764 + 1e-9)
        assertTrue(pdf.pageHeightRoundingErrorMm <= 0.1764 + 1e-9)
    }
}
