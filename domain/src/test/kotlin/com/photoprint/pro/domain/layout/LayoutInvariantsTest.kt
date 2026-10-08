package com.photoprint.pro.domain.layout

import com.photoprint.pro.domain.layout.LayoutOutcome.Failure
import com.photoprint.pro.domain.layout.LayoutOutcome.Success
import com.photoprint.pro.domain.model.CutLine
import com.photoprint.pro.domain.model.Margin
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoPlacement
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.model.Spacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * Sweeps many paper/photo/margin/spacing combinations and checks properties that must hold for
 * every layout, including a deliberately different, brute-force count of how many photos fit.
 */
class LayoutInvariantsTest {
    private val engine = LayoutEngine()
    private val eps = 1e-6

    private val papers = PaperSizePresets.all + PaperSize("odd", 133.3, 201.7)
    private val photos = PhotoSizePresets.all + PhotoSize("custom", 40.0, 60.0) + PhotoSize("tiny", 7.5, 9.0)
    private val margins = listOf(Margin.ZERO, Margin.uniform(3.0), Margin(10.0, 4.0, 2.0, 8.0))
    private val spacings = listOf(Spacing.ZERO, Spacing.uniform(2.0), Spacing(5.0, 0.5))

    /** Independent of LayoutEngine.fit: walk across the usable span placing items one by one. */
    private fun bruteCount(usable: Double, size: Double, gap: Double): Int {
        var n = 0
        var cursor = 0.0
        while (cursor + size <= usable + eps) {
            n++
            cursor += size + gap
        }
        return n
    }

    private fun bruteBest(paper: PaperSize, photo: PhotoSize, m: Margin, s: Spacing, rotation: Boolean): Int {
        val uw = paper.widthMm - m.leftMm - m.rightMm
        val uh = paper.heightMm - m.topMm - m.bottomMm
        val upright = bruteCount(uw, photo.widthMm, s.horizontalMm) * bruteCount(uh, photo.heightMm, s.verticalMm)
        val rotated = bruteCount(uw, photo.heightMm, s.horizontalMm) * bruteCount(uh, photo.widthMm, s.verticalMm)
        return if (rotation) maxOf(upright, rotated) else upright
    }

    private fun overlapsInterior(l: CutLine, p: PhotoPlacement): Boolean {
        val minX = minOf(l.x1Mm, l.x2Mm)
        val maxX = maxOf(l.x1Mm, l.x2Mm)
        val minY = minOf(l.y1Mm, l.y2Mm)
        val maxY = maxOf(l.y1Mm, l.y2Mm)
        // Open-interior intersection: touching an edge is allowed, crossing the inside is not.
        return minX < p.rightMm - eps && maxX > p.xMm + eps && minY < p.bottomMm - eps && maxY > p.yMm + eps
    }

    @Test
    fun `layout invariants hold across a wide sweep`() {
        var checked = 0
        for (paper in papers) for (photo in photos) for (m in margins) for (s in spacings) for (rot in listOf(false, true)) {
            val request = LayoutRequest(
                paper = paper,
                photo = photo,
                fill = FillStrategy.Copies.of("p", 37),
                margin = m,
                spacing = s,
                allowRotation = rot,
                showCutLines = true,
            )
            val label = "$paper $photo $m $s rot=$rot"
            val expected = bruteBest(paper, photo, m, s, rot)
            when (val out = engine.calculate(request)) {
                is Failure -> assertEquals(0, expected, "engine failed but $expected photos fit: $label")
                is Success -> {
                    val plan = out.value
                    assertEquals(expected, plan.photosPerSheet, "count mismatch: $label")
                    assertEquals(37, plan.sheets.sumOf { it.placements.size }, label)
                    assertEquals(plan.sheetCount, plan.sheets.size, label)
                    plan.sheets.dropLast(1).forEach { assertEquals(plan.photosPerSheet, it.placements.size, label) }

                    for (sheet in plan.sheets) {
                        assertEquals(paper.widthMm, sheet.paperWidthMm, 0.0)
                        assertEquals(paper.heightMm, sheet.paperHeightMm, 0.0)
                        for (p in sheet.placements) {
                            assertTrue(p.xMm >= m.leftMm - eps && p.yMm >= m.topMm - eps, "inside top/left margin: $label")
                            assertTrue(p.rightMm <= paper.widthMm - m.rightMm + eps, "inside right margin: $label")
                            assertTrue(p.bottomMm <= paper.heightMm - m.bottomMm + eps, "inside bottom margin: $label")
                            val expectedSize = if (p.rotation == 0) photo.widthMm to photo.heightMm else photo.heightMm to photo.widthMm
                            assertTrue(p.rotation == 0 || p.rotation == 90, "rotation is 0 or 90: $label")
                            assertEquals(expectedSize, p.widthMm to p.heightMm, "frame keeps physical size: $label")
                            assertTrue(rot || p.rotation == 0, "never rotated when rotation is disallowed: $label")
                        }
                        // No two frames overlap (touching edges is fine).
                        for (i in sheet.placements.indices) for (j in i + 1 until sheet.placements.size) {
                            val a = sheet.placements[i]
                            val b = sheet.placements[j]
                            val overlap = a.xMm < b.rightMm - eps && b.xMm < a.rightMm - eps &&
                                a.yMm < b.bottomMm - eps && b.yMm < a.bottomMm - eps
                            assertTrue(!overlap, "frames overlap: $label")
                        }
                        // Cut guides stay on the paper and never cross a photo.
                        for (l in sheet.cutLines) {
                            assertTrue(
                                listOf(l.x1Mm, l.x2Mm).all { it in -eps..paper.widthMm + eps } &&
                                    listOf(l.y1Mm, l.y2Mm).all { it in -eps..paper.heightMm + eps },
                                "cut line on paper: $label",
                            )
                            for (p in sheet.placements) assertTrue(!overlapsInterior(l, p), "cut line crosses photo: $label")
                        }
                    }
                    checked++
                }
            }
        }
        assertTrue(checked > 500, "sweep should exercise many successful layouts, got $checked")
    }
}
