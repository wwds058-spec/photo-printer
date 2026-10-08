package com.photoprint.pro.presentation.session

import com.photoprint.pro.domain.layout.LayoutError
import com.photoprint.pro.domain.layout.LayoutOutcome
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.model.BuiltInTemplates
import com.photoprint.pro.domain.model.toLayoutRequest
import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.presentation.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PrintSessionTest {
    private var n = 0
    private val settings = AppSettings(defaultMarginMm = 3.0, defaultSpacingMm = 2.0)
    private val session = PrintSession(ids = { "p${++n}" }, settings = settings)
    private val s get() = session.state.value

    private fun pick(name: String, w: Int = 1200, h: Int = 1600) = PickedPhoto("ref/$name", w, h)

    @Test
    fun `new session uses the defaults and has no layout yet`() {
        assertEquals(PhotoSizePresets.Passport, s.photoSize)
        assertEquals(PaperSizePresets.Photo4x6, s.paperSize)
        assertEquals(3.0, s.margin.topMm)
        assertEquals(2.0, s.spacing.horizontalMm)
        assertIs<LayoutOutcome.Failure>(s.layout)
        assertEquals(LayoutError.NoPhotos, (s.layout as LayoutOutcome.Failure).error)
        assertNull(s.plan)
        assertTrue(!s.hasPhotos)
    }

    @Test
    fun `adding photos builds the layout immediately`() {
        session.setAllowRotation(false)
        session.addPhotos(listOf(pick("a")))
        assertEquals(1, s.photos.size)
        val plan = assertNotNull(s.plan)
        assertEquals(6, plan.photosPerSheet) // 35×45, margin 3, spacing 2 on 4×6, upright
        assertEquals(1, plan.totalPhotos)
        session.setCopies(20)
        assertEquals(4, s.plan!!.sheetCount)
        assertEquals(listOf(6, 6, 6, 2), s.plan!!.sheets.map { it.placements.size })
    }

    @Test
    fun `duplicates are ignored and new photos inherit the current copies`() {
        session.setCopies(3)
        session.addPhotos(listOf(pick("a"), pick("a"), pick("b")))
        session.addPhotos(listOf(pick("b"), pick("c")))
        assertEquals(listOf("ref/a", "ref/b", "ref/c"), s.photos.map { it.sourceRef })
        assertEquals(listOf(3, 3, 3), s.photos.map { it.copies })
        assertEquals(listOf("p1", "p2", "p3"), s.photos.map { it.photoId })
    }

    @Test
    fun `reordering moves a photo and clamps at the ends`() {
        session.addPhotos(listOf(pick("a"), pick("b"), pick("c")))
        val (a, b, c) = s.photos.map { it.photoId }
        session.movePhoto(c, -1)
        assertEquals(listOf(a, c, b), s.photos.map { it.photoId })
        session.movePhoto(a, -5)
        assertEquals(listOf(a, c, b), s.photos.map { it.photoId }, "already first")
        session.movePhoto(a, 99)
        assertEquals(listOf(c, b, a), s.photos.map { it.photoId })
        session.movePhoto("missing", 1)
        assertEquals(listOf(c, b, a), s.photos.map { it.photoId })
    }

    @Test
    fun `removing the last photo returns to the no photos state`() {
        session.addPhotos(listOf(pick("a")))
        session.removePhoto(s.photos.single().photoId)
        assertEquals(LayoutError.NoPhotos, (s.layout as LayoutOutcome.Failure).error)
    }

    @Test
    fun `layout order follows photo order`() {
        session.addPhotos(listOf(pick("a"), pick("b")))
        val (a, b) = s.photos.map { it.photoId }
        assertEquals(listOf(a, b), s.plan!!.sheets[0].placements.map { it.photoId })
        session.movePhoto(b, -1)
        assertEquals(listOf(b, a), s.plan!!.sheets[0].placements.map { it.photoId })
    }

    @Test
    fun `current sheet stays valid when the sheet count shrinks`() {
        session.setAllowRotation(false)
        session.addPhotos(listOf(pick("a")))
        session.setCopies(20)
        session.setSheet(3)
        assertEquals(3, s.currentSheet)
        session.setCopies(2)
        assertEquals(0, s.currentSheet)
        session.setSheet(99)
        assertEquals(0, s.currentSheet)
        session.setSheet(-4)
        assertEquals(0, s.currentSheet)
    }

    @Test
    fun `settings are clamped to the ranges the screen offers`() {
        session.setMarginAll(99.0)
        assertEquals(AppSettings.MAX_MARGIN_MM, s.margin.leftMm)
        session.setSpacingAll(-4.0)
        assertEquals(0.0, s.spacing.verticalMm)
        session.setCopies(0)
        assertEquals(1, s.copies)
        session.setCopies(10_000)
        assertEquals(PrintSession.MAX_COPIES, s.copies)
        session.setFillSheets(0)
        assertEquals(1, s.fillSheets)
    }

    @Test
    fun `auto fill fills whole sheets and ignores copies`() {
        session.setAllowRotation(false)
        session.addPhotos(listOf(pick("a")))
        session.setAutoFill(true)
        session.setFillSheets(2)
        assertEquals(12, s.plan!!.totalPhotos)
        assertEquals(2, s.plan!!.sheetCount)
        session.setAutoFill(false)
        assertEquals(1, s.plan!!.totalPhotos)
    }

    @Test
    fun `impossible settings keep the session usable and report why`() {
        session.addPhotos(listOf(pick("a")))
        session.setPhotoSize(PhotoSize("huge", 200.0, 300.0))
        val e = (s.layout as LayoutOutcome.Failure).error
        assertIs<LayoutError.PhotoDoesNotFit>(e)
        assertEquals(1, s.photos.size, "nothing is lost")
        session.setPhotoSize(PhotoSizePresets.Passport)
        assertNotNull(s.plan)
    }

    @Test
    fun `changing the photo size re-clamps crops to the new frame`() {
        session.addPhotos(listOf(pick("a", 1600, 1200))) // landscape image
        val id = s.photos.single().photoId
        // Wide pan is legal in a 35:45 portrait frame…
        session.setCrop(id, CropState(panX = 0.19))
        assertEquals(0.19, s.photo(id)!!.crop.panX, 1e-12)
        // …but a square frame has much less horizontal room, so the stored pan is pulled in.
        session.setPhotoSize(PhotoSizePresets.TwoByTwoInch)
        assertTrue(kotlin.math.abs(s.photo(id)!!.crop.panX) <= 0.1668)
    }

    @Test
    fun `editing one photo reaches its placements and leaves others alone`() {
        session.addPhotos(listOf(pick("a"), pick("b")))
        val (a, b) = s.photos.map { it.photoId }
        val edited = CropState(zoom = 2.0)
        session.setCrop(a, edited)
        val byId = s.plan!!.sheets[0].placements.associate { it.photoId to it.cropState }
        assertEquals(edited, byId[a])
        assertEquals(CropState(), byId[b])
    }

    @Test
    fun `replacing a photo resets only its crop`() {
        session.addPhotos(listOf(pick("a")))
        val id = s.photos.single().photoId
        session.setCrop(id, CropState(zoom = 3.0))
        session.replacePhoto(id, PickedPhoto("ref/new", 2000, 3000))
        val p = s.photo(id)!!
        assertEquals("ref/new", p.sourceRef)
        assertEquals(2000, p.widthPx)
        assertEquals(CropState(), p.crop)
        assertEquals(id, p.photoId, "same frame, new picture")
    }

    @Test
    fun `going back and forth loses nothing`() {
        session.addPhotos(listOf(pick("a")))
        session.setPaperSize(PaperSizePresets.A4)
        session.setCopies(5)
        val snapshot = s
        // Screens only read state; there is no per-screen copy to lose.
        assertEquals(snapshot, session.state.value)
    }

    @Test
    fun `toProject uses the same layout as the session`() {
        session.addPhotos(listOf(pick("a"), pick("b")))
        session.setAutoFill(true)
        session.setCopies(4)
        val project = s.toProject("proj", 10L, createdAtMillis = 5L)
        assertEquals(5L, project.createdAtMillis)
        assertEquals(10L, project.modifiedAtMillis)
        assertEquals(1, project.fillSheets)
        val viaProject = (LayoutEngine().calculate(project.toLayoutRequest()) as LayoutOutcome.Success).value
        assertEquals(s.plan, viaProject)
    }

    @Test
    fun `loading a project restores its settings`() {
        session.addPhotos(listOf(pick("a")))
        session.setPaperSize(PaperSize("custom", 130.0, 180.0))
        session.setShowCutLines(true)
        val saved = s.toProject("saved", 1L)
        session.startNew(settings)
        assertTrue(!s.hasPhotos)
        session.load(saved)
        assertEquals("saved", s.projectId)
        assertEquals(saved.paperSize, s.paperSize)
        assertTrue(s.showCutLines)
        assertEquals(s.photos, saved.photos)
        assertNotNull(s.plan)
    }

    @Test
    fun `quick print and template start from their presets`() {
        session.startQuickPrint(QuickPrints.photo4x6, settings)
        assertEquals(102.0, s.photoSize.widthMm)
        assertEquals(0.0, s.margin.leftMm)
        session.addPhotos(listOf(pick("a")))
        assertEquals(1, s.plan!!.photosPerSheet)

        session.startFromTemplate(BuiltInTemplates.passportA4, settings)
        assertEquals(PaperSizePresets.A4, s.paperSize)
        assertTrue(s.photos.isEmpty())
        assertEquals(4, QuickPrints.all.size)
    }

    @Test
    fun `suggested name mentions both sizes`() {
        assertEquals("Passport on 4 × 6 inch", PrintSession.suggestedName(s))
    }
}
