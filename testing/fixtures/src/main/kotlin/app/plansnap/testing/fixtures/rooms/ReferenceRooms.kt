package app.plansnap.testing.fixtures.rooms

import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList

/**
 * Reference room with analytically derived expectations (TESTING section 3). Every expected value below is a
 * hand-derived literal. None of them is computed by production code, so they are test data, not results.
 *
 * @property key short unique name of the case.
 * @property room raw input as the user enters it, a closed room with the local frame of DOMAIN section 2.
 * @property vertices corner points `V_0 … V_{n-1}` of the expected shape in the local frame, counter-clockwise.
 * @property expectedAreaMm2 area of the inner contour in mm².
 * @property expectedPerimeterMm sum of the wall lengths in mm.
 * @property expectedInteriorAnglesDeg interior angle in degrees at each corner `V_0 … V_{n-1}`, reflex above 180.
 * @property expectedStatus status DOMAIN section 4 assigns to [room], `null` if not asserted analytically.
 * @property inputDeterminesShape whether the raw values of [room] alone fix [vertices]. `false` for an input that
 * leaves the shape open and needs a solver or an additional measurement to arrive at [vertices].
 */
data class ReferenceRoom(
    val key: String,
    val room: Room,
    val vertices: ImmutableList<Vec2>,
    val expectedAreaMm2: Double,
    val expectedPerimeterMm: Double,
    val expectedInteriorAnglesDeg: ImmutableList<Double>,
    val expectedStatus: ShapeStatus?,
    val inputDeterminesShape: Boolean = true,
) {
    /** Expected area in m². */
    val expectedAreaSquareMeters: Double get() = expectedAreaMm2 / MM2_PER_M2

    private companion object {
        const val MM2_PER_M2 = 1_000_000.0
    }
}

private const val RIGHT_DEG = 90.0
private const val REFLEX_DEG = 270.0
private val RECTANGULAR_ANGLES: List<Double> = listOf(RIGHT_DEG, RIGHT_DEG, RIGHT_DEG, RIGHT_DEG)

// Oblique corners of the 3-4-5 triangles: atan(4/3) = 53.13 degrees turn, so the interior angles are 180 +- 53.13.
private const val OBTUSE_126_87_DEG = 126.86989764584402
private const val OBTUSE_143_13_DEG = 143.13010235415598
private const val REFLEX_233_13_DEG = 233.13010235415598

// Isosceles trapezoid 3 m, 3 m, 3 m, 5 m: leg 3 m, offset (5 - 3) / 2 = 1 m, height sqrt(3^2 - 1^2) = 2 sqrt(2) m.
private const val TRAPEZOID_HEIGHT_MM = 2828.42712474619
private const val TRAPEZOID_AREA_MM2 = 11_313_708.498984762
private const val TRAPEZOID_BASE_ANGLE_DEG = 70.52877936550931
private const val TRAPEZOID_TOP_ANGLE_DEG = 109.47122063449069
private const val TRAPEZOID_LONG_BASE_MM = 5000L
private const val TRAPEZOID_SIDE_MM = 3000L
private const val TRAPEZOID_DIAGONAL_MM = 4899L
private const val REFERENCE_THICKNESS_MM = 120L

/**
 * The reference rooms of TESTING section 3: rectangle, square, L, U, T, niche, bay, slope, concave corners, very
 * small room, very large room and the trapezoid 3 × 3 × 3 × 5 m with area 8√2 m².
 *
 * Each outline is counter-clockwise with `V_0` at a corner and `w_0` pointing to +x where possible.
 */
object ReferenceRooms {
    /** Rectangle 4 m × 3 m. Area 12 m², perimeter 14 m. */
    val RECTANGLE: ReferenceRoom =
        reference(
            key = "rectangle",
            vertices =
                listOf(
                    vertex(x = 0, y = 0),
                    vertex(x = 4000, y = 0),
                    vertex(x = 4000, y = 3000),
                    vertex(x = 0, y = 3000),
                ),
            areaMm2 = 12_000_000.0,
            perimeterMm = 14_000.0,
            angles = RECTANGULAR_ANGLES,
        )

    /** Square 3 m × 3 m. Area 9 m², perimeter 12 m. */
    val SQUARE: ReferenceRoom =
        reference(
            key = "square",
            vertices =
                listOf(
                    vertex(x = 0, y = 0),
                    vertex(x = 3000, y = 0),
                    vertex(x = 3000, y = 3000),
                    vertex(x = 0, y = 3000),
                ),
            areaMm2 = 9_000_000.0,
            perimeterMm = 12_000.0,
            angles = RECTANGULAR_ANGLES,
        )

