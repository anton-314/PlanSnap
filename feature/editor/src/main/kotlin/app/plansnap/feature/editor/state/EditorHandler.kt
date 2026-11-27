package app.plansnap.feature.editor.state

import app.plansnap.core.domain.command.ProjectEdit
import app.plansnap.core.model.Project
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.StateFlow

/**
 * What a sub-function handler may do with the editor. The view model implements it with an ordered work queue, so
 * handlers never touch the history or the saver directly.
 */
interface EditorHost {
    /** Current state. Read it at the time of the intent, never capture it across suspension points. */
    val state: StateFlow<EditorUiState>

    /**
     * Scope of the screen. Work launched here is cancelled when the editor is left, so only previews and evaluations
     * belong in it (ARCHITECTURE section 5). A handler that needs a newer run to replace an older one collects
     * [state] with `collectLatest`.
     */
    val scope: CoroutineScope

    /** Replaces the state by the result of the pure [transform]. It may be called more than once per update. */
    fun update(transform: (EditorUiState) -> EditorUiState)

    /**
     * Executes [edit] as one confirmed command: in the application scope, in call order, never cancelled by leaving
     * the editor, followed by saving. A failure is reported as [EditorEffect.Error] or [SaveStatus.Failed].
     */
    fun submit(edit: ProjectEdit)

    /**
     * Like [submit], for an edit that depends on the commands queued before it: [build] receives the project that is
     * committed when the turn of the edit comes. [onCommitted] runs once the command changed that project, before it
     * is saved and before any later command runs. It does not run if the command is rejected.
     *
     * A host without a work queue keeps this default: it builds the edit from the displayed project, submits it and
     * never calls [onCommitted], because it does not learn the outcome.
     */
    fun submitQueued(
        build: (Project) -> ProjectEdit,
        onCommitted: () -> Unit,
    ) {
        state.value.project?.let { submit(build(it)) }
    }

    /** Sends a one-time [effect] to the screen. */
    fun send(effect: EditorEffect)
}

/**
 * Handler of one sub-function of the editor. Handlers are collected by Hilt as a set: a module binds one with
 * `@Binds @IntoSet`, and neither the view model nor `EditorUiState` changes.
 */
fun interface EditorHandler {
    /**
     * Handles [intent] if it belongs to this handler.
     *
     * @return `true` if the intent was consumed, `false` to offer it to the next handler.
     */
    fun handle(
        intent: EditorIntent,
        host: EditorHost,
    ): Boolean
}

/** Declares the set of [EditorHandler]s, which is empty until a sub-function contributes one. */
@Module
@InstallIn(SingletonComponent::class)
abstract class EditorHandlerModule {
    /** The handlers of all sub-functions of the editor. */
    @Multibinds
    abstract fun handlers(): Set<@JvmSuppressWildcards EditorHandler>
}
