package app.plansnap.core.domain.placement

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.EvaluatedShape
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.domain.validation.OpeningRules
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import java.util.concurrent.atomic.AtomicInteger

/**
 * Bound for floating-point noise of the rigid transforms in these tests, in mm. It is far below
 * `GeometryTolerances.SOLVER_RESIDUAL_MM`, so "equal within [EPS_MM]" proves exact placement up to rounding.
 */
const val EPS_MM = 1e-6

/** Bound for floating-point noise of unit vectors and angles. */
const val EPS_UNIT = 1e-9

/** Default tolerance passed to the resolver. The fake evaluator ignores it. */
val TOLERANCE = ClosureTolerance()

/** Counter-clockwise rectangle with `V_0` in the origin and `w_0` along +x. */
fun rectangle(
    widthMm: Int,
    heightMm: Int,
): List<Vec2> =
    listOf(
        Vec2(0.0, 0.0),
        Vec2(widthMm.toDouble(), 0.0),
        Vec2(widthMm.toDouble(), heightMm.toDouble()),
        Vec2(0.0, heightMm.toDouble()),
    )

/** Id of wall `w_index` of the room [roomId] built by [rawRoom]. */
fun wallId(
    roomId: String,
    index: Int,
): WallId = WallId("$roomId-w$index")

/**
 * Closed room with [wallCount] walls `<id>-w<i>`. The raw lengths are placeholders: the [FakeShapeEvaluator] supplies
 * the displayed outline, which is all the resolver reads besides ids, thicknesses and openings.
 */
fun rawRoom(
    id: String,
    wallCount: Int,
    thicknessMm: (Int) -> Long = { 120L },
    placement: RoomPlacement = RoomPlacement.DEFAULT,
    openings: List<Opening> = emptyList(),
): Room =
    Room(
        id = RoomId(id),
        walls = List(wallCount) { Wall(wallId(id, it), Length(1000), Length(thicknessMm(it))) }.toImmutableList(),
        corners =
            List(wallCount) { if (it == 0) CornerSpec.Unspecified else CornerSpec.DeclaredRight(TurnDirection.LEFT) }
                .toImmutableList(),
        openings = openings.toImmutableList(),
        isClosed = true,
        placement = placement,
    )

/** Dock link of [ownWall] to [hostWall] of [hostId]. */
fun link(
    hostId: String,
    hostWall: Int,
    ownId: String,
    ownWall: Int,
    hostOffsetMm: Long,
): DockLink =
    DockLink(
        hostRoomId = RoomId(hostId),
        hostWallId = wallId(hostId, hostWall),
        ownWallId = wallId(ownId, ownWall),
        hostOffset = Length(hostOffsetMm),
    )

/** Returns this link with [hostOffset][DockLink.hostOffset] counted from the start of opening [openingId]. */
fun DockLink.viaOpening(openingId: String?): DockLink = copy(hostOpeningId = openingId?.let(::OpeningId))

/** Returns this room docked by [dock], keeping its current origin and rotation. */
fun Room.dockedBy(dock: DockLink): Room = copy(placement = placement.copy(dock = dock))

/** Door of 900 mm at [offsetMm] on [wall]. */
fun door(
    id: String,
    wall: WallId,
    offsetMm: Long,
): Opening = Opening(OpeningId(id), wall, OpeningType.DOOR, Length(offsetMm), Length(900), null, null, null)

/** Project with [rooms]. */
fun projectOf(rooms: List<Room>): Project =
    Project(ProjectId("p"), "Plan", Length(2500), rooms.toImmutableList(), createdAtEpochMs = 1, updatedAtEpochMs = 2)

/** Displayed shape of [vertices], computed independently of the resolver. */
fun shapeOf(vertices: List<Vec2>): EvaluatedShape {
    val n = vertices.size
    val edges = List(n) { vertices[(it + 1) % n] - vertices[it] }
    val twiceArea = vertices.indices.sumOf { vertices[it].cross(vertices[(it + 1) % n]) }
    return EvaluatedShape(
        outline = Polygon2(vertices),
        winding = if (twiceArea > 0.0) Winding.COUNTER_CLOCKWISE else Winding.CLOCKWISE,
        lengthsMm = edges.map { it.length() }.toImmutableList(),
        turnsRad =
            List(n) { i ->
                val before = edges[(i + n - 1) % n]
                StrictMath.atan2(before.cross(edges[i]), before.dot(edges[i]))
            }.toImmutableList(),
        areaMm2 = kotlin.math.abs(twiceArea) / 2.0,
        perimeterMm = edges.sumOf { it.length() },
    )
}

/**
 * Evaluator fake that returns `CONFIRMED` with the registered outline, or `ADJUSTED` for rooms in [adjusted].
 * Rooms in [open] are `OPEN`, rooms in [conflict] are `CONFLICT` and rooms in [failing] fail with `InvalidValue`.
 */