    /** L shape: 6 m × 2 m base plus a 3 m × 2 m arm. Area 18 m², perimeter 20 m, one reflex corner. */
    val L_SHAPE: ReferenceRoom =
        reference(
            key = "l-shape",
            vertices =
                listOf(
                    vertex(x = 0, y = 0),
                    vertex(x = 6000, y = 0),
                    vertex(x = 6000, y = 2000),
                    vertex(x = 3000, y = 2000),
                    vertex(x = 3000, y = 4000),
                    vertex(x = 0, y = 4000),
                ),
            areaMm2 = 18_000_000.0,
            perimeterMm = 20_000.0,
            angles = listOf(RIGHT_DEG, RIGHT_DEG, RIGHT_DEG, REFLEX_DEG, RIGHT_DEG, RIGHT_DEG),
        )

    /** U shape: 6 m × 4 m minus a 2 m × 2.5 m cut-out. Area 19 m², perimeter 25 m, two reflex corners. */
    val U_SHAPE: ReferenceRoom =
        reference(
            key = "u-shape",
            vertices =
                listOf(
                    vertex(x = 0, y = 0),
                    vertex(x = 6000, y = 0),
                    vertex(x = 6000, y = 4000),
                    vertex(x = 4000, y = 4000),
                    vertex(x = 4000, y = 1500),
                    vertex(x = 2000, y = 1500),
                    vertex(x = 2000, y = 4000),
                    vertex(x = 0, y = 4000),
                ),
            areaMm2 = 19_000_000.0,
            perimeterMm = 25_000.0,
            angles = listOf(RIGHT_DEG, RIGHT_DEG, RIGHT_DEG, RIGHT_DEG, REFLEX_DEG, REFLEX_DEG, RIGHT_DEG, RIGHT_DEG),
        )

    /** T shape: stem 2 m × 3 m below a bar 6 m × 2 m. Area 18 m², perimeter 22 m, two reflex corners. */
    val T_SHAPE: ReferenceRoom =
        reference(
            key = "t-shape",
            vertices =
                listOf(
                    vertex(x = 2000, y = 0),
                    vertex(x = 4000, y = 0),
                    vertex(x = 4000, y = 3000),
                    vertex(x = 6000, y = 3000),
                    vertex(x = 6000, y = 5000),
                    vertex(x = 0, y = 5000),
                    vertex(x = 0, y = 3000),
                    vertex(x = 2000, y = 3000),
                ),
            areaMm2 = 18_000_000.0,
            perimeterMm = 22_000.0,
            angles = listOf(RIGHT_DEG, RIGHT_DEG, REFLEX_DEG, RIGHT_DEG, RIGHT_DEG, RIGHT_DEG, RIGHT_DEG, REFLEX_DEG),
        )

    /** Rectangle 4 m × 3 m with an outward niche 1 m wide and 0.5 m deep. Area 12.5 m², perimeter 15 m. */
    val NICHE: ReferenceRoom =
        reference(
            key = "niche",
            vertices =
                listOf(
                    vertex(x = 0, y = 0),
                    vertex(x = 1500, y = 0),
                    vertex(x = 1500, y = -500),
                    vertex(x = 2500, y = -500),
                    vertex(x = 2500, y = 0),
                    vertex(x = 4000, y = 0),
                    vertex(x = 4000, y = 3000),
                    vertex(x = 0, y = 3000),
                ),
            areaMm2 = 12_500_000.0,
            perimeterMm = 15_000.0,
            angles = listOf(RIGHT_DEG, REFLEX_DEG, RIGHT_DEG, RIGHT_DEG, REFLEX_DEG, RIGHT_DEG, RIGHT_DEG, RIGHT_DEG),
        )

    /**
     * Rectangle 4 m × 3 m with a bay on the top wall. The bay is a trapezoid with bases 2 m and 0.8 m, height 0.8 m
     * and 3-4-5 legs of 1 m. Area 12 + (2 + 0.8) / 2 · 0.8 = 13.12 m², perimeter 14 - 2 + 1 + 0.8 + 1 = 14.8 m.
     * The oblique corners are measured and carry the rounding to thousandths of a degree.
     */
    val BAY: ReferenceRoom =
        reference(
            key = "bay",
            vertices =
                listOf(
                    vertex(x = 0, y = 0),
                    vertex(x = 4000, y = 0),
                    vertex(x = 4000, y = 3000),
                    vertex(x = 3000, y = 3000),
                    vertex(x = 2400, y = 3800),
                    vertex(x = 1600, y = 3800),
                    vertex(x = 1000, y = 3000),
                    vertex(x = 0, y = 3000),
                ),
            areaMm2 = 13_120_000.0,
            perimeterMm = 14_800.0,
            angles =
                listOf(
                    RIGHT_DEG,
                    RIGHT_DEG,
                    RIGHT_DEG,
                    REFLEX_233_13_DEG,
                    OBTUSE_126_87_DEG,
                    OBTUSE_126_87_DEG,
                    REFLEX_233_13_DEG,
                    RIGHT_DEG,
                ),
        )

