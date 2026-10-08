package com.photoprint.pro.domain.pdf

import com.photoprint.pro.domain.layout.LayoutPlan
import java.io.OutputStream
import java.util.Locale

/** A JPEG ready to embed untouched (no recompression). [components] is 3 for RGB, 1 for grey. */
class PhotoImage(
    val widthPx: Int,
    val heightPx: Int,
    val jpegBytes: ByteArray,
    val components: Int = 3,
) {
    init {
        require(widthPx > 0 && heightPx > 0) { "Image has no pixels" }
        require(components == 1 || components == 3) { "Only grey or RGB JPEGs are supported" }
    }
}

class MissingImageException(val photoId: String) : IllegalStateException("No image supplied for photo '$photoId'")

/**
 * Writes sheets as a PDF. Pure JVM, deterministic (no timestamps), no Compose, no Android.
 *
 *  - One PDF page per sheet; the MediaBox is the exact paper size as real numbers, so there is no
 *    whole-point rounding of the page.
 *  - JPEGs are embedded as-is (DCTDecode); the PDF never recompresses photos.
 *  - Each frame clips its image, so crop/zoom never changes the frame's physical size.
 *  - `/ViewerPreferences /PrintScaling /None` asks PDF viewers to print at 100 %.
 *  - Frame positions come from [PdfPageSpec], which is derived from the same SheetLayout as the preview.
 */
class PdfSheetWriter {

    fun write(plan: LayoutPlan, images: (photoId: String) -> PhotoImage?, out: OutputStream) =
        write(plan.sheets.map { it.toPdfPageSpec() }, images, out)

    fun write(pages: List<PdfPageSpec>, images: (photoId: String) -> PhotoImage?, out: OutputStream) {
        require(pages.isNotEmpty()) { "A PDF needs at least one page" }

        // Resolve every image up front so a missing one fails before any bytes are written.
        val resolved = LinkedHashMap<String, PhotoImage>()
        for (p in pages) for (fr in p.frames) {
            if (fr.photoId !in resolved) resolved[fr.photoId] = images(fr.photoId) ?: throw MissingImageException(fr.photoId)
        }
        val imageObjectId = resolved.keys.withIndex().associate { (i, id) -> id to FIRST_IMAGE_ID + i }
        val firstPageId = FIRST_IMAGE_ID + resolved.size
        fun pageId(i: Int) = firstPageId + 2 * i
        fun contentId(i: Int) = firstPageId + 2 * i + 1

        val w = Counting(out)
        val offsets = HashMap<Int, Long>()
        fun obj(id: Int, body: () -> Unit) {
            offsets[id] = w.count
            w.text("$id 0 obj\n")
            body()
            w.text("endobj\n")
        }

        w.text("%PDF-1.4\n")
        w.write(byteArrayOf('%'.code.toByte(), 0xE2.toByte(), 0xE3.toByte(), 0xCF.toByte(), 0xD3.toByte(), '\n'.code.toByte()))

        obj(CATALOG_ID) {
            w.text("<< /Type /Catalog /Pages $PAGES_ID 0 R /ViewerPreferences << /PrintScaling /None >> >>\n")
        }
        obj(FONT_ID) {
            w.text("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica /Encoding /WinAnsiEncoding >>\n")
        }
        obj(PAGES_ID) {
            val kids = pages.indices.joinToString(" ") { "${pageId(it)} 0 R" }
            w.text("<< /Type /Pages /Count ${pages.size} /Kids [$kids] >>\n")
        }
        for ((photoId, img) in resolved) {
            obj(imageObjectId.getValue(photoId)) {
                val cs = if (img.components == 1) "/DeviceGray" else "/DeviceRGB"
                w.text(
                    "<< /Type /XObject /Subtype /Image /Width ${img.widthPx} /Height ${img.heightPx} " +
                        "/ColorSpace $cs /BitsPerComponent 8 /Filter /DCTDecode /Length ${img.jpegBytes.size} >>\nstream\n",
                )
                w.write(img.jpegBytes)
                w.text("\nendstream\n")
            }
        }
        pages.forEachIndexed { i, page ->
            val names = page.frames.map { it.photoId }.distinct()
            val xobjects = names.joinToString(" ") { "/Im${imageObjectId.getValue(it)} ${imageObjectId.getValue(it)} 0 R" }
            obj(pageId(i)) {
                w.text(
                    "<< /Type /Page /Parent $PAGES_ID 0 R /MediaBox [0 0 ${num(page.pageWidthPt)} ${num(page.pageHeightPt)}] " +
                        "/Resources << /Font << /F1 $FONT_ID 0 R >> /XObject << $xobjects >> >> /Contents ${contentId(i)} 0 R >>\n",
                )
            }
            val content = content(page, resolved, imageObjectId).toByteArray(Charsets.ISO_8859_1)
            obj(contentId(i)) {
                w.text("<< /Length ${content.size} >>\nstream\n")
                w.write(content)
                w.text("\nendstream\n")
            }
        }

        val lastId = pageId(pages.size - 1) + 1
        val xrefStart = w.count
        w.text("xref\n0 ${lastId + 1}\n")
        w.text("0000000000 65535 f \n")
        for (id in 1..lastId) w.text(String.format(Locale.ROOT, "%010d 00000 n \n", offsets.getValue(id)))
        w.text("trailer\n<< /Size ${lastId + 1} /Root $CATALOG_ID 0 R >>\nstartxref\n$xrefStart\n%%EOF\n")
        w.flush()
    }

