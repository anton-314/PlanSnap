package app.plansnap.core.drawing.hit

import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.HitTester
import app.plansnap.core.geometry.polygon.DefaultPolygonAnalyzer
import app.plansnap.core.geometry.polygon.PolygonAnalyzer
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.isCloseTo
import app.plansnap.core.model.RoomId
import kotlin.math.abs
import kotlin.math.max

/**
 * Hit testing with the priority rule of DOMAIN section 11.
 *
 * Every scene element with a [SceneElement.target] is a candidate if its distance to the point, as defined below, is
 * at most the radius. Elements without target are decoration and never hit. The best candidate is chosen in this
 * order:
 *
 * 1. **Class:** opening before corner before wall before diagonal before room interior. DOMAIN names no rank for
 *    diagonals; they rank behind walls, which they connect, and before the interior, which always contains them.
 * 2. **Distance:** the smallest distance of any element of the target wins.
 * 3. **Area,** only for the room interior: the room whose largest [SceneElement.Fill] with a
 *    [SceneTarget.RoomBody] target has the smaller area wins. A room without such a fill counts as infinitely large.
 * 4. **ID:** the smallest ID wins, compared as strings by UTF-16 code units: the opening, wall or diagonal ID, then
 *    the room ID, then the corner index.
 *
 * Distances and areas count as tied if they differ by at most [TIE_RELATIVE] times `max(1, |a|, |b|)`. Ties are
 * resolved against the minimum, so the result does not depend on the order of rooms or elements.
 *
 * Distances: zero inside a filled polygon, otherwise the Euclidean distance to the drawn outline, polyline or arc.
 * A label counts as a disc around its anchor with its height as diameter, because the text width depends on the font.
 * Rooms whose bounds are farther than the radius are skipped without looking at their elements.
 *
 * @property polygonAnalyzer computes the areas of the room fills.
 */
class DefaultHitTester(
    private val polygonAnalyzer: PolygonAnalyzer = DefaultPolygonAnalyzer(),
) : HitTester {
    /** @throws IllegalArgumentException if [radiusMm] is negative, NaN or infinite. */
    override fun hitTest(
        scene: DrawingScene,
        world: Vec2,
        radiusMm: Double,
    ): SceneTarget? {
        require(radiusMm.isFinite() && radiusMm >= 0.0) { "Invalid hit radius: $radiusMm" }
        val distances = candidateDistances(scene, world, radiusMm)
        if (distances.isEmpty()) return null
        val bestClass = distances.keys.minOf { classOf(it) }
        var finalists = keepMinimal(distances.filterKeys { classOf(it) == bestClass })
        if (bestClass == HitClass.ROOM_BODY && finalists.size > 1) {
            finalists = keepMinimal(finalists.mapValues { (target, _) -> bodyArea(scene, target.roomId) })
        }
        return finalists.keys.minWith(ID_ORDER)
    }

    /** Returns the smallest distance of every target with an element within [radiusMm] of [world]. */
    private fun candidateDistances(
        scene: DrawingScene,
        world: Vec2,
        radiusMm: Double,
    ): Map<SceneTarget, Double> {
        val reach = WorldBounds(world.x - radiusMm, world.y - radiusMm, world.x + radiusMm, world.y + radiusMm)
        val distances = LinkedHashMap<SceneTarget, Double>()
        val reachable = scene.rooms.filter { room -> room.bounds?.intersects(reach) == true }
        for (element in reachable.flatMap { it.elements }) {
            val target = element.target ?: continue
            val distance = HitGeometry.distance(element, world)
            if (distance <= radiusMm) distances[target] = minOf(distances[target] ?: distance, distance)
        }
        return distances
    }

    private fun bodyArea(
        scene: DrawingScene,
        roomId: RoomId,
    ): Double =
        scene.rooms
            .asSequence()
            .flatMap { it.elements }
            .filterIsInstance<SceneElement.Fill>()
            .filter { it.target == SceneTarget.RoomBody(roomId) }
            .maxOfOrNull { polygonAnalyzer.areaMm2(it.polygon) }
            ?: Double.POSITIVE_INFINITY

    /** Priority classes, best first. */
    private enum class HitClass {
        OPENING,
        CORNER,
        WALL,
        DIAGONAL,
        ROOM_BODY,
    }

    /** Constants. */
    companion object {
        /** Relative tolerance under which two distances or two areas count as equal. */
        const val TIE_RELATIVE: Double = 1e-9

        private val ID_ORDER: Comparator<SceneTarget> =
            compareBy<SceneTarget>({ primaryId(it) }, { it.roomId.value }, { (it as? SceneTarget.Corner)?.index ?: 0 })

        private fun classOf(target: SceneTarget): HitClass =
            when (target) {
                is SceneTarget.Opening -> HitClass.OPENING
                is SceneTarget.Corner -> HitClass.CORNER
                is SceneTarget.Wall -> HitClass.WALL
                is SceneTarget.Diagonal -> HitClass.DIAGONAL
                is SceneTarget.RoomBody -> HitClass.ROOM_BODY
            }

        private fun primaryId(target: SceneTarget): String =
            when (target) {
                is SceneTarget.Opening -> target.openingId.value
                is SceneTarget.Wall -> target.wallId.value
                is SceneTarget.Diagonal -> target.diagonalId.value
                is SceneTarget.Corner, is SceneTarget.RoomBody -> target.roomId.value
            }

        /** Keeps the entries whose value ties with the minimum. Infinite values only tie with each other. */
        private fun keepMinimal(values: Map<SceneTarget, Double>): Map<SceneTarget, Double> {
            val minimum = values.values.min()
            return values.filterValues { isTie(it, minimum) }
        }

        private fun isTie(
            value: Double,
            minimum: Double,
        ): Boolean =
            when {
                minimum.isInfinite() || value.isInfinite() -> minimum.isInfinite() && value.isInfinite()
                else -> value.isCloseTo(minimum, TIE_RELATIVE * max(1.0, max(abs(value), abs(minimum))))
            }
    }
}