    /**
     * Right trapezoid with a slanted wall: 6 m base, 6 m left side, 3 m right side and a 3-4-5 slope of 5 m. The
     * area is the rectangle 2 m × 6 m plus the trapezoid between x = 2 m and x = 6 m, 12 + (3 + 6) / 2 · 4 = 30 m².
     * Perimeter 6 + 3 + 5 + 2 + 6 = 22 m. The oblique corners are measured.
     */
    val SLOPE: ReferenceRoom =
        reference(
            key = "slope",
            vertices =
                listOf(
                    vertex(x = 0, y = 0),
                    vertex(x = 6000, y = 0),
                    vertex(x = 6000, y = 3000),
                    vertex(x = 2000, y = 6000),
                    vertex(x = 0, y = 6000),
                ),
            areaMm2 = 30_000_000.0,
            perimeterMm = 22_000.0,
            angles = listOf(RIGHT_DEG, RIGHT_DEG, OBTUSE_126_87_DEG, OBTUSE_143_13_DEG, RIGHT_DEG),
        )

    /** Cross of five 2 m squares with four concave corners. Area 20 m², perimeter 24 m, 12 walls. */
    val CROSS: ReferenceRoom =
        reference(
            key = "cross",
            vertices =
                listOf(
                    vertex(x = 2000, y = 0),
                    vertex(x = 4000, y = 0),
                    vertex(x = 4000, y = 2000),
                    vertex(x = 6000, y = 2000),
                    vertex(x = 6000, y = 4000),
                    vertex(x = 4000, y = 4000),
                    vertex(x = 4000, y = 6000),
                    vertex(x = 2000, y = 6000),
                    vertex(x = 2000, y = 4000),
                    vertex(x = 0, y = 4000),
                    vertex(x = 0, y = 2000),
                    vertex(x = 2000, y = 2000),
                ),
            areaMm2 = 20_000_000.0,
            perimeterMm = 24_000.0,
            angles =
                listOf(
                    RIGHT_DEG,
                    RIGHT_DEG,
                    REFLEX_DEG,
                    RIGHT_DEG,
                    RIGHT_DEG,
                    REFLEX_DEG,
                    RIGHT_DEG,
                    RIGHT_DEG,
                    REFLEX_DEG,
                    RIGHT_DEG,
                    RIGHT_DEG,
                    REFLEX_DEG,
                ),
        )

    /** Very small room, a 10 mm square, the smallest permitted wall length. Area 100 mm², perimeter 40 mm. */
    val TINY: ReferenceRoom =
        reference(
            key = "tiny",
            vertices =
                listOf(
                    vertex(x = 0, y = 0),
                    vertex(x = 10, y = 0),
                    vertex(x = 10, y = 10),
                    vertex(x = 0, y = 10),
                ),
            areaMm2 = 100.0,
            perimeterMm = 40.0,
            angles = RECTANGULAR_ANGLES,
        )

    /** Very large room, a 100 m square, the largest permitted wall length. Area 10 000 m², perimeter 400 m. */
    val HUGE: ReferenceRoom =
        reference(
            key = "huge",
            vertices =
                listOf(
                    vertex(x = 0, y = 0),
                    vertex(x = 100_000, y = 0),
                    vertex(x = 100_000, y = 100_000),
                    vertex(x = 0, y = 100_000),
                ),
            areaMm2 = 10_000_000_000.0,
            perimeterMm = 400_000.0,
            angles = RECTANGULAR_ANGLES,
        )

