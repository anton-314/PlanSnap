package app.plansnap.core.drawing

import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotPageMetrics
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.drawing.scene.DrawingScene
import app.plansnap.core.drawing.scene.LabelText
import app.plansnap.core.drawing.scene.RoomScene
import app.plansnap.core.drawing.scene.SceneElement
import app.plansnap.core.drawing.scene.SceneOptions
import app.plansnap.core.drawing.scene.SceneStyle
import app.plansnap.core.drawing.scene.SceneTarget
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.ScreenPoint
import app.plansnap.core.drawing.viewport.Viewport
import app.plansnap.core.geometry.primitives.Polygon2
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.ShapeStatus
import kotlinx.collections.immutable.persistentListOf
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DrawingContractTest {
    private val roomId = RoomId("r1")
    private val bounds = WorldBounds(0.0, 0.0, 10.0, 20.0)
    private val line =
        SceneElement.Polyline(
            persistentListOf(Vec2.ZERO, Vec2(10.0, 20.0)),
            closed = false,
            style = SceneStyle.WALL_LINE,
            target = SceneTarget.RoomBody(roomId),
        )

    private fun assertRejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun `given bounds when measured and combined then results are exact`() {
        assertEquals(10.0, bounds.width)
        assertEquals(20.0, bounds.height)
        assertEquals(WorldBounds(-5.0, 0.0, 10.0, 30.0), bounds.union(WorldBounds(-5.0, 5.0, 1.0, 30.0)))
    }

    @Test
    fun `given bounds when tested for intersection then touching edges count`() {
        assertTrue(bounds.intersects(WorldBounds(10.0, 20.0, 11.0, 21.0)))
        assertFalse(bounds.intersects(WorldBounds(10.1, 0.0, 11.0, 1.0)))
        assertFalse(bounds.intersects(WorldBounds(-2.0, 0.0, -1.0, 1.0)))
        assertFalse(bounds.intersects(WorldBounds(0.0, 20.1, 1.0, 21.0)))
        assertFalse(bounds.intersects(WorldBounds(0.0, -2.0, 1.0, -1.0)))
    }

    @Test
    fun `given invalid bounds when created then they are rejected`() {
        assertRejected { WorldBounds(Double.NaN, 0.0, 1.0, 1.0) }
        assertRejected { WorldBounds(0.0, Double.NEGATIVE_INFINITY, 1.0, 1.0) }
        assertRejected { WorldBounds(0.0, 0.0, Double.POSITIVE_INFINITY, 1.0) }
        assertRejected { WorldBounds(0.0, 0.0, 1.0, Double.NaN) }
        assertRejected { WorldBounds(2.0, 0.0, 1.0, 1.0) }
        assertRejected { WorldBounds(0.0, 2.0, 1.0, 1.0) }
    }

    @Test
    fun `given labels when values are invalid then they are rejected`() {
        assertEquals(1.0, LabelText.Dimension(1.0).mm)
        assertEquals(1.0, LabelText.Area(1.0).mm2)
        assertEquals(270.0, LabelText.InteriorAngle(270.0).degrees)
        assertEquals("Bath", LabelText.Literal("Bath").text)
        assertEquals(RoomTypeKey("bath"), LabelText.RoomType(RoomTypeKey("bath")).key)
        assertRejected { LabelText.Dimension(-1.0) }
        assertRejected { LabelText.Dimension(Double.NaN) }
        assertRejected { LabelText.Area(-1.0) }
        assertRejected { LabelText.Area(Double.POSITIVE_INFINITY) }
        assertRejected { LabelText.InteriorAngle(Double.NaN) }
    }

    @Test
    fun `given scene elements when values are invalid then they are rejected`() {
        val polygon = Polygon2(listOf(Vec2.ZERO, Vec2(1.0, 0.0), Vec2(0.0, 1.0)))
        assertEquals(polygon, SceneElement.Fill(polygon, SceneStyle.WALL_BAND, null).polygon)
        assertRejected { SceneElement.Polyline(persistentListOf(Vec2.ZERO), false, SceneStyle.DIMENSION, null) }
        assertEquals(1.0, arc(radius = 1.0).radiusMm)
        assertRejected { arc(radius = 0.0) }
        assertRejected { arc(radius = Double.NaN) }
        assertRejected { arc(radius = 1.0, start = Double.NaN) }
        assertRejected { arc(radius = 1.0, sweep = Double.POSITIVE_INFINITY) }
        assertEquals(5.0, label(height = 5.0).heightMm)
        assertRejected { label(height = 0.0) }
        assertRejected { label(height = Double.NaN) }
        assertRejected { label(height = 1.0, rotation = Double.NaN) }
    }

    @Test
    fun `given corner target when index is negative then it is rejected`() {
        assertEquals(0, SceneTarget.Corner(roomId, 0).index)
        assertRejected { SceneTarget.Corner(roomId, -1) }
    }

    @Test
    fun `given room scene when bounds and elements disagree then it is rejected`() {
        assertEquals(1, RoomScene(roomId, ShapeStatus.CONFIRMED, bounds, persistentListOf(line)).elements.size)
        assertNull(RoomScene(roomId, ShapeStatus.OPEN, null, persistentListOf()).bounds)
        assertRejected { RoomScene(roomId, ShapeStatus.OPEN, bounds, persistentListOf()) }
        assertRejected { RoomScene(roomId, ShapeStatus.OPEN, null, persistentListOf(line)) }
    }

    @Test
    fun `given drawing scene when rooms and bounds disagree then it is rejected`() {
        val filled = RoomScene(roomId, ShapeStatus.CONFIRMED, bounds, persistentListOf(line))
        val empty = RoomScene(RoomId("r2"), ShapeStatus.OPEN, null, persistentListOf())
        assertEquals(bounds, DrawingScene(persistentListOf(filled, empty), bounds).bounds)
        assertNull(DrawingScene(persistentListOf(empty), null).bounds)
        assertNull(DrawingScene(persistentListOf(), null).bounds)
        assertRejected { DrawingScene(persistentListOf(filled, filled), bounds) }
        assertRejected { DrawingScene(persistentListOf(filled), null) }
        assertRejected { DrawingScene(persistentListOf(empty), bounds) }
    }

    @Test
    fun `given scene options when defaulted then the full editor scene is requested`() {
        assertFalse(SceneOptions().exportableOnly)
        assertFalse(SceneOptions().includeNotes)
    }

    @Test
    fun `given viewport values when invalid then they are rejected`() {
        assertEquals(1.5, ScreenPoint(1.5, 2.0).x)
        assertRejected { ScreenPoint(Double.NaN, 0.0) }
        assertRejected { ScreenPoint(0.0, Double.POSITIVE_INFINITY) }
        assertEquals(0, Viewport(Vec2.ZERO, 1.0, 0, 0).widthPx)
        assertRejected { Viewport(Vec2.ZERO, 0.0, 1, 1) }
        assertRejected { Viewport(Vec2.ZERO, Double.NaN, 1, 1) }
        assertRejected { Viewport(Vec2.ZERO, 1.0, -1, 1) }
        assertRejected { Viewport(Vec2.ZERO, 1.0, 1, -1) }
    }

    @Test
    fun `given plot enums when read then they follow the order of domain section 12`() {
        assertEquals(listOf(50, 100, 200), PlotScale.entries.map { it.denominator })
        assertEquals(listOf(PaperSize.A4, PaperSize.A3), PaperSize.entries.toList())
        assertEquals(210 to 297, PaperSize.A4.shortSideMm to PaperSize.A4.longSideMm)
        assertEquals(297 to 420, PaperSize.A3.shortSideMm to PaperSize.A3.longSideMm)
        assertEquals(10 to 25, PlotPageMetrics.MARGIN_MM to PlotPageMetrics.TITLE_BLOCK_MM)
    }

    @Test
    fun `given plot plan when orientation changes then page sides swap`() {
        val portrait = PlotPlan(PaperSize.A4, PageOrientation.PORTRAIT, PlotScale.ONE_TO_100, bounds)
        val landscape = portrait.copy(orientation = PageOrientation.LANDSCAPE)
        assertEquals(210 to 297, portrait.pageWidthMm to portrait.pageHeightMm)
        assertEquals(297 to 210, landscape.pageWidthMm to landscape.pageHeightMm)
        assertNull(PlotSelection().scale)
        assertNull(PlotSelection().paper)
    }

    @Test
    fun `given page orientations when usable area is read then margins and title block are removed`() {
        val usable =
            PaperSize.entries.flatMap { paper ->
                PageOrientation.entries.map { orientation ->
                    val plan = PlotPlan(paper, orientation, PlotScale.ONE_TO_50, bounds)
                    plan.usableWidthMm to plan.usableHeightMm
                }
            }
        assertEquals(listOf(190 to 252, 277 to 165, 277 to 375, 400 to 252), usable)
    }

    @Test
    fun `given reference plans of domain section 12 when the order is applied then the specified page wins`() {
        assertEquals(
            Triple(PlotScale.ONE_TO_50, PaperSize.A3, PageOrientation.PORTRAIT),
            specifiedChoice(13_500.0, 13_000.0),
        )
        assertEquals(
            Triple(PlotScale.ONE_TO_50, PaperSize.A4, PageOrientation.PORTRAIT),
            specifiedChoice(9_250.0, 8_500.0),
        )
    }

    @Test
    fun `given plans fitting both orientations when the order is applied then the plan shape decides`() {
        val a4 = Triple(PlotScale.ONE_TO_50, PaperSize.A4, PageOrientation.LANDSCAPE)
        assertEquals(a4, specifiedChoice(8_000.0, 5_000.0))
        assertEquals(a4.copy(third = PageOrientation.PORTRAIT), specifiedChoice(5_000.0, 8_000.0))
        assertEquals(a4, specifiedChoice(5_000.0, 5_000.0))
        assertEquals(null, specifiedChoice(81_000.0, 10_000.0))
    }

    /** Executable form of the selection order in DOMAIN section 12, on the page metrics of the contract. */
    private fun specifiedChoice(
        widthMm: Double,
        heightMm: Double,
    ): Triple<PlotScale, PaperSize, PageOrientation>? {
        val preferred = if (heightMm - widthMm > 1.0) PageOrientation.PORTRAIT else PageOrientation.LANDSCAPE
        return PlotScale.entries
            .asSequence()
            .flatMap { scale -> PaperSize.entries.asSequence().map { scale to it } }
            .mapNotNull { (scale, paper) ->
                val fitting =
                    PageOrientation.entries
                        .map { PlotPlan(paper, it, scale, WorldBounds(0.0, 0.0, widthMm, heightMm)) }
                        .filter {
                            widthMm <= it.usableWidthMm * scale.denominator &&
                                heightMm <= it.usableHeightMm * scale.denominator
                        }
                (fitting.firstOrNull { it.orientation == preferred } ?: fitting.firstOrNull())
                    ?.let { Triple(scale, paper, it.orientation) }
            }.firstOrNull()
    }

    private fun arc(
        radius: Double,
        start: Double = 0.0,
        sweep: Double = 1.0,
    ) = SceneElement.Arc(Vec2.ZERO, radius, start, sweep, SceneStyle.OPENING, null)

    private fun label(
        height: Double,
        rotation: Double = 0.0,
    ) = SceneElement.Label(Vec2.ZERO, LabelText.ShapeNotConfirmed, rotation, height, SceneStyle.WARNING, null)
}
