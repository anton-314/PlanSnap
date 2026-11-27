package app.plansnap.feature.editor.opening

import app.plansnap.feature.editor.state.EditorHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Contributes the [OpeningHandler] to the set of editor handlers. */
@Module
@InstallIn(SingletonComponent::class)
abstract class OpeningModule {
    /** The handler of the opening sheets. */
    @Binds
    @IntoSet
    abstract fun openingHandler(handler: OpeningHandler): EditorHandler
}
