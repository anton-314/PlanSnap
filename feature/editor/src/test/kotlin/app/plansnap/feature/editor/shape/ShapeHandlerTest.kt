package app.plansnap.feature.editor.shape

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.result.InvalidValueReason
import app.plansnap.core.common.result.ValueField
import app.plansnap.core.common.units.Angle
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Diagonal
import app.plansnap.core.model.DiagonalId
import app.plansnap.core.model.Room
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorUiState
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

class ShapeHandlerTest {
    private val handler = ShapeHandler(SequentialIdGenerator("gen"))
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

    private fun host(
        room: Room = roomOf(4000, 3000, 4000),
        block: EditorUiState.() -> EditorUiState = { this },
    ) = RecordingHost(stateOf(room, block))

    private fun RecordingHost.send(vararg intents: ShapeIntent) =
        intents.forEach { assertTrue(handler.handle(it, this)) }

    private fun RecordingHost.type(text: String) = send(ShapeIntent.SheetTextChanged(text))

    @Test
    fun `given a core intent when offered then the handler leaves it to the others`() {
        assertFalse(handler.handle(CoreIntent.Undo, host()))
    }

    @Test
    fun `given an open room when the angle sheet opens then it starts empty with the entered direction`() {
        val host = host()
        host.send(ShapeIntent.OpenCorner())
        assertEquals(ShapeSheet.Corner(CornerTarget.Next, TurnDirection.LEFT, ""), host.shape.sheet)
        assertNull(host.shape.error)
        assertFalse(host.shape.canConfirm)
    }

    @Test
    fun `given a room without walls when the angle sheet opens then nothing opens`() {
        val host = host(roomOf())
        host.send(ShapeIntent.OpenCorner())
        assertNull(host.shape.sheet)
    }

    @Test
    fun `given a measured next corner when the angle sheet opens then it shows the stored angle and direction`() {
        val host = host { copy(nextCorner = CornerSpec.Measured(TurnDirection.RIGHT, Angle(75_500))) }
        host.send(ShapeIntent.OpenCorner())
        assertEquals(ShapeSheet.Corner(CornerTarget.Next, TurnDirection.RIGHT, "75.5"), host.shape.sheet)
    }

    @Test
    fun `given a typed angle when confirmed then the next corner is measured and nothing is submitted`() {
        val host = host()
        host.send(ShapeIntent.OpenCorner(), ShapeIntent.SheetDirectionChanged(TurnDirection.RIGHT))
        host.type("75")
        assertTrue(host.shape.canConfirm)
        host.send(ShapeIntent.ConfirmSheet)
        assertEquals(CornerSpec.Measured(TurnDirection.RIGHT, Angle(75_000)), host.state.value.nextCorner)
        assertNull(host.shape.sheet)
        assertTrue(host.submitted.isEmpty())
    }

    @Test
    fun `given an angle outside the range when typed then the sheet explains it and cannot confirm`() {
        val host = host()
        host.send(ShapeIntent.OpenCorner())
        host.type("0")
        assertEquals(DomainError.InvalidValue(ValueField.CORNER_ANGLE, InvalidValueReason.TOO_SMALL), host.shape.error)
        host.type("181")
        assertEquals(DomainError.InvalidValue(ValueField.CORNER_ANGLE, InvalidValueReason.TOO_LARGE), host.shape.error)
        host.type("89.1234")
        assertEquals(
            DomainError.InvalidValue(ValueField.CORNER_ANGLE, InvalidValueReason.NOT_REPRESENTABLE),
            host.shape.error,
        )
        assertFalse(host.shape.canConfirm)
        host.send(ShapeIntent.ConfirmSheet)
        assertEquals(
            DomainError.InvalidValue(ValueField.CORNER_ANGLE, InvalidValueReason.NOT_REPRESENTABLE),
            host.shape.error,
        )
        assertTrue(host.shape.sheet is ShapeSheet.Corner)
    }

    @Test
    fun `given a straight angle when confirmed then it is accepted`() {
        val host = host()
        host.send(ShapeIntent.OpenCorner())
        host.type("180")
        host.send(ShapeIntent.ConfirmSheet)
        assertEquals(Angle.STRAIGHT, (host.state.value.nextCorner as CornerSpec.Measured).angle)
    }

