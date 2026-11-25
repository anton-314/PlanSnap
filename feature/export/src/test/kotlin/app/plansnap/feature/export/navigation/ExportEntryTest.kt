package app.plansnap.feature.export.navigation

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
class ExportEntryTest {
    @get:Rule
    val compose = createComposeRule()

    private var backs = 0

    private fun show() {
        compose.setContent { PlanSnapTheme { ExportEntry(onBack = { backs++ }) } }
    }

    @Test
    fun showsTitleAndEmptyState() {
        show()
        compose.onNodeWithText("Export").assertExists()
        compose.onNodeWithText("No room yet").assertExists()
    }

    @Test
    fun backInvokesCallback() {
        show()
        compose.onNodeWithContentDescription("Back").performClick()
        assertEquals(1, backs)
    }

    @Test
    @Config(qualifiers = "de")
    fun germanEmptyState() {
        show()
        compose.onNodeWithText("Noch kein Raum").assertExists()
    }
}
