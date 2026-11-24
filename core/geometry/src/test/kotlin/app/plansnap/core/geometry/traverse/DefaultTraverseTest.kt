package app.plansnap.core.geometry.traverse

import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.geometry.primitives.Winding
import app.plansnap.testing.fixtures.rooms.ReferenceRoom
import app.plansnap.testing.fixtures.rooms.ReferenceRooms
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Named
import org.junit.jupiter.api.Test
import org.junit.jupiter.params.ParameterizedTest
import org.junit.jupiter.params.provider.MethodSource

class DefaultTraverseTest {
    private val traverse = DefaultTraverse()

    // region targets

    @Test
    fun `given counter-clockwise rectangle when targets computed then all turns are left quarter turns`() {
        val targets = traverse.targets(rightAngled(4000, 3000, 4000, 3000))
        assertEquals(Winding.COUNTER_CLOCKWISE, targets.winding)
        assertEquals(List(4) { Angle.RIGHT }, targets.targetTurns)
    }

    @Test
    fun `given clockwise rectangle when targets computed then all turns are right quarter turns`() {
        val targets = traverse.targets(rightAngled(4000, 3000, 4000, 3000, direction = TurnDirection.RIGHT))
        assertEquals(Winding.CLOCKWISE, targets.winding)
        assertEquals(List(4) { -Angle.RIGHT }, targets.targetTurns)
    }

    @Test
    fun `given inner turns summing to zero when targets computed then winding is counter-clockwise`() {
        val input =
            ShapeInput(
                listOf(1000, 1000, 1000, 1000),
                listOf(CornerInput.Free, turnCorner(90_000), turnCorner(-90_000), turnCorner(0)),
            )
        val targets = traverse.targets(input)
        assertEquals(Winding.COUNTER_CLOCKWISE, targets.winding)
        assertEquals(listOf(0, 90_000, -90_000, 0), targets.targetTurns.map { it.milliDegrees })
    }

    @ParameterizedTest(name = "inner turn {0} gives closing target {1}")
    @MethodSource("freeClosingCases")
    fun `given free closing corner when targets computed then nearest candidate with tie rule wins`(
        innerTurnMilliDegrees: Int,
        expectedClosingMilliDegrees: Int,
    ) {
        val input =
            ShapeInput(
                lengthsMm = List(4) { 1000L },
                corners = listOf(CornerInput.Free) + List(3) { turnCorner(innerTurnMilliDegrees) },
            )
        assertEquals(Angle(expectedClosingMilliDegrees), traverse.targets(input).targetTurns.first())
    }

    @Test
    fun `given two inner quarter turns when targets computed then remainder of 180 degrees resolves by winding`() {
        val left = rightAngled(1000, 1000, 1000)
        assertEquals(Angle.RIGHT, traverse.targets(left).targetTurns.first())
        val right = rightAngled(1000, 1000, 1000, direction = TurnDirection.RIGHT)
        assertEquals(-Angle.RIGHT, traverse.targets(right).targetTurns.first())
    }

    @Test
    fun `given fixed closing corner when targets computed then its entered turn is the target`() {
        val measured =
            rightAngled(4000, 3000, 4000, 3000, closing = CornerInput.Measured(TurnDirection.RIGHT, Angle(100_000)))
        assertEquals(Angle(-80_000), traverse.targets(measured).targetTurns.first())
        val declared = rightAngled(4000, 3000, 4000, 3000, closing = CornerInput.DeclaredRight(TurnDirection.LEFT))
        assertEquals(Angle.RIGHT, traverse.targets(declared).targetTurns.first())
    }

    // endregion

    // region walk

    @Test
    fun `given no walls when walked then only the origin is returned`() {
        assertEquals(listOf(Vec2.ZERO), traverse.walk(emptyList(), emptyList()))
    }

    @Test
    fun `given mismatching sizes when walked then it throws`() {
        assertThrows(IllegalArgumentException::class.java) { traverse.walk(listOf(1000.0), emptyList()) }
    }

    @Test
    fun `given turn at index zero when walked then it is ignored and the first wall points along x`() {
        val points = traverse.walk(listOf(1500.0), listOf(1.0))
        assertEquals(listOf(Vec2.ZERO, Vec2(1500.0, 0.0)), points)
    }

    @Test
    fun `given quarter turns when walked then rectangle points are reached`() {
        val points = traverse.walk(listOf(4000.0, 3000.0, 4000.0, 3000.0), List(4) { HALF_PI })
        val expected = listOf(Vec2.ZERO, Vec2(4000.0, 0.0), Vec2(4000.0, 3000.0), Vec2(0.0, 3000.0), Vec2.ZERO)
        assertPointsClose(expected, points, WALK_TOLERANCE_MM)
    }

