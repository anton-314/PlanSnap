package app.plansnap.feature.editor.a11y

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasScrollAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.core.app.ApplicationProvider
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.ShapeStatus
import app.plansnap.core.model.WallId
import app.plansnap.feature.editor.R
import app.plansnap.feature.editor.canvas.PlanCanvas
import app.plansnap.feature.editor.opening.OpeningIntent
import app.plansnap.feature.editor.rooms.GUEST_ID
import app.plansnap.feature.editor.rooms.HOST_ID
import app.plansnap.feature.editor.rooms.roomsState
import app.plansnap.feature.editor.shape.RecordingHost
import app.plansnap.feature.editor.shape.SHAPE_ROOM
import app.plansnap.feature.editor.shape.diagonalOf
import app.plansnap.feature.editor.shape.impossibleRoom
import app.plansnap.feature.editor.shape.largeCorrectionRoom
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.shape.withOverlay
import app.plansnap.feature.editor.state.CoreIntent
import app.plansnap.feature.editor.state.EditorIntent
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The list of rooms and walls, the semantic alternative to the plan (UX section 7, REQ-UX-008). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class PlanOutlineTest {
    @get:Rule
    val compose = createComposeRule()

    private val context get() = ApplicationProvider.getApplicationContext<android.content.Context>()

    private val intents = mutableListOf<EditorIntent>()

    private fun outline(state: EditorUiState): List<OutlineEntry> = planOutline(state, context.resources)

    private fun List<OutlineEntry>.of(kind: OutlineKind): List<List<String>> =
        filter { it.kind == kind }.map { it.lines.toList() }

    private fun show(state: EditorUiState) {
        val entries = planOutline(state, context.resources)
        compose.setContent { PlanSnapTheme { PlanOutlineContent(entries) { intents += it } } }
    }

    private fun scrollTo(text: String) {
        compose.onNode(hasScrollAction()).performScrollToNode(hasText(text))
    }

    private fun click(text: String) {
        scrollTo(text)
        compose.onNodeWithText(text).performClick()
    }

    @Test
    fun `a confirmed room lists status, area and every wall with length, thickness and status`() {
        val entries = outline(stateOf(roomOf(4000, 3000, 4000, 3000, closed = true)))

        assertEquals(
            listOf(listOf("Küche", "Shape confirmed", "Area: 12.00 m²", "4 walls")),
            entries.of(OutlineKind.ROOM),
        )
        assertEquals(
            listOf("Wall 1, Küche", "Length 400.0 cm", "Thickness 12.0 cm", "Shape confirmed"),
            entries.of(OutlineKind.WALL).first(),
        )
        assertEquals(4, entries.of(OutlineKind.WALL).size)
        assertEquals(ShapeStatus.CONFIRMED, entries.first().status)
    }

    @Test
    fun `openings, diagonals and measured angles are listed with dimensions and fit`() {
        val entries = outline(stateOf(furnishedRoom()))

        assertEquals(
            listOf(
                listOf(
                    "Door on wall 1, Küche",
                    "Door, 90 cm wide, starts 50 cm from the start of the wall",
                    "Fits on the wall",
                ),
                listOf(
                    "Door on wall 2, Küche",
                    "Door, 900 cm wide, starts 0 cm from the start of the wall",
                    context.getString(R.string.opening_status_out_of_bounds),
                ),
            ),
            entries.of(OutlineKind.OPENING),
        )
        assertTrue("Entered angle at corner 2: 90°" in entries.of(OutlineKind.WALL)[1])
        assertEquals(
            listOf("Diagonal from corner 1 to corner 3, Küche", "500.0 cm"),
            entries.of(OutlineKind.DIAGONAL).single(),
        )
    }

    @Test
    fun `a conflicting room says why and marks every wall`() {
        val entries = outline(stateOf(impossibleRoom()))

        val room = entries.of(OutlineKind.ROOM).single()
        assertEquals("Measurements conflict", room[1])
        assertTrue(context.getString(R.string.shape_conflict_wall_too_long) in room)
        assertTrue(entries.of(OutlineKind.WALL).all { it.last() == "Measurements conflict" })
    }

    @Test
    fun `an adjusted room lists the length of the plan and the entered one beside it`() {
        val state = stateOf(largeCorrectionRoom().withOverlay())

        val first = outline(state).of(OutlineKind.WALL).first()

        // The adjustment shortens wall 1, so the plan shows another length than the one entered.
        assertEquals(
            listOf("Wall 1, Küche", "Length 414.9 cm", "Entered length 415.0 cm", "Thickness 12.0 cm"),
            first.take(4),
        )
    }

    @Test
    fun `an open room, a pending room and a failed room say so`() {
        val open = stateOf(roomOf(4000, 3000))
        assertEquals("Not closed", outline(open).of(OutlineKind.ROOM).single()[1])

        val pending = open.copy(evaluations = persistentMapOf())
        assertEquals("Being calculated", outline(pending).of(OutlineKind.ROOM).single()[1])

        val error = DomainError.Storage(StorageProblem.READ_FAILED)
        val failed = pending.copy(evaluationErrors = persistentMapOf(SHAPE_ROOM to error))
        assertTrue(outline(failed).of(OutlineKind.ROOM).single()[1].startsWith("This room could not be evaluated."))
    }

    @Test
    fun `a room without walls says so and a state without project has no entries`() {
        assertEquals("No walls yet", outline(stateOf(roomOf())).of(OutlineKind.ROOM).single().last())
        assertEquals(emptyList<OutlineEntry>(), outline(EditorUiState()))
    }

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h640dp-port-notnight-mdpi")
    fun `the list is German on a German device`() {
        val room = roomOf(4000, 3000, 4000, 3000, closed = true, diagonals = listOf(diagonalOf(0, 2, 5000)))

        val entries = outline(stateOf(room))

        assertEquals("Form bestätigt", entries.of(OutlineKind.ROOM).single()[1])
        assertEquals("4 Wände", entries.of(OutlineKind.ROOM).single().last())
        assertEquals("Wand 1, Küche", entries.of(OutlineKind.WALL).first().first())
        assertEquals("Länge 400,0 cm", entries.of(OutlineKind.WALL).first()[1])
        assertEquals(
            "Diagonale von Ecke 1 zu Ecke 3, Küche",
            entries.of(OutlineKind.DIAGONAL).single().first(),
        )
    }

    @Test
    fun `activating a room, a wall or an opening closes the list and acts like a tap on the plan`() {
        val room = furnishedRoom()
        show(stateOf(room))

        click("Küche")
        assertEquals(listOf(PlanOutlineIntent.Dismiss, CoreIntent.SelectRoom(room.id)), intents)

        intents.clear()
        click("Wall 2, Küche")
        assertEquals(
            listOf(
                PlanOutlineIntent.Dismiss,
                CoreIntent.SelectRoom(room.id),
                OpeningIntent.OpenWall(room.id, WallId("w1")),
            ),
            intents,
        )

        intents.clear()
        click("Door on wall 1, Küche")
        assertEquals(
            listOf(
                PlanOutlineIntent.Dismiss,
                CoreIntent.SelectRoom(room.id),
                OpeningIntent.Edit(room.id, OpeningId("fits")),
            ),
            intents,
        )
    }

    @Test
    fun `entries name their action, only the selected room is selected and a diagonal is not tappable`() {
        show(roomsState())

        scrollTo(HOST_ID.value)
        compose.onNodeWithText(HOST_ID.value).assertIsNotSelected()
        scrollTo(GUEST_ID.value)
        compose
            .onNodeWithText(GUEST_ID.value)
            .assertIsSelected()
            .assert(SemanticsMatcher.keyIsDefined(SemanticsProperties.Heading))
            .assert(
                SemanticsMatcher("click action is named") {
                    it.config[SemanticsActions.OnClick].label == context.getString(R.string.a11y_outline_action_select)
                },
            )
    }

    @Test
    fun `a diagonal has no click action`() {
        show(stateOf(furnishedRoom()))
        val diagonal = "Diagonal from corner 1 to corner 3, Küche"

        compose.onNode(hasScrollAction()).performScrollToNode(hasText(diagonal))

        compose.onNodeWithText(diagonal).assertHasNoClickAction()
    }

    @Test
    fun `an empty project says that there are no rooms`() {
        show(roomsState().let { it.copy(project = it.project!!.copy(rooms = persistentListOf())) })

        compose.onNodeWithText(context.getString(R.string.details_overview_empty)).assertExists()
        compose.onNodeWithText(context.getString(R.string.a11y_outline_close)).performClick()

        assertEquals(listOf<EditorIntent>(PlanOutlineIntent.Dismiss), intents)
    }

    @Test
    fun `the handler opens and closes the list and ignores other intents`() {
        val host = RecordingHost(stateOf(roomOf()))
        val handler = PlanOutlineHandler()

        assertTrue(handler.handle(PlanOutlineMenu.intent, host))
        assertEquals(PlanOutlineState(open = true), host.state.value.section(PlanOutlineState::class))
        assertTrue(handler.handle(PlanOutlineIntent.Dismiss, host))
        assertEquals(PlanOutlineState(open = false), host.state.value.section(PlanOutlineState::class))
        assertFalse(handler.handle(CoreIntent.SelectRoom(RoomId("r1")), host))
    }

    @Test
    fun `the host shows the list only while it is open`() {
        val closed = stateOf(roomOf(4000, 3000))
        compose.setContent { PlanSnapTheme { PlanOutlineHost(closed) {} } }

        compose.onNodeWithText(context.getString(R.string.a11y_outline_title)).assertDoesNotExist()
    }

    @Test
    fun `the open host shows title and rooms`() {
        val open = stateOf(roomOf(4000, 3000)).withSection(PlanOutlineState::class, PlanOutlineState(open = true))
        compose.setContent { PlanSnapTheme { PlanOutlineHost(open) {} } }

        compose.onNodeWithText(context.getString(R.string.a11y_outline_title)).assertExists()
        compose.onNodeWithText("Küche").assertExists()
    }

    @Test
    fun `the plan canvas offers the list as an accessibility action`() {
        var shown = 0
        compose.setContent {
            PlanSnapTheme {
                PlanCanvas(
                    scene = null,
                    view = null,
                    onViewChange = {},
                    onTap = {},
                    onShowOutline = { shown++ },
                )
            }
        }
        val actions =
            compose
                .onNodeWithContentDescription(context.getString(R.string.canvas_description_empty))
                .fetchSemanticsNode()
                .config[SemanticsActions.CustomActions]

        compose.runOnIdle {
            actions.single { it.label == context.getString(R.string.a11y_outline_action_show) }.action()
        }

        assertEquals(1, shown)
        assertEquals(2, actions.size)
    }
}
