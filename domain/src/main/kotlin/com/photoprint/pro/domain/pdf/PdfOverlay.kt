package com.photoprint.pro.domain.pdf

import com.photoprint.pro.domain.measurement.Rect

/**
 * Extra vector drawing on a page, in points, top-left origin (y down): used by the calibration
 * page. Photos and cut guides have their own typed fields on [PdfPageSpec].
 */
sealed interface PdfOverlay

data class PdfStrokeLine(
    val line: PdfLine,
    val widthPt: Double = 0.5,
    /** 0 = black, 1 = white. */
    val gray: Double = 0.0,
) : PdfOverlay

data class PdfStrokeRect(
    val rect: Rect,
    val widthPt: Double = 0.5,
    val gray: Double = 0.0,
) : PdfOverlay

/**
 * Left-aligned text in built-in Helvetica (no font embedding). Characters outside Latin-1 are
 * printed as `?`; `\u00D7` (×) is supported.
 */
data class PdfText(
    val text: String,
    val xPt: Double,
    val baselineYPt: Double,
    val sizePt: Double = 10.0,
    val gray: Double = 0.0,
) : PdfOverlay
