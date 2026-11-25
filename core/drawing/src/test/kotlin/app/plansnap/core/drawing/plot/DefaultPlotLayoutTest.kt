package app.plansnap.core.drawing.plot

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.ScreenPoint
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.isCloseTo
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.CsvSource

class DefaultPlotLayoutTest {
    private val layout = DefaultPlotLayout()

    /** Formats a result as `ONE_TO_50 A3 PORTRAIT` or as the export problem name. */
    private fun describe(result: DomainResult<PlotPlan>): String =
        when (result) {
            is DomainResult.Success -> with(result.value) { "$scale $paper $orientation" }
            is DomainResult.Failure -> (result.error as DomainError.Export).problem.name
        }

    private fun bounds(
        widthMm: Double,
        heightMm: Double,
    ) = WorldBounds(0.0, 0.0, widthMm, heightMm)

    @ParameterizedTest(name = "{0} x {1} mm -> {2}")
    @CsvSource(
        // DOMAIN section 12 reference cases.
        "13500, 13000, ONE_TO_50 A3 PORTRAIT",
        "9250, 8500, ONE_TO_50 A4 PORTRAIT",
        // Orientation tie rule: both orientations of A4 fit at 1:50.
        "1000, 1000, ONE_TO_50 A4 LANDSCAPE",
        "1000, 1001, ONE_TO_50 A4 LANDSCAPE",
        "1000, 1001.5, ONE_TO_50 A4 PORTRAIT",
        "1001.5, 1000, ONE_TO_50 A4 LANDSCAPE",
        "0, 0, ONE_TO_50 A4 LANDSCAPE",
        // 1:50 A4 portrait 9500 x 12600 and landscape 13850 x 8250, exact and 1 mm too large.
        "9500, 12600, ONE_TO_50 A4 PORTRAIT",
        "9501, 12600, ONE_TO_50 A3 PORTRAIT",
        "9500, 12601, ONE_TO_50 A3 PORTRAIT",
        "13850, 8250, ONE_TO_50 A4 LANDSCAPE",
        "13851, 8250, ONE_TO_50 A3 LANDSCAPE",
        "13850, 8251, ONE_TO_50 A3 LANDSCAPE",
        // 1:50 A3 portrait 13850 x 18750 and landscape 20000 x 12600.
        "13850, 18750, ONE_TO_50 A3 PORTRAIT",
        "13851, 18750, ONE_TO_100 A4 PORTRAIT",
        "13850, 18751, ONE_TO_100 A4 PORTRAIT",
        "20000, 12600, ONE_TO_50 A3 LANDSCAPE",
        "20001, 12600, ONE_TO_100 A4 LANDSCAPE",
        "20000, 12601, ONE_TO_100 A4 LANDSCAPE",
        // 1:100 A4 portrait 19000 x 25200 and landscape 27700 x 16500.
        "19000, 25200, ONE_TO_100 A4 PORTRAIT",
        "19001, 25200, ONE_TO_100 A3 PORTRAIT",
        "27700, 16500, ONE_TO_100 A4 LANDSCAPE",
        "27700, 16501, ONE_TO_100 A3 LANDSCAPE",
        // 1:100 A3 portrait 27700 x 37500 and landscape 40000 x 25200.
        "27700, 37500, ONE_TO_100 A3 PORTRAIT",
        "27700, 37501, ONE_TO_200 A4 PORTRAIT",
        "40000, 25200, ONE_TO_100 A3 LANDSCAPE",
        "40001, 25200, ONE_TO_200 A4 LANDSCAPE",
        // 1:200 A4 portrait 38000 x 50400 and landscape 55400 x 33000.
        "38000, 50400, ONE_TO_200 A4 PORTRAIT",
        "38001, 50400, ONE_TO_200 A3 PORTRAIT",
        "55400, 33000, ONE_TO_200 A4 LANDSCAPE",
        "55401, 33000, ONE_TO_200 A3 LANDSCAPE",
        // 1:200 A3 portrait 55400 x 75000 and landscape 80000 x 50400, the largest pages.
        "55400, 75000, ONE_TO_200 A3 PORTRAIT",
        "55401, 75000, PLAN_TOO_LARGE",
        "55400, 75001, PLAN_TOO_LARGE",
        "80000, 50400, ONE_TO_200 A3 LANDSCAPE",
        "80001, 50400, PLAN_TOO_LARGE",
        "80000, 50401, PLAN_TOO_LARGE",
        "100000, 100000, PLAN_TOO_LARGE",
    )
    fun `given plan size when laid out automatically then first fitting page in DOMAIN order wins`(
        widthMm: Double,
        heightMm: Double,
        expected: String,
    ) {
        assertEquals(expected, describe(layout.layout(bounds(widthMm, heightMm), PlotSelection())))
    }

