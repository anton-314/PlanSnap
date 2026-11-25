package app.plansnap.feature.settings.ui

import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertHeightIsAtLeast
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsOff
import androidx.compose.ui.test.assertIsOn
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.StorageProblem
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.EditorHint
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.coroutines.Dispatchers
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class SettingsScreenTest {
    @get:Rule
    val compose = createComposeRule()

    private var repository = FakeSettingsRepository()
    private var backs = 0

    private fun show() {
        val viewModel = SettingsViewModel(repository, TestDispatchers(Dispatchers.Main))
        compose.setContent {
            PlanSnapTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                SettingsScreen(
                    state = state,
                    actions =
                        SettingsActions(
                            onUnitSelected = viewModel::onUnitSelected,
                            onAbsoluteToleranceChanged = viewModel::onAbsoluteToleranceChanged,
                            onRelativeToleranceChanged = viewModel::onRelativeToleranceChanged,
                            onGridChanged = viewModel::onGridChanged,
                            onSnapChanged = viewModel::onSnapChanged,
                            onResetHints = viewModel::onResetHints,
                            onSaveErrorDismissed = viewModel::onSaveErrorDismissed,
                        ),
                    onBack = { backs++ },
                )
            }
        }
        compose.waitForIdle()
    }

    private fun absoluteField() = compose.onAllNodes(hasSetTextAction())[0]

    private fun relativeField() = compose.onAllNodes(hasSetTextAction())[1]

    @Test
    fun showsTitleSectionsAndDefaults() {
        show()
        compose.onNodeWithText("Settings").assertExists()
        compose.onNodeWithText("Unit").assertExists()
        compose.onNodeWithText("Closure tolerance").assertExists()
        compose.onNodeWithText("Grid and snapping").assertExists()
        compose.onNodeWithText("Centimeters (cm)").assertIsSelected()
        absoluteField().assertTextContains("5.0")
        relativeField().assertTextContains("1")
        compose.onNodeWithText("Show grid").assertIsOn()
        compose.onNodeWithText("Snap to grid").assertIsOn()
    }

    @Test
    fun backInvokesCallback() {
        show()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    fun unitChoiceIsStoredAndMirrored() {
        show()
        compose.onNodeWithText("Meters (m)").performClick()
        compose.waitForIdle()
        assertEquals(LengthUnit.METER, repository.value.lengthUnit)
        compose.onNodeWithText("Meters (m)").assertIsSelected()
        compose.onNodeWithText("Centimeters (cm)").assertIsNotSelected()
        absoluteField().assertTextContains("0.050")
    }

    @Test
    fun validAbsoluteToleranceIsStored() {
        show()
        absoluteField().performTextReplacement("8")
        compose.waitForIdle()
        assertEquals(80L, repository.value.tolerance.absoluteMm)
    }

    @Test
    fun invalidAbsoluteToleranceIsExplainedAndNotStored() {
        show()
        absoluteField().performTextReplacement("0")
        compose.waitForIdle()
        compose.onNodeWithText("The absolute limit must be at least 1 mm.").assertExists()
        assertEquals(50L, repository.value.tolerance.absoluteMm)
        assertEquals(0, repository.updateCalls)
        absoluteField().performTextReplacement("abc")
        compose.waitForIdle()
        compose.onNodeWithText("Enter a number, for example 5,0 or 5.0.").assertExists()
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun validRelativeToleranceIsStored() {
        show()
        relativeField().performTextReplacement("3")
        compose.waitForIdle()
        assertEquals(0.03, repository.value.tolerance.relative, 1e-12)
    }

    @Test
    fun invalidRelativeToleranceIsExplainedAndNotStored() {
        show()
        relativeField().performTextReplacement("150")
        compose.waitForIdle()
        compose.onNodeWithText("The relative limit must not exceed 100 percent.").assertExists()
        assertEquals(0.01, repository.value.tolerance.relative, 0.0)
        assertEquals(0, repository.updateCalls)
    }

    @Test
    fun gridSwitchIsStored() {
        show()
        compose.onNodeWithText("Show grid").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, repository.value.gridEnabled)
        compose.onNodeWithText("Show grid").assertIsOff()
    }

    @Test
    fun snapSwitchIsStored() {
        show()
        compose.onNodeWithText("Snap to grid").performScrollTo().performClick()
        compose.waitForIdle()
        assertEquals(false, repository.value.snapEnabled)
        compose.onNodeWithText("Snap to grid").assertIsOff()
    }

    @Test
    fun resetHintsIsDisabledWhenNothingIsDismissed() {
        show()
        compose.onNodeWithText("All hints are active and appear again in the editor.").assertExists()
        compose.onNodeWithText("Reset hints").assertIsNotEnabled()
    }

    @Test
    fun resetHintsClearsDismissedHints() {
        repository =
            FakeSettingsRepository(
                AppSettings(dismissedHints = persistentSetOf(EditorHint.NUMPAD, EditorHint.CLOSE)),
            )
        show()
        compose.onNodeWithText("2 hints are closed.").assertExists()
        compose
            .onNodeWithText("Reset hints")
            .performScrollTo()
            .assertIsEnabled()
            .performClick()
        compose.waitForIdle()
        assertEquals(0, repository.value.dismissedHints.size)
        compose.onNodeWithText("Reset hints").assertIsNotEnabled()
    }

    @Test
    fun saveFailureIsShownAndCanBeDismissed() {
        show()
        repository.failure = DomainError.Storage(StorageProblem.WRITE_FAILED)
        compose.onNodeWithText("Show grid").performScrollTo().performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Not saved").assertExists()
        compose.onNodeWithText("The setting could not be saved. Try again.").assertExists()
        compose.onNodeWithText("Show grid").assertIsOn()
        compose.onNodeWithText("Dismiss").performClick()
        compose.waitForIdle()
        compose.onNodeWithText("Not saved").assertDoesNotExist()
    }

    @Test
    fun interactiveRowsHaveTouchTargetsOfAtLeast48dp() {
        show()
        listOf("Millimeters (mm)", "Show grid", "Snap to grid").forEach {
            compose.onNodeWithText(it).assertHeightIsAtLeast(48.dp)
        }
        compose.onNodeWithText("Reset hints").assertHeightIsAtLeast(48.dp)
        compose.onNodeWithContentDescription("Back").assertHeightIsAtLeast(48.dp)
    }

    @Test
    @Config(qualifiers = "de")
    fun germanTexts() {
        show()
        compose.onNodeWithText("Einstellungen").assertExists()
        compose.onNodeWithText("Hinweise zurücksetzen").assertExists()
        compose.onNodeWithText("Raster anzeigen").assertExists()
    }
}
