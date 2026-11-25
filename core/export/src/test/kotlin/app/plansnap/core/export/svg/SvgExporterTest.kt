package app.plansnap.core.export.svg

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportSummary
import app.plansnap.core.model.RoomId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.w3c.dom.Element
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.OutputStream
import java.util.Locale
import javax.xml.XMLConstants
import javax.xml.parsers.DocumentBuilderFactory

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class SvgExporterTest {
    private val exporter =
        SvgExporter(DefaultSceneBuilder(), DefaultPlotLayout(), ExportFixtures::formatter, ExportFixtures.unconfined)

    private fun run(
        request: app.plansnap.core.export.ExportRequest,
        output: OutputStream = ByteArrayOutputStream(),
    ) = runBlocking { exporter.export(request, output) }

    private fun svg(request: app.plansnap.core.export.ExportRequest): Pair<String, ExportSummary> {
        val bytes = ByteArrayOutputStream()
        val result = run(request, bytes) as DomainResult.Success
        return bytes.toString(Charsets.UTF_8) to result.value
    }

    private fun parse(text: String): Element {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true)
        return factory.newDocumentBuilder().parse(text.byteInputStream(Charsets.UTF_8)).documentElement
    }

    private fun children(element: Element): List<Element> =
        (0 until element.childNodes.length).map { element.childNodes.item(it) }.filterIsInstance<Element>()

    private fun byId(
        root: Element,
        id: String,
    ): Element? =
        root.getElementsByTagNameNS(SVG_NS, "*").let { all ->
            (0 until all.length).map { all.item(it) as Element }.firstOrNull { it.getAttribute("id") == id }
        }

    private fun request(
        vararg rooms: app.plansnap.core.model.Room,
        selection: PlotSelection = PlotSelection(),
        locale: Locale = Locale.GERMAN,
        unit: LengthUnit = LengthUnit.CENTIMETER,
    ) = ExportFixtures.request(ExportFixtures.project(*rooms), ExportFormat.SVG, locale, unit, selection)

    @Test
    fun `given a project when exporting then the document is well-formed SVG with the page in millimeters`() {
        val (text, summary) = svg(request(ExportFixtures.confirmed))
        val root = parse(text)
        val plan = summary.plan!!
        assertEquals(SVG_NS, root.namespaceURI)
        assertEquals("svg", root.localName)
        assertEquals("${plan.pageWidthMm}mm", root.getAttribute("width"))
        assertEquals("${plan.pageHeightMm}mm", root.getAttribute("height"))
        assertEquals("0 0 ${plan.pageWidthMm} ${plan.pageHeightMm}", root.getAttribute("viewBox"))
        assertEquals("1:${plan.scale.denominator}", root.getAttribute("data-scale"))
        assertEquals(ExportFormat.SVG, summary.format)
    }

    @Test
    fun `given rooms when exporting then every scene element of every exportable room is present`() {
        val request = request(ExportFixtures.confirmed, ExportFixtures.unconfirmed)
        val (text, _) = svg(request)
        val root = parse(text)
        val builder = DefaultSceneBuilder()
        request.project.rooms.forEachIndexed { index, room ->
            val scene = builder.buildRoom(room, request.evaluations.getValue(room.id), SceneOptions(true, true))
            val group = byId(root, "room-$index")
            assertNotNull(group)
            assertEquals(room.id.value, group!!.getAttribute("data-room-id"))
            assertEquals(scene.elements.size, children(group).size)
            assertEquals(
                scene.elements.map { SvgStyles.className(it.style) },
                children(group).map { it.getAttribute("class") },
            )
        }
    }

    @Test
    fun `given user text with markup when exporting then it is escaped and parses back unchanged`() {
        val (text, _) = svg(request(ExportFixtures.confirmed))
        val root = parse(text)
        assertTrue(text.contains("Küche &lt;&amp;&gt; \"A\""))
        val texts = root.getElementsByTagNameNS(SVG_NS, "text")
        val contents = (0 until texts.length).map { texts.item(it).textContent }
        assertTrue(contents.contains("Küche <&> \"A\""))
        assertTrue(contents.contains("Notiz & mehr"))
        assertEquals("Wohnung <1>", root.getElementsByTagNameNS(SVG_NS, "title").item(0).textContent)
    }

    @Test
    fun `given a title block when exporting then it carries project name scale label and scale bar`() {
        val (text, summary) = svg(request(ExportFixtures.confirmed))
        val root = parse(text)
        val plan = summary.plan!!
        assertEquals("Wohnung <1>", byId(root, "project-name")!!.textContent)
        assertEquals("1:${plan.scale.denominator}", byId(root, "scale-label")!!.textContent)
        assertNotNull(byId(root, "scale-bar"))
        assertEquals("0", byId(root, "scale-bar-start")!!.textContent)
        assertEquals("200,0 cm", byId(root, "scale-bar-end")!!.textContent)
        val frame = byId(root, "title-frame")!!
        assertEquals((plan.pageHeightMm - 35).toString(), frame.getAttribute("y"))
    }

    @Test
    fun `given the unit and locale when exporting then labels use them`() {
        val (english, _) = svg(request(ExportFixtures.unconfirmed, locale = Locale.ENGLISH, unit = LengthUnit.METER))
        val (german, _) = svg(request(ExportFixtures.unconfirmed, locale = Locale.GERMAN, unit = LengthUnit.CENTIMETER))
        assertTrue(Regex(">4\\.\\d+ m<").containsMatchIn(english))
        assertTrue(german.contains(">400,1 cm<"))
        assertTrue(german.contains("Form nicht bestätigt"))
        assertFalse(english.contains("Form nicht bestätigt"))
    }

    @Test
    fun `given an unconfirmed room when exporting then it is marked by text and dashes`() {
        val (text, _) = svg(request(ExportFixtures.unconfirmed))
        assertTrue(text.contains("Form nicht bestätigt"))
        assertTrue(text.contains("stroke-dasharray"))
        assertTrue(text.contains("class=\"room-body-unconfirmed\""))
    }

    @Test
    fun `given open and conflict rooms when exporting then they are omitted and reported`() {
        val (text, summary) = svg(request(ExportFixtures.confirmed, ExportFixtures.open, ExportFixtures.conflict))
        val root = parse(text)
        assertEquals(setOf(RoomId("open"), RoomId("conflict")), summary.omittedRooms)
        assertEquals("open conflict", root.getAttribute("data-omitted-rooms"))
        assertNull(byId(root, "room-1"))
        assertEquals("confirmed", byId(root, "room-0")!!.getAttribute("data-room-id"))
    }

    @Test
    fun `given only open and conflict rooms when exporting then nothing is written`() {
        val bytes = ByteArrayOutputStream()
        val result = run(request(ExportFixtures.open, ExportFixtures.conflict), bytes)
        assertEquals(DomainResult.Failure(DomainError.Export(ExportProblem.NOTHING_TO_EXPORT)), result)
        assertEquals(0, bytes.size())
    }

    @Test
    fun `given a project without rooms when exporting then there is nothing to export`() {
        val result = run(request())
        assertEquals(DomainResult.Failure(DomainError.Export(ExportProblem.NOTHING_TO_EXPORT)), result)
    }

    @Test
    fun `given a plan that fits no page when exporting then the layout error is passed on`() {
        val result = run(request(ExportFixtures.huge))
        assertEquals(DomainResult.Failure(DomainError.Export(ExportProblem.PLAN_TOO_LARGE)), result)
    }

    @Test
    fun `given a selection that does not fit when exporting then the selection error is passed on`() {
        val result = run(request(ExportFixtures.wide, selection = PlotSelection(PlotScale.ONE_TO_50, PaperSize.A4)))
        assertEquals(
            DomainResult.Failure(DomainError.Export(ExportProblem.PLAN_DOES_NOT_FIT_SELECTION)),
            result,
        )
    }

    @Test
    fun `given a failing stream when exporting then the result is an export error`() {
        val failing =
            object : OutputStream() {
                override fun write(b: Int) = throw IOException("disk full")

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = throw IOException("disk full")
            }
        val result = run(request(ExportFixtures.confirmed), failing)
        assertEquals(DomainResult.Failure(DomainError.Export(ExportProblem.WRITE_FAILED)), result)
    }

    @Test
    fun `given a cancellation while writing when exporting then it propagates`() {
        val cancelling =
            object : OutputStream() {
                override fun write(b: Int) = throw CancellationException("cancelled")

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = throw CancellationException("cancelled")
            }
        assertThrows(CancellationException::class.java) { run(request(ExportFixtures.confirmed), cancelling) }
    }

    @Test
    fun `given a stream when exporting then it stays open`() {
        var closed = false
        val stream =
            object : ByteArrayOutputStream() {
                override fun close() {
                    closed = true
                    super.close()
                }
            }
        run(request(ExportFixtures.confirmed), stream)
        assertFalse(closed)
        assertTrue(stream.size() > 0)
    }

    @Test
    fun `given another format when exporting then it is a programming error`() {
        val json = ExportFixtures.request(ExportFixtures.project(ExportFixtures.confirmed), ExportFormat.JSON)
        assertThrows(IllegalArgumentException::class.java) { run(json) }
    }

    @Test
    fun `given the same input when exporting twice then the bytes are equal`() {
        val first = ByteArrayOutputStream()
        val second = ByteArrayOutputStream()
        run(request(ExportFixtures.confirmed, ExportFixtures.unconfirmed), first)
        run(request(ExportFixtures.confirmed, ExportFixtures.unconfirmed), second)
        assertTrue(first.toByteArray().contentEquals(second.toByteArray()))
    }

    @Test
    fun `given a default locale with a decimal comma when exporting then numbers still use a point`() {
        val saved = Locale.getDefault()
        try {
            Locale.setDefault(Locale.GERMANY)
            val (text, _) = svg(request(ExportFixtures.confirmed))
            val reference = text
            Locale.setDefault(Locale.forLanguageTag("ar-EG"))
            val (arabic, _) = svg(request(ExportFixtures.confirmed))
            assertEquals(reference, arabic)
            val root = parse(text)
            val paths = root.getElementsByTagNameNS(SVG_NS, "path")
            for (i in 0 until paths.length) {
                val d = (paths.item(i) as Element).getAttribute("d")
                assertTrue(d, d.matches(Regex("[MLAZ0-9 .\\-]+")))
            }
        } finally {
            Locale.setDefault(saved)
        }
    }

    @Test
    fun `given label blocks when exporting at every scale then their lines do not overlap`() {
        for (scale in PlotScale.entries) {
            val request = request(ExportFixtures.confirmed, selection = PlotSelection(scale = scale))
            val (text, summary) = svg(request)
            assertEquals(scale, summary.plan!!.scale)
            val group = byId(parse(text), "room-0")!!
            val scene =
                DefaultSceneBuilder().buildRoom(
                    ExportFixtures.confirmed,
                    request.evaluations.getValue(ExportFixtures.confirmed.id),
                    SceneOptions(true, true),
                )
            val lines =
                scene.elements
                    .zip(children(group))
                    .filter { (e, _) -> e is SceneElement.Label && e.blockAnchor != null }
            assertTrue("block expected", lines.size >= 3)
            val placed = lines.map { (_, el) -> position(el) to fontSize(el) }.sortedBy { it.first }
            placed.zipWithNext().forEach { (a, b) ->
                assertTrue("lines overlap at 1:${scale.denominator}", b.first - a.first >= maxOf(a.second, b.second))
            }
        }
    }

    private fun position(text: Element): Double =
        Regex("translate\\(([-0-9.]+) ([-0-9.]+)\\)").find(text.getAttribute("transform"))!!.groupValues[2].toDouble()

    private fun fontSize(text: Element): Double = text.getAttribute("font-size").toDouble()

    private companion object {
        const val SVG_NS = "http://www.w3.org/2000/svg"
    }
}
