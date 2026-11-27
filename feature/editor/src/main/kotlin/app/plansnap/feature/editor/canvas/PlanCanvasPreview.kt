package app.plansnap.feature.editor.canvas

import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.core.graphics.withSave
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.drawing.viewport.hitRadiusMm
import app.plansnap.core.render.EditorAppearance
import app.plansnap.core.render.SceneRenderer

/** Snap distance in dp (UX section 4, DOMAIN section 10), converted to millimeters by the canvas. */
internal const val SNAP_DISTANCE_DP: Double = 16.0

private const val GRID_STROKE_PX = 1f

/** The rooms of a frame, split into the ones that stay and the ones a preview moves. */
internal class SplitRooms(
    val still: List<RoomScene>,
    val moving: List<RoomScene>,
)

/**
 * Splits the visible [rooms] for drawing. Rooms that a [preview] moves are taken from [all] instead, because their
 * visibility must be judged at the moved place, not at the stored one. Without a preview, [rooms] stays untouched.
 */
internal fun splitRooms(
    rooms: List<RoomScene>,
    all: List<RoomScene>,
    preview: RoomTransformPreview?,
): SplitRooms {
    if (preview == null) return SplitRooms(rooms, emptyList())
    return SplitRooms(
        still = rooms.filter { it.roomId !in preview.roomIds },
        moving = all.filter { it.roomId in preview.roomIds },
    )
}

/** A [RoomTransformPreview] in screen pixels, prepared in composition so the draw pass computes nothing. */
internal class PreviewLayer(
    val shiftX: Float,
    val shiftY: Float,
    val clockwiseDegrees: Float,
    val pivotX: Float,
    val pivotY: Float,
) {
    /** Construction. */
    companion object {
        /** [preview] on the screen of [viewport]. Screen y points down, so the shift and the angle flip. */
        fun of(
            viewport: Viewport,
            preview: RoomTransformPreview,
        ): PreviewLayer {
            val scale = viewport.pixelsPerMm
            val pivot = preview.pivotMm - viewport.centerWorld
            return PreviewLayer(
                shiftX = (preview.translationMm.x * scale).toFloat(),
                shiftY = (-preview.translationMm.y * scale).toFloat(),
                clockwiseDegrees = -Math.toDegrees(preview.rotationRad).toFloat(),
                pivotX = (pivot.x * scale + viewport.widthPx / 2.0).toFloat(),
                pivotY = (viewport.heightPx / 2.0 - pivot.y * scale).toFloat(),
            )
        }
    }
}

/** What the draw pass of one frame needs besides the viewport, all of it prepared in composition. */
internal class PlanFrame(
    val rooms: SplitRooms,
    val layer: PreviewLayer?,
    val grid: PlanGrid.Lines?,
    val gridColor: Color,
)

/**
 * Prepares the frame: the rooms of [scene] that [math] finds visible, the transform of [preview] and the grid if
 * [showGrid] is set.
 */
@Composable
internal fun rememberFrame(
    math: PlanViewMath,
    scene: DrawingScene,
    viewport: Viewport,
    preview: RoomTransformPreview?,
    showGrid: Boolean,
): PlanFrame {
    val pixelsPerDp = LocalDensity.current.density
    val gridColor = PlanSnapTheme.colors.planGrid
    val cullingMarginPx = FIT_MARGIN_DP * pixelsPerDp * 2
    val rooms = remember(scene, viewport) { math.visibleRooms(scene.rooms, viewport, cullingMarginPx) }
    val split = remember(rooms, scene, preview?.roomIds) { splitRooms(rooms, scene.rooms, preview) }
    val layer = remember(viewport, preview) { preview?.let { PreviewLayer.of(viewport, it) } }
    val grid = rememberGrid(showGrid, viewport, pixelsPerDp)
    return remember(split, layer, grid, gridColor) { PlanFrame(split, layer, grid, gridColor) }
}

/** Draws the whole frame: the grid below, the rooms that stay, then the moving ones through the preview transform. */
internal fun DrawScope.drawPlan(
    renderer: SceneRenderer,
    frame: PlanFrame,
    viewport: Viewport,
    appearance: EditorAppearance,
) {
    frame.grid?.let { drawGrid(it, frame.gridColor) }
    drawIntoCanvas { renderer.drawRooms(it.nativeCanvas, frame, viewport, appearance) }
}

private fun SceneRenderer.drawRooms(
    canvas: android.graphics.Canvas,
    frame: PlanFrame,
    viewport: Viewport,
    appearance: EditorAppearance,
) {
    drawEditor(canvas, frame.rooms.still, viewport, appearance)
    val layer = frame.layer
    if (layer != null && frame.rooms.moving.isNotEmpty()) {
        canvas.withSave {
            translate(layer.shiftX, layer.shiftY)
            rotate(layer.clockwiseDegrees, layer.pivotX, layer.pivotY)
            drawEditor(this, frame.rooms.moving, viewport, appearance)
        }
    }
}

/**
 * The room that a long press at [at] would move, with the snap parameters of the current zoom, `null` if there is no
 * room within the touch radius.
 */
internal fun PlanViewMath.roomGestureStart(
    scene: DrawingScene,
    viewport: Viewport,
    at: Offset,
    pixelsPerDp: Double,
): RoomGestureStart? =
    tap(scene, viewport, at.x.toDouble(), at.y.toDouble(), pixelsPerDp).target?.let { target ->
        RoomGestureStart(
            roomId = target.roomId,
            snapDistanceMm = viewport.hitRadiusMm(SNAP_DISTANCE_DP, pixelsPerDp),
            gridStepMm = PlanGrid.stepMm(viewport.pixelsPerMm, pixelsPerDp),
        )
    }

/**
 * Listens for the long-press gesture on a room if [callbacks] are active. [scene] and [viewport] are read at the time
 * of the touch.
 */
@Suppress("LongParameterList") // The collaborators of the canvas that the gesture needs.
internal fun Modifier.roomGestureInput(
    callbacks: State<RoomGestureCallbacks>,
    math: PlanViewMath,
    density: Float,
    scene: () -> DrawingScene,
    viewport: () -> Viewport,
): Modifier =
    if (callbacks.value.isActive) {
        pointerInput(density) {
            detectRoomGestures(
                begin = { at ->
                    val start = math.roomGestureStart(scene(), viewport(), at, density.toDouble())
                    if (start != null) callbacks.value.onStart(start)
                    start != null
                },
                pixelsPerMm = { viewport().pixelsPerMm },
                callbacks = callbacks,
            )
        }
    } else {
        this
    }

/** The grid lines of [viewport] if [show] is set, `null` otherwise. */
@Composable
internal fun rememberGrid(
    show: Boolean,
    viewport: Viewport,
    pixelsPerDp: Float,
): PlanGrid.Lines? =
    remember(show, viewport, pixelsPerDp) {
        if (show && viewport.widthPx > 0 && viewport.heightPx > 0) {
            PlanGrid.lines(viewport, PlanGrid.stepMm(viewport.pixelsPerMm, pixelsPerDp.toDouble()))
        } else {
            null
        }
    }

/** Draws the grid [lines] across the whole drawing area in [color], without allocating. */
internal fun DrawScope.drawGrid(
    lines: PlanGrid.Lines,
    color: Color,
) {
    var x = lines.firstX
    while (x <= size.width) {
        drawLine(color, Offset(x, 0f), Offset(x, size.height), GRID_STROKE_PX)
        x += lines.spacing
    }
    var y = lines.firstY
    while (y <= size.height) {
        drawLine(color, Offset(0f, y), Offset(size.width, y), GRID_STROKE_PX)
        y += lines.spacing
    }
}