    /**
     * Mandatory case 3000 / 3000 / 3000 / 5000 mm with all corners declared right and `V_0` unspecified, so the
     * rectangle-like input does not close (gap 2 m, DOMAIN section 4). The expected shape is the isosceles
     * trapezoid with the 5 m wall as long base: `V_0 = (0, 0)`, `V_1 = (5, 0)`, `V_2 = (4, h)`, `V_3 = (1, h)` in
     * meters with `h = 2√2`. The area is `(5 + 3) / 2 · 2√2 = 8√2 m²` (about 11.3137 m²), the perimeter 14 m.
     * The base angles are `acos(1/3)` and the top angles its supplement. The input leaves the shape open, so
     * [ReferenceRoom.expectedStatus] is `PROPOSED` and [ReferenceRoom.inputDeterminesShape] is `false`.
     */
    val TRAPEZOID_3_3_3_5: ReferenceRoom =
        ReferenceRoom(
            key = "trapezoid-3-3-3-5",
            room = trapezoidInput(withDiagonal = false),
            vertices = trapezoidVertices(),
            expectedAreaMm2 = TRAPEZOID_AREA_MM2,
            expectedPerimeterMm = 14_000.0,
            expectedInteriorAnglesDeg =
                persistentListOf(
                    TRAPEZOID_BASE_ANGLE_DEG,
                    TRAPEZOID_BASE_ANGLE_DEG,
                    TRAPEZOID_TOP_ANGLE_DEG,
                    TRAPEZOID_TOP_ANGLE_DEG,
                ),
            expectedStatus = ShapeStatus.PROPOSED,
            inputDeterminesShape = false,
        )

    /**
     * The trapezoid of [TRAPEZOID_3_3_3_5] plus the diagonal `V_0 → V_2` of `sqrt(4² + (2√2)²) = sqrt(24)` m, entered
     * as 4899 mm. It confirms the shape (E2E-8). The status is not asserted because the rounded diagonal is only
     * checked against the solver, not derived analytically here.
     */
    val TRAPEZOID_WITH_DIAGONAL: ReferenceRoom =
        TRAPEZOID_3_3_3_5.copy(
            key = "trapezoid-3-3-3-5-diagonal",
            room = trapezoidInput(withDiagonal = true),
            expectedStatus = null,
        )

    /** All reference rooms in a fixed order. */
    val ALL: ImmutableList<ReferenceRoom> =
        persistentListOf(
            RECTANGLE,
            SQUARE,
            L_SHAPE,
            U_SHAPE,
            T_SHAPE,
            NICHE,
            BAY,
            SLOPE,
            CROSS,
            TINY,
            HUGE,
            TRAPEZOID_3_3_3_5,
            TRAPEZOID_WITH_DIAGONAL,
        )

    private fun reference(
        key: String,
        vertices: List<Vec2>,
        areaMm2: Double,
        perimeterMm: Double,
        angles: List<Double>,
    ): ReferenceRoom =
        ReferenceRoom(
            key = key,
            room = roomFromVertices(idPrefix = "ref-$key", vertices = vertices),
            vertices = vertices.toImmutableList(),
            expectedAreaMm2 = areaMm2,
            expectedPerimeterMm = perimeterMm,
            expectedInteriorAnglesDeg = angles.toImmutableList(),
            expectedStatus = ShapeStatus.CONFIRMED,
        )

    private fun trapezoidVertices(): ImmutableList<Vec2> =
        persistentListOf(
            vertex(x = 0, y = 0),
            vertex(x = 5000, y = 0),
            Vec2(x = 4000.0, y = TRAPEZOID_HEIGHT_MM),
            Vec2(x = 1000.0, y = TRAPEZOID_HEIGHT_MM),
        )

    private fun trapezoidInput(withDiagonal: Boolean): Room {
        val idPrefix = if (withDiagonal) "ref-trapezoid-3-3-3-5-diagonal" else "ref-trapezoid-3-3-3-5"
        val lengths = listOf(TRAPEZOID_LONG_BASE_MM, TRAPEZOID_SIDE_MM, TRAPEZOID_SIDE_MM, TRAPEZOID_SIDE_MM)
        return Room(
            id = RoomId(idPrefix),
            walls =
                lengths
                    .mapIndexed {
                        i,
                        mm,
                        ->
                        Wall(WallId("$idPrefix-w$i"), Length(mm), Length(REFERENCE_THICKNESS_MM))
                    }.toImmutableList(),
            corners =
                persistentListOf(
                    CornerSpec.Unspecified,
                    CornerSpec.DeclaredRight(TurnDirection.LEFT),
                    CornerSpec.DeclaredRight(TurnDirection.LEFT),
                    CornerSpec.DeclaredRight(TurnDirection.LEFT),
                ),
            diagonals =
                if (withDiagonal) {
                    persistentListOf(Diagonal(DiagonalId("$idPrefix-d0"), 0, 2, Length(TRAPEZOID_DIAGONAL_MM)))
                } else {
                    persistentListOf()
                },
            isClosed = true,
        )
    }
}
