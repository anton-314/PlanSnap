package app.plansnap.core.common.result

/** Sealed hierarchy of expected failures. Features translate each case into a string resource. */
sealed interface DomainError {
    /** A user or file value is outside its permitted range. */
    data class InvalidValue(
        val field: ValueField,
        val reason: InvalidValueReason,
    ) : DomainError

    /** A referenced entity does not exist. [id] is the raw identifier. */
    data class NotFound(
        val entity: EntityKind,
        val id: String,
    ) : DomainError

    /** A command violates a domain [rule]. */
    data class RuleViolation(
        val rule: DomainRule,
    ) : DomainError

    /** A project document cannot be read. */
    data class Document(
        val problem: DocumentProblem,
    ) : DomainError

    /** A storage operation failed at the data boundary. */
    data class Storage(
        val problem: StorageProblem,
    ) : DomainError

    /** An export could not be produced. */
    data class Export(
        val problem: ExportProblem,
    ) : DomainError
}

/** Value that failed validation. */
enum class ValueField {
    /** Wall length. */
    WALL_LENGTH,

    /** Wall thickness. */
    WALL_THICKNESS,

    /** Corner angle. */
    CORNER_ANGLE,

    /** Diagonal length. */
    DIAGONAL_LENGTH,

    /** Opening offset along its wall. */
    OPENING_OFFSET,

    /** Opening width. */
    OPENING_WIDTH,

    /** Opening height. */
    OPENING_HEIGHT,

    /** Window sill height. */
    SILL_HEIGHT,

    /** Room or project height. */
    ROOM_HEIGHT,

    /** Area credit factor. */
    AREA_FACTOR,

    /** Closure tolerance setting. */
    TOLERANCE,

    /** Project or room name. */
    NAME,
}

/** Reason a value was rejected. */
enum class InvalidValueReason {
    /** The text is not a number in the expected format. */
    MALFORMED,

    /** The value is below the permitted minimum. */
    TOO_SMALL,

    /** The value is above the permitted maximum. */
    TOO_LARGE,

    /** The value cannot be represented without loss or overflow. */
    NOT_REPRESENTABLE,

    /** The value is not applicable to this kind of element. */
    NOT_APPLICABLE,
}

/** Kind of entity referenced by [DomainError.NotFound]. */
enum class EntityKind {
    /** A project. */
    PROJECT,

    /** A room. */
    ROOM,

    /** A wall. */
    WALL,

    /** A corner. */
    CORNER,

    /** A diagonal. */
    DIAGONAL,

    /** An opening. */
    OPENING,
}

/** Domain rule a command can violate. */
enum class DomainRule {
    /** The room already has the maximum number of walls. */
    TOO_MANY_WALLS,

    /** The room needs at least three walls for this action. */
    TOO_FEW_WALLS,

    /** The action requires a room that is not closed. */
    ROOM_ALREADY_CLOSED,

    /** The action requires a closed room. */
    ROOM_NOT_CLOSED,

    /** Only the closing corner may be unspecified. */
    CORNER_MUST_BE_SPECIFIED,

    /** A diagonal must connect two different existing corners. */
    DIAGONAL_INVALID_CORNERS,

    /** The opening does not fit on its wall. */
    OPENING_OUT_OF_BOUNDS,

    /** The opening overlaps another opening on the same wall. */
    OPENING_OVERLAP,

    /** The docking would create a cycle. */
    DOCK_CYCLE,

    /** The referenced host opening is not on the host wall. */
    DOCK_OPENING_NOT_ON_WALL,

    /** No valid closure adjustment is available. */
    ADJUSTMENT_NOT_AVAILABLE,

    /** A new element uses an id that already exists. */
    DUPLICATE_ID,

    /** A room cannot dock to itself. */
    DOCK_TO_SELF,

    /** The room has no wall that could be removed. */
    NO_WALL_TO_REMOVE,

    /** The wall to remove still carries openings. */
    WALL_HAS_OPENINGS,

    /** The wall to remove is still part of a dock link. */
    WALL_HAS_DOCKED_ROOMS,

    /** The corner to remove is still used by a diagonal. */
    CORNER_HAS_DIAGONALS,

    /** The opening cannot move to another wall while a room is docked to it. */
    OPENING_HAS_DOCKED_ROOMS,

    /** The proposed adjustment is a large correction and was accepted without an explicit confirmation. */
    EXPLICIT_CONFIRMATION_REQUIRED,
}

/** Reason a project document was rejected. */
enum class DocumentProblem {
    /** The content is not a valid document. */
    MALFORMED,

    /** The document has a newer major format version. */
    UNSUPPORTED_VERSION,

    /** The document exceeds the size limit. */
    TOO_LARGE,

    /** The document contains values outside their permitted ranges. */
    INVALID_VALUES,
}

/** Technical storage failure translated at the data boundary. */
enum class StorageProblem {
    /** Reading failed. */
    READ_FAILED,

    /** Writing failed. */
    WRITE_FAILED,

    /** The device has no free space. */
    NO_SPACE,
}

/** Reason an export failed. */
enum class ExportProblem {
    /** The project contains no exportable room. */
    NOTHING_TO_EXPORT,

    /** The plan does not fit at 1:200 on A3. */
    PLAN_TOO_LARGE,

    /** Writing the output failed. */
    WRITE_FAILED,

    /** The plan fits automatically, but not with the selected scale or paper. */
    PLAN_DOES_NOT_FIT_SELECTION,

    /** The output could not be encoded. */
    ENCODING_FAILED,

    /** The device has no free space for the output. */
    NO_SPACE,
}
