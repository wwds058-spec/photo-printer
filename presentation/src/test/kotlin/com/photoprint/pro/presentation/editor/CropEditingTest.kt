package com.photoprint.pro.presentation.editor

import com.photoprint.pro.domain.measurement.Rect
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.pdf.ImageFit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class CropEditingTest {
    // 4:5 photo from a 4:3 landscape image: wide image, so horizontal pan has room even at zoom 1.
    private val imgW = 1600
    private val imgH = 1200
    private val frame = CropEditing.Frame(35.0, 45.0)
    private val frameRect = Rect(0.0, 0.0, 35.0, 45.0)

    /** Where an image point (u,v in 0..1) lands inside the 35×45 frame, using the same maths as the PDF. */
    private fun landing(crop: CropState, u: Double, v: Double): Pair<Double, Double> =
        ImageFit.draw(frameRect, 0, crop, imgW, imgH).matrix.apply(u, v)

    @Test
    fun `pan is clamped by the same limits the PDF uses`() {
        val far = CropEditing.panBy(CropState(), 5.0, -5.0, frame, imgW, imgH)
        val limits = ImageFit.panLimits(35.0, 45.0, imgW, imgH, CropState())
        assertEquals(limits.maxX, far.panX, 1e-12)
        assertEquals(-limits.maxY, far.panY, 1e-12)
        // At zoom 1 a landscape image in a portrait frame can move sideways but not vertically.
        assertTrue(limits.maxX > 0.0)
        assertEquals(0.0, limits.maxY, 1e-12)
        // And the edited value lands exactly where draw() would put it (no further clamping happens at print time).
        val before = ImageFit.draw(frameRect, 0, far, imgW, imgH).matrix
        val afterRaw = ImageFit.draw(frameRect, 0, far.copy(panX = far.panX + 3.0), imgW, imgH).matrix
        assertEquals(before, afterRaw, "pan beyond the limit changes nothing")
    }

    @Test
    fun `zooming about a point keeps the image point under the fingers fixed`() {
        val start = CropState(zoom = 2.0, panX = 0.1, panY = 0.05)
        // Focal point: a quarter of the frame right of and above centre.
        val fx = 0.25
        val fy = -0.25
        val zoomed = CropEditing.zoomBy(start, 1.5, fx, fy, frame, imgW, imgH)
        assertEquals(3.0, zoomed.zoom, 1e-12)

        // Find the image point that was under the focal point before, then check it is still there.
        val fpx = frameRect.width * (0.5 + fx)
        val fpy = frameRect.height * (0.5 + fy)
        val m0 = ImageFit.draw(frameRect, 0, start, imgW, imgH).matrix
        // Invert the affine by solving for (u,v).
        val det = m0.a * m0.d - m0.b * m0.c
        val u = (m0.d * (fpx - m0.e) - m0.c * (fpy - m0.f)) / det
        val v = (-m0.b * (fpx - m0.e) + m0.a * (fpy - m0.f)) / det
        val (x1, y1) = landing(zoomed, u, v)
        assertEquals(fpx, x1, 1e-9)
        assertEquals(fpy, y1, 1e-9)
    }

    @Test
    fun `zoom is limited and re-clamps pan`() {
        val z = CropEditing.zoomBy(CropState(), 100.0, 0.0, 0.0, frame, imgW, imgH)
        assertEquals(CropEditing.MAX_ZOOM, z.zoom, 1e-12)
        val out = CropEditing.zoomBy(CropState(zoom = 2.0, panX = 0.4), 0.01, 0.0, 0.0, frame, imgW, imgH)
        assertEquals(CropEditing.MIN_ZOOM, out.zoom, 1e-12)
        val limits = ImageFit.panLimits(35.0, 45.0, imgW, imgH, out)
        assertTrue(kotlin.math.abs(out.panX) <= limits.maxX + 1e-12, "zooming out pulls the image back to cover the frame")
    }

    @Test
    fun `nonsense zoom factors are ignored`() {
        val c = CropState(zoom = 2.0)
        assertEquals(c, CropEditing.zoomBy(c, Double.NaN, 0.0, 0.0, frame, imgW, imgH))
        assertEquals(c, CropEditing.zoomBy(c, 0.0, 0.0, 0.0, frame, imgW, imgH))
        assertEquals(c, CropEditing.zoomBy(c, -2.0, 0.0, 0.0, frame, imgW, imgH))
    }

    @Test
    fun `rotation advances in quarter turns and re-centres`() {
        var c = CropState(zoom = 2.0, panX = 0.2, panY = 0.1)
        c = CropEditing.rotateClockwise(c, frame, imgW, imgH)
        assertEquals(90, c.rotationDegrees)
        assertEquals(0.0, c.panX, 1e-12)
        assertEquals(0.0, c.panY, 1e-12)
        repeat(3) { c = CropEditing.rotateClockwise(c, frame, imgW, imgH) }
        assertEquals(0, c.rotationDegrees, "four turns is back to upright")
    }

    @Test
    fun `reset returns the default crop`() {
        assertTrue(CropEditing.isDefault(CropEditing.reset()))
        assertTrue(!CropEditing.isDefault(CropState(zoom = 1.2)))
    }

    @Test
    fun `brightness and contrast are limited to the slider range`() {
        assertEquals(1.0, CropEditing.setBrightness(CropState(), 5.0).brightness)
        assertEquals(-1.0, CropEditing.setContrast(CropState(), -5.0).contrast)
    }

    @Test
    fun `clamp repairs any out of range crop`() {
        val wild = CropState(zoom = 99.0, panX = 50.0, panY = -50.0)
        val ok = CropEditing.clamp(wild, frame, imgW, imgH)
        assertEquals(CropEditing.MAX_ZOOM, ok.zoom, 1e-12)
        val l = ImageFit.panLimits(35.0, 45.0, imgW, imgH, ok)
        assertEquals(l.maxX, ok.panX, 1e-12)
        assertEquals(-l.maxY, ok.panY, 1e-12)
    }

    @Test
    fun `free aspect follows the image and presets give their ratio`() {
        val free = CropEditing.frameFor(AspectOption.Free, 800, 600)
        assertEquals(800.0 / 600.0, free.widthUnits / free.heightUnits, 1e-12)
        val p = CropEditing.frameFor(AspectOption.Passport, 800, 600)
        assertEquals(35.0 / 45.0, p.widthUnits / p.heightUnits, 1e-12)
        assertEquals(listOf("Free", "35:45", "1:1", "3:4", "4:5"), AspectOption.presets.map { it.label })
        assertEquals(null, AspectOption.custom(0.0, 5.0))
        assertEquals(null, AspectOption.custom(Double.NaN, 5.0))
        assertEquals(0.75, AspectOption.custom(3.0, 4.0)!!.value, 1e-12)
        assertEquals("35:45", AspectOption.of(35.0, 45.0).label)
    }

    @Test
    fun `colour matrix is identity at zero and scales around mid grey`() {
        assertTrue(ColorAdjust.isIdentity(0.0, 0.0))
        val id = ColorAdjust.matrix(0.0, 0.0)
        assertEquals(1f, id[0]); assertEquals(0f, id[4]); assertEquals(1f, id[18]); assertEquals(0f, id[19])
        val bright = ColorAdjust.matrix(0.5, 0.0)
        assertEquals(127.5f, bright[4], 1e-4f)
        val contrast = ColorAdjust.matrix(0.0, 1.0)
        assertEquals(2f, contrast[0])
        assertEquals(-128f, contrast[4], 1e-4f) // mid-grey (128) stays 128: 2·128 − 128
        val flat = ColorAdjust.matrix(0.0, -1.0)
        assertEquals(0f, flat[0])
        assertEquals(128f, flat[4], 1e-4f)
        assertNotEquals(id.toList(), bright.toList())
        assertEquals(ColorAdjust.matrix(1.0, 1.0).toList(), ColorAdjust.matrix(9.0, 9.0).toList(), "out-of-range input is clamped")
    }
}
