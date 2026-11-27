package app.plansnap.feature.editor.save

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.feature.editor.state.SaveStatus

/** What the user can do about a failed save; it decides the explanation of the message. */
internal enum class PendingSaveFailure {
    /** Storing failed; saving again may work. */
    GENERAL,

    /** The device is full; the user has to free space before saving again can work. */
    NO_SPACE,
}

/**
 * The failed save that is still unresolved at [status], `null` if everything is stored.
 *
 * A failure stays unresolved until a save succeeds. [SaveStatus.Saving] says nothing about the outcome, so it keeps
 * [previous]: a new command after a failure passes through it, and the message must neither disappear nor flicker
 * then. [SaveStatus.Idle] belongs to a freshly opened project, which has no change that could still be stored.
 *
 * @param status current persistence status of the editor.
 * @param previous the unresolved failure before [status] was reported.
 */
internal fun pendingSaveFailure(
    status: SaveStatus,
    previous: PendingSaveFailure?,
): PendingSaveFailure? =
    when (status) {
        is SaveStatus.Failed -> failureOf(status.error)
        SaveStatus.Saving -> previous
        SaveStatus.Idle, is SaveStatus.Saved -> null
    }

private fun failureOf(error: DomainError): PendingSaveFailure =
    if (error is DomainError.Storage && error.problem == StorageProblem.NO_SPACE) {
        PendingSaveFailure.NO_SPACE
    } else {
        PendingSaveFailure.GENERAL
    }
