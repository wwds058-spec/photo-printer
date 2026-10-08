package com.photoprint.pro.domain.layout

import com.photoprint.pro.domain.layout.LayoutOutcome.Failure
import com.photoprint.pro.domain.layout.LayoutOutcome.Success
import com.photoprint.pro.domain.measurement.LengthUnit
import com.photoprint.pro.domain.model.Margin
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PaperSizePresets.A4
import com.photoprint.pro.domain.model.PaperSizePresets.Photo4x6
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets.IdPhoto
import com.photoprint.pro.domain.model.PhotoSizePresets.Passport
import com.photoprint.pro.domain.model.PhotoSizePresets.TwoByTwoInch
import com.photoprint.pro.domain.model.Spacing
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class LayoutEngineTest {
    private val engine = LayoutEngine()

    private fun request(
        paper: PaperSize = Photo4x6,
        photo: PhotoSize = Passport,
        copies: Int = 1,
        margin: Margin = Margin.ZERO,
        spacing: Spacing = Spacing.ZERO,
        rotation: Boolean = false,
        cut: Boolean = false,
    ) = LayoutRequest(paper, photo, FillStrategy.Copies.of("p", copies), margin, spacing, rotation, cut)

    private fun plan(r: LayoutRequest): LayoutPlan = assertIs<Success<LayoutPlan>>(engine.calculate(r)).value

    private fun error(r: LayoutRequest): LayoutError = assertIs<Failure>(engine.calculate(r)).error

    private fun LayoutPlan.perSheetCounts() = sheets.map { it.placements.size }

    // 1. 35×45 on 102×152
    @Test
    fun `passport on 4x6 without rotation gives 2 by 3 upright`() {
        val p = plan(request(rotation = false))
        assertEquals(PhotoOrientation.UPRIGHT, p.grid.orientation)
        assertEquals(2, p.grid.columns)
        assertEquals(3, p.grid.rows)
        assertEquals(6, p.photosPerSheet)
        // Grid is 70 × 135 centred on 102 × 152.
        assertEquals(16.0, p.grid.originXMm, 1e-9)
        assertEquals(8.5, p.grid.originYMm, 1e-9)
        assertEquals(32.0, p.grid.unusedWidthMm, 1e-9)
        assertEquals(17.0, p.grid.unusedHeightMm, 1e-9)
    }

    @Test
    fun `passport on 4x6 with rotation allowed fits 8 rotated`() {
        val p = plan(request(rotation = true))
        assertEquals(PhotoOrientation.ROTATED_90, p.grid.orientation)
        assertEquals(2, p.grid.columns)
        assertEquals(4, p.grid.rows)
        assertEquals(8, p.photosPerSheet)
        assertEquals(45.0, p.sheets[0].placements[0].widthMm, 1e-9)
        assertEquals(35.0, p.sheets[0].placements[0].heightMm, 1e-9)
        assertEquals(90, p.sheets[0].placements[0].rotation)
    }

    @Test
    fun `practical margin and spacing`() {
        val p = plan(request(margin = Margin.uniform(3.0), spacing = Spacing.uniform(2.0)))
        assertEquals(2, p.grid.columns)
        assertEquals(3, p.grid.rows)
        assertEquals(15.0, p.grid.originXMm, 1e-9) // 3 + (96 - 72) / 2
        assertEquals(6.5, p.grid.originYMm, 1e-9) // 3 + (146 - 139) / 2
    }

    // 2. 51×51 on 102×152
    @Test
    fun `2x2 inch on 4x6 gives 4 regardless of rotation`() {
        for (rot in listOf(false, true)) {
            val p = plan(request(photo = TwoByTwoInch, rotation = rot))
            assertEquals(PhotoOrientation.UPRIGHT, p.grid.orientation, "square photo ties; upright wins")
            assertEquals(2, p.grid.columns)
            assertEquals(2, p.grid.rows)
            assertEquals(0.0, p.grid.originXMm, 1e-9)
            assertEquals(25.0, p.grid.originYMm, 1e-9)
        }
    }

    // 3. 35×45 on A4
    @Test
    fun `passport on A4 gives 36 and picks upright over rotated 32`() {
        val p = plan(request(paper = A4, copies = 100, rotation = true))
        assertEquals(PhotoOrientation.UPRIGHT, p.grid.orientation)
        assertEquals(6, p.grid.columns)
        assertEquals(6, p.grid.rows)
        assertEquals(listOf(36, 36, 28), p.perSheetCounts())
    }

    // 4 & 5. Landscape and portrait paper
    @Test
    fun `landscape paper`() {
        val landscape = PaperSize("4x6 landscape", 152.0, 102.0)
        val p = plan(request(paper = landscape, rotation = true))
        assertEquals(PhotoOrientation.UPRIGHT, p.grid.orientation)
        assertEquals(4, p.grid.columns)
        assertEquals(2, p.grid.rows)
        assertEquals(8, p.photosPerSheet)
    }

    @Test
    fun `portrait and landscape paper hold the same count when rotation is allowed`() {
        val portrait = plan(request(paper = Photo4x6, rotation = true)).photosPerSheet
        val landscape = plan(request(paper = PaperSize("L", 152.0, 102.0), rotation = true)).photosPerSheet
        assertEquals(portrait, landscape)
    }

    // 6. Zero spacing: photos touch
    @Test
    fun `zero spacing places photos edge to edge`() {
        val sheet = plan(request(copies = 6)).sheets[0]
        val a = sheet.placements[0]
        val b = sheet.placements[1]
        assertEquals(a.rightMm, b.xMm, 1e-9)
    }

    // 7. Large spacing
    @Test
    fun `large spacing reduces count and rotation does not help`() {
        val p = plan(request(spacing = Spacing.uniform(20.0), rotation = true))
        assertEquals(PhotoOrientation.UPRIGHT, p.grid.orientation)
        assertEquals(2, p.grid.columns)
        assertEquals(2, p.grid.rows)
    }

    // 8. Large margins
    @Test
    fun `large margins leave a single narrow column`() {
        val p = plan(request(margin = Margin.uniform(30.0), rotation = true))
        assertEquals(1, p.grid.columns)
        assertEquals(2, p.grid.rows)
        assertEquals(PhotoOrientation.UPRIGHT, p.grid.orientation)
    }

    @Test
    fun `asymmetric margins are honoured`() {
        val p = plan(request(photo = PhotoSize("sq", 50.0, 50.0), margin = Margin(0.0, 0.0, 2.0, 0.0)))
        // usable width 100 → 2 columns exactly, no centring slack, so origin x == left margin.
        assertEquals(2, p.grid.columns)
        assertEquals(2.0, p.grid.originXMm, 1e-9)
    }

    // 9. Custom sizes
    @Test
    fun `custom size in cm and inches is normalised to millimetres`() {
        val photo = PhotoSize.custom(5.0, 7.0, LengthUnit.CM)
        assertEquals(50.0, photo.widthMm, 1e-9)
        assertEquals(70.0, photo.heightMm, 1e-9)
        val paper = PaperSize.custom(5.0, 7.0, LengthUnit.INCH)
        assertEquals(127.0, paper.widthMm, 1e-9)
        assertEquals(177.8, paper.heightMm, 1e-9)
    }

    @Test
    fun `exact inch fits survive floating point error`() {
        // 2 in photo on 4 × 6 in paper: 2 × 3 = 6, even though 6 × 25.4 is not exactly 152.4.
        val photo = PhotoSize.custom(2.0, 2.0, LengthUnit.INCH)
        val paper = PaperSize.custom(4.0, 6.0, LengthUnit.INCH)
        val p = plan(request(paper = paper, photo = photo))
        assertEquals(2, p.grid.columns)
        assertEquals(3, p.grid.rows)
    }

    @Test
    fun `custom 40x60 on 5x7 prefers rotation`() {
        val p = plan(request(paper = PaperSize("5x7", 127.0, 178.0), photo = PhotoSize("c", 40.0, 60.0), rotation = true))
        assertEquals(PhotoOrientation.ROTATED_90, p.grid.orientation)
        assertEquals(8, p.photosPerSheet)
    }

    // 10. Rotation required to fit at all
    @Test
    fun `photo that only fits rotated`() {
        val tall = PhotoSize("wide", 150.0, 100.0)
        val p = plan(request(photo = tall, rotation = true))
        assertEquals(PhotoOrientation.ROTATED_90, p.grid.orientation)
        assertEquals(1, p.photosPerSheet)
        val e = assertIs<LayoutError.PhotoDoesNotFit>(error(request(photo = tall, rotation = false)))
        assertEquals(false, e.rotationWasAllowed)
    }

    // 11. One photo only
    @Test
    fun `single photo yields one sheet with one placement`() {
        val p = plan(request(copies = 1))
        assertEquals(1, p.sheetCount)
        assertEquals(1, p.totalPhotos)
        assertEquals(listOf(1), p.perSheetCounts())
        assertEquals(1, p.sheets[0].totalSheets)
        assertEquals(1, p.sheets[0].sheetNumber)
    }

    // 12. More copies than fit on one sheet
    @Test
    fun `20 copies at 6 per sheet need 4 sheets`() {
        val p = plan(request(copies = 20))
        assertEquals(20, p.totalPhotos)
        assertEquals(4, p.sheetCount)
        assertEquals(listOf(6, 6, 6, 2), p.perSheetCounts())
        assertEquals(listOf(0, 1, 2, 3), p.sheets.map { it.sheetIndex })
        assertTrue(p.sheets.all { it.totalSheets == 4 })
    }

    @Test
    fun `20 copies at 8 per sheet need 3 sheets`() {
        val p = plan(request(copies = 20, rotation = true))
        assertEquals(listOf(8, 8, 4), p.perSheetCounts())
    }

    @Test
    fun `exact multiple of sheet capacity has no empty trailing sheet`() {
        assertEquals(listOf(6, 6), plan(request(copies = 12)).perSheetCounts())
    }

    @Test
    fun `last sheet reuses the same slot positions as earlier sheets`() {
        val p = plan(request(copies = 8))
        assertEquals(p.sheets[0].placements[0].xMm, p.sheets[1].placements[0].xMm)
        assertEquals(p.sheets[0].placements[0].yMm, p.sheets[1].placements[0].yMm)
    }

    @Test
    fun `auto fill paper fills whole sheets cycling photos`() {
        val r = LayoutRequest(Photo4x6, Passport, FillStrategy.FillSheets(listOf("a", "b")), allowRotation = false)
        val p = plan(r)
        assertEquals(1, p.sheetCount)
        assertEquals(6, p.totalPhotos)
        assertEquals(listOf("a", "b", "a", "b", "a", "b"), p.sheets[0].placements.map { it.photoId })
        val two = plan(r.copy(fill = FillStrategy.FillSheets(listOf("a"), sheets = 3)))
        assertEquals(listOf(6, 6, 6), two.perSheetCounts())
    }

    @Test
    fun `mixed photos keep order and counts`() {
        val r = request().copy(fill = FillStrategy.Copies(listOf(PhotoCopies("a", 2), PhotoCopies("b", 3))))
        assertEquals(listOf("a", "a", "b", "b", "b"), plan(r).sheets[0].placements.map { it.photoId })
    }

    // 13. Impossible layouts
    @Test
    fun `photo larger than paper`() {
        val e = assertIs<LayoutError.PhotoDoesNotFit>(error(request(photo = PhotoSize("huge", 120.0, 170.0), rotation = true)))
        assertEquals(102.0, e.usableWidthMm, 1e-9)
    }

    @Test
    fun `margins that consume the paper`() {
        assertIs<LayoutError.MarginsConsumePaper>(error(request(margin = Margin(0.0, 0.0, 51.0, 51.0))))
        assertIs<LayoutError.MarginsConsumePaper>(error(request(margin = Margin.uniform(100.0))))
    }

    @Test
    fun `margins that leave a sliver`() {
        assertIs<LayoutError.PhotoDoesNotFit>(error(request(margin = Margin(0.0, 0.0, 50.0, 50.0))))
    }

    @Test
    fun `invalid inputs are typed errors not crashes`() {
        assertIs<LayoutError.InvalidPaper>(error(request(paper = PaperSize("x", 0.0, 100.0))))
        assertIs<LayoutError.InvalidPaper>(error(request(paper = PaperSize("x", Double.NaN, 100.0))))
        assertIs<LayoutError.InvalidPhoto>(error(request(photo = PhotoSize("x", -1.0, 45.0))))
        assertIs<LayoutError.InvalidPhoto>(error(request(photo = PhotoSize("x", 0.1, 45.0))))
        assertIs<LayoutError.InvalidMargin>(error(request(margin = Margin(-1.0, 0.0, 0.0, 0.0))))
        assertIs<LayoutError.InvalidSpacing>(error(request(spacing = Spacing(0.0, -2.0))))
        assertIs<LayoutError.InvalidCopies>(error(request(copies = 0)))
        assertIs<LayoutError.InvalidCopies>(error(request(copies = -3)))
        assertIs<LayoutError.NoPhotos>(
            error(request().copy(fill = FillStrategy.Copies(emptyList()))),
        )
        assertIs<LayoutError.NoPhotos>(
            error(request().copy(fill = FillStrategy.FillSheets(emptyList()))),
        )
        assertIs<LayoutError.TooManyPhotos>(error(request(copies = 100_000)))
    }

    // Cut lines
    @Test
    fun `cut lines disabled gives none`() {
        assertTrue(plan(request(copies = 6, cut = false)).sheets[0].cutLines.isEmpty())
    }

    @Test
    fun `zero spacing cut guides are ticks outside the grid only`() {
        val p = plan(request(copies = 6, cut = true))
        // 3 vertical edges × 2 + 4 horizontal edges × 2
        assertEquals(14, p.sheets[0].cutLines.size)
    }

    @Test
    fun `spacing cut guides run through the gaps`() {
        val p = plan(request(copies = 6, cut = true, spacing = Spacing.uniform(2.0)))
        // 1 + 2 interior full lines, plus outer ticks: 2×2 vertical + 2×2 horizontal
        assertEquals(11, p.sheets[0].cutLines.size)
    }

    @Test
    fun `no room for ticks when the grid fills the paper`() {
        val paper = PaperSize("sq", 102.0, 102.0)
        val p = plan(request(paper = paper, photo = TwoByTwoInch, copies = 4, cut = true))
        assertTrue(p.sheets[0].cutLines.isEmpty())
    }

    @Test
    fun `per-photo crops override the default crop`() {
        val special = com.photoprint.pro.domain.model.CropState(zoom = 1.8)
        val r = request(copies = 3).copy(
            fill = FillStrategy.Copies(listOf(PhotoCopies("a", 1), PhotoCopies("b", 2))),
            crops = mapOf("b" to special),
            defaultCrop = com.photoprint.pro.domain.model.CropState(zoom = 1.1),
        )
        val crops = plan(r).sheets[0].placements.map { it.cropState.zoom }
        assertEquals(listOf(1.1, 1.8, 1.8), crops)
    }
}
