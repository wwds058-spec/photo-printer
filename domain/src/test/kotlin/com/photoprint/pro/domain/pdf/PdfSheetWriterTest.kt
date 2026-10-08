package com.photoprint.pro.domain.pdf

import com.photoprint.pro.domain.layout.FillStrategy
import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.layout.LayoutOutcome
import com.photoprint.pro.domain.layout.LayoutPlan
import com.photoprint.pro.domain.layout.LayoutRequest
import com.photoprint.pro.domain.measurement.Measurement
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.Margin
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoPlacement
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.model.SheetLayout
import com.photoprint.pro.domain.model.Spacing
import org.apache.pdfbox.Loader
import org.apache.pdfbox.cos.COSName
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject
import org.apache.pdfbox.rendering.ImageType
import org.apache.pdfbox.rendering.PDFRenderer
import java.awt.Color
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import javax.imageio.ImageIO
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertTrue

/**
 * Verifies physical output end to end with an independent reader: parse the PDF, render it at
 * 254 dpi (exactly 10 px per mm) and measure where colours land.
 */
class PdfSheetWriterTest {
    private val writer = PdfSheetWriter()
    private val pxPerMm = 10.0
    private val dpi = 254f

    private val red = Color(255, 0, 0)
    private val green = Color(0, 255, 0)
    private val blue = Color(0, 0, 255)
    private val yellow = Color(255, 255, 0)
    private val palette = mapOf("R" to red, "G" to green, "B" to blue, "Y" to yellow, "W" to Color.WHITE)

    // ---- helpers -------------------------------------------------------------------------

    private fun jpeg(w: Int, h: Int, paint: (Int, Int) -> Color): PhotoImage {
        val img = BufferedImage(w, h, BufferedImage.TYPE_INT_RGB)
        for (y in 0 until h) for (x in 0 until w) img.setRGB(x, y, paint(x, y).rgb)
        val bytes = ByteArrayOutputStream().also { check(ImageIO.write(img, "jpg", it)) }.toByteArray()
        return PhotoImage(w, h, bytes)
    }

    private fun solid(c: Color, w: Int = 200, h: Int = 200) = jpeg(w, h) { _, _ -> c }

    /** TL red, TR green, BL blue, BR yellow. */
    private fun quadrants(n: Int = 400) = jpeg(n, n) { x, y ->
        when {
            y < n / 2 && x < n / 2 -> red
            y < n / 2 -> green
            x < n / 2 -> blue
            else -> yellow
        }
    }

    /** Four vertical stripes: red, green, blue, yellow (left to right). */
    private fun stripes(w: Int = 400, h: Int = 400) = jpeg(w, h) { x, _ -> listOf(red, green, blue, yellow)[x * 4 / w] }

    private fun pdf(pages: List<PdfPageSpec>, vararg images: Pair<String, PhotoImage>): ByteArray {
        val map = images.toMap()
        return ByteArrayOutputStream().also { writer.write(pages, { id -> map[id] }, it) }.toByteArray()
    }

    private fun render(bytes: ByteArray, page: Int = 0): BufferedImage =
        Loader.loadPDF(bytes).use { PDFRenderer(it).renderImageWithDPI(page, dpi, ImageType.RGB) }

    private fun nearest(argb: Int): String {
        val c = Color(argb)
        return palette.minBy { (_, p) ->
            (p.red - c.red).let { it * it } + (p.green - c.green).let { it * it } + (p.blue - c.blue).let { it * it }
        }.key
    }

    /** Colour name at a point given in millimetres from the paper's top-left. */
    private fun BufferedImage.at(xMm: Double, yMm: Double) = nearest(getRGB((xMm * pxPerMm).toInt(), (yMm * pxPerMm).toInt()))

    private fun singleFrame(
        x: Double = 31.0,
        y: Double = 56.0,
        size: Double = 40.0,
        rotation: Int = 0,
        crop: CropState = CropState(),
    ): PdfPageSpec = SheetLayout(
        102.0,
        152.0,
        listOf(PhotoPlacement("p", x, y, size, size, rotation, crop)),
        emptyList(),
        0,
        1,
    ).toPdfPageSpec()

    /** Colours at the centres of the frame's four quadrants: TL TR BL BR. */
    private fun BufferedImage.quadrantColours(x: Double = 31.0, y: Double = 56.0, size: Double = 40.0) = listOf(
        at(x + size * .25, y + size * .25),
        at(x + size * .75, y + size * .25),
        at(x + size * .25, y + size * .75),
        at(x + size * .75, y + size * .75),
    )

