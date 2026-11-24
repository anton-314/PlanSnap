package app.plansnap.core.common.units

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ValueField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class DecimalFormattersTest {
    private val german = Locale.GERMANY
    private val american = Locale.US

    @Test
    fun `given 3450 mm when formatted then places follow the unit in de and en`() {
        val length = Length(3450L)
        assertEquals("3450", DecimalLengthFormatter.format(length, LengthUnit.MILLIMETER, german))
        assertEquals("345,0", DecimalLengthFormatter.format(length, LengthUnit.CENTIMETER, german))
        assertEquals("3,450", DecimalLengthFormatter.format(length, LengthUnit.METER, german))
        assertEquals("3450", DecimalLengthFormatter.format(length, LengthUnit.MILLIMETER, american))
        assertEquals("345.0", DecimalLengthFormatter.format(length, LengthUnit.CENTIMETER, american))
        assertEquals("3.450", DecimalLengthFormatter.format(length, LengthUnit.METER, american))
    }

    @Test
    fun `given zero and the smallest step when formatted then no float artifacts appear`() {
        assertEquals("0,000", DecimalLengthFormatter.format(Length.ZERO, LengthUnit.METER, german))
        assertEquals("0,1", DecimalLengthFormatter.format(Length(1L), LengthUnit.CENTIMETER, german))
        assertEquals("0,001", DecimalLengthFormatter.format(Length(1L), LengthUnit.METER, german))
        assertEquals("0.001", DecimalLengthFormatter.format(Length(1L), LengthUnit.METER, american))
    }

    @Test
    fun `given extreme lengths when formatted then all digits are exact`() {
        assertEquals(
            "9223372036854775807",
            DecimalLengthFormatter.format(Length(Long.MAX_VALUE), LengthUnit.MILLIMETER, german),
        )
        assertEquals(
            "-9223372036854775808",
            DecimalLengthFormatter.format(Length(Long.MIN_VALUE), LengthUnit.MILLIMETER, american),
        )
        assertEquals(
            "9223372036854775,807",
            DecimalLengthFormatter.format(Length(Long.MAX_VALUE), LengthUnit.METER, german),
        )
    }

    @Test
    fun `given large mm values when formatted then no grouping separator is written`() {
        assertEquals("1234567", DecimalLengthFormatter.format(Length(1_234_567L), LengthUnit.MILLIMETER, german))
        assertEquals("1234,567", DecimalLengthFormatter.format(Length(1_234_567L), LengthUnit.METER, german))
    }

    @Test
    fun `given angles when formatted then no superfluous zeros are written in de and en`() {
        assertEquals("90", DecimalAngleFormatter.format(Angle.RIGHT, german))
        assertEquals("90", DecimalAngleFormatter.format(Angle.RIGHT, american))
        assertEquals("109,471", DecimalAngleFormatter.format(Angle(109_471), german))
        assertEquals("109.471", DecimalAngleFormatter.format(Angle(109_471), american))
        assertEquals("45,5", DecimalAngleFormatter.format(Angle(45_500), german))
        assertEquals("70,529", DecimalAngleFormatter.format(Angle(70_529), german))
        assertEquals("0,001", DecimalAngleFormatter.format(Angle(1), german))
        assertEquals("180", DecimalAngleFormatter.format(Angle.STRAIGHT, german))
        assertEquals("360", DecimalAngleFormatter.format(Angle.FULL, american))
        assertEquals("-45", DecimalAngleFormatter.format(Angle(-45_000), german))
    }

    @Test
    fun `given formatted lengths when parsed back then the value is unchanged`() {
        val samples = listOf(0L, 1L, 10L, 3450L, 1_234_567L, 100_000_000L, Long.MAX_VALUE)
        listOf(german, american).forEach { locale ->
            LengthUnit.entries.forEach { unit ->
                samples.forEach { mm ->
                    val text = DecimalLengthFormatter.format(Length(mm), unit, locale)
                    val parsed = DecimalLengthParser.parse(text, unit, ValueField.WALL_LENGTH)
                    assertEquals(DomainResult.Success(Length(mm)), parsed, text)
                }
            }
        }
    }

    @Test
    fun `given formatted angles when parsed back then the value is unchanged`() {
        val samples = listOf(1, 999, 70_529, 90_000, 180_000)
        listOf(german, american).forEach { locale ->
            samples.forEach { milli ->
                val text = DecimalAngleFormatter.format(Angle(milli), locale)
                val parsed = DecimalAngleParser.parse(text, locale)
                assertEquals(DomainResult.Success(Angle(milli)), parsed, text)
            }
        }
    }
}
