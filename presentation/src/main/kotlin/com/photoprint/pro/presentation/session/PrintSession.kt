package com.photoprint.pro.presentation.session

import com.photoprint.pro.domain.layout.LayoutEngine
import com.photoprint.pro.domain.layout.LayoutOutcome
import com.photoprint.pro.domain.layout.LayoutPlan
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.Margin
import com.photoprint.pro.domain.model.PaperSize
import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.domain.model.PrintProject
import com.photoprint.pro.domain.model.PrintTemplate
import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.domain.model.Spacing
import com.photoprint.pro.domain.model.toLayoutRequest
import com.photoprint.pro.domain.printing.PrintSettings
import com.photoprint.pro.domain.project.IdGenerator
import com.photoprint.pro.presentation.editor.AspectOption
import com.photoprint.pro.presentation.editor.CropEditing
import com.photoprint.pro.presentation.settings.AppSettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A photo returned by the picker/camera: where it is stored and how big it is. */
data class PickedPhoto(val sourceRef: String, val widthPx: Int, val heightPx: Int)

/**
 * Everything the user has chosen so far. Screens only read this and call [PrintSession]; going back a
 * step never discards it. [layout] is recomputed with every change, so the preview, PDF and print all
 * read the same plan.
 */
data class SessionState(
    val projectId: String?,
    val name: String,
    val photos: List<ProjectPhoto>,
    val photoSize: PhotoSize,
    val paperSize: PaperSize,
    val margin: Margin,
    val spacing: Spacing,
    val copies: Int,
    val autoFill: Boolean,
    val fillSheets: Int,
    val allowRotation: Boolean,
    val showCutLines: Boolean,
    val currentSheet: Int,
    val printerId: String?,
    val printSettings: PrintSettings,
    val editorAspect: AspectOption,
    val layout: LayoutOutcome<LayoutPlan>,
) {
    val plan: LayoutPlan? get() = (layout as? LayoutOutcome.Success)?.value
    val hasPhotos: Boolean get() = photos.isNotEmpty()

    fun photo(id: String): ProjectPhoto? = photos.firstOrNull { it.photoId == id }

    /** Same conversion the engine, PDF and print use. */
    fun toProject(id: String, nowMillis: Long, createdAtMillis: Long = nowMillis) = PrintProject(
        id = id,
        name = name,
        photos = photos,
        photoSize = photoSize,
        paperSize = paperSize,
        margin = margin,
        spacing = spacing,
        allowRotation = allowRotation,
        showCutLines = showCutLines,
        fillSheets = if (autoFill) fillSheets else null,
        printerId = printerId,
        createdAtMillis = createdAtMillis,
        modifiedAtMillis = nowMillis,
    )
}

/** Quick Print shortcut on the home screen: preset sizes with sensible margins. */
data class QuickPrint(val id: String, val photoSize: PhotoSize, val paperSize: PaperSize, val marginMm: Double, val spacingMm: Double)

object QuickPrints {
    val passport = QuickPrint("passport", PhotoSizePresets.Passport, PaperSizePresets.Photo4x6, 3.0, 2.0)
    val idPhoto = QuickPrint("id", PhotoSizePresets.IdPhoto, PaperSizePresets.Photo4x6, 3.0, 2.0)
    val twoByTwo = QuickPrint("2x2", PhotoSizePresets.TwoByTwoInch, PaperSizePresets.Photo4x6, 3.0, 2.0)

    /** One photo filling the whole 4×6 sheet: no margin, so use a printer's borderless mode if it has one. */
    val photo4x6 = QuickPrint("4x6", PhotoSize("4 × 6 inch", 102.0, 152.0), PaperSizePresets.Photo4x6, 0.0, 0.0)

    val all = listOf(passport, idPhoto, twoByTwo, photo4x6)
}

/**
 * Mutable holder for the work in progress. Pure Kotlin: the UI observes [state]; an app-level
 * observer can autosave it as a project.
 */
