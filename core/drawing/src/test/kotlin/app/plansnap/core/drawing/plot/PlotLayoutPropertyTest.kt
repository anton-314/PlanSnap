package app.plansnap.core.drawing.plot

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.isCloseTo
import io.kotest.common.ExperimentalKotest
import io.kotest.property.Arb
import io.kotest.property.PropTestConfig
import io.kotest.property.RandomSource
import io.kotest.property.arbitrary.arbitrary
import io.kotest.property.checkAll
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max

/**
 * Checks [DefaultPlotLayout] against an independent oracle that enumerates every candidate of DOMAIN section 12 in its
 * order. Plan sizes are biased towards the fit boundaries `usable · M` and one millimeter around them.
 */
class PlotLayoutPropertyTest {
    private val layout = DefaultPlotLayout()

    private data class Candidate(
        val scale: PlotScale,
        val paper: PaperSize,
        val orientation: PageOrientation,
    )

    /** All candidates in DOMAIN order: scale, then paper, then orientation. */
    private val candidates =
        PlotScale.entries.flatMap { scale ->
            PaperSize.entries.flatMap { paper -> PageOrientation.entries.map { Candidate(scale, paper, it) } }
        }

    private fun Candidate.fits(bounds: WorldBounds): Boolean {
        val plan = PlotPlan(paper, orientation, scale, bounds)
        return bounds.width <= plan.usableWidthMm.toDouble() * scale.denominator &&
            bounds.height <= plan.usableHeightMm.toDouble() * scale.denominator
    }

    private fun Candidate.matches(selection: PlotSelection): Boolean =
        (selection.scale == null || selection.scale == scale) && (selection.paper == null || selection.paper == paper)

    @Test
    fun `automatic layout picks the largest fitting scale and contains the plan in the drawing area`() =
        runBlocking<Unit> {
            var laidOut = 0
            checkAll(ITERATIONS, config(SEED_AUTOMATIC), boundsArb) { bounds ->
                val result = layout.layout(bounds, PlotSelection())
                val fitting = candidates.filter { it.fits(bounds) }
                when (result) {
                    is DomainResult.Success -> {
                        val plan = result.value
                        val chosen = Candidate(plan.scale, plan.paper, plan.orientation)
                        assertEquals(bounds, plan.worldBounds)
                        assertTrue(chosen.fits(bounds), "chosen $chosen does not fit $bounds")
                        val larger = fitting.filter { it.scale.denominator < plan.scale.denominator }
                        assertTrue(larger.isEmpty(), "larger scale $larger fits $bounds, chose $chosen")
                        val earlierPaper = fitting.filter { it.scale == plan.scale && it.paper < plan.paper }
                        assertTrue(earlierPaper.isEmpty(), "earlier paper $earlierPaper fits $bounds")
                        assertOrientationRule(bounds, chosen, fitting)
                        assertContained(plan)
                        laidOut++
                    }

                    is DomainResult.Failure -> {
                        assertEquals(DomainError.Export(ExportProblem.PLAN_TOO_LARGE), result.error)
                        assertTrue(fitting.isEmpty(), "rejected $bounds although $fitting fit")
                    }
                }
            }
            assertTrue(laidOut > 0 && laidOut < ITERATIONS, "both outcomes must occur, laid out $laidOut")
        }

