package app.plansnap.core.drawing.plot

import app.plansnap.core.drawing.scene.WorldBounds
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class TitleBlockSpecTest {
    private val bounds = WorldBounds(0.0, 0.0, 1000.0, 1000.0)

    /** Every paper, orientation and scale of DOMAIN section 12. */
    private val plans =
        PaperSize.entries.flatMap { paper ->
            PageOrientation.entries.flatMap { orientation ->
                PlotScale.entries.map { scale -> PlotPlan(paper, orientation, scale, bounds) }
            }
        }

    /** Generous width of the end label (`10 000 mm` at 3 mm) centered on the bar end. */
    private val endLabelHalfWidthMm = 15.0

    @Test
    fun `given every scale when asked for the bar then it represents 2, 5 and 10 m`() {
        assertEquals(2_000L, TitleBlockSpec.nominalLengthMm(PlotScale.ONE_TO_50))
        assertEquals(5_000L, TitleBlockSpec.nominalLengthMm(PlotScale.ONE_TO_100))
        assertEquals(10_000L, TitleBlockSpec.nominalLengthMm(PlotScale.ONE_TO_200))
        assertEquals("1:50", TitleBlockSpec.scaleText(PlotScale.ONE_TO_50))
        assertEquals("1:200", TitleBlockSpec.scaleText(PlotScale.ONE_TO_200))
    }

    @Test
    fun `given every paper and scale when laid out then the scale bar fits into the block`() {
        assertEquals(12, plans.size)
        plans.forEach { plan ->
            val layout = plan.titleBlockLayout
            val frame = layout.frame
            val bar = layout.scaleBar
            val name = "${plan.paper} ${plan.orientation} ${plan.scale}"
            assertEquals(plan.titleBlockMm, frame, name)
            assertEquals(bar.nominalLengthMm.toDouble() / plan.scale.denominator, bar.lengthMm, 1e-12, name)
            assertTrue(bar.leftMm >= frame.leftMm + TitleBlockSpec.PADDING_MM, name)
            val barEnd = bar.leftMm + bar.lengthMm + endLabelHalfWidthMm
            assertTrue(barEnd <= frame.rightMm - TitleBlockSpec.PADDING_MM, name)
            assertTrue(bar.topMm > layout.projectName.baselineMm, name)
            assertTrue(bar.endLabel.baselineMm <= frame.bottomMm - TitleBlockSpec.STROKE_MM, name)
            assertTrue(layout.projectName.baselineMm - layout.projectName.sizeMm >= frame.topMm, name)
            assertTrue(layout.projectNameMaxWidthMm > 0.0, name)
            assertTrue(
                layout.projectName.xMm + layout.projectNameMaxWidthMm + TitleBlockSpec.SCALE_TEXT_RESERVE_MM <=
                    layout.scaleLabel.xMm,
                name,
            )
        }
    }

    @Test
    fun `given every plan when laid out then the segments alternate and cover the bar exactly`() {
        plans.forEach { plan ->
            val bar = plan.titleBlockLayout.scaleBar
            assertEquals(TitleBlockSpec.BAR_SEGMENTS, bar.segments.size)
            var x = bar.leftMm
            bar.segments.forEachIndexed { i, segment ->
                assertEquals(x, segment.leftMm, 1e-12)
                assertEquals(i % 2 == 0, segment.filled)
                assertEquals(bar.lengthMm / TitleBlockSpec.BAR_SEGMENTS, segment.widthMm, 1e-12)
                x += segment.widthMm
            }
            assertEquals(bar.leftMm + bar.lengthMm, x, 1e-9)
            assertEquals(bar.leftMm, bar.startLabel.xMm, 0.0)
            assertEquals(bar.leftMm + bar.lengthMm, bar.endLabel.xMm, 0.0)
            assertEquals(TitleTextAlign.CENTER, bar.endLabel.align)
        }
    }

    @Test
    fun `given A4 landscape at 1 to 50 when laid out then positions match the specification`() {
        val plan = PlotPlan(PaperSize.A4, PageOrientation.LANDSCAPE, PlotScale.ONE_TO_50, bounds)
        val layout = TitleBlockSpec.layout(plan)
        assertEquals(PageRectMm(10, 175, 277, 25), layout.frame)
        assertEquals(TitleTextSlot(15.0, 183.0, 5.0, TitleTextAlign.START), layout.projectName)
        assertEquals(TitleTextSlot(282.0, 183.0, 5.0, TitleTextAlign.END), layout.scaleLabel)
        assertEquals(242.0, layout.projectNameMaxWidthMm, 0.0)
        assertEquals(0.25, layout.strokeMm, 0.0)
        val bar = layout.scaleBar
        assertEquals(listOf(15.0, 187.0, 40.0, 3.0), listOf(bar.leftMm, bar.topMm, bar.lengthMm, bar.heightMm))
        assertEquals(
            listOf(ScaleBarSegment(15.0, 20.0, true), ScaleBarSegment(35.0, 20.0, false)),
            bar.segments,
        )
        assertEquals(TitleTextSlot(15.0, 193.0, 3.0, TitleTextAlign.CENTER), bar.startLabel)
        assertEquals(TitleTextSlot(55.0, 193.0, 3.0, TitleTextAlign.CENTER), bar.endLabel)
    }
}