    @Test
    fun `given a stored corner when its angle is confirmed then SetCorner is submitted`() {
        val host = host()
        host.send(ShapeIntent.OpenCorner(CornerTarget.Existing(1)))
        host.type("105,5")
        host.send(ShapeIntent.ConfirmSheet)
        assertEquals(
            listOf<Any>(ProjectEdit.SetCorner(SHAPE_ROOM, 1, CornerSpec.Measured(TurnDirection.LEFT, Angle(105_500)))),
            host.submitted,
        )
        assertNull(host.shape.sheet)
    }

    @Test
    fun `given an unknown corner when the sheet opens then the user hears that it does not exist`() {
        val host = host()
        host.send(ShapeIntent.OpenCorner(CornerTarget.Existing(9)))
        assertNull(host.shape.sheet)
        assertEquals(listOf<Any>(EditorEffect.Error(DomainError.NotFound(EntityKind.CORNER, "9"))), host.effects)
    }

    @Test
    fun `given the closing corner when its angle sheet opens then it starts with the next direction`() {
        val host = host { copy(nextCorner = CornerSpec.DeclaredRight(TurnDirection.RIGHT)) }
        host.send(ShapeIntent.OpenCorner(CornerTarget.Existing(0)))
        assertEquals(ShapeSheet.Corner(CornerTarget.Existing(0), TurnDirection.RIGHT, ""), host.shape.sheet)
    }

    @Test
    fun `given a room with walls when the diagonal sheet opens then it proposes corners one and three`() {
        val host = host()
        host.send(ShapeIntent.OpenDiagonal())
        assertEquals(ShapeSheet.Diagonal(0, 2, ""), host.shape.sheet)
    }

    @Test
    fun `given a recommended diagonal when the sheet opens then it starts with those corners`() {
        val host = host(roomOf(3000, 3000, 3000, 5000, closed = true))
        host.send(ShapeIntent.OpenDiagonal(1, 3))
        assertEquals(ShapeSheet.Diagonal(1, 3, ""), host.shape.sheet)
    }

    @Test
    fun `given a room with one wall when the diagonal sheet opens then it asks for more walls`() {
        val host = host(roomOf(4000))
        host.send(ShapeIntent.OpenDiagonal())
        assertNull(host.shape.sheet)
        assertEquals(
            listOf<Any>(EditorEffect.Error(DomainError.RuleViolation(DomainRule.TOO_FEW_WALLS))),
            host.effects,
        )
    }

    @Test
    fun `given a typed diagonal when confirmed then AddDiagonal carries the exact length in mm`() {
        val host = host(roomOf(3000, 3000, 3000, 5000, closed = true))
        host.send(ShapeIntent.OpenDiagonal(), ShapeIntent.DiagonalCornersChanged(0, 2))
        host.type("424,3")
        host.send(ShapeIntent.ConfirmSheet)
        val edit = host.submitted.single() as ProjectEdit.AddDiagonal
        assertEquals(SHAPE_ROOM, edit.roomId)
        assertEquals(Diagonal(DiagonalId("gen-1"), 0, 2, Length(4243)), edit.diagonal)
        assertNull(host.shape.sheet)
    }

    @Test
    fun `given the same corner twice when the diagonal is typed then the sheet reports invalid corners`() {
        val host = host()
        host.send(ShapeIntent.OpenDiagonal(), ShapeIntent.DiagonalCornersChanged(1, 1))
        host.type("300")
        assertEquals(DomainError.RuleViolation(DomainRule.DIAGONAL_INVALID_CORNERS), host.shape.error)
        assertFalse(host.shape.canConfirm)
        host.send(ShapeIntent.ConfirmSheet)
        assertTrue(host.submitted.isEmpty())
        host.send(ShapeIntent.DiagonalCornersChanged(0, 2))
        assertNull(host.shape.error)
        assertTrue(host.shape.canConfirm)
    }

    @Test
    fun `given corners outside the room when chosen then they are ignored`() {
        val host = host()
        host.send(ShapeIntent.OpenDiagonal(), ShapeIntent.DiagonalCornersChanged(0, 7))
        assertEquals(ShapeSheet.Diagonal(0, 2, ""), host.shape.sheet)
    }

    @Test
    fun `given a diagonal of zero when typed then the sheet says it is too small`() {
        val host = host()
        host.send(ShapeIntent.OpenDiagonal())
        host.type("0")
        assertEquals(
            DomainError.InvalidValue(ValueField.DIAGONAL_LENGTH, InvalidValueReason.TOO_SMALL),
            host.shape.error,
        )
    }

