package app.plansnap.feature.editor.rooms

import app.plansnap.feature.editor.state.EditorHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Contributes the [RoomsHandler] to the set of editor handlers. */
@Module
@InstallIn(SingletonComponent::class)
abstract class RoomsModule {
    /** The handler of the rooms function. */
    @Binds
    @IntoSet
    abstract fun roomsHandler(handler: RoomsHandler): EditorHandler
}
