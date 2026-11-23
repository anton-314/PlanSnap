package app.plansnap.core.domain.repository

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.flow.Flow
import java.io.InputStream

/**
 * Entry of the project list.
 *
 * @property roomCount number of rooms, not negative.
 */
data class ProjectSummary(
    val id: ProjectId,
    val name: String,
    val roomCount: Int,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(roomCount >= 0) { "Room count must not be negative: $roomCount" }
    }
}

/** Project storage (REQ-DATA-001, REQ-DATA-007). Technical failures are returned as `DomainError.Storage`. */
interface ProjectRepository {
    /** Emits the project list on every change. */
    fun observeProjects(): Flow<ImmutableList<ProjectSummary>>

    /** Loads a project, or fails with `NotFound`. */
    suspend fun getProject(id: ProjectId): DomainResult<Project>

    /** Stores a new project. Fails with `DUPLICATE_ID` if its id exists. */
    suspend fun createProject(project: Project): DomainResult<Unit>

    /** Renames a project. */
    suspend fun renameProject(
        id: ProjectId,
        name: String,
    ): DomainResult<Unit>

    /** Deletes a project with all rooms and its draft. The confirmation belongs to the caller. */
    suspend fun deleteProject(id: ProjectId): DomainResult<Unit>

    /** Creates an independent deep copy with new ids, keeping all internal references, and returns its id. */
    suspend fun duplicateProject(id: ProjectId): DomainResult<ProjectId>

    /**
     * Imports an untrusted project document (ADR 0017): decodes [input] with `DocumentCodec`, checks the result with
     * `ProjectValidator` and only then stores it. Ids are kept unless the project id or a room id already exists, in
     * which case the project is stored as with [duplicateProject]. Returns the stored id.
     *
     * [input] stays open and belongs to the caller. Any failure leaves the stored data unchanged:
     * `DomainError.Document` for an invalid document, `DomainError.Storage(READ_FAILED)` for an unreadable stream, the
     * violation reported by `ProjectValidator`, or a storage failure.
     */
    suspend fun importProject(input: InputStream): DomainResult<ProjectId>
}

/**
 * Serial writer running in the application scope, so that it outlives the ViewModel (ARCHITECTURE section 5).
 * A confirmed wall counts as saved only after this call succeeded.
 */
fun interface ProjectSaver {
    /**
     * Writes project metadata and [changedRooms] in one transaction. An id that is no longer in [project] deletes the
     * stored room. Sets `updatedAtEpochMs` through `EpochClock` and returns the stored value.
     */
    suspend fun save(
        project: Project,
        changedRooms: Set<RoomId>,
    ): DomainResult<Long>
}

/**
 * Unconfirmed input that survives process death (REQ-DATA-002).
 *
 * @property roomId room being edited, `null` if none is selected.
 * @property inputText running input, exactly as typed.
 */
data class EditorDraft(
    val projectId: ProjectId,
    val roomId: RoomId?,
    val inputText: String,
)

/** Storage of the running input, one draft per project. */
interface EditorDraftRepository {
    /** Loads the draft, `null` if none exists. */
    suspend fun getDraft(projectId: ProjectId): DomainResult<EditorDraft?>

    /** Replaces the draft of its project. */
    suspend fun saveDraft(draft: EditorDraft): DomainResult<Unit>

    /** Removes the draft, if any. */
    suspend fun clearDraft(projectId: ProjectId): DomainResult<Unit>
}

/** One-time editor hints (UX section 5). */
enum class EditorHint {
    /** Number pad. */
    NUMPAD,

    /** Direction of the next wall. */
    DIRECTION,

    /** Closing the room. */
    CLOSE,

    /** Diagonal for a proposal. */
    DIAGONAL,

    /** Adjustment of a gap. */
    ADJUSTMENT,
}

/**
 * User settings.
 *
 * @property lengthUnit display and input unit.
 * @property tolerance limit between measurement error and shape deviation.
 * @property gridEnabled whether the grid is shown.
 * @property snapEnabled whether snapping is active.
 * @property lastThickness last used wall thickness, within `0..MAX_THICKNESS_MM`.
 * @property dismissedHints hints the user has closed.
 */
data class AppSettings(
    val lengthUnit: LengthUnit = LengthUnit.CENTIMETER,
    val tolerance: ClosureTolerance = ClosureTolerance(),
    val gridEnabled: Boolean = true,
    val snapEnabled: Boolean = true,
    val lastThickness: Length = Length(GeometryTolerances.DEFAULT_THICKNESS_MM),
    val dismissedHints: ImmutableSet<EditorHint> = persistentSetOf(),
) {
    init {
        require(lastThickness.mm in 0L..GeometryTolerances.MAX_THICKNESS_MM) {
            "Thickness out of range: ${lastThickness.mm}"
        }
    }
}

/** Settings storage. Invalid stored values fall back to their defaults. */
interface SettingsRepository {
    /** Emits the settings on every change. */
    val settings: Flow<AppSettings>

    /** Atomically replaces the settings with the result of [transform]. */
    suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit>
}
