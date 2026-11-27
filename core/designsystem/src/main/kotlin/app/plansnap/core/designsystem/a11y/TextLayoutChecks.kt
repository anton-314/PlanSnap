package app.plansnap.core.designsystem.a11y

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.style.TextOverflow

// The text rules of AccessibilityAudit, which judge how a text was laid out.

/** How [node] laid out its texts, empty for a node without text. */
internal fun textLayouts(node: SemanticsNode): List<TextLayoutResult> {
    val results = mutableListOf<TextLayoutResult>()
    node.config
        .getOrNull(SemanticsActions.GetTextLayoutResult)
        ?.action
        ?.invoke(results)
    return results
}

/** The violation of a text rule by [result], `null` if it is laid out well. */
internal fun textFinding(result: TextLayoutResult): AccessibilityFinding? {
    val text = result.layoutInput.text.text
    val ellipsized = result.layoutInput.overflow != TextOverflow.Clip
    val rule =
        when {
            !ellipsized && isCutOff(result) -> AccessibilityRule.CLIPPED_TEXT
            breaksWord(result, text) -> AccessibilityRule.BROKEN_WORD
            else -> return null
        }
    return AccessibilityFinding(rule, "'$text' in ${result.size.width} x ${result.size.height} px")
}

/** Whether lines are missing or lower than needed, or a line is wider than the text may be. */
private fun isCutOff(result: TextLayoutResult): Boolean =
    result.didOverflowHeight ||
        (0 until result.lineCount).any { result.getLineRight(it) - result.getLineLeft(it) > result.size.width + 1 }

private fun breaksWord(
    result: TextLayoutResult,
    text: String,
): Boolean =
    (0 until result.lineCount - 1).any { line ->
        val end = result.getLineEnd(line)
        end in 1 until text.length && text[end - 1].isLetterOrDigit() && text[end].isLetterOrDigit()
    }
