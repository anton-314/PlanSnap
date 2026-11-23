package app.plansnap.testing.fixtures.rooms

import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.Room
import app.plansnap.core.model.ShapeStatus

private const val DEFAULT_CORRECTION_TOLERANCE = 0.01
private const val STRAIGHT_CORNER_TOLERANCE = 0.001
private const val M1_WALL = 3
private const val M1_ADJUSTED_WALL_MM = 3025.0

/**
 * Expected counts of the rank analysis (DOMAIN section 5.4).
 *
 * @property freeVariables `v`.
 * @property conditions `m`.
 * @property routingRank `ρ` at the routing point.
 * @property missing `v − ρ` at the solution.
 * @property redundancy `r = m − ρ` at the solution.
 */
data class ExpectedRank(
    val freeVariables: Int,
    val conditions: Int,
    val routingRank: Int,
    val missing: Int,
    val redundancy: Int,
)

/**
 * Expected size of the corrections, compared within [toleranceRelative].
 *
 * @property sumMm sum norm `S` in mm.
 * @property normalizedSquareSum `q²`.
 * @property toleranceRelative permitted relative deviation of both values.
 */
data class ExpectedCorrection(
    val sumMm: Double,
    val normalizedSquareSum: Double,
    val toleranceRelative: Double = DEFAULT_CORRECTION_TOLERANCE,
)

/**
 * Named reference case of the overdetermination rules (DOMAIN sections 4 to 6, ADR 0018). Expected values are test data
 * from the review and the recalculation of T-ARC-006, never output of production code. T-GEO-005 and T-GEO-006 must
 * check every case.
 *
 * @property key short unique name.
 * @property finding review finding or decision that the case secures.
 * @property room raw input.
 * @property expectedStatus status of the evaluation with the default tolerance.
 * @property expectedConflict conflict reason, present exactly for [ShapeStatus.CONFLICT].
 * @property expectedRank rank analysis, `null` if the path does not run it.
 * @property expectedCorrection size of the corrections, `null` if it is not asserted.
 * @property expectedConfirmation required confirmation, present exactly for [ShapeStatus.NEEDS_ADJUSTMENT].
 * @property expectedUncontrolled measurements that are not controllable, with their recommended measurement.
 * @property expectedLargestCorrection measurement with the largest correction, `null` if it is not asserted.
 * @property expectedAdjustedLengthsMm adjusted wall lengths by wall index, compared within 0.01 mm.
 */
data class OverdeterminedCase(
    val key: String,
    val finding: String,
    val room: Room,
    val expectedStatus: ShapeStatus,
    val expectedConflict: ConflictReason? = null,
    val expectedRank: ExpectedRank? = null,
    val expectedCorrection: ExpectedCorrection? = null,
    val expectedConfirmation: AdjustmentConfirmation? = null,
    val expectedUncontrolled: Map<ObservationRef, MeasurementRecommendation?> = emptyMap(),
    val expectedLargestCorrection: ObservationRef? = null,
    val expectedAdjustedLengthsMm: Map<Int, Double> = emptyMap(),
) {
    init {
        require((expectedStatus == ShapeStatus.CONFLICT) == (expectedConflict != null)) {
            "A conflict reason is required exactly for CONFLICT: $key"
        }
        require((expectedStatus == ShapeStatus.NEEDS_ADJUSTMENT) == (expectedConfirmation != null)) {
            "A confirmation is required exactly for NEEDS_ADJUSTMENT: $key"
        }
    }
}

private val ALL_MEASURED_RANK =
    ExpectedRank(freeVariables = 0, conditions = 3, routingRank = 0, missing = 0, redundancy = 3)
private val REPEATED_WALL_RANK =
    ExpectedRank(freeVariables = 3, conditions = 3, routingRank = 2, missing = 1, redundancy = 1)

