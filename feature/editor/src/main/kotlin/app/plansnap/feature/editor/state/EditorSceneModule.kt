package app.plansnap.feature.editor.state

import app.plansnap.core.drawing.scene.DefaultSceneBuilder
import app.plansnap.core.drawing.scene.SceneBuilder
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Provides the stateless scene builder that the editor state layer draws with. */
@Module
@InstallIn(SingletonComponent::class)
object EditorSceneModule {
    /** The scene builder of ADR 0008. */
    @Provides
    @Singleton
    fun sceneBuilder(): SceneBuilder = DefaultSceneBuilder()
}
