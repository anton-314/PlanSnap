package app.plansnap.core.export.svg

import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.ScaleBarLayout
import app.plansnap.core.drawing.plot.TitleBlockSpec
import app.plansnap.core.drawing.plot.TitleTextAlign
import app.plansnap.core.drawing.plot.TitleTextSlot
import app.plansnap.core.drawing.plot.titleBlockLayout
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.model.RoomId
import app.plansnap.core.render.SceneStyleSheet

/**
 * Input of [SvgDocument].
 *
 * @property rooms scenes of the exportable rooms, each with the evaluation status as its class.
 * @property omittedRooms ids of the `OPEN` and `CONFLICT` rooms left out.
 */
internal class SvgContent(
    val plan: PlotPlan,
    val projectName: String,
    val rooms: List<RoomScene>,
    val omittedRooms: List<RoomId>,
)

/**
 * Composes the SVG text of a plan: the page in millimeters (`viewBox` equals the page), one group per room, and the
 * title block with the scale bar and the scale as resolved by `TitleBlockSpec` (DOMAIN section 12, ADR 0022). The
 * output depends only on its input.
 */
internal class SvgDocument(
    private val mapping: PageMapping,
    private val format: (LabelText) -> String,
    private val checkActive: () -> Unit,
) {
    /** Returns the complete document for [content]. */
    fun render(content: SvgContent): String {
        val plan = content.plan
        val out = StringBuilder()
        val w = plan.pageWidthMm.toString()
        val h = plan.pageHeightMm.toString()
        out.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n")
        out.append("<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"${w}mm\" height=\"${h}mm\" ")
        out.append("viewBox=\"0 0 $w $h\" data-scale=\"1:${plan.scale.denominator}\" ")
        out.append("data-paper=\"${plan.paper.name}\" data-orientation=\"${plan.orientation.name}\" ")
        out.append("data-omitted-rooms=\"")
        out.append(SvgText.escape(content.omittedRooms.joinToString(" ") { it.value }, attribute = true))
        out.append("\">\n")
        out.append("<title>").append(SvgText.escape(content.projectName)).append("</title>\n")
        out.append("<rect id=\"page-background\" x=\"0\" y=\"0\" width=\"$w\" height=\"$h\" fill=\"$background\"/>\n")
        out.append("<g id=\"plan\">\n")
        val writer = SvgElementWriter(mapping, plan.scale.denominator, format)
        content.rooms.forEachIndexed { index, room ->
            checkActive()
            out.append("<g id=\"room-$index\" data-room-id=\"")
            out.append(SvgText.escape(room.roomId.value, attribute = true))
            out.append("\" data-status=\"${room.status.name}\">\n")
            room.elements.forEach { writer.write(out, it) }
            out.append("</g>\n")
        }
        out.append("</g>\n")
        titleBlock(out, content)
        out.append("</svg>\n")
        return out.toString()
    }

    private fun titleBlock(
        out: StringBuilder,
        content: SvgContent,
    ) {
        val plan = content.plan
        val layout = plan.titleBlockLayout
        val frame = layout.frame
        out.append("<g id=\"title-block\" stroke=\"$ink\" fill=\"none\" ")
        out.append("stroke-width=\"${SvgText.number(layout.strokeMm)}\">\n")
        out.append("<rect id=\"title-frame\" x=\"${frame.leftMm}\" y=\"${frame.topMm}\" ")
        out.append("width=\"${frame.widthMm}\" height=\"${frame.heightMm}\"/>\n")
        text(out, "project-name", layout.projectName, content.projectName)
        text(out, "scale-label", layout.scaleLabel, TitleBlockSpec.scaleText(plan.scale))
        scaleBar(out, layout.scaleBar)
        out.append("</g>\n")
    }

    private fun scaleBar(
        out: StringBuilder,
        bar: ScaleBarLayout,
    ) {
        out.append("<g id=\"scale-bar\">\n")
        bar.segments.forEach { segment ->
            out.append("<rect x=\"${SvgText.number(segment.leftMm)}\" y=\"${SvgText.number(bar.topMm)}\" ")
            out.append("width=\"${SvgText.number(segment.widthMm)}\" height=\"${SvgText.number(bar.heightMm)}\" ")
            out.append("fill=\"${if (segment.filled) ink else background}\"/>\n")
        }
        text(out, "scale-bar-start", bar.startLabel, "0")
        text(out, "scale-bar-end", bar.endLabel, format(LabelText.Dimension(bar.nominalLengthMm.toDouble())))
        out.append("</g>\n")
    }

    private fun text(
        out: StringBuilder,
        id: String,
        slot: TitleTextSlot,
        content: String,
    ) {
        out.append("<text id=\"$id\" x=\"${SvgText.number(slot.xMm)}\" y=\"${SvgText.number(slot.baselineMm)}\" ")
        out.append("text-anchor=\"${anchor(slot.align)}\" font-family=\"sans-serif\" ")
        out.append("font-size=\"${SvgText.number(slot.sizeMm)}\" fill=\"$ink\" ")
        out.append("stroke=\"none\">").append(SvgText.escape(content)).append("</text>\n")
    }

    private companion object {
        /** Title block ink and page background of the shared style sheet (ADR 0022). */
        val ink: String = SvgElementWriter.hex(SvgStyles.argb(SceneStyleSheet.TITLE_BLOCK_INK))
        val background: String = SvgElementWriter.hex(SceneStyleSheet.PAGE_BACKGROUND_ARGB)

        fun anchor(align: TitleTextAlign): String =
            when (align) {
                TitleTextAlign.START -> "start"
                TitleTextAlign.CENTER -> "middle"
                TitleTextAlign.END -> "end"
            }
    }
}
