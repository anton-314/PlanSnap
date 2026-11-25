package app.plansnap.feature.editor.state

import androidx.compose.runtime.Immutable
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toPersistentMap
import kotlin.reflect.KClass
import kotlin.reflect.safeCast

/** Whether the project of the editor is available (UX section 1: loading and error state of the editor). */
sealed interface EditorLoadState {
    /** The project is being loaded. */
    data object Loading : EditorLoadState

    /** The project is loaded and editable. */
    data object Loaded : EditorLoadState

    /**
     * The project could not be loaded. The screen offers going back to the list and [ReloadProject].
     *
     * @property error expected failure of the repository.
     */
    data class Failed(
        val error: DomainError,
    ) : EditorLoadState
}

/** Persistence status of the confirmed project (REQ-DATA-002). */
sealed interface SaveStatus {
    /** Nothing was changed since the project was opened. */
    data object Idle : SaveStatus

    /** A save is queued or running. */
    data object Saving : SaveStatus

    /**
     * Everything confirmed so far is stored.
     *
     * @property updatedAtEpochMs time stamp the `ProjectSaver` stored.
     */
    data class Saved(
        val updatedAtEpochMs: Long,
    ) : SaveStatus

    /**
     * The last save failed. The changes stay pending and [CoreIntent.RetrySave] stores them again.
     *
     * @property error failure reported by the `ProjectSaver`.
     */
    data class Failed(
        val error: DomainError,
    ) : SaveStatus
}

/**
 * Pan and zoom of the plan, the part of the viewport that survives process death. The screen size is not part of it,
 * because only the canvas knows it.
 *
 * @property centerXMm world x shown at the center of the screen.
 * @property centerYMm world y shown at the center of the screen.
 * @property pixelsPerMm zoom, positive and finite.
 */
@Immutable
data class PlanView(
    val centerXMm: Double,
    val centerYMm: Double,
    val pixelsPerMm: Double,
) {
    init {
        require(centerXMm.isFinite() && centerYMm.isFinite()) { "View center must be finite" }
        require(pixelsPerMm.isFinite() && pixelsPerMm > 0.0) { "Invalid zoom: $pixelsPerMm" }
    }
}

/**
 * Live preview of the wall that is being typed (REQ-UX-002): the selected room with the wall appended, evaluated and
 * drawn. It exists only while the input text is a valid length that fits the room.
 *
 * @property roomId room that receives the wall.
 * @property room selected room including the preview wall. Its wall id is a placeholder and never stored.
 * @property evaluation evaluation of [room].
 * @property scene scene of [room], to be drawn instead of the stored scene of the room.
 */
@Immutable
data class WallPreview(
    val roomId: RoomId,
    val room: Room,
    val evaluation: RoomEvaluation,
    val scene: RoomScene,
)

/**
 * State of one sub-function of the editor (sheets, hints, history keys, ...). A handler stores its state in
 * [EditorUiState.sections] under its own class, so the core state does not change when a sub-function is added.
 */
interface EditorSectionState

/**
 * Immutable state of the editor (ARCHITECTURE section 4). Every change is a new instance, produced from an
 * [EditorIntent] and emitted through `EditorViewModel.state`.
 *
 * @property load whether the project is available. The fields below describe an empty editor while it is not.
 * @property project the confirmed project, `null` until it is [EditorLoadState.Loaded].
 * @property selectedRoomId room that receives the input, `null` if the project has no room.
 * @property inputText running input exactly as typed. Survives process death.
 * @property nextCorner entered corner that precedes the next wall. The start is "left, 90 degrees" (UX section 3).
 * @property thicknessOverride thickness chosen for the next wall, `null` uses the last stored thickness.
 * @property settings current user settings.
 * @property view pan and zoom of the plan, `null` until the canvas set it.
 * @property evaluations current evaluation of every room that could be evaluated.
 * @property evaluationErrors reason why a room has no evaluation. Such a room is not in [scene].
 * @property scene scene of the confirmed project, `null` until the first evaluation finished.
 * @property preview preview of the typed wall, `null` if there is none.
 * @property inputError why the typed text is not a wall that can be appended, `null` for an empty or valid input.
 * @property undoDepth available undo steps.
 * @property redoDepth available redo steps.
 * @property save persistence status of the confirmed project.
 * @property draftError the last failure to store the running input, `null` if the input is stored.
 * @property sections state of the sub-function handlers.
 */
@Immutable
data class EditorUiState(
    val load: EditorLoadState = EditorLoadState.Loading,
    val project: Project? = null,
    val selectedRoomId: RoomId? = null,
    val inputText: String = "",
    val nextCorner: CornerSpec = DEFAULT_NEXT_CORNER,
    val thicknessOverride: Length? = null,
    val settings: AppSettings = AppSettings(),
    val view: PlanView? = null,
    val evaluations: ImmutableMap<RoomId, RoomEvaluation> = persistentMapOf(),
    val evaluationErrors: ImmutableMap<RoomId, DomainError> = persistentMapOf(),
    val scene: DrawingScene? = null,
    val preview: WallPreview? = null,
    val inputError: DomainError? = null,
    val undoDepth: Int = 0,
    val redoDepth: Int = 0,
    val save: SaveStatus = SaveStatus.Idle,
    val draftError: DomainError? = null,
    val sections: ImmutableMap<KClass<out EditorSectionState>, EditorSectionState> = persistentMapOf(),
) {
    /** The room that receives the input, `null` if none is selected. */
    val selectedRoom: Room? get() = project?.rooms?.firstOrNull { it.id == selectedRoomId }

    /** Thickness of the next wall: the chosen one, otherwise the last stored one. */
    val thickness: Length get() = thicknessOverride ?: settings.lastThickness

    /** Tolerance of the settings, the one every evaluation of the editor uses. */
    val tolerance: ClosureTolerance get() = settings.tolerance

    /** Whether an undo step exists. */
    val canUndo: Boolean get() = undoDepth > 0

    /** Whether a redo step exists. */
    val canRedo: Boolean get() = redoDepth > 0

    /** Returns the state of the section [type], `null` if its handler has not stored one. */
    fun <T : EditorSectionState> section(type: KClass<T>): T? = type.safeCast(sections[type])

    /** Returns a copy in which the section [type] has the state [section]. */
    fun <T : EditorSectionState> withSection(
        type: KClass<T>,
        section: T,
    ): EditorUiState = copy(sections = sections.toPersistentMap().put(type, section))

    /** Constants of the state. */
    companion object {
        /** Direction of the next wall before the user chooses one: left at 90 degrees (UX section 3). */
        val DEFAULT_NEXT_CORNER: CornerSpec = CornerSpec.DeclaredRight(TurnDirection.LEFT)
    }
}
