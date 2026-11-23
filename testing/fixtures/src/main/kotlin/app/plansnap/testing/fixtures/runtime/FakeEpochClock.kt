package app.plansnap.testing.fixtures.runtime

import app.plansnap.core.common.runtime.EpochClock

/**
 * Manually driven [EpochClock]. Time only moves when a test calls [advanceBy] or [set], so tests never see the real
 * clock.
 *
 * @param startEpochMs time returned until the clock is moved.
 */
class FakeEpochClock(
    startEpochMs: Long = DEFAULT_START_EPOCH_MS,
) : EpochClock {
    @Volatile
    private var currentEpochMs: Long = startEpochMs

    override fun nowEpochMillis(): Long = currentEpochMs

    /** Moves the clock forward by [millis]. A negative value is rejected so that time stays monotonic. */
    fun advanceBy(millis: Long) {
        require(millis >= 0L) { "Clock must not run backwards: $millis" }
        currentEpochMs = Math.addExact(currentEpochMs, millis)
    }

    /** Sets the clock to [epochMs], which may be earlier than the current time to simulate clock changes. */
    fun set(epochMs: Long) {
        currentEpochMs = epochMs
    }

    /** Constants. */
    companion object {
        /** 2023-11-14T22:13:20Z, an arbitrary fixed test instant. */
        const val DEFAULT_START_EPOCH_MS: Long = 1_700_000_000_000L
    }
}
