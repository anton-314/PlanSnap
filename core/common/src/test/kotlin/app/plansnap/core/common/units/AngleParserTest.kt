package app.plansnap.core.common.units

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import java.util.Locale

class AngleParserTest {
    private fun parsed(
        text: String,
        locale: Locale = Locale.GERMANY,
    ) = DecimalAngleParser.parse(text, locale)

    private fun failure(reason: InvalidValueReason) =
        DomainResult.Failure(DomainError.InvalidValue(ValueField.CORNER_ANGLE, reason))

    @Test
    fun `given limits of the range when parsed then only values above 0 up to 180 pass`() {
        assertEquals(DomainResult.Success(Angle(1)), parsed("0,001"))
        assertEquals(DomainResult.Success(Angle.STRAIGHT), parsed("180"))
        assertEquals(DomainResult.Success(Angle.STRAIGHT), parsed("180,000"))
        assertEquals(failure(InvalidValueReason.TOO_SMALL), parsed("0"))
        assertEquals(failure(InvalidValueReason.TOO_SMALL), parsed("-90"))
        assertEquals(failure(InvalidValueReason.TOO_LARGE), parsed("180,001"))
        assertEquals(failure(InvalidValueReason.TOO_LARGE), parsed("99999999999"))
    }

    @Test
    fun `given decimal separators when parsed then locale comma and point are accepted`() {
        val expected = DomainResult.Success(Angle(90_125))
        assertEquals(expected, parsed("90,125", Locale.GERMANY))
        assertEquals(expected, parsed("90.125", Locale.GERMANY))
        assertEquals(expected, parsed("90.125", Locale.US))
        assertEquals(expected, parsed("90,125", Locale.US))
        assertEquals(expected, parsed("90\u066B125", Locale.forLanguageTag("ar-EG-u-nu-arab")))
        assertEquals(expected, parsed("  90,125 "))
    }

    @Test
    fun `given millidegree precision when parsed then no value is rounded`() {
        assertEquals(DomainResult.Success(Angle(70_529)), parsed("70,529"))
        assertEquals(DomainResult.Success(Angle(90_100)), parsed("90,1000"))
        assertEquals(failure(InvalidValueReason.NOT_REPRESENTABLE), parsed("90,1255"))
        assertEquals(failure(InvalidValueReason.NOT_REPRESENTABLE), parsed("0,0001"))
    }

    @Test
    fun `given malformed text when parsed then it is rejected`() {
        listOf("", " ", "abc", "90,", ",5", "1,2,3", "9e1", "NaN", "٩٠", "1".repeat(33)).forEach {
            assertEquals(failure(InvalidValueReason.MALFORMED), parsed(it), it)
        }
    }
}