    private fun plan(copies: Int, cut: Boolean = false, margin: Double = 3.0, spacing: Double = 2.0): LayoutPlan {
        val out = LayoutEngine().calculate(
            LayoutRequest(
                PaperSizePresets.Photo4x6,
                PhotoSizePresets.Passport,
                FillStrategy.Copies.of("p", copies),
                Margin.uniform(margin),
                Spacing.uniform(spacing),
                allowRotation = true,
                showCutLines = cut,
            ),
        )
        return assertIs<LayoutOutcome.Success<LayoutPlan>>(out).value
    }

    // ---- document structure --------------------------------------------------------------

    @Test
    fun `page count and media box match the paper exactly`() {
        val p = plan(copies = 20) // 8 per sheet rotated → 3 sheets
        val bytes = ByteArrayOutputStream().also { writer.write(p, { solid(red) }, it) }.toByteArray()
        Loader.loadPDF(bytes).use { doc ->
            assertEquals(3, doc.numberOfPages)
            for (page in doc.pages) {
                assertEquals(102.0, Measurement.pointsToMm(page.mediaBox.width.toDouble()), 0.002)
                assertEquals(152.0, Measurement.pointsToMm(page.mediaBox.height.toDouble()), 0.002)
            }
        }
    }

    @Test
    fun `viewer print scaling is set to none`() {
        Loader.loadPDF(pdf(listOf(singleFrame()), "p" to solid(red))).use { doc ->
            val prefs = doc.documentCatalog.cosObject.getCOSDictionary(COSName.VIEWER_PREFERENCES)
            assertEquals("None", prefs.getNameAsString("PrintScaling"))
        }
    }

    @Test
    fun `jpeg bytes are embedded untouched and shared between frames`() {
        val img = solid(blue, 321, 123)
        val p = plan(copies = 6)
        val bytes = ByteArrayOutputStream().also { writer.write(p, { img }, it) }.toByteArray()
        Loader.loadPDF(bytes).use { doc ->
            val resources = doc.getPage(0).resources
            val names = resources.xObjectNames.toList()
            assertEquals(1, names.size, "one image object, drawn many times")
            val x = resources.getXObject(names.single()) as PDImageXObject
            assertEquals(321, x.width)
            assertEquals(123, x.height)
            assertContentEquals(img.jpegBytes, x.cosObject.createRawInputStream().use { it.readBytes() })
        }
    }

    @Test
    fun `output is deterministic`() {
        val p = plan(copies = 10, cut = true)
        val img = solid(red)
        fun once() = ByteArrayOutputStream().also { writer.write(p, { img }, it) }.toByteArray()
        assertContentEquals(once(), once())
    }

    @Test
    fun `missing image fails loudly before writing`() {
        val sink = ByteArrayOutputStream()
        val e = assertFailsWith<MissingImageException> { writer.write(plan(1), { null }, sink) }
        assertEquals("p", e.photoId)
        assertEquals(0, sink.size())
    }

    // ---- physical position (rendered pixels) ---------------------------------------------

    @Test
    fun `every frame lands at its layout millimetre position`() {
        val p = plan(copies = 8, margin = 3.0, spacing = 2.0)
        val bytes = ByteArrayOutputStream().also { writer.write(p, { solid(red) }, it) }.toByteArray()
        val img = render(bytes)
        assertEquals(1020, img.width) // 102 mm × 10 px/mm
        assertEquals(1520.0, img.height.toDouble(), 1.0) // renderer truncates 1519.99 → 1519
        val inset = 0.3 // mm; allows for anti-aliasing at the edge
        for (pl in p.sheets[0].placements) {
            // Just inside each corner: photo colour.
            for ((cx, cy) in listOf(
                pl.xMm + inset to pl.yMm + inset,
                pl.rightMm - inset to pl.yMm + inset,
                pl.xMm + inset to pl.bottomMm - inset,
                pl.rightMm - inset to pl.bottomMm - inset,
            )) assertEquals("R", img.at(cx, cy), "inside $pl")
            // Just outside the left/top/right/bottom edge, across the 2 mm gap: paper white.
            assertEquals("W", img.at(pl.xMm - inset, pl.yMm + pl.heightMm / 2).let { if (pl.xMm - 2 < 3) "W" else it }, "left of $pl")
            assertEquals("W", img.at(pl.xMm + pl.widthMm / 2, pl.yMm - inset).let { if (pl.yMm - 2 < 3) "W" else it }, "above $pl")
        }
        // Margins stay white.
        assertEquals("W", img.at(1.5, 1.5))
        assertEquals("W", img.at(100.5, 150.5))
    }

