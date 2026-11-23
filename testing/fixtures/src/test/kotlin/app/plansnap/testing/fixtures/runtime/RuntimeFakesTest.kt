package app.plansnap.testing.fixtures.runtime

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.runTest
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class RuntimeFakesTest {
    @Test
    fun `given new clock when read repeatedly then time does not move by itself`() {
        val clock = FakeEpochClock()

        val first = clock.nowEpochMillis()
        val second = clock.nowEpochMillis()

        assertEquals(FakeEpochClock.DEFAULT_START_EPOCH_MS, first)
        assertEquals(first, second)
    }

    @Test
    fun `given clock when advanced then time moves exactly by the amount`() {
        val clock = FakeEpochClock(startEpochMs = 1_000L)

        clock.advanceBy(250L)
        clock.advanceBy(0L)

        assertEquals(1_250L, clock.nowEpochMillis())
    }

    @Test
    fun `given clock when advanced by negative amount then it is rejected and time is unchanged`() {
        val clock = FakeEpochClock(startEpochMs = 1_000L)

        assertThrows<IllegalArgumentException> { clock.advanceBy(-1L) }

        assertEquals(1_000L, clock.nowEpochMillis())
    }

    @Test
    fun `given clock when set to an earlier time then it jumps back`() {
        val clock = FakeEpochClock(startEpochMs = 5_000L)

        clock.set(42L)

        assertEquals(42L, clock.nowEpochMillis())
    }

    @Test
    fun `given clock when advance overflows then it fails instead of wrapping`() {
        val clock = FakeEpochClock(startEpochMs = Long.MAX_VALUE)

        assertThrows<ArithmeticException> { clock.advanceBy(1L) }
    }

    @Test
    fun `given generator when asked repeatedly then ids are sequential and non-blank`() {
        val ids = SequentialIdGenerator("room")

        val generated = List(3) { ids.newId() }

        assertEquals(listOf("room-1", "room-2", "room-3"), generated)
    }

    @Test
    fun `given two generators with equal prefix then they produce the same sequence`() {
        val a = SequentialIdGenerator()
        val b = SequentialIdGenerator()

        assertEquals(List(5) { a.newId() }, List(5) { b.newId() })
    }

    @Test
    fun `given blank prefix when creating generator then it is rejected`() {
        assertThrows<IllegalArgumentException> { SequentialIdGenerator(" ") }
    }

    @Test
    fun `given provider when roles are read then all roles share the test dispatcher`() {
        val dispatcher = StandardTestDispatcher()
        val provider = TestDispatcherProvider(dispatcher)

        assertSame(dispatcher, provider.main)
        assertSame(dispatcher, provider.default)
        assertSame(dispatcher, provider.io)
    }

    @Test
    fun `given standard dispatcher when work is launched then it waits for the scheduler`() =
        runTest {
            val provider = TestDispatcherProvider(StandardTestDispatcher(testScheduler))
            val scope = TestScope(provider.io)
            var ran = false

            scope.launch(provider.io) { ran = true }
            assertTrue(!ran)
            testScheduler.advanceUntilIdle()

            assertTrue(ran)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `given provider with unconfined dispatcher when work is launched then it runs immediately`() =
        runTest {
            val provider = TestDispatcherProvider(UnconfinedTestDispatcher())
            var ran = false

            launch(provider.default) { ran = true }

            assertTrue(ran)
        }

    @Test
    fun `given default provider then a fresh dispatcher is created per instance`() {
        assertNotEquals(TestDispatcherProvider().dispatcher, TestDispatcherProvider().dispatcher)
    }
}
