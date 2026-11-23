package app.plansnap.core.model

import app.plansnap.core.common.units.Length
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf

/**
 * Link of a docked room to a host wall (DOMAIN section 10).
 *
 * @property hostOffset shift along the host wall. Counts from the start of the opening if [hostOpeningId] is set,
 * otherwise from `V_i`. May be negative.
 * @property hostOpeningId optional opening on [hostWallId] the room follows.
 */
data class DockLink(
    val hostRoomId: RoomId,
    val hostWallId: WallId,
    val ownWallId: WallId,
    val hostOffset: Length,
    val hostOpeningId: OpeningId? = null,
)

/**
 * Rigid placement `p_world = R(rotation) · p_local + origin` (DOMAIN section 2).
 *
 * @property originX world x of the origin in mm, finite.
 * @property originY world y of the origin in mm, finite.
 * @property rotationRad rotation in radians, finite.
 * @property dock optional dock link.
 */
data class RoomPlacement(
    val originX: Double,
    val originY: Double,
    val rotationRad: Double,
    val dock: DockLink?,
) {
    init {
        require(originX.isFinite() && originY.isFinite()) { "Origin must be finite" }
        require(rotationRad.isFinite()) { "Rotation must be finite" }
    }

    /** Constants. */
    companion object {
        /** Placement at the world origin without rotation or dock link. */
        val DEFAULT: RoomPlacement = RoomPlacement(0.0, 0.0, 0.0, null)
    }
}

/** Area credit factor `f` in whole percent, from 0 to 100 (DOMAIN section 7). */
@JvmInline
value class AreaFactor(
    val percent: Int,
) {
    init {
        require(percent in 0..MAX_PERCENT) { "Area factor out of range: $percent" }
    }

    /** Constants. */
    companion object {
        private const val MAX_PERCENT = 100

        /** Full credit, the default. */
        val FULL: AreaFactor = AreaFactor(MAX_PERCENT)
    }
}

/**
 * Immutable room with raw measurements and optional adjustment overlay (DOMAIN sections 3 to 10).
 *
 * Structural invariants are enforced here. Value limits from `GeometryTolerances` are enforced by `:core:domain`.
 *
 * @property name display name.
 * @property typeKey optional room type template.
 * @property notes free-text notes.
 * @property heightOverride room height, positive. `null` uses the project default.
 * @property areaFactor area credit factor.
 * @property walls walls `w_0 … w_{n-1}` with unique ids.
 * @property corners corners `V_0 … V_{n-1}`, one per wall. Only `V_0` may be [CornerSpec.Unspecified].
 * @property diagonals diagonals with unique ids and corner indices below `n`.
 * @property openings openings with unique ids.
 * @property isClosed whether the outline was closed. Requires at least three walls.
 * @property adjustment confirmed overlay. Requires a closed room and one entry per wall.
 * @property placement rigid placement. A room cannot dock to itself.
 * @throws IllegalArgumentException if a structural invariant is violated.
 */
data class Room(
    val id: RoomId,
    val name: String = "",
    val typeKey: RoomTypeKey? = null,
    val notes: String = "",
    val heightOverride: Length? = null,
    val areaFactor: AreaFactor = AreaFactor.FULL,
    val walls: ImmutableList<Wall> = persistentListOf(),
    val corners: ImmutableList<CornerSpec> = persistentListOf(),
    val diagonals: ImmutableList<Diagonal> = persistentListOf(),
    val openings: ImmutableList<Opening> = persistentListOf(),
    val isClosed: Boolean = false,
    val adjustment: ClosureAdjustment? = null,
    val placement: RoomPlacement = RoomPlacement.DEFAULT,
) {
    init {
        val n = walls.size
        require(corners.size == n) { "Expected one corner per wall" }
        require(corners.drop(1).none { it is CornerSpec.Unspecified }) { "Only V_0 may be unspecified" }
        require(walls.distinctBy { it.id }.size == n) { "Wall ids must be unique" }
        require(diagonals.distinctBy { it.id }.size == diagonals.size) { "Diagonal ids must be unique" }
        require(openings.distinctBy { it.id }.size == openings.size) { "Opening ids must be unique" }
        require(diagonals.all { it.fromCorner < n && it.toCorner < n }) { "Diagonal corner index out of range" }
        require(heightOverride == null || heightOverride > Length.ZERO) { "Room height must be positive" }
        require(!isClosed || n >= MIN_CLOSED_WALLS) { "A closed room needs at least $MIN_CLOSED_WALLS walls" }
        require(adjustment == null || (isClosed && adjustment.adjustedLengthsMm.size == n)) {
            "An adjustment requires a closed room and one entry per wall"
        }
        require(placement.dock?.hostRoomId != id) { "A room cannot dock to itself" }
    }

    /** Returns the room height if set, otherwise [projectDefault] (REQ-MOD-005). */
    fun effectiveHeight(projectDefault: Length): Length = heightOverride ?: projectDefault

    private companion object {
        const val MIN_CLOSED_WALLS = 3
    }
}

/**
 * Immutable project of one floor.
 *
 * @property name display name.
 * @property defaultHeight default room height, positive.
 * @property rooms rooms in display order with unique ids.
 * @property createdAtEpochMs creation time.
 * @property updatedAtEpochMs last change time.
 * @throws IllegalArgumentException if an invariant is violated.
 */
data class Project(
    val id: ProjectId,
    val name: String,
    val defaultHeight: Length,
    val rooms: ImmutableList<Room>,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
) {
    init {
        require(defaultHeight > Length.ZERO) { "Default height must be positive: ${defaultHeight.mm}" }
        require(rooms.distinctBy { it.id }.size == rooms.size) { "Room ids must be unique" }
    }
}
