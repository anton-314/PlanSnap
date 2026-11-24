package app.plansnap.core.drawing.hit

import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/** Builders for hand-made scenes in the hit tests. */
internal object HitScenes {
    fun line(
        target: SceneTarget?,
        vararg points: Vec2,
    ): SceneElement =
        SceneElement.Polyline(persistentListOf(*points), closed = false, style = SceneStyle.WALL_LINE, target = target)

    fun fill(
        target: SceneTarget?,
        vertices: List<Vec2>,
    ): SceneElement = SceneElement.Fill(Polygon2(vertices), SceneStyle.WALL_BAND, target)

    /** A corner marker: a polyline of length zero at [at]. */
    fun cornerMark(
        target: SceneTarget.Corner,
        at: Vec2,
    ): SceneElement = line(target, at, at)

    /** Bounds of every point an element can be hit at with radius zero. */
    fun boundsOf(element: SceneElement): WorldBounds =
        when (element) {
            is SceneElement.Fill -> pointBounds(element.polygon.vertices)
            is SceneElement.Polyline -> pointBounds(element.points)
            is SceneElement.Arc -> square(element.center, element.radiusMm)
            is SceneElement.Label -> square(element.anchor, element.heightMm / 2.0)
        }

    fun room(
        id: String,
        elements: List<SceneElement>,
    ): RoomScene =
        RoomScene(
            roomId = RoomId(id),
            status = ShapeStatus.CONFIRMED,
            bounds = elements.map(::boundsOf).reduceOrNull(WorldBounds::union),
            elements = elements.toImmutableList(),
        )

    fun scene(vararg rooms: RoomScene): DrawingScene =
        DrawingScene(rooms.toList().toImmutableList(), rooms.mapNotNull { it.bounds }.reduceOrNull(WorldBounds::union))

    /**
     * Room [id] with the outline [vertices]: a body fill, one wall polyline per edge with the IDs `<id>-w<i>` and one
     * corner marker per vertex.
     */
    fun outlinedRoom(
        id: String,
        vertices: List<Vec2>,
        extra: List<SceneElement> = emptyList(),
    ): RoomScene {
        val roomId = RoomId(id)
        val walls =
            vertices.indices.map { i ->
                line(SceneTarget.Wall(roomId, WallId("$id-w$i")), vertices[i], vertices[(i + 1) % vertices.size])
            }
        val corners = vertices.mapIndexed { i, vertex -> cornerMark(SceneTarget.Corner(roomId, i), vertex) }
        return room(id, listOf(fill(SceneTarget.RoomBody(roomId), vertices)) + walls + corners + extra)
    }

    fun rectangle(
        minX: Double,
        minY: Double,
        maxX: Double,
        maxY: Double,
    ): List<Vec2> = listOf(Vec2(minX, minY), Vec2(maxX, minY), Vec2(maxX, maxY), Vec2(minX, maxY))

    private fun pointBounds(points: List<Vec2>): WorldBounds =
        WorldBounds(points.minOf { it.x }, points.minOf { it.y }, points.maxOf { it.x }, points.maxOf { it.y })

    private fun square(
        center: Vec2,
        halfSize: Double,
    ): WorldBounds = WorldBounds(center.x - halfSize, center.y - halfSize, center.x + halfSize, center.y + halfSize)
}
