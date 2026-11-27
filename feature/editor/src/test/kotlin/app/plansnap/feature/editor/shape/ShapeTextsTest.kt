package app.plansnap.feature.editor.shape

import android.content.res.Configuration
import android.content.res.Resources
import app.plansnap.core.common.result.DocumentProblem
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
class ShapeTextsTest {
    private fun resources(locale: Locale): Resources {
        val configuration = Configuration(RuntimeEnvironment.getApplication().resources.configuration)
        configuration.setLocale(locale)
        return RuntimeEnvironment.getApplication().createConfigurationContext(configuration).resources
    }

    private val allErrors: List<DomainError> =
        buildList {
            ValueField.entries.forEach { field ->
                InvalidValueReason.entries.forEach { add(DomainError.InvalidValue(field, it)) }
            }
            EntityKind.entries.forEach { add(DomainError.NotFound(it, "x")) }
            DomainRule.entries.forEach { add(DomainError.RuleViolation(it)) }
            DocumentProblem.entries.forEach { add(DomainError.Document(it)) }
            StorageProblem.entries.forEach { add(DomainError.Storage(it)) }
            ExportProblem.entries.forEach { add(DomainError.Export(it)) }
        }

    @Test
    fun `given any domain error then both languages have a complete message`() {
        listOf(Locale.ENGLISH, Locale.GERMAN).forEach { locale ->
            val resources = resources(locale)
            allErrors.forEach { error ->
                val message = resources.errorMessage(error)
                assertTrue("$locale $error", message.isNotBlank())
                assertFalse("$locale $error: $message", message.contains('%') || message.contains("\$s"))
            }
        }
    }

    @Test
    fun `given two different rules then their messages differ so the user can tell them apart`() {
        val resources = resources(Locale.ENGLISH)
        val messages = DomainRule.entries.map { resources.errorMessage(DomainError.RuleViolation(it)) }
        assertEquals(messages.size, messages.toSet().size)
    }

    @Test
    fun `given a rule then the german message is not the english one`() {
        val english = resources(Locale.ENGLISH)
        val german = resources(Locale.GERMAN)
        DomainRule.entries.forEach {
            val error = DomainError.RuleViolation(it)
            assertNotEquals("$it", english.errorMessage(error), german.errorMessage(error))
        }
    }

    @Test
    fun `given a rejected adjustment then the messages tell what to do`() {
        val english = resources(Locale.ENGLISH)
        assertEquals(
            "The proposal is out of date or no longer available. Review the room again.",
            english.errorMessage(DomainError.RuleViolation(DomainRule.ADJUSTMENT_NOT_AVAILABLE)),
        )
        assertEquals(
            "This is a large correction. Check your measurements and confirm it explicitly.",
            english.errorMessage(DomainError.RuleViolation(DomainRule.EXPLICIT_CONFIRMATION_REQUIRED)),
        )
    }

    @Test
    fun `given an invalid value then the message names the value and the reason`() {
        val english = resources(Locale.ENGLISH)
        assertEquals(
            "The wall length is too large.",
            english.errorMessage(DomainError.InvalidValue(ValueField.WALL_LENGTH, InvalidValueReason.TOO_LARGE)),
        )
        assertEquals(
            "Die Wandstärke hat zu viele Nachkommastellen.",
            resources(Locale.GERMAN)
                .errorMessage(
                    DomainError.InvalidValue(ValueField.WALL_THICKNESS, InvalidValueReason.NOT_REPRESENTABLE),
                ),
        )
    }

    @Test
    fun `given measurements then they are named one-based and a missing diagonal falls back to its number`() {
        val english = resources(Locale.ENGLISH)
        val room = roomOf(3000, 3000, 3000, 5000, closed = true, diagonals = listOf(diagonalOf(0, 2, 4300)))
        assertEquals("wall 1", english.observationName(ObservationRef.WallLength(0), room))
        assertEquals("angle at corner 4", english.observationName(ObservationRef.CornerAngle(3), room))
        assertEquals(
            "diagonal from corner 1 to corner 3",
            english.observationName(ObservationRef.DiagonalLength(0), room),
        )
        assertEquals("diagonal 6", english.observationName(ObservationRef.DiagonalLength(5), room))
        assertEquals("diagonal 1", english.observationName(ObservationRef.DiagonalLength(0), null))
    }

    @Test
    fun `given a recommendation then the sentence names its corners`() {
        val german = resources(Locale.GERMAN)
        assertEquals(
            "Miss die Diagonale von Ecke 2 nach Ecke 4.",
            german.recommendationText(MeasurementRecommendation.DiagonalBetween(1, 3)),
        )
        assertEquals(
            "Miss den Winkel an Ecke 1.",
            german.recommendationText(MeasurementRecommendation.CornerAngle(0)),
        )
    }
}
