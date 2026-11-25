package app.plansnap.core.render

import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.toImmutableList

/** Hand-built scenes that mimic the output of the scene builder for the render tests. */
internal object TestScenes {
    private val roomId = RoomId("room")
    private val body = SceneTarget.RoomBody(roomId)

    fun polyline(
        style: SceneStyle,
        vararg points: Vec2,
        target: SceneTarget? = null,
    ): SceneElement = SceneElement.Polyline(points.toList().toImmutableList(), false, style, target)

    fun rect(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
        style: SceneStyle,
    ): SceneElement.Fill =
        SceneElement.Fill(
            Polygon2(listOf(Vec2(minX, minY), Vec2(maxX, minY), Vec2(maxX, maxY), Vec2(minX, maxY))),
            style,
            body,
        )

    fun label(
        x: Double,
        y: Double,
        text: LabelText,
        style: SceneStyle,
        heightMm: Double = 150.0,
    ): SceneElement.Label = SceneElement.Label(Vec2(x, y), text, 0.0, heightMm, style, body)

    /**
     * A room label block around [anchor] like the scene builder makes it: lines of 200 mm, 300 mm apart, first on top,
     * all sharing [anchor] as block anchor.
     */
    fun block(
        anchor: Vec2,
        vararg lines: Pair<LabelText, SceneStyle>,
    ): List<SceneElement.Label> =
        lines.mapIndexed { i, (text, style) ->
            val shift = ((lines.size - 1) / 2.0 - i) * LINE_MM
            SceneElement.Label(Vec2(anchor.x, anchor.y + shift), text, 0.0, ROOM_LABEL_MM, style, body, anchor)
        }

    fun scene(
        status: ShapeStatus,
        elements: List<SceneElement>,
        bounds: WorldBounds = WorldBounds(minX = -2000.0, minY = -1500.0, maxX = 2000.0, maxY = 1500.0),
    ): RoomScene = RoomScene(roomId, status, bounds, elements.toImmutableList())

    /** A 4 m by 3 m room around the origin with four wall bands of 200 mm, dimensions and name. */
    fun room(
        status: ShapeStatus,
        withOpenings: Boolean = false,
    ): RoomScene {
        val unconfirmed = status == ShapeStatus.NEEDS_ADJUSTMENT || status == ShapeStatus.PROPOSED
        val chain = status == ShapeStatus.OPEN || status == ShapeStatus.CONFLICT
        val elements = ArrayList<SceneElement>()
        val corners = listOf(Vec2(-2000.0, -1500.0), Vec2(2000.0, -1500.0), Vec2(2000.0, 1500.0), Vec2(-2000.0, 1500.0))
        when {
            chain -> {
                val style = if (status == ShapeStatus.CONFLICT) SceneStyle.CONFLICT else SceneStyle.WALL_LINE
                for (i in 0 until if (status == ShapeStatus.OPEN) 3 else 4) {
                    elements += polyline(style, corners[i], corners[(i + 1) % 4], target = wall(i))
                }
            }

            else -> {
                elements +=
                    rect(
                        -2000.0,
                        -1500.0,
                        2000.0,
                        1500.0,
                        if (unconfirmed) SceneStyle.ROOM_BODY_UNCONFIRMED else SceneStyle.ROOM_BODY,
                    )
                if (unconfirmed) {
                    for (i in 0 until 4) {
                        elements +=
                            polyline(SceneStyle.WALL_UNCONFIRMED, corners[i], corners[(i + 1) % 4], target = wall(i))
                    }
                } else {
                    elements += rect(-2100.0, -1600.0, 2100.0, -1400.0, SceneStyle.WALL_BAND)
                    elements += rect(-2100.0, 1400.0, 2100.0, 1600.0, SceneStyle.WALL_BAND)
                    elements += rect(-2100.0, -1400.0, -1900.0, 1400.0, SceneStyle.WALL_BAND)
                    elements += rect(1900.0, -1400.0, 2100.0, 1400.0, SceneStyle.WALL_BAND)
                }
            }
        }
        if (withOpenings) {
            elements += rect(-600.0, -1600.0, 300.0, -1400.0, SceneStyle.OPENING)
            elements += SceneElement.Arc(Vec2(-600.0, -1400.0), 900.0, 0.0, Math.PI / 2.0, SceneStyle.OPENING, null)
            elements += rect(800.0, 1400.0, 1700.0, 1600.0, SceneStyle.OPENING_OUT_OF_BOUNDS)
        }
        elements += polyline(SceneStyle.DIMENSION, Vec2(-2000.0, -1900.0), Vec2(2000.0, -1900.0), target = wall(0))
        elements += label(0.0, -2050.0, LabelText.Dimension(4000.0), SceneStyle.DIMENSION).copy(target = wall(0))
        elements += polyline(SceneStyle.DIAGONAL, corners[0], corners[2], target = SceneTarget.Corner(roomId, 0))
        elements += roomLabel(status, unconfirmed)
        corners.forEachIndexed { i, c ->
            elements += polyline(SceneStyle.WALL_LINE, c, c, target = SceneTarget.Corner(roomId, i))
        }
        return scene(status, elements)
    }

    /** Name and type, then the mark of an unconfirmed or conflict room, with the name at y = 500 mm. */
    private fun roomLabel(
        status: ShapeStatus,
        unconfirmed: Boolean,
    ): List<SceneElement.Label> {
        val lines =
            buildList {
                add(LabelText.Literal("Küche") to SceneStyle.LABEL)
                add(LabelText.RoomType(RoomTypeKey("kitchen")) to SceneStyle.LABEL)
                if (unconfirmed) add(LabelText.ShapeNotConfirmed to SceneStyle.WARNING)
                if (status == ShapeStatus.CONFLICT) add(LabelText.ShapeConflict to SceneStyle.CONFLICT)
            }
        val blockY = if (lines.size == 2) 350.0 else 200.0
        return block(Vec2(0.0, blockY), *lines.toTypedArray())
    }

    private fun wall(index: Int): SceneTarget = SceneTarget.Wall(roomId, WallId("w$index"))

    private const val LINE_MM = 300.0
    private const val ROOM_LABEL_MM = 200.0
}