    @Test
    fun `frame edges measure exactly the requested size`() {
        // 35 × 45 mm photo; measure the red run along the middle row and column in pixels.
        val sheet = SheetLayout(
            102.0, 152.0,
            listOf(PhotoPlacement("p", 20.0, 30.0, 35.0, 45.0)),
            emptyList(), 0, 1,
        ).toPdfPageSpec()
        val img = render(pdf(listOf(sheet), "p" to solid(red)))
        fun isRed(x: Int, y: Int) = nearest(img.getRGB(x, y)) == "R"
        val row = (0 until img.width).filter { isRed(it, 52 * 10) }
        val col = (0 until img.height).filter { isRed(20 * 10 + 175, it) }
        assertEquals(350.0, (row.last() - row.first() + 1).toDouble(), 2.0, "35 mm wide = 350 px")
        assertEquals(450.0, (col.last() - col.first() + 1).toDouble(), 2.0, "45 mm tall = 450 px")
        assertEquals(200.0, row.first().toDouble(), 2.0)
        assertEquals(300.0, col.first().toDouble(), 2.0)
    }

    @Test
    fun `cut lines are drawn and stay out of photos`() {
        val p = plan(copies = 8, cut = true, margin = 3.0, spacing = 2.0)
        val img = render(ByteArrayOutputStream().also { writer.write(p, { solid(red) }, it) }.toByteArray())
        val sheet = p.sheets[0]
        // A full gap line sits on a pixel boundary at 0.25 pt (< 1 px), so look at the pixels
        // around it for an anti-aliased grey one: neither paper-white nor photo-red.
        val gap = sheet.cutLines.first { it.x1Mm == it.x2Mm && it.y2Mm - it.y1Mm > 20 }
        val px = (gap.x1Mm * pxPerMm).toInt()
        val py = ((gap.y1Mm + gap.y2Mm) / 2 * pxPerMm).toInt()
        val grey = (px - 2..px + 2).map { Color(img.getRGB(it, py)) }.filter { it.red < 250 && it.red == it.green && it.green == it.blue }
        assertTrue(grey.isNotEmpty(), "expected a grey guide pixel near x=$px")
        // And it is in the gap: the pixels 1 mm either side are white paper (the photos start 1 mm away).
        assertEquals("W", img.at(gap.x1Mm - 0.8, (gap.y1Mm + gap.y2Mm) / 2))
        assertEquals("W", img.at(gap.x1Mm + 0.8, (gap.y1Mm + gap.y2Mm) / 2))
    }

    // ---- crop / zoom / pan / rotation ----------------------------------------------------

    @Test
    fun `plain placement shows the image upright`() {
        val img = render(pdf(listOf(singleFrame()), "p" to quadrants()))
        assertEquals(listOf("R", "G", "B", "Y"), img.quadrantColours())
    }

    @Test
    fun `placement turned 90 clockwise puts the image top on the right`() {
        val img = render(pdf(listOf(singleFrame(rotation = 90)), "p" to quadrants()))
        // old TL→TR, TR→BR, BL→TL, BR→BL
        assertEquals(listOf("B", "R", "Y", "G"), img.quadrantColours())
    }

    @Test
    fun `user crop rotation 90 turns the content clockwise`() {
        val img = render(pdf(listOf(singleFrame(crop = CropState(rotationDegrees = 90))), "p" to quadrants()))
        assertEquals(listOf("B", "R", "Y", "G"), img.quadrantColours())
    }

    @Test
    fun `rotation 180 and 270`() {
        val r180 = render(pdf(listOf(singleFrame(crop = CropState(rotationDegrees = 180))), "p" to quadrants()))
        assertEquals(listOf("Y", "B", "G", "R"), r180.quadrantColours())
        val r270 = render(pdf(listOf(singleFrame(crop = CropState(rotationDegrees = 270))), "p" to quadrants()))
        assertEquals(listOf("G", "Y", "R", "B"), r270.quadrantColours())
    }

    @Test
    fun `both rotations compose`() {
        val spec = singleFrame(rotation = 90, crop = CropState(rotationDegrees = 90))
        // two clockwise quarter turns = 180°
        assertEquals(listOf("Y", "B", "G", "R"), render(pdf(listOf(spec), "p" to quadrants())).quadrantColours())
    }

