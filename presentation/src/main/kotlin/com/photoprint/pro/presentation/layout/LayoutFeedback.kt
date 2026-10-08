package com.photoprint.pro.presentation.layout

import com.photoprint.pro.domain.layout.GridPlan
import com.photoprint.pro.domain.layout.LayoutError
import com.photoprint.pro.domain.layout.LayoutOutcome
import com.photoprint.pro.domain.layout.LayoutPlan
import com.photoprint.pro.domain.layout.PhotoOrientation
import com.photoprint.pro.presentation.session.SessionState

/** What the layout settings screen shows live: "6 photos per sheet · 2 columns × 3 rows". */
data class LayoutSummary(
    val photosPerSheet: Int,
    val columns: Int,
    val rows: Int,
    val sheets: Int,
    val totalPhotos: Int,
    val rotated: Boolean,
    val photosOnLastSheet: Int,
)

/** A concrete thing the user can try, so an error message is never a dead end. */
enum class Suggestion { REDUCE_MARGIN, REDUCE_SPACING, LARGER_PAPER, SMALLER_PHOTO, ALLOW_ROTATION, CHECK_VALUES, ADD_PHOTOS, FEWER_COPIES }

data class LayoutProblem(val error: LayoutError, val suggestions: List<Suggestion>)

sealed interface LayoutFeedback {
    data class Ok(val summary: LayoutSummary) : LayoutFeedback
    data class Problem(val problem: LayoutProblem) : LayoutFeedback

    companion object {
        fun from(state: SessionState): LayoutFeedback = when (val o = state.layout) {
            is LayoutOutcome.Success -> Ok(summary(o.value))
            is LayoutOutcome.Failure -> Problem(LayoutProblem(o.error, suggestionsFor(o.error, state)))
        }

        fun summary(plan: LayoutPlan): LayoutSummary {
            val g: GridPlan = plan.grid
            return LayoutSummary(
                photosPerSheet = g.photosPerSheet,
                columns = g.columns,
                rows = g.rows,
                sheets = plan.sheetCount,
                totalPhotos = plan.totalPhotos,
                rotated = g.orientation == PhotoOrientation.ROTATED_90,
                photosOnLastSheet = plan.sheets.last().placements.size,
            )
        }

        /** Ordered most-likely-first. Needs the session so it can tell whether margins or paper are the obstacle. */
        fun suggestionsFor(e: LayoutError, state: SessionState): List<Suggestion> = when (e) {
            is LayoutError.InvalidPaper, is LayoutError.InvalidPhoto,
            LayoutError.InvalidMargin, LayoutError.InvalidSpacing, LayoutError.InvalidCopies,
            -> listOf(Suggestion.CHECK_VALUES)
            LayoutError.NoPhotos -> listOf(Suggestion.ADD_PHOTOS)
            is LayoutError.TooManyPhotos -> listOf(Suggestion.FEWER_COPIES)
            is LayoutError.MarginsConsumePaper -> listOf(Suggestion.REDUCE_MARGIN, Suggestion.LARGER_PAPER)
            is LayoutError.PhotoDoesNotFit -> {
                val pw = state.paperSize.widthMm
                val ph = state.paperSize.heightMm
                val fitsPaperUpright = e.photoWidthMm <= pw && e.photoHeightMm <= ph
                val fitsPaperTurned = e.photoHeightMm <= pw && e.photoWidthMm <= ph
                val rotationWouldHelp = !e.rotationWasAllowed && fitsPaperTurned &&
                    e.photoHeightMm <= e.usableWidthMm && e.photoWidthMm <= e.usableHeightMm
                buildList {
                    if (rotationWouldHelp) add(Suggestion.ALLOW_ROTATION)
                    if (fitsPaperUpright || (e.rotationWasAllowed && fitsPaperTurned)) {
                        // The photo is smaller than the paper: the margins are what is in the way.
                        add(Suggestion.REDUCE_MARGIN)
                    }
                    add(Suggestion.LARGER_PAPER)
                    add(Suggestion.SMALLER_PHOTO)
                }.distinct()
            }
        }
    }
}
