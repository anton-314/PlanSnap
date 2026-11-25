package app.plansnap.core.drawing.scene

import app.plansnap.core.drawing.scene.SceneMetrics.ROOM_LABEL_HEIGHT_MM
import app.plansnap.core.drawing.scene.SceneMetrics.ROOM_LABEL_LINE_MM
import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.abs

/** Room labels and the point they are centered on. */
internal object RoomLabels {
    /** Smallest twice-area for which the area centroid is used, below it the vertex mean. */
    private const val MIN_TWICE_AREA_MM2 = 1.0

    /** Denominator of the centroid formula `C_y = Σ (y_i + y_{i+1}) · c_i / (3 · 2A)`. */
    private const val CENTROID_DENOMINATOR = 3.0

    /** One line of the room label. */
    data class Line(
        val text: LabelText,
        val style: SceneStyle,
    )

    /**
     * Stacks [lines] vertically around [anchor], first line on top, upright in world coordinates. Every line selects
     * the room body.
     */
    fun stacked(
        anchor: Vec2,
        lines: List<Line>,
        target: SceneTarget.RoomBody,
    ): List<SceneElement> =
        lines.mapIndexed { i, line ->
            val shift = ((lines.size - 1) / 2.0 - i) * ROOM_LABEL_LINE_MM
            SceneElement.Label(
                anchor = Vec2(anchor.x, anchor.y + shift),
                text = line.text,
                rotationRad = 0.0,
                heightMm = ROOM_LABEL_HEIGHT_MM,
                style = line.style,
                target = target,
            )
        }

    /**
     * Point inside the simple polygon [vertices]: the middle of the widest interior span of the horizontal line through
     * the area centroid. The centroid itself may lie outside a concave room. Crossings use the half-open rule of the
     * even-odd test, so a vertex on the line is counted once.
     */
    fun interiorPoint(vertices: List<Vec2>): Vec2 {
        val y = centroidY(vertices)
        val crossings = mutableListOf<Double>()
        var previous = vertices.last()
        for (current in vertices) {
            if ((current.y > y) != (previous.y > y)) {
                crossings += current.x + (y - current.y) * (previous.x - current.x) / (previous.y - current.y)
            }
            previous = current
        }
        crossings.sort()
        val widest =
            crossings
                .chunked(2)
                .filter { it.size == 2 }
                .maxByOrNull { it[1] - it[0] }
                ?: return mean(vertices)
        return Vec2((widest[0] + widest[1]) / 2.0, y)
    }

    /** Mean of [points], the label anchor of a room without closed shape. */
    fun mean(points: List<Vec2>): Vec2 = Vec2(points.sumOf { it.x } / points.size, points.sumOf { it.y } / points.size)

    /** Y of the area centroid, computed relative to the first vertex for precision far from the origin. */
    private fun centroidY(vertices: List<Vec2>): Double {
        val base = vertices.first()
        var twiceArea = 0.0
        var moment = 0.0
        for (i in vertices.indices) {
            val a = vertices[i] - base
            val b = vertices[(i + 1) % vertices.size] - base
            val cross = a.cross(b)
            twiceArea += cross
            moment += (a.y + b.y) * cross
        }
        return if (abs(twiceArea) <
            MIN_TWICE_AREA_MM2
        ) {
            mean(vertices).y
        } else {
            base.y + moment / (CENTROID_DENOMINATOR * twiceArea)
        }
    }
}
