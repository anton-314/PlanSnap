package app.plansnap.feature.editor.opening

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Length
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.geometry.primitives.GeometryTolerances
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomPlacement
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.shape.SHAPE_ROOM
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.testing.fixtures.rooms.OverdeterminedCases
import kotlinx.collections.immutable.persistentListOf
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class OpeningHandlerTest {
    private var previous: Locale = Locale.getDefault()

    @Before
    fun useEnglishNumbers() {
        previous = Locale.getDefault()
        Locale.setDefault(Locale.US)
    }

    @After
    fun restoreLocale() {
        Locale.setDefault(previous)
    }

    private val w0 = WallId("w0")
    private val w1 = WallId("w1")

    private fun harness(vararg openings: Opening) = OpeningHarness(stateOf(openingRoom(*openings)))

    private fun addDoor(harness: OpeningHarness) = harness.send(OpeningIntent.Add(SHAPE_ROOM, w0, OpeningType.DOOR))

    @Test
    fun `given another intent when handled then it is not consumed`() {
        val harness = harness()
        assertFalse(harness.send(CoreIntent.Undo))
        assertTrue(harness.send(OpeningIntent.Dismiss))
    }

    @Test
    fun `given a wall when a door is added then the form has usual values and no conflict`() {
        val harness = harness()
        addDoor(harness)
        val draft = harness.form.draft
        assertNull(harness.form.editingId)
        assertEquals(OpeningType.DOOR, draft.type)
        assertEquals("0", draft.offset)
        assertEquals("90", draft.width)
        assertEquals("", draft.height)
        assertEquals("", draft.sill)
        assertNull(draft.swing)
        assertTrue(harness.opening.canConfirm)
    }

    @Test
    fun `given a wall with an opening when one is added then it starts behind the last opening`() {
        val harness = harness(door("o1", "w0", 500, 900))
        addDoor(harness)
        assertEquals("140", harness.form.draft.offset)
        assertTrue(harness.opening.canConfirm)
    }

    @Test
    fun `given a door when it is stored then AddOpening carries a new id and the typed values`() {
        val harness = harness()
        addDoor(harness)
        harness.send(
            OpeningIntent.OffsetChanged("30"),
            OpeningIntent.WidthChanged("80,5"),
            OpeningIntent.HeightChanged(""),
        )
        harness.send(OpeningIntent.SwingChanged(DoorSwing.HINGE_RIGHT_OUTWARD), OpeningIntent.Confirm)
        val edit = harness.submitted.single() as ProjectEdit.AddOpening
        assertEquals(SHAPE_ROOM, edit.roomId)
        assertEquals(OpeningId("gen-1"), edit.opening.id)
        assertEquals(w0, edit.opening.wallId)
        assertEquals(Length(300), edit.opening.offset)
        assertEquals(Length(805), edit.opening.width)
        assertNull(edit.opening.height)
        assertEquals(DoorSwing.HINGE_RIGHT_OUTWARD, edit.opening.swing)
        assertNull(harness.opening.sheet)
    }

    @Test
    fun `given a window when it is added then it has a sill height and no swing`() {
        val harness = harness()
        harness.send(OpeningIntent.Add(SHAPE_ROOM, w1, OpeningType.WINDOW), OpeningIntent.SillChanged("95"))
        harness.send(OpeningIntent.Confirm)
        val opening = (harness.submitted.single() as ProjectEdit.AddOpening).opening
        assertEquals(OpeningType.WINDOW, opening.type)
        assertEquals(Length(950), opening.sillHeight)
        assertEquals(Length(1200), opening.width)
        assertNull(opening.swing)
    }

    @Test
    fun `given a passage when it is added then it has neither sill nor swing`() {
        val harness = harness()
        harness.send(OpeningIntent.Add(SHAPE_ROOM, w0, OpeningType.PASSAGE), OpeningIntent.Confirm)
        val opening = (harness.submitted.single() as ProjectEdit.AddOpening).opening
        assertEquals(OpeningType.PASSAGE, opening.type)
        assertNull(opening.sillHeight)
        assertNull(opening.swing)
    }

    @Test
    fun `given a new opening without optional values when stored then height, sill and swing stay empty`() {
        OpeningType.entries.forEach { type ->
            val harness = harness()
            harness.send(OpeningIntent.Add(SHAPE_ROOM, w0, type), OpeningIntent.Confirm)
            val opening = (harness.submitted.single() as ProjectEdit.AddOpening).opening
            assertEquals(type, opening.type)
            assertNull(opening.height)
            assertNull(opening.sillHeight)
            assertNull(opening.swing)
        }
    }

    @Test
    fun `given a chosen swing when it is withdrawn then the door is stored without one`() {
        val harness = harness(door("o1", "w0", 0, 900))
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")), OpeningIntent.SwingChanged(null))
        harness.send(OpeningIntent.HeightChanged(""), OpeningIntent.Confirm)
        val opening = (harness.submitted.single() as ProjectEdit.EditOpening).opening
        assertNull(opening.swing)
        assertNull(opening.height)
    }

    @Test
    fun `given a new door with the usual width when the type changes then the width follows the type`() {
        val harness = harness()
        addDoor(harness)
        harness.send(OpeningIntent.TypeChanged(OpeningType.WINDOW))
        assertEquals("120", harness.form.draft.width)
        harness.send(OpeningIntent.TypeChanged(OpeningType.PASSAGE))
        assertEquals("100", harness.form.draft.width)
    }

    @Test
    fun `given typed values when the type changes back and forth then they are kept`() {
        val harness = harness()
        addDoor(harness)
        harness.send(
            OpeningIntent.WidthChanged("77"),
            OpeningIntent.HeightChanged("201"),
            OpeningIntent.SwingChanged(DoorSwing.HINGE_RIGHT_INWARD),
            OpeningIntent.TypeChanged(OpeningType.WINDOW),
            OpeningIntent.SillChanged("88"),
            OpeningIntent.TypeChanged(OpeningType.DOOR),
        )
        assertEquals("77", harness.form.draft.width)
        assertEquals("201", harness.form.draft.height)
        assertEquals(DoorSwing.HINGE_RIGHT_INWARD, harness.form.draft.swing)
        harness.send(OpeningIntent.TypeChanged(OpeningType.WINDOW))
        assertEquals("88", harness.form.draft.sill)
    }

    @Test
    fun `given a stored opening when its type changes then no measurement is changed or invented`() {
        val stored = door("o1", "w0", 0, 900).copy(height = null, swing = null)
        val harness = harness(stored)
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, stored.id), OpeningIntent.TypeChanged(OpeningType.WINDOW))
        harness.send(OpeningIntent.Confirm)
        val opening = (harness.submitted.single() as ProjectEdit.EditOpening).opening
        assertEquals(stored.copy(type = OpeningType.WINDOW), opening)
    }

    @Test
    fun `given a stored opening when the chosen type is tapped again then nothing changes`() {
        val stored = door("o1", "w0", 0, 900).copy(height = null, swing = null)
        val harness = harness(stored)
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, stored.id), OpeningIntent.TypeChanged(OpeningType.DOOR))
        harness.send(OpeningIntent.Confirm)
        assertTrue(harness.submitted.isEmpty())
    }

    @Test
    fun `given a selected room when an opening is added from the menu then its form is on the last wall`() {
        val harness = harness()
        harness.send(OpeningIntent.AddToSelectedRoom(OpeningType.WINDOW))
        assertEquals(WallId("w3"), harness.form.draft.wallId)
        assertEquals(OpeningType.WINDOW, harness.form.draft.type)
    }

    @Test
    fun `given no wall to add to when an opening is added from the menu then nothing opens`() {
        val harness = OpeningHarness(stateOf(roomOf()))
        harness.send(OpeningIntent.AddToSelectedRoom(OpeningType.DOOR))
        assertNull(harness.opening.sheet)
        val unselected = OpeningHarness(stateOf(openingRoom()) { copy(selectedRoomId = null) })
        unselected.send(OpeningIntent.AddToSelectedRoom(OpeningType.DOOR))
        assertNull(unselected.opening.sheet)
    }

    @Test
    fun `given a stored opening when it is edited then the form shows its values and changes are one EditOpening`() {
        val harness = harness(door("o1", "w0", 200, 900))
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")))
        assertEquals("20", harness.form.draft.offset)
        assertEquals("90", harness.form.draft.width)
        harness.send(OpeningIntent.WidthChanged("100"), OpeningIntent.WallChanged(w1), OpeningIntent.Confirm)
        val edit = harness.submitted.single() as ProjectEdit.EditOpening
        assertEquals(OpeningId("o1"), edit.opening.id)
        assertEquals(w1, edit.opening.wallId)
        assertEquals(Length(1000), edit.opening.width)
        assertEquals(Length(200), edit.opening.offset)
        assertNull(harness.opening.sheet)
    }

    @Test
    fun `given an unchanged opening when it is confirmed then no command is submitted`() {
        val harness = harness(door("o1", "w0", 200, 900))
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")), OpeningIntent.Confirm)
        assertTrue(harness.submitted.isEmpty())
        assertNull(harness.opening.sheet)
    }

    @Test
    fun `given an unknown opening or wall when opened then nothing opens`() {
        val harness = harness()
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, OpeningId("missing")))
        harness.send(OpeningIntent.OpenWall(SHAPE_ROOM, WallId("missing")))
        harness.send(OpeningIntent.Add(RoomId("other"), w0, OpeningType.DOOR))
        assertNull(harness.opening.sheet)
    }

    @Test
    fun `given a wall when it is opened then its sheet lists it`() {
        val harness = harness()
        harness.send(OpeningIntent.OpenWall(SHAPE_ROOM, w1))
        assertEquals(OpeningSheet.Wall(SHAPE_ROOM, w1), harness.opening.sheet)
        assertFalse(harness.opening.canConfirm)
    }

    @Test
    fun `given a door wider than the wall when typed then the conflict is shown and nothing is stored`() {
        val harness = harness()
        addDoor(harness)
        harness.send(OpeningIntent.WidthChanged("500"))
        assertEquals(DomainError.RuleViolation(DomainRule.OPENING_OUT_OF_BOUNDS), harness.opening.conflict)
        assertFalse(harness.opening.canConfirm)
        harness.send(OpeningIntent.Confirm)
        assertTrue(harness.submitted.isEmpty())
        assertEquals(DomainError.RuleViolation(DomainRule.OPENING_OUT_OF_BOUNDS), harness.opening.conflict)
        harness.send(OpeningIntent.WidthChanged("90"))
        assertNull(harness.opening.conflict)
        assertTrue(harness.opening.canConfirm)
    }

    @Test
    fun `given an opening on the wall when another overlaps it then the overlap is shown`() {
        val harness = harness(door("o1", "w0", 0, 900))
        addDoor(harness)
        harness.send(OpeningIntent.OffsetChanged("50"))
        assertEquals(DomainError.RuleViolation(DomainRule.OPENING_OVERLAP), harness.opening.conflict)
        harness.send(OpeningIntent.OffsetChanged("90"))
        assertNull(harness.opening.conflict)
    }

    @Test
    fun `given a stored opening when its own place is typed then it does not conflict with itself`() {
        val harness = harness(door("o1", "w0", 0, 900))
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")), OpeningIntent.OffsetChanged("10"))
        assertNull(harness.opening.conflict)
    }

    @Test
    fun `given an opening that no longer fits when edited then the conflict is shown at once`() {
        val harness = harness(door("o1", "w0", 3500, 900))
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")))
        assertEquals(DomainError.RuleViolation(DomainRule.OPENING_OUT_OF_BOUNDS), harness.opening.conflict)
        harness.send(OpeningIntent.OffsetChanged("310"))
        assertTrue(harness.opening.canConfirm)
    }

    @Test
    fun `given values that are no numbers when typed then the field is named and nothing is stored`() {
        val harness = harness()
        addDoor(harness)
        harness.send(OpeningIntent.OffsetChanged("abc"))
        assertEquals(
            DomainError.InvalidValue(ValueField.OPENING_OFFSET, InvalidValueReason.MALFORMED),
            harness.opening.valueError,
        )
        harness.send(OpeningIntent.OffsetChanged("0"), OpeningIntent.WidthChanged("0"))
        assertEquals(
            DomainError.InvalidValue(ValueField.OPENING_WIDTH, InvalidValueReason.TOO_SMALL),
            harness.opening.valueError,
        )
        harness.send(OpeningIntent.WidthChanged("90"), OpeningIntent.HeightChanged("0"))
        assertEquals(
            DomainError.InvalidValue(ValueField.OPENING_HEIGHT, InvalidValueReason.TOO_SMALL),
            harness.opening.valueError,
        )
        harness.send(OpeningIntent.HeightChanged("210"), OpeningIntent.WidthChanged("90,05"))
        assertEquals(
            DomainError.InvalidValue(ValueField.OPENING_WIDTH, InvalidValueReason.NOT_REPRESENTABLE),
            harness.opening.valueError,
        )
        harness.send(OpeningIntent.Confirm)
        assertTrue(harness.submitted.isEmpty())
    }

    @Test
    fun `given a window with a negative sill when typed then the sill is rejected`() {
        val harness = harness()
        harness.send(OpeningIntent.Add(SHAPE_ROOM, w0, OpeningType.WINDOW), OpeningIntent.SillChanged("-5"))
        assertEquals(
            DomainError.InvalidValue(ValueField.SILL_HEIGHT, InvalidValueReason.TOO_SMALL),
            harness.opening.valueError,
        )
    }

    @Test
    fun `given a value that overflows when typed then it is rejected instead of crashing`() {
        val harness = harness()
        addDoor(harness)
        val huge = "900000000000000000"
        harness.send(OpeningIntent.OffsetChanged(huge), OpeningIntent.WidthChanged(huge))
        assertEquals(
            DomainError.InvalidValue(ValueField.OPENING_OFFSET, InvalidValueReason.TOO_LARGE),
            harness.opening.valueError,
        )
        assertFalse(harness.opening.canConfirm)
    }

    @Test
    fun `given a shorter preview wall when a door fits the raw wall then it is accepted`() {
        // The raw length of the first wall is 4030 mm, the preview shows it shorter than the door reaches (ADR 0021).
        val room = OverdeterminedCases.GAP_30_MM.room
        val wall = room.walls.first().id
        val harness = OpeningHarness(stateOf(room))
        val evaluation =
            harness.host.state.value.evaluations
                .getValue(room.id)
        val shown = (evaluation as RoomEvaluation.NeedsAdjustment).shape.lengthsMm.first()
        val doorEndMm = 4031.0
        assertTrue("$shown", shown + GeometryTolerances.EXACT_CLOSURE_MM < doorEndMm)
        harness.send(OpeningIntent.Add(room.id, wall, OpeningType.DOOR))
        harness.send(OpeningIntent.OffsetChanged("320"), OpeningIntent.WidthChanged("83,1"))
        assertNull(harness.opening.conflict)
        assertTrue(harness.opening.canConfirm)
        harness.send(OpeningIntent.WidthChanged("83,2"))
        assertEquals(DomainError.RuleViolation(DomainRule.OPENING_OUT_OF_BOUNDS), harness.opening.conflict)
    }

    @Test
    fun `given a stored opening when it is deleted then the dialog comes first and RemoveOpening follows`() {
        val harness = harness(door("o1", "w0", 0, 900))
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, OpeningId("o1")), OpeningIntent.RequestDelete)
        assertTrue(harness.opening.confirmDelete)
        harness.send(OpeningIntent.CancelDelete)
        assertFalse(harness.opening.confirmDelete)
        assertTrue(harness.submitted.isEmpty())
        harness.send(OpeningIntent.RequestDelete, OpeningIntent.ConfirmDelete)
        assertEquals(listOf<ProjectEdit>(ProjectEdit.RemoveOpening(SHAPE_ROOM, OpeningId("o1"))), harness.submitted)
        assertNull(harness.opening.sheet)
        assertFalse(harness.opening.confirmDelete)
    }

    @Test
    fun `given a new opening when deletion is requested then nothing happens`() {
        val harness = harness()
        addDoor(harness)
        harness.send(OpeningIntent.RequestDelete, OpeningIntent.ConfirmDelete)
        assertFalse(harness.opening.confirmDelete)
        assertTrue(harness.submitted.isEmpty())
    }

    @Test
    fun `given a room docked to the opening when it moves to another wall then the rule is shown before storing`() {
        val opening = door("o1", "w0", 0, 900)
        val host = openingRoom(opening)
        val guest =
            roomOf(2000, 2000, 2000, 2000, closed = true).copy(
                id = RoomId("r2"),
                placement =
                    RoomPlacement(0.0, 0.0, 0.0, DockLink(SHAPE_ROOM, w0, WallId("w0"), Length.ZERO, opening.id)),
            )
        val state = stateOf(host).let { it.copy(project = it.project!!.copy(rooms = persistentListOf(host, guest))) }
        val harness = OpeningHarness(state)
        harness.send(OpeningIntent.Edit(SHAPE_ROOM, opening.id), OpeningIntent.WallChanged(w1))
        assertEquals(DomainError.RuleViolation(DomainRule.OPENING_HAS_DOCKED_ROOMS), harness.opening.conflict)
        harness.send(OpeningIntent.WallChanged(w0))
        assertNull(harness.opening.conflict)
    }

    @Test
    fun `given typing when changes are made then the domain is asked once per change and never for status`() {
        val harness = harness(door("o1", "w0", 0, 900))
        addDoor(harness)
        val afterOpening = harness.rules.checkCalls
        repeat(CHANGES) { harness.send(OpeningIntent.OffsetChanged("${100 + it}")) }
        assertEquals(afterOpening + CHANGES, harness.rules.checkCalls)
        assertEquals(0, harness.rules.statusCalls)
    }

    private companion object {
        const val CHANGES = 20
    }
}
