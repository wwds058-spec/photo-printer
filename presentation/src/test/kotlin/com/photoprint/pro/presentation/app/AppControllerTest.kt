package com.photoprint.pro.presentation.app

import com.photoprint.pro.domain.model.BuiltInTemplates
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.domain.pdf.PhotoImage
import com.photoprint.pro.domain.printing.Availability
import com.photoprint.pro.domain.printing.PrinterCapabilities
import com.photoprint.pro.domain.printing.PrinterInfo
import com.photoprint.pro.domain.printing.PrinterStatus
import com.photoprint.pro.domain.printing.ReadinessItem
import com.photoprint.pro.domain.printing.ScalingMode
import com.photoprint.pro.domain.project.InMemoryProjectRepository
import com.photoprint.pro.domain.project.InMemoryTemplateRepository
import com.photoprint.pro.domain.project.ProjectService
import com.photoprint.pro.presentation.nav.NavStack
import com.photoprint.pro.presentation.nav.Route
import com.photoprint.pro.presentation.session.PickedPhoto
import com.photoprint.pro.presentation.session.PrintSession
import com.photoprint.pro.presentation.session.QuickPrints
import com.photoprint.pro.presentation.settings.AppSettings
import com.photoprint.pro.presentation.settings.InMemorySettingsRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class FakeGateway : PlatformGateway {
    override var actualPxPerMm = 6.3
    var gallery: List<PickedPhoto> = emptyList()
    var camera: PickedPhoto? = null
    var imageOk = true
    var discovery: PrinterDiscovery = PrinterDiscovery.SystemDialogOnly
    var settingsOpens = true
    var result: GatewayResult = GatewayResult.Done

    class PrintCall(val pdf: ByteArray, val jobName: String, val request: PrintRequest)
    val saved = mutableListOf<Pair<ByteArray, String>>()
    val shared = mutableListOf<Pair<ByteArray, String>>()
    val savedImages = mutableListOf<Triple<ByteArray, Int, String>>()
    val sharedImages = mutableListOf<Triple<ByteArray, Int, String>>()
    val prints = mutableListOf<PrintCall>()

    override suspend fun pickPhotos() = gallery
    override suspend fun takePhoto() = camera
    override suspend fun loadPrintImage(photo: ProjectPhoto): PhotoImage? = if (imageOk) PhotoImage(100, 130, byteArrayOf(1, 2, 3)) else null
    override suspend fun discoverPrinters() = discovery
    override fun openPrintServiceSettings() = settingsOpens
    var throwOnSave: Exception? = null
    override suspend fun savePdf(pdf: ByteArray, fileName: String): GatewayResult {
        throwOnSave?.let { throw it }
        return result.also { saved += pdf to fileName }
    }
    override suspend fun sharePdf(pdf: ByteArray, fileName: String) = result.also { shared += pdf to fileName }
    override suspend fun saveSheetImage(pdf: ByteArray, pageIndex: Int, fileName: String) = result.also { savedImages += Triple(pdf, pageIndex, fileName) }
    override suspend fun shareSheetImage(pdf: ByteArray, pageIndex: Int, fileName: String) = result.also { sharedImages += Triple(pdf, pageIndex, fileName) }
    override suspend fun print(pdf: ByteArray, jobName: String, request: PrintRequest) = result.also { prints += PrintCall(pdf, jobName, request) }
}

@OptIn(ExperimentalCoroutinesApi::class)
class AppControllerTest {
    private var idCounter = 0
    private var now = 1_000L
    private val ids = com.photoprint.pro.domain.project.IdGenerator { "id${++idCounter}" }
    private val time = com.photoprint.pro.domain.project.TimeSource { now }
    private val gateway = FakeGateway()
    private val projectRepo = InMemoryProjectRepository()
    private val templateRepo = InMemoryTemplateRepository()
    private val settingsRepo = InMemorySettingsRepository()

    private fun TestScope.controller(): AppController {
        val dispatcher = StandardTestDispatcher(testScheduler)
        val scope = kotlinx.coroutines.CoroutineScope(dispatcher)
        return AppController(
            nav = NavStack(),
            session = PrintSession(ids = ids, settings = AppSettings()),
            projectRepository = projectRepo,
            projectService = ProjectService(projectRepo, ids, time),
            templateRepository = templateRepo,
            settingsRepository = settingsRepo,
            gateway = gateway,
            ids = ids,
            time = time,
            scope = scope,
            cpu = dispatcher,
        )
    }

