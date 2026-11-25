package app.plansnap.core.drawing.plot

/**
 * Axis-aligned rectangle on a page in whole millimeters, measured from the top left page corner with y down.
 *
 * @property leftMm distance of the left edge from the left page edge.
 * @property topMm distance of the top edge from the top page edge.
 * @property widthMm extent to the right.
 * @property heightMm extent downwards.
 * @throws IllegalArgumentException if a value is negative.
 */
data class PageRectMm(
    val leftMm: Int,
    val topMm: Int,
    val widthMm: Int,
    val heightMm: Int,
) {
    init {
        require(leftMm >= 0 && topMm >= 0 && widthMm >= 0 && heightMm >= 0) { "Page rectangle must not be negative" }
    }

    /** Distance of the right edge from the left page edge. */
    val rightMm: Int get() = leftMm + widthMm

    /** Distance of the bottom edge from the top page edge. */
    val bottomMm: Int get() = topMm + heightMm
}

/**
 * Area of the page the plan is drawn into: inside the [PlotPageMetrics.MARGIN_MM] margins and above the title block,
 * [PlotPlan.usableWidthMm] wide and [PlotPlan.usableHeightMm] high (DOMAIN section 12).
 */
val PlotPlan.drawingAreaMm: PageRectMm
    get() =
        PageRectMm(
            leftMm = PlotPageMetrics.MARGIN_MM,
            topMm = PlotPageMetrics.MARGIN_MM,
            widthMm = usableWidthMm,
            heightMm = usableHeightMm,
        )

/**
 * Title block: at the bottom of the area inside the [PlotPageMetrics.MARGIN_MM] margins, over the full usable width and
 * [PlotPageMetrics.TITLE_BLOCK_MM] high (DOMAIN section 12). It directly adjoins [drawingAreaMm].
 */
val PlotPlan.titleBlockMm: PageRectMm
    get() =
        PageRectMm(
            leftMm = PlotPageMetrics.MARGIN_MM,
            topMm = pageHeightMm - PlotPageMetrics.MARGIN_MM - PlotPageMetrics.TITLE_BLOCK_MM,
            widthMm = usableWidthMm,
            heightMm = PlotPageMetrics.TITLE_BLOCK_MM,
        )