    @ParameterizedTest(name = "{0} x {1} mm with scale {2} and paper {3} -> {4}")
    @CsvSource(
        "1000, 1000, ONE_TO_200, , ONE_TO_200 A4 LANDSCAPE",
        "1000, 1000, ONE_TO_100, , ONE_TO_100 A4 LANDSCAPE",
        "1000, 1000, , A3, ONE_TO_50 A3 LANDSCAPE",
        "1000, 1000, ONE_TO_200, A3, ONE_TO_200 A3 LANDSCAPE",
        "1000, 1500, ONE_TO_200, A3, ONE_TO_200 A3 PORTRAIT",
        "13500, 13000, ONE_TO_100, A4, ONE_TO_100 A4 LANDSCAPE",
        "13500, 13000, ONE_TO_100, , ONE_TO_100 A4 LANDSCAPE",
        "13500, 13000, ONE_TO_50, A4, PLAN_DOES_NOT_FIT_SELECTION",
        "13500, 13000, ONE_TO_50, , ONE_TO_50 A3 PORTRAIT",
        "13500, 13000, , A4, ONE_TO_100 A4 LANDSCAPE",
        "40000, 25200, , A4, ONE_TO_200 A4 LANDSCAPE",
        "40000, 25200, ONE_TO_100, A3, ONE_TO_100 A3 LANDSCAPE",
        "40001, 25200, ONE_TO_100, , PLAN_DOES_NOT_FIT_SELECTION",
        "20001, 12600, ONE_TO_50, , PLAN_DOES_NOT_FIT_SELECTION",
        "55401, 33000, , A4, PLAN_DOES_NOT_FIT_SELECTION",
        "80000, 50400, ONE_TO_200, A3, ONE_TO_200 A3 LANDSCAPE",
        "80001, 50400, ONE_TO_200, A3, PLAN_TOO_LARGE",
        "80001, 50400, ONE_TO_50, A4, PLAN_TOO_LARGE",
        "80001, 50400, , , PLAN_TOO_LARGE",
    )
    fun `given user selection when laid out then only selected scale and paper are considered`(
        widthMm: Double,
        heightMm: Double,
        scale: PlotScale?,
        paper: PaperSize?,
        expected: String,
    ) {
        assertEquals(expected, describe(layout.layout(bounds(widthMm, heightMm), PlotSelection(scale, paper))))
    }

    @Test
    fun `given translated bounds when laid out then choice depends on extent only and keeps bounds`() {
        val translated = WorldBounds(-100_000.0, 50_000.0, -86_500.0, 63_000.0)
        val result = layout.layout(translated, PlotSelection())
        assertEquals(
            DomainResult.Success(PlotPlan(PaperSize.A3, PageOrientation.PORTRAIT, PlotScale.ONE_TO_50, translated)),
            result,
        )
    }

    @ParameterizedTest(name = "{0} {1}: page {2} mm high, drawing area {3} x {4} mm")
    @CsvSource(
        "A4, PORTRAIT, 297, 190, 252",
        "A4, LANDSCAPE, 210, 277, 165",
        "A3, PORTRAIT, 420, 277, 375",
        "A3, LANDSCAPE, 297, 400, 252",
    )
    fun `given page when areas are derived then drawing area and title block follow DOMAIN section 12`(
        paper: PaperSize,
        orientation: PageOrientation,
        pageHeightMm: Int,
        usableWidthMm: Int,
        usableHeightMm: Int,
    ) {
        val plan = PlotPlan(paper, orientation, PlotScale.ONE_TO_100, bounds(1.0, 1.0))
        assertEquals(PageRectMm(MARGIN, MARGIN, usableWidthMm, usableHeightMm), plan.drawingAreaMm)
        val title = plan.titleBlockMm
        assertEquals(PageRectMm(MARGIN, MARGIN + usableHeightMm, usableWidthMm, TITLE), title)
        assertEquals(plan.drawingAreaMm.bottomMm, title.topMm)
        assertEquals(pageHeightMm - MARGIN, title.bottomMm)
        assertEquals(plan.pageWidthMm - MARGIN, title.rightMm)
    }

