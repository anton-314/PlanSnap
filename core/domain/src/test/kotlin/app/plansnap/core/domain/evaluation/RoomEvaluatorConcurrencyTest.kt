package app.plansnap.core.domain.evaluation

import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Job
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.util.concurrent.Executors
import kotlin.coroutines.coroutineContext

/** Dispatcher, cancellation and determinism of the evaluation (ADR 0016, DOMAIN section 11). */
class RoomEvaluatorConcurrencyTest {
    @Test
    fun `given an evaluation when it runs then every computation runs on the injected default dispatcher`() {
        val executor = Executors.newSingleThreadExecutor { Thread(it, DEFAULT_THREAD) }
        try {
            val threads = mutableListOf<String>()
            val recording =
                RecordingComponents(onCall = {
                    threads +=
                        Thread.currentThread().name.substringBefore(COROUTINE_SUFFIX)
                })
            val dispatchers = SingleDispatchers(executor.asCoroutineDispatcher())
            val evaluation =
                evaluated(ReferenceRooms.TRAPEZOID_3_3_3_5.room, evaluator = recording.evaluator(dispatchers))
            assertEquals(ProposalReason.UNDERDETERMINED, proposalOf(evaluation))
            assertTrue(threads.isNotEmpty())
            assertEquals(setOf(DEFAULT_THREAD), threads.toSet())
        } finally {
            executor.shutdown()
        }
    }

    @Test
    fun `given a running evaluation when its job is cancelled then the geometry stops without a result`() =
        runBlocking<Unit> {
            lateinit var job: Job
            var completed = false
            val recording =
                RecordingComponents(
                    onCall = { signal ->
                        job.cancel()
                        signal.checkpoint()
                    },
                )
            job =
                launch {
                    recording.evaluator().evaluate(ReferenceRooms.TRAPEZOID_3_3_3_5.room, DEFAULT_TOLERANCE)
                    completed = true
                }
            job.join()
            assertTrue(job.isCancelled)
            assertFalse(completed)
            // The first cancellable call throws, so neither solver nor adjustment runs afterwards.
            assertEquals(listOf("closureError", "targets", "classify", "analyzeConstraints"), recording.calls)
        }

    @Test
    fun `given a cancelled job when an evaluation starts then nothing is computed`() =
        runBlocking<Unit> {
            val recording = RecordingComponents()
            var completed = false
            val job =
                launch {
                    coroutineContext.job.cancel()
                    recording.evaluator().evaluate(OverdeterminedCases.GAP_30_MM.room, DEFAULT_TOLERANCE)
                    completed = true
                }
            job.join()
            assertFalse(completed)
            assertEquals(emptyList<String>(), recording.calls)
        }

    @Test
    fun `given equal input when evaluated twice by separate evaluators then the results are bit identical`() {
        val rooms = OverdeterminedCases.ALL.map { it.room } + ReferenceRooms.ALL.map { it.room }
        for (room in rooms) {
            val first = evaluated(room, evaluator = DefaultRoomEvaluator(DirectDispatchers))
            val second = evaluated(room, evaluator = DefaultRoomEvaluator(DirectDispatchers))
            // Data class equality compares every Double by its bits, which is the point of this test.
            assertEquals(first, second, "${room.id}")
            val firstShape = first.shape ?: continue
            val secondShape = second.shape!!
            assertEquals(firstShape.areaMm2.toRawBits(), secondShape.areaMm2.toRawBits(), "${room.id}")
            assertEquals(firstShape.turnsRad.map(Double::toRawBits), secondShape.turnsRad.map(Double::toRawBits))
        }
    }

    private class SingleDispatchers(
        dispatcher: CoroutineDispatcher,
    ) : DispatcherProvider {
        override val main: CoroutineDispatcher = dispatcher
        override val default: CoroutineDispatcher = dispatcher
        override val io: CoroutineDispatcher = dispatcher
    }

    private companion object {
        const val DEFAULT_THREAD = "evaluation-default"

        /** Separator of the coroutine name that debug mode appends to the thread name. */
        const val COROUTINE_SUFFIX = " @"
    }
}
