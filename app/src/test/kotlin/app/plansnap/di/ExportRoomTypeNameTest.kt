package app.plansnap.di

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.RoomEvaluator
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.Exporter
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import dagger.hilt.android.testing.HiltTestApplication
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentMapOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import java.io.ByteArrayOutputStream
import java.util.Locale
import javax.inject.Inject

/**
 * The exporters of the production Hilt graph draw the display name of a room type in the language of the request, not
 * the stored key (REQ-MOD-011). The device language is neither German nor English unless a test says otherwise.
 */
@HiltAndroidTest
@RunWith(RobolectricTestRunner::class)
@Config(application = HiltTestApplication::class, qualifiers = "fr")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ExportRoomTypeNameTest {
    @get:Rule
    val hilt = HiltAndroidRule(this)

    @Inject lateinit var exporters: Set<@JvmSuppressWildcards Exporter>

    @Inject lateinit var evaluator: RoomEvaluator

    @Before
    fun inject() = hilt.inject()

    @Test
    fun `given a german request when exporting svg then the german type name replaces the key`() {
        val svg = svg("kitchen", Locale.GERMANY)
        assertTrue(svg, svg.contains(">Küche<"))
        assertFalse(svg, svg.contains(">kitchen<"))
    }

    @Test
    fun `given an english request when exporting svg then the english type name replaces the key`() {
        val svg = svg("kitchen", Locale.US)
        assertTrue(svg, svg.contains(">Kitchen<"))
        assertFalse(svg, svg.contains(">kitchen<"))
    }

    @Test
    @Config(qualifiers = "de")
    fun `given a german device when exporting in english then the request language wins`() {
        val svg = svg("living", Locale.ENGLISH)
        assertTrue(svg, svg.contains(">Living room<"))
    }

    @Test
    fun `given a key outside the catalog when exporting svg then it is shown as it is`() {
        val svg = svg("winter-garden", Locale.GERMANY)
        assertTrue(svg, svg.contains(">winter-garden<"))
    }

    @Test
    fun `given a german request when exporting png then it shows the german type name`() {
        assertPngDrawsName(Locale.GERMANY, "Küche")
    }

    @Test
    fun `given an english request when exporting png then it shows the english type name`() {
        assertPngDrawsName(Locale.US, "Kitchen")
    }

    /**
     * A raster image holds no text, so it is compared with the image of a room whose unknown key is the expected name
     * and is therefore drawn literally. A second literal key proves that the label changes the pixels at all.
     */
    private fun assertPngDrawsName(
        locale: Locale,
        name: String,
    ) {
        val expected = export(ExportFormat.PNG, name, locale)
        assertFalse(expected.contentEquals(export(ExportFormat.PNG, "unrelated-key", locale)))
        assertArrayEquals(expected, export(ExportFormat.PNG, "kitchen", locale))
    }

    private fun svg(
        typeKey: String,
        locale: Locale,
    ): String = export(ExportFormat.SVG, typeKey, locale).toString(Charsets.UTF_8)

    private fun export(
        format: ExportFormat,
        typeKey: String,
        locale: Locale,
    ): ByteArray =
        runBlocking {
            val room = room(typeKey)
            val evaluation =
                when (val result = evaluator.evaluate(room, ClosureTolerance())) {
                    is DomainResult.Success -> result.value
                    is DomainResult.Failure -> error("Evaluation failed: ${result.error}")
                }
            val project = Project(ProjectId("p"), "Flat", Length(2_500), persistentListOf(room), 0, 0)
            val request =
                ExportRequest(project, persistentMapOf(room.id to evaluation), format, LengthUnit.CENTIMETER, locale)
            val output = ByteArrayOutputStream()
            val result = exporters.single { it.format == format }.export(request, output)
            assertTrue(result.toString(), result is DomainResult.Success)
            output.toByteArray()
        }

    private fun room(typeKey: String): Room =
        Room(
            id = RoomId("room"),
            typeKey = RoomTypeKey(typeKey),
            walls =
                listOf(4_000L, 3_000L, 4_000L, 3_000L)
                    .mapIndexed { index, mm -> Wall(WallId("w$index"), Length(mm), Length(120)) }
                    .toImmutableList(),
            corners =
                List(4) { if (it == 0) CornerSpec.Unspecified else CornerSpec.DeclaredRight(TurnDirection.LEFT) }
                    .toImmutableList(),
            isClosed = true,
        )
}
