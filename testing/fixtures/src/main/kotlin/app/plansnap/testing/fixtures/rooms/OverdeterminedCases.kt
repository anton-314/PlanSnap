package app.plansnap.testing.fixtures.rooms

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.AdjustmentConfirmation
import app.plansnap.core.geometry.adjustment.ObservationRef
import app.plansnap.core.geometry.determinacy.MeasurementRecommendation
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.ConflictReason
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.toImmutableList

private const val DEFAULT_CORRECTION_TOLERANCE = 0.01
private const val RIGHT_MILLI_DEGREES = 90_000

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

    /** Every case in a stable order. */
    val ALL: List<OverdeterminedCase> =
        listOf(
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

/** Closed room with a free closing corner and declared right angles in [directions] (index 0 is not used). */
private fun declaredRoom(
    id: String,
    lengths: String,
    directions: String,
    diagonals: String,
): Room =
    closedRoom(
        id,
        lengths,
        directions.mapIndexed { i, code ->
            if (i == 0) CornerSpec.Unspecified else CornerSpec.DeclaredRight(directionOf(code))
        },
        diagonals,
    )

/** Closed room in which every corner, including `V_0`, is measured as 90 degrees in [directions]. */
private fun measuredRoom(
    id: String,
    lengths: String,
    directions: String,
): Room =
    closedRoom(
        id,
        lengths,
        directions.map { CornerSpec.Measured(directionOf(it), Angle(RIGHT_MILLI_DEGREES)) },
        "",
    )

private fun closedRoom(
    id: String,
    lengths: String,
    corners: List<CornerSpec>,
    diagonals: String,
): Room =
    Room(
        id = RoomId(id),
        walls =
            lengths
                .split(' ')
                .mapIndexed { i, mm ->
                    Wall(WallId("$id-w$i"), Length(mm.toLong()), Length(GeometryTolerances.DEFAULT_THICKNESS_MM))
                }.toImmutableList(),
        corners = corners.toImmutableList(),
        diagonals =
            diagonals
                .split(' ')
                .filter { it.isNotEmpty() }
                .mapIndexed { i, spec ->
                    val (from, to, mm) = spec.split('-', '=')
                    Diagonal(DiagonalId("$id-d$i"), from.toInt(), to.toInt(), Length(mm.toLong()))
                }.toImmutableList(),
        isClosed = true,
    )

private fun directionOf(code: Char): TurnDirection = if (code == 'L') TurnDirection.LEFT else TurnDirection.RIGHT
