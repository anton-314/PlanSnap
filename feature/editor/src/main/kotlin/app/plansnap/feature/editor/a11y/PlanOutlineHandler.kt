package app.plansnap.feature.editor.a11y

import app.plansnap.feature.editor.state.EditorHandler
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorIntent
import javax.inject.Inject

/** Opens and closes the plan outline. It changes no model data. */
class PlanOutlineHandler
    @Inject
    constructor() : EditorHandler {
        override fun handle(
            intent: EditorIntent,
            host: EditorHost,
        ): Boolean {
            if (intent !is PlanOutlineIntent) return false
            val open = intent == PlanOutlineIntent.Open
            host.update { it.withSection(PlanOutlineState::class, PlanOutlineState(open)) }
            return true
        }
    }
