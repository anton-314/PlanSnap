package app.plansnap.feature.editor.details

import app.plansnap.feature.editor.state.EditorHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Contributes the [DetailsHandler] to the set of editor handlers. */
@Module
@InstallIn(SingletonComponent::class)
abstract class DetailsModule {
    /** The handler of the details sheet and the area overview. */
    @Binds
    @IntoSet
    abstract fun detailsHandler(handler: DetailsHandler): EditorHandler
}
