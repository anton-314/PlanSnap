package app.plansnap.core.drawing.plot

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.drawing.viewport.ScreenPoint
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2

/**
 * Scale and page choice of DOMAIN section 12.
 *
 * **Fit:** a candidate fits if `bounds.width ≤ usableWidthMm · M` and `bounds.height ≤ usableHeightMm · M`, both
 * inclusive and evaluated exactly, because the right sides are whole millimeters.
 *
 * **Order:** scales [PlotScale.entries] (1:50, 1:100, 1:200), per scale [PaperSize.entries] (A4, A3), per paper both
 * orientations. A [PlotSelection] restricts the scales or papers to the chosen value and keeps the order for the rest.
 *
 * **Placement:** the plan is centred in [drawingAreaMm]: the center of [PlotPlan.worldBounds] lands on the center of
 * the drawing area, world y up becomes page y down.
 */
class DefaultPlotLayout : PlotLayout {
    override fun layout(
        bounds: WorldBounds,
        selection: PlotSelection,
    ): DomainResult<PlotPlan> {
        val chosen = firstFit(bounds, selection)
        return when {
            chosen != null -> DomainResult.Success(chosen)
            firstFit(bounds, PlotSelection()) != null -> failure(ExportProblem.PLAN_DOES_NOT_FIT_SELECTION)
            else -> failure(ExportProblem.PLAN_TOO_LARGE)
        }
    }

    /**
     * Applies `pt = mm_world / M · 72 / 25.4` relative to the centred placement described on the class.
     *
     * @throws IllegalArgumentException if the page point is not finite, which needs world coordinates near `1e308`.
     */
    override fun worldToPagePoints(
        plan: PlotPlan,
        world: Vec2,
    ): ScreenPoint {
        val area = plan.drawingAreaMm
        val bounds = plan.worldBounds
        val scale = plan.scale.denominator.toDouble()
        val centerX = bounds.minX + bounds.width / 2.0
        val centerY = bounds.minY + bounds.height / 2.0
        val pageXMm = area.leftMm + area.widthMm / 2.0 + (world.x - centerX) / scale
        val pageYMm = area.topMm + area.heightMm / 2.0 - (world.y - centerY) / scale
        return ScreenPoint(pageXMm * POINTS_PER_MM, pageYMm * POINTS_PER_MM)
    }

    private fun firstFit(
        bounds: WorldBounds,
        selection: PlotSelection,
    ): PlotPlan? {
        for (scale in PlotScale.entries.filter { selection.scale == null || it == selection.scale }) {
            for (paper in PaperSize.entries.filter { selection.paper == null || it == selection.paper }) {
                val fitting =
                    PageOrientation.entries
                        .map { PlotPlan(paper, it, scale, bounds) }
                        .filter { fits(it) }
                val preferred = preferredOrientation(bounds)
                val plan = fitting.firstOrNull { it.orientation == preferred } ?: fitting.firstOrNull()
                if (plan != null) return plan
            }
        }
        return null
    }

    private fun fits(plan: PlotPlan): Boolean {
        val scale = plan.scale.denominator.toDouble()
        return plan.worldBounds.width <= plan.usableWidthMm * scale &&
            plan.worldBounds.height <= plan.usableHeightMm * scale
    }

    /** Portrait if the height exceeds the width by more than `EXACT_CLOSURE_MM`, otherwise landscape. */
    private fun preferredOrientation(bounds: WorldBounds): PageOrientation =
        if (bounds.height - bounds.width > GeometryTolerances.EXACT_CLOSURE_MM) {
            PageOrientation.PORTRAIT
        } else {
            PageOrientation.LANDSCAPE
        }

    private fun failure(problem: ExportProblem): DomainResult<PlotPlan> =
        DomainResult.Failure(DomainError.Export(problem))

    private companion object {
        const val POINTS_PER_INCH = 72.0
        const val MM_PER_INCH = 25.4
        const val POINTS_PER_MM = POINTS_PER_INCH / MM_PER_INCH
    }
}
