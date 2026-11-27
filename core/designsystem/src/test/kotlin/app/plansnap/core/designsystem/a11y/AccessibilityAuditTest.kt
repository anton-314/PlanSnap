package app.plansnap.core.designsystem.a11y

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.onLongClick
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import app.plansnap.core.designsystem.component.PlanSnapButton
import app.plansnap.core.designsystem.component.PlanSnapIconButton
import app.plansnap.core.designsystem.component.PlanSnapIcons
import app.plansnap.core.designsystem.component.PlanSnapNumericKey
import app.plansnap.core.designsystem.component.PlanSnapOutlinedButton
import app.plansnap.core.designsystem.theme.PlanSnapTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/** Proves that every rule of [AccessibilityAudit] finds its violation and accepts correct content. */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [36], qualifiers = "en-rUS-w360dp-h640dp-port-notnight-mdpi")
class AccessibilityAuditTest {
    @get:Rule
    val compose = createComposeRule()

    private fun show(content: @Composable () -> Unit) = compose.setContent { PlanSnapTheme { content() } }

    private fun controlRules(): List<AccessibilityRule> =
        AccessibilityAudit.controls(compose.onAllNodes(isRoot()).fetchSemanticsNodes()).map { it.rule }

    private fun textRules(): List<AccessibilityRule> =
        AccessibilityAudit
            .texts(compose.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes())
            .map { it.rule }

    @Test
    fun `given the components of the design system when audited then nothing is found`() {
        show {
            Column {
                PlanSnapButton("OK", onClick = {})
                PlanSnapOutlinedButton("Left", onClick = {})
                PlanSnapIconButton(PlanSnapIcons.Back, "Back", onClick = {})
                PlanSnapNumericKey("7", onClick = {})
                TextField(value = "", onValueChange = {}, label = { Text("Name") })
            }
        }

        assertEquals(emptyList<AccessibilityRule>(), controlRules())
        assertEquals(emptyList<AccessibilityRule>(), textRules())
    }

    @OptIn(ExperimentalFoundationApi::class)
    @Test
    fun `given an unlabelled clickable around a labelled control when audited then it is reported`() {
        show {
            Box(Modifier.combinedClickable(onLongClick = {}, onClick = {})) {
                Box(Modifier.size(48.dp).clickable {}) { Text("Inside") }
            }
        }

        assertEquals(listOf(AccessibilityRule.MISSING_LABEL), controlRules())
    }

    @Test
    fun `given only a long press around a labelled control of the same size when audited then it is accepted`() {
        show {
            Box(Modifier.semantics(mergeDescendants = true) { onLongClick { true } }) {
                Box(Modifier.size(48.dp).clickable {}) { Text("Inside") }
            }
        }

        assertEquals(emptyList<AccessibilityRule>(), controlRules())
    }

    @Test
    fun `given a control without a label when audited then it is reported`() {
        show { Box(Modifier.size(48.dp).clickable {}) }

        assertEquals(listOf(AccessibilityRule.MISSING_LABEL), controlRules())
    }

    @Test
    fun `given a control labelled with a symbol only when audited then it is reported`() {
        show { Box(Modifier.size(48.dp).clickable {}) { Text("∠") } }

        assertEquals(listOf(AccessibilityRule.MISSING_LABEL), controlRules())
    }

    @Test
    fun `given a text field without a label when audited then it is reported`() {
        show { TextField(value = "", onValueChange = {}) }

        assertEquals(listOf(AccessibilityRule.MISSING_LABEL), controlRules())
    }

    @Test
    fun `given a control lower than 48 dp when audited then it is reported`() {
        show {
            Box(
                Modifier
                    .width(48.dp)
                    .height(47.dp)
                    .clickable {}
                    .semantics { contentDescription = "low" },
            )
        }

        assertEquals(listOf(AccessibilityRule.SMALL_TARGET), controlRules())
    }

    @Test
    fun `given a hidden control when audited then it is skipped`() {
        show { Box(Modifier.size(1.dp).semantics { hideFromAccessibility() }.clickable {}) }

        assertEquals(emptyList<AccessibilityRule>(), controlRules())
    }

    @Test
    fun `given a text higher than its bounds when audited then it is reported as clipped`() {
        show { Text("Wall", modifier = Modifier.height(5.dp)) }

        assertEquals(listOf(AccessibilityRule.CLIPPED_TEXT), textRules())
    }

    @Test
    fun `given a text that asks for an ellipsis when audited then it is accepted`() {
        show {
            Text(
                "A long project name",
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.width(40.dp),
            )
        }

        assertEquals(emptyList<AccessibilityRule>(), textRules())
    }

    @Test
    fun `given a word wider than its column when audited then the broken word is reported`() {
        show { Text("Wandstaerke", modifier = Modifier.width(40.dp)) }

        assertEquals(listOf(AccessibilityRule.BROKEN_WORD), textRules())
    }

    @Test
    fun `given a text that wraps between words when audited then it is accepted`() {
        show { Text("one two three four five six", modifier = Modifier.width(60.dp)) }

        assertEquals(emptyList<AccessibilityRule>(), textRules())
    }
}
