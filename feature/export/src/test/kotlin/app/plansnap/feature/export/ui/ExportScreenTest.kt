package app.plansnap.feature.export.ui

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.unit.dp
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.ExportProblem
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.export.ExportFormat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en-w360dp-h1200dp")
class ExportScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var format: ExportFormat? = null
    private var scale: PlotScale? = PlotScale.ONE_TO_200
    private var paper: PaperSize? = PaperSize.A3
    private val calls = mutableListOf<String>()

    private val actions =
        ExportActions(
            onFormatSelected = { format = it },
            onScaleSelected = { scale = it },
            onPaperSelected = { paper = it },
            onShare = { calls += "share" },
            onSave = { calls += "save" },
            onRetry = { calls += "retry" },
            onRetryLoad = { calls += "retryLoad" },
            onErrorDismissed = { calls += "dismissError" },
            onNoticeDismissed = { calls += "dismissNotice" },
        )

    private fun show(state: ExportUiState) {
        compose.setContent {
            PlanSnapTheme { ExportScreen(state = state, actions = actions, onBack = { calls += "back" }) }
        }
    }

    @Test
    fun `given no room when shown then the hint is displayed and back works`() {
        show(ExportUiState.Empty("Flat"))
        compose.onNodeWithText("No room yet").assertExists()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(listOf("back"), calls)
    }

    @Test
    @Config(qualifiers = "de-w360dp-h1200dp")
    fun `given no room when german then the german hint is displayed`() {
        show(ExportUiState.Empty("Flat"))
        compose.onNodeWithText("Noch kein Raum").assertExists()
    }

    @Test
    fun `given a load failure when shown then the cause is explained and retry is offered`() {
        show(ExportUiState.Failed(DomainError.Storage(StorageProblem.READ_FAILED)))
        compose.onNodeWithText("The project could not be read. Try again.").assertExists()
        compose.onNodeWithText("Try again").performClick()
        assertEquals(listOf("retryLoad"), calls)
    }

    @Test
    fun `given the standard state when shown then format, page and result are visible, automatic selected`() {
        show(ExportScreenStates.standard)
        listOf("PDF", "PNG", "SVG", "JSON").forEach { compose.onNodeWithText(it).assertExists() }
        compose.onNodeWithText("Result: scale 1:50 on A4, Landscape").assertExists()
        compose.onNodeWithText("The scale bar and the scale are printed in the title block.").assertExists()
        compose.onAllNodesWithText("Automatic").assertCountEquals(2)
        compose.onAllNodesWithText("Automatic").onFirst().assertIsSelected()
        compose.onNodeWithText("Share").assertIsEnabled()
        compose.onNodeWithText("Save as file").assertIsEnabled()
    }

    @Test
    fun `given the shown orientation when tapped then it is not a choice`() {
        show(ExportScreenStates.standard)
        compose.onNodeWithText("Orientation").assertExists()
        compose.onNodeWithText("The orientation is always chosen to match the plan.").assertExists()
        compose.onAllNodesWithText("Landscape").assertCountEquals(1)
        compose.onAllNodesWithText("Portrait").assertCountEquals(0)
    }

    @Test
    fun `given choices when tapped then the callbacks receive format, scale and paper`() {
        show(ExportScreenStates.standard)
        compose.onNodeWithText("SVG").performClick()
        assertEquals(ExportFormat.SVG, format)
        compose.onNodeWithText("1:100").performClick()
        assertEquals(PlotScale.ONE_TO_100, scale)
        compose.onNodeWithText("A3").performClick()
        assertEquals(PaperSize.A3, paper)
        compose.onAllNodesWithText("Automatic")[0].performClick()
        assertNull(scale)
        compose.onAllNodesWithText("Automatic")[1].performClick()
        assertNull(paper)
    }

    @Test
    fun `given share and save when tapped then the callbacks are invoked`() {
        show(ExportScreenStates.standard)
        compose.onNodeWithText("Share").performClick()
        compose.onNodeWithText("Save as file").performClick()
        assertEquals(listOf("share", "save"), calls)
    }

    @Test
    fun `given JSON when shown then the page section and the warnings are hidden`() {
        show(ExportScreenStates.warnings.copy(format = ExportFormat.JSON, fit = PlanFit.NotApplicable))
        compose.onNodeWithText("Page").assertDoesNotExist()
        compose.onNodeWithText("Please note").assertDoesNotExist()
        compose.onNodeWithText("Share").assertIsEnabled()
    }

    @Test
    fun `given findings when shown then each warning names its rooms and unnamed rooms get a number`() {
        show(ExportScreenStates.warnings)
        compose.onNodeWithText("Please note").performScrollTo().assertExists()
        compose
            .onNodeWithText(
                "2 rooms are left out of the drawing because their shape is open or contradictory: Shed, Room 4.",
            ).assertExists()
        compose.onNodeWithText("1 room is marked \"shape not confirmed\" in the drawing: Living room.").assertExists()
        compose.onNodeWithText("2 openings no longer fit on their walls in Kitchen (2).").assertExists()
    }

    @Test
    @Config(qualifiers = "de-w360dp-h1200dp")
    fun `given findings when german then the warnings are german`() {
        show(ExportScreenStates.warnings)
        compose.onNodeWithText("Bitte beachten").assertExists()
        compose
            .onNodeWithText("1 Raum ist in der Zeichnung mit „Form nicht bestätigt“ gekennzeichnet: Living room.")
            .assertExists()
    }

    @Test
    fun `given an empty drawing when shown then the block is explained and sharing is disabled`() {
        show(ExportScreenStates.emptyDrawing)
        compose
            .onNodeWithText(
                "No room has a usable shape yet, so there is nothing to draw. PDF, PNG and SVG are not possible. " +
                    "JSON still contains all rooms.",
            ).assertExists()
        compose.onNodeWithText("Share").assertIsNotEnabled()
        compose.onNodeWithText("Save as file").assertIsNotEnabled()
    }

    @Test
    fun `given a plan that does not fit the choice when shown then the cause and the way out are explained`() {
        show(ExportScreenStates.doesNotFit)
        compose
            .onNodeWithText(
                "The plan does not fit with this scale and paper. Choose a smaller scale or a larger paper, " +
                    "or set both to automatic.",
            ).assertExists()
        compose.onNodeWithText("Share").assertIsNotEnabled()
    }

    @Test
    fun `given a plan that is too large when shown then it is explained`() {
        show(ExportScreenStates.tooLarge)
        compose.onNodeWithText("The plan is too large. It does not fit on A3 even at 1:200.").assertExists()
    }

    @Test
    fun `given a running export when shown then progress is visible and nothing can be started`() {
        show(ExportScreenStates.standard.copy(running = ExportAction.SHARE))
        compose.onNodeWithText("Exporting").assertExists()
        compose.onNodeWithText("Share").assertIsNotEnabled()
        compose.onNodeWithText("Save as file").assertIsNotEnabled()
        compose.onNodeWithText("SVG").assertIsNotEnabled()
        compose.onNodeWithText("1:100").assertIsNotEnabled()
    }

    @Test
    fun `given a failed export when shown then the cause, retry and dismiss are offered`() {
        show(ExportScreenStates.standard.copy(error = DomainError.Export(ExportProblem.NO_SPACE)))
        compose.onNodeWithText("Export failed").assertExists()
        compose.onNodeWithText("There is not enough free storage space for the file.").assertExists()
        compose.onNodeWithText("Try again").performClick()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(listOf("retry", "dismissError"), calls)
    }

    @Test
    fun `given a saved file when shown then the confirmation can be dismissed`() {
        show(ExportScreenStates.standard.copy(notice = ExportNotice.Saved(ExportFormat.PNG)))
        compose.onNodeWithText("Saved as PNG file.").assertExists()
        compose.onNodeWithText("Dismiss").performClick()
        assertEquals(listOf("dismissNotice"), calls)
    }

    @Test
    fun `given the screen when measured then every control is at least 48 dp high`() {
        show(ExportScreenStates.standard)
        listOf("Share", "Save as file", "PDF", "JSON", "1:50", "A4").forEach {
            compose.onNode(hasText(it)).assertHeightIsAtLeast(48.dp)
        }
        compose.onAllNodesWithText("Automatic")[0].assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Back").assertHeightIsAtLeast(48.dp)
    }
}
