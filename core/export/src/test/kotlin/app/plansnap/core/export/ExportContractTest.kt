package app.plansnap.core.export

import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.geometry.primitives.Vec2
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningStatus
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class ExportContractTest {
    private val roomA = RoomId("a")
    private val roomB = RoomId("b")
    private val plan =
        PlotPlan(PaperSize.A4, PageOrientation.PORTRAIT, PlotScale.ONE_TO_50, WorldBounds(0.0, 0.0, 1.0, 1.0))
    private val project =
        Project(ProjectId("p"), "Flat", Length(2500), persistentListOf(Room(roomA)), 1L, 2L)
    private val evaluation =
        RoomEvaluation.Open(roomA, persistentListOf(Vec2.ZERO), persistentMapOf<OpeningId, OpeningStatus>())

    private fun assertRejected(block: () -> Unit) {
        assertThrows(IllegalArgumentException::class.java) { block() }
    }

    @Test
    fun `given formats when listed then all required outputs exist`() {
        assertEquals(listOf("PDF", "PNG", "SVG", "JSON"), ExportFormat.entries.map { it.name })
    }

    @Test
    fun `given request when a room has no evaluation then it is rejected`() {
        val request =
            ExportRequest(
                project,
                persistentMapOf(roomA to evaluation),
                ExportFormat.SVG,
                LengthUnit.METER,
                Locale.GERMANY,
            )
        assertFalse(request.includeNotes)
        assertEquals(LengthUnit.METER, request.lengthUnit)
        assertEquals(Locale.GERMANY, request.locale)
        assertRejected {
            ExportRequest(project, persistentMapOf(), ExportFormat.JSON, LengthUnit.METER, Locale.GERMANY)
        }
    }

    @Test
    fun `given summary when plan and format disagree then it is rejected`() {
        assertEquals(plan, ExportSummary(ExportFormat.PDF, plan, persistentSetOf(roomA)).plan)
        assertEquals(null, ExportSummary(ExportFormat.JSON, null, persistentSetOf()).plan)
        assertRejected { ExportSummary(ExportFormat.PDF, null, persistentSetOf()) }
        assertRejected { ExportSummary(ExportFormat.JSON, plan, persistentSetOf()) }
        assertRejected { ExportSummary(ExportFormat.JSON, null, persistentSetOf(roomA)) }
    }

    @Test
    fun `given preflight when room sets are inconsistent then it is rejected`() {
        val none = persistentSetOf<RoomId>()
        val noOpenings = persistentSetOf<RoomOpeningRef>()
        assertFalse(
            ExportPreflightReport(persistentSetOf(roomA), none, persistentSetOf(roomA), noOpenings).isDrawingEmpty,
        )
        assertTrue(ExportPreflightReport(none, persistentSetOf(roomB), none, noOpenings).isDrawingEmpty)
        assertRejected { ExportPreflightReport(persistentSetOf(roomA), persistentSetOf(roomA), none, noOpenings) }
        assertRejected { ExportPreflightReport(persistentSetOf(roomA), none, persistentSetOf(roomB), noOpenings) }
    }

    @Test
    fun `given equal opening ids in two rooms when reported then both findings stay distinct`() {
        val door = OpeningId("door")
        val findings = persistentSetOf(RoomOpeningRef(roomA, door), RoomOpeningRef(roomB, door))
        val report =
            ExportPreflightReport(persistentSetOf(roomA, roomB), persistentSetOf(), persistentSetOf(), findings)
        assertEquals(2, report.outOfBoundsOpenings.size)
    }
}
