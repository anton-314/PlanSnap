package app.plansnap.feature.editor.hints

import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.model.RoomId
import app.plansnap.feature.editor.input.editorState
import app.plansnap.feature.editor.rooms.DockDraft
import app.plansnap.feature.editor.rooms.RoomsState
import app.plansnap.feature.editor.shape.almostRectangle
import app.plansnap.feature.editor.shape.impossibleRoom
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.shape.withOverlay
import app.plansnap.feature.editor.state.EditorLoadState
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Which hint belongs to which situation of the editor, and when none is shown. */
class HintSelectionTest {
    private fun EditorUiState.dismissed(vararg hints: EditorHint) =
        copy(settings = AppSettings(dismissedHints = hints.toList().toImmutableSet()))

    @Test
    fun `given each situation when asked then exactly its hint is active`() {
        val expected =
            mapOf(
                Situation.EMPTY to EditorHint.NUMPAD,
                Situation.ONE_WALL to EditorHint.DIRECTION,
                Situation.THREE_WALLS to EditorHint.CLOSE,
                Situation.PROPOSAL to EditorHint.DIAGONAL,
                Situation.GAP to EditorHint.ADJUSTMENT,
            )
        expected.forEach { (situation, hint) -> assertEquals(situation.name, hint, activeHint(situation.state())) }
        assertEquals(EditorHint.entries.toSet(), expected.values.toSet())
    }

    @Test
    fun `given two walls when asked then the direction is still the hint`() {
        assertEquals(EditorHint.DIRECTION, activeHint(editorState(walls = 2)))
    }

    @Test
    fun `given a project without rooms when asked then the number pad is the hint`() {
        val state = editorState()
        val empty = state.copy(project = state.project!!.copy(rooms = persistentListOf()), selectedRoomId = null)

        assertEquals(EditorHint.NUMPAD, activeHint(empty))
    }

    @Test
    fun `given rooms but no selection when asked then there is no hint`() {
        assertNull(activeHint(editorState().copy(selectedRoomId = null)))
    }

    @Test
    fun `given the hint of the situation is dismissed when asked then no other hint replaces it`() {
        Situation.entries.forEach { situation ->
            val state = situation.state()
            val hint = activeHint(state)!!

            assertNull(situation.name, activeHint(state.dismissed(hint)))
        }
    }

    @Test
    fun `given other hints are dismissed when asked then the hint of the situation stays`() {
        val others = EditorHint.entries.filter { it != EditorHint.CLOSE }.toTypedArray()

        assertEquals(EditorHint.CLOSE, activeHint(editorState(walls = 3).dismissed(*others)))
    }

    @Test
    fun `given a hint that is closing when asked then it is hidden`() {
        val state =
            editorState().withSection(HintsState::class, HintsState(persistentSetOf(EditorHint.NUMPAD)))

        assertNull(activeHint(state))
    }

    @Test
    fun `given the project is not loaded when asked then there is no hint`() {
        val failed = EditorLoadState.Failed(DomainError.NotFound(EntityKind.PROJECT, "p1"))

        assertNull(activeHint(EditorUiState()))
        assertNull(activeHint(editorState().copy(load = failed)))
    }

    @Test
    fun `given a dock in progress when asked then there is no hint`() {
        val docking =
            editorState().withSection(RoomsState::class, RoomsState(dock = DockDraft(guestId = RoomId("room-1"))))

        assertNull(activeHint(docking))
    }

    @Test
    fun `given the rooms section without a dock when asked then the hint stays`() {
        val state = editorState().withSection(RoomsState::class, RoomsState())

        assertEquals(EditorHint.NUMPAD, activeHint(state))
    }

    @Test
    fun `given a closed room that is confirmed adjusted or impossible when asked then there is no hint`() {
        val rooms =
            listOf(
                roomOf(4000, 3000, 4000, 3000, closed = true),
                almostRectangle(3030).withOverlay(),
                impossibleRoom(),
            )

        rooms.forEach { assertNull(activeHint(stateOf(it))) }
    }

    @Test
    fun `given a closed room whose evaluation is pending when asked then there is no hint`() {
        assertNull(activeHint(stateOf(almostRectangle(3030)).copy(evaluations = persistentMapOf())))
    }
}
