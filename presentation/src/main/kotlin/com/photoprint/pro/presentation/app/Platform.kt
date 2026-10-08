package com.photoprint.pro.presentation.app

import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.domain.pdf.PhotoImage
import com.photoprint.pro.domain.printing.PrinterCapabilities
import com.photoprint.pro.domain.printing.PrinterInfo
import com.photoprint.pro.presentation.session.PickedPhoto

enum class GatewayFailure {
    NO_PRINT_SERVICE,
    PRINTER_UNAVAILABLE,
    STORAGE_FAILED,
    PERMISSION_DENIED,

    /** The platform layer has no implementation for this yet. Reported honestly, never faked as success. */
    NOT_AVAILABLE_YET,
    IMAGE_UNREADABLE,
    UNKNOWN,
}

sealed interface GatewayResult {
    data object Done : GatewayResult

    /** The user backed out (closed the picker or print dialog). Not an error. */
    data object Cancelled : GatewayResult

    data class Failed(val reason: GatewayFailure) : GatewayResult
}

data class DiscoveredPrinter(val info: PrinterInfo, val capabilities: PrinterCapabilities)

sealed interface PrinterDiscovery {
    data class Found(val printers: List<DiscoveredPrinter>) : PrinterDiscovery

    /**
     * The normal case on stock Android: apps cannot list printers. The system print dialog shows the
     * available printers (and "Add printer") when printing starts.
     */
    data object SystemDialogOnly : PrinterDiscovery

    data class Failed(val reason: GatewayFailure) : PrinterDiscovery
}

/**
 * Everything that needs the operating system. Kept deliberately small: PDF generation, layout and all
 * decisions stay in pure Kotlin; implementations only move bytes and launch system UI.
 */
interface PlatformGateway {
    /** Physical pixels per millimetre of this screen, so "100 %" in the preview is true size. */
    val actualPxPerMm: Double

    suspend fun pickPhotos(): List<PickedPhoto>

    suspend fun takePhoto(): PickedPhoto?

    /** A high-quality JPEG of the photo with brightness/contrast applied, ready to embed unchanged. */
    suspend fun loadPrintImage(photo: ProjectPhoto): PhotoImage?

    suspend fun discoverPrinters(): PrinterDiscovery

    /** Opens the system screen for adding a printer/print service. @return false if it cannot be opened. */
    fun openPrintServiceSettings(): Boolean

    suspend fun savePdf(pdf: ByteArray, fileName: String): GatewayResult

    suspend fun sharePdf(pdf: ByteArray, fileName: String): GatewayResult

    /** Rasterise one PDF page and save it as an image. */
    suspend fun saveSheetImage(pdf: ByteArray, pageIndex: Int, fileName: String): GatewayResult

    suspend fun shareSheetImage(pdf: ByteArray, pageIndex: Int, fileName: String): GatewayResult

    /**
     * Hand [pdf] to the system print dialog. [request] carries what to pre-select; the dialog and the
     * printer still have the final say.
     */
    suspend fun print(pdf: ByteArray, jobName: String, request: PrintRequest): GatewayResult
}

/** Print-dialog hints. Anything the print service ignores simply is not applied. */
data class PrintRequest(
    val paperWidthMm: Double,
    val paperHeightMm: Double,
    val copies: Int,
    val color: Boolean,
    val borderlessRequested: Boolean,
    val printerId: String?,
)

enum class MessageKind {
    NO_PHOTOS_YET,
    LAYOUT_IMPOSSIBLE,
    IMAGE_UNREADABLE,
    PDF_FAILED,
    PDF_SAVED,
    PDF_SHARED,
    IMAGE_SAVED,
    IMAGE_SHARED,
    PRINT_SENT,
    NO_PRINT_SERVICE,
    PRINTER_UNAVAILABLE,
    STORAGE_FAILED,
    PERMISSION_DENIED,
    NOT_AVAILABLE_YET,
    PROJECT_SAVED,
    PROJECT_RENAMED,
    PROJECT_DUPLICATED,
    PROJECT_DELETED,
    PROJECT_NOT_FOUND,
    NAME_INVALID,
    TEMPLATE_SAVED,
    UNKNOWN,
}

data class UiMessage(val kind: MessageKind, val id: Long)

sealed interface PrinterListState {
    data object Idle : PrinterListState
    data object Loading : PrinterListState
    data class Loaded(val printers: List<DiscoveredPrinter>) : PrinterListState
    data object SystemDialogOnly : PrinterListState
    data class Failed(val reason: GatewayFailure) : PrinterListState
}

enum class BusyKind { BUILDING_PDF, PRINTING, SAVING }
