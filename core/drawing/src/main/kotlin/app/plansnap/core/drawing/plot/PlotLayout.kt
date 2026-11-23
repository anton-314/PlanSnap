package app.plansnap.core.drawing.plot

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.ScreenPoint
import app.plansnap.core.geometry.primitives.Vec2

private const val A4_SHORT_MM = 210
private const val A4_LONG_MM = 297
private const val A3_SHORT_MM = 297
private const val A3_LONG_MM = 420
private const val SCALE_50 = 50
private const val SCALE_100 = 100
private const val SCALE_200 = 200

/** Paper size in the order of the automatic choice (DOMAIN section 12). */
enum class PaperSize(
    val shortSideMm: Int,
    val longSideMm: Int,
) {
    /** DIN A4. */
    A4(A4_SHORT_MM, A4_LONG_MM),

    /** DIN A3. */
    A3(A3_SHORT_MM, A3_LONG_MM),
}

/** Plot scale `1:denominator` in the order of the automatic choice (DOMAIN section 12). */
enum class PlotScale(
    val denominator: Int,
) {
    /** 1:50. */
    ONE_TO_50(SCALE_50),

    /** 1:100. */
    ONE_TO_100(SCALE_100),

    /** 1:200. */
    ONE_TO_200(SCALE_200),
}

/** Page orientation. */
enum class PageOrientation {
    /** Height exceeds width. */
    PORTRAIT,

    /** Width exceeds height. */
    LANDSCAPE,
}

/** Page layout constants (DOMAIN section 12). */
object PlotPageMetrics {
    /** Page margin in mm. */
    const val MARGIN_MM: Int = 10

    /** Height of the title block in mm. */
    const val TITLE_BLOCK_MM: Int = 25
}

/**
 * User override of the automatic choice (REQ-EXP-002). A `null` value is chosen automatically.
 *
 * @property scale fixed scale.
 * @property paper fixed paper.
 */
data class PlotSelection(
    val scale: PlotScale? = null,
    val paper: PaperSize? = null,
)

/**
 * Chosen page for a plan.
 *
 * @property worldBounds plotted world area.
 */
data class PlotPlan(
    val paper: PaperSize,
    val orientation: PageOrientation,
    val scale: PlotScale,
    val worldBounds: WorldBounds,
) {
    /** Page width in mm for the orientation. */
    val pageWidthMm: Int
        get() = if (orientation == PageOrientation.PORTRAIT) paper.shortSideMm else paper.longSideMm

    /** Page height in mm for the orientation. */
    val pageHeightMm: Int
        get() = if (orientation == PageOrientation.PORTRAIT) paper.longSideMm else paper.shortSideMm
}

/** Scale and page choice and the only conversion between world and page in exports (DOMAIN section 12). */
interface PlotLayout {
    /**
     * Chooses the first fitting combination, trying scales in order and A4 before A3 for each.
     *
     * @return the plan, `PLAN_TOO_LARGE` if nothing fits at 1:200 on A3, or `PLAN_DOES_NOT_FIT_SELECTION` if the plan
     * would fit automatically but not with [selection].
     */
    fun layout(
        bounds: WorldBounds,
        selection: PlotSelection,
    ): DomainResult<PlotPlan>

    /** Maps a world point to page points with `pt = mm_world / M · 72 / 25.4`, y down. */
    fun worldToPagePoints(
        plan: PlotPlan,
        world: Vec2,
    ): ScreenPoint
}
