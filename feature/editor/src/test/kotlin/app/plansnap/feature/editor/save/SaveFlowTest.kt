package app.plansnap.feature.editor.save

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.SaveStatus
import app.plansnap.feature.editor.state.emptyProject
import app.plansnap.testing.fixtures.fakes.FakeProjectSaver
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * A failing storage behind the real view model and domain: walls typed on the keypad, the message, the retry and the
 * state of the repository.
 */
@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h740dp-port-notnight-mdpi")
class SaveFlowTest {
    @get:Rule
    val compose = createComposeRule()

    private val scope = TestScope()
    private val rig = EditorRig(scope, emptyProject())
    private val saver = GatedSaver(rig.saver)
    private val viewModel: EditorViewModel by lazy { rig.viewModel(saver = saver) }

    private fun show() {
        compose.setContent {
            val state by viewModel.state.collectAsStateWithLifecycle()
            SaveEditorHost(state, viewModel::onIntent)
        }
        settle()
    }

    /** Lets the view model and the evaluator run on the virtual clock, then lets Compose draw the result. */
    private fun settle() {
        scope.advanceUntilIdle()
        compose.waitForIdle()
        scope.runCurrent()
        compose.waitForIdle()
    }

    private fun tap(label: String) {
        compose.onNode(hasText(label) and hasClickAction()).performClick()
        settle()
    }

    private fun addWall() {
        listOf("4", "0", "0", "OK").forEach(::tap)
    }

    private fun failNextSave() {
        rig.saver.failures.failNext(FakeProjectSaver.Operation.SAVE, WRITE_FAILED)
    }

    private fun storedWalls(): Int =
        rig.stored.rooms
            .single()
            .walls.size

    private fun assertMessage() {
        compose.onNodeWithText("Not saved").assertIsDisplayed()
        compose.onNodeWithText("Save again").assertIsDisplayed()
    }

    private fun assertNoMessage() {
        compose.onNodeWithText("Not saved").assertDoesNotExist()
        compose.onNodeWithText("Save again").assertDoesNotExist()
    }

    @Test
    fun `given a working storage when a wall is added then no message appears`() {
        show()
        addWall()
        assertNoMessage()
        assertEquals(1, storedWalls())
    }

    @Test
    fun `given a failed save when saving again succeeds then the wall is stored and the message is gone`() {
        show()
        failNextSave()
        addWall()
        assertMessage()
        assertEquals(0, storedWalls())
        tap("Save again")
        assertNoMessage()
        assertEquals(1, storedWalls())
    }

    @Test
    fun `given a failed save when saving again fails then the message stays`() {
        show()
        failNextSave()
        addWall()
        failNextSave()
        tap("Save again")
        assertMessage()
        assertEquals(0, storedWalls())
        tap("Save again")
        assertNoMessage()
        assertEquals(1, storedWalls())
    }

    @Test
    fun `given a slow retry when it is still running then the message stays until it succeeded`() {
        show()
        failNextSave()
        addWall()
        val gate = CompletableDeferred<Unit>()
        saver.gate = gate
        tap("Save again")
        assertMessage()
        tap("Save again")
        assertMessage()
        gate.complete(Unit)
        settle()
        assertNoMessage()
        assertEquals(1, storedWalls())
        assertEquals("a second tap saves nothing twice", 1, rig.saver.savedRoomSets.size)
    }

    @Test
    fun `given a failed save when the next wall is still being saved then the message stays`() {
        show()
        failNextSave()
        addWall()
        val gate = CompletableDeferred<Unit>()
        saver.gate = gate
        addWall()
        assertEquals(SaveStatus.Saving, viewModel.state.value.save)
        assertMessage()
        gate.complete(Unit)
        settle()
        assertNoMessage()
        assertEquals(2, storedWalls())
    }

    @Test
    fun `given a failed save when the next save fails too then the message stays and both walls follow later`() {
        show()
        failNextSave()
        addWall()
        failNextSave()
        addWall()
        assertMessage()
        assertEquals(0, storedWalls())
        tap("Save again")
        assertNoMessage()
        assertEquals(2, storedWalls())
    }
}
