package app.plansnap.core.domain.evaluation

import app.plansnap.core.geometry.cancellation.CancellationSignal
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive

/** Returns a signal that follows the job of the calling coroutine (ADR 0016). */
suspend fun currentCancellationSignal(): CancellationSignal {
    val context = currentCoroutineContext()
    return CancellationSignal { context.ensureActive() }
}
