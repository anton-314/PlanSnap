package app.plansnap.core.export.svg

import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.plot.titleBlockMm
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.model.RoomId
import app.plansnap.core.render.RenderPalette

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
 * title block with the scale bar and the scale (DOMAIN section 12, ADR 0009). The output depends only on its input.
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
        out.append("<rect id=\"page-background\" x=\"0\" y=\"0\" width=\"$w\" height=\"$h\" fill=\"#FFFFFF\"/>\n")
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
        val block = plan.titleBlockMm
        val ink = SvgElementWriter.hex(RenderPalette.Light.wall)
        val left = block.leftMm + PADDING_MM
        out.append("<g id=\"title-block\" stroke=\"$ink\" fill=\"none\" stroke-width=\"$FRAME_MM\">\n")
        out.append("<rect id=\"title-frame\" x=\"${block.leftMm}\" y=\"${block.topMm}\" ")
        out.append("width=\"${block.widthMm}\" height=\"${block.heightMm}\"/>\n")
        val baseline = (block.topMm + NAME_BASELINE_MM).toDouble()
        text(out, TextSpot("project-name", left.toDouble(), baseline, "start", NAME_SIZE_MM), content.projectName)
        val scaleSpot = TextSpot("scale-label", (block.rightMm - PADDING_MM).toDouble(), baseline, "end", NAME_SIZE_MM)
        text(out, scaleSpot, "1:${plan.scale.denominator}")
        scaleBar(out, plan.scale, left, block.topMm + BAR_TOP_MM, ink)
        out.append("</g>\n")
    }

    private fun scaleBar(
        out: StringBuilder,
        scale: PlotScale,
        left: Int,
        top: Int,
        ink: String,
    ) {
        val nominalMm = nominalLengthMm(scale)
        val widthMm = nominalMm / scale.denominator
        val half = widthMm / 2.0
        out.append("<g id=\"scale-bar\">\n")
        out.append("<rect x=\"$left\" y=\"$top\" width=\"${SvgText.number(half)}\" height=\"$BAR_HEIGHT_MM\" ")
        out.append("fill=\"$ink\"/>\n")
        out.append("<rect x=\"${SvgText.number(left + half)}\" y=\"$top\" width=\"${SvgText.number(half)}\" ")
        out.append("height=\"$BAR_HEIGHT_MM\" fill=\"#FFFFFF\"/>\n")
        val labelY = top + BAR_HEIGHT_MM + LABEL_SIZE_MM
        text(out, TextSpot("scale-bar-start", left.toDouble(), labelY, "middle", LABEL_SIZE_MM), "0")
        val end = format(LabelText.Dimension(nominalMm))
        text(out, TextSpot("scale-bar-end", left + widthMm, labelY, "middle", LABEL_SIZE_MM), end)
        out.append("</g>\n")
    }

    private fun text(
        out: StringBuilder,
        spot: TextSpot,
        content: String,
    ) {
        val ink = SvgElementWriter.hex(RenderPalette.Light.wall)
        out.append("<text id=\"${spot.id}\" x=\"${SvgText.number(spot.x)}\" y=\"${SvgText.number(spot.y)}\" ")
        out.append("text-anchor=\"${spot.anchor}\" font-family=\"sans-serif\" ")
        out.append("font-size=\"${SvgText.number(spot.sizeMm)}\" fill=\"$ink\" ")
        out.append("stroke=\"none\">").append(SvgText.escape(content)).append("</text>\n")
    }

    private class TextSpot(
        val id: String,
        val x: Double,
        val y: Double,
        val anchor: String,
        val sizeMm: Double,
    )

    private companion object {
        const val PADDING_MM = 5
        const val FRAME_MM = 0.25
        const val NAME_BASELINE_MM = 8
        const val NAME_SIZE_MM = 5.0
        const val BAR_TOP_MM = 12
        const val BAR_HEIGHT_MM = 3
        const val LABEL_SIZE_MM = 3.0
        const val TWO_METERS_MM = 2000.0
        const val FIVE_METERS_MM = 5000.0
        const val TEN_METERS_MM = 10000.0

        /** Nominal length of the scale bar in world millimeters: 2 m at 1:50, 5 m at 1:100, 10 m at 1:200. */
        fun nominalLengthMm(scale: PlotScale): Double =
            when (scale) {
                PlotScale.ONE_TO_50 -> TWO_METERS_MM
                PlotScale.ONE_TO_100 -> FIVE_METERS_MM
                PlotScale.ONE_TO_200 -> TEN_METERS_MM
            }
    }
}
