package app.plansnap.core.designsystem.component

/** Evaluation state of a room shape as shown to the user. Presentation only; the decision is made elsewhere. */
enum class ShapeStatus {
    /** The shape is closed and confirmed. */
    Confirmed,

    /** The shape is plausible but not yet confirmed ("shape not confirmed"). */
    Unconfirmed,

    /** The measurements contradict each other. */
    Conflicting,
}
