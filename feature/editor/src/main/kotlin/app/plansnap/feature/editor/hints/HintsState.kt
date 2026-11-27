package app.plansnap.feature.editor.hints

import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.feature.editor.state.EditorSectionState
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf

/**
 * Section state of the hints.
 *
 * @property closing hints the user has closed that the settings of the editor do not report as dismissed yet. They
 * are hidden at once. A hint whose dismissal could not be stored stays here, so it stays hidden until the editor is
 * left, and appears again the next time.
 */
data class HintsState(
    val closing: ImmutableSet<EditorHint> = persistentSetOf(),
) : EditorSectionState
