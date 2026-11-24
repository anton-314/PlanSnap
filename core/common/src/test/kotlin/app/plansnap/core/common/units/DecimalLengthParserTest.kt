package app.plansnap.core.common.units

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class DecimalLengthParserTest {
    private fun parsed(
        text: String,
        unit: LengthUnit,
        field: ValueField = ValueField.WALL_LENGTH,
    ) = DecimalLengthParser.parse(text, unit, field)

    private fun failure(
        reason: InvalidValueReason,
        field: ValueField = ValueField.WALL_LENGTH,
    ) = DomainResult.Failure(DomainError.InvalidValue(field, reason))

    @Test
    fun `given requirement examples when parsed then all equal 3450 mm`() {
        val expected = DomainResult.Success(Length(3450L))
        assertEquals(expected, parsed("345", LengthUnit.CENTIMETER))
        assertEquals(expected, parsed("3,45", LengthUnit.METER))
        assertEquals(expected, parsed("3.45", LengthUnit.METER))
        assertEquals(expected, parsed("3450", LengthUnit.MILLIMETER))
    }

    @Test
    fun `given comma or point as separator when parsed then both are accepted in every unit`() {
        assertEquals(DomainResult.Success(Length(1_250L)), parsed("125,0", LengthUnit.CENTIMETER))
        assertEquals(DomainResult.Success(Length(1_250L)), parsed("125.0", LengthUnit.CENTIMETER))
        assertEquals(DomainResult.Success(Length(1_250L)), parsed("1,25", LengthUnit.METER))
        assertEquals(DomainResult.Success(Length(1_250L)), parsed("1.25", LengthUnit.METER))
    }

    @Test
    fun `given surrounding whitespace when parsed then it is ignored`() {
        assertEquals(DomainResult.Success(Length(3450L)), parsed("  345 ", LengthUnit.CENTIMETER))
    }

    @Test
    fun `given zero when parsed then zero length is accepted`() {
        assertEquals(DomainResult.Success(Length.ZERO), parsed("0", LengthUnit.MILLIMETER))
        assertEquals(DomainResult.Success(Length.ZERO), parsed("0,000", LengthUnit.METER))
    }

    @Test
    fun `given the finest unit step when parsed then exactly 1 mm is accepted`() {
        assertEquals(DomainResult.Success(Length(1L)), parsed("0,1", LengthUnit.CENTIMETER))
        assertEquals(DomainResult.Success(Length(1L)), parsed("0,001", LengthUnit.METER))
    }

    @Test
    fun `given values finer than 1 mm when parsed then they are rejected not rounded`() {
        // Halbwerte: 0,5 mm wird weder auf 0 noch auf 1 gerundet.
        assertEquals(failure(InvalidValueReason.NOT_REPRESENTABLE), parsed("0,5", LengthUnit.MILLIMETER))
        assertEquals(failure(InvalidValueReason.NOT_REPRESENTABLE), parsed("3450,5", LengthUnit.MILLIMETER))
        assertEquals(failure(InvalidValueReason.NOT_REPRESENTABLE), parsed("0,05", LengthUnit.CENTIMETER))
        assertEquals(failure(InvalidValueReason.NOT_REPRESENTABLE), parsed("3,4505", LengthUnit.METER))
        assertEquals(failure(InvalidValueReason.NOT_REPRESENTABLE), parsed("1,0005", LengthUnit.METER))
    }

    @Test
    fun `given negative values when parsed then they are too small`() {
        assertEquals(failure(InvalidValueReason.TOO_SMALL), parsed("-1", LengthUnit.MILLIMETER))
        assertEquals(failure(InvalidValueReason.TOO_SMALL), parsed("-0,5", LengthUnit.CENTIMETER))
        assertEquals(failure(InvalidValueReason.TOO_SMALL), parsed("-3,45", LengthUnit.METER))
        assertEquals(failure(InvalidValueReason.TOO_SMALL), parsed("-99999999999999999999", LengthUnit.MILLIMETER))
    }

    @Test
    fun `given values beyond the long range when parsed then they are too large`() {
        assertEquals(failure(InvalidValueReason.TOO_LARGE), parsed("9223372036854775808", LengthUnit.MILLIMETER))
        assertEquals(failure(InvalidValueReason.TOO_LARGE), parsed("922337203685477580.8", LengthUnit.CENTIMETER))
        assertEquals(failure(InvalidValueReason.TOO_LARGE), parsed("9223372036854775.808", LengthUnit.METER))
        assertEquals(failure(InvalidValueReason.TOO_LARGE), parsed("9".repeat(30), LengthUnit.METER))
    }

    @Test
    fun `given the largest long in each unit when parsed then it is accepted`() {
        assertEquals(
            DomainResult.Success(Length(Long.MAX_VALUE)),
            parsed("9223372036854775807", LengthUnit.MILLIMETER),
        )
        assertEquals(
            DomainResult.Success(Length(Long.MAX_VALUE)),
            parsed("922337203685477580.7", LengthUnit.CENTIMETER),
        )
    }

    @Test
    fun `given malformed text when parsed then it is malformed`() {
        val malformed =
            listOf("", " ", "abc", "3,4,5", "3.4.5", "3,", ",5", "3 45", "+5", "1e3", "NaN", "٣٤٥", "1".repeat(33))
        malformed.forEach {
            assertEquals(failure(InvalidValueReason.MALFORMED), parsed(it, LengthUnit.MILLIMETER), it)
        }
    }

    @Test
    fun `given a field when rejected then the error names that field`() {
        assertEquals(
            failure(InvalidValueReason.MALFORMED, ValueField.DIAGONAL_LENGTH),
            parsed("abc", LengthUnit.CENTIMETER, ValueField.DIAGONAL_LENGTH),
        )
        assertEquals(
            failure(InvalidValueReason.TOO_SMALL, ValueField.OPENING_WIDTH),
            parsed("-1", LengthUnit.CENTIMETER, ValueField.OPENING_WIDTH),
        )
        assertEquals(
            failure(InvalidValueReason.TOO_LARGE, ValueField.ROOM_HEIGHT),
            parsed("9".repeat(30), LengthUnit.METER, ValueField.ROOM_HEIGHT),
        )
    }
}
