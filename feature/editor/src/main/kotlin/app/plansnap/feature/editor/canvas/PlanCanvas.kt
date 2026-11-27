package app.plansnap.feature.editor.canvas

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.render.EditorAppearance
import app.plansnap.core.render.LabelTextFormatter
import app.plansnap.core.render.SceneRenderer
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.state.PlanView
import app.plansnap.feature.editor.state.WallPreview
import kotlinx.collections.immutable.toImmutableList

/** Distance between the plan and the screen edge after "fit plan", in dp (UX section 4). */
internal const val FIT_MARGIN_DP: Double = 24.0

/** Zoom shown while no view exists and the plan is empty, so the first wall appears at a readable size. */
private const val DEFAULT_PIXELS_PER_MM = 0.1

/**
 * The plan of the editor: draws the scene, pans and zooms it, fits it on double tap and resolves taps to elements.
 *
 * The composable holds no model state. Gestures only produce a [PlanView] for [onViewChange]; the model is never
 * touched (UX section 4). The scene arrives ready to draw from the state layer, so every confirmed wall appears with
 * the next frame, and [preview] replaces the stored scene of its room, so the wall that is being typed appears
 * before it is confirmed. The one piece of local state is a [PlanViewBuffer] that keeps gestures smooth between two
 * frames of the state layer. The draw pass allocates nothing of its own: `SceneRenderer` is the only code in it, and
 * everything else (viewport, visible rooms, texts) is prepared in composition.
 *
 * - **Pan:** one finger drags the plan, two fingers pan and pinch around their centroid, within the zoom limits.
 * - **Double tap:** fits the whole plan with 24 dp margin on every side. An empty plan leaves the view unchanged.
 * - **Tap:** hit-tests with a radius of 24 dp and reports the result to [onTap].
 * - **Long press on a room (optional):** with active [roomGestures], one finger moves the room and a second finger
 *   twists it. The callbacks only report; [roomPreview] draws the result without any scene or model change.
 * - **First view:** if [view] is `null` and the plan has content, the canvas reports a fitted view once it knows its
 *   size.
 *
 * Wire it in the editor screen with `onViewChange = { onIntent(CoreIntent.ViewChanged(it)) }`.
 *
 * @param scene confirmed scene of the project, `null` while it is not evaluated.
 * @param view pan and zoom, `null` until the canvas has set it.
 * @param onViewChange called with the new view while a gesture runs.
 * @param onTap called with the world point and the best hit of a tap, the hit is `null` on empty ground.
 * @param modifier layout of the canvas. The canvas should fill the plan area.
 * @param preview wall that is being typed, drawn instead of the stored scene of its room.
 * @param lengthUnit unit of dimension texts.
 * @param roomTypeName display name of a room type, the key itself by default.
 * @param roomGestures callbacks of the long-press gesture on a room, disabled by default.
 * @param roomPreview rigid transform drawn on the rooms that are being dragged, `null` for none.
 * @param showGrid whether the background grid is drawn.
 */