    @Test
    fun `given the thickness sheet when opened then it starts with the thickness of the next wall`() {
        val host = host()
        host.send(ShapeIntent.OpenThickness)
        assertEquals(ShapeSheet.Thickness("12.0"), host.shape.sheet)
        assertTrue(host.shape.canConfirm)
    }

    @Test
    fun `given a typed thickness when confirmed then it applies to the next wall`() {
        val host = host()
        host.send(ShapeIntent.OpenThickness)
        host.type("20")
        host.send(ShapeIntent.ConfirmSheet)
        assertEquals(Length(200), host.state.value.thicknessOverride)
        assertEquals(Length(200), host.state.value.thickness)
        assertNull(host.shape.sheet)
    }

    @Test
    fun `given a thickness of zero when confirmed then the wall becomes a line`() {
        val host = host()
        host.send(ShapeIntent.OpenThickness)
        host.type("0")
        host.send(ShapeIntent.ConfirmSheet)
        assertEquals(Length.ZERO, host.state.value.thicknessOverride)
    }

    @Test
    fun `given a thickness above the limit when typed then the sheet says it is too large`() {
        val host = host()
        host.send(ShapeIntent.OpenThickness)
        host.type("101")
        assertEquals(
            DomainError.InvalidValue(ValueField.WALL_THICKNESS, InvalidValueReason.TOO_LARGE),
            host.shape.error,
        )
        host.send(ShapeIntent.ConfirmSheet)
        assertNull(host.state.value.thicknessOverride)
    }

    @Test
    fun `given a room when closing is requested then CloseRoom is submitted`() {
        val host = host()
        host.send(ShapeIntent.CloseRoom)
        assertEquals(listOf<Any>(ProjectEdit.CloseRoom(SHAPE_ROOM)), host.submitted)
    }

    @Test
    fun `given a room with a proposal when the adjustment opens then the sheet shows it`() {
        val host = host(almostRectangle(3030))
        host.send(ShapeIntent.OpenAdjustment)
        assertEquals(ShapeSheet.Adjustment, host.shape.sheet)
    }

    @Test
    fun `given a room without a proposal when the adjustment is requested then nothing opens`() {
        val host = host(almostRectangle(3000))
        host.send(ShapeIntent.OpenAdjustment)
        assertNull(host.shape.sheet)
    }

    @Test
    fun `given the adjustment sheet when a large correction is requested then the dialog opens until withdrawn`() {
        val host = host(largeCorrectionRoom())
        host.send(ShapeIntent.OpenAdjustment, ShapeIntent.RequestExplicitConfirmation)
        assertTrue(host.shape.explicitConfirmation)
        host.send(ShapeIntent.CancelExplicitConfirmation)
        assertFalse(host.shape.explicitConfirmation)
        assertEquals(ShapeSheet.Adjustment, host.shape.sheet)
    }

    @Test
    fun `given no adjustment sheet when the large correction is requested then no dialog opens`() {
        val host = host()
        host.send(ShapeIntent.RequestExplicitConfirmation)
        assertFalse(host.shape.explicitConfirmation)
    }

    @Test
    fun `given an adjusted room when reverting then RevertAdjustment is submitted and otherwise nothing`() {
        val plain = host()
        plain.send(ShapeIntent.RevertAdjustment)
        assertTrue(plain.submitted.isEmpty())
        val adjusted = host(almostRectangle(3030).withOverlay())
        adjusted.send(ShapeIntent.RevertAdjustment)
        assertEquals(listOf<Any>(ProjectEdit.RevertAdjustment(SHAPE_ROOM)), adjusted.submitted)
    }

    @Test
    fun `given an open sheet when dismissed then the whole section is reset`() {
        val host = host(largeCorrectionRoom())
        host.send(ShapeIntent.OpenAdjustment, ShapeIntent.RequestExplicitConfirmation, ShapeIntent.Dismiss)
        assertEquals(ShapeState(), host.shape)
    }

    @Test
    fun `given a text change without a value sheet when typed then nothing changes`() {
        val host = host(largeCorrectionRoom())
        host.send(ShapeIntent.OpenAdjustment)
        host.type("12")
        assertEquals(ShapeSheet.Adjustment, host.shape.sheet)
    }
}
