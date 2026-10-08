package com.photoprint.pro.domain.layout

import com.photoprint.pro.domain.layout.LayoutOutcome.Failure
import com.photoprint.pro.domain.layout.LayoutOutcome.Success
import com.photoprint.pro.domain.model.CutLine
import com.photoprint.pro.domain.model.PhotoPlacement
import com.photoprint.pro.domain.model.SheetLayout
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.round

/**
 * Computes physical photo layouts. Pure Kotlin: no Android, no Compose, no I/O.
 *
 * The result ([LayoutPlan]) is the single model consumed by the on-screen preview, the PDF
 * writer and the print job. Nothing downstream recomputes positions.
 *
 * The engine tests each allowed photo orientation, computes how many frames fit on a sheet
 * (`columns × rows`, honouring margins and spacing), and keeps the orientation that fits more.
 * On a tie the upright orientation wins. It never hard-codes counts for any paper/photo pair.
 *
 * Current limitation: one orientation per sheet. Mixed-orientation packing of the leftover strip
 * is a possible later optimisation.
 */
class LayoutEngine {

    /** Chooses the best grid for one sheet. Also used for the live "6 photos per sheet" estimate. */
    fun planGrid(request: LayoutRequest): LayoutOutcome<GridPlan> = planGrid(
        paperW = request.paper.widthMm,
        paperH = request.paper.heightMm,
        photoW = request.photo.widthMm,
        photoH = request.photo.heightMm,
        m = request.margin,
        s = request.spacing,
        allowRotation = request.allowRotation,
    )

    /** Full layout: grid + distribution of all requested photos across as many sheets as needed. */
    fun calculate(request: LayoutRequest): LayoutOutcome<LayoutPlan> {
        val grid = when (val g = planGrid(request)) {
            is Failure -> return g
            is Success -> g.value
        }
        val photoIds = when (val ids = resolvePhotoIds(request.fill, grid.photosPerSheet)) {
            is Failure -> return ids
            is Success -> ids.value
        }

        val perSheet = grid.photosPerSheet
        val sheetCount = ceil(photoIds.size.toDouble() / perSheet).toInt()
        val cutLines = if (request.showCutLines) cutLines(grid) else emptyList()
        val rotation = grid.orientation.rotationDegrees

        val sheets = (0 until sheetCount).map { sheetIndex ->
            val start = sheetIndex * perSheet
            val end = min(photoIds.size, start + perSheet)
            val placements = (start until end).map { i ->
                val slot = i - start
                val col = slot % grid.columns
                val row = slot / grid.columns
                PhotoPlacement(
                    photoId = photoIds[i],
                    xMm = roundMm(grid.originXMm + col * (grid.cellWidthMm + grid.spacingXMm)),
                    yMm = roundMm(grid.originYMm + row * (grid.cellHeightMm + grid.spacingYMm)),
                    widthMm = grid.cellWidthMm,
                    heightMm = grid.cellHeightMm,
                    rotation = rotation,
                    cropState = request.crops[photoIds[i]] ?: request.defaultCrop,
                )
            }
            SheetLayout(
                paperWidthMm = grid.paperWidthMm,
                paperHeightMm = grid.paperHeightMm,
                placements = placements,
                cutLines = cutLines,
                sheetIndex = sheetIndex,
                totalSheets = sheetCount,
            )
        }
        return Success(LayoutPlan(grid, photoIds.size, sheets))
    }

    // ---- grid ----------------------------------------------------------------------------

    private fun planGrid(
        paperW: Double,
        paperH: Double,
        photoW: Double,
        photoH: Double,
        m: com.photoprint.pro.domain.model.Margin,
        s: com.photoprint.pro.domain.model.Spacing,
        allowRotation: Boolean,
    ): LayoutOutcome<GridPlan> {
        if (!validDimension(paperW) || !validDimension(paperH)) return Failure(LayoutError.InvalidPaper(paperW, paperH))
        if (!validPhotoDimension(photoW) || !validPhotoDimension(photoH)) {
            return Failure(LayoutError.InvalidPhoto(photoW, photoH))
        }
        if (listOf(m.topMm, m.bottomMm, m.leftMm, m.rightMm).any { !it.isFinite() || it < 0 }) {
            return Failure(LayoutError.InvalidMargin)
        }
        if (listOf(s.horizontalMm, s.verticalMm).any { !it.isFinite() || it < 0 }) {
            return Failure(LayoutError.InvalidSpacing)
        }

        val usableW = paperW - m.leftMm - m.rightMm
        val usableH = paperH - m.topMm - m.bottomMm
        if (usableW <= EPS || usableH <= EPS) return Failure(LayoutError.MarginsConsumePaper(usableW, usableH))

        val upright = Candidate(PhotoOrientation.UPRIGHT, photoW, photoH, usableW, usableH, s.horizontalMm, s.verticalMm)
        val rotated = if (allowRotation) {
            Candidate(PhotoOrientation.ROTATED_90, photoH, photoW, usableW, usableH, s.horizontalMm, s.verticalMm)
        } else {
            null
        }
        val best = if (rotated != null && rotated.count > upright.count) rotated else upright
        if (best.count == 0) {
            return Failure(LayoutError.PhotoDoesNotFit(photoW, photoH, usableW, usableH, allowRotation))
        }

        val gridW = best.columns * best.cellW + (best.columns - 1) * s.horizontalMm
        val gridH = best.rows * best.cellH + (best.rows - 1) * s.verticalMm
        return Success(
            GridPlan(
                paperWidthMm = paperW,
                paperHeightMm = paperH,
                orientation = best.orientation,
                columns = best.columns,
                rows = best.rows,
                cellWidthMm = best.cellW,
                cellHeightMm = best.cellH,
                spacingXMm = s.horizontalMm,
                spacingYMm = s.verticalMm,
                originXMm = roundMm(m.leftMm + (usableW - gridW) / 2),
                originYMm = roundMm(m.topMm + (usableH - gridH) / 2),
                usableWidthMm = usableW,
                usableHeightMm = usableH,
            ),
        )
    }

