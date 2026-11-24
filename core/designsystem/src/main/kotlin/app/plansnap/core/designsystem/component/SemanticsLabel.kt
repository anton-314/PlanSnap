package app.plansnap.core.designsystem.component

import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

/** Replaces the spoken label of a control when [label] is given; otherwise leaves the visible text in place. */
internal fun Modifier.semanticsLabel(label: String?): Modifier =
    if (label == null) this else semantics { contentDescription = label }
