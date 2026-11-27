package app.plansnap.feature.editor.hints

import app.plansnap.feature.editor.state.EditorHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Contributes the [HintsHandler] to the set of editor handlers. */
@Module
@InstallIn(SingletonComponent::class)
abstract class HintsModule {
    /** The handler of the one-time hints. */
    @Binds
    @IntoSet
    abstract fun hintsHandler(handler: HintsHandler): EditorHandler
}
