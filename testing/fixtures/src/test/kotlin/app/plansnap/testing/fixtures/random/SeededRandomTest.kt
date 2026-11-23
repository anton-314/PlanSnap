package app.plansnap.testing.fixtures.random

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class SeededRandomTest {
    @Test
    fun `given seed zero when drawing then the published SplitMix64 reference values come out`() {
        val random = SeededRandom(0L)

        assertEquals(-0x1ddf57c684e23251L, random.nextLong())
        assertEquals(0x6e789e6aa1b965f4L, random.nextLong())
        assertEquals(0x06c45d188009454fL, random.nextLong())
    }

    @Test
    fun `given equal seeds when drawing then the sequences are identical`() {
        val a = SeededRandom(1234L)
        val b = SeededRandom(1234L)

        assertEquals(List(100) { a.nextLong() }, List(100) { b.nextLong() })
    }

    @Test
    fun `given different seeds when drawing then the sequences differ`() {
        assertNotEquals(SeededRandom(1L).nextLong(), SeededRandom(2L).nextLong())
    }

    @Test
    fun `given bound when drawing then every value is inside and all values occur`() {
        val random = SeededRandom(7L)

        val values = List(2_000) { random.nextInt(5) }

        assertEquals(setOf(0, 1, 2, 3, 4), values.toSet())
    }

    @Test
    fun `given range when drawing then both ends are reachable and nothing is outside`() {
        val random = SeededRandom(8L)

        val values = List(2_000) { random.nextInt(-2..2) }

        assertEquals((-2..2).toSet(), values.toSet())
    }

    @Test
    fun `given step when drawing multiples then every value is aligned`() {
        val random = SeededRandom(9L)

        val values = List(500) { random.nextMultiple(100..300, 50) }

        assertTrue(values.all { it % 50 == 0 && it in 100..300 })
        assertEquals(setOf(100, 150, 200, 250, 300), values.toSet())
    }

    @Test
    fun `given misaligned range when drawing multiples then it is rejected`() {
        assertThrows<IllegalArgumentException> { SeededRandom(0L).nextMultiple(101..300, 50) }
    }

    @Test
    fun `given invalid bound or empty range then it is rejected`() {
        val random = SeededRandom(0L)

        assertThrows<IllegalArgumentException> { random.nextInt(0) }
        assertThrows<IllegalArgumentException> { random.nextInt(IntRange.EMPTY) }
    }

    @Test
    fun `given draws then doubles are in the unit interval and booleans take both values`() {
        val random = SeededRandom(11L)

        val doubles = List(1_000) { random.nextDouble() }
        val booleans = List(100) { random.nextBoolean() }

        assertTrue(doubles.all { it >= 0.0 && it < 1.0 })
        assertEquals(setOf(true, false), booleans.toSet())
    }
}
