package app.plansnap.di

import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.details.RoomTypeCatalog
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.util.Locale

/**
 * The label formatter that [ExportersModule] hands to the SVG, PNG and PDF exporters speaks the language of the
 * request. `PdfDocument` does not run under Robolectric, so the texts of the PDF are checked here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(application = android.app.Application::class, qualifiers = "fr")
class ExportLabelFormatterTest {
    private val factory = ExportersModule.formatterFactory(RuntimeEnvironment.getApplication())

    private fun formatter(locale: Locale) = factory(locale, LengthUnit.CENTIMETER)

    @Test
    fun `given each catalog type when formatted in german and english then no key is shown`() {
        val german = formatter(Locale.GERMANY)
        val english = formatter(Locale.US)
        for (type in RoomTypeCatalog.types) {
            val label = LabelText.RoomType(type.key)
            assertNotEquals(type.key.value, german.format(label))
            assertNotEquals(type.key.value, english.format(label))
        }
        assertEquals("Küche", german.format(LabelText.RoomType(RoomTypeKey("kitchen"))))
        assertEquals("Kitchen", english.format(LabelText.RoomType(RoomTypeKey("kitchen"))))
    }

    @Test
    fun `given a request language when formatting shape marks then they use that language`() {
        assertNotEquals(
            formatter(Locale.GERMANY).format(LabelText.ShapeNotConfirmed),
            formatter(Locale.US).format(LabelText.ShapeNotConfirmed),
        )
    }
}
