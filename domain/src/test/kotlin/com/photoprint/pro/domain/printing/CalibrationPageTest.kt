package com.photoprint.pro.domain.printing

import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.pdf.PdfSheetWriter
import org.apache.pdfbox.Loader
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import org.apache.pdfbox.text.PDFTextStripper
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Renders the calibration page at 254 dpi (10 px = 1 mm) and measures the printed rulers. */
class CalibrationPageTest {
    private fun page(paper: PaperSize, photo: PhotoSize) =
        assertIs<CalibrationOutcome.Page>(CalibrationPage.build(paper, photo))

    private fun pdf(p: CalibrationOutcome.Page): ByteArray =
        ByteArrayOutputStream().also { PdfSheetWriter().write(listOf(p.spec), { null }, it) }.toByteArray()

    private fun render(bytes: ByteArray): BufferedImage =
        Loader.loadPDF(bytes).use { PDFRenderer(it).renderImageWithDPI(0, 254f, ImageType.RGB) }

    private fun dark(img: BufferedImage, x: Int, y: Int) = Color(img.getRGB(x, y)).red < 140

    /** Extent in px of dark pixels along a row band, between xFrom and xTo. */
    private fun spanX(img: BufferedImage, yPx: Int, xFrom: Int, xTo: Int): Int {
        val xs = (xFrom..xTo).filter { x -> (yPx - 1..yPx + 1).any { dark(img, x, it) } }
        return xs.last() - xs.first() + 1
    }

    private fun spanY(img: BufferedImage, xPx: Int, yFrom: Int, yTo: Int): Int {
        val ys = (yFrom..yTo).filter { y -> (xPx - 1..xPx + 1).any { dark(img, it, y) } }
        return ys.last() - ys.first() + 1
    }

    // A ruler must be one unbroken line, not just two end ticks. The renderer snaps strokes to pixel
    // centres (up to half a pixel = 0.05 mm), so the outermost 2 px at each end are not checked.
    private fun unbrokenX(img: BufferedImage, yPx: Int, xFrom: Int, xTo: Int): Boolean =
        (xFrom + 2..xTo - 2).all { x -> (yPx - 1..yPx + 1).any { dark(img, x, it) } }

    private fun unbrokenY(img: BufferedImage, xPx: Int, yFrom: Int, yTo: Int): Boolean =
        (yFrom + 2..yTo - 2).all { y -> (xPx - 1..xPx + 1).any { dark(img, it, y) } }

    @Test
    fun `portrait 4x6 rulers measure 100 and 50 mm and the frame its true size`() {
        val p = page(PaperSizePresets.Photo4x6, PhotoSizePresets.Passport)
        assertTrue(p.includesPhotoFrame)
        assertEquals(listOf(100.0, 50.0), p.referenceLengthsMm)
        val img = render(pdf(p))
        // 100 mm vertical ruler at x = 92 mm, from y = 34 mm.
        assertEquals(1000.0, spanY(img, 920, 300, 1400).toDouble(), 3.0)
        // 50 mm horizontal ruler at y = 34 mm, from x = 10 mm (stop before the vertical ruler).
        assertEquals(500.0, spanX(img, 340, 90, 800).toDouble(), 3.0)
        // And they are continuous lines (34 mm → 134 mm and 10 mm → 60 mm), not just end ticks.
        assertTrue(unbrokenY(img, 920, 340, 1340), "100 mm ruler has a gap")
        assertTrue(unbrokenX(img, 340, 100, 600), "50 mm ruler has a gap")
        // Passport frame outline at (10, 48) mm: 35 × 45 mm.
        assertEquals(350.0, spanX(img, 480, 90, 800).toDouble(), 3.0)
        assertEquals(450.0, spanY(img, 275, 470, 945).toDouble(), 3.0)
    }

    @Test
    fun `landscape paper puts the 100 mm ruler along the long side`() {
        val landscape = PaperSize("4x6 landscape", 152.0, 102.0)
        val p = page(landscape, PhotoSizePresets.IdPhoto)
        assertTrue(p.includesPhotoFrame)
        val img = render(pdf(p))
        assertEquals(1000.0, spanX(img, 340, 90, 1300).toDouble(), 3.0) // horizontal 100 mm at y = 34 mm
        assertEquals(500.0, spanY(img, 1420, 300, 900).toDouble(), 3.0) // vertical 50 mm at x = 142 mm
        assertTrue(unbrokenX(img, 340, 100, 1100), "100 mm ruler has a gap")
        assertTrue(unbrokenY(img, 1420, 340, 840), "50 mm ruler has a gap")
    }

