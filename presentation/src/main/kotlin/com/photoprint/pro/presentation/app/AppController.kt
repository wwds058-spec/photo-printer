package com.photoprint.pro.presentation.app

import com.photoprint.pro.domain.layout.LayoutOutcome
import com.photoprint.pro.domain.layout.LayoutPlan
import com.photoprint.pro.domain.model.BuiltInTemplates
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.PrintTemplate
import com.photoprint.pro.domain.pdf.PdfSheetWriter
import com.photoprint.pro.domain.printing.CalibrationOutcome
import com.photoprint.pro.domain.printing.CalibrationPage
import com.photoprint.pro.domain.printing.PrintReadiness
import com.photoprint.pro.domain.printing.PrinterCapabilities
import com.photoprint.pro.domain.printing.PrinterInfo
import com.photoprint.pro.domain.printing.ReadinessReport
import com.photoprint.pro.domain.project.IdGenerator
import com.photoprint.pro.domain.project.ProjectError
import com.photoprint.pro.domain.project.ProjectOutcome
import com.photoprint.pro.domain.project.ProjectRepository
import com.photoprint.pro.domain.project.ProjectService
import com.photoprint.pro.domain.project.TemplateRepository
import com.photoprint.pro.domain.project.TimeSource
import com.photoprint.pro.presentation.nav.NavStack
import com.photoprint.pro.presentation.nav.Route
import com.photoprint.pro.presentation.session.PrintSession
import com.photoprint.pro.presentation.session.QuickPrint
import com.photoprint.pro.presentation.settings.AppSettings
import com.photoprint.pro.presentation.settings.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * The app's brain, in pure Kotlin (the Android ViewModel just holds one of these). Screens read the
 * StateFlows and call these methods; nothing here touches Android or Compose.
 */
