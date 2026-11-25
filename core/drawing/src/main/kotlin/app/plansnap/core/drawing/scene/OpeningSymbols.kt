package app.plansnap.core.drawing.scene

import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningType
import kotlin.math.PI

/**
 * Symbols of doors, windows and passages (DOMAIN section 9). Every element carries the opening as its target.
 *
 * The opening covers `offset … offset + width` along its wall from `V_i`, also when it no longer fits on the wall.
 * On a wall of positive thickness it is the filled rectangle across the band, on a wall drawn as a line it is the
 * segment on that line. A window adds a line along the middle of the band. A door with a swing adds its leaf and a
 * quarter arc around the hinge.
 *
 * Left and right apply in wall direction `V_i → V_{i+1}`: the left hinge lies at the start `offset`, the right hinge at
 * the end `offset + width`. An inward door opens into the room from the inner face, an outward door out of the room
 * from the outer face of the band.
 */
internal object OpeningSymbols {
    fun elements(
        wall: WallFrame,
        opening: Opening,
        style: SceneStyle,
        target: SceneTarget.Opening,
    ): List<SceneElement> {
        val startMm = opening.offset.mm.toDouble()
        val endMm = opening.endOffset.mm.toDouble()
        val thickness = wall.thicknessMm
        val elements = mutableListOf<SceneElement>()
        if (thickness > 0.0) {
            val outline =
                listOf(wall.at(startMm), wall.at(endMm), wall.at(endMm, thickness), wall.at(startMm, thickness))
            elements += SceneElement.Fill(Polygon2(outline), style, target)
            if (opening.type == OpeningType.WINDOW) {
                elements += segment(wall.at(startMm, thickness / 2.0), wall.at(endMm, thickness / 2.0), style, target)
            }
        } else {
            elements += segment(wall.at(startMm), wall.at(endMm), style, target)
        }
        opening.swing?.let { elements += door(wall, opening, it, style, target) }
        return elements
    }

    private fun door(
        wall: WallFrame,
        opening: Opening,
        swing: DoorSwing,
        style: SceneStyle,
        target: SceneTarget.Opening,
    ): List<SceneElement> {
        val startMm = opening.offset.mm.toDouble()
        val endMm = opening.endOffset.mm.toDouble()
        val hingeLeft = swing == DoorSwing.HINGE_LEFT_INWARD || swing == DoorSwing.HINGE_LEFT_OUTWARD
        val inward = swing == DoorSwing.HINGE_LEFT_INWARD || swing == DoorSwing.HINGE_RIGHT_INWARD
        val face = if (inward) 0.0 else wall.thicknessMm
        val hinge = wall.at(if (hingeLeft) startMm else endMm, face)
        val jamb = wall.at(if (hingeLeft) endMm else startMm, face)
        val widthMm = endMm - startMm
        val side = if (inward) wall.outward * -1.0 else wall.outward
        val leafEnd = hinge + side * widthMm
        val closed = jamb - hinge
        val sweep = if (closed.cross(side) > 0.0) PI / 2.0 else -PI / 2.0
        return listOf(
            segment(hinge, leafEnd, style, target),
            SceneElement.Arc(hinge, widthMm, StrictMath.atan2(closed.y, closed.x), sweep, style, target),
        )
    }
}