class FakeShapeEvaluator(
    private val outlines: Map<RoomId, List<Vec2>>,
    private val open: Set<RoomId> = emptySet(),
    private val conflict: Set<RoomId> = emptySet(),
    private val failing: Set<RoomId> = emptySet(),
    private val adjusted: Set<RoomId> = emptySet(),
) : RoomEvaluator {
    /** Number of evaluations so far. */
    val calls = AtomicInteger()

    override suspend fun evaluate(
        room: Room,
        tolerance: ClosureTolerance,
    ): DomainResult<RoomEvaluation> {
        calls.incrementAndGet()
        val statuses = persistentMapOf<OpeningId, OpeningStatus>()
        return when (room.id) {
            in failing -> {
                DomainResult.Failure(DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_SMALL))
            }

            in open -> {
                DomainResult.Success(RoomEvaluation.Open(room.id, persistentListOf(Vec2.ZERO), statuses))
            }

            in conflict -> {
                DomainResult.Success(
                    RoomEvaluation.Conflict(room.id, ConflictReason.NO_SIMPLE_SHAPE, null, null, statuses),
                )
            }

            in adjusted -> {
                DomainResult.Success(RoomEvaluation.Adjusted(room.id, shapeOf(outlines.getValue(room.id)), statuses))
            }

            else -> {
                DomainResult.Success(RoomEvaluation.Confirmed(room.id, shapeOf(outlines.getValue(room.id)), statuses))
            }
        }
    }
}

/** Opening rules that accept every opening, so command tests here depend only on placement. */
class AcceptingOpeningRules : OpeningRules {
    override fun statusOf(
        room: Room,
        opening: Opening,
    ): OpeningStatus = OpeningStatus.VALID

    override fun check(
        room: Room,
        opening: Opening,
    ): DomainResult<Unit> = DomainResult.Success(Unit)
}

/** Returns the successful value or fails the test with the error. */
fun <T> DomainResult<T>.value(): T =
    when (this) {
        is DomainResult.Success -> value
        is DomainResult.Failure -> throw AssertionError("Expected success but got $error")
    }

/** Room [id] of this project. */
fun Project.room(id: String): Room = rooms.first { it.id.value == id }

/** World outline of [room] with its local [outline]. */
fun worldOutline(
    room: Room,
    outline: List<Vec2>,
): List<Vec2> = outline.map { room.placement.toWorld(it) }

/** Maps a world point into the local frame of [placement], the inverse of [toWorld]. */
fun RoomPlacement.toLocal(world: Vec2): Vec2 = (world - Vec2(originX, originY)).rotated(-rotationRad)

/**
 * Asserts the docking geometry of DOMAIN section 10 for room [roomId] in [project], computed independently from the
 * world outlines: the own wall is antiparallel to the host wall, both own wall ends lie exactly the host wall thickness
 * outside the host inner contour and the own start point lies [expectedAlongMm] along the host wall from `V_i`.
 */
fun assertDocked(
    project: Project,
    outlines: Map<RoomId, List<Vec2>>,
    roomId: String,
    expectedAlongMm: Double,
) {
    val room = project.room(roomId)
    val dock = checkNotNull(room.placement.dock) { "$roomId is not docked" }
    val host = project.rooms.first { it.id == dock.hostRoomId }
    val hostIndex = host.walls.indexOfFirst { it.id == dock.hostWallId }
    val ownIndex = room.walls.indexOfFirst { it.id == dock.ownWallId }
    val hostWorld = worldOutline(host, outlines.getValue(host.id))
    val ownWorld = worldOutline(room, outlines.getValue(room.id))
    val a = hostWorld[hostIndex]
    val b = hostWorld[(hostIndex + 1) % hostWorld.size]
    val p = ownWorld[ownIndex]
    val q = ownWorld[(ownIndex + 1) % ownWorld.size]
    val u = (b - a) * (1.0 / (b - a).length())
    val v = (q - p) * (1.0 / (q - p).length())
    val sign = if (shapeOf(outlines.getValue(host.id)).winding == Winding.COUNTER_CLOCKWISE) 1.0 else -1.0
    val normal = Vec2(u.y, -u.x) * sign
    val thickness =
        host.walls[hostIndex]
            .thickness.mm
            .toDouble()
    assertTrue(u.isCloseTo(v * -1.0, EPS_UNIT), "own wall must be antiparallel: $u vs $v")
    assertEquals(thickness, (p - a).dot(normal), EPS_MM, "distance of the own start")
    assertEquals(thickness, (q - a).dot(normal), EPS_MM, "distance of the own end")
    assertEquals(expectedAlongMm, (p - a).dot(u), EPS_MM, "position along the host wall")
}
