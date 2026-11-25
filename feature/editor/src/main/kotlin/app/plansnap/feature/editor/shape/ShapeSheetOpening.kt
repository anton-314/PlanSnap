package app.plansnap.feature.editor.shape

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.units.DecimalAngleFormatter
import app.plansnap.core.common.units.DecimalLengthFormatter
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.CornerSpec
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorHost
import java.util.Locale

/** Opens the shape sheets with the values the user most likely wants to start from. */
internal object ShapeSheetOpening {
    /** Corner a new diagonal ends at before the user chooses one: it skips the neighbor of corner 0. */
    private const val DEFAULT_DIAGONAL_TARGET = 2

    /** Opens the angle sheet for [target]. Nothing opens without a room or, for the next corner, without a wall. */
    fun corner(
        target: CornerTarget,
        host: EditorHost,
    ) {
        val state = host.state.value
        val room = state.selectedRoom ?: return
        val current: CornerSpec? =
            when (target) {
                CornerTarget.Next -> state.nextCorner.takeIf { room.walls.isNotEmpty() }
                is CornerTarget.Existing -> room.corners.getOrNull(target.index)
            }
        if (current == null) {
            if (target is CornerTarget.Existing) {
                host.send(EditorEffect.Error(DomainError.NotFound(EntityKind.CORNER, target.index.toString())))
            }
            return
        }
        val text =
            (current as? CornerSpec.Measured)?.let {
                DecimalAngleFormatter.format(
                    it.angle,
                    Locale.getDefault(),
                )
            }
        val direction = current.direction ?: state.nextCorner.direction ?: TurnDirection.LEFT
        host.updateShape { ShapeState(sheet = ShapeSheet.Corner(target, direction, text.orEmpty())) }
    }

    /** Opens the diagonal sheet, with [from] and [to] if the evaluation recommended them. */
    fun diagonal(
        from: Int?,
        to: Int?,
        host: EditorHost,
    ) {
        val room = host.state.value.selectedRoom ?: return
        val last = room.walls.size - 1
        if (last < 1) {
            host.send(EditorEffect.Error(DomainError.RuleViolation(DomainRule.TOO_FEW_WALLS)))
            return
        }
        val sheet =
            ShapeSheet.Diagonal(
                (from ?: 0).coerceIn(0, last),
                (to ?: DEFAULT_DIAGONAL_TARGET).coerceIn(0, last),
                "",
            )
        val error = ShapeValues.errorOf(sheet, host.state.value.settings.lengthUnit, Locale.getDefault())
        host.updateShape { ShapeState(sheet = sheet, error = error) }
    }

    /** Opens the thickness sheet, starting with the thickness the next wall would get. */
    fun thickness(host: EditorHost) {
        val state = host.state.value
        val text = DecimalLengthFormatter.format(state.thickness, state.settings.lengthUnit, Locale.getDefault())
        host.updateShape { ShapeState(sheet = ShapeSheet.Thickness(text)) }
    }

    /** Opens the adjustment sheet if the selected room has a proposal. */
    fun adjustment(host: EditorHost) {
        val state = host.state.value
        if (state.evaluations[state.selectedRoomId] is RoomEvaluation.NeedsAdjustment) {
            host.updateShape { ShapeState(sheet = ShapeSheet.Adjustment) }
        }
    }
}
