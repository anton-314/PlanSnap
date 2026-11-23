package app.plansnap.core.drawing.viewport

import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.geometry.primitives.Vec2
import kotlinx.collections.immutable.ImmutableList

/** Point in screen pixels: x to the right, y down. Both components are finite. */
data class ScreenPoint(
    val x: Double,
    val y: Double,
) {
    init {
        require(x.isFinite() && y.isFinite()) { "Screen point must be finite: ($x, $y)" }
    }
}

/**
 * View of the world on the screen.
 *
 * @property centerWorld world point shown at the center of the screen.
 * @property pixelsPerMm zoom, positive and finite.
 * @property widthPx screen width, not negative.
 * @property heightPx screen height, not negative.
 */
data class Viewport(
    val centerWorld: Vec2,
    val pixelsPerMm: Double,
    val widthPx: Int,
    val heightPx: Int,
) {
    init {
        require(pixelsPerMm.isFinite() && pixelsPerMm > 0.0) { "Invalid zoom: $pixelsPerMm" }
        require(widthPx >= 0 && heightPx >= 0) { "Screen size must not be negative" }
    }
}

/** The only conversion between world and screen in the editor (ARCHITECTURE section 8). */
interface ViewportTransform {
    /** Maps a world point to the screen. */
    fun worldToScreen(
        viewport: Viewport,
        world: Vec2,
    ): ScreenPoint

    /** Maps a screen point to the world, the inverse of [worldToScreen]. */
    fun screenToWorld(
        viewport: Viewport,
        screen: ScreenPoint,
    ): Vec2

    /** Scales by [factor], positive and finite, keeping the world point under [focus] stable. */
    fun zoomAround(
        viewport: Viewport,
        focus: ScreenPoint,
        factor: Double,
    ): Viewport

    /** Moves the view by a screen delta. */
    fun panBy(
        viewport: Viewport,
        deltaXPx: Double,
        deltaYPx: Double,
    ): Viewport

    /** Fits [bounds] into the screen of [viewport]. */
    fun fit(
        viewport: Viewport,
        bounds: WorldBounds,
    ): Viewport

    /** Returns the visible world area. */
    fun visibleBounds(viewport: Viewport): WorldBounds
}

/** Selects the rooms to draw. */
fun interface SceneCuller {
    /** Returns the rooms of [scene] intersecting [visible], in scene order. */
    fun visibleRooms(
        scene: DrawingScene,
        visible: WorldBounds,
    ): ImmutableList<RoomScene>
}

/** Hit testing in world coordinates (ARCHITECTURE section 8). */
fun interface HitTester {
    /** Returns the best target within [radiusMm] of [world], or `null`. */
    fun hitTest(
        scene: DrawingScene,
        world: Vec2,
        radiusMm: Double,
    ): SceneTarget?
}
