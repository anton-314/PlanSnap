package app.plansnap.feature.editor.hints

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.ApplicationScope
import app.plansnap.core.domain.repository.EditorHint
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.feature.editor.state.EditorEffect
import app.plansnap.feature.editor.state.EditorHandler
import app.plansnap.feature.editor.state.EditorHost
import app.plansnap.feature.editor.state.EditorIntent
import kotlinx.collections.immutable.ImmutableSet
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableSet
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Handler of the one-time hints (T-EDT-009, REQ-UX-006). A closed hint is stored in
 * `AppSettings.dismissedHints`, the set that the settings screen clears to show all hints again.
 *
 * The write runs in the [ApplicationScope], so leaving the editor right after closing a hint does not lose it. A
 * failure is reported as [EditorEffect.Error].
 */
class HintsHandler
    @Inject
    constructor(
        private val settings: SettingsRepository,
        private val applicationScope: ApplicationScope,
    ) : EditorHandler {
        override fun handle(
            intent: EditorIntent,
            host: EditorHost,
        ): Boolean {
            when (intent) {
                is HintsIntent.Dismiss -> dismiss(intent.hint, host)
                else -> return false
            }
            return true
        }

        private fun dismiss(
            hint: EditorHint,
            host: EditorHost,
        ) {
            val state = host.state.value
            val closing = state.section(HintsState::class)?.closing.orEmpty()
            if (hint in state.settings.dismissedHints || hint in closing) return
            host.updateClosing { (it + hint).toImmutableSet() }
            applicationScope.launch {
                val result =
                    settings.update { it.copy(dismissedHints = (it.dismissedHints + hint).toImmutableSet()) }
                if (result is DomainResult.Failure) host.send(EditorEffect.Error(result.error))
            }
            // Once the editor sees the stored dismissal, the settings alone decide, so a later reset takes effect.
            host.scope.launch {
                host.state.first { hint in it.settings.dismissedHints }
                host.updateClosing { (it - hint).toImmutableSet() }
            }
        }

        private fun EditorHost.updateClosing(transform: (Set<EditorHint>) -> ImmutableSet<EditorHint>) {
            update {
                val closing = it.section(HintsState::class)?.closing ?: persistentSetOf()
                it.withSection(HintsState::class, HintsState(transform(closing)))
            }
        }
    }
