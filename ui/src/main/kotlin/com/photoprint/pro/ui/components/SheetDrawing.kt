package com.photoprint.pro.ui.components

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.withTransform
import com.photoprint.pro.core.ui.theme.CutGuide
import com.photoprint.pro.core.ui.theme.PaperEdge
import com.photoprint.pro.core.ui.theme.PaperWhite
import com.photoprint.pro.domain.measurement.Rect
import com.photoprint.pro.domain.model.CropState
import com.photoprint.pro.domain.model.SheetLayout
import com.photoprint.pro.domain.pdf.Affine
import com.photoprint.pro.domain.pdf.ImageFit
import com.photoprint.pro.presentation.editor.ColorAdjust
import com.photoprint.pro.presentation.preview.SheetViewport

private fun Affine.toMatrix(): Matrix {
    // Compose Matrix is column-major 4×4: x' = v[0]x + v[4]y + v[12], y' = v[1]x + v[5]y + v[13].
    val v = FloatArray(16)
    v[0] = a.toFloat(); v[1] = b.toFloat()
    v[4] = c.toFloat(); v[5] = d.toFloat()
    v[10] = 1f; v[15] = 1f
    v[12] = e.toFloat(); v[13] = f.toFloat()
    return Matrix(v)
}

/**
 * Draws [bitmap] into [frame] (pixels) using exactly the same crop maths as the PDF writer
 * ([ImageFit.draw]), so what you see is what prints. The frame clips the picture.
 */
fun DrawScope.drawPhotoInFrame(
    bitmap: ImageBitmap?,
    frame: Rect,
    placementRotation: Int,
    crop: CropState,
    placeholder: Color,
) {
    if (bitmap == null) {
        drawRect(placeholder, Offset(frame.left.toFloat(), frame.top.toFloat()), Size(frame.width.toFloat(), frame.height.toFloat()))
        return
    }
    val drawing = ImageFit.draw(frame, placementRotation, crop, bitmap.width, bitmap.height)
    val pixelToPage = Affine.scale(1.0 / bitmap.width, 1.0 / bitmap.height).then(drawing.matrix)
    val filter = if (ColorAdjust.isIdentity(crop.brightness, crop.contrast)) null
    else ColorFilter.colorMatrix(ColorMatrix(ColorAdjust.matrix(crop.brightness, crop.contrast)))
    clipRect(frame.left.toFloat(), frame.top.toFloat(), frame.right.toFloat(), frame.bottom.toFloat()) {
        withTransform({ transform(pixelToPage.toMatrix()) }) {
            drawImage(bitmap, colorFilter = filter)
        }
    }
}

/**
 * Draws one sheet at the viewport's scale. Positions come straight from the [SheetLayout] (millimetres)
 * times the view scale: the same numbers the PDF uses, only scaled for the screen.
 */
fun DrawScope.drawSheet(
    sheet: SheetLayout,
    vp: SheetViewport,
    bitmaps: Map<String, ImageBitmap>,
    selectedIndex: Int?,
    placeholder: Color,
    selection: Color,
) {
    val k = vp.scale
    val left = vp.offsetX.toFloat()
    val top = vp.offsetY.toFloat()
    val w = (sheet.paperWidthMm * k).toFloat()
    val h = (sheet.paperHeightMm * k).toFloat()

    // Soft shadow, then the paper.
    drawRect(Color(0x22000000), Offset(left + 3f, top + 4f), Size(w, h))
    drawRect(PaperWhite, Offset(left, top), Size(w, h))

    sheet.placements.forEachIndexed { i, p ->
        val frame = Rect(left + p.xMm * k, top + p.yMm * k, p.widthMm * k, p.heightMm * k)
        drawPhotoInFrame(bitmaps[p.photoId], frame, p.rotation, p.cropState, placeholder)
        if (i == selectedIndex) {
            drawRect(
                selection,
                Offset(frame.left.toFloat(), frame.top.toFloat()),
                Size(frame.width.toFloat(), frame.height.toFloat()),
                style = Stroke(width = 3f),
            )
        }
    }

    for (l in sheet.cutLines) {
        drawLine(
            CutGuide,
            Offset((left + l.x1Mm * k).toFloat(), (top + l.y1Mm * k).toFloat()),
            Offset((left + l.x2Mm * k).toFloat(), (top + l.y2Mm * k).toFloat()),
            strokeWidth = 1f,
        )
    }
    drawRect(PaperEdge, Offset(left, top), Size(w, h), style = Stroke(width = 1f))
}