    private fun photos(vararg names: String) = names.map { PickedPhoto("ref/$it", 1200, 1600) }

    private fun TestScope.ready(copies: Int = 1, rotation: Boolean = false): AppController {
        val c = controller()
        gateway.gallery = photos("a")
        c.newPrint()
        c.addFromGallery()
        advanceUntilIdle()
        c.session.setAllowRotation(rotation)
        c.session.setCopies(copies)
        return c
    }

    private fun AppController.msg() = message.value?.kind

    private fun ByteArray.text() = String(this, Charsets.ISO_8859_1)

    // ---- starting and photos -------------------------------------------------------------

    @Test
    fun `new print starts clean and opens photo selection`() = runTest {
        val c = controller()
        c.newPrint()
        assertEquals(Route.SelectPhotos, c.nav.current)
        assertTrue(!c.session.state.value.hasPhotos)
    }

    @Test
    fun `quick print and templates preset the sizes`() = runTest {
        val c = controller()
        c.quickPrint(QuickPrints.twoByTwo)
        assertEquals(51.0, c.session.state.value.photoSize.widthMm)
        c.useTemplate(BuiltInTemplates.passportA4)
        assertEquals(210.0, c.session.state.value.paperSize.widthMm)
        assertEquals(4, c.builtInTemplates.size)
    }

    @Test
    fun `gallery camera and replace feed the session`() = runTest {
        val c = controller()
        c.newPrint()
        gateway.gallery = photos("a", "b")
        c.addFromGallery()
        advanceUntilIdle()
        assertEquals(2, c.session.state.value.photos.size)
        gateway.camera = PickedPhoto("cam/1", 3000, 4000)
        c.addFromCamera()
        advanceUntilIdle()
        assertEquals(3, c.session.state.value.photos.size)
        gateway.camera = null // user cancelled
        c.addFromCamera()
        advanceUntilIdle()
        assertEquals(3, c.session.state.value.photos.size)
        gateway.gallery = listOf(PickedPhoto("new", 10, 10))
        c.replacePhoto(c.session.state.value.photos[0].photoId)
        advanceUntilIdle()
        assertEquals("new", c.session.state.value.photos[0].sourceRef)
        gateway.gallery = emptyList() // cancelled picker
        c.replacePhoto(c.session.state.value.photos[0].photoId)
        advanceUntilIdle()
        assertEquals("new", c.session.state.value.photos[0].sourceRef)
    }

    // ---- saving the project --------------------------------------------------------------

    @Test
    fun `persist creates once then updates the same project`() = runTest {
        val c = ready()
        val first = assertNotNull(c.persist())
        assertEquals(first.id, c.session.state.value.projectId)
        assertEquals("Passport on 4 × 6 inch", first.name)
        now = 5_000
        c.session.setCopies(4)
        val second = assertNotNull(c.persist())
        assertEquals(first.id, second.id)
        assertEquals(first.createdAtMillis, second.createdAtMillis)
        assertEquals(5_000L, second.modifiedAtMillis)
        advanceUntilIdle()
        assertEquals(1, c.projects.value.size)
    }

    @Test
    fun `persist skips the write when nothing changed`() = runTest {
        val c = ready()
        val first = c.persist()!!
        now = 9_000
        val again = c.persist()!!
        assertEquals(first.modifiedAtMillis, again.modifiedAtMillis, "modified time is not bumped by a no-op save")
    }

    @Test
    fun `persist does nothing without photos`() = runTest {
        val c = controller()
        c.newPrint()
        assertNull(c.persist())
        advanceUntilIdle()
        assertTrue(c.projects.value.isEmpty())
    }

    @Test
    fun `autosave saves after changes settle`() = runTest {
        val c = ready()
        val job = c.startAutosave(debounceMillis = 500)
        advanceTimeBy(100)
        c.session.setCopies(2)
        advanceTimeBy(200)
        c.session.setCopies(3)
        advanceTimeBy(300)
        assertTrue(c.projects.value.isEmpty(), "still within the debounce window")
        advanceTimeBy(1_000)
        advanceUntilIdle()
        assertEquals(1, c.projects.value.size)
        assertEquals(3, c.projects.value.single().photos.single().copies)
        job.cancel()
    }

    // ---- export --------------------------------------------------------------------------

