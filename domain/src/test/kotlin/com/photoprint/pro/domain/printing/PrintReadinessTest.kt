package com.photoprint.pro.domain.printing

import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.layout.LayoutError
import com.photoprint.pro.domain.layout.LayoutOutcome
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.Margin
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.domain.model.Spacing
import com.photoprint.pro.domain.model.toLayoutRequest
import com.photoprint.pro.domain.pdf.ImageQuality
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class PrintReadinessTest {
    private val ready = PrinterInfo("p1", "Epson L3250", PrinterStatus.READY, "Wi-Fi")
    private val fullCaps = PrinterCapabilities(
        media = setOf(MediaSpec(4016, 5984)), // 102 × 152 mm
        borderless = Availability.SUPPORTED,
        colorChoice = Availability.SUPPORTED,
        scalingControl = Availability.SUPPORTED,
    )

    private fun photo(id: String = "a", w: Int = 1000, h: Int = 1300, copies: Int = 20, crop: CropState = CropState()) =
        ProjectPhoto(id, "file://$id", w, h, copies, crop)

    private fun project(photos: List<ProjectPhoto> = listOf(photo()), margin: Margin = Margin.uniform(3.0)) = PrintProject(
        id = "proj", name = "Test", photos = photos,
        photoSize = PhotoSizePresets.Passport, paperSize = PaperSizePresets.Photo4x6,
        margin = margin, spacing = Spacing.uniform(2.0), allowRotation = false,
        createdAtMillis = 0, modifiedAtMillis = 0,
    )

    private fun report(
        p: PrintProject = project(),
        printer: PrinterInfo? = ready,
        caps: PrinterCapabilities = fullCaps,
        settings: PrintSettings = PrintSettings(),
    ) = PrintReadiness.evaluate(p, printer, caps, settings)

    private inline fun <reified T : ReadinessItem> ReadinessReport.item(): T? = items.filterIsInstance<T>().firstOrNull()

    @Test
    fun `everything verified gives a clean ready-to-print report`() {
        val r = report()
        assertTrue(r.canPrint)
        assertFalse(r.hasWarnings, r.items.toString())
        assertEquals(PhotoSizePresets.Passport, r.item<ReadinessItem.Photo>()!!.size)
        assertEquals(PaperSizePresets.Photo4x6, r.item<ReadinessItem.Paper>()!!.size)
        val c = r.item<ReadinessItem.Copies>()!!
        assertEquals(20, c.photos)
        assertEquals(6, c.photosPerSheet)
        assertEquals(4, c.sheets)
        assertNotNull(r.item<ReadinessItem.ScalingActualSize>())
        assertEquals(ImageQuality.GOOD, r.item<ReadinessItem.ImageQualityResult>()!!.quality)
    }

    @Test
    fun `no printer blocks printing`() {
        val r = report(printer = null)
        assertFalse(r.canPrint)
        assertNotNull(r.item<ReadinessItem.NoPrinter>())
    }

    @Test
    fun `printer chosen in the system dialog is neutral not a pass or a blocker`() {
        val r = PrintReadiness.evaluate(project(), null, PrinterCapabilities(), PrintSettings(), printerChosenBySystem = true)
        assertTrue(r.canPrint)
        assertNotNull(r.item<ReadinessItem.PrinterChosenInPrintDialog>())
        assertEquals(null, r.item<ReadinessItem.NoPrinter>())
        assertEquals(Severity.INFO, r.item<ReadinessItem.PrinterChosenInPrintDialog>()!!.severity)
        assertFalse(r.items.any { it is ReadinessItem.Printer }, "no printer check mark is claimed")
        // Without the flag the same situation still blocks.
        assertFalse(PrintReadiness.evaluate(project(), null, PrinterCapabilities(), PrintSettings()).canPrint)
    }

    @Test
    fun `printer status maps to severity`() {
        assertFalse(report(printer = ready.copy(status = PrinterStatus.OFFLINE)).canPrint)
        val busy = report(printer = ready.copy(status = PrinterStatus.BUSY))
        assertTrue(busy.canPrint && busy.hasWarnings)
        val unknown = report(printer = ready.copy(status = PrinterStatus.UNKNOWN))
        assertTrue(unknown.canPrint && unknown.hasWarnings)
    }

    @Test
    fun `unconfirmed actual size is a warning not a pass`() {
        val r = report(caps = fullCaps.copy(scalingControl = Availability.UNKNOWN))
        assertTrue(r.canPrint)
        assertNotNull(r.item<ReadinessItem.ScalingUnverified>())
        assertEquals(null, r.item<ReadinessItem.ScalingActualSize>())
        // Explicitly unsupported is also not claimed as verified.
        assertNotNull(report(caps = fullCaps.copy(scalingControl = Availability.UNSUPPORTED)).item<ReadinessItem.ScalingUnverified>())
    }

    @Test
    fun `fit to page warns that sizes will change`() {
        val r = report(settings = PrintSettings(scaling = ScalingMode.FIT_TO_PAGE))
        assertNotNull(r.item<ReadinessItem.ScalingWillResize>())
        assertEquals(null, r.item<ReadinessItem.ScalingActualSize>())
        assertTrue(r.canPrint, "the user may still proceed knowingly")
    }

    @Test
    fun `borderless warns unless the printer confirms support`() {
        val want = PrintSettings(borderless = true)
        assertEquals(null, report(settings = want).item<ReadinessItem.BorderlessNotGuaranteed>())
        for (a in listOf(Availability.UNKNOWN, Availability.UNSUPPORTED)) {
            val w = report(settings = want, caps = fullCaps.copy(borderless = a)).item<ReadinessItem.BorderlessNotGuaranteed>()
            assertEquals(a, w!!.availability)
        }
        // Not requested: nothing to warn about.
        assertEquals(null, report(caps = fullCaps.copy(borderless = Availability.UNSUPPORTED)).item<ReadinessItem.BorderlessNotGuaranteed>())
    }

    @Test
    fun `paper support is only judged when the printer reports a list`() {
        assertEquals(null, report(caps = fullCaps.copy(media = null)).item<ReadinessItem.PaperNotAdvertised>())
        assertNotNull(report(caps = fullCaps.copy(media = setOf(MediaSpec(8268, 11693)))).item<ReadinessItem.PaperNotAdvertised>()) // A4 only
        assertEquals(null, report(caps = fullCaps.copy(media = setOf(MediaSpec(5984, 4016)))).item<ReadinessItem.PaperNotAdvertised>()) // landscape entry
        assertEquals(null, report(caps = fullCaps.copy(media = setOf(MediaSpec(4000, 6000)))).item<ReadinessItem.PaperNotAdvertised>()) // within 1 mm
    }

    @Test
    fun `low resolution is reported with the offending photos`() {
        // 150 px across 35 mm ≈ 109 dpi; 1000 px is fine.
        val r = report(project(listOf(photo("sharp"), photo("tiny", w = 150, h = 200))))
        val q = r.item<ReadinessItem.ImageQualityResult>()!!
        assertEquals(ImageQuality.LOW, q.quality)
        assertEquals(listOf("tiny"), q.photoIds)
        assertEquals(150 / (35 / 25.4), q.lowestDpi, 0.5)
        assertTrue(r.canPrint && r.hasWarnings)
    }

    @Test
    fun `zooming in can drop a good image to acceptable`() {
        // 420 px / 35 mm ≈ 305 dpi; at zoom 1.5 → 203 dpi.
        val r = report(project(listOf(photo(w = 420, h = 540, crop = CropState(zoom = 1.5)))))
        assertEquals(ImageQuality.ACCEPTABLE, r.item<ReadinessItem.ImageQualityResult>()!!.quality)
    }

    @Test
    fun `impossible layout blocks and hides the checkmarks that would be false`() {
        val r = report(project(margin = Margin.uniform(60.0)))
        assertFalse(r.canPrint)
        assertIs<LayoutError.MarginsConsumePaper>(r.item<ReadinessItem.LayoutFailed>()!!.error)
        assertEquals(null, r.item<ReadinessItem.Photo>())
        assertEquals(null, r.item<ReadinessItem.Copies>())
    }

    @Test
    fun `test print is the first sheet of the real layout`() {
        val plan = (LayoutEngine().calculate(project().toLayoutRequest()) as LayoutOutcome.Success).value
        assertEquals(4, plan.sheetCount)
        val test = PrintReadiness.testSheet(plan)
        assertEquals(1, test.sheetCount)
        assertEquals(1, test.sheets[0].totalSheets)
        assertEquals(0, test.sheets[0].sheetIndex)
        assertEquals(6, test.totalPhotos)
        assertEquals(plan.sheets[0].placements, test.sheets[0].placements, "same coordinates as the real first sheet")
    }

    @Test
    fun `test print of a short job keeps only what exists`() {
        val plan = (LayoutEngine().calculate(project(listOf(photo(copies = 2))).toLayoutRequest()) as LayoutOutcome.Success).value
        assertEquals(2, PrintReadiness.testSheet(plan).totalPhotos)
    }
}
