package app.plansnap.core.domain.command

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.testing.fixtures.rooms.roomFromVertices
import app.plansnap.testing.fixtures.rooms.vertex
import app.plansnap.testing.fixtures.runtime.TestDispatcherProvider
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope

/** Rectangle 4 m by 3 m, counter-clockwise, closed. Walls are `<id>-w0 ... <id>-w3`. */
fun rectangle(id: String): Room =
    roomFromVertices(
        idPrefix = id,
        vertices = listOf(vertex(0, 0), vertex(4000, 0), vertex(4000, 3000), vertex(0, 3000)),
    )

/** Room with [wallCount] walls of 1 m that is not closed. Corners after `V_0` are declared right angles. */
fun openRoom(
    id: String,
    wallCount: Int,
): Room =
    Room(
        id = RoomId(id),
        walls = List(wallCount) { Wall(WallId("$id-w$it"), Length(1000), Length(120)) }.toImmutableList(),
        corners =
            List(wallCount) { if (it == 0) CornerSpec.Unspecified else CornerSpec.DeclaredRight(TurnDirection.LEFT) }
                .toImmutableList(),
    )

/** Project with [rooms] and a default height of 2.5 m. */
fun projectOf(vararg rooms: Room): Project =
    Project(ProjectId("p"), "Flat", Length(2500), persistentListOf(*rooms), createdAtEpochMs = 1, updatedAtEpochMs = 2)

/** Door of 900 mm at [offset] on [wallId]. */
fun door(
    id: String,
    wallId: WallId,
    offset: Long = 100,
    width: Long = 900,
): Opening = Opening(OpeningId(id), wallId, OpeningType.DOOR, Length(offset), Length(width), null, null, null)

/** Returns the failure of [result] or fails the test. */
fun errorOf(result: DomainResult<*>): DomainError =
    (result as? DomainResult.Failure)?.error ?: error("Expected a failure but was $result")

/** Returns the value of [result] or fails the test. */
fun <T> valueOf(result: DomainResult<T>): T =
    (result as? DomainResult.Success)?.value ?: error("Expected a success but was $result")

/** The error of a violated [rule]. */
fun violation(rule: DomainRule): DomainError = DomainError.RuleViolation(rule)

/** The error of a missing [entity]. */
fun missing(
    entity: EntityKind,
    id: String,
): DomainError = DomainError.NotFound(entity, id)

/** Room by id. */
fun Project.room(id: String): Room = rooms.first { it.id.value == id }

/** Corner specification with a measured right angle. */
fun measuredRight(): CornerSpec = CornerSpec.Measured(TurnDirection.LEFT, Angle(90_000))

/**
 * Minimal stand-in for the opening rules of T-DOM-003: an opening fits if it ends on the raw wall length and does not
 * overlap another opening of its wall.
 */
class FakeOpeningRules : OpeningRules {
    override fun statusOf(
        room: Room,
        opening: Opening,
    ): OpeningStatus = OpeningStatus.VALID

    override fun check(
        room: Room,
        opening: Opening,
    ): DomainResult<Unit> {
        val wall = room.walls.firstOrNull { it.id == opening.wallId }
        val others = room.openings.filter { it.wallId == opening.wallId && it.id != opening.id }
        val error =
            when {
                wall == null -> {
                    missing(EntityKind.WALL, opening.wallId.value)
                }

                opening.endOffset > wall.length -> {
                    violation(DomainRule.OPENING_OUT_OF_BOUNDS)
                }

                others.any { opening.offset < it.endOffset && it.offset < opening.endOffset } -> {
                    violation(DomainRule.OPENING_OVERLAP)
                }

                else -> {
                    null
                }
            }
        return if (error == null) DomainResult.Success(Unit) else DomainResult.Failure(error)
    }
}

/** Evaluator that reports every room as open and records the tolerances it was called with. */
class OpenEvaluator : RoomEvaluator {
    /** Tolerances of all calls, in order. */
    val tolerances = mutableListOf<ClosureTolerance>()

    override suspend fun evaluate(
        room: Room,
        tolerance: ClosureTolerance,
    ): DomainResult<RoomEvaluation> {
        tolerances += tolerance
        return DomainResult.Success(RoomEvaluation.Open(room.id, persistentListOf(Vec2.ZERO), persistentMapOf()))
    }
}

/** Resolver that returns the project unchanged, or [transform] of it, and records every call. */
class RecordingResolver(
    private val transform: suspend (Project) -> DomainResult<Project> = { DomainResult.Success(it) },
) : DockPlacementResolver {
    /** Projects and tolerances of all calls, in order. */
    val calls = mutableListOf<Pair<Project, ClosureTolerance>>()

    override suspend fun resolve(
        project: Project,
        tolerance: ClosureTolerance,
    ): DomainResult<Project> {
        calls += project to tolerance
        return transform(project)
    }
}

/** History over [initial] whose default dispatcher is the virtual clock of this test. */
fun TestScope.historyOf(
    initial: Project,
    evaluator: RoomEvaluator = OpenEvaluator(),
    resolver: DockPlacementResolver = RecordingResolver(),
): DefaultCommandHistory =
    DefaultCommandHistory(initial, evaluator, resolver, TestDispatcherProvider(StandardTestDispatcher(testScheduler)))