@Suppress("LongParameterList") // Compose API: the content, the callbacks and the optional display settings.
@Composable
fun PlanCanvas(
    scene: DrawingScene?,
    view: PlanView?,
    onViewChange: (PlanView) -> Unit,
    onTap: (PlanTap) -> Unit,
    modifier: Modifier = Modifier,
    preview: WallPreview? = null,
    lengthUnit: LengthUnit = LengthUnit.CENTIMETER,
    roomTypeName: (RoomTypeKey) -> String = { it.value },
    roomGestures: RoomGestureCallbacks = RoomGestureCallbacks.None,
    roomPreview: RoomTransformPreview? = null,
    showGrid: Boolean = false,
) {
    val math = remember { PlanViewMath() }
    val shown = remember(scene, preview) { scene.withPreview(preview) }
    val buffer = remember { PlanViewBuffer(view) }
    LaunchedEffect(view) { buffer.sync(view) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    val density = LocalDensity.current
    val marginPx = FIT_MARGIN_DP * density.density
    val currentScene by rememberUpdatedState(shown)
    val currentOnViewChange by rememberUpdatedState(onViewChange)
    val currentOnTap by rememberUpdatedState(onTap)
    val currentRoomGestures = rememberUpdatedState(roomGestures)

    fun viewportNow(): Viewport = math.viewport(buffer.current ?: DEFAULT_VIEW, size.width, size.height)

    fun publish(next: Viewport) {
        val planView = math.planView(next)
        if (planView != buffer.current) {
            buffer.emit(planView)
            currentOnViewChange(planView)
        }
    }

    fun fitNow() {
        math.fit(viewportNow(), currentScene.bounds, marginPx)?.let(::publish)
    }

    val hasContent = shown.bounds != null
    val hasSize = size.width > 0 && size.height > 0
    LaunchedEffect(hasSize, hasContent, buffer.current == null) {
        if (buffer.current == null && hasContent && hasSize) fitNow()
    }

    val viewport = math.viewport(buffer.current ?: DEFAULT_VIEW, size.width, size.height)
    val frame = rememberFrame(math, shown, viewport, roomPreview, showGrid)
    val renderer = rememberSceneRenderer(lengthUnit, roomTypeName)
    val appearance = rememberAppearance()
    val description = planDescription(shown)
    val fitLabel = stringResource(R.string.canvas_action_fit)

    Canvas(
        modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface)
            .clipToBounds()
            .onSizeChanged { size = it }
            .semantics {
                contentDescription = description
                customActions = listOf(CustomAccessibilityAction(fitLabel) { fitNow().let { true } })
            }.pointerInput(density.density) {
                detectTransformGestures { centroid, pan, zoom, _ ->
                    val step =
                        GestureStep(
                            centroid.x.toDouble(),
                            centroid.y.toDouble(),
                            pan.x.toDouble(),
                            pan.y.toDouble(),
                            zoom.toDouble(),
                        )
                    publish(math.gesture(viewportNow(), step))
                }
            }.pointerInput(density.density) {
                detectTapGestures(
                    onDoubleTap = { fitNow() },
                    onTap = { at ->
                        val pixelsPerDp = density.density.toDouble()
                        currentOnTap(
                            math.tap(currentScene, viewportNow(), at.x.toDouble(), at.y.toDouble(), pixelsPerDp),
                        )
                    },
                )
            }.roomGestureInput(currentRoomGestures, math, density.density, { currentScene }, { viewportNow() }),
    ) {
        drawPlan(renderer, frame, viewport, appearance)
    }
}

private val DEFAULT_VIEW = PlanView(0.0, 0.0, DEFAULT_PIXELS_PER_MM)

@Composable
private fun rememberSceneRenderer(
    unit: LengthUnit,
    roomTypeName: (RoomTypeKey) -> String,
): SceneRenderer {
    val resources = LocalResources.current
    val configuration = LocalConfiguration.current
    return remember(resources, configuration, unit, roomTypeName) {
        val locale = configuration.locales[0]
        SceneRenderer(LabelTextFormatter(resources, locale, unit, roomTypeName))
    }
}

@Composable
private fun rememberAppearance(): EditorAppearance {
    val colors = PlanSnapTheme.colors
    val surface = MaterialTheme.colorScheme.surface
    val density = LocalDensity.current
    return remember(colors, surface, density) {
        EditorAppearance(colors.toRenderPalette(surface), density.density, density.fontScale)
    }
}

@Composable
private fun planDescription(scene: DrawingScene): String {
    val rooms = scene.rooms.size
    val walls = remember(scene) { scene.wallCount() }
    return if (rooms == 0) {
        stringResource(R.string.canvas_description_empty)
    } else {
        stringResource(
            R.string.canvas_description,
            pluralStringResource(R.plurals.canvas_rooms, rooms, rooms),
            pluralStringResource(R.plurals.canvas_walls, walls, walls),
        )
    }
}

private fun DrawingScene.wallCount(): Int =
    rooms
        .flatMap { room -> room.elements.mapNotNull { (it.target as? SceneTarget.Wall)?.wallId } }
        .distinct()
        .size

/** The scene that is drawn: [preview] takes the place of the stored scene of its room, or is added if it is new. */
private fun DrawingScene?.withPreview(preview: WallPreview?): DrawingScene {
    val base = this ?: DrawingScene(emptyList<RoomScene>().toImmutableList(), null)
    if (preview == null) return base
    val replaced = base.rooms.map { if (it.roomId == preview.roomId) preview.scene else it }
    val rooms = if (replaced.any { it.roomId == preview.roomId }) replaced else replaced + preview.scene
    val bounds = rooms.mapNotNull(RoomScene::bounds).reduceOrNull(WorldBounds::union)
    return DrawingScene(rooms.toImmutableList(), bounds)
}
