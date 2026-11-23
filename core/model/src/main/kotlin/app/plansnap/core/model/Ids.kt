package app.plansnap.core.model

/** Stable project identifier, not blank. */
@JvmInline
value class ProjectId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "ProjectId must not be blank" }
    }
}

/** Stable room identifier, not blank. */
@JvmInline
value class RoomId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "RoomId must not be blank" }
    }
}

/** Stable wall identifier, not blank. */
@JvmInline
value class WallId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "WallId must not be blank" }
    }
}

/** Stable diagonal identifier, not blank. */
@JvmInline
value class DiagonalId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "DiagonalId must not be blank" }
    }
}

/** Stable opening identifier, not blank. */
@JvmInline
value class OpeningId(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "OpeningId must not be blank" }
    }
}

/** Key of a room type template (REQ-MOD-011), not blank. The template catalog is not part of the model. */
@JvmInline
value class RoomTypeKey(
    val value: String,
) {
    init {
        require(value.isNotBlank()) { "RoomTypeKey must not be blank" }
    }
}
