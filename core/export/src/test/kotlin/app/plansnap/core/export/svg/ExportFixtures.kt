package app.plansnap.core.export.svg

import android.content.res.Configuration
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.LengthUnit
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.evaluation.DefaultRoomEvaluator
import app.plansnap.core.domain.evaluation.RoomEvaluation
import app.plansnap.core.drawing.plot.PlotSelection
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.geometry.primitives.ClosureTolerance
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.DoorSwing
import app.plansnap.core.model.Opening
import app.plansnap.core.model.OpeningId
import app.plansnap.core.model.OpeningType
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.RoomTypeKey
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.core.render.LabelTextFormatter
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.collections.immutable.toImmutableMap
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import org.robolectric.RuntimeEnvironment
import java.util.Locale

/** Rooms of every relevant status, built with the real evaluator, and the request helpers of the export tests. */
internal object ExportFixtures {
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher get() = Dispatchers.Unconfined
            override val default: CoroutineDispatcher get() = Dispatchers.Unconfined
            override val io: CoroutineDispatcher get() = Dispatchers.Unconfined
        }
    val unconfined: DispatcherProvider = dispatchers

    private fun walls(
        prefix: String,
        vararg lengthsMm: Long,
    ) = lengthsMm
        .mapIndexed { i, mm -> Wall(WallId("$prefix-w$i"), Length(mm), Length(120)) }
        .toImmutableList()

    private fun corners(count: Int) =
        List(count) { if (it == 0) CornerSpec.Unspecified else CornerSpec.DeclaredRight(TurnDirection.LEFT) }
            .toImmutableList()

    /** 4 m by 3 m with a door and a window, a hostile name and notes, `CONFIRMED`. */
    val confirmed: Room =
        Room(
            id = RoomId("confirmed"),
            name = "Küche <&> \"A\"",
            typeKey = RoomTypeKey("kitchen"),
            notes = "Notiz & mehr",
            walls = walls("c", 4000, 3000, 4000, 3000),
            corners = corners(4),
            openings =
                persistentListOf(
                    Opening(
                        OpeningId("door"),
                        WallId("c-w0"),
                        OpeningType.DOOR,
                        Length(500),
                        Length(900),
                        null,
                        null,
                        DoorSwing.HINGE_LEFT_INWARD,
                    ),
                    Opening(
                        OpeningId("win"),
                        WallId("c-w1"),
                        OpeningType.WINDOW,
                        Length(800),
                        Length(1200),
                        null,
                        null,
                        null,
                    ),
                ),
            isClosed = true,
        )

    /** The rectangle with its third wall 30 mm too long, `NEEDS_ADJUSTMENT` and so unconfirmed. */
    val unconfirmed: Room =
        Room(
            id = RoomId("gap"),
            name = "Bad",
            walls = walls("g", 4000, 3000, 4030, 3000),
            corners = corners(4),
            isClosed = true,
        )

    /** Triangle 1 m, 1 m, 5 m, `CONFLICT`. */
    val conflict: Room =
        Room(id = RoomId("conflict"), walls = walls("x", 1000, 1000, 5000), corners = corners(3), isClosed = true)

    /** Two walls, not closed, `OPEN`. */
    val open: Room =
        Room(id = RoomId("open"), walls = walls("o", 3000, 2000), corners = corners(2))

    /** A 100 m by 3 m room that fits no page. */
    val huge: Room =
        Room(
            id = RoomId("huge"),
            walls = walls("h", 100000, 3000, 100000, 3000),
            corners = corners(4),
            isClosed = true,
        )

    /** A 40 m by 3 m room that only fits at 1:200. */
    val wide: Room =
        Room(
            id = RoomId("wide"),
            walls = walls("w", 40000, 3000, 40000, 3000),
            corners = corners(4),
            isClosed = true,
        )

    fun evaluate(room: Room): RoomEvaluation =
        runBlocking {
            when (val result = DefaultRoomEvaluator(dispatchers).evaluate(room, ClosureTolerance())) {
                is DomainResult.Success -> result.value
                is DomainResult.Failure -> error("Evaluation failed: ${result.error}")
            }
        }

    fun project(vararg rooms: Room) =
        Project(ProjectId("p"), "Wohnung <1>", Length(2500), rooms.toList().toImmutableList(), 1L, 2L)

    fun request(
        project: Project,
        format: ExportFormat,
        locale: Locale = Locale.GERMAN,
        unit: LengthUnit = LengthUnit.CENTIMETER,
        selection: PlotSelection = PlotSelection(),
    ) = ExportRequest(
        project,
        project.rooms.associate { it.id to evaluate(it) }.toImmutableMap(),
        format,
        unit,
        locale,
        selection,
        includeNotes = true,
    )

    /** Formatter with the resources of the requested locale. */
    fun formatter(
        locale: Locale,
        unit: LengthUnit,
    ): LabelTextFormatter {
        val app = RuntimeEnvironment.getApplication()
        val configuration = Configuration(app.resources.configuration).apply { setLocale(locale) }
        val resources = app.createConfigurationContext(configuration).resources
        return LabelTextFormatter(resources, locale, unit) { it.value.replaceFirstChar(Char::uppercase) }
    }
}
