package app.plansnap.di

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.document.DocumentCodec
import app.plansnap.core.drawing.plot.PlotLayout
import app.plansnap.core.drawing.scene.SceneBuilder
import app.plansnap.core.export.Exporter
import app.plansnap.core.export.json.JsonExporter
import app.plansnap.core.export.pdf.PdfExporter
import app.plansnap.core.export.png.PngExporter
import app.plansnap.core.export.svg.SvgExporter
import app.plansnap.core.render.LabelTextFormatter
import app.plansnap.feature.editor.details.RoomTypeCatalog
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import java.util.Locale
import javax.inject.Singleton

/** Contributes one exporter per format to the `Set<Exporter>` declared by the export feature (T-FEX-001). */
@Module
@InstallIn(SingletonComponent::class)
object ExportersModule {
    /**
     * Creates the label formatter of an export. All texts, including the room type names of the editor's catalog
     * (REQ-MOD-011), come from the resources of the requested locale, not of the device configuration.
     */
    @SuppressLint("AppBundleLocaleChanges") // APK only (ADR 0014): no bundle split can drop a language.
    internal fun formatterFactory(context: Context): (Locale, LengthUnit) -> LabelTextFormatter =
        { locale, unit ->
            val configuration = Configuration(context.resources.configuration).apply { setLocale(locale) }
            val resources = context.createConfigurationContext(configuration).resources
            LabelTextFormatter(resources, locale, unit) { RoomTypeCatalog.displayName(resources, it) }
        }

    /** SVG export. */
    @Provides
    @IntoSet
    @Singleton
    fun svgExporter(
        sceneBuilder: SceneBuilder,
        plotLayout: PlotLayout,
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): Exporter = SvgExporter(sceneBuilder, plotLayout, formatterFactory(context), dispatchers)

    /** JSON export of the whole project. */
    @Provides
    @IntoSet
    @Singleton
    fun jsonExporter(
        codec: DocumentCodec,
        dispatchers: DispatcherProvider,
    ): Exporter = JsonExporter(codec, dispatchers)

    /** PNG export. */
    @Provides
    @IntoSet
    @Singleton
    fun pngExporter(
        sceneBuilder: SceneBuilder,
        plotLayout: PlotLayout,
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): Exporter = PngExporter(sceneBuilder, plotLayout, formatterFactory(context), dispatchers)

    /** PDF export. */
    @Provides
    @IntoSet
    @Singleton
    fun pdfExporter(
        sceneBuilder: SceneBuilder,
        plotLayout: PlotLayout,
        @ApplicationContext context: Context,
        dispatchers: DispatcherProvider,
    ): Exporter = PdfExporter(sceneBuilder, plotLayout, formatterFactory(context), dispatchers)
}
