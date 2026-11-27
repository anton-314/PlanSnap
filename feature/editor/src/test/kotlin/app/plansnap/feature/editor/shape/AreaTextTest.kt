package app.plansnap.feature.editor.shape

import app.plansnap.feature.editor.details.DetailsFormat
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test
import java.util.Locale

/** The status card and the area overview round an area the same way (DOMAIN section 7). */
class AreaTextTest {
    private val original = Locale.getDefault()

    @After
    fun restoreLocale() = Locale.setDefault(original)

    @Test
    fun `given an exact half of a hundredth when formatted for the status card then it rounds up`() {
        Locale.setDefault(Locale.US)
        assertEquals("2.68", areaText(2_675_000.0))
        assertEquals("1.01", areaText(1_005_000.0))
        assertEquals("2.67", areaText(2_674_999.0))
    }

    @Test
    fun `given half values in both languages when formatted then card and overview agree`() {
        for (locale in listOf(Locale.US, Locale.GERMANY)) {
            Locale.setDefault(locale)
            for (mm2 in listOf(5_000.0, 1_005_000.0, 1_015_000.0, 2_665_000.0, 2_675_000.0, 12_345_000.0)) {
                assertEquals("$mm2 in $locale", DetailsFormat.area(mm2, locale), areaText(mm2))
            }
        }
    }
}
