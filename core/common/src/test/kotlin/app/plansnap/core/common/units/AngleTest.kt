package app.plansnap.core.common.units

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.math.PI

class AngleTest {
    @Test
    fun `given angles when combined then result is exact`() {
        assertEquals(Angle(3), Angle(1) + Angle(2))
        assertEquals(Angle(-1), Angle(1) - Angle(2))
        assertEquals(Angle(-1), -Angle(1))
        assertTrue(Angle(1) > Angle.ZERO)
    }

    @Test
    fun `given extreme angles when combined then overflow throws`() {
        assertThrows(ArithmeticException::class.java) { Angle(Int.MAX_VALUE) + Angle(1) }
        assertThrows(ArithmeticException::class.java) { Angle(Int.MIN_VALUE) - Angle(1) }
        assertThrows(ArithmeticException::class.java) { -Angle(Int.MIN_VALUE) }
    }

    @Test
    fun `given angles when normalized then range is half open`() {
        assertEquals(Angle.STRAIGHT, Angle.STRAIGHT.normalized())
        assertEquals(Angle.STRAIGHT, (-Angle.STRAIGHT).normalized())
        assertEquals(Angle(-179_999), Angle(180_001).normalized())
        assertEquals(Angle.ZERO, Angle.FULL.normalized())
        assertEquals(Angle(-45_000), Angle(315_000).normalized())
        assertEquals(Angle(90_000), Angle(-270_000).normalized())
    }

    @Test
    fun `given extreme ints when normalized then result stays in range`() {
        listOf(Int.MIN_VALUE, Int.MAX_VALUE).forEach {
            val normalized = Angle(it).normalized().milliDegrees
            assertTrue(normalized > -180_000 && normalized <= 180_000, "value $it")
            assertEquals(0, Math.floorMod(it.toLong() - normalized.toLong(), 360_000L))
        }
    }

    @Test
    fun `given corner angle limits when validated then only open zero to 180 is valid`() {
        assertFalse(Angle.ZERO.isValidCornerAngle())
        assertFalse(Angle(-1).isValidCornerAngle())
        assertTrue(Angle(1).isValidCornerAngle())
        assertTrue(Angle.STRAIGHT.isValidCornerAngle())
        assertFalse(Angle(180_001).isValidCornerAngle())
    }

    @Test
    fun `given corner angle when converted then turn follows convention`() {
        assertEquals(Angle.RIGHT, Angle.RIGHT.toTurn(TurnDirection.LEFT))
        assertEquals(-Angle.RIGHT, Angle.RIGHT.toTurn(TurnDirection.RIGHT))
        assertEquals(Angle.ZERO, Angle.STRAIGHT.toTurn(TurnDirection.LEFT))
        assertEquals(Angle(109_471), Angle(70_529).toTurn(TurnDirection.LEFT))
        assertEquals(Angle(-179_999), Angle(1).toTurn(TurnDirection.RIGHT))
    }

    @Test
    fun `given invalid corner angle when converted then it is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { Angle.ZERO.toTurn(TurnDirection.LEFT) }
        assertThrows(IllegalArgumentException::class.java) { Angle(180_001).toTurn(TurnDirection.RIGHT) }
    }

    @Test
    fun `given angle when converted to radians then value is correct`() {
        assertEquals(PI / 2.0, Angle.RIGHT.toRadians(), 1e-15)
        assertEquals(-PI, (-Angle.STRAIGHT).toRadians(), 1e-15)
        assertEquals(0.0, Angle.ZERO.toRadians(), 0.0)
    }

    @Test
    fun `given radians when rounded then nearest millidegree is returned`() {
        assertEquals(Angle.RIGHT, Angle.roundedFromRadiansOrNull(PI / 2.0))
        assertEquals(Angle(-45_000), Angle.roundedFromRadiansOrNull(-PI / 4.0))
    }

    @Test
    fun `given non finite or too large radians when rounded then result is null`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 1e9, -1e9, Double.MAX_VALUE)
            .forEach { assertNull(Angle.roundedFromRadiansOrNull(it), "value $it") }
    }

    @Test
    fun `given decimal degrees when converted then only exact values pass`() {
        assertEquals(Angle(70_529), Angle.ofDegreesExactOrNull(BigDecimal("70.529")))
        assertEquals(Angle(-90_000), Angle.ofDegreesExactOrNull(BigDecimal("-90")))
        assertNull(Angle.ofDegreesExactOrNull(BigDecimal("70.5291")))
        assertNull(Angle.ofDegreesExactOrNull(BigDecimal("3000000")))
    }

    @Test
    fun `given turn directions when read then signs match the convention`() {
        assertEquals(1, TurnDirection.LEFT.sign)
        assertEquals(-1, TurnDirection.RIGHT.sign)
    }
}
