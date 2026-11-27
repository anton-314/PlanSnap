package app.plansnap.feature.editor.details

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainRule
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.model.AreaFactor
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.feature.editor.shape.SHAPE_ROOM
import app.plansnap.feature.editor.shape.evaluate
import app.plansnap.feature.editor.shape.impossibleRoom
import app.plansnap.feature.editor.shape.roomOf
import app.plansnap.feature.editor.shape.stateOf
import app.plansnap.feature.editor.state.EditorUiState
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableMap
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.util.Locale

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-rUS-w360dp-h1000dp")
class DetailsSheetsTest {
    @get:Rule
    val compose = createComposeRule()

    private val minTarget = 48.dp

    private fun show(
        state: EditorUiState = detailsState(),
        vararg opening: DetailsIntent,
    ): DetailsEditor {
        val editor = DetailsEditor(state)
        opening.forEach(editor::onIntent)
        editor.intents.clear()
        compose.setContent {
            val current by editor.stateAsState()
            PlanSnapTheme { DetailsHost(current, editor::onIntent) }
        }
        return editor
    }

    private fun tag(tag: String): SemanticsNodeInteraction = compose.onNodeWithTag(tag).performScrollTo()

    @Test
    @Config(qualifiers = "de-rDE-w360dp-h1000dp")
    fun `given the german language when the overview opens then texts and numbers are german`() {
        withLocale(Locale.GERMANY) { show(opening = arrayOf(DetailsIntent.OpenOverview)) }
        compose.onNodeWithText("Räume und Flächen").assertIsDisplayed()
        val row = compose.onNodeWithTag("${TAG_ROW_PREFIX}r1").performScrollTo()
        listOf("Küche · Küche", "Fläche: 12,00 m²", "Anrechenbare Fläche: 6,00 m²")
            .forEach { row.assert(hasText(it, substring = true)) }
        compose
            .onNodeWithText("Dies ist keine rechtsverbindliche Wohnflächenberechnung.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `given a room when the details open then every field shows its stored value`() {
        show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        compose.onNodeWithText("Room details").assertIsDisplayed()
        tag(TAG_NAME).assertTextContains("Küche")
        tag(TAG_NOTES).assertTextContains("Fenster nach Süden")
        tag(TAG_HEIGHT).assertTextContains("260.0")
        tag(TAG_FACTOR).assertTextContains("50")
        compose
            .onNodeWithText("Leave empty to use the default height of the project: 250.0 cm.", substring = true)
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `given the type chips when one is tapped then the type changes through an intent`() {
        val editor = show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        compose.onNodeWithText("Bathroom").performScrollTo().performClick()
        assertEquals(
            listOf(DetailsIntent.TypeChanged(RoomTypeKey("bathroom"))),
            editor.sent<DetailsIntent.TypeChanged>(),
        )
        compose.onNodeWithText("No type").performScrollTo().performClick()
        assertEquals(DetailsIntent.TypeChanged(null), editor.sent<DetailsIntent.TypeChanged>().last())
    }

    @Test
    fun `given typed values when applied then the intents arrive in order and the command is submitted`() {
        val editor = show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        tag(TAG_NAME).performTextReplacement("Bad")
        tag(TAG_HEIGHT).performTextReplacement("240")
        compose.onNodeWithText("Apply").performScrollTo().performClick()
        assertTrue(editor.sent<DetailsIntent.NameChanged>().single().text == "Bad")
        assertEquals(1, editor.host.submitted.size)
        compose.onNodeWithText("Room details").assertDoesNotExist()
    }

    @Test
    fun `given an invalid height when typed then the field explains it and apply is disabled`() {
        show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        tag(TAG_HEIGHT).performTextReplacement("abc")
        compose.onNodeWithText("The room height is not a valid number.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apply").performScrollTo().assertIsNotEnabled()
        tag(TAG_HEIGHT).performTextReplacement("250")
        compose.onNodeWithText("Apply").performScrollTo().assertIsEnabled()
    }

    @Test
    fun `given an invalid factor when typed then the factor field explains it`() {
        show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        tag(TAG_FACTOR).performTextReplacement("101")
        compose.onNodeWithText("The area factor is too large.").performScrollTo().assertIsDisplayed()
    }

    @Test
    fun `given the sheet when measured then every control is at least 48 dp high`() {
        show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        listOf("Apply", "Cancel", "Kitchen", "No type").forEach {
            compose.onNodeWithText(it).performScrollTo().assertHeightIsAtLeast(minTarget)
        }
        listOf(TAG_NAME, TAG_HEIGHT, TAG_FACTOR).forEach { tag(it).assertHeightIsAtLeast(minTarget) }
    }

    @Test
    fun `given cancel when tapped then the sheet is dismissed without a command`() {
        val editor = show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        compose.onNodeWithText("Cancel").performScrollTo().performClick()
        assertEquals(listOf<Any>(DetailsIntent.Dismiss), editor.intents)
        assertTrue(editor.host.submitted.isEmpty())
    }

    @Test
    fun `given the room vanishes when the sheet is open then it closes itself`() {
        val editor = show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        editor.host.update { it.copy(project = it.project!!.copy(rooms = persistentListOf())) }
        compose.waitForIdle()
        assertTrue(editor.sent<DetailsIntent.Dismiss>().isNotEmpty())
    }

    @Test
    fun `given the overview when opened then rows totals and the legal note are shown`() {
        show(opening = arrayOf(DetailsIntent.OpenOverview))
        compose.onNodeWithText("Rooms and areas").assertIsDisplayed()
        val row = compose.onNodeWithTag("${TAG_ROW_PREFIX}r1").performScrollTo()
        listOf(
            "Küche · Kitchen",
            "Area: 12.00 m²",
            "Perimeter: 1400.0 cm",
            "Factor: 50 %",
            "Credited area: 6.00 m²",
            "Volume: 31.20 m³",
        ).forEach { row.assert(hasText(it, substring = true)) }
        val totals = compose.onNodeWithTag(TAG_TOTALS).performScrollTo()
        listOf("Measured area: 12.00 m²", "Credited area: 6.00 m²", "Perimeter: 1400.0 cm", "Volume: 31.20 m³")
            .forEach { totals.assert(hasText(it, substring = true)) }
        compose
            .onNodeWithText("This is not a legally binding living space calculation.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `given a room without a shape when the overview opens then it is marked and the totals say it is missing`() {
        val base = detailsState()
        val open = roomOf(4000, 3000).copy(id = RoomId("open"), name = "Flur")
        val conflict = impossibleRoom().copy(id = RoomId("conflict"), name = "")
        val project = checkNotNull(base.project)
        val rooms = persistentListOf(project.rooms.single(), open, conflict)
        val state =
            base.copy(
                project = project.copy(rooms = rooms),
                evaluations =
                    rooms
                        .associate { it.id to evaluate(it) }
                        .toImmutableMap(),
            )
        show(state, DetailsIntent.OpenOverview)
        compose
            .onNodeWithText("No shape yet: the room is not closed. It is missing in the totals.")
            .performScrollTo()
            .assertIsDisplayed()
        compose
            .onNodeWithText("No shape: the measurements conflict. The room is missing in the totals.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithText("Room 3").performScrollTo().assertIsDisplayed()
        compose
            .onNodeWithText("2 rooms without a shape are not included in the totals.")
            .performScrollTo()
            .assertIsDisplayed()
        compose.onNodeWithTag(TAG_TOTALS).performScrollTo().assert(hasText("Measured area: 12.00 m²", substring = true))
    }

    @Test
    fun `given a row when tapped then the details of that room open`() {
        val editor = show(opening = arrayOf(DetailsIntent.OpenOverview))
        compose
            .onNodeWithTag("${TAG_ROW_PREFIX}r1")
            .performScrollTo()
            .assertHeightIsAtLeast(minTarget)
            .performClick()
        assertEquals(listOf<Any>(DetailsIntent.OpenRoomDetails(RoomId("r1"))), editor.intents)
        compose.onNodeWithText("Name").assertIsDisplayed()
    }

    @Test
    fun `given the project height when applied then the command is submitted from the overview`() {
        val editor = show(opening = arrayOf(DetailsIntent.OpenOverview))
        tag(TAG_PROJECT_HEIGHT).performTextReplacement("270")
        compose
            .onNodeWithText("Apply default height")
            .performScrollTo()
            .assertHeightIsAtLeast(minTarget)
            .performClick()
        assertEquals(1, editor.host.submitted.size)
    }

    @Test
    fun `given an invalid project height when typed then apply is disabled and the field explains it`() {
        show(opening = arrayOf(DetailsIntent.OpenOverview))
        tag(TAG_PROJECT_HEIGHT).performTextReplacement("0")
        compose.onNodeWithText("The room height is too small.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apply default height").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `given an empty project when the overview opens then it says so`() {
        val base = stateOf(roomOf(1000, 1000, 1000))
        val empty = base.copy(project = base.project!!.copy(rooms = persistentListOf()))
        show(empty, DetailsIntent.OpenOverview)
        compose.onNodeWithText("There are no rooms yet.").assertIsDisplayed()
        compose
            .onNodeWithText("This is not a legally binding living space calculation.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `given a name that is too long when typed then the field names the limit`() {
        show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        tag(TAG_NAME).performTextReplacement("x".repeat(61))
        compose.onNodeWithText("The name can have at most 60 characters.").performScrollTo().assertIsDisplayed()
        compose.onNodeWithText("Apply").performScrollTo().assertIsNotEnabled()
    }

    @Test
    fun `given typed notes when applied then the command carries them`() {
        val editor = show(opening = arrayOf(DetailsIntent.OpenRoomDetails()))
        tag(TAG_NOTES).performTextReplacement("Heizkörper links")
        tag(TAG_FACTOR).performTextReplacement("25")
        compose.onNodeWithText("Apply").performScrollTo().performClick()
        val details = (editor.host.submitted.single() as ProjectEdit.UpdateRoomDetails).details
        assertEquals("Heizkörper links", details.notes)
        assertEquals(AreaFactor(25), details.areaFactor)
    }

    @Test
    fun `given a room that is still being evaluated when the overview opens then it says so`() {
        show(detailsState().copy(evaluations = persistentMapOf()), DetailsIntent.OpenOverview)
        compose
            .onNodeWithText("Being calculated. The room is not in the totals yet.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `given a room whose evaluation fails while the overview is open then the row explains it`() {
        val editor = show(detailsState().copy(evaluations = persistentMapOf()), DetailsIntent.OpenOverview)
        editor.host.update {
            it.copy(
                evaluationErrors =
                    persistentMapOf(SHAPE_ROOM to DomainError.RuleViolation(DomainRule.TOO_FEW_WALLS)),
            )
        }
        compose
            .onNodeWithText("No shape: the room could not be evaluated. It is missing in the totals.")
            .performScrollTo()
            .assertIsDisplayed()
    }

    @Test
    fun `given the room type names when remembered then they are translated and unknown keys stay`() {
        var names: List<String> = emptyList()
        compose.setContent {
            val roomTypeName = rememberRoomTypeName()
            names = listOf(roomTypeName(RoomTypeKey("kitchen")), roomTypeName(RoomTypeKey("sauna")))
        }
        compose.waitForIdle()
        assertEquals(listOf("Kitchen", "sauna"), names)
    }

    @Test
    fun `given no sheet when hosted then nothing is shown`() {
        show()
        compose.onNodeWithText("Room details").assertDoesNotExist()
    }
}
