package com.photoprint.pro.presentation.layout

import com.photoprint.pro.domain.model.PaperSizePresets
import com.photoprint.pro.domain.model.PhotoSize
import com.photoprint.pro.domain.model.PhotoSizePresets
import com.photoprint.pro.presentation.session.PickedPhoto
import com.photoprint.pro.presentation.session.PrintSession
import com.photoprint.pro.presentation.settings.AppSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class LayoutFeedbackTest {
    private var n = 0
    private val session = PrintSession(ids = { "p${++n}" }, settings = AppSettings())

    private fun withPhoto() = session.addPhotos(listOf(PickedPhoto("r", 1200, 1600)))

    private fun feedback() = LayoutFeedback.from(session.state.value)

    @Test
    fun `summary describes the grid for the live estimate`() {
        withPhoto()
        session.setAllowRotation(false)
        session.setCopies(20)
        val ok = assertIs<LayoutFeedback.Ok>(feedback()).summary
        assertEquals(6, ok.photosPerSheet)
        assertEquals(2, ok.columns)
        assertEquals(3, ok.rows)
        assertEquals(4, ok.sheets)
        assertEquals(20, ok.totalPhotos)
        assertEquals(2, ok.photosOnLastSheet)
        assertEquals(false, ok.rotated)
    }

    @Test
    fun `rotation is reported when it wins`() {
        withPhoto()
        session.setMarginAll(0.0)
        session.setSpacingAll(0.0)
        val ok = assertIs<LayoutFeedback.Ok>(feedback()).summary
        assertEquals(8, ok.photosPerSheet)
        assertEquals(true, ok.rotated)
    }

    @Test
    fun `no photos asks to add photos`() {
        val p = assertIs<LayoutFeedback.Problem>(feedback()).problem
        assertEquals(listOf(Suggestion.ADD_PHOTOS), p.suggestions)
    }

    @Test
    fun `margins that eat the paper suggest smaller margins first`() {
        withPhoto()
        session.setMarginAll(20.0)
        session.setPaperSize(PaperSizePresets.Photo4x6.copy(widthMm = 30.0)) // 30 − 40 < 0
        val p = assertIs<LayoutFeedback.Problem>(feedback()).problem
        assertEquals(listOf(Suggestion.REDUCE_MARGIN, Suggestion.LARGER_PAPER), p.suggestions)
    }

    @Test
    fun `a photo that fits the paper but not inside the margins blames the margins`() {
        withPhoto()
        session.setPhotoSize(PhotoSize("wide", 90.0, 120.0))
        session.setMarginAll(10.0) // 102 − 20 = 82 < 90, but 90 ≤ 102
        val p = assertIs<LayoutFeedback.Problem>(feedback()).problem
        assertEquals(Suggestion.REDUCE_MARGIN, p.suggestions.first())
        assertEquals(true, Suggestion.LARGER_PAPER in p.suggestions)
    }

    @Test
    fun `a photo bigger than the paper does not blame the margins`() {
        withPhoto()
        session.setPhotoSize(PhotoSize("huge", 200.0, 300.0))
        val p = assertIs<LayoutFeedback.Problem>(feedback()).problem
        assertEquals(listOf(Suggestion.LARGER_PAPER, Suggestion.SMALLER_PHOTO), p.suggestions)
    }

    @Test
    fun `turning rotation on is suggested when it would fit`() {
        withPhoto()
        session.setAllowRotation(false)
        session.setPhotoSize(PhotoSize("landscape", 140.0, 90.0)) // fits 102×152 only turned
        session.setMarginAll(0.0)
        val p = assertIs<LayoutFeedback.Problem>(feedback()).problem
        assertEquals(Suggestion.ALLOW_ROTATION, p.suggestions.first())
        session.setAllowRotation(true)
        assertIs<LayoutFeedback.Ok>(feedback())
    }

    @Test
    fun `too many copies suggests fewer`() {
        withPhoto()
        session.setPhotoSize(PhotoSizePresets.IdPhoto)
        session.setAutoFill(true)
        session.setFillSheets(PrintSession.MAX_FILL_SHEETS)
        // 50 sheets of ~20 photos is within the limit; shrink the photo so it is not.
        session.setPhotoSize(PhotoSize("tiny", 2.0, 2.0))
        session.setMarginAll(0.0)
        session.setSpacingAll(0.0)
        val p = assertIs<LayoutFeedback.Problem>(feedback()).problem
        assertEquals(listOf(Suggestion.FEWER_COPIES), p.suggestions)
    }
}
