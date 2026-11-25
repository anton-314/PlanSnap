package app.plansnap.feature.editor.canvas

import app.plansnap.core.drawing.hit.DefaultHitTester
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.DEFAULT_HIT_RADIUS_DP
import app.plansnap.core.drawing.viewport.DefaultViewportTransform
import app.plansnap.core.drawing.viewport.HitTester
import app.plansnap.core.drawing.viewport.ScreenPoint
import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.drawing.viewport.hitRadiusMm
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.feature.editor.state.PlanView

/** Result of a tap on the plan: the world point under the finger and the best hit there. */
data class PlanTap(
    val world: Vec2,
    val target: SceneTarget?,
)

/**
 * One step of a pan and pinch gesture.
 *
 * @property focusX x of the centroid of the fingers after the step, in pixels.
 * @property focusY y of the centroid of the fingers after the step, in pixels.
 * @property panX movement of the centroid along x, in pixels.
 * @property panY movement of the centroid along y, in pixels.
 * @property zoom scale factor of the step, 1 for a pure pan.
 */
internal data class GestureStep(
    val focusX: Double,
    val focusY: Double,
    val panX: Double,
    val panY: Double,
    val zoom: Double,
) {
    /** Whether the step can be applied to a viewport. */
    val isFinite: Boolean get() = listOf(focusX, focusY, panX, panY).all(Double::isFinite)

    /** Whether the step changes the zoom. */
    val scales: Boolean get() = zoom.isFinite() && zoom > 0.0 && zoom != 1.0
}

/**
 * Gesture arithmetic of the plan canvas, free of Compose and Android so it can be tested on the JVM. Every
 * conversion between screen and world goes through [DefaultViewportTransform] (ARCHITECTURE section 8); this class
 * only decides which of its operations a gesture maps to. Nothing here can change the model, it only produces views.
 *
 * @property transform the viewport transform, with the zoom limits of UX section 4.
 * @property hitTester hit testing in world coordinates.
 */
internal class PlanViewMath(
    private val transform: DefaultViewportTransform = DefaultViewportTransform(),
    private val hitTester: HitTester = DefaultHitTester(),
) {
    /** Returns the viewport of [view] on a screen of [widthPx] by [heightPx]. */
    fun viewport(
        view: PlanView,
        widthPx: Int,
        heightPx: Int,
    ): Viewport = Viewport(Vec2(view.centerXMm, view.centerYMm), view.pixelsPerMm, widthPx, heightPx)

    /** Returns the part of [viewport] that is stored in the saved state. */
    fun planView(viewport: Viewport): PlanView =
        PlanView(viewport.centerWorld.x, viewport.centerWorld.y, viewport.pixelsPerMm)

    /**
     * Applies one [step] of a pan and pinch gesture: the content follows the centroid and is scaled around its new
     * position. The zoom stays within the limits of the transform. A step with non-finite input leaves [viewport]
     * unchanged, and so does a zoom that is not positive and finite.
     */
    fun gesture(
        viewport: Viewport,
        step: GestureStep,
    ): Viewport {
        if (!step.isFinite) return viewport
        val panned = transform.panBy(viewport, step.panX, step.panY)
        return if (step.scales) {
            transform.zoomAround(panned, ScreenPoint(step.focusX, step.focusY), step.zoom)
        } else {
            panned
        }
    }

    /**
     * Fits [bounds] into [viewport] and leaves [marginPx] on every side (UX section 4). The core transform fits
     * without margin, so the fit runs on a screen reduced by `2 * marginPx` and the original size is restored, which
     * keeps the plan centered. Returns `null` for an empty plan, because then the view must not change.
     */
    fun fit(
        viewport: Viewport,
        bounds: WorldBounds?,
        marginPx: Double,
    ): Viewport? {
        if (bounds == null) return null
        val reduced =
            viewport.copy(
                widthPx = (viewport.widthPx - 2.0 * marginPx).toInt().coerceAtLeast(1),
                heightPx = (viewport.heightPx - 2.0 * marginPx).toInt().coerceAtLeast(1),
            )
        return transform.fit(reduced, bounds).copy(widthPx = viewport.widthPx, heightPx = viewport.heightPx)
    }

    /**
     * Returns the rooms that can be seen in [viewport], widened by [marginPx] because editor text is drawn in a
     * constant screen size and may reach out of its room. Returns [rooms] itself if every room is visible. The call
     * belongs in composition, not in the draw pass.
     */
    fun visibleRooms(
        rooms: List<RoomScene>,
        viewport: Viewport,
        marginPx: Double,
    ): List<RoomScene> {
        val visible = transform.visibleBounds(viewport)
        val margin = marginPx / viewport.pixelsPerMm
        val area =
            WorldBounds(visible.minX - margin, visible.minY - margin, visible.maxX + margin, visible.maxY + margin)
        val seen = rooms.filter { room -> room.bounds?.intersects(area) ?: false }
        return if (seen.size == rooms.size) rooms else seen
    }

    /**
     * Resolves a tap at the screen point ([xPx], [yPx]) to a world point and the best element within 24 dp (UX section
     * 4). [pixelsPerDp] is the screen density.
     */
    fun tap(
        scene: DrawingScene,
        viewport: Viewport,
        xPx: Double,
        yPx: Double,
        pixelsPerDp: Double,
    ): PlanTap {
        val world = transform.screenToWorld(viewport, ScreenPoint(xPx, yPx))
        val radius = viewport.hitRadiusMm(DEFAULT_HIT_RADIUS_DP, pixelsPerDp)
        return PlanTap(world, hitTester.hitTest(scene, world, radius))
    }
}
