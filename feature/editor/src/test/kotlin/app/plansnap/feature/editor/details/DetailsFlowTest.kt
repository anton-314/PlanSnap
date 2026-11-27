package app.plansnap.feature.editor.details

import app.plansnap.core.common.units.Length
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorRig
import app.plansnap.feature.editor.state.EditorViewModel
import app.plansnap.feature.editor.state.ROOM_ID
import app.plansnap.feature.editor.state.closedProject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Locale

/** The details sheets against the real view model, domain and history, with fake storage. */
@OptIn(ExperimentalCoroutinesApi::class)
class DetailsFlowTest {
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

    private fun TestScope.opened(): Pair<EditorRig, EditorViewModel> {
        val rig = EditorRig(this, closedProject(4000))
        rig.handlers += DetailsHandler()
        val viewModel = rig.viewModel()
        runCurrent()
        return rig to viewModel
    }

    private fun TestScope.send(
        viewModel: EditorViewModel,
        vararg intents: EditorIntent,
    ) {
        intents.forEach(viewModel::onIntent)
        runCurrent()
    }

    private val Project.room: Room get() = rooms.single { it.id == ROOM_ID }

    @Test
    fun `given edited details when applied then they are stored and saved`() =
        runTest {
            val (rig, viewModel) = opened()
            send(
                viewModel,
                DetailsIntent.OpenRoomDetails(),
                DetailsIntent.NameChanged("Küche"),
                DetailsIntent.TypeChanged(RoomTypeKey("kitchen")),
                DetailsIntent.NotesChanged("Fenster nach Süden"),
                DetailsIntent.HeightChanged("260"),
                DetailsIntent.FactorChanged("50"),
                DetailsIntent.ApplyRoomDetails,
            )
            val stored = rig.stored.room
            assertEquals("Küche", stored.name)
            assertEquals(RoomTypeKey("kitchen"), stored.typeKey)
            assertEquals("Fenster nach Süden", stored.notes)
            assertEquals(Length(2600), stored.heightOverride)
            assertEquals(AreaFactor(50), stored.areaFactor)
            assertNull(
                viewModel.state.value
                    .section(DetailsState::class)
                    ?.sheet,
            )
        }

    @Test
    fun `given applied details when undone and redone then the room returns exactly`() =
        runTest {
            val (rig, viewModel) = opened()
            val before = rig.stored.room
            send(
                viewModel,
                DetailsIntent.OpenRoomDetails(),
                DetailsIntent.NameChanged("Bad"),
                DetailsIntent.FactorChanged("0"),
                DetailsIntent.ApplyRoomDetails,
            )
            val after = rig.stored.room
            assertEquals(1, viewModel.state.value.undoDepth)
            send(viewModel, CoreIntent.Undo)
            assertEquals(before, rig.stored.room)
            send(viewModel, CoreIntent.Redo)
            assertEquals(after, rig.stored.room)
        }

    @Test
    fun `given a new project height when applied then it is stored and undo restores it`() =
        runTest {
            val (rig, viewModel) = opened()
            send(
                viewModel,
                DetailsIntent.OpenOverview,
                DetailsIntent.ProjectHeightChanged("270"),
                DetailsIntent.ApplyProjectHeight,
            )
            assertEquals(Length(2700), rig.stored.defaultHeight)
            send(viewModel, CoreIntent.Undo)
            assertEquals(Length(2500), rig.stored.defaultHeight)
        }

    @Test
    fun `given an invalid draft when applied then nothing is stored and nothing can be undone`() =
        runTest {
            val (rig, viewModel) = opened()
            val before = rig.stored
            send(
                viewModel,
                DetailsIntent.OpenRoomDetails(),
                DetailsIntent.HeightChanged("x"),
                DetailsIntent.ApplyRoomDetails,
            )
            assertEquals(before, rig.stored)
            assertEquals(0, viewModel.state.value.undoDepth)
            assertTrue(
                viewModel.state.value
                    .section(DetailsState::class)
                    ?.sheet is DetailsSheet.RoomDetails,
            )
        }
}