    @Test
    fun `given negative page rectangle when created then it is rejected`() {
        assertThrows(IllegalArgumentException::class.java) { PageRectMm(-1, 0, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PageRectMm(0, -1, 1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PageRectMm(0, 0, -1, 1) }
        assertThrows(IllegalArgumentException::class.java) { PageRectMm(0, 0, 1, -1) }
    }

    @Test
    fun `given exactly fitting plan when mapped then corners land on the drawing area corners`() {
        val plan = PlotPlan(PaperSize.A4, PageOrientation.LANDSCAPE, PlotScale.ONE_TO_50, bounds(13_850.0, 8_250.0))
        assertPage(10.0, 10.0, layout.worldToPagePoints(plan, Vec2(0.0, 8_250.0)))
        assertPage(287.0, 175.0, layout.worldToPagePoints(plan, Vec2(13_850.0, 0.0)))
        assertPage(10.0, 175.0, layout.worldToPagePoints(plan, Vec2(0.0, 0.0)))
        assertPage(287.0, 10.0, layout.worldToPagePoints(plan, Vec2(13_850.0, 8_250.0)))
    }

    @Test
    fun `given smaller plan when mapped then it is centred in the drawing area`() {
        val plan =
            PlotPlan(
                PaperSize.A4,
                PageOrientation.PORTRAIT,
                PlotScale.ONE_TO_100,
                WorldBounds(-500.0, 1_000.0, 1_500.0, 3_000.0),
            )
        // Drawing area center: (10 + 95, 10 + 126) = (105, 136) mm.
        assertPage(105.0, 136.0, layout.worldToPagePoints(plan, Vec2(500.0, 2_000.0)))
        assertPage(95.0, 126.0, layout.worldToPagePoints(plan, Vec2(-500.0, 3_000.0)))
        assertPage(115.0, 146.0, layout.worldToPagePoints(plan, Vec2(1_500.0, 1_000.0)))
        // Outside the bounds the mapping stays affine: 1 m to the right is 10 mm at 1:100.
        assertPage(125.0, 136.0, layout.worldToPagePoints(plan, Vec2(2_500.0, 2_000.0)))
    }

    @ParameterizedTest(name = "1 m at {0} -> {1} pt")
    @CsvSource(
        "ONE_TO_50, 56.69291338582677",
        "ONE_TO_100, 28.346456692913385",
        "ONE_TO_200, 14.173228346456693",
    )
    fun `given scale when one meter is mapped then page length follows pt formula`(
        scale: PlotScale,
        expectedPt: Double,
    ) {
        val plan = PlotPlan(PaperSize.A3, PageOrientation.LANDSCAPE, scale, bounds(1_000.0, 1_000.0))
        val start = layout.worldToPagePoints(plan, Vec2(0.0, 0.0))
        val right = layout.worldToPagePoints(plan, Vec2(1_000.0, 0.0))
        val up = layout.worldToPagePoints(plan, Vec2(0.0, 1_000.0))
        assertTrue((right.x - start.x).isCloseTo(expectedPt, PT_TOLERANCE), "x: ${right.x - start.x}")
        assertTrue((start.y - up.y).isCloseTo(expectedPt, PT_TOLERANCE), "y down: ${start.y - up.y}")
    }

    private fun assertPage(
        expectedXMm: Double,
        expectedYMm: Double,
        actual: ScreenPoint,
    ) {
        val close =
            actual.x.isCloseTo(expectedXMm * PT_PER_MM, PT_TOLERANCE) &&
                actual.y.isCloseTo(expectedYMm * PT_PER_MM, PT_TOLERANCE)
        assertTrue(
            close,
            "expected ($expectedXMm, $expectedYMm) mm, was ${actual.x / PT_PER_MM}, ${actual.y / PT_PER_MM}",
        )
    }

    private companion object {
        const val MARGIN = 10
        const val TITLE = 25
        const val PT_PER_MM = 72.0 / 25.4
        const val PT_TOLERANCE = 1e-9
    }
}
