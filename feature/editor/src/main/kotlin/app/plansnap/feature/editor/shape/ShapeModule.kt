package app.plansnap.feature.editor.shape

import app.plansnap.feature.editor.state.EditorHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Contributes the [ShapeHandler] to the set of editor handlers. */
@Module
@InstallIn(SingletonComponent::class)
abstract class ShapeModule {
    /** The handler of the shape sheets. */
    @Binds
    @IntoSet
    abstract fun shapeHandler(handler: ShapeHandler): EditorHandler
}