class PrintSession(
    private val engine: LayoutEngine = LayoutEngine(),
    private val ids: IdGenerator,
    settings: AppSettings = AppSettings(),
) {
    private val _state = MutableStateFlow(initial(settings))
    val state: StateFlow<SessionState> get() = _state

    private fun initial(s: AppSettings) = withLayout(
        SessionState(
            projectId = null,
            name = "",
            photos = emptyList(),
            photoSize = s.defaultPhotoSize,
            paperSize = s.defaultPaperSize,
            margin = Margin.uniform(s.defaultMarginMm),
            spacing = Spacing.uniform(s.defaultSpacingMm),
            copies = 1,
            autoFill = false,
            fillSheets = 1,
            allowRotation = true,
            showCutLines = false,
            currentSheet = 0,
            printerId = null,
            printSettings = PrintSettings(quality = s.quality),
            editorAspect = aspectOf(s.defaultPhotoSize),
            layout = LayoutOutcome.Failure(com.photoprint.pro.domain.layout.LayoutError.NoPhotos),
        ),
    )

    private fun aspectOf(p: PhotoSize) = AspectOption.of(p.widthMm, p.heightMm)

    private fun withLayout(s: SessionState): SessionState {
        val project = s.toProject("preview", 0)
        val outcome = engine.calculate(project.toLayoutRequest())
        val sheets = (outcome as? LayoutOutcome.Success)?.value?.sheetCount ?: 1
        return s.copy(layout = outcome, currentSheet = s.currentSheet.coerceIn(0, sheets - 1))
    }

    private inline fun update(block: (SessionState) -> SessionState) {
        _state.value = withLayout(block(_state.value))
    }

    // ---- starting points -----------------------------------------------------------------

    fun startNew(settings: AppSettings) {
        _state.value = initial(settings)
    }

    fun startQuickPrint(q: QuickPrint, settings: AppSettings) {
        startNew(settings)
        update { it.copy(photoSize = q.photoSize, paperSize = q.paperSize, margin = Margin.uniform(q.marginMm), spacing = Spacing.uniform(q.spacingMm), editorAspect = aspectOf(q.photoSize)) }
    }

    fun startFromTemplate(t: PrintTemplate, settings: AppSettings) {
        startNew(settings)
        update {
            it.copy(
                photoSize = t.photoSize, paperSize = t.paperSize, margin = t.margin, spacing = t.spacing,
                allowRotation = t.allowRotation, showCutLines = t.showCutLines, editorAspect = aspectOf(t.photoSize),
            )
        }
    }

    fun load(p: PrintProject) {
        _state.value = withLayout(
            SessionState(
                projectId = p.id, name = p.name, photos = p.photos, photoSize = p.photoSize, paperSize = p.paperSize,
                margin = p.margin, spacing = p.spacing,
                copies = p.photos.firstOrNull()?.copies ?: 1,
                autoFill = p.fillSheets != null, fillSheets = p.fillSheets ?: 1,
                allowRotation = p.allowRotation, showCutLines = p.showCutLines,
                currentSheet = 0, printerId = p.printerId, printSettings = PrintSettings(),
                editorAspect = aspectOf(p.photoSize),
                layout = LayoutOutcome.Failure(com.photoprint.pro.domain.layout.LayoutError.NoPhotos),
            ),
        )
    }

    // ---- photos --------------------------------------------------------------------------

    /** Adds photos, skipping any already present (same source). Each keeps the session's current copies. */
    fun addPhotos(picked: List<PickedPhoto>) = update { s ->
        val known = s.photos.map { it.sourceRef }.toSet()
        val fresh = picked.filter { it.sourceRef !in known }.distinctBy { it.sourceRef }
            .map { ProjectPhoto(ids.next(), it.sourceRef, it.widthPx, it.heightPx, copies = s.copies) }
        s.copy(photos = s.photos + fresh)
    }

    fun removePhoto(id: String) = update { it.copy(photos = it.photos.filterNot { p -> p.photoId == id }) }

    fun movePhoto(id: String, offset: Int) = update { s ->
        val from = s.photos.indexOfFirst { it.photoId == id }
        if (from < 0) return@update s
        val to = (from + offset).coerceIn(0, s.photos.lastIndex)
        if (to == from) return@update s
        val list = s.photos.toMutableList()
        list.add(to, list.removeAt(from))
        s.copy(photos = list)
    }

    /** Swap the image behind a frame; the crop starts fresh because the old one fit another picture. */
    fun replacePhoto(id: String, with: PickedPhoto) = update { s ->
        s.copy(photos = s.photos.map { if (it.photoId == id) it.copy(sourceRef = with.sourceRef, widthPx = with.widthPx, heightPx = with.heightPx, crop = CropState()) else it })
    }

    // ---- sizes and layout settings -------------------------------------------------------

    fun setPhotoSize(size: PhotoSize) = update { s ->
        // A different shape means the old pan/zoom may no longer be valid: re-clamp every crop to the new frame.
        val frame = CropEditing.Frame(size.widthMm, size.heightMm)
        s.copy(
            photoSize = size,
            editorAspect = aspectOf(size),
            photos = s.photos.map { it.copy(crop = CropEditing.clamp(it.crop, frame, it.widthPx, it.heightPx)) },
        )
    }

    fun setPaperSize(size: PaperSize) = update { it.copy(paperSize = size) }

    fun setMarginAll(mm: Double) = update { it.copy(margin = Margin.uniform(mm.coerceIn(0.0, AppSettings.MAX_MARGIN_MM))) }

    fun setSpacingAll(mm: Double) = update { it.copy(spacing = Spacing.uniform(mm.coerceIn(0.0, AppSettings.MAX_SPACING_MM))) }

    /** Copies of each photo; applies to every photo in the session. */
    fun setCopies(n: Int) = update { s ->
        val c = n.coerceIn(1, MAX_COPIES)
        s.copy(copies = c, photos = s.photos.map { it.copy(copies = c) })
    }

    fun setAutoFill(on: Boolean) = update { it.copy(autoFill = on) }

    fun setFillSheets(n: Int) = update { it.copy(fillSheets = n.coerceIn(1, MAX_FILL_SHEETS)) }

    fun setAllowRotation(on: Boolean) = update { it.copy(allowRotation = on) }

    fun setShowCutLines(on: Boolean) = update { it.copy(showCutLines = on) }

    fun setEditorAspect(a: AspectOption) = update { it.copy(editorAspect = a) }

    // ---- per-photo editing ---------------------------------------------------------------

    fun setCrop(photoId: String, crop: CropState) = update { s ->
        s.copy(photos = s.photos.map { if (it.photoId == photoId) it.copy(crop = crop) else it })
    }

    // ---- printing ------------------------------------------------------------------------

    fun setSheet(index: Int) = update { it.copy(currentSheet = index) }

    fun setPrinter(id: String?) = update { it.copy(printerId = id) }

    fun setPrintSettings(s: PrintSettings) = update { it.copy(printSettings = s) }

    fun setName(name: String) = update { it.copy(name = name) }

    /** Called after the first save, so later saves update the same project. */
    fun assignProject(id: String, name: String) = update { it.copy(projectId = id, name = name) }

    companion object {
        const val MAX_COPIES = 500
        const val MAX_FILL_SHEETS = 50

        /** A readable default like "Passport on 4 × 6 inch". */
        fun suggestedName(s: SessionState) = "${s.photoSize.name} on ${s.paperSize.name}"
    }
}