/**
 * Catalog of the numeric cases of the phase 3 re-review and the decisions of T-ARC-006.
 *
 * Rooms are written as compact text: wall lengths in mm separated by spaces, one direction letter per corner, and
 * diagonals as `from-to=mm`.
 */
object OverdeterminedCases {
    /** NH1: the same diagonal twice leaves one measurement missing, so the shape is only a proposal. */
    val PENTAGON_REPEATED_DIAGONAL: OverdeterminedCase =
        OverdeterminedCase(
            key = "nh1-pentagon-repeated-diagonal",
            finding = "NH1",
            room = declaredRoom("nh1", "4000 3000 2500 2500 3000", "LLLLL", "0-2=5000 0-2=5000"),
            expectedStatus = ShapeStatus.PROPOSED,
            expectedRank =
                ExpectedRank(
                    freeVariables = 4,
                    conditions = 4,
                    routingRank = 3,
                    missing = 1,
                    redundancy = 1,
                ),
        )

    /** NH2: the plain adjustment from `τ̂` found a wrong shape. The solver start yields the measured one. */
    val HEXAGON_ALL_DIAGONALS: OverdeterminedCase =
        OverdeterminedCase(
            key = "nh2-hexagon-all-diagonals",
            finding = "NH2, NM6",
            room =
                declaredRoom(
                    "nh2",
                    "4404 2614 2150 4671 3690 5090",
                    "LLLLLL",
                    "0-2=6105 0-3=7543 0-4=8181 1-3=4718 1-4=7842 1-5=6603 2-4=6090 2-5=6137 3-5=5981",
                ),
            expectedStatus = ShapeStatus.CONFIRMED,
            expectedRank =
                ExpectedRank(
                    freeVariables = 5,
                    conditions = 11,
                    routingRank = 5,
                    missing = 0,
                    redundancy = 6,
                ),
            expectedCorrection =
                ExpectedCorrection(
                    sumMm = 2.03,
                    normalizedSquareSum = 0.016,
                    toleranceRelative = 0.05,
                ),
        )

    /** NH3: rectangle, U = 14 m, one wall wrong by 380 mm. */
    val RECTANGLE_WRONG_WALL: OverdeterminedCase =
        wrongWall(
            "nh3-rectangle-380",
            "4380 3000 4000 3000",
            "LLLL",
            ExpectedCorrection(sumMm = 576.96, normalizedSquareSum = 51.789),
        )

    /** NH3: L shape, U = 20 m, one wall wrong by 380 mm. */
    val L_SHAPE_WRONG_WALL: OverdeterminedCase =
        wrongWall(
            "nh3-l-shape-380",
            "6380 2000 3000 2000 3000 4000",
            "LLLRLL",
            ExpectedCorrection(sumMm = 660.35, normalizedSquareSum = 31.253),
        )

    /** NH3: U shape, U = 25 m, one wall wrong by 630 mm. */
    val U_SHAPE_WRONG_WALL: OverdeterminedCase =
        wrongWall(
            "nh3-u-shape-630",
            "6630 4000 2000 2500 2000 2500 2000 4000",
            "LLLLRRLL",
            ExpectedCorrection(sumMm = 1424.93, normalizedSquareSum = 55.159),
        )

    /** NH3: cross, U = 24 m, one wall wrong by 910 mm. */
    val CROSS_WRONG_WALL: OverdeterminedCase =
        wrongWall(
            "nh3-cross-910",
            "2910 2000 2000 2000 2000 2000 2000 2000 2000 2000 2000 2000",
            "LLRLLRLLRLLR",
            ExpectedCorrection(sumMm = 1630.89, normalizedSquareSum = 61.138),
        )

    /** REQ-MEAS-011: a gap of 3 cm stays a measurement error with a simple confirmation. */
    val GAP_30_MM: OverdeterminedCase =
        OverdeterminedCase(
            key = "gap-30-mm",
            finding = "NH3, REQ-MEAS-011",
            room = declaredRoom("gap", "4030 3000 4000 3000", "LLLL", ""),
            expectedStatus = ShapeStatus.NEEDS_ADJUSTMENT,
            expectedConfirmation = AdjustmentConfirmation.SIMPLE,
        )

