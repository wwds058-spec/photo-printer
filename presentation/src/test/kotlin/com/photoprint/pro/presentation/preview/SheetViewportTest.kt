package com.photoprint.pro.presentation.preview

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class SheetViewportTest {
    private val w = 102.0
    private val h = 152.0
    private val viewW = 1080.0
    private val viewH = 1500.0
    private val actual = 6.3 // px per mm at ~160 dpi

    private val fit = SheetViewportMath.fit(w, h, viewW, viewH, paddingPx = 20.0)

    private fun zoom(vp: SheetViewport, scale: Double, fx: Double = viewW / 2, fy: Double = viewH / 2) =
        SheetViewportMath.zoomTo(vp, scale, fx, fy, w, h, viewW, viewH, actual, fit.scale)

    @Test
    fun `fit centres the whole sheet inside the padding`() {
        assertTrue(w * fit.scale <= viewW - 40 + 1e-9)
        assertTrue(h * fit.scale <= viewH - 40 + 1e-9)
        assertEquals((viewW - w * fit.scale) / 2, fit.offsetX, 1e-9)
        assertEquals((viewH - h * fit.scale) / 2, fit.offsetY, 1e-9)
        // Height is the limiting side here.
        assertEquals((viewH - 40) / h, fit.scale, 1e-9)
    }

    @Test
    fun `100 percent is true physical size`() {
        val real = SheetViewport(actual, 0.0, 0.0)
        assertEquals(100.0, SheetViewportMath.percent(real, actual), 1e-9)
        assertEquals(actual, SheetViewportMath.scaleForPercent(100.0, actual), 1e-12)
        assertEquals(200.0, SheetViewportMath.percent(real.copy(scale = actual * 2), actual), 1e-9)
    }

    @Test
    fun `zoom keeps the point under the fingers fixed`() {
        val start = zoom(fit, actual) // 100 %
        val fx = 300.0
        val fy = 700.0
        val mmX = (fx - start.offsetX) / start.scale
        val mmY = (fy - start.offsetY) / start.scale
        val more = zoom(start, actual * 2, fx, fy)
        assertEquals(fx, more.offsetX + mmX * more.scale, 1e-6)
        assertEquals(fy, more.offsetY + mmY * more.scale, 1e-6)
    }

    @Test
    fun `zoom is bounded at both ends`() {
        val max = SheetViewportMath.scaleForPercent(SheetViewportMath.MAX_PERCENT, actual)
        assertEquals(max, zoom(fit, 1e9).scale, 1e-9)
        // The lower bound is a little below the smaller of "fit" and the smallest step, and always positive.
        val min = zoom(fit, 1e-9).scale
        assertTrue(min > 0)
        assertEquals(minOf(fit.scale, SheetViewportMath.scaleForPercent(25.0, actual)) * 0.9, min, 1e-9)
    }

    @Test
    fun `a sheet smaller than the view stays centred and cannot be panned away`() {
        val moved = SheetViewportMath.panBy(fit, 500.0, -500.0, w, h, viewW, viewH)
        assertEquals(fit.offsetX, moved.offsetX, 1e-9)
        assertEquals(fit.offsetY, moved.offsetY, 1e-9)
    }

    @Test
    fun `a zoomed sheet can be dragged but never lost`() {
        val big = zoom(fit, actual * 4) // 400 %: sheet is far larger than the view
        val farLeft = SheetViewportMath.panBy(big, -1e6, 0.0, w, h, viewW, viewH)
        val farRight = SheetViewportMath.panBy(big, 1e6, 0.0, w, h, viewW, viewH)
        val size = w * big.scale
        assertEquals(viewW * 0.25 - size, farLeft.offsetX, 1e-9, "a quarter of the view stays covered")
        assertEquals(viewW * 0.75, farRight.offsetX, 1e-9)
        // Inside the range, panning is 1:1.
        val small = SheetViewportMath.panBy(big, 10.0, 0.0, w, h, viewW, viewH)
        assertEquals(big.offsetX + 10.0, small.offsetX, 1e-9)
    }

    @Test
    fun `plus and minus move through the percent steps`() {
        val at100 = SheetViewport(actual, 0.0, 0.0)
        assertEquals(150.0, SheetViewportMath.percent(at100.copy(scale = SheetViewportMath.stepScale(at100, true, actual)), actual), 1e-9)
        assertEquals(75.0, SheetViewportMath.percent(at100.copy(scale = SheetViewportMath.stepScale(at100, false, actual)), actual), 1e-9)
        // From fit (an in-between value) the step goes to the next preset, not to a fixed neighbour.
        val mid = at100.copy(scale = actual * 1.2)
        assertEquals(150.0, SheetViewportMath.percent(mid.copy(scale = SheetViewportMath.stepScale(mid, true, actual)), actual), 1e-9)
        assertEquals(100.0, SheetViewportMath.percent(mid.copy(scale = SheetViewportMath.stepScale(mid, false, actual)), actual), 1e-9)
        // Ends of the range stay put.
        val top = at100.copy(scale = SheetViewportMath.scaleForPercent(600.0, actual))
        assertEquals(600.0, SheetViewportMath.percent(top.copy(scale = SheetViewportMath.stepScale(top, true, actual)), actual), 1e-9)
        val bottom = at100.copy(scale = SheetViewportMath.scaleForPercent(25.0, actual))
        assertEquals(25.0, SheetViewportMath.percent(bottom.copy(scale = SheetViewportMath.stepScale(bottom, false, actual)), actual), 1e-9)
    }
}
