package app.plansnap.core.export.svg

import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.render.RenderTextRules
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

/**
 * World to page mapping in millimeters: `x = originX + (wx - centerX) · mmPerWorldX`, with a negative `mmPerWorldY`
 * because world y points up and page y down. Built from the page points of `PlotLayout`, the one conversion of exports.
 */
internal class PageMapping(
    private val centerX: Double,
    private val centerY: Double,
    private val originX: Double,
    private val originY: Double,
    val mmPerWorldX: Double,
    val mmPerWorldY: Double,
) {
    fun x(world: Double): Double = originX + (world - centerX) * mmPerWorldX

    fun y(world: Double): Double = originY + (world - centerY) * mmPerWorldY
}

/** Writes one scene element as exactly one SVG element, following the rules of `SceneRenderer.drawExport`. */
internal class SvgElementWriter(
    private val mapping: PageMapping,
    private val denominator: Int,
    private val format: (LabelText) -> String,
) {
    private val textFactor = RenderTextRules.exportFactor(denominator)

    fun write(
        out: StringBuilder,
        element: SceneElement,
    ) {
        when (element) {
            is SceneElement.Fill -> fill(out, element)
            is SceneElement.Polyline -> polyline(out, element)
            is SceneElement.Arc -> arc(out, element)
            is SceneElement.Label -> label(out, element)
        }
    }

    private fun fill(
        out: StringBuilder,
        element: SceneElement.Fill,
    ) {
        val look = SvgStyles.look(element.style)
        val d = StringBuilder()
        element.polygon.vertices.forEachIndexed { i, v -> d.append(if (i == 0) 'M' else 'L').append(point(v.x, v.y)) }
        d.append('Z')
        out
            .append("<path class=\"")
            .append(SvgStyles.className(element.style))
            .append("\" d=\"")
            .append(d)
            .append('"')
        paint(out, look.fillArgb)
        if (element.style != SceneStyle.WALL_BAND) stroke(out, look) else out.append(" stroke=\"none\"")
        out.append("/>\n")
    }

    private fun polyline(
        out: StringBuilder,
        element: SceneElement.Polyline,
    ) {
        val look = SvgStyles.look(element.style)
        val points = element.points
        val cls = SvgStyles.className(element.style)
        if (points.size == 2 && points[0] == points[1]) {
            val radius = SvgStyles.MARKER_UNITS * SvgStyles.UNIT_MM / 2.0
            out
                .append("<circle class=\"")
                .append(cls)
                .append("\" cx=\"")
                .append(SvgText.number(mapping.x(points[0].x)))
                .append("\" cy=\"")
                .append(SvgText.number(mapping.y(points[0].y)))
                .append("\" r=\"")
                .append(SvgText.number(radius))
                .append('"')
            paint(out, look.strokeArgb)
            out.append(" stroke=\"none\"/>\n")
            return
        }
        val d = StringBuilder()
        points.forEachIndexed { i, v -> d.append(if (i == 0) 'M' else 'L').append(point(v.x, v.y)) }
        if (element.closed) d.append('Z')
        out
            .append("<path class=\"")
            .append(cls)
            .append("\" d=\"")
            .append(d)
            .append("\" fill=\"none\"")
        stroke(out, look)
        out.append("/>\n")
    }

    private fun arc(
        out: StringBuilder,
        element: SceneElement.Arc,
    ) {
        val look = SvgStyles.look(element.style)
        val d = StringBuilder()
        val sweep = element.sweepRad
        val start = pointOnArc(element, element.startRad)
        d.append('M').append(start)
        if (abs(sweep) >= 2.0 * PI) {
            val half = pointOnArc(element, element.startRad + PI)
            d.append(arcTo(element, half, sweepFlag = sweep > 0, large = false))
            d.append(arcTo(element, start, sweepFlag = sweep > 0, large = false))
        } else if (sweep != 0.0) {
            val end = pointOnArc(element, element.startRad + sweep)
            d.append(arcTo(element, end, sweepFlag = sweep > 0, large = abs(sweep) > PI))
        }
        out
            .append("<path class=\"")
            .append(SvgStyles.className(element.style))
            .append("\" d=\"")
            .append(d)
        out.append("\" fill=\"none\"")
        stroke(out, look)
        out.append("/>\n")
    }

    private fun arcTo(
        element: SceneElement.Arc,
        end: String,
        sweepFlag: Boolean,
        large: Boolean,
    ): String {
        val r = SvgText.number(element.radiusMm * abs(mapping.mmPerWorldX))
        return "A$r $r 0 ${if (large) 1 else 0} ${if (sweepFlag) 1 else 0} $end"
    }

    private fun pointOnArc(
        element: SceneElement.Arc,
        angle: Double,
    ): String =
        point(element.center.x + element.radiusMm * cos(angle), element.center.y + element.radiusMm * sin(angle))

    private fun label(
        out: StringBuilder,
        element: SceneElement.Label,
    ) {
        val look = SvgStyles.look(element.style)
        val block = element.blockAnchor
        val anchor = element.anchor
        val at =
            if (block == null) {
                anchor
            } else {
                Vec2(block.x + textFactor * (anchor.x - block.x), block.y + textFactor * (anchor.y - block.y))
            }
        val sizeMm = element.heightMm * textFactor / denominator
        out
            .append("<text class=\"")
            .append(SvgStyles.className(element.style))
            .append("\" transform=\"translate(")
            .append(point(at.x, at.y))
            .append(") rotate(")
            .append(SvgText.number(-Math.toDegrees(element.rotationRad)))
            .append(")\" text-anchor=\"middle\" dy=\"0.35em\" font-family=\"sans-serif\" font-size=\"")
            .append(SvgText.number(sizeMm))
        out.append('"')
        paint(out, look.textArgb)
        out.append('>').append(SvgText.escape(format(element.text))).append("</text>\n")
    }

    private fun point(
        wx: Double,
        wy: Double,
    ): String = SvgText.number(mapping.x(wx)) + " " + SvgText.number(mapping.y(wy))

    private fun stroke(
        out: StringBuilder,
        look: SvgLook,
    ) {
        out.append(" stroke=\"").append(hex(look.strokeArgb)).append("\" stroke-width=\"")
        out.append(SvgText.number(look.strokeUnits * SvgStyles.UNIT_MM)).append('"')
        if (look.roundCap) out.append(" stroke-linecap=\"round\"")
        look.dash?.let { dash ->
            out.append(" stroke-dasharray=\"")
            out.append(dash.joinToString(" ") { SvgText.number(it * SvgStyles.UNIT_MM) }).append('"')
        }
    }

    companion object {
        private const val OPAQUE = 255
        private const val BYTE = 0xFF
        private const val SHIFT_RED = 16
        private const val SHIFT_GREEN = 8
        private const val SHIFT_ALPHA = 24

        /** `#RRGGBB` of an ARGB integer. */
        fun hex(argb: Int): String =
            "#%02X%02X%02X".format(
                Locale.ROOT,
                (argb shr SHIFT_RED) and BYTE,
                (argb shr SHIFT_GREEN) and BYTE,
                argb and BYTE,
            )

        /** Appends a `fill` attribute and, for translucent colors, `fill-opacity`. */
        fun paint(
            out: StringBuilder,
            argb: Int,
        ) {
            val alpha = (argb ushr SHIFT_ALPHA) and BYTE
            if (alpha == 0) {
                out.append(" fill=\"none\"")
                return
            }
            out.append(" fill=\"").append(hex(argb)).append('"')
            if (alpha != OPAQUE) {
                out.append(" fill-opacity=\"").append(SvgText.number(alpha / OPAQUE.toDouble())).append('"')
            }
        }
    }
}