    /** Large correction below `χ²(3; 0.95)`: ordinary confirmation, `S` stays visible. */
    val RECTANGLE_LARGE_SIMPLE: OverdeterminedCase =
        band(
            key = "band-rectangle-145-simple",
            lengths = "4145 3000 4000 3000",
            correction = ExpectedCorrection(sumMm = 216.4, normalizedSquareSum = 7.533, toleranceRelative = 0.005),
            confirmation = AdjustmentConfirmation.SIMPLE,
        )

    /** Large correction between `χ²(3; 0.95)` and `χ²(3; 0.99)`: explicit confirmation. */
    val RECTANGLE_LARGE_EXPLICIT: OverdeterminedCase =
        band(
            key = "band-rectangle-150-explicit",
            lengths = "4150 3000 4000 3000",
            correction = ExpectedCorrection(sumMm = 223.94, normalizedSquareSum = 8.062, toleranceRelative = 0.005),
            confirmation = AdjustmentConfirmation.EXPLICIT,
        )

    /** Large correction above `χ²(3; 0.99)`: conflict. */
    val RECTANGLE_LARGE_CONFLICT: OverdeterminedCase =
        OverdeterminedCase(
            key = "band-rectangle-180-conflict",
            finding = "NH3",
            room = measuredRoom("band-180", "4180 3000 4000 3000", "LLLL"),
            expectedStatus = ShapeStatus.CONFLICT,
            expectedConflict = ConflictReason.MEASUREMENTS_INCONSISTENT,
            expectedRank = ALL_MEASURED_RANK,
            expectedCorrection =
                ExpectedCorrection(sumMm = 269.32, normalizedSquareSum = 11.61, toleranceRelative = 0.005),
        )

    /** NM6: a consistent additional diagonal keeps the trapezoid confirmed. */
    val TRAPEZOID_TWO_DIAGONALS: OverdeterminedCase =
        OverdeterminedCase(
            key = "nm6-trapezoid-two-diagonals",
            finding = "NM6",
            room = declaredRoom("nm6", "5000 3000 3000 3000", "LLLL", "0-2=4899 1-3=4899"),
            expectedStatus = ShapeStatus.CONFIRMED,
            expectedRank =
                ExpectedRank(
                    freeVariables = 3,
                    conditions = 4,
                    routingRank = 3,
                    missing = 0,
                    redundancy = 1,
                ),
        )

    /** Rank at the outline: a diagonal between adjacent corners only repeats wall `w_3`. */
    val QUAD_REPEATED_WALL: OverdeterminedCase =
        repeatedWall("quad-adjacent-diagonal-3000", "0-3=3000", ShapeStatus.PROPOSED, null)

    /** As [QUAD_REPEATED_WALL], with the 1 mm difference of the review prototype. It must not end in a conflict. */
    val QUAD_REPEATED_WALL_1_MM: OverdeterminedCase =
        repeatedWall("quad-adjacent-diagonal-3001", "0-3=3001", ShapeStatus.PROPOSED, null)

    /** As [QUAD_REPEATED_WALL], with a repetition that contradicts the wall by 400 mm. */
    val QUAD_REPEATED_WALL_CONTRADICTION: OverdeterminedCase =
        repeatedWall(
            "quad-adjacent-diagonal-3400",
            "0-3=3400",
            ShapeStatus.CONFLICT,
            ConflictReason.MEASUREMENTS_INCONSISTENT,
        )

