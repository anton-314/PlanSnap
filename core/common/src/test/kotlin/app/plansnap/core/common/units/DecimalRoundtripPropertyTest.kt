package app.plansnap.core.common.units

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ValueField
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.arbitrary.int
import io.kotest.property.arbitrary.long
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Test
import java.util.Locale

/** Format then parse must return the original raw value, for every unit and both locales (fixed seed). */
@OptIn(ExperimentalKotest::class)
class DecimalRoundtripPropertyTest {
    private val config = PropTestConfig(seed = SEED, iterations = ITERATIONS)
    private val locales = listOf(Locale.GERMANY, Locale.US)

    @Test
    fun `given any valid length when formatted then parsed back it is unchanged`() =
        runBlocking<Unit> {
            // Gültiger Bereich des Parsers: 0 bis Long.MAX_VALUE mm. Die Grenzen werden zusätzlich explizit geprüft.
            listOf(0L, 1L, 9L, 10L, Long.MAX_VALUE - 1, Long.MAX_VALUE).forEach { assertLengthRoundtrip(it) }
            checkAll(config, Arb.long(0L..Long.MAX_VALUE)) { assertLengthRoundtrip(it) }
        }

    @Test
    fun `given any valid corner angle when formatted then parsed back it is unchanged`() =
        runBlocking<Unit> {
            // Gültiger Bereich: 0° < α ≤ 180°, also 0,001° bis 180° in Milligrad. Die Grenzen zusätzlich explizit.
            listOf(1, 999, 1_000, Angle.STRAIGHT.milliDegrees).forEach { assertAngleRoundtrip(it) }
            checkAll(config, Arb.int(1..Angle.STRAIGHT.milliDegrees)) { assertAngleRoundtrip(it) }
        }

    private fun assertLengthRoundtrip(mm: Long) {
        val length = Length(mm)
        LengthUnit.entries.forEach { unit ->
            locales.forEach { locale ->
                val text = DecimalLengthFormatter.format(length, unit, locale)
                val parsed = DecimalLengthParser.parse(text, unit, ValueField.WALL_LENGTH)
                check(parsed == DomainResult.Success(length)) { "$mm mm as '$text' in $unit, $locale" }
            }
        }
    }

    private fun assertAngleRoundtrip(milli: Int) {
        val angle = Angle(milli)
        locales.forEach { locale ->
            val text = DecimalAngleFormatter.format(angle, locale)
            val parsed = DecimalAngleParser.parse(text, locale)
            check(parsed == DomainResult.Success(angle)) { "$milli md as '$text' in $locale" }
        }
    }

    private companion object {
        const val SEED = 20_261_124L
        const val ITERATIONS = 1_000
    }
}
