package app.plansnap.core.designsystem.a11y

import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull

private const val MIN_TARGET_DP = 48f

/** Tolerance for the rounding of dp to whole pixels. */
private const val ROUNDING_DP = 0.5f

/** A rule of [AccessibilityAudit] (docs/UX.md sections 6 and 7, REQ-UX-008). */
enum class AccessibilityRule {
    /** A control or image has nothing a screen reader could say: no text and no content description. */
    MISSING_LABEL,

    /** A control reserves less than 48 dp in width or height. */
    SMALL_TARGET,

    /** A text does not fit its bounds and is cut off without an ellipsis. */
    CLIPPED_TEXT,

    /** A line break splits a word, which happens when a large font squeezes a text into a narrow column. */
    BROKEN_WORD,
}

/**
 * One violation found by [AccessibilityAudit].
 *
 * @property rule the violated rule.
 * @property description what the node says or shows and its size, for a failure message.
 */
data class AccessibilityFinding(
    val rule: AccessibilityRule,
    val description: String,
)

/**
 * Automatic accessibility checks on a Compose semantics tree, the tree that TalkBack reads. The checks need no
 * device, so screen tests run them for every state of a screen.
 */
object AccessibilityAudit {
    /**
     * Checks every control and image below [roots]: it must have a spoken label with at least one letter or digit,
     * and a control must reserve at least 48 dp in both directions.
     *
     * Pass the roots of the merged tree, one per window, so that a control is judged with the text of its children,
     * as a screen reader does. Nodes that are hidden from accessibility are skipped with their children.
     */
    fun controls(roots: List<SemanticsNode>): List<AccessibilityFinding> =
        roots.flatMap(::visibleNodes).flatMap { node ->
            listOfNotNull(missingLabel(node), smallTarget(node))
        }

    /**
     * Checks every laid out text below [roots]: it must not be cut off, unless it asks for an ellipsis, and no line
     * may break inside a word.
     *
     * Pass the roots of the unmerged tree, one per window.
     */
    fun texts(roots: List<SemanticsNode>): List<AccessibilityFinding> =
        roots.flatMap(::visibleNodes).flatMap { node ->
            textLayouts(node).mapNotNull(::textFinding)
        }

    @Suppress("DEPRECATION") // InvisibleToUser is deprecated but still honored by the framework.
    private fun visibleNodes(node: SemanticsNode): List<SemanticsNode> {
        val hidden =
            SemanticsProperties.HideFromAccessibility in node.config ||
                SemanticsProperties.InvisibleToUser in node.config
        return if (hidden) emptyList() else listOf(node) + node.children.flatMap(::visibleNodes)
    }

    private fun SemanticsNode.isControl(): Boolean =
        SemanticsActions.OnClick in config ||
            SemanticsActions.OnLongClick in config ||
            SemanticsActions.SetText in config ||
            SemanticsActions.SetProgress in config

    private fun spokenLabel(node: SemanticsNode): String {
        val description = node.config.getOrNull(SemanticsProperties.ContentDescription).orEmpty()
        if (description.any { it.isNotBlank() }) return description.joinToString()
        return node.config
            .getOrNull(SemanticsProperties.Text)
            .orEmpty()
            .joinToString { it.text }
    }

    private fun missingLabel(node: SemanticsNode): AccessibilityFinding? {
        val needsLabel = node.isControl() || node.config.getOrNull(SemanticsProperties.Role) == Role.Image
        if (!needsLabel || isSpeakable(node) || isLongPressWrapper(node)) return null
        return AccessibilityFinding(AccessibilityRule.MISSING_LABEL, describe(node))
    }

    private fun isSpeakable(node: SemanticsNode): Boolean = spokenLabel(node).any { it.isLetterOrDigit() }

    /**
     * Whether [node] only adds a long press around a labelled control of the same bounds, as the tooltip of Material
     * does around the drag handle of a bottom sheet. A screen reader focuses the control inside.
     */
    private fun isLongPressWrapper(node: SemanticsNode): Boolean =
        SemanticsActions.OnLongClick in node.config &&
            SemanticsActions.OnClick !in node.config &&
            node.children.any { it.isControl() && it.boundsInRoot == node.boundsInRoot && isSpeakable(it) }

    private fun smallTarget(node: SemanticsNode): AccessibilityFinding? {
        // The size of the layout node includes the space that Material reserves around a small control, which the
        // touch area fills. The touch bounds alone prove nothing: Compose widens them even where they overlap.
        val info = node.layoutInfo
        val smallest = minOf(info.width, info.height) / info.density.density
        val tooSmall = node.isControl() && smallest < MIN_TARGET_DP - ROUNDING_DP
        return if (tooSmall) AccessibilityFinding(AccessibilityRule.SMALL_TARGET, describe(node)) else null
    }

    private fun describe(node: SemanticsNode): String {
        val info = node.layoutInfo
        val density = info.density.density
        val tag = node.config.getOrNull(SemanticsProperties.TestTag).orEmpty()
        return "'${spokenLabel(node)}' $tag ${info.width / density} x ${info.height / density} dp " +
            "at ${node.boundsInRoot}"
    }
}
