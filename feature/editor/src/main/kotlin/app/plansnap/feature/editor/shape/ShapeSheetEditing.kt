package app.plansnap.feature.editor.shape

import app.plansnap.core.common.units.TurnDirection
import app.plansnap.feature.editor.state.EditorHost
import java.util.Locale

/** Changes the value of the open shape sheet and validates it on every change. */
internal object ShapeSheetEditing {
    /** The typed value of the open sheet changed to [text]. A sheet without a value ignores it. */
    fun text(
        text: String,
        host: EditorHost,
    ) {
        val unit = host.state.value.settings.lengthUnit
        host.updateShape { shape ->
            val sheet =
                when (val open = shape.sheet) {
                    is ShapeSheet.Corner -> open.copy(text = text)
                    is ShapeSheet.Diagonal -> open.copy(text = text)
                    is ShapeSheet.Thickness -> open.copy(text = text)
                    ShapeSheet.Adjustment, null -> null
                }
            if (sheet ==
                null
            ) {
                shape
            } else {
                shape.copy(sheet = sheet, error = ShapeValues.errorOf(sheet, unit, Locale.getDefault()))
            }
        }
    }

    /** The angle sheet turns in [direction]. */
    fun direction(
        direction: TurnDirection,
        host: EditorHost,
    ) {
        host.updateShape { shape ->
            (shape.sheet as? ShapeSheet.Corner)?.let { shape.copy(sheet = it.copy(direction = direction)) } ?: shape
        }
    }

    /** The diagonal sheet connects [from] and [to]. Corners outside the room are ignored. */
    fun corners(
        from: Int,
        to: Int,
        host: EditorHost,
    ) {
        val last =
            (
                host.state.value.selectedRoom
                    ?.walls
                    ?.size ?: 0
            ) - 1
        if (from !in 0..last || to !in 0..last) return
        val unit = host.state.value.settings.lengthUnit
        host.updateShape { shape ->
            (shape.sheet as? ShapeSheet.Diagonal)?.let {
                val moved = it.copy(from = from, to = to)
                shape.copy(sheet = moved, error = ShapeValues.errorOf(moved, unit, Locale.getDefault()))
            } ?: shape
        }
    }
}