    @Test
    fun `photo frame is omitted rather than scaled when it does not fit`() {
        // A passport frame cannot sit beside the rulers on 4×6 landscape; it must not be shrunk.
        val p = page(PaperSize("4x6 landscape", 152.0, 102.0), PhotoSizePresets.Passport)
        assertFalse(p.includesPhotoFrame)
        assertTrue(p.spec.overlays.none { it is com.photoprint.pro.domain.pdf.PdfStrokeRect })
    }

    @Test
    fun `a frame too wide to sit beside the ruler is omitted too`() {
        // 80 mm wide would run into the 100 mm ruler at x = 92 mm on 4×6 portrait.
        val p = page(PaperSizePresets.Photo4x6, PhotoSize("wide", 80.0, 30.0))
        assertFalse(p.includesPhotoFrame)
        // A narrower one of the same height does fit.
        assertTrue(page(PaperSizePresets.Photo4x6, PhotoSize("ok", 70.0, 30.0)).includesPhotoFrame)
    }

    @Test
    fun `page is the exact paper size and works on A4`() {
        val p = page(PaperSizePresets.A4, PhotoSizePresets.TwoByTwoInch)
        assertEquals(210.0, p.spec.pageWidthPt / 72 * 25.4, 1e-9)
        assertEquals(297.0, p.spec.pageHeightPt / 72 * 25.4, 1e-9)
        assertTrue(p.includesPhotoFrame)
    }

    @Test
    fun `labels name the size and tell the user to print at actual size`() {
        val bytes = pdf(page(PaperSizePresets.Photo4x6, PhotoSizePresets.Passport))
        val text = Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }
        assertTrue("100 mm" in text, text)
        assertTrue("50 mm" in text, text)
        assertTrue("35 × 45 mm" in text, text)
        assertTrue("ACTUAL SIZE" in text, text)
        assertTrue("102 × 152 mm" in text, text)
    }

    @Test
    fun `paper too small for a 100 mm ruler is reported`() {
        val e = assertIs<CalibrationOutcome.PaperTooSmall>(CalibrationPage.build(PaperSize("small", 80.0, 120.0), PhotoSizePresets.Passport))
        assertTrue(e.minLongSideMm > 120.0)
    }

    @Test
    fun `text with PDF delimiters and non-latin characters is escaped`() {
        val spec = com.photoprint.pro.domain.pdf.PdfPageSpec(
            200.0, 200.0, emptyList(), emptyList(),
            listOf(com.photoprint.pro.domain.pdf.PdfText("a (b) \\ c 中", 10.0, 100.0)),
        )
        val bytes = ByteArrayOutputStream().also { PdfSheetWriter().write(listOf(spec), { null }, it) }.toByteArray()
        val text = Loader.loadPDF(bytes).use { PDFTextStripper().getText(it) }
        assertTrue("a (b) \\ c ?" in text, text)
    }

    // ---- scale diagnosis -----------------------------------------------------------------

    @Test
    fun `scale diagnosis classifies measured lengths`() {
        val exact = assertNotNull(ScaleDiagnosis.fromMeasured(100.0, 100.0))
        assertEquals(ScaleVerdict.ACCURATE, exact.verdict)
        assertEquals(ScaleVerdict.ACCURATE, ScaleDiagnosis.fromMeasured(100.0, 100.4)!!.verdict)
        val small = assertNotNull(ScaleDiagnosis.fromMeasured(100.0, 94.0))
        assertEquals(ScaleVerdict.PRINTED_SMALL, small.verdict)
        assertEquals(94.0, small.scalePercent, 1e-9)
        assertEquals(-6.0, small.errorMm, 1e-9)
        val large = assertNotNull(ScaleDiagnosis.fromMeasured(50.0, 51.0))
        assertEquals(ScaleVerdict.PRINTED_LARGE, large.verdict)
        assertEquals(102.0, large.scalePercent, 1e-9)
    }

    @Test
    fun `scale diagnosis rejects nonsense input`() {
        assertNull(ScaleDiagnosis.fromMeasured(100.0, 0.0))
        assertNull(ScaleDiagnosis.fromMeasured(0.0, 100.0))
        assertNull(ScaleDiagnosis.fromMeasured(100.0, Double.NaN))
        assertNull(ScaleDiagnosis.fromMeasured(100.0, -5.0))
    }
}
