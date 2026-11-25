package app.plansnap.core.drawing.scene

import app.plansnap.core.drawing.scene.SceneFixtures.evaluate
import app.plansnap.core.model.Room
import app.plansnap.core.model.ShapeStatus
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.DynamicTest
import org.junit.jupiter.api.TestFactory

/** Table tests of the status marking (DOMAIN section 4, UX section 3, REQ-EXP-006). */
class DefaultSceneBuilderStatusTest {
    private val builder = DefaultSceneBuilder()

    /**
     * Expected marking per status.
     *
     * @property bodyStyle style of the room body fill, `null` if the room has no body.
     * @property wallLineStyle style of the walls drawn as lines, `null` if all walls are bands.
     * @property areaStyle style of the area label, `null` without area.
     */
    private data class Row(
        val room: Room,
        val status: ShapeStatus,
        val bodyStyle: SceneStyle?,
        val wallLineStyle: SceneStyle?,
        val areaStyle: SceneStyle?,
        val marked: Boolean,
        val exportable: Boolean,
    )

    private val rows =
        listOf(
            Row(
                SceneFixtures.confirmed,
                ShapeStatus.CONFIRMED,
                SceneStyle.ROOM_BODY,
                null,
                SceneStyle.LABEL,
                false,
                true,
            ),
            Row(
                SceneFixtures.needsAdjustment,
                ShapeStatus.NEEDS_ADJUSTMENT,
                SceneStyle.ROOM_BODY_UNCONFIRMED,
                null,
                SceneStyle.WARNING,
                true,
                true,
            ),
            Row(
                SceneFixtures.adjusted,
                ShapeStatus.ADJUSTED,
                SceneStyle.ROOM_BODY,
                null,
                SceneStyle.LABEL,
                false,
                true,
            ),
            Row(
                SceneFixtures.proposed,
                ShapeStatus.PROPOSED,
                SceneStyle.ROOM_BODY_UNCONFIRMED,
                null,
                SceneStyle.WARNING,
                true,
                true,
            ),
            Row(SceneFixtures.open, ShapeStatus.OPEN, null, SceneStyle.WALL_LINE, null, false, false),
            Row(SceneFixtures.conflict, ShapeStatus.CONFLICT, null, SceneStyle.CONFLICT, null, false, false),
        )

    @TestFactory
    fun `given each status when built then the scene carries its marking`(): List<DynamicTest> =
        rows.map { row ->
            DynamicTest.dynamicTest(row.status.name) {
                val evaluation = evaluate(row.room)
                assertEquals(row.status, evaluation.status)
                val scene = builder.buildRoom(row.room, evaluation, SceneOptions())
                assertEquals(row.status, scene.status)
                val bodies = scene.elements.filter { it.target is SceneTarget.RoomBody && it is SceneElement.Fill }
                assertEquals(listOfNotNull(row.bodyStyle), bodies.map { it.style })
                val wallLines =
                    scene.elements.filter { it.target is SceneTarget.Wall && it is SceneElement.Polyline }
                val wallStyles = wallLines.filter { it.style != SceneStyle.DIMENSION }.map { it.style }.toSet()
                assertEquals(setOfNotNull(row.wallLineStyle), wallStyles)
                val labels = scene.elements.filterIsInstance<SceneElement.Label>()
                val area = labels.filter { it.text is LabelText.Area }
                assertEquals(listOfNotNull(row.areaStyle), area.map { it.style })
                val marks = labels.filter { it.text == LabelText.ShapeNotConfirmed }
                assertEquals(
                    if (row.marked) listOf(SceneStyle.WARNING) else emptyList<SceneStyle>(),
                    marks.map { it.style },
                )
                assertTrue(marks.all { it.target == SceneTarget.RoomBody(row.room.id) })
                val conflictMarks = labels.filter { it.text == LabelText.ShapeConflict }
                assertEquals(
                    if (row.status == ShapeStatus.CONFLICT) listOf(SceneStyle.CONFLICT) else emptyList<SceneStyle>(),
                    conflictMarks.map { it.style },
                )
                assertTrue(conflictMarks.all { it.target == SceneTarget.RoomBody(row.room.id) })
                val corners = scene.elements.filter { it.target is SceneTarget.Corner && it is SceneElement.Polyline }
                assertEquals(
                    row.room.walls.indices
                        .toList(),
                    corners.map { (it.target as SceneTarget.Corner).index },
                )
            }
        }

    @TestFactory
    fun `given each status when built for export then only exportable rooms are drawn`(): List<DynamicTest> =
        rows.map { row ->
            DynamicTest.dynamicTest(row.status.name) {
                val scene = builder.buildRoom(row.room, evaluate(row.room), SceneOptions(exportableOnly = true))
                assertEquals(row.exportable, scene.elements.isNotEmpty())
                if (!row.exportable) assertNull(scene.bounds)
            }
        }
}
