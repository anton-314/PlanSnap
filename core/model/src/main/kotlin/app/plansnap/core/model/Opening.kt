package app.plansnap.core.model

import app.plansnap.core.common.units.Length

/** Kind of an opening (REQ-MOD-001). */
enum class OpeningType {
    /** Door. */
    DOOR,

    /** Window. */
    WINDOW,

    /** Passage without a door leaf. */
    PASSAGE,
}

/** Door swing. Left and right apply in wall direction `V_i → V_{i+1}`, inward opens into the room. */
enum class DoorSwing {
    /** Hinge on the left, opens into the room. */
    HINGE_LEFT_INWARD,

    /** Hinge on the left, opens out of the room. */
    HINGE_LEFT_OUTWARD,

    /** Hinge on the right, opens into the room. */
    HINGE_RIGHT_INWARD,

    /** Hinge on the right, opens out of the room. */
    HINGE_RIGHT_OUTWARD,
}

/** Derived status of an opening relative to its wall (DOMAIN section 9). */
enum class OpeningStatus {
    /** The opening fits on its wall. */
    VALID,

    /** The wall changed and the opening no longer fits. It stays stored and is reported. */
    OUT_OF_BOUNDS,
}

/**
 * Door, window or passage bound to a wall by its stable [wallId] (DOMAIN section 9).
 *
 * The fit on the wall is a derived status because a stored opening may stop fitting after a wall edit.
 *
 * @property offset distance from `V_i` along the wall, not negative.
 * @property width width, positive. `offset + width` must not overflow.
 * @property height optional height, positive.
 * @property sillHeight optional sill height, not negative, windows only.
 * @property swing optional swing, doors only.
 */
data class Opening(
    val id: OpeningId,
    val wallId: WallId,
    val type: OpeningType,
    val offset: Length,
    val width: Length,
    val height: Length?,
    val sillHeight: Length?,
    val swing: DoorSwing?,
) {
    init {
        require(offset >= Length.ZERO) { "Opening offset must not be negative: ${offset.mm}" }
        require(width > Length.ZERO) { "Opening width must be positive: ${width.mm}" }
        require(offset.mm <= Long.MAX_VALUE - width.mm) { "Opening end overflows" }
        require(height == null || height > Length.ZERO) { "Opening height must be positive" }
        require(sillHeight == null || sillHeight >= Length.ZERO) { "Sill height must not be negative" }
        require(sillHeight == null || type == OpeningType.WINDOW) { "Sill height is only valid for windows" }
        require(swing == null || type == OpeningType.DOOR) { "Swing is only valid for doors" }
    }

    /** End of the opening along the wall, `offset + width`. */
    val endOffset: Length get() = offset + width
}
