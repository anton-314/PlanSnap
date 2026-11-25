package app.plansnap.core.export.svg

import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.model.Room
import kotlinx.coroutines.runBlocking
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.ByteArrayOutputStream
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SvgGoldenTest {
    private val exporter =
        SvgExporter(DefaultSceneBuilder(), DefaultPlotLayout(), ExportFixtures::formatter, ExportFixtures.unconfined)

    private fun export(
        vararg rooms: Room,
        selection: PlotSelection = PlotSelection(),
        locale: Locale = Locale.GERMAN,
    ): String {
        val request =
            ExportFixtures.request(ExportFixtures.project(*rooms), ExportFormat.SVG, locale, selection = selection)
        val bytes = ByteArrayOutputStream()
        runBlocking { exporter.export(request, bytes) }
        return bytes.toString(Charsets.UTF_8)
    }

    @Test
    fun `given a confirmed room with openings when exporting then it matches the golden file at 1 to 50`() {
        Golden.assertMatches("svg/confirmed-1-50.svg", export(ExportFixtures.confirmed))
    }

    @Test
    fun `given a mixed project when exporting then it matches the golden file and omits open rooms`() {
        val text = export(ExportFixtures.confirmed, ExportFixtures.unconfirmed, ExportFixtures.open)
        Golden.assertMatches("svg/mixed-project.svg", text)
    }

    @Test
    fun `given an English locale when exporting at 1 to 200 then it matches the golden file`() {
        val text =
            export(
                ExportFixtures.unconfirmed,
                selection = PlotSelection(scale = PlotScale.ONE_TO_200),
                locale = Locale.ENGLISH,
            )
        Golden.assertMatches("svg/unconfirmed-1-200-en.svg", text)
    }
}
