package app.plansnap.core.designsystem

import androidx.compose.ui.graphics.Color
import app.plansnap.core.designsystem.theme.PlanSnapColors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.pow

/** WCAG 2.x contrast ratio (relative luminance of sRGB, ratio (L1 + 0.05) / (L2 + 0.05)). */
internal fun contrastRatio(
    first: Color,
    second: Color,
): Double {
    val a = luminance(first)
    val b = luminance(second)
    return (maxOf(a, b) + 0.05) / (minOf(a, b) + 0.05)
}

private fun luminance(color: Color): Double {
    fun channel(value: Float): Double {
        val v = value.toDouble()
        return if (v <= 0.03928) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    }
    return 0.2126 * channel(color.red) + 0.7152 * channel(color.green) + 0.0722 * channel(color.blue)
}

/** Proves WCAG AA (REQ-UX-007): 4.5:1 for text, 3:1 for UI elements and plan strokes, in both palettes. */
class ContrastTest {
    private data class ColorPair(
        val name: String,
        val foreground: Color,
        val background: Color,
        val minimum: Double,
    )

    private fun pairs(colors: PlanSnapColors): List<ColorPair> {
        val m = colors.material
        val text = 4.5
        val ui = 3.0
        return listOf(
            ColorPair("onPrimary/primary", m.onPrimary, m.primary, text),
            ColorPair("onPrimaryContainer/primaryContainer", m.onPrimaryContainer, m.primaryContainer, text),
            ColorPair("onSecondary/secondary", m.onSecondary, m.secondary, text),
            ColorPair("onSecondaryContainer/secondaryContainer", m.onSecondaryContainer, m.secondaryContainer, text),
            ColorPair("onTertiary/tertiary", m.onTertiary, m.tertiary, text),
            ColorPair("onBackground/background", m.onBackground, m.background, text),
            ColorPair("onSurface/surface", m.onSurface, m.surface, text),
            ColorPair("onSurfaceVariant/surfaceVariant", m.onSurfaceVariant, m.surfaceVariant, text),
            ColorPair("onSurfaceVariant/surface", m.onSurfaceVariant, m.surface, text),
            ColorPair("onError/error", m.onError, m.error, text),
            ColorPair("onErrorContainer/errorContainer", m.onErrorContainer, m.errorContainer, text),
            ColorPair("onSurface/surfaceContainerLowest", m.onSurface, m.surfaceContainerLowest, text),
            ColorPair("onSurface/surfaceContainerLow", m.onSurface, m.surfaceContainerLow, text),
            ColorPair("onSurface/surfaceContainer", m.onSurface, m.surfaceContainer, text),
            ColorPair("onSurface/surfaceContainerHigh", m.onSurface, m.surfaceContainerHigh, text),
            ColorPair("onSurface/surfaceContainerHighest", m.onSurface, m.surfaceContainerHighest, text),
            ColorPair("primary text on surface", m.primary, m.surface, text),
            ColorPair("planDimension text on surface", colors.planDimension, m.surface, text),
            ColorPair("warning text on surface", colors.warning, m.surface, text),
            ColorPair("success text on surface", colors.success, m.surface, text),
            ColorPair("conflict text on surface", colors.conflict, m.surface, text),
            ColorPair("planWall on surface", colors.planWall, m.surface, ui),
            ColorPair("planHighlight on surface", colors.planHighlight, m.surface, ui),
            ColorPair("outline on surface", m.outline, m.surface, ui),
        )
    }

    private fun assertAll(
        label: String,
        colors: PlanSnapColors,
    ) {
        val failures =
            pairs(colors).mapNotNull { pair ->
                val ratio = contrastRatio(pair.foreground, pair.background)
                if (ratio >= pair.minimum) null else "$label ${pair.name}: $ratio < ${pair.minimum}"
            }
        assertTrue(failures.joinToString("\n"), failures.isEmpty())
    }

    @Test
    fun `given the light palette when pairs are measured then all meet WCAG AA`() =
        assertAll("light", PlanSnapColors.Light)

    @Test
    fun `given the dark palette when pairs are measured then all meet WCAG AA`() =
        assertAll("dark", PlanSnapColors.Dark)

    @Test
    fun `given black on white when measured then the ratio is 21`() {
        assertEquals(21.0, contrastRatio(Color.Black, Color.White), 1e-9)
    }

    @Test
    fun `given identical colors when measured then the ratio is 1`() {
        assertEquals(1.0, contrastRatio(Color.Red, Color.Red), 1e-9)
    }

    @Test
    fun `given the UX tokens when read then they match docs UX section 6`() {
        assertEquals(Color(0xFF1B5FAA), PlanSnapColors.Light.material.primary)
        assertEquals(Color(0xFFA6C8FF), PlanSnapColors.Dark.material.primary)
        assertEquals(Color(0xFF8A5000), PlanSnapColors.Light.warning)
        assertEquals(Color(0xFF7FDB9F), PlanSnapColors.Dark.success)
        assertEquals(Color(0xFFDCE3EC), PlanSnapColors.Dark.planWall)
    }
}
