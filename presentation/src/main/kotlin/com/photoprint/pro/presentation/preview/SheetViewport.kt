package com.photoprint.pro.presentation.preview

import kotlin.math.max
import kotlin.math.min

/**
 * On-screen view of the sheet. [scale] is pixels per millimetre of paper and [offsetX]/[offsetY] is the
 * sheet's top-left in view pixels. This is ONLY the viewing transform: it never touches the layout in mm.
 */
data class SheetViewport(val scale: Double, val offsetX: Double, val offsetY: Double)

object SheetViewportMath {
    /** Zoom steps offered by the [−]/[+] buttons, as percent of true physical size. */
    val zoomSteps = listOf(25, 50, 75, 100, 150, 200, 300, 400, 600)
    const val MAX_PERCENT = 800.0
    private const val MIN_VISIBLE_FRACTION = 0.25

    /** The whole sheet centred in the view with [paddingPx] around it. */
    fun fit(sheetWMm: Double, sheetHMm: Double, viewW: Double, viewH: Double, paddingPx: Double = 16.0): SheetViewport {
        val availW = max(1.0, viewW - 2 * paddingPx)
        val availH = max(1.0, viewH - 2 * paddingPx)
        val scale = min(availW / sheetWMm, availH / sheetHMm)
        return SheetViewport(scale, (viewW - sheetWMm * scale) / 2, (viewH - sheetHMm * scale) / 2)
    }

    /**
     * 100 % means 1 mm of paper is 1 mm on the screen (given the device's [actualPxPerMm]), i.e. true size.
     */
    fun percent(vp: SheetViewport, actualPxPerMm: Double): Double = vp.scale / actualPxPerMm * 100

    fun scaleForPercent(percent: Double, actualPxPerMm: Double): Double = percent / 100 * actualPxPerMm

    /** Zoom to [newScale] keeping the sheet point under ([focalX], [focalY]) fixed. */
    fun zoomTo(
        vp: SheetViewport,
        newScale: Double,
        focalX: Double,
        focalY: Double,
        sheetWMm: Double,
        sheetHMm: Double,
        viewW: Double,
        viewH: Double,
        actualPxPerMm: Double,
        fitScale: Double,
    ): SheetViewport {
        val lo = min(fitScale, scaleForPercent(zoomSteps.first().toDouble(), actualPxPerMm)) * 0.9
        val hi = scaleForPercent(MAX_PERCENT, actualPxPerMm)
        val s = newScale.coerceIn(lo, hi)
        val k = s / vp.scale
        val moved = SheetViewport(s, focalX - (focalX - vp.offsetX) * k, focalY - (focalY - vp.offsetY) * k)
        return clamp(moved, sheetWMm, sheetHMm, viewW, viewH)
    }

    fun panBy(vp: SheetViewport, dx: Double, dy: Double, sheetWMm: Double, sheetHMm: Double, viewW: Double, viewH: Double) =
        clamp(vp.copy(offsetX = vp.offsetX + dx, offsetY = vp.offsetY + dy), sheetWMm, sheetHMm, viewW, viewH)

    /** The next step above/below the current zoom (by percent), for the [+]/[−] buttons. */
    fun stepScale(vp: SheetViewport, up: Boolean, actualPxPerMm: Double): Double {
        val pct = percent(vp, actualPxPerMm)
        val next = if (up) zoomSteps.firstOrNull { it > pct + 0.5 } ?: zoomSteps.last()
        else zoomSteps.lastOrNull { it < pct - 0.5 } ?: zoomSteps.first()
        return scaleForPercent(next.toDouble(), actualPxPerMm)
    }

    /**
     * If the sheet is smaller than the view along an axis it is centred; otherwise it can be dragged but at
     * least a quarter of the view must stay covered, so it can never be lost off-screen.
     */
    fun clamp(vp: SheetViewport, sheetWMm: Double, sheetHMm: Double, viewW: Double, viewH: Double): SheetViewport =
        vp.copy(
            offsetX = clampAxis(vp.offsetX, sheetWMm * vp.scale, viewW),
            offsetY = clampAxis(vp.offsetY, sheetHMm * vp.scale, viewH),
        )

    private fun clampAxis(offset: Double, size: Double, view: Double): Double {
        if (size <= view) return (view - size) / 2
        val keep = view * MIN_VISIBLE_FRACTION
        return offset.coerceIn(keep - size, view - keep)
    }
}
