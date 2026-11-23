package app.plansnap.testing.fixtures.fakes

import app.plansnap.core.common.result.DomainError

/**
 * Lets a test trigger the failure paths of a fake. A failure is returned by the operation as a
 * `DomainResult.Failure` before the fake changes any state, exactly like a transactional storage that rolled back.
 *
 * @param Op operations of the fake that can fail.
 */
class FailureInjector<Op : Enum<Op>> {
    private val queued = mutableMapOf<Op, ArrayDeque<DomainError>>()
    private val permanent = mutableMapOf<Op, DomainError>()

    /** Makes the next call of [operation] fail with [error]. Several calls queue up in order. */
    fun failNext(
        operation: Op,
        error: DomainError,
    ) {
        synchronized(this) { queued.getOrPut(operation) { ArrayDeque() }.addLast(error) }
    }

    /** Makes every call of [operation] fail with [error] until [reset] is called. Queued failures go first. */
    fun failAlways(
        operation: Op,
        error: DomainError,
    ) {
        synchronized(this) { permanent[operation] = error }
    }

    /** Removes all queued and permanent failures. */
    fun reset() {
        synchronized(this) {
            queued.clear()
            permanent.clear()
        }
    }

    /** Returns the failure to report for this call of [operation], or `null` if it should succeed. */
    internal fun take(operation: Op): DomainError? =
        synchronized(this) { queued[operation]?.removeFirstOrNull() ?: permanent[operation] }
}