    private class Candidate(
        val orientation: PhotoOrientation,
        val cellW: Double,
        val cellH: Double,
        usableW: Double,
        usableH: Double,
        gapX: Double,
        gapY: Double,
    ) {
        val columns = fit(usableW, cellW, gapX)
        val rows = fit(usableH, cellH, gapY)
        val count: Int get() = columns * rows
    }

    // ---- photo distribution --------------------------------------------------------------

    private fun resolvePhotoIds(fill: FillStrategy, perSheet: Int): LayoutOutcome<List<String>> = when (fill) {
        is FillStrategy.Copies -> {
            if (fill.items.isEmpty()) {
                Failure(LayoutError.NoPhotos)
            } else if (fill.items.any { it.copies < 0 }) {
                Failure(LayoutError.InvalidCopies)
            } else {
                val total = fill.items.sumOf { it.copies.toLong() }
                when {
                    total == 0L -> Failure(LayoutError.InvalidCopies)
                    total > MAX_PHOTOS -> Failure(LayoutError.TooManyPhotos(total, MAX_PHOTOS))
                    else -> Success(fill.items.flatMap { item -> List(item.copies) { item.photoId } })
                }
            }
        }

        is FillStrategy.FillSheets -> {
            val total = fill.sheets.toLong() * perSheet
            when {
                fill.photoIds.isEmpty() -> Failure(LayoutError.NoPhotos)
                fill.sheets < 1 -> Failure(LayoutError.InvalidCopies)
                total > MAX_PHOTOS -> Failure(LayoutError.TooManyPhotos(total, MAX_PHOTOS))
                else -> Success(List(total.toInt()) { fill.photoIds[it % fill.photoIds.size] })
            }
        }
    }

    // ---- cutting guides ------------------------------------------------------------------

    /**
     * Cutting guides that can never sit on a photo:
     *  - between photos, when there is a gap, a full-length line along the gap's centre;
     *  - at every photo edge (outer edges, and interior edges when spacing is zero), short crop
     *    ticks drawn OUTSIDE the grid, in the margin. Ticks are skipped where there is no room.
     */
    private fun cutLines(g: GridPlan): List<CutLine> {
        val lines = mutableListOf<CutLine>()
        val top = g.originYMm
        val bottom = g.originYMm + g.gridHeightMm
        val left = g.originXMm
        val right = g.originXMm + g.gridWidthMm

        val topTick = min(TICK_MM, top)
        val bottomTick = min(TICK_MM, g.paperHeightMm - bottom)
        val leftTick = min(TICK_MM, left)
        val rightTick = min(TICK_MM, g.paperWidthMm - right)

        for (c in 0..g.columns) {
            val interiorGap = c in 1 until g.columns && g.spacingXMm > EPS
            if (interiorGap) {
                val x = roundMm(left + c * g.cellWidthMm + (c - 1) * g.spacingXMm + g.spacingXMm / 2)
                lines += CutLine(x, top, x, bottom)
            } else {
                val x = roundMm(left + c * g.cellWidthMm + (c - 1).coerceAtLeast(0) * g.spacingXMm)
                if (topTick >= MIN_TICK_MM) lines += CutLine(x, roundMm(top - topTick), x, top)
                if (bottomTick >= MIN_TICK_MM) lines += CutLine(x, bottom, x, roundMm(bottom + bottomTick))
            }
        }
        for (r in 0..g.rows) {
            val interiorGap = r in 1 until g.rows && g.spacingYMm > EPS
            if (interiorGap) {
                val y = roundMm(top + r * g.cellHeightMm + (r - 1) * g.spacingYMm + g.spacingYMm / 2)
                lines += CutLine(left, y, right, y)
            } else {
                val y = roundMm(top + r * g.cellHeightMm + (r - 1).coerceAtLeast(0) * g.spacingYMm)
                if (leftTick >= MIN_TICK_MM) lines += CutLine(roundMm(left - leftTick), y, left, y)
                if (rightTick >= MIN_TICK_MM) lines += CutLine(right, y, roundMm(right + rightTick), y)
            }
        }
        return lines
    }

    companion object {
        /** Tolerance for floating-point comparisons, in mm (one nanometre-ish; far below print resolution). */
        const val EPS = 1e-6
        const val MAX_PHOTOS = 5000
        const val MIN_PHOTO_MM = 1.0
        private const val TICK_MM = 3.0
        private const val MIN_TICK_MM = 0.5

        private fun validDimension(v: Double) = v.isFinite() && v > 0
        private fun validPhotoDimension(v: Double) = v.isFinite() && v >= MIN_PHOTO_MM

        /** How many items of [size] separated by [gap] fit in [available]. */
        internal fun fit(available: Double, size: Double, gap: Double): Int =
            floor((available + gap + EPS) / (size + gap)).toInt().coerceAtLeast(0)

        /** Round to 1e-6 mm so coordinates are stable and free of float noise. */
        internal fun roundMm(v: Double): Double = round(v * 1e6) / 1e6
    }
}