    @Test
    fun `given 3-4-5 triangle turns when walked then the general angle traverse closes`() {
        val turnAtV2 = StrictMath.PI - StrictMath.atan2(3.0, 4.0)
        val points = traverse.walk(listOf(3000.0, 4000.0, 5000.0), listOf(0.0, HALF_PI, turnAtV2))
        val expected = listOf(Vec2.ZERO, Vec2(3000.0, 0.0), Vec2(3000.0, 4000.0), Vec2.ZERO)
        assertPointsClose(expected, points, WALK_TOLERANCE_MM)
    }

    @Test
    fun `given fractional lengths when walked then they are used without rounding`() {
        val points = traverse.walk(listOf(0.25, 0.5), listOf(0.0, -HALF_PI))
        assertPointsClose(listOf(Vec2.ZERO, Vec2(0.25, 0.0), Vec2(0.25, -0.5)), points, WALK_TOLERANCE_MM)
    }

    // endregion

    // region closure error

    @ParameterizedTest(name = "{0}")
    @MethodSource("determinedReferenceRooms")
    fun `given determined reference room when closure computed then target shape matches and closes`(
        reference: ReferenceRoom,
    ) {
        val input = reference.room.toShapeInput()
        val error = traverse.closureError(input)
        val points = traverse.targetPoints(input, traverse.targets(input))
        // The reference frame may place V_0 away from the origin, w_0 always points along +x.
        val origin = reference.vertices.first()
        assertPointsClose(reference.vertices, points.dropLast(1).map { it + origin }, REFERENCE_VERTEX_TOLERANCE_MM)
        assertTrue(error.errorMm <= GeometryTolerances.EXACT_CLOSURE_MM, "error ${error.errorMm}")
        assertEquals(ClosureClass.EXACT, traverse.classify(input, error, ClosureTolerance()))
        assertEquals(reference.expectedPerimeterMm, input.lengthsMm.sum().toDouble(), 0.0)
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("rightAngledReferenceRooms")
    fun `given right angled reference room when closure computed then gap is exactly zero`(reference: ReferenceRoom) {
        val error = traverse.closureError(reference.room.toShapeInput())
        assertEquals(0.0.toRawBits(), error.gapMm.toRawBits())
        assertEquals(0.0.toRawBits(), error.closingAngleResidualMm.toRawBits())
    }

    @Test
    fun `given trapezoid 3-3-3-5 with right angles when closure computed then gap is two meters`() {
        val input = ReferenceRooms.TRAPEZOID_3_3_3_5.room.toShapeInput()
        val error = traverse.closureError(input)
        assertEquals(TRAPEZOID_GAP_MM, error.gapMm, ANALYTIC_TOLERANCE_MM)
        assertEquals(0.0, error.maxDiagonalResidualMm, 0.0)
        assertEquals(0.0, error.closingAngleResidualMm, 0.0)
        assertEquals(ClosureClass.SHAPE_DEVIATION, traverse.classify(input, error, ClosureTolerance()))
    }

    @Test
    fun `given trapezoid with diagonal when closure computed then residual is against the target rectangle`() {
        val input = ReferenceRooms.TRAPEZOID_WITH_DIAGONAL.room.toShapeInput()
        val error = traverse.closureError(input)
        // Target corners V_0 = (0, 0) and V_2 = (5000, 3000), so d = sqrt(34 000 000) mm against 4899 mm.
        assertEquals(StrictMath.sqrt(34_000_000.0) - 4899.0, error.maxDiagonalResidualMm, ANALYTIC_TOLERANCE_MM)
        assertEquals(TRAPEZOID_GAP_MM, error.errorMm, ANALYTIC_TOLERANCE_MM)
    }

    @Test
    fun `given several diagonals when closure computed then the largest residual is reported`() {
        val input =
            rightAngled(
                4000,
                3000,
                4000,
                3000,
                diagonals = listOf(DiagonalInput(0, 2, 5010), DiagonalInput(1, 3, 4970), DiagonalInput(3, 0, 3000)),
            )
        val error = traverse.closureError(input)
        assertEquals(30.0, error.maxDiagonalResidualMm, ANALYTIC_TOLERANCE_MM)
        assertEquals(30.0, error.errorMm, ANALYTIC_TOLERANCE_MM)
    }

    @Test
    fun `given diagonal shorter than target when closure computed then residual is the absolute difference`() {
        val input = rightAngled(4000, 3000, 4000, 3000, diagonals = listOf(DiagonalInput(2, 0, 4990)))
        assertEquals(10.0, traverse.closureError(input).maxDiagonalResidualMm, ANALYTIC_TOLERANCE_MM)
    }

    @Test
    fun `given room with 3 cm gap and 14 m perimeter when classified then it is a measurement error`() {
        val input = rightAngled(4000, 3000, 3970, 3000)
        val error = traverse.closureError(input)
        assertEquals(30.0, error.gapMm, ANALYTIC_TOLERANCE_MM)
        assertEquals(ClosureClass.MEASUREMENT_ERROR, traverse.classify(input, error, ClosureTolerance()))
    }

    @Test
    fun `given oblique gap when closure computed then gap is the Euclidean distance`() {
        // Rectangle walls 4000 / 3000 / 3970 / 2960 leave the chain end at (30, 40).
        val error = traverse.closureError(rightAngled(4000, 3000, 3970, 2960))
        assertEquals(50.0, error.gapMm, ANALYTIC_TOLERANCE_MM)
    }

    @Test
    fun `given measured general angles when closure computed then equilateral triangle closes`() {
        val input = ShapeInput(List(3) { 2000L }, listOf(CornerInput.Free, turnCorner(120_000), turnCorner(120_000)))
        val error = traverse.closureError(input)
        // The free closing target is the candidate nearest to r = 120°, which does not affect the gap.
        assertEquals(Angle.RIGHT, traverse.targets(input).targetTurns.first())
        assertEquals(0.0, error.gapMm, ANALYTIC_TOLERANCE_MM)
        val points = traverse.targetPoints(input, traverse.targets(input))
        val expected = listOf(Vec2.ZERO, Vec2(2000.0, 0.0), Vec2(1000.0, StrictMath.sqrt(3.0) * 1000.0), Vec2.ZERO)
        assertPointsClose(expected, points, ANALYTIC_TOLERANCE_MM)
    }

    @Test
    fun `given closing corner deviating by one degree when closure computed then angle term is converted to mm`() {
        val input =
            rightAngled(4000, 3000, 4000, 3000, closing = CornerInput.Measured(TurnDirection.LEFT, Angle(89_000)))
        val error = traverse.closureError(input)
        assertEquals(14_000.0 / 360.0, error.closingAngleResidualMm, ANALYTIC_TOLERANCE_MM)
        assertEquals(0.0, error.gapMm, ANALYTIC_TOLERANCE_MM)
    }

    @Test
    fun `given agreeing fixed closing corner when closure computed then angle term is zero`() {
        val input = rightAngled(4000, 3000, 4000, 3000, closing = CornerInput.DeclaredRight(TurnDirection.LEFT))
        assertEquals(0.0, traverse.closureError(input).closingAngleResidualMm, 0.0)
    }

    @Test
    fun `given closing deviation beyond 180 degrees when closure computed then it is wrapped`() {
        // Inner turns 10° each, so the derived closing turn is 330°. A straight closing corner (0°) differs by 30°.
        val input =
            ShapeInput(
                lengthsMm = List(4) { 1000L },
                corners =
                    listOf(CornerInput.Measured(TurnDirection.LEFT, Angle.STRAIGHT)) + List(3) { turnCorner(10_000) },
            )
        assertEquals(4000.0 * 30.0 / 360.0, traverse.closureError(input).closingAngleResidualMm, ANALYTIC_TOLERANCE_MM)
    }

    @Test
    fun `given clockwise room with deviating closing corner when closure computed then derived turn uses winding`() {
        val closing = CornerInput.Measured(TurnDirection.RIGHT, Angle(92_000))
        val input = rightAngled(4000, 3000, 4000, 3000, direction = TurnDirection.RIGHT, closing = closing)
        assertEquals(14_000.0 * 2.0 / 360.0, traverse.closureError(input).closingAngleResidualMm, ANALYTIC_TOLERANCE_MM)
    }

    // endregion

    // region classify

    @Test
    fun `given error exactly at exact closure limit when classified then it is exact`() {
        val input = rightAngled(4000, 3000, 4000, 3000)
        val limit = GeometryTolerances.EXACT_CLOSURE_MM
        assertEquals(ClosureClass.EXACT, classifyGap(input, limit, ClosureTolerance()))
        assertEquals(ClosureClass.EXACT, classifyGap(input, Math.nextDown(limit), ClosureTolerance()))
        assertEquals(ClosureClass.MEASUREMENT_ERROR, classifyGap(input, Math.nextUp(limit), ClosureTolerance()))
    }

    @Test
    fun `given error around relative limit when classified then the limit itself is a measurement error`() {
        val input = rightAngled(4000, 3000, 4000, 3000)
        val tolerance = ClosureTolerance(absoluteMm = 50, relative = 0.5)
        val limit = 7000.0
        assertEquals(ClosureClass.MEASUREMENT_ERROR, classifyGap(input, Math.nextDown(limit), tolerance))
        assertEquals(ClosureClass.MEASUREMENT_ERROR, classifyGap(input, limit, tolerance))
        assertEquals(ClosureClass.SHAPE_DEVIATION, classifyGap(input, Math.nextUp(limit), tolerance))
    }

    @Test
    fun `given error around absolute limit when classified then the absolute part dominates`() {
        val input = rightAngled(4000, 3000, 4000, 3000)
        val tolerance = ClosureTolerance(absoluteMm = 200, relative = 0.01)
        assertEquals(ClosureClass.MEASUREMENT_ERROR, classifyGap(input, Math.nextDown(200.0), tolerance))
        assertEquals(ClosureClass.MEASUREMENT_ERROR, classifyGap(input, 200.0, tolerance))
        assertEquals(ClosureClass.SHAPE_DEVIATION, classifyGap(input, Math.nextUp(200.0), tolerance))
    }

    @Test
    fun `given default tolerance when classified then limit grows with the raw perimeter`() {
        val error = ClosureError(gapMm = 3000.0, maxDiagonalResidualMm = 0.0, closingAngleResidualMm = 0.0)
        val small = ReferenceRooms.RECTANGLE.room.toShapeInput()
        val huge = ReferenceRooms.HUGE.room.toShapeInput()
        assertEquals(ClosureClass.SHAPE_DEVIATION, traverse.classify(small, error, ClosureTolerance()))
        assertEquals(ClosureClass.MEASUREMENT_ERROR, traverse.classify(huge, error, ClosureTolerance()))
    }

    @Test
    fun `given each error component alone when classified then it drives the class`() {
        val input = rightAngled(4000, 3000, 4000, 3000)
        val tolerance = ClosureTolerance()
        val diagonalOnly = ClosureError(gapMm = 0.0, maxDiagonalResidualMm = 2.0, closingAngleResidualMm = 0.0)
        val angleOnly = ClosureError(gapMm = 0.0, maxDiagonalResidualMm = 0.0, closingAngleResidualMm = 141.0)
        assertEquals(ClosureClass.MEASUREMENT_ERROR, traverse.classify(input, diagonalOnly, tolerance))
        assertEquals(ClosureClass.SHAPE_DEVIATION, traverse.classify(input, angleOnly, tolerance))
    }

    // endregion

    private fun classifyGap(
        input: ShapeInput,
        gapMm: Double,
        tolerance: ClosureTolerance,
    ): ClosureClass =
        traverse.classify(
            input,
            ClosureError(gapMm = gapMm, maxDiagonalResidualMm = 0.0, closingAngleResidualMm = 0.0),
            tolerance,
        )

    private fun assertPointsClose(
        expected: List<Vec2>,
        actual: List<Vec2>,
        tolerance: Double,
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEach { (e, a) -> assertTrue(e.isCloseTo(a, tolerance), "expected $e but was $a") }
    }

    private companion object {
        const val HALF_PI = StrictMath.PI / 2.0
        const val WALK_TOLERANCE_MM = 1e-9
        const val ANALYTIC_TOLERANCE_MM = 1e-9

        /** Rounding of measured angles to thousandths of a degree moves a corner by far less than this. */
        const val REFERENCE_VERTEX_TOLERANCE_MM = 0.1
        const val TRAPEZOID_GAP_MM = 2000.0

        @JvmStatic
        fun freeClosingCases(): List<Array<Int>> =
            listOf(
                // s = +1 and r = 360° − 3τ.
                arrayOf(90_000, 90_000), // r = 90°
                arrayOf(135_000, 0), // r = −45°, tie of −90° and 0° goes to 0°
                arrayOf(105_000, 90_000), // r = 45°, tie of 0° and 90° goes to 90°
                arrayOf(100_000, 90_000), // r = 60°
                arrayOf(140_000, -90_000), // r = −60°
                arrayOf(10_000, 0), // r = 330° ≡ −30°
                arrayOf(50_000, -90_000), // r = 210° ≡ −150°
                arrayOf(70_000, 90_000), // r = 150°
                arrayOf(60_000, 90_000), // r = 180°, tie of −90° and 90° goes to s · 90°
                // s = −1 and r = −360° − 3τ.
                arrayOf(-135_000, 0), // r = 45°, tie of 0° and 90° goes to 0°
                arrayOf(-105_000, -90_000), // r = −45°, tie of −90° and 0° goes to −90°
                arrayOf(-90_000, -90_000), // r = −90°
                arrayOf(-60_000, -90_000), // r = −180° ≡ 180°, tie goes to s · 90°
            )

        @JvmStatic
        fun determinedReferenceRooms(): List<Named<ReferenceRoom>> =
            ReferenceRooms.ALL.filter { it.inputDeterminesShape }.map { Named.of(it.key, it) }

        @JvmStatic
        fun rightAngledReferenceRooms(): List<Named<ReferenceRoom>> =
            ReferenceRooms.ALL
                .filter { reference ->
                    reference.inputDeterminesShape &&
                        reference.room
                            .toShapeInput()
                            .corners
                            .none { it is CornerInput.Measured }
                }.map { Named.of(it.key, it) }
    }
}
