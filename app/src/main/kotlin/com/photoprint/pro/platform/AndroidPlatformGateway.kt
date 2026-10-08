package com.photoprint.pro.platform

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.provider.Settings
import androidx.core.content.FileProvider
import com.photoprint.pro.domain.measurement.Measurement
import com.photoprint.pro.domain.model.ProjectPhoto
import com.photoprint.pro.domain.pdf.PhotoImage
import com.photoprint.pro.presentation.app.GatewayFailure
import com.photoprint.pro.presentation.app.GatewayResult
import com.photoprint.pro.presentation.app.PlatformGateway
import com.photoprint.pro.presentation.app.PrintRequest
import com.photoprint.pro.presentation.app.PrinterDiscovery
import com.photoprint.pro.presentation.editor.ColorAdjust
import com.photoprint.pro.presentation.session.PickedPhoto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Android implementation of [PlatformGateway]. It only moves bytes and launches system UI; every
 * decision (layout, PDF contents, what is allowed) is made in the pure-Kotlin modules.
 *
 * Public-API limits, by design: apps cannot list printers (the system print dialog does that), cannot preset
 * the number of copies, and cannot force borderless or "actual size". The values passed are hints.
 */
class AndroidPlatformGateway(
    private val appContext: Context,
    private val host: () -> AndroidHost?,
) : PlatformGateway {

    override val actualPxPerMm: Double
        get() = appContext.resources.displayMetrics.let { (it.xdpi.takeIf { d -> d > 0f } ?: (it.density * 160f)) / Measurement.MM_PER_INCH.toFloat() }.toDouble()

    private val photoDir get() = File(appContext.filesDir, "photos").apply { mkdirs() }
    private val sharedDir get() = File(appContext.cacheDir, "shared").apply { mkdirs() }

    // ---- photos --------------------------------------------------------------------------

    override suspend fun pickPhotos(): List<PickedPhoto> {
        val h = host() ?: return emptyList()
        val uris = h.pickImages(MAX_PICK)
        return withContext(Dispatchers.IO) { uris.mapNotNull { importUri(it) } }
    }

    private fun importUri(uri: android.net.Uri): PickedPhoto? {
        val dest = File(photoDir, UUID.randomUUID().toString())
        try {
            val input = appContext.contentResolver.openInputStream(uri) ?: return null
            input.use { src -> dest.outputStream().use { src.copyTo(it) } }
        } catch (_: Exception) {
            dest.delete()
            return null
        }
        return describe(dest)
    }

    private fun describe(file: File): PickedPhoto? {
        val size = PhotoFiles.uprightSize(file)
        if (size == null) {
            file.delete()
            return null
        }
        return PickedPhoto(file.absolutePath, size.first, size.second)
    }

    override suspend fun takePhoto(): PickedPhoto? {
        val h = host() ?: return null
        val out = File(appContext.filesDir, "camera").apply { mkdirs() }.let { File(it, "capture-${UUID.randomUUID()}.jpg") }
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", out)
        if (!h.takePicture(uri)) {
            out.delete()
            return null
        }
        return withContext(Dispatchers.IO) {
            // Move it next to the other photos so every photo lives in one place.
            val dest = File(photoDir, out.name)
            if (out.renameTo(dest) || out.copyTo(dest, overwrite = true).exists().also { out.delete() }) describe(dest) else null
        }
    }

    /**
     * A JPEG ready to embed. An untouched RGB or grey JPEG is passed through byte for byte (no
     * recompression); anything that needs work (EXIF rotation, brightness/contrast, other formats, CMYK)
     * is decoded at full resolution and re-encoded at quality 95.
     */
    override suspend fun loadPrintImage(photo: ProjectPhoto): PhotoImage? = withContext(Dispatchers.IO) {
        try {
            val file = File(photo.sourceRef)
            val crop = photo.crop
            val adjusted = !ColorAdjust.isIdentity(crop.brightness, crop.contrast)
            val bytes = file.readBytes()
            val components = if (PhotoFiles.isJpeg(bytes)) PhotoFiles.jpegComponents(bytes) else null
            if (!adjusted && PhotoFiles.exifRotation(file) == 0 && components != null && (components == 1 || components == 3)) {
                return@withContext PhotoImage(photo.widthPx, photo.heightPx, bytes, components)
            }
            val bmp = PhotoFiles.decodeUpright(file, maxSide = null) ?: return@withContext null
            val out = if (adjusted) applyAdjustments(bmp, crop.brightness, crop.contrast) else bmp
            val encoded = ByteArrayOutputStream().also { out.compress(Bitmap.CompressFormat.JPEG, 95, it) }.toByteArray()
            val result = PhotoImage(out.width, out.height, encoded, 3)
            if (out !== bmp) out.recycle()
            bmp.recycle()
            result
        } catch (_: Exception) {
            null
        } catch (_: OutOfMemoryError) {
            null
        }
    }

    private fun applyAdjustments(src: Bitmap, brightness: Double, contrast: Double): Bitmap {
        val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
        val paint = Paint().apply { colorFilter = ColorMatrixColorFilter(ColorMatrix(ColorAdjust.matrix(brightness, contrast))) }
        Canvas(out).drawBitmap(src, 0f, 0f, paint)
        return out
    }

    // ---- printers ------------------------------------------------------------------------

    /** Apps can't enumerate printers on stock Android; the system print dialog shows them. */
    override suspend fun discoverPrinters(): PrinterDiscovery = PrinterDiscovery.SystemDialogOnly

    override fun openPrintServiceSettings(): Boolean {
        val h = host() ?: return false
        return h.launch(Intent(Settings.ACTION_PRINT_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    // ---- files ---------------------------------------------------------------------------

    override suspend fun savePdf(pdf: ByteArray, fileName: String): GatewayResult = saveBytes(pdf, "application/pdf", fileName)

    override suspend fun sharePdf(pdf: ByteArray, fileName: String): GatewayResult = shareBytes(pdf, "application/pdf", fileName)

    override suspend fun saveSheetImage(pdf: ByteArray, pageIndex: Int, fileName: String): GatewayResult {
        val png = renderPage(pdf, pageIndex) ?: return GatewayResult.Failed(GatewayFailure.UNKNOWN)
        return saveBytes(png, "image/png", fileName)
    }

    override suspend fun shareSheetImage(pdf: ByteArray, pageIndex: Int, fileName: String): GatewayResult {
        val png = renderPage(pdf, pageIndex) ?: return GatewayResult.Failed(GatewayFailure.UNKNOWN)
        return shareBytes(png, "image/png", fileName)
    }

    private suspend fun saveBytes(bytes: ByteArray, mime: String, name: String): GatewayResult {
        val h = host() ?: return GatewayResult.Failed(GatewayFailure.UNKNOWN)
        val uri = h.createDocument(mime, name) ?: return GatewayResult.Cancelled
        return withContext(Dispatchers.IO) {
            try {
                val out = appContext.contentResolver.openOutputStream(uri) ?: return@withContext GatewayResult.Failed(GatewayFailure.STORAGE_FAILED)
                out.use { it.write(bytes) }
                GatewayResult.Done
            } catch (_: Exception) {
                GatewayResult.Failed(GatewayFailure.STORAGE_FAILED)
            }
        }
    }

    private suspend fun shareBytes(bytes: ByteArray, mime: String, name: String): GatewayResult {
        val h = host() ?: return GatewayResult.Failed(GatewayFailure.UNKNOWN)
        val file = withContext(Dispatchers.IO) {
            try {
                // Only the file being shared is kept in the cache.
                sharedDir.listFiles()?.forEach { it.delete() }
                File(sharedDir, name.replace('/', '_')).also { it.writeBytes(bytes) }
            } catch (_: Exception) {
                null
            }
        } ?: return GatewayResult.Failed(GatewayFailure.STORAGE_FAILED)
        val uri = FileProvider.getUriForFile(appContext, "${appContext.packageName}.files", file)
        val send = Intent(Intent.ACTION_SEND)
            .setType(mime)
            .putExtra(Intent.EXTRA_STREAM, uri)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        return if (h.launch(chooser)) GatewayResult.Done else GatewayResult.Failed(GatewayFailure.UNKNOWN)
    }

    /** Rasterises one PDF page at 300 dpi to PNG, for "save/share as image". */
    private suspend fun renderPage(pdf: ByteArray, index: Int): ByteArray? = withContext(Dispatchers.IO) {
        val tmp = File(appContext.cacheDir, "render-${UUID.randomUUID()}.pdf")
        try {
            tmp.writeBytes(pdf)
            ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { renderer ->
                    renderer.openPage(index.coerceIn(0, renderer.pageCount - 1)).use { page ->
                        val scale = 300f / 72f
                        val bmp = Bitmap.createBitmap((page.width * scale).roundToInt(), (page.height * scale).roundToInt(), Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(android.graphics.Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_PRINT)
                        ByteArrayOutputStream().also { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
                    }
                }
            }
        } catch (_: Exception) {
            null
        } finally {
            tmp.delete()
        }
    }

    // ---- printing ------------------------------------------------------------------------

    override suspend fun print(pdf: ByteArray, jobName: String, request: PrintRequest): GatewayResult = withContext(Dispatchers.Main) {
        val h = host() ?: return@withContext GatewayResult.Failed(GatewayFailure.UNKNOWN)
        val pm = h.activityContext.getSystemService(Context.PRINT_SERVICE) as? PrintManager
            ?: return@withContext GatewayResult.Failed(GatewayFailure.NO_PRINT_SERVICE)
        val w = Measurement.mmToMils(request.paperWidthMm).roundToInt()
        val ht = Measurement.mmToMils(request.paperHeightMm).roundToInt()
        // MediaSize ids are free-form; the print service matches by dimensions.
        val portrait = PrintAttributes.MediaSize("PHOTOPRINT_${w}x$ht", "${request.paperWidthMm.roundToInt()} × ${request.paperHeightMm.roundToInt()} mm", minOf(w, ht), maxOf(w, ht))
        val attributes = PrintAttributes.Builder()
            .setMediaSize(if (w > ht) portrait.asLandscape() else portrait)
            // The PDF already contains the margins the user chose; ask the system not to add its own.
            .setMinMargins(PrintAttributes.Margins.NO_MARGINS)
            .setColorMode(if (request.color) PrintAttributes.COLOR_MODE_COLOR else PrintAttributes.COLOR_MODE_MONOCHROME)
            .build()
        pm.print(jobName, PdfBytesAdapter(pdf, jobName), attributes)
        // The system dialog is now showing; the user may still cancel there.
        GatewayResult.Done
    }

    /** Streams an in-memory PDF to the print spooler. */
    private class PdfBytesAdapter(private val pdf: ByteArray, private val name: String) : PrintDocumentAdapter() {
        override fun onLayout(old: PrintAttributes?, new: PrintAttributes?, cancel: CancellationSignal?, callback: LayoutResultCallback, extras: Bundle?) {
            if (cancel?.isCanceled == true) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder("$name.pdf")
                .setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT)
                .setPageCount(PrintDocumentInfo.PAGE_COUNT_UNKNOWN)
                .build()
            callback.onLayoutFinished(info, true)
        }

        override fun onWrite(pages: Array<out PageRange>?, destination: ParcelFileDescriptor, cancel: CancellationSignal?, callback: WriteResultCallback) {
            Thread {
                try {
                    FileOutputStream(destination.fileDescriptor).use { it.write(pdf) }
                    callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
                } catch (e: Exception) {
                    callback.onWriteFailed(e.message)
                }
            }.start()
        }
    }

    private companion object {
        const val MAX_PICK = 50
    }
}
