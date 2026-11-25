package app.plansnap.feature.editor.navigation

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(qualifiers = "en")
class EditorEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private var backs = 0
    private var exports = 0

    private fun show() {
        compose.setContent {
            PlanSnapTheme {
                EditorEntry(EditorNavigationActions(onBack = { backs++ }, onOpenExport = { exports++ }))
            }
        }
    }

    @Test
    fun showsTitleAndEmptyState() {
        show()
        compose.onNodeWithText("Editor").assertExists()
        compose.onNodeWithText("Enter the first wall").assertExists()
    }

    @Test
    fun backAndExportInvokeTheirCallbacks() {
        show()
        compose.onNodeWithContentDescription("Back").performClick()
        compose.onNodeWithText("Export").performClick()
        assertEquals(1, backs)
        assertEquals(1, exports)
    }

    @Test
    @Config(qualifiers = "de")
    fun germanEmptyState() {
        show()
        compose.onNodeWithText("Erste Wand eingeben").assertExists()
    }
}