    /** A determined triangle with a right turn can only close by violating that declared direction. */
    val TRIANGLE_WRONG_DIRECTION: OverdeterminedCase =
        OverdeterminedCase(
            key = "direction-triangle",
            finding = "Decision 4",
            room = declaredRoom("direction", "3000 4000 6000", "LLR", ""),
            expectedStatus = ShapeStatus.CONFLICT,
            expectedConflict = ConflictReason.TURN_DIRECTION_VIOLATED,
            expectedRank =
                ExpectedRank(
                    freeVariables = 2,
                    conditions = 2,
                    routingRank = 2,
                    missing = 0,
                    redundancy = 0,
                ),
        )

    /** Fan plus one diagonal: `w_3`, `w_4`, `w_5` and the diagonal (0,4) have no cross-check. (2,5) provides it. */
    val HEXAGON_UNCONTROLLED: OverdeterminedCase =
        OverdeterminedCase(
            key = "uncontrolled-hexagon-fan",
            finding = "Decision 3",
            room =
                declaredRoom(
                    "uncontrolled",
                    "4000 2236 2663 2502 2860 2119",
                    "LLLLLL",
                    "0-2=5385 0-3=5467 0-4=4415 1-3=4230",
                ),
            expectedStatus = ShapeStatus.CONFIRMED,
            expectedRank =
                ExpectedRank(
                    freeVariables = 5,
                    conditions = 6,
                    routingRank = 5,
                    missing = 0,
                    redundancy = 1,
                ),
            expectedUncontrolled =
                listOf(
                    ObservationRef.WallLength(index = 3),
                    ObservationRef.WallLength(index = 4),
                    ObservationRef.WallLength(index = 5),
                    ObservationRef.DiagonalLength(index = 2),
                ).associateWith { MeasurementRecommendation.DiagonalBetween(fromCorner = 2, toCorner = 5) },
        )

    /** H1: the outline closes with gap 0, but `w_3` crosses `w_0` at (1000, 0). */
    val SELF_INTERSECTING_EXACT: OverdeterminedCase =
        OverdeterminedCase(
            key = "h1-self-intersecting-exact",
            finding = "H1",
            room = declaredRoom("h1", "2000 2000 1000 3000 1000 1000", "LLLLRR", ""),
            expectedStatus = ShapeStatus.CONFLICT,
            expectedConflict = ConflictReason.NO_SIMPLE_SHAPE,
        )

    /** H2 case a: rounding makes the start form fail, branch B still confirms the trapezoid. */
    val TRAPEZOID_ROUNDED_ANGLES: OverdeterminedCase =
        roundedTrapezoid("h2a-trapezoid-109471", "F L M109471 M109471")

    /** H2 case a, control value: with 109.472 degrees the start form exists. */
    val TRAPEZOID_ROUNDED_ANGLES_CONTROL: OverdeterminedCase =
        roundedTrapezoid("h2a-trapezoid-109472", "F L M109472 M109472")

    /** H2 case b: the diagonal (0,3) follows from `L_3`, `L_4` and `α_4`, so the rank at the outline is 2. */
    val PENTAGON_DEPENDENT_DIAGONAL: OverdeterminedCase =
        OverdeterminedCase(
            key = "h2b-pentagon-dependent-diagonal",
            finding = "H2",
            room = mixedRoom("h2b", "4000 2864 3106 2802 2915", "F L L L M118918", "0-3=4924"),
            expectedStatus = ShapeStatus.PROPOSED,
            expectedRank =
                ExpectedRank(
                    freeVariables = 3,
                    conditions = 3,
                    routingRank = 3,
                    missing = 1,
                    redundancy = 1,
                ),
            expectedCorrection =
                ExpectedCorrection(sumMm = 0.3207, normalizedSquareSum = 5.3e-5, toleranceRelative = 0.05),
        )

    /** H3: a straight corner declared left. */
    val STRAIGHT_CORNER_LEFT: OverdeterminedCase =
        straightCorner(
            "h3-straight-left",
            "M90000 M180000 M90000 M90000 M90000",
            ExpectedCorrection(
                sumMm = 223.3962,
                normalizedSquareSum = 6.67592,
                toleranceRelative = STRAIGHT_CORNER_TOLERANCE,
            ),
        )

