package app.plansnap.core.model

import app.plansnap.core.common.units.TurnDirection

private const val FNV_OFFSET_BASIS: Long = -0x340d631b7bdddcdbL
private const val FNV_PRIME: Long = 0x100000001b3L
private const val BYTE_MASK: Long = 0xFFL
private const val BITS_PER_BYTE = 8
private const val TAG_UNSPECIFIED = 0
private const val TAG_DECLARED_RIGHT = 1
private const val TAG_MEASURED = 2
private const val DIRECTION_NONE = 0
private const val DIRECTION_LEFT = 1
private const val DIRECTION_RIGHT = 2

private val DIAGONAL_ORDER: Comparator<CanonicalDiagonal> =
    compareBy<CanonicalDiagonal> { it.low }.thenBy { it.high }.thenBy { it.mm }

private class CanonicalDiagonal(
    val low: Int,
    val high: Int,
    val mm: Long,
)

private class Fnv1a64 {
    var hash: Long = FNV_OFFSET_BASIS
        private set

    fun putByte(value: Int) {
        hash = (hash xor (value.toLong() and BYTE_MASK)) * FNV_PRIME
    }

    fun putInt(value: Int) {
        for (index in Int.SIZE_BYTES - 1 downTo 0) putByte(value ushr (index * BITS_PER_BYTE))
    }

    fun putLong(value: Long) {
        for (index in Long.SIZE_BYTES - 1 downTo 0) putByte((value ushr (index * BITS_PER_BYTE)).toInt())
    }
}

/**
 * Computes the stable FNV-1a-64 fingerprint of the raw shape input (DOMAIN section 6).
 *
 * The hash covers wall lengths, corner specifications and canonically ordered diagonals.
 * Ids, wall thicknesses and all other room data do not contribute.
 */
fun Room.inputFingerprint(): Long {
    val hasher = Fnv1a64()
    hasher.putInt(walls.size)
    walls.forEach { hasher.putLong(it.length.mm) }
    corners.forEach { corner ->
        when (corner) {
            CornerSpec.Unspecified -> {
                hasher.putByte(TAG_UNSPECIFIED)
                hasher.putByte(DIRECTION_NONE)
            }

            is CornerSpec.DeclaredRight -> {
                hasher.putByte(TAG_DECLARED_RIGHT)
                hasher.putByte(corner.direction.fingerprintByte())
            }

            is CornerSpec.Measured -> {
                hasher.putByte(TAG_MEASURED)
                hasher.putByte(corner.direction.fingerprintByte())
                hasher.putInt(corner.angle.milliDegrees)
            }
        }
    }
    hasher.putInt(diagonals.size)
    diagonals
        .map { CanonicalDiagonal(minOf(it.fromCorner, it.toCorner), maxOf(it.fromCorner, it.toCorner), it.length.mm) }
        .sortedWith(DIAGONAL_ORDER)
        .forEach {
            hasher.putInt(it.low)
            hasher.putInt(it.high)
            hasher.putLong(it.mm)
        }
    return hasher.hash
}

/** Whether [adjustment] still matches the raw input. The solver version is deliberately ignored. */
fun Room.isAdjustmentCurrent(adjustment: ClosureAdjustment): Boolean = adjustment.inputFingerprint == inputFingerprint()

private fun TurnDirection.fingerprintByte(): Int =
    when (this) {
        TurnDirection.LEFT -> DIRECTION_LEFT
        TurnDirection.RIGHT -> DIRECTION_RIGHT
    }
