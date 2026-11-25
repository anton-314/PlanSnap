package app.plansnap.core.domain.evaluation

import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Room
import app.plansnap.core.model.isAdjustmentCurrent
import kotlinx.collections.immutable.ImmutableMap
import kotlinx.collections.immutable.toImmutableMap

/**
 * Shape of a stored overlay that DOMAIN section 4, step 2 accepts: the room is closed, the fingerprint matches and the
 * overlay is structurally valid (DOMAIN section 6). Only [ShapeBuilder.acceptedOverlay] creates it, so no preview or
 * solver shape can be passed where the effective length of an opening is decided.
 *
 * @property shape displayed shape of the overlay, its lengths are the adjusted lengths.
 */
@JvmInline
internal value class AcceptedOverlay(
    val shape: EvaluatedShape,
)

/** Accepted overlay of [room] after DOMAIN section 4, step 2, or `null` if the room has none. */
internal fun ShapeBuilder.acceptedOverlay(room: Room): AcceptedOverlay? =
    room.adjustment
        ?.takeIf { room.isClosed && room.isAdjustmentCurrent(it) }
        ?.let(::overlayShape)
        ?.let(::AcceptedOverlay)

/**
 * The one definition of `L_effective` for openings (DOMAIN section 9, ADR 0021), shared by `DefaultOpeningRules` and
 * [DefaultRoomEvaluator], so a command accepts exactly the openings the scene and the export preflight show as valid.
 *
 * `L_effective` is the adjusted length of an [AcceptedOverlay] and the raw length in every other case: without an
 * overlay, with a stale or structurally invalid one, for `OPEN` and `CONFLICT`, and also while `NEEDS_ADJUSTMENT` or
 * `PROPOSED` displays a preview or solver shape. It depends neither on the tolerance nor on a preview, so an opening
 * changes its status only when the raw values or the accepted overlay change.
 */
internal object EffectiveWallLength {
    /** `L_effective` of the wall at [wallIndex] in mm. */
    fun lengthMm(
        room: Room,
        overlay: AcceptedOverlay?,
        wallIndex: Int,
    ): Double {
        val raw = room.walls[wallIndex].length.mm
        return overlay?.shape?.lengthsMm?.get(wallIndex) ?: raw.toDouble()
    }

    /**
     * Whether [opening] fits on a wall of [lengthMm]: `offset + width ≤ L_effective + EXACT_CLOSURE_MM`. Offset and
     * width are already non-negative and positive by construction of the model.
     */
    fun fits(
        opening: Opening,
        lengthMm: Double,
    ): Boolean = opening.endOffset.mm.toDouble() <= lengthMm + GeometryTolerances.EXACT_CLOSURE_MM

    /** Derived status of [opening] in [room]. An opening on a wall that is not part of [room] is out of bounds. */
    fun statusOf(
        room: Room,
        overlay: AcceptedOverlay?,
        opening: Opening,
    ): OpeningStatus {
        val index = room.walls.indexOfFirst { it.id == opening.wallId }
        val fits = index >= 0 && fits(opening, lengthMm(room, overlay, index))
        return if (fits) OpeningStatus.VALID else OpeningStatus.OUT_OF_BOUNDS
    }

    /** Derived status of every opening of [room], in the order of [Room.openings]. */
    fun statuses(
        room: Room,
        overlay: AcceptedOverlay?,
    ): ImmutableMap<OpeningId, OpeningStatus> =
        room.openings.associate { it.id to statusOf(room, overlay, it) }.toImmutableMap()
}