    @Test
    fun `save pdf builds one page per sheet and names the file`() = runTest {
        val c = ready(copies = 7) // 6 per sheet upright → 2 sheets
        c.session.setName("My: set/1")
        c.savePdf()
        advanceUntilIdle()
        val (pdf, name) = gateway.saved.single()
        assertEquals("My set1.pdf", name, "characters that are unsafe in file names are removed")
        assertTrue(pdf.text().startsWith("%PDF-"))
        assertTrue("/Count 2" in pdf.text())
        assertEquals(MessageKind.PDF_SAVED, c.msg())
        assertNull(c.busy.value)
    }

    @Test
    fun `share pdf and the image variants use the current sheet`() = runTest {
        val c = ready(copies = 7)
        c.session.setSheet(1)
        c.sharePdf(); advanceUntilIdle()
        assertEquals(MessageKind.PDF_SHARED, c.msg())
        assertEquals(1, gateway.shared.size)
        c.saveImage(); advanceUntilIdle()
        assertEquals(1, gateway.savedImages.single().second)
        assertEquals(MessageKind.IMAGE_SAVED, c.msg())
        c.shareImage(); advanceUntilIdle()
        assertEquals(1, gateway.sharedImages.single().second)
        assertEquals(MessageKind.IMAGE_SHARED, c.msg())
    }

    @Test
    fun `an unreadable photo stops the export before anything is saved`() = runTest {
        val c = ready()
        gateway.imageOk = false
        c.savePdf(); advanceUntilIdle()
        assertEquals(MessageKind.IMAGE_UNREADABLE, c.msg())
        assertTrue(gateway.saved.isEmpty())
        assertNull(c.busy.value, "busy indicator is cleared on failure")
    }

    @Test
    fun `an exception from the platform becomes a message and clears the busy flag`() = runTest {
        val c = ready()
        gateway.throwOnSave = IllegalStateException("disk exploded")
        c.savePdf(); advanceUntilIdle()
        assertEquals(MessageKind.UNKNOWN, c.msg())
        assertNull(c.busy.value)
        // The controller is still usable afterwards.
        gateway.throwOnSave = null
        c.savePdf(); advanceUntilIdle()
        assertEquals(MessageKind.PDF_SAVED, c.msg())
    }

    @Test
    fun `export with no photos or an impossible layout explains why`() = runTest {
        val c = controller()
        c.newPrint()
        c.savePdf(); advanceUntilIdle()
        assertEquals(MessageKind.NO_PHOTOS_YET, c.msg())
        gateway.gallery = photos("a")
        c.addFromGallery(); advanceUntilIdle()
        c.session.setPhotoSize(PhotoSize("huge", 300.0, 400.0))
        c.savePdf(); advanceUntilIdle()
        assertEquals(MessageKind.LAYOUT_IMPOSSIBLE, c.msg())
        assertTrue(gateway.saved.isEmpty())
    }

    @Test
    fun `storage and permission failures map to friendly messages and cancel is silent`() = runTest {
        val c = ready()
        gateway.result = GatewayResult.Failed(GatewayFailure.STORAGE_FAILED)
        c.savePdf(); advanceUntilIdle()
        assertEquals(MessageKind.STORAGE_FAILED, c.msg())
        gateway.result = GatewayResult.Failed(GatewayFailure.PERMISSION_DENIED)
        c.savePdf(); advanceUntilIdle()
        assertEquals(MessageKind.PERMISSION_DENIED, c.msg())
        gateway.result = GatewayResult.Failed(GatewayFailure.NOT_AVAILABLE_YET)
        c.savePdf(); advanceUntilIdle()
        assertEquals(MessageKind.NOT_AVAILABLE_YET, c.msg())
        c.consumeMessage(c.message.value!!.id)
        gateway.result = GatewayResult.Cancelled
        c.savePdf(); advanceUntilIdle()
        assertNull(c.message.value, "backing out of a dialog is not an error")
    }

    @Test
    fun `messages are consumed by id so a newer one is not dropped`() = runTest {
        val c = ready()
        c.savePdf(); advanceUntilIdle()
        val firstId = c.message.value!!.id
        c.savePdf(); advanceUntilIdle()
        c.consumeMessage(firstId)
        assertNotNull(c.message.value, "stale consume does not clear the newer message")
        c.consumeMessage(c.message.value!!.id)
        assertNull(c.message.value)
    }

    // ---- printing ------------------------------------------------------------------------

