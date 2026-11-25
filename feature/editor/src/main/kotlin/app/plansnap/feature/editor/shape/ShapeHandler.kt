package app.plansnap.feature.editor.shape

import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.feature.editor.state.EditorHandler
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorIntent
import javax.inject.Inject

/**
 * Handler of the shape sheets (T-EDT-004): the angle of a corner, diagonals, the thickness of the next wall, closing
 * and the adjustment of a room. It only turns typed values into commands. Whether a value is possible, what a room
 * looks like and what an adjustment changes is decided by the domain.
 *
 * Commands go through [EditorHost.submit], so they run as confirmed work in the application scope. Accepting an
 * adjustment is `CoreIntent.AcceptAdjustment`, sent by the sheet itself.
 */
class ShapeHandler
    @Inject
    constructor(
        ids: IdGenerator,
    ) : EditorHandler {
        private val commit = ShapeSheetCommit(ids)

        override fun handle(
            intent: EditorIntent,
            host: EditorHost,
        ): Boolean = intent is ShapeIntent && (opens(intent, host) || edits(intent, host) || decides(intent, host))

        /** The intents that open a sheet. */
        private fun opens(
            intent: ShapeIntent,
            host: EditorHost,
        ): Boolean {
            when (intent) {
                is ShapeIntent.OpenCorner -> ShapeSheetOpening.corner(intent.target, host)
                is ShapeIntent.OpenDiagonal -> ShapeSheetOpening.diagonal(intent.from, intent.to, host)
                ShapeIntent.OpenThickness -> ShapeSheetOpening.thickness(host)
                ShapeIntent.OpenAdjustment -> ShapeSheetOpening.adjustment(host)
                else -> return false
            }
            return true
        }

        /** The intents that change the open sheet. */
        private fun edits(
            intent: ShapeIntent,
            host: EditorHost,
        ): Boolean {
            when (intent) {
                is ShapeIntent.SheetTextChanged -> ShapeSheetEditing.text(intent.text, host)
                is ShapeIntent.SheetDirectionChanged -> ShapeSheetEditing.direction(intent.direction, host)
                is ShapeIntent.DiagonalCornersChanged -> ShapeSheetEditing.corners(intent.from, intent.to, host)
                ShapeIntent.ConfirmSheet -> commit.confirm(host)
                ShapeIntent.Dismiss -> host.updateShape { ShapeState() }
                else -> return false
            }
            return true
        }

        /** The intents that decide about the room itself: closing it and the adjustment. */
        private fun decides(
            intent: ShapeIntent,
            host: EditorHost,
        ): Boolean {
            val room = host.state.value.selectedRoom
            when (intent) {
                ShapeIntent.CloseRoom -> {
                    room?.let { host.submit(ProjectEdit.CloseRoom(it.id)) }
                }

                ShapeIntent.RequestExplicitConfirmation -> {
                    host.updateShape {
                        if (it.sheet ==
                            ShapeSheet.Adjustment
                        ) {
                            it.copy(explicitConfirmation = true)
                        } else {
                            it
                        }
                    }
                }

                ShapeIntent.CancelExplicitConfirmation -> {
                    host.updateShape { it.copy(explicitConfirmation = false) }
                }

                ShapeIntent.RevertAdjustment -> {
                    room?.takeIf { it.adjustment != null }?.let { host.submit(ProjectEdit.RevertAdjustment(it.id)) }
                }

                else -> {
                    return false
                }
            }
            return true
        }
    }