    /** H3: the same geometry declared right must give the same result. */
    val STRAIGHT_CORNER_RIGHT: OverdeterminedCase =
        straightCorner(
            "h3-straight-right",
            "M90000 m180000 M90000 M90000 M90000",
            ExpectedCorrection(
                sumMm = 223.3962,
                normalizedSquareSum = 6.67592,
                toleranceRelative = STRAIGHT_CORNER_TOLERANCE,
            ),
        )

    /** H3: 179.8 degrees to the right stays within the direction tolerance. */
    val NEARLY_STRAIGHT_CORNER_RIGHT: OverdeterminedCase =
        straightCorner(
            "h3-nearly-straight-right",
            "M90000 m179800 M90000 M90000 M90000",
            ExpectedCorrection(
                sumMm = 231.2667,
                normalizedSquareSum = 7.065564,
                toleranceRelative = STRAIGHT_CORNER_TOLERANCE,
            ),
        )

    /** M1: the proposal changes wall 3 from 3000 to 3025 mm and must report the correction. */
    val PROPOSED_WITH_CORRECTION: OverdeterminedCase =
        OverdeterminedCase(
            key = "m1-proposed-with-correction",
            finding = "M1",
            room = declaredRoom("m1", "5000 3000 3000 3000", "LLLL", "0-3=3050"),
            expectedStatus = ShapeStatus.PROPOSED,
            expectedRank = REPEATED_WALL_RANK,
            expectedCorrection = ExpectedCorrection(sumMm = 50.0, normalizedSquareSum = 50.0),
            expectedLargestCorrection = ObservationRef.WallLength(index = 3),
            expectedAdjustedLengthsMm = mapOf(M1_WALL to M1_ADJUSTED_WALL_MM),
        )

    /** Systematic check: the start must take the branch that also fits the dependent closing condition. */
    val TRIANGLE_START_BRANCH: OverdeterminedCase =
        OverdeterminedCase(
            key = "flow-start-branch-triangle",
            finding = "Flow start selection",
            room = mixedRoom("flow-branch", "6019 5092 9633", "F M119996 L", ""),
            expectedStatus = ShapeStatus.CONFIRMED,
            expectedRank =
                ExpectedRank(
                    freeVariables = 1,
                    conditions = 2,
                    routingRank = 1,
                    missing = 0,
                    redundancy = 1,
                ),
        )

    /** Systematic check: the solver must not leave an outline that is already closed and simple. */
    val HEXAGON_KEEPS_SIMPLE: OverdeterminedCase =
        OverdeterminedCase(
            key = "flow-keeps-simple-hexagon",
            finding = "Flow simple iterate",
            room = mixedRoom("flow-simple", "6497 3430 4955 2310 3418 4526", "F L M84256 L L L", ""),
            expectedStatus = ShapeStatus.PROPOSED,
            expectedRank =
                ExpectedRank(
                    freeVariables = 4,
                    conditions = 2,
                    routingRank = 2,
                    missing = 2,
                    redundancy = 0,
                ),
        )

    /** Systematic check: the adjustment must not pull corners without an observation back to their target. */
    val HEXAGON_UNOBSERVED_TURNS: OverdeterminedCase =
        OverdeterminedCase(
            key = "flow-unobserved-turns-hexagon",
            finding = "Flow unobserved turns",
            room = mixedRoom("flow-pull", "2090 2597 1328 1273 9514 4745", "F L L L M81921 L", "3-5=9420"),
            expectedStatus = ShapeStatus.PROPOSED,
            expectedRank =
                ExpectedRank(
                    freeVariables = 4,
                    conditions = 3,
                    routingRank = 2,
                    missing = 2,
                    redundancy = 1,
                ),
        )

