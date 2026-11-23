package app.plansnap.core.geometry.cancellation

/**
 * Cooperative cancellation of long computations, free of any coroutine dependency (ADR 0016).
 *
 * Solver, adjustment and rank analysis call [checkpoint] at least before each start value, once per iteration and once
 * per elimination step. A cancelled computation yields no partial result.
 */
fun interface CancellationSignal {
    /** Returns normally while the computation may continue, otherwise throws `CancellationException`. */
    fun checkpoint()

    /** Constants. */
    companion object {
        /** Signal that never cancels. */
        val NONE: CancellationSignal = CancellationSignal { }
    }
}
