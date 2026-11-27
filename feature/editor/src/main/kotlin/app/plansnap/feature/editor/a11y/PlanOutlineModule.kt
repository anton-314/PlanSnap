package app.plansnap.feature.editor.a11y

import app.plansnap.feature.editor.state.EditorHandler
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet

/** Contributes the [PlanOutlineHandler] to the set of editor handlers. */
@Module
@InstallIn(SingletonComponent::class)
abstract class PlanOutlineModule {
    /** The handler of the plan outline. */
    @Binds
    @IntoSet
    abstract fun planOutlineHandler(handler: PlanOutlineHandler): EditorHandler
}
