package app.plansnap.core.render

import android.content.res.Resources
import app.plansnap.core.common.units.DecimalLengthFormatter
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.model.RoomTypeKey
import java.util.Locale
import kotlin.math.roundToLong

/**
 * Turns the language-independent [LabelText] of a scene into display text at the output edge (ADR 0008): units, number
 * format and translations apply here. Results are cached per label, so a repeated draw of an unchanged scene
 * allocates no strings. A new formatter is created when locale, unit or resources change.
 *
 * @property resources resources of the target language, which hold the texts of this module.
 * @property locale number format of the output.
 * @property unit unit of dimension labels.
 * @property roomTypeName display name of a room type, the key itself by default; room type templates live outside
 * `:core:render`.
 */
class LabelTextFormatter(
    private val resources: Resources,
    private val locale: Locale,
    private val unit: LengthUnit,
    private val roomTypeName: (RoomTypeKey) -> String = { it.value },
) {
    private val cache = HashMap<LabelText, String>()

    /** Returns the display text of [text]. */
    fun format(text: LabelText): String {
        if (text is LabelText.Literal) return text.text
        return cache[text] ?: build(text).also {
            if (cache.size >= MAX_CACHED) cache.clear()
            cache[text] = it
        }
    }

    private fun build(text: LabelText): String =
        when (text) {
            is LabelText.Literal -> {
                text.text
            }

            is LabelText.Dimension -> {
                resources.getString(
                    dimensionResource(),
                    DecimalLengthFormatter.format(Length(text.mm.roundToLong()), unit, locale),
                )
            }

            is LabelText.Area -> {
                resources.getString(R.string.render_area_m2, number(text.mm2 / MM2_PER_M2, AREA_DECIMALS))
            }

            is LabelText.InteriorAngle -> {
                resources.getString(R.string.render_angle_degrees, number(text.degrees, ANGLE_DECIMALS))
            }

            is LabelText.RoomType -> {
                roomTypeName(text.key)
            }

            LabelText.ShapeNotConfirmed -> {
                resources.getString(R.string.render_shape_not_confirmed)
            }

            LabelText.ShapeConflict -> {
                resources.getString(R.string.render_shape_conflict)
            }
        }

    private fun dimensionResource(): Int =
        when (unit) {
            LengthUnit.MILLIMETER -> R.string.render_dimension_mm
            LengthUnit.CENTIMETER -> R.string.render_dimension_cm
            LengthUnit.METER -> R.string.render_dimension_m
        }

    private fun number(
        value: Double,
        decimals: Int,
    ): String = String.format(locale, "%.${decimals}f", value)

    private companion object {
        const val MAX_CACHED = 4096
        const val MM2_PER_M2 = 1_000_000.0
        const val AREA_DECIMALS = 2
        const val ANGLE_DECIMALS = 1
    }
}
