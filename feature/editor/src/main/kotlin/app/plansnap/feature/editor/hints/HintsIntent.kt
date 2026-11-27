package app.plansnap.feature.editor.hints

import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.feature.editor.state.EditorIntent

/** Intents of the one-time hints (UX section 5), consumed by [HintsHandler]. */
sealed interface HintsIntent : EditorIntent {
    /** The user closed [hint]. It does not appear again until the hints are reset in the settings. */
    data class Dismiss(
        val hint: EditorHint,
    ) : HintsIntent
}
