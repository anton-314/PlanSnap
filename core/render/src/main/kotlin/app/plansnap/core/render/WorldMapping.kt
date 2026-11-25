package app.plansnap.core.render

import app.plansnap.core.geometry.primitives.Vec2
import kotlin.math.abs
import kotlin.math.hypot

/**
 * Affine mapping from world millimeters to canvas coordinates, `canvas = scale · world + offset` per axis. It is
 * derived once per frame from [app.plansnap.core.drawing.viewport.ViewportTransform] (editor) or
 * [app.plansnap.core.drawing.plot.PlotLayout] (export), so the renderer maps points with plain arithmetic and no
 * allocation. The y scale is negative because world y points up and canvas y down.
 */
internal class WorldMapping {
    private var scaleX = 1.0
    private var offsetX = 0.0
    private var scaleY = -1.0
    private var offsetY = 0.0

    /** Whether the world y axis points up on the canvas, which mirrors angles. */
    val mirrorsY: Boolean get() = scaleY < 0.0

    /** Canvas pixels (or points) per world millimeter. */
    val unitsPerMm: Double get() = abs(scaleX)

    /**
     * Derives the mapping from the canvas position (`originX`, `originY`) of the world point [center] and the canvas
     * position (`stepX`, `stepY`) of the world point one millimeter right and up of it.
     */
    fun set(
        center: Vec2,
        originX: Double,
        originY: Double,
        stepX: Double,
        stepY: Double,
    ) {
        scaleX = stepX - originX
        scaleY = stepY - originY
        offsetX = originX - scaleX * center.x
        offsetY = originY - scaleY * center.y
    }

    /** Canvas x of a world x. */
    fun x(worldX: Double): Float = (scaleX * worldX + offsetX).toFloat()

    /** Canvas y of a world y. */
    fun y(worldY: Double): Float = (scaleY * worldY + offsetY).toFloat()

    /** Canvas length of the segment from [from] to [to]. */
    fun length(
        from: Vec2,
        to: Vec2,
    ): Float = hypot((to.x - from.x) * scaleX, (to.y - from.y) * scaleY).toFloat()
}
