package app.plansnap.core.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RenderTextRulesTest {
    @Test
    fun `given the nominal height when sizing editor text then it is the 12 sp reference`() {
        assertEquals(36f, RenderTextRules.editorTextSizePx(150.0, referencePx = 36f), 1e-4f)
    }

    @Test
    fun `given other heights when sizing editor text then they scale in proportion to the nominal height`() {
        assertEquals(48f, RenderTextRules.editorTextSizePx(200.0, referencePx = 36f), 1e-4f)
        assertEquals(28.8f, RenderTextRules.editorTextSizePx(120.0, referencePx = 36f), 1e-4f)
    }

    @Test
    fun `given the export scales when computing the factor then it is 1, 1,2 and 2,4`() {
        assertEquals(1.0, RenderTextRules.exportFactor(50), 1e-12)
        assertEquals(1.2, RenderTextRules.exportFactor(100), 1e-12)
        assertEquals(2.4, RenderTextRules.exportFactor(200), 1e-12)
    }

    @Test
    fun `given a dimension label when sizing export text then it is at least 1,8 mm on paper at every scale`() {
        val pointsPerMm = 72.0 / 25.4
        for (denominator in listOf(50, 100, 200)) {
            val sizeMm = RenderTextRules.exportTextSizePt(150.0, denominator) / pointsPerMm
            assertTrue("1:$denominator gives $sizeMm mm", sizeMm >= 1.8 - 1e-4)
        }
        assertEquals(3.0, RenderTextRules.exportTextSizePt(150.0, 50) / (72.0 / 25.4), 1e-4)
        assertEquals(1.8, RenderTextRules.exportTextSizePt(150.0, 100) / (72.0 / 25.4), 1e-4)
        assertEquals(1.8, RenderTextRules.exportTextSizePt(150.0, 200) / (72.0 / 25.4), 1e-4)
    }

    @Test
    fun `given a text wider than its element when checking visibility then it is hidden`() {
        assertTrue(RenderTextRules.isHidden(textWidthPx = 80f, referenceLengthPx = 79.9f))
    }

    @Test
    fun `given a text that fits or exactly matches its element when checking visibility then it is shown`() {
        assertFalse(RenderTextRules.isHidden(textWidthPx = 80f, referenceLengthPx = 80f))
        assertFalse(RenderTextRules.isHidden(textWidthPx = 80f, referenceLengthPx = 200f))
    }

    @Test
    fun `given export text when computing the block spread then it equals the export factor at every scale`() {
        val pointsPerMm = 72.0 / 25.4
        for (denominator in listOf(50, 100, 200)) {
            val size = RenderTextRules.exportTextSizePt(200.0, denominator)
            val spread = RenderTextRules.blockSpread(size, 200.0, pointsPerMm / denominator)
            assertEquals("1:$denominator", RenderTextRules.exportFactor(denominator), spread, 1e-5)
        }
    }

    @Test
    fun `given editor text when computing the block spread then the screen line spacing does not depend on the zoom`() {
        val size = RenderTextRules.editorTextSizePx(200.0, referencePx = 24f)
        for (pixelsPerMm in listOf(0.005, 0.05, 0.5, 5.0)) {
            val spacingPx = 300.0 * RenderTextRules.blockSpread(size, 200.0, pixelsPerMm) * pixelsPerMm
            assertEquals("at $pixelsPerMm px/mm", 1.5 * size, spacingPx, 1e-3)
        }
    }

    @Test
    fun `given a block line when checking the band of its room then it is shown only while it fits`() {
        assertFalse(RenderTextRules.isOutsideBlockBand(lineOffsetPx = 30f, textSizePx = 40f, referenceLengthPx = 100f))
        assertTrue(RenderTextRules.isOutsideBlockBand(lineOffsetPx = 30f, textSizePx = 40f, referenceLengthPx = 99.9f))
    }
}
