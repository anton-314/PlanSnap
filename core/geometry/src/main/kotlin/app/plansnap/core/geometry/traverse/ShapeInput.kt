package app.plansnap.core.geometry.traverse

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.GeometryTolerances

/** Model-free corner input of the geometry core. `:core:domain` maps room corners to this type. */
sealed interface CornerInput {
    /** Declared 90° corner in [direction]. */
    data class DeclaredRight(
        val direction: TurnDirection,
    ) : CornerInput

    /** Measured corner with entered angle `α` in `(0°, 180°]`. */
    data class Measured(
        val direction: TurnDirection,
        val angle: Angle,
    ) : CornerInput {
        init {
            require(angle.isValidCornerAngle()) { "Corner angle out of range: ${angle.milliDegrees}" }
        }
    }

    /** Corner without an entered value. Only permitted at the closing corner `V_0`. */
    data object Free : CornerInput
}

/**
 * Measured diagonal between two different corners.
 *
 * @property lengthMm raw length in mm, positive.
 */
data class DiagonalInput(
    val fromCorner: Int,
    val toCorner: Int,
    val lengthMm: Long,
) {
    init {
        require(fromCorner >= 0 && toCorner >= 0) { "Corner index must not be negative" }
        require(fromCorner != toCorner) { "Diagonal must connect two different corners" }
        require(lengthMm > 0L) { "Diagonal length must be positive: $lengthMm" }
    }
}

/**
 * Complete raw input of one closed room outline, validated against DOMAIN sections 1 and 3.
 * All lists are copied and immutable.
 *
 * @throws IllegalArgumentException if a value or the structure violates the contract.
 */
class ShapeInput(
    lengthsMm: List<Long>,
    corners: List<CornerInput>,
    diagonals: List<DiagonalInput> = emptyList(),
) {
    /** Wall lengths `L_i` in mm, each within the wall limits. */
    val lengthsMm: List<Long> = java.util.Collections.unmodifiableList(ArrayList(lengthsMm))

    /** Corner inputs `V_i`, one per wall. */
    val corners: List<CornerInput> = java.util.Collections.unmodifiableList(ArrayList(corners))

    /** Measured diagonals with corner indices below the wall count. */
    val diagonals: List<DiagonalInput> = java.util.Collections.unmodifiableList(ArrayList(diagonals))

    /** Number of walls `n`. */
    val wallCount: Int get() = lengthsMm.size

    init {
        require(wallCount in MIN_WALLS..GeometryTolerances.MAX_WALLS_PER_ROOM) { "Invalid wall count: $wallCount" }
        require(this.corners.size == wallCount) { "Expected one corner per wall" }
        require(this.lengthsMm.all { it in GeometryTolerances.MIN_WALL_MM..GeometryTolerances.MAX_WALL_MM }) {
            "Wall length out of range"
        }
        require(this.corners.drop(1).none { it is CornerInput.Free }) { "Only V_0 may be free" }
        require(this.diagonals.all { it.fromCorner < wallCount && it.toCorner < wallCount }) {
            "Diagonal corner index out of range"
        }
    }

    override fun equals(other: Any?): Boolean =
        other is ShapeInput &&
            lengthsMm == other.lengthsMm && corners == other.corners && diagonals == other.diagonals

    override fun hashCode(): Int = 31 * (31 * lengthsMm.hashCode() + corners.hashCode()) + diagonals.hashCode()

    override fun toString(): String = "ShapeInput(lengthsMm=$lengthsMm, corners=$corners, diagonals=$diagonals)"

    private companion object {
        const val MIN_WALLS = 3
    }
}