class AppController(
    val nav: NavStack,
    val session: PrintSession,
    private val projectRepository: ProjectRepository,
    private val projectService: ProjectService,
    private val templateRepository: TemplateRepository,
    private val settingsRepository: SettingsRepository,
    private val gateway: PlatformGateway,
    private val ids: IdGenerator,
    private val time: TimeSource,
    private val scope: CoroutineScope,
    private val cpu: CoroutineDispatcher = Dispatchers.Default,
    private val writer: PdfSheetWriter = PdfSheetWriter(),
) {
    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(scope, SharingStarted.Eagerly, AppSettings())
    val projects: StateFlow<List<PrintProject>> = projectRepository.observeAll().stateIn(scope, SharingStarted.Eagerly, emptyList())
    val userTemplates: StateFlow<List<PrintTemplate>> = templateRepository.observeUserTemplates().stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val _printers = MutableStateFlow<PrinterListState>(PrinterListState.Idle)
    val printers: StateFlow<PrinterListState> get() = _printers

    private val _busy = MutableStateFlow<BusyKind?>(null)
    val busy: StateFlow<BusyKind?> get() = _busy

    private val _message = MutableStateFlow<UiMessage?>(null)
    val message: StateFlow<UiMessage?> get() = _message

    val actualPxPerMm: Double get() = gateway.actualPxPerMm

    val builtInTemplates: List<PrintTemplate> get() = BuiltInTemplates.all

    private fun say(kind: MessageKind) {
        _message.value = UiMessage(kind, (_message.value?.id ?: 0) + 1)
    }

    fun consumeMessage(id: Long) {
        if (_message.value?.id == id) _message.value = null
    }

    // ---- starting a job ------------------------------------------------------------------

    fun newPrint() {
        session.startNew(settings.value)
        nav.push(Route.SelectPhotos)
    }

    fun quickPrint(q: QuickPrint) {
        session.startQuickPrint(q, settings.value)
        nav.push(Route.SelectPhotos)
    }

    fun useTemplate(t: PrintTemplate) {
        session.startFromTemplate(t, settings.value)
        nav.push(Route.SelectPhotos)
    }

    fun openProject(id: String) {
        scope.launch {
            val p = projectRepository.get(id)
            if (p == null) say(MessageKind.PROJECT_NOT_FOUND) else {
                session.load(p)
                nav.push(Route.Preview)
            }
        }
    }

    /** Reprint: open the saved project and go straight to the final check. */
    fun reprint(id: String) {
        scope.launch {
            val p = projectRepository.get(id)
            if (p == null) say(MessageKind.PROJECT_NOT_FOUND) else {
                session.load(p)
                nav.push(Route.FinalCheck)
            }
        }
    }

    // ---- photos --------------------------------------------------------------------------

    fun addFromGallery() {
        scope.launch { session.addPhotos(gateway.pickPhotos()) }
    }

    fun addFromCamera() {
        scope.launch { gateway.takePhoto()?.let { session.addPhotos(listOf(it)) } }
    }

    fun replacePhoto(photoId: String) {
        scope.launch { gateway.pickPhotos().firstOrNull()?.let { session.replacePhoto(photoId, it) } }
    }

    // ---- autosave ------------------------------------------------------------------------

    /** Saves the work in progress shortly after the last change. Call once; cancel the returned job to stop. */
    @OptIn(FlowPreview::class)
    fun startAutosave(debounceMillis: Long = 800): Job = scope.launch {
        session.state.debounce(debounceMillis).collect { if (it.hasPhotos) persist() }
    }

    /** Writes the current session as a project (creating it the first time). */
    suspend fun persist(): PrintProject? {
        val s = session.state.value
        if (!s.hasPhotos) return null
        val id = s.projectId ?: ids.next()
        val name = s.name.ifBlank { PrintSession.suggestedName(s) }
        val existing = s.projectId?.let { projectRepository.get(it) }
        val project = s.toProject(id, time.nowMillis(), existing?.createdAtMillis ?: time.nowMillis()).copy(name = name)
        // Skip the write (and the modified-time bump) when nothing changed.
        if (existing != null && existing.copy(modifiedAtMillis = project.modifiedAtMillis) == project) return existing
        projectRepository.upsert(project)
        if (s.projectId == null) session.assignProject(id, name)
        return project
    }

    // ---- printers ------------------------------------------------------------------------

    fun refreshPrinters() {
        scope.launch {
            _printers.value = PrinterListState.Loading
            _printers.value = when (val d = gateway.discoverPrinters()) {
                is PrinterDiscovery.Found -> PrinterListState.Loaded(d.printers)
                PrinterDiscovery.SystemDialogOnly -> PrinterListState.SystemDialogOnly
                is PrinterDiscovery.Failed -> PrinterListState.Failed(d.reason)
            }
        }
    }

    fun selectPrinter(id: String?) = session.setPrinter(id)

    fun openAddPrinter() {
        if (!gateway.openPrintServiceSettings()) say(MessageKind.NO_PRINT_SERVICE)
    }

    private fun selectedPrinter(): Pair<PrinterInfo, PrinterCapabilities>? {
        val loaded = _printers.value as? PrinterListState.Loaded ?: return null
        val id = session.state.value.printerId ?: return null
        return loaded.printers.firstOrNull { it.info.id == id }?.let { it.info to it.capabilities }
    }

    /** The READY TO PRINT report for the current work. */
    fun readiness(): ReadinessReport {
        val s = session.state.value
        val chosen = selectedPrinter()
        return PrintReadiness.evaluate(
            project = s.toProject("check", 0),
            printer = chosen?.first,
            capabilities = chosen?.second ?: PrinterCapabilities(),
            settings = s.printSettings,
            printerChosenBySystem = _printers.value !is PrinterListState.Loaded,
        )
    }

    // ---- export and print ----------------------------------------------------------------

    /** Builds the PDF for [plan] with the same layout the preview shows. Null (with a message) on failure. */
    private suspend fun buildPdf(plan: LayoutPlan): ByteArray? {
        val photos = session.state.value.photos
        val images = HashMap<String, com.photoprint.pro.domain.pdf.PhotoImage>()
        for (id in plan.sheets.flatMap { sh -> sh.placements.map { it.photoId } }.distinct()) {
            val p = photos.firstOrNull { it.photoId == id }
            val img = p?.let { gateway.loadPrintImage(it) }
            if (img == null) {
                say(MessageKind.IMAGE_UNREADABLE)
                return null
            }
            images[id] = img
        }
        return try {
            withContext(cpu) { ByteArrayOutputStream().also { writer.write(plan, { images[it] }, it) }.toByteArray() }
        } catch (_: Exception) {
            say(MessageKind.PDF_FAILED)
            null
        }
    }

    private fun currentPlan(): LayoutPlan? {
        val s = session.state.value
        if (!s.hasPhotos) { say(MessageKind.NO_PHOTOS_YET); return null }
        val plan = (s.layout as? LayoutOutcome.Success)?.value
        if (plan == null) say(MessageKind.LAYOUT_IMPOSSIBLE)
        return plan
    }

    private fun fileName(ext: String): String {
        val s = session.state.value
        val base = s.name.ifBlank { PrintSession.suggestedName(s) }.replace(Regex("[^A-Za-z0-9 _.-]"), "").trim().ifEmpty { "PhotoPrint" }
        return "$base.$ext"
    }

    private fun report(r: GatewayResult, ok: MessageKind) {
        when (r) {
            GatewayResult.Done -> say(ok)
            GatewayResult.Cancelled -> Unit
            is GatewayResult.Failed -> say(
                when (r.reason) {
                    GatewayFailure.NO_PRINT_SERVICE -> MessageKind.NO_PRINT_SERVICE
                    GatewayFailure.PRINTER_UNAVAILABLE -> MessageKind.PRINTER_UNAVAILABLE
                    GatewayFailure.STORAGE_FAILED -> MessageKind.STORAGE_FAILED
                    GatewayFailure.PERMISSION_DENIED -> MessageKind.PERMISSION_DENIED
                    GatewayFailure.NOT_AVAILABLE_YET -> MessageKind.NOT_AVAILABLE_YET
                    GatewayFailure.IMAGE_UNREADABLE -> MessageKind.IMAGE_UNREADABLE
                    GatewayFailure.UNKNOWN -> MessageKind.UNKNOWN
                },
            )
        }
    }

    /**
     * Runs [block] with the busy indicator on. Anything unexpected the platform throws becomes a friendly
     * message instead of crashing the app, and the indicator is always cleared.
     */
    private fun <T> withBusy(kind: BusyKind, block: suspend () -> T) {
        scope.launch {
            _busy.value = kind
            try {
                block()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                say(MessageKind.UNKNOWN)
            } finally {
                _busy.value = null
            }
        }
    }

    fun savePdf() = withBusy(BusyKind.SAVING) {
        val plan = currentPlan() ?: return@withBusy
        val pdf = buildPdf(plan) ?: return@withBusy
        report(gateway.savePdf(pdf, fileName("pdf")), MessageKind.PDF_SAVED)
    }

    fun sharePdf() = withBusy(BusyKind.SAVING) {
        val plan = currentPlan() ?: return@withBusy
        val pdf = buildPdf(plan) ?: return@withBusy
        report(gateway.sharePdf(pdf, fileName("pdf")), MessageKind.PDF_SHARED)
    }

    fun saveImage() = withBusy(BusyKind.SAVING) {
        val plan = currentPlan() ?: return@withBusy
        val pdf = buildPdf(plan) ?: return@withBusy
        report(gateway.saveSheetImage(pdf, session.state.value.currentSheet, fileName("png")), MessageKind.IMAGE_SAVED)
    }

    fun shareImage() = withBusy(BusyKind.SAVING) {
        val plan = currentPlan() ?: return@withBusy
        val pdf = buildPdf(plan) ?: return@withBusy
        report(gateway.shareSheetImage(pdf, session.state.value.currentSheet, fileName("png")), MessageKind.IMAGE_SHARED)
    }

    /** Sends the job to the system print dialog. [testOnly] prints just the first sheet. */
    fun print(testOnly: Boolean) = withBusy(BusyKind.PRINTING) {
        val full = currentPlan() ?: return@withBusy
        val plan = if (testOnly) PrintReadiness.testSheet(full) else full
        val pdf = buildPdf(plan) ?: return@withBusy
        val s = session.state.value
        val result = gateway.print(
            pdf,
            fileName("pdf").removeSuffix(".pdf") + if (testOnly) " (test)" else "",
            PrintRequest(
                paperWidthMm = s.paperSize.widthMm,
                paperHeightMm = s.paperSize.heightMm,
                copies = s.printSettings.copies,
                color = s.printSettings.colorMode == com.photoprint.pro.domain.printing.ColorMode.COLOR,
                borderlessRequested = s.printSettings.borderless,
                printerId = s.printerId,
            ),
        )
        report(result, MessageKind.PRINT_SENT)
        if (result == GatewayResult.Done && !testOnly) persist()
    }

    /** Prints the 100 mm / 50 mm calibration page for the current paper and photo size. */
    fun printCalibration() = withBusy(BusyKind.PRINTING) {
        val s = session.state.value
        val page = when (val c = CalibrationPage.build(s.paperSize, s.photoSize)) {
            is CalibrationOutcome.Page -> c
            is CalibrationOutcome.PaperTooSmall -> { say(MessageKind.LAYOUT_IMPOSSIBLE); return@withBusy }
        }
        val pdf = try {
            withContext(cpu) { ByteArrayOutputStream().also { writer.write(listOf(page.spec), { null }, it) }.toByteArray() }
        } catch (_: Exception) { say(MessageKind.PDF_FAILED); return@withBusy }
        report(
            gateway.print(pdf, "PHOTOPrint calibration", PrintRequest(s.paperSize.widthMm, s.paperSize.heightMm, 1, true, false, s.printerId)),
            MessageKind.PRINT_SENT,
        )
    }

    // ---- projects and templates ----------------------------------------------------------

    private fun <T> projectResult(o: ProjectOutcome<T>, ok: MessageKind) {
        when (o) {
            is ProjectOutcome.Success -> say(ok)
            is ProjectOutcome.Failure -> say(
                when (o.error) {
                    ProjectError.NOT_FOUND -> MessageKind.PROJECT_NOT_FOUND
                    ProjectError.BLANK_NAME, ProjectError.NAME_TOO_LONG -> MessageKind.NAME_INVALID
                    ProjectError.NO_PHOTOS -> MessageKind.NO_PHOTOS_YET
                },
            )
        }
    }

    fun renameProject(id: String, name: String) {
        scope.launch { projectResult(projectService.rename(id, name), MessageKind.PROJECT_RENAMED) }
    }

    fun duplicateProject(id: String) {
        scope.launch { projectResult(projectService.duplicate(id), MessageKind.PROJECT_DUPLICATED) }
    }

    fun deleteProject(id: String) {
        scope.launch { projectResult(projectService.delete(id), MessageKind.PROJECT_DELETED) }
    }

    /** "My Studio Template": the current layout settings, without any photos. */
    fun saveCurrentAsTemplate(name: String) {
        val clean = name.trim()
        if (clean.isEmpty() || clean.length > ProjectService.MAX_NAME) { say(MessageKind.NAME_INVALID); return }
        val s = session.state.value
        scope.launch {
            templateRepository.upsert(
                PrintTemplate(
                    id = ids.next(), name = clean, photoSize = s.photoSize, paperSize = s.paperSize, margin = s.margin,
                    spacing = s.spacing, allowRotation = s.allowRotation, showCutLines = s.showCutLines,
                ),
            )
            say(MessageKind.TEMPLATE_SAVED)
        }
    }

    fun deleteUserTemplate(id: String) {
        scope.launch { templateRepository.delete(id) }
    }

    // ---- settings ------------------------------------------------------------------------

    fun updateSettings(transform: (AppSettings) -> AppSettings) {
        scope.launch { settingsRepository.update(transform) }
    }
}
