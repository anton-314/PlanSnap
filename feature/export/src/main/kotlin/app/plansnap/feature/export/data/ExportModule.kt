package app.plansnap.feature.export.data

import app.plansnap.core.drawing.plot.DefaultPlotLayout
import app.plansnap.core.drawing.plot.PlotLayout
import app.plansnap.core.export.Exporter
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.Multibinds
import javax.inject.Singleton

/**
 * Bindings of the export feature that need nothing from `:app`.
 *
 * `:app` contributes one [Exporter] per format with `@Provides @IntoSet` and may use the [PlotLayout] bound here.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class ExportModule {
    /** File access for sharing and saving. */
    @Binds
    abstract fun exportFiles(files: AndroidExportFiles): ExportFiles

    /** Page preview of the dialog. */
    @Binds
    abstract fun plotPreview(preview: DefaultPlotPreview): PlotPreview

    /** Declares the exporter set, so that the graph is valid before any exporter is contributed. */
    @Multibinds
    abstract fun exporters(): Set<@JvmSuppressWildcards Exporter>

    internal companion object {
        /** Scale and page choice shared by the preview and all drawing exporters. Stateless. */
        @Provides
        @Singleton
        fun plotLayout(): PlotLayout = DefaultPlotLayout()
    }
}