    /** Every case in a stable order. */
    val ALL: List<OverdeterminedCase> =
        listOf(
            SELF_INTERSECTING_EXACT,
            TRAPEZOID_ROUNDED_ANGLES,
            TRAPEZOID_ROUNDED_ANGLES_CONTROL,
            PENTAGON_DEPENDENT_DIAGONAL,
            STRAIGHT_CORNER_LEFT,
            STRAIGHT_CORNER_RIGHT,
            NEARLY_STRAIGHT_CORNER_RIGHT,
            PROPOSED_WITH_CORRECTION,
            TRIANGLE_START_BRANCH,
            HEXAGON_KEEPS_SIMPLE,
            HEXAGON_UNOBSERVED_TURNS,
            PENTAGON_REPEATED_DIAGONAL,
            HEXAGON_ALL_DIAGONALS,
            RECTANGLE_WRONG_WALL,
            L_SHAPE_WRONG_WALL,
            U_SHAPE_WRONG_WALL,
            CROSS_WRONG_WALL,
            GAP_30_MM,
            RECTANGLE_LARGE_SIMPLE,
            RECTANGLE_LARGE_EXPLICIT,
            RECTANGLE_LARGE_CONFLICT,
            TRAPEZOID_TWO_DIAGONALS,
            QUAD_REPEATED_WALL,
            QUAD_REPEATED_WALL_1_MM,
            QUAD_REPEATED_WALL_CONTRADICTION,
            TRIANGLE_WRONG_DIRECTION,
            HEXAGON_UNCONTROLLED,
        )
}

private fun wrongWall(
    key: String,
    lengths: String,
    directions: String,
    correction: ExpectedCorrection,
): OverdeterminedCase =
    OverdeterminedCase(
        key = key,
        finding = "NH3",
        room = measuredRoom(key, lengths, directions),
        expectedStatus = ShapeStatus.CONFLICT,
        expectedConflict = ConflictReason.MEASUREMENTS_INCONSISTENT,
        expectedRank = ALL_MEASURED_RANK,
        expectedCorrection = correction,
    )

private fun band(
    key: String,
    lengths: String,
    correction: ExpectedCorrection,
    confirmation: AdjustmentConfirmation,
): OverdeterminedCase =
    OverdeterminedCase(
        key = key,
        finding = "NH3",
        room = measuredRoom(key, lengths, "LLLL"),
        expectedStatus = ShapeStatus.NEEDS_ADJUSTMENT,
        expectedRank = ALL_MEASURED_RANK,
        expectedCorrection = correction,
        expectedConfirmation = confirmation,
    )

private fun repeatedWall(
    key: String,
    diagonal: String,
    status: ShapeStatus,
    conflict: ConflictReason?,
): OverdeterminedCase =
    OverdeterminedCase(
        key = key,
        finding = "Decision 5",
        room = declaredRoom(key, "5000 3000 3000 3000", "LLLL", diagonal),
        expectedStatus = status,
        expectedConflict = conflict,
        expectedRank = REPEATED_WALL_RANK,
    )

private fun roundedTrapezoid(
    key: String,
    corners: String,
): OverdeterminedCase =
    OverdeterminedCase(
        key = key,
        finding = "H2",
        room = mixedRoom(key, "5000 3000 3000 3000", corners, ""),
        expectedStatus = ShapeStatus.CONFIRMED,
        expectedRank =
            ExpectedRank(
                freeVariables = 1,
                conditions = 2,
                routingRank = 1,
                missing = 0,
                redundancy = 1,
            ),
    )

private fun straightCorner(
    key: String,
    corners: String,
    correction: ExpectedCorrection,
): OverdeterminedCase =
    OverdeterminedCase(
        key = key,
        finding = "H3",
        room = mixedRoom(key, "2150 2000 3000 4000 3000", corners, ""),
        expectedStatus = ShapeStatus.NEEDS_ADJUSTMENT,
        expectedRank = ALL_MEASURED_RANK,
        expectedCorrection = correction,
        expectedConfirmation = AdjustmentConfirmation.SIMPLE,
    )