    @Test
    fun `print sends all sheets and the print settings then saves the project`() = runTest {
        val c = ready(copies = 7)
        c.session.setPrintSettings(c.session.state.value.printSettings.copy(copies = 2, borderless = true))
        c.print(testOnly = false); advanceUntilIdle()
        val call = gateway.prints.single()
        assertTrue("/Count 2" in call.pdf.text())
        assertEquals(102.0, call.request.paperWidthMm)
        assertEquals(152.0, call.request.paperHeightMm)
        assertEquals(2, call.request.copies)
        assertTrue(call.request.borderlessRequested)
        assertEquals(MessageKind.PRINT_SENT, c.msg())
        advanceUntilIdle()
        assertEquals(1, c.projects.value.size, "a printed job is kept so it can be reprinted")
    }

    @Test
    fun `test print sends only the first sheet and does not save`() = runTest {
        val c = ready(copies = 20)
        c.print(testOnly = true); advanceUntilIdle()
        val call = gateway.prints.single()
        assertTrue("/Count 1" in call.pdf.text())
        assertTrue(call.jobName.endsWith("(test)"))
        assertTrue(c.projects.value.isEmpty())
    }

    @Test
    fun `a cancelled or failed print does not save the project`() = runTest {
        val c = ready()
        gateway.result = GatewayResult.Cancelled
        c.print(false); advanceUntilIdle()
        gateway.result = GatewayResult.Failed(GatewayFailure.NO_PRINT_SERVICE)
        c.print(false); advanceUntilIdle()
        assertEquals(MessageKind.NO_PRINT_SERVICE, c.msg())
        assertTrue(c.projects.value.isEmpty())
    }

    @Test
    fun `calibration page prints at the paper size and refuses paper that is too small`() = runTest {
        val c = ready()
        c.printCalibration(); advanceUntilIdle()
        val call = gateway.prints.single()
        assertTrue("/MediaBox [0 0 289.1339 430.8661]" in call.pdf.text())
        assertTrue("100 mm" in call.pdf.text())
        gateway.prints.clear()
        c.session.setPaperSize(PaperSize("tiny", 80.0, 120.0))
        c.printCalibration(); advanceUntilIdle()
        assertTrue(gateway.prints.isEmpty())
        assertEquals(MessageKind.LAYOUT_IMPOSSIBLE, c.msg())
    }

    // ---- printers and readiness ----------------------------------------------------------

    @Test
    fun `printer discovery states`() = runTest {
        val c = controller()
        assertEquals(PrinterListState.Idle, c.printers.value)
        gateway.discovery = PrinterDiscovery.SystemDialogOnly
        c.refreshPrinters(); advanceUntilIdle()
        assertEquals(PrinterListState.SystemDialogOnly, c.printers.value)
        gateway.discovery = PrinterDiscovery.Failed(GatewayFailure.PERMISSION_DENIED)
        c.refreshPrinters(); advanceUntilIdle()
        assertEquals(PrinterListState.Failed(GatewayFailure.PERMISSION_DENIED), c.printers.value)
        val p = DiscoveredPrinter(PrinterInfo("e1", "Epson L3250", PrinterStatus.READY, "Wi-Fi"), PrinterCapabilities())
        gateway.discovery = PrinterDiscovery.Found(listOf(p))
        c.refreshPrinters(); advanceUntilIdle()
        assertEquals(PrinterListState.Loaded(listOf(p)), c.printers.value)
    }

    @Test
    fun `add printer reports when the settings screen cannot be opened`() = runTest {
        val c = controller()
        c.openAddPrinter()
        assertNull(c.message.value)
        gateway.settingsOpens = false
        c.openAddPrinter()
        assertEquals(MessageKind.NO_PRINT_SERVICE, c.msg())
    }

    @Test
    fun `readiness with system dialog printer selection is informational`() = runTest {
        val c = ready()
        gateway.discovery = PrinterDiscovery.SystemDialogOnly
        c.refreshPrinters(); advanceUntilIdle()
        val r = c.readiness()
        assertTrue(r.canPrint)
        assertTrue(r.items.any { it is ReadinessItem.PrinterChosenInPrintDialog })
    }

