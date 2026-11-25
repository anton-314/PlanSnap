package app.plansnap.core.render

import android.content.res.Resources
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.model.RoomTypeKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class LabelTextFormatterTest {
    private fun resources(language: String): Resources {
        val app = RuntimeEnvironment.getApplication()
        val configuration = android.content.res.Configuration(app.resources.configuration)
        configuration.setLocale(Locale.forLanguageTag(language))
        return app.createConfigurationContext(configuration).resources
    }

    private fun formatter(
        language: String,
        unit: LengthUnit = LengthUnit.CENTIMETER,
    ) = LabelTextFormatter(resources(language), Locale.forLanguageTag(language), unit)

    @Test
    fun `given German when formatting the status marks then they are German`() {
        val formatter = formatter("de")
        assertEquals("Form nicht bestätigt", formatter.format(LabelText.ShapeNotConfirmed))
        assertEquals("Maße widersprüchlich", formatter.format(LabelText.ShapeConflict))
    }

    @Test
    fun `given English when formatting the status marks then they are English`() {
        val formatter = formatter("en")
        assertEquals("Shape not confirmed", formatter.format(LabelText.ShapeNotConfirmed))
        assertEquals("Measurements contradict each other", formatter.format(LabelText.ShapeConflict))
    }

    @Test
    fun `given centimeters when formatting a dimension then the decimal separator follows the language`() {
        assertEquals("345,0 cm", formatter("de").format(LabelText.Dimension(3450.0)))
        assertEquals("345.0 cm", formatter("en").format(LabelText.Dimension(3450.0)))
    }

    @Test
    fun `given other units when formatting a dimension then the unit symbol is appended`() {
        assertEquals("3450 mm", formatter("en", LengthUnit.MILLIMETER).format(LabelText.Dimension(3450.0)))
        assertEquals("3.450 m", formatter("en", LengthUnit.METER).format(LabelText.Dimension(3450.0)))
    }

    @Test
    fun `given an area and an angle when formatting then they use square meters and degrees`() {
        val formatter = formatter("de")
        assertEquals("12,00 m²", formatter.format(LabelText.Area(12_000_000.0)))
        assertEquals("90,0°", formatter.format(LabelText.InteriorAngle(90.0)))
    }

    @Test
    fun `given a room type when formatting then the resolver decides and the key is the default`() {
        val resolved =
            LabelTextFormatter(resources("en"), Locale.ENGLISH, LengthUnit.CENTIMETER) { "Type " + it.value }
        assertEquals("Type kitchen", resolved.format(LabelText.RoomType(RoomTypeKey("kitchen"))))
        assertEquals("kitchen", formatter("en").format(LabelText.RoomType(RoomTypeKey("kitchen"))))
    }

    @Test
    fun `given the same label twice when formatting then the cached text is returned`() {
        val formatter = formatter("en")
        assertSame(formatter.format(LabelText.Dimension(1000.0)), formatter.format(LabelText.Dimension(1000.0)))
    }
}