    @Test
    fun `selection restricts the candidates and distinguishes the two rejections`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_SELECTION), boundsArb, selectionArb) { bounds, selection ->
                val fitting = candidates.filter { it.fits(bounds) }
                val selected = fitting.filter { it.matches(selection) }
                when (val result = layout.layout(bounds, selection)) {
                    is DomainResult.Success -> {
                        val plan = result.value
                        val chosen = Candidate(plan.scale, plan.paper, plan.orientation)
                        assertTrue(chosen in selected, "$chosen is not a fitting candidate of $selection")
                        val first = selected.first()
                        assertEquals(first.scale to first.paper, plan.scale to plan.paper)
                        assertOrientationRule(bounds, chosen, selected)
                        assertContained(plan)
                    }

                    is DomainResult.Failure -> {
                        val expected =
                            if (fitting.isEmpty()) {
                                ExportProblem.PLAN_TOO_LARGE
                            } else {
                                ExportProblem.PLAN_DOES_NOT_FIT_SELECTION
                            }
                        assertTrue(selected.isEmpty(), "rejected $bounds for $selection although $selected fit")
                        assertEquals(DomainError.Export(expected), result.error)
                    }
                }
            }
        }

    @Test
    fun `page mapping scales distances by the plot scale and flips y`() =
        runBlocking<Unit> {
            checkAll(ITERATIONS, config(SEED_MAPPING), boundsArb, boundsArb) { bounds, segment ->
                val plan = PlotPlan(PaperSize.A3, PageOrientation.LANDSCAPE, PlotScale.ONE_TO_100, bounds)
                val a = Vec2(segment.minX, segment.minY)
                val b = Vec2(segment.maxX, segment.maxY)
                val pa = layout.worldToPagePoints(plan, a)
                val pb = layout.worldToPagePoints(plan, b)
                val factor = PT_PER_MM / plan.scale.denominator
                val tolerance = RELATIVE_TOLERANCE * max(1.0, magnitude(bounds, segment)) * factor
                assertTrue((pb.x - pa.x).isCloseTo((b.x - a.x) * factor, tolerance), "x of $a to $b")
                assertTrue((pa.y - pb.y).isCloseTo((b.y - a.y) * factor, tolerance), "y of $a to $b")
                val length = hypot(pb.x - pa.x, pb.y - pa.y)
                assertTrue(length.isCloseTo(hypot(b.x - a.x, b.y - a.y) * factor, 2.0 * tolerance), "length")
            }
        }

    /** If both orientations of the chosen paper and scale fit, portrait wins only for a clearly taller plan. */
    private fun assertOrientationRule(
        bounds: WorldBounds,
        chosen: Candidate,
        fitting: List<Candidate>,
    ) {
        val both =
            fitting.count { it.scale == chosen.scale && it.paper == chosen.paper } == PageOrientation.entries.size
        if (both) {
            val tallerByMoreThanTolerance = bounds.height - bounds.width > GeometryTolerances.EXACT_CLOSURE_MM
            val expected = if (tallerByMoreThanTolerance) PageOrientation.PORTRAIT else PageOrientation.LANDSCAPE
            assertEquals(expected, chosen.orientation, "orientation for $bounds")
        }
    }

    /** All four bound corners map into the drawing area of the page, which never overlaps the title block. */
    private fun assertContained(plan: PlotPlan) {
        val area = plan.drawingAreaMm
        val b = plan.worldBounds
        val slackPt = RELATIVE_TOLERANCE * max(1.0, magnitude(b, b)) * PT_PER_MM
        for (corner in listOf(Vec2(b.minX, b.minY), Vec2(b.minX, b.maxY), Vec2(b.maxX, b.minY), Vec2(b.maxX, b.maxY))) {
            val page = layout.worldToPagePoints(plan, corner)
            val inside =
                page.x >= area.leftMm * PT_PER_MM - slackPt &&
                    page.x <= area.rightMm * PT_PER_MM + slackPt &&
                    page.y >= area.topMm * PT_PER_MM - slackPt &&
                    page.y <= area.bottomMm * PT_PER_MM + slackPt
            assertTrue(inside, "corner $corner at $page outside $area for $plan")
        }
        assertTrue(area.bottomMm <= plan.titleBlockMm.topMm)
    }

    private fun magnitude(
        first: WorldBounds,
        second: WorldBounds,
    ): Double =
        listOf(first.minX, first.minY, first.maxX, first.maxY, second.minX, second.minY, second.maxX, second.maxY)
            .maxOf { abs(it) }

    private val boundsArb: Arb<WorldBounds> =
        arbitrary { rs ->
            val width = rs.extent(horizontal = true)
            val height = rs.extent(horizontal = false)
            val minX = rs.offset()
            val minY = rs.offset()
            WorldBounds(minX, minY, minX + width, minY + height)
        }

    private val selectionArb: Arb<PlotSelection> =
        arbitrary { rs ->
            val scale = rs.random.nextInt(PlotScale.entries.size + 1)
            val paper = rs.random.nextInt(PaperSize.entries.size + 1)
            PlotSelection(PlotScale.entries.getOrNull(scale), PaperSize.entries.getOrNull(paper))
        }

    /**
     * Half the extents are a fit boundary `usable · M` of a random candidate plus `-1`, `0` or `+1` mm, the others are
     * uniform up to beyond the largest page.
     */
    private fun RandomSource.extent(horizontal: Boolean): Double {
        if (random.nextBoolean()) return random.nextDouble() * MAX_EXTENT_MM
        val candidate = candidates[random.nextInt(candidates.size)]
        val plan = PlotPlan(candidate.paper, candidate.orientation, candidate.scale, WorldBounds(0.0, 0.0, 0.0, 0.0))
        val usable = if (horizontal) plan.usableWidthMm else plan.usableHeightMm
        val delta = random.nextInt(-1, 2)
        return (usable * candidate.scale.denominator + delta).toDouble()
    }

    private fun RandomSource.offset(): Double = (random.nextDouble() * 2.0 - 1.0) * MAX_OFFSET_MM

    private companion object {
        const val ITERATIONS = 1_000
        const val MAX_EXTENT_MM = 90_000.0
        const val MAX_OFFSET_MM = 1e5
        const val PT_PER_MM = 72.0 / 25.4
        const val RELATIVE_TOLERANCE = 1e-12
        const val SEED_AUTOMATIC = 9_001L
        const val SEED_SELECTION = 9_002L
        const val SEED_MAPPING = 9_003L

        /** Fixed seed so every run checks the same cases (TESTING section 2). */
        @OptIn(ExperimentalKotest::class)
        fun config(seed: Long) = PropTestConfig(seed = seed)
    }
}
