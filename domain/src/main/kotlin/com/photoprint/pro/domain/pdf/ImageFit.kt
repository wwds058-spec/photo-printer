package com.photoprint.pro.domain.pdf

import com.photoprint.pro.domain.measurement.Measurement
import com.photoprint.pro.domain.measurement.Rect
import com.photoprint.pro.domain.model.CropState
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin

/**
 * 2-D affine transform. `X = a·x + c·y + e`, `Y = b·x + d·y + f` (PDF's [a b c d e f] order).
 * Used here in a y-DOWN, top-left-origin space; [PdfSheetWriter] flips to PDF's y-up at the end.
 */
data class Affine(
    val a: Double = 1.0,
    val b: Double = 0.0,
    val c: Double = 0.0,
    val d: Double = 1.0,
    val e: Double = 0.0,
    val f: Double = 0.0,
) {
    /** Apply this transform first, then [o]. */
    fun then(o: Affine) = Affine(
        a = o.a * a + o.c * b,
        b = o.b * a + o.d * b,
        c = o.a * c + o.c * d,
        d = o.b * c + o.d * d,
        e = o.a * e + o.c * f + o.e,
        f = o.b * e + o.d * f + o.f,
    )

    fun apply(x: Double, y: Double) = (a * x + c * y + e) to (b * x + d * y + f)

    companion object {
        fun translate(dx: Double, dy: Double) = Affine(e = dx, f = dy)

        fun scale(sx: Double, sy: Double) = Affine(a = sx, d = sy)

        /** Clockwise as seen on screen/paper (y-down). Exact for multiples of 90°. */
        fun rotateClockwise(degrees: Int): Affine {
            val q = ((degrees % 360) + 360) % 360
            val (cs, sn) = when (q) {
                0 -> 1.0 to 0.0
                90 -> 0.0 to 1.0
                180 -> -1.0 to 0.0
                270 -> 0.0 to -1.0
                else -> cos(q * PI / 180) to sin(q * PI / 180)
            }
            return Affine(a = cs, b = sn, c = -sn, d = cs)
        }
    }
}

/** Where and how one image is drawn inside its frame. [matrix] maps the unit image square (u,v ∈ 0..1, v down) to the page. */
data class ImageDrawing(
    val matrix: Affine,
    val clip: Rect,
    /** Source pixels per inch of paper at this crop/zoom. */
    val effectiveDpi: Double,
)

enum class ImageQuality { GOOD, ACCEPTABLE, LOW }

/** Largest |pan| (as a fraction of the frame's width/height) that still lets the image cover the frame. */
data class PanLimits(val maxX: Double, val maxY: Double)

/**
 * Crop/zoom/pan/rotate maths. The frame is fixed; only the image moves inside it. The image
 * always covers the frame (zoom < 1 and over-panning are clamped) so no blank edges are printed.
 */
object ImageFit {
    const val GOOD_DPI = 300.0
    const val ACCEPTABLE_DPI = 200.0

    private fun quarterTurns(deg: Int): Int {
        val q = ((deg % 360) + 360) % 360
        require(q % 90 == 0) { "Only 90° image rotations are supported, got $deg" }
        return q
    }

    private fun coverScale(frameW: Double, frameH: Double, imgW: Int, imgH: Int, quarter: Int, zoom: Double): Double {
        val swap = quarter == 90 || quarter == 270
        val iw = (if (swap) imgH else imgW).toDouble()
        val ih = (if (swap) imgW else imgH).toDouble()
        return max(frameW / iw, frameH / ih) * zoom.coerceAtLeast(1.0)
    }

    /**
     * Pan limits for an unrotated photo frame of any unit ([frameW] × [frameH]); only the ratio matters.
     * The editor and [draw] share this, so what the user can drag is exactly what gets printed.
     */
    fun panLimits(frameW: Double, frameH: Double, imgW: Int, imgH: Int, crop: CropState): PanLimits {
        val quarter = quarterTurns(crop.rotationDegrees)
        val s = coverScale(frameW, frameH, imgW, imgH, quarter, crop.zoom)
        val swap = quarter == 90 || quarter == 270
        val scaledW = (if (swap) imgH else imgW) * s
        val scaledH = (if (swap) imgW else imgH) * s
        return PanLimits(maxOf(0.0, (scaledW - frameW) / (2 * frameW)), maxOf(0.0, (scaledH - frameH) / (2 * frameH)))
    }

    /**
     * Effective print resolution for an image in an unrotated photo frame of the given physical
     * size. Honest and unit-agnostic: no upscaling is hidden by the writer.
     */
    fun effectiveDpi(imgW: Int, imgH: Int, crop: CropState, photoWidthMm: Double, photoHeightMm: Double): Double {
        val s = coverScale(photoWidthMm, photoHeightMm, imgW, imgH, quarterTurns(crop.rotationDegrees), crop.zoom)
        return Measurement.MM_PER_INCH / s // s = mm per source pixel
    }

    fun quality(dpi: Double): ImageQuality = when {
        dpi >= GOOD_DPI -> ImageQuality.GOOD
        dpi >= ACCEPTABLE_DPI -> ImageQuality.ACCEPTABLE
        else -> ImageQuality.LOW
    }

    /**
     * @param frame the frame on the page, in points, top-left origin (as laid out on paper)
     * @param placementRotation 0, or 90 when the engine turned the photo to fit more per sheet
     */
    fun draw(frame: Rect, placementRotation: Int, crop: CropState, imgW: Int, imgH: Int): ImageDrawing {
        require(imgW > 0 && imgH > 0) { "Image has no pixels" }
        require(placementRotation == 0 || placementRotation == 90) { "Placement rotation must be 0 or 90" }
        val quarter = quarterTurns(crop.rotationDegrees)
        val turned = placementRotation == 90

        // Frame as the photo sees it, before the sheet-level turn.
        val fw = if (turned) frame.height else frame.width
        val fh = if (turned) frame.width else frame.height

        val s = coverScale(fw, fh, imgW, imgH, quarter, crop.zoom)
        val limits = panLimits(fw, fh, imgW, imgH, crop)
        val panX = crop.panX.coerceIn(-limits.maxX, limits.maxX) * fw
        val panY = crop.panY.coerceIn(-limits.maxY, limits.maxY) * fh

        var m = Affine.scale(imgW.toDouble(), imgH.toDouble()) // unit → source pixels
            .then(Affine.translate(-imgW / 2.0, -imgH / 2.0))
            .then(Affine.rotateClockwise(quarter))
            .then(Affine.scale(s, s))
            .then(Affine.translate(fw / 2 + panX, fh / 2 + panY)) // frame-local, unturned
        m = if (turned) {
            m.then(Affine.translate(-fw / 2, -fh / 2))
                .then(Affine.rotateClockwise(90))
                .then(Affine.translate(frame.left + frame.width / 2, frame.top + frame.height / 2))
        } else {
            m.then(Affine.translate(frame.left, frame.top))
        }
        return ImageDrawing(m, frame, Measurement.POINTS_PER_INCH / s)
    }
}
