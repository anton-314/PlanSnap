package app.plansnap.core.common.units

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class LengthTest {
    @Test
    fun `given lengths when added and subtracted then result is exact`() {
        assertEquals(Length(5L), Length(2L) + Length(3L))
        assertEquals(Length(-1L), Length(2L) - Length(3L))
        assertEquals(Length(-2L), -Length(2L))
        assertEquals(Length.ZERO, Length(7L) - Length(7L))
    }

    @Test
    fun `given extreme lengths when combined then overflow throws`() {
        assertThrows(ArithmeticException::class.java) { Length(Long.MAX_VALUE) + Length(1L) }
        assertThrows(ArithmeticException::class.java) { Length(Long.MIN_VALUE) - Length(1L) }
        assertThrows(ArithmeticException::class.java) { -Length(Long.MIN_VALUE) }
    }

    @Test
    fun `given lengths when compared then order follows millimeters`() {
        assertTrue(Length(-1L) < Length.ZERO)
        assertTrue(Length(2L) > Length(1L))
    }

    @Test
    fun `given computed value when rounded then halves round up`() {
        assertEquals(Length(3L), Length.roundedFromMmOrNull(2.5))
        assertEquals(Length(-2L), Length.roundedFromMmOrNull(-2.5))
        assertEquals(Length(0L), Length.roundedFromMmOrNull(0.49999999999999994))
        assertEquals(Length(0L), Length.roundedFromMmOrNull(-0.0))
    }

    @Test
    fun `given non finite or out of range value when rounded then result is null`() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY, 9.223372036854775807E18, -1e19)
            .forEach { assertNull(Length.roundedFromMmOrNull(it), "value $it") }
    }

    @Test
    fun `given smallest long as double when rounded then it is representable`() {
        assertEquals(Length(Long.MIN_VALUE), Length.roundedFromMmOrNull(-9.223372036854775808E18))
    }

    @Test
    fun `given requirement examples when converted then all equal 3450 mm`() {
        val expected = Length(3450L)
        assertEquals(expected, LengthUnit.CENTIMETER.toLengthExactOrNull(BigDecimal("345")))
        assertEquals(expected, LengthUnit.METER.toLengthExactOrNull(BigDecimal("3.45")))
        assertEquals(expected, LengthUnit.MILLIMETER.toLengthExactOrNull(BigDecimal("3450")))
    }

    @Test
    fun `given value finer than one mm when converted then result is null`() {
        assertNull(LengthUnit.METER.toLengthExactOrNull(BigDecimal("1.0005")))
        assertNull(LengthUnit.CENTIMETER.toLengthExactOrNull(BigDecimal("0.01")))
        assertNull(LengthUnit.MILLIMETER.toLengthExactOrNull(BigDecimal("0.5")))
    }

    @Test
    fun `given overflowing value when converted then result is null`() {
        assertNull(LengthUnit.METER.toLengthExactOrNull(BigDecimal(Long.MAX_VALUE)))
        assertNull(LengthUnit.MILLIMETER.toLengthExactOrNull(BigDecimal("1E+1000")))
    }

    @Test
    fun `given any unit when round tripped then length is unchanged`() {
        val samples = listOf(0L, 1L, -1L, 3450L, 100_000L, Long.MAX_VALUE / 1_000L, Long.MIN_VALUE / 1_000L)
        LengthUnit.entries.forEach { unit ->
            samples.forEach { mm ->
                assertEquals(Length(mm), unit.toLengthExactOrNull(unit.fromLength(Length(mm))), "$unit $mm")
            }
        }
    }

    @Test
    fun `given length when shown in unit then decimal is exact`() {
        assertEquals(0, BigDecimal("3.45").compareTo(LengthUnit.METER.fromLength(Length(3450L))))
        assertEquals(0, BigDecimal("345").compareTo(LengthUnit.CENTIMETER.fromLength(Length(3450L))))
        assertEquals(0, BigDecimal("0.1").compareTo(LengthUnit.CENTIMETER.fromLength(Length(1L))))
    }

    @Test
    fun `given length when converted to double then millimeters are kept`() {
        assertEquals(3450.0, Length(3450L).toDoubleMm(), 0.0)
    }
}
