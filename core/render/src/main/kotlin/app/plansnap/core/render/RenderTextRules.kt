package app.plansnap.core.render

import kotlin.math.max

/**
 * Text sizing rules of the renderer (ADR 0008, addendum CR-W2-04). Scene label heights are nominal world heights; the
 * editor draws text at a constant screen size in their ratio, the export enlarges it to a minimum on paper.
 */
object RenderTextRules {
    /** Nominal label height in world millimeters that is drawn with the reference size, the dimension number. */
    const val NOMINAL_HEIGHT_MM: Double = 150.0

    /** Reference text size of the nominal height in the editor, in sp. */
    const val EDITOR_REFERENCE_SP: Float = 12f

    /** Minimum text height on paper in millimeters. */
    const val EXPORT_MIN_TEXT_MM: Double = 1.8

    private const val POINTS_PER_MM = 72.0 / 25.4

    /**
     * Editor text size in pixels, independent of the zoom.
     *
     * @param heightMm nominal label height in world millimeters.
     * @param referencePx pixels of [EDITOR_REFERENCE_SP], that is `12 · density · fontScale`.
     */
    fun editorTextSizePx(
        heightMm: Double,
        referencePx: Float,
    ): Float = (referencePx * heightMm / NOMINAL_HEIGHT_MM).toFloat()

    /** Export factor `f = max(1, 1,8 mm · M / 150 mm)` for the scale `1:[scaleDenominator]` (DOMAIN section 12). */
    fun exportFactor(scaleDenominator: Int): Double =
        max(1.0, EXPORT_MIN_TEXT_MM * scaleDenominator / NOMINAL_HEIGHT_MM)

    /** Export text size in page points for a label of [heightMm] at the scale `1:[scaleDenominator]`. */
    fun exportTextSizePt(
        heightMm: Double,
        scaleDenominator: Int,
    ): Float = (heightMm * exportFactor(scaleDenominator) / scaleDenominator * POINTS_PER_MM).toFloat()

    /**
     * Whether an editor label is hidden: its text is wider than the element it refers to (the dimension line of its
     * wall or diagonal, otherwise the smaller side of its room), measured on the screen.
     */
    fun isHidden(
        textWidthPx: Float,
        referenceLengthPx: Float,
    ): Boolean = textWidthPx > referenceLengthPx

    /**
     * Spread factor `k` of a label block line (ADR 0008, addendum CR-W2-05): the drawn text size divided by the
     * nominal height on the canvas. The line is drawn at `blockAnchor + k · (anchor − blockAnchor)`, so the spacing of
     * the lines scales with the text. In the export `k` equals [exportFactor], in the editor it keeps the lines at a
     * constant screen spacing at every zoom.
     *
     * @param textSize drawn text size in canvas units (pixels or points).
     * @param heightMm nominal label height in world millimeters, positive.
     * @param unitsPerMm canvas units per world millimeter, positive.
     */
    fun blockSpread(
        textSize: Float,
        heightMm: Double,
        unitsPerMm: Double,
    ): Double = textSize / (heightMm * unitsPerMm)

    /**
     * Whether an editor line of a label block is hidden because it leaves the band around the block anchor that its
     * room offers: twice its distance [lineOffsetPx] from the block anchor plus its text size exceeds
     * [referenceLengthPx], the smaller side of the room on the screen. Lines in the middle of the block are kept
     * longest; it complements [isHidden], which limits the width.
     */
    fun isOutsideBlockBand(
        lineOffsetPx: Float,
        textSizePx: Float,
        referenceLengthPx: Float,
    ): Boolean = 2f * lineOffsetPx + textSizePx > referenceLengthPx
}