    private fun content(page: PdfPageSpec, images: Map<String, PhotoImage>, ids: Map<String, Int>): String {
        val h = page.pageHeightPt
        val sb = StringBuilder()
        for (fr in page.frames) {
            val img = images.getValue(fr.photoId)
            val d = ImageFit.draw(fr.rectPt, fr.rotationDegrees, fr.cropState, img.widthPx, img.heightPx)
            val m = d.matrix
            // y-down → PDF y-up. The image XObject's unit square has its top row at y = 1.
            val pa = m.a
            val pb = -m.b
            val pc = -m.c
            val pd = m.d
            val pe = m.c + m.e
            val pf = h - m.f - m.d
            val r = fr.rectPt
            sb.append("q\n")
                .append("${num(r.left)} ${num(h - r.bottom)} ${num(r.width)} ${num(r.height)} re W n\n")
                .append("${num(pa)} ${num(pb)} ${num(pc)} ${num(pd)} ${num(pe)} ${num(pf)} cm\n")
                .append("/Im${ids.getValue(fr.photoId)} Do\n")
                .append("Q\n")
        }
        if (page.cutLines.isNotEmpty()) {
            sb.append("q\n0.5 G 0.25 w\n")
            for (l in page.cutLines) {
                sb.append("${num(l.x1Pt)} ${num(h - l.y1Pt)} m ${num(l.x2Pt)} ${num(h - l.y2Pt)} l S\n")
            }
            sb.append("Q\n")
        }
        for (o in page.overlays) overlay(sb, o, h)
        return sb.toString()
    }

    private fun overlay(sb: StringBuilder, o: PdfOverlay, pageH: Double) {
        when (o) {
            is PdfStrokeLine -> sb.append("q ${num(o.gray)} G ${num(o.widthPt)} w ")
                .append("${num(o.line.x1Pt)} ${num(pageH - o.line.y1Pt)} m ${num(o.line.x2Pt)} ${num(pageH - o.line.y2Pt)} l S Q\n")
            is PdfStrokeRect -> sb.append("q ${num(o.gray)} G ${num(o.widthPt)} w ")
                .append("${num(o.rect.left)} ${num(pageH - o.rect.bottom)} ${num(o.rect.width)} ${num(o.rect.height)} re S Q\n")
            is PdfText -> sb.append("q ${num(o.gray)} g BT /F1 ${num(o.sizePt)} Tf ")
                .append("${num(o.xPt)} ${num(pageH - o.baselineYPt)} Td (${escape(o.text)}) Tj ET Q\n")
        }
    }

    /** Latin-1 only; escapes the PDF string delimiters. */
    private fun escape(s: String): String = buildString {
        for (ch in s) {
            when {
                ch == '(' || ch == ')' || ch == '\\' -> append('\\').append(ch)
                ch.code in 32..255 -> append(ch)
                else -> append('?')
            }
        }
    }

    private fun num(v: Double): String {
        val s = String.format(Locale.ROOT, "%.4f", v).trimEnd('0').trimEnd('.')
        return if (s == "-0" || s.isEmpty()) "0" else s
    }

    private class Counting(private val out: OutputStream) {
        var count = 0L
            private set

        fun write(b: ByteArray) {
            out.write(b)
            count += b.size
        }

        fun text(s: String) = write(s.toByteArray(Charsets.ISO_8859_1))

        fun flush() = out.flush()
    }

    private companion object {
        const val CATALOG_ID = 1
        const val PAGES_ID = 2
        const val FONT_ID = 3
        const val FIRST_IMAGE_ID = 4
    }
}
