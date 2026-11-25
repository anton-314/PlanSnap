package app.plansnap.core.drawing.plot

/** Horizontal alignment of a title block text relative to its x position. */
enum class TitleTextAlign {
    /** The text starts at x. */
    START,

    /** The text is centered on x. */
    CENTER,

    /** The text ends at x. */
    END,
}

/**
 * Position of one title block text on the page, in millimeters from the top left page corner with y down.
 *
 * @property xMm reference x, interpreted by [align].
 * @property baselineMm y of the alphabetic baseline.
 * @property sizeMm font size (em height) on paper, as SVG `font-size` and Android `Paint.textSize`.
 * @property align alignment relative to [xMm].
 */
data class TitleTextSlot(
    val xMm: Double,
    val baselineMm: Double,
    val sizeMm: Double,
    val align: TitleTextAlign,
)

/**
 * One segment of the scale bar on the page.
 *
 * @property leftMm left edge.
 * @property widthMm extent to the right, positive.
 * @property filled `true` for a segment filled with the title block ink, `false` for one filled with the page
 * background. Both are outlined with [TitleBlockLayout.strokeMm].
 */
data class ScaleBarSegment(
    val leftMm: Double,
    val widthMm: Double,
    val filled: Boolean,
)

/**
 * Scale bar of a page (DOMAIN section 12).
 *
 * @property nominalLengthMm length the bar represents, in world millimeters. Its label is this length formatted with
 * the export unit, like a dimension.
 * @property leftMm left edge of the bar on the page.
 * @property topMm top edge of the bar on the page.
 * @property lengthMm length on paper, `nominalLengthMm / M`.
 * @property heightMm height on paper.
 * @property segments alternating segments of equal width, starting filled, covering [lengthMm] without gaps.
 * @property startLabel slot of the label `0`, centered under the left end.
 * @property endLabel slot of the nominal length, centered under the right end.
 */
data class ScaleBarLayout(
    val nominalLengthMm: Long,
    val leftMm: Double,
    val topMm: Double,
    val lengthMm: Double,
    val heightMm: Double,
    val segments: List<ScaleBarSegment>,
    val startLabel: TitleTextSlot,
    val endLabel: TitleTextSlot,
)

/**
 * Resolved title block of a page: every position in page millimeters, so PDF, PNG and SVG draw the same block without
 * own arithmetic (ADR 0009, ADR 0022).
 *
 * @property frame outline of the block, equal to [titleBlockMm].
 * @property strokeMm stroke width of the frame and of the scale bar segment outlines.
 * @property projectName slot of the project name, top left.
 * @property projectNameMaxWidthMm width the project name may take. Outputs that can measure text shorten a longer
 * name with an ellipsis.
 * @property scaleLabel slot of the scale text `1:M` ([TitleBlockSpec.scaleText]), top right.
 * @property scaleBar the scale bar below the project name.
 */
data class TitleBlockLayout(
    val frame: PageRectMm,
    val strokeMm: Double,
    val projectName: TitleTextSlot,
    val projectNameMaxWidthMm: Double,
    val scaleLabel: TitleTextSlot,
    val scaleBar: ScaleBarLayout,
)

/**
 * The one definition of the title block (DOMAIN section 12, ADR 0022). All offsets are relative to [titleBlockMm] and
 * in millimeters on paper. Colors are not part of this pure specification; they come from the style sheet of
 * `:core:render` (title block ink and page background).
 *
 * Layout: a frame around the block. The project name starts at the left padding and the scale `1:M` ends at the right
 * padding, both on the header baseline. Below them the scale bar starts at the left padding, split into
 * [BAR_SEGMENTS] equal segments, with `0` and the nominal length centered under its ends.
 */
object TitleBlockSpec {
    /** Inner distance of texts and scale bar from the frame. */
    const val PADDING_MM: Double = 5.0

    /** Stroke width of the frame and the scale bar outlines. */
    const val STROKE_MM: Double = 0.25

    /** Baseline of project name and scale below the top edge of the block. */
    const val HEADER_BASELINE_MM: Double = 8.0

    /** Font size of project name and scale. */
    const val HEADER_TEXT_MM: Double = 5.0

    /** Width kept free for the scale text to the left of the right padding. */
    const val SCALE_TEXT_RESERVE_MM: Double = 25.0

    /** Top edge of the scale bar below the top edge of the block. */
    const val BAR_TOP_MM: Double = 12.0

    /** Height of the scale bar. */
    const val BAR_HEIGHT_MM: Double = 3.0

    /** Number of alternating segments of the scale bar. */
    const val BAR_SEGMENTS: Int = 2

    /** Font size of the scale bar labels. Their baseline lies this far below the bar. */
    const val BAR_LABEL_TEXT_MM: Double = 3.0

    private const val TWO_METERS_MM = 2_000L
    private const val FIVE_METERS_MM = 5_000L
    private const val TEN_METERS_MM = 10_000L

    /** Nominal length of the scale bar in world millimeters: 2 m at 1:50, 5 m at 1:100, 10 m at 1:200. */
    fun nominalLengthMm(scale: PlotScale): Long =
        when (scale) {
            PlotScale.ONE_TO_50 -> TWO_METERS_MM
            PlotScale.ONE_TO_100 -> FIVE_METERS_MM
            PlotScale.ONE_TO_200 -> TEN_METERS_MM
        }

    /** Scale text of the title block, `1:50`, locale independent. */
    fun scaleText(scale: PlotScale): String = "1:${scale.denominator}"

    /** Resolves the title block of [plan] to page positions. */
    fun layout(plan: PlotPlan): TitleBlockLayout {
        val frame = plan.titleBlockMm
        val left = frame.leftMm + PADDING_MM
        val right = frame.rightMm - PADDING_MM
        val headerBaseline = frame.topMm + HEADER_BASELINE_MM
        return TitleBlockLayout(
            frame = frame,
            strokeMm = STROKE_MM,
            projectName = TitleTextSlot(left, headerBaseline, HEADER_TEXT_MM, TitleTextAlign.START),
            projectNameMaxWidthMm = right - SCALE_TEXT_RESERVE_MM - left,
            scaleLabel = TitleTextSlot(right, headerBaseline, HEADER_TEXT_MM, TitleTextAlign.END),
            scaleBar = scaleBar(plan.scale, left, frame.topMm + BAR_TOP_MM),
        )
    }

    private fun scaleBar(
        scale: PlotScale,
        left: Double,
        top: Double,
    ): ScaleBarLayout {
        val nominal = nominalLengthMm(scale)
        val length = nominal.toDouble() / scale.denominator
        val segmentWidth = length / BAR_SEGMENTS
        val segments = List(BAR_SEGMENTS) { ScaleBarSegment(left + it * segmentWidth, segmentWidth, it % 2 == 0) }
        val labelBaseline = top + BAR_HEIGHT_MM + BAR_LABEL_TEXT_MM
        return ScaleBarLayout(
            nominalLengthMm = nominal,
            leftMm = left,
            topMm = top,
            lengthMm = length,
            heightMm = BAR_HEIGHT_MM,
            segments = segments,
            startLabel = TitleTextSlot(left, labelBaseline, BAR_LABEL_TEXT_MM, TitleTextAlign.CENTER),
            endLabel = TitleTextSlot(left + length, labelBaseline, BAR_LABEL_TEXT_MM, TitleTextAlign.CENTER),
        )
    }
}

/** Title block of this page resolved by [TitleBlockSpec]. */
val PlotPlan.titleBlockLayout: TitleBlockLayout get() = TitleBlockSpec.layout(this)
