package app.plansnap.feature.editor.details

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import app.plansnap.feature.editor.shape.evaluate
import app.plansnap.feature.editor.shape.impossibleRoom
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.underdeterminedRoom
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class AreaOverviewTest {
    private fun rect(
        id: String,
        w: Long = 4000,
        h: Long = 3000,
    ): Room = roomOf(w, h, w, h, closed = true).copy(id = RoomId(id), name = id)

    private fun stateWith(vararg rooms: Room): EditorUiState {
        val base = detailsState()
        return base.copy(
            project = base.project!!.copy(rooms = persistentListOf(*rooms)),
            evaluations = rooms.associate { it.id to evaluate(it) }.toImmutableMap(),
        )
    }

    private fun overview(state: EditorUiState) = checkNotNull(areaOverviewOf(state))

    @Test
    fun `given no project when the overview is built then there is none`() {
        assertNull(areaOverviewOf(EditorUiState()))
    }

    @Test
    fun `given a rectangle when built then area perimeter factor and volume are the unrounded values`() {
        val row = overview(stateWith(rect("a").copy(areaFactor = AreaFactor(50)))).rows.single()
        val metrics = checkNotNull(row.metrics)
        assertEquals(12_000_000.0, metrics.areaMm2, 1e-3)
        assertEquals(6_000_000.0, metrics.creditedAreaMm2, 1e-3)
        assertEquals(14_000.0, metrics.perimeterMm, 1e-6)
        assertEquals(12_000_000.0 * 2500, metrics.volumeMm3, 1.0)
        assertEquals(AreaFactor(50), row.factor)
    }

    @Test
    fun `given a room height when built then the volume uses it instead of the project default`() {
        val metrics =
            checkNotNull(overview(stateWith(rect("a").copy(heightOverride = Length(2000)))).rows.single().metrics)
        assertEquals(12_000_000.0 * 2000, metrics.volumeMm3, 1.0)
    }

    @Test
    fun `given several rooms when built then the totals are the sums of the unrounded values`() {
        val result = overview(stateWith(rect("a"), rect("b", 5000, 2000), rect("c").copy(areaFactor = AreaFactor(0))))
        assertEquals(12_000_000.0 + 10_000_000.0 + 12_000_000.0, result.totals.areaMm2, 1e-3)
        assertEquals(12_000_000.0 + 10_000_000.0, result.totals.creditedAreaMm2, 1e-3)
        assertEquals(0, result.omitted)
        assertEquals(listOf("a", "b", "c"), result.rows.map { it.name })
    }

    @Test
    fun `given an open and a conflicting room when built then they have no metrics and are not in the totals`() {
        val open = roomOf(4000, 3000).copy(id = RoomId("open"))
        val conflict = impossibleRoom().copy(id = RoomId("conflict"))
        val result = overview(stateWith(rect("a"), open, conflict))
        assertNotNull(result.rows[0].metrics)
        assertNull(result.rows[1].metrics)
        assertNull(result.rows[2].metrics)
        assertEquals(ShapeStatus.OPEN, result.rows[1].status)
        assertEquals(ShapeStatus.CONFLICT, result.rows[2].status)
        assertEquals(2, result.omitted)
        assertEquals(12_000_000.0, result.totals.areaMm2, 1e-3)
    }

    @Test
    fun `given a room without an evaluation when built then it is pending and the others still count`() {
        val base = stateWith(rect("a"), rect("b"))
        val state = base.copy(evaluations = base.evaluations.filterKeys { it.value == "a" }.toImmutableMap())
        val result = overview(state)
        assertNull(result.rows[1].status)
        assertNull(result.rows[1].metrics)
        assertTrue(result.rows[1].pending)
        assertFalse(result.rows[0].pending)
        assertEquals(1, result.omitted)
        assertEquals(12_000_000.0, result.totals.areaMm2, 1e-3)
    }

    @Test
    fun `given a room whose evaluation failed when built then it has no shape and is not pending`() {
        val base = stateWith(rect("a"), rect("b"))
        val state =
            base.copy(
                evaluations = base.evaluations.filterKeys { it.value == "a" }.toImmutableMap(),
                evaluationErrors =
                    mapOf(RoomId("b") to DomainError.RuleViolation(DomainRule.TOO_FEW_WALLS)).toImmutableMap(),
            )
        val row = overview(state).rows[1]
        assertNull(row.metrics)
        assertFalse(row.pending)
    }

    @Test
    fun `given a proposed shape when built then the room counts with its displayed shape`() {
        val room = underdeterminedRoom()
        val evaluation = evaluate(room)
        assertTrue(evaluation is RoomEvaluation.Proposed)
        val result = overview(stateWith(room))
        assertEquals(ShapeStatus.PROPOSED, result.rows.single().status)
        assertNotNull(result.rows.single().metrics)
    }

    @Test
    fun `given a value that is half a hundredth when formatted then it rounds up only at the display`() {
        // 2,675 m² is exactly representable in mm² (2 675 000), and sums are rounded after adding.
        assertEquals("2.68", DetailsFormat.area(2_675_000.0, Locale.US))
        assertEquals("2.67", DetailsFormat.area(2_674_999.0, Locale.US))
        assertEquals("0.01", DetailsFormat.area(5_000.0, Locale.US))
        assertEquals("0.00", DetailsFormat.area(4_999.0, Locale.US))
    }

    @Test
    fun `given the german locale when formatted then the comma is the decimal separator`() {
        assertEquals("11,31", DetailsFormat.area(11_310_000.0, Locale.GERMANY))
        assertEquals("12,00", DetailsFormat.area(12_000_000.0, Locale.GERMANY))
        assertEquals("30,00", DetailsFormat.volume(30_000_000_000.0, Locale.GERMANY))
    }

    @Test
    fun `given rows that round apart when summed then the total is the rounded sum of unrounded values`() {
        val first = 1_004_000.0
        val second = 1_004_000.0
        assertEquals("1.00", DetailsFormat.area(first, Locale.US))
        assertEquals("2.01", DetailsFormat.area(first + second, Locale.US))
    }
}