    @Test
    fun `readiness with a listed printer checks its state and capabilities`() = runTest {
        val c = ready()
        val offline = DiscoveredPrinter(PrinterInfo("hp", "HP Smart Tank", PrinterStatus.OFFLINE, "Wi-Fi"), PrinterCapabilities(scalingControl = Availability.SUPPORTED))
        val online = DiscoveredPrinter(PrinterInfo("ep", "Epson L3250", PrinterStatus.READY, "Wi-Fi"), PrinterCapabilities(scalingControl = Availability.SUPPORTED))
        gateway.discovery = PrinterDiscovery.Found(listOf(offline, online))
        c.refreshPrinters(); advanceUntilIdle()

        assertFalse(c.readiness().canPrint, "listed but none chosen: a printer must be selected")
        c.selectPrinter("hp")
        assertFalse(c.readiness().canPrint, "offline printer blocks")
        c.selectPrinter("ep")
        val r = c.readiness()
        assertTrue(r.canPrint)
        assertTrue(r.items.any { it is ReadinessItem.ScalingActualSize })
        c.session.setPrintSettings(c.session.state.value.printSettings.copy(scaling = ScalingMode.FIT_TO_PAGE))
        assertTrue(c.readiness().items.any { it is ReadinessItem.ScalingWillResize })
    }

    // ---- projects, templates, settings ---------------------------------------------------

    @Test
    fun `open and reprint navigate to the right step`() = runTest {
        val c = ready()
        val p = c.persist()!!
        c.session.startNew(AppSettings())
        c.openProject(p.id); advanceUntilIdle()
        assertEquals(Route.Preview, c.nav.current)
        assertEquals(p.id, c.session.state.value.projectId)
        c.reprint(p.id); advanceUntilIdle()
        assertEquals(Route.FinalCheck, c.nav.current)
        c.openProject("gone"); advanceUntilIdle()
        assertEquals(MessageKind.PROJECT_NOT_FOUND, c.msg())
        assertEquals(Route.FinalCheck, c.nav.current, "no navigation on failure")
    }

    @Test
    fun `rename duplicate delete report their outcome`() = runTest {
        val c = ready()
        val p = c.persist()!!
        c.renameProject(p.id, "  "); advanceUntilIdle()
        assertEquals(MessageKind.NAME_INVALID, c.msg())
        c.renameProject(p.id, "Visa"); advanceUntilIdle()
        assertEquals(MessageKind.PROJECT_RENAMED, c.msg())
        c.duplicateProject(p.id); advanceUntilIdle()
        assertEquals(MessageKind.PROJECT_DUPLICATED, c.msg())
        assertEquals(2, c.projects.value.size)
        c.deleteProject(p.id); advanceUntilIdle()
        assertEquals(MessageKind.PROJECT_DELETED, c.msg())
        assertEquals(1, c.projects.value.size)
        c.deleteProject(p.id); advanceUntilIdle()
        assertEquals(MessageKind.PROJECT_NOT_FOUND, c.msg())
    }

    @Test
    fun `saving a template keeps layout settings but no photos`() = runTest {
        val c = ready()
        c.session.setMarginAll(5.0)
        c.saveCurrentAsTemplate("   "); advanceUntilIdle()
        assertEquals(MessageKind.NAME_INVALID, c.msg())
        c.saveCurrentAsTemplate("My Studio Template"); advanceUntilIdle()
        assertEquals(MessageKind.TEMPLATE_SAVED, c.msg())
        val t = c.userTemplates.value.single()
        assertEquals("My Studio Template", t.name)
        assertEquals(5.0, t.margin.topMm)
        assertFalse(t.builtIn)
        c.deleteUserTemplate(t.id); advanceUntilIdle()
        assertTrue(c.userTemplates.value.isEmpty())
    }

    @Test
    fun `settings changes flow back and new prints use them`() = runTest {
        val c = controller()
        c.updateSettings { it.copy(defaultMarginMm = 7.0, defaultPaperSize = PaperSize("A5", 148.0, 210.0)) }
        advanceUntilIdle()
        assertEquals(7.0, c.settings.value.defaultMarginMm)
        c.newPrint()
        assertEquals(7.0, c.session.state.value.margin.topMm)
        assertEquals(148.0, c.session.state.value.paperSize.widthMm)
    }

    @Test
    fun `pdf bytes are stable for the same job`() = runTest {
        val c = ready(copies = 7)
        c.savePdf(); advanceUntilIdle()
        c.savePdf(); advanceUntilIdle()
        assertContentEquals(gateway.saved[0].first, gateway.saved[1].first)
        assertNotEquals(0, gateway.saved[0].first.size)
        assertIs<com.photoprint.pro.domain.layout.LayoutPlan>(c.session.state.value.plan)
    }
}