    @Test
    fun `zoom crops to the middle and pan moves the window`() {
        // Samples the frame's left and right halves (centres at 10 mm and 30 mm across a 40 mm frame).
        fun halves(crop: CropState): List<String> {
            val img = render(pdf(listOf(singleFrame(crop = crop)), "p" to stripes()))
            return listOf(img.at(31.0 + 10, 76.0), img.at(31.0 + 30, 76.0))
        }
        // zoom 1: four 10 mm stripes R,G,B,Y. Sample mid-stripe (5 mm and 35 mm), never on a boundary.
        val plain = render(pdf(listOf(singleFrame()), "p" to stripes()))
        assertEquals(listOf("R", "G", "B", "Y"), listOf(5.0, 15.0, 25.0, 35.0).map { plain.at(31.0 + it, 76.0) })
        // zoom 2, centred: window is stripes G,B (image x 25–75 %) → left half G, right half B
        assertEquals(listOf("G", "B"), halves(CropState(zoom = 2.0)))
        // zoom 2, pan far right: clamped so the image's left edge meets the frame's → R,G
        assertEquals(listOf("R", "G"), halves(CropState(zoom = 2.0, panX = 9.0)))
        // zoom 2, pan far left: clamped to the image's right edge → B,Y
        assertEquals(listOf("B", "Y"), halves(CropState(zoom = 2.0, panX = -9.0)))
    }

    @Test
    fun `zoom below 1 is clamped so no blank edge is printed`() {
        val img = render(pdf(listOf(singleFrame(crop = CropState(zoom = 0.3, panX = 5.0, panY = 5.0))), "p" to solid(red)))
        for ((x, y) in listOf(31.3 to 56.3, 70.7 to 56.3, 31.3 to 95.7, 70.7 to 95.7)) assertEquals("R", img.at(x, y))
    }

    @Test
    fun `non-square image covers the frame without distortion`() {
        // 2:1 image, 4 stripes (each 200 px) in a square frame → shows the middle half: G then B.
        val img = render(pdf(listOf(singleFrame()), "p" to stripes(800, 400)))
        assertEquals(listOf("G", "B"), listOf(img.at(31.0 + 5, 76.0), img.at(31.0 + 35, 76.0)))
    }

    // ---- image quality -------------------------------------------------------------------

    @Test
    fun `effective dpi reflects crop zoom and physical size`() {
        // 420 px across 35 mm = 420 / (35 / 25.4) ≈ 304.8 dpi.
        val dpi = ImageFit.effectiveDpi(420, 540, CropState(), 35.0, 45.0)
        assertEquals(304.8, dpi, 0.05)
        assertEquals(ImageQuality.GOOD, ImageFit.quality(dpi))
        // Zooming in 2× halves the effective resolution.
        assertEquals(dpi / 2, ImageFit.effectiveDpi(420, 540, CropState(zoom = 2.0), 35.0, 45.0), 1e-9)
        assertEquals(ImageQuality.ACCEPTABLE, ImageFit.quality(250.0))
        assertEquals(ImageQuality.LOW, ImageFit.quality(150.0))
        // A user rotation of 90° swaps which image side covers which frame side.
        assertEquals(
            ImageFit.effectiveDpi(540, 420, CropState(), 35.0, 45.0),
            ImageFit.effectiveDpi(420, 540, CropState(rotationDegrees = 90), 35.0, 45.0),
            1e-9,
        )
        // Zoom below 1 is not allowed to upscale beyond the cover fit.
        assertEquals(dpi, ImageFit.effectiveDpi(420, 540, CropState(zoom = 0.5), 35.0, 45.0), 1e-9)
    }

    @Test
    fun `dpi from the drawing matches dpi from millimetres`() {
        val spec = plan(1).sheets[0].toPdfPageSpec()
        val fr = spec.frames.single()
        val d = ImageFit.draw(fr.rectPt, fr.rotationDegrees, fr.cropState, 800, 1000)
        val pl = plan(1).sheets[0].placements.single()
        val photoW = if (pl.rotation == 90) pl.heightMm else pl.widthMm
        val photoH = if (pl.rotation == 90) pl.widthMm else pl.heightMm
        assertEquals(ImageFit.effectiveDpi(800, 1000, fr.cropState, photoW, photoH), d.effectiveDpi, 1e-6)
    }
}
