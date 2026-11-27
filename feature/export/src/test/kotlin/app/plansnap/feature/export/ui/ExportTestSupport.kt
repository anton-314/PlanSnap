package app.plansnap.feature.export.ui

import android.net.Uri
import app.plansnap.core.common.result.DomainError
import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.result.EntityKind
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.units.Length
import app.plansnap.core.common.units.TurnDirection
import app.plansnap.core.domain.repository.AppSettings
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSummary
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.drawing.plot.PageOrientation
import app.plansnap.core.drawing.plot.PaperSize
import app.plansnap.core.drawing.plot.PlotPlan
import app.plansnap.core.drawing.plot.PlotScale
import app.plansnap.core.drawing.scene.WorldBounds
import app.plansnap.core.export.ExportFormat
import app.plansnap.core.export.ExportRequest
import app.plansnap.core.export.ExportSummary
import app.plansnap.core.export.Exporter
import app.plansnap.core.model.CornerSpec
import app.plansnap.core.model.Project
import app.plansnap.core.model.ProjectId
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.core.model.Wall
import app.plansnap.core.model.WallId
import app.plansnap.feature.export.data.ExportFiles
import app.plansnap.feature.export.data.ExportTarget
import kotlinx.collections.immutable.ImmutableList
import kotlinx.collections.immutable.persistentListOf
import kotlinx.collections.immutable.persistentSetOf
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream

/** Rooms of known status. The statuses come from the real evaluator in the tests. */
internal object ExportRooms {
    private fun walls(
        prefix: String,
        vararg lengthsMm: Long,
    ) = lengthsMm.mapIndexed { i, mm -> Wall(WallId("$prefix-w$i"), Length(mm), Length(120)) }.toImmutableList()

    private fun corners(count: Int) =
        List(count) { if (it == 0) CornerSpec.Unspecified else CornerSpec.DeclaredRight(TurnDirection.LEFT) }
            .toImmutableList()

    /** A room without any wall, as every new project starts with one. */
    val bare = Room(id = RoomId("bare"))

    /** 4 m by 3 m, confirmed. */
    val confirmed =
        Room(
            id = RoomId("confirmed"),
            name = "Kitchen",
            walls = walls("c", 4000, 3000, 4000, 3000),
            corners = corners(4),
            isClosed = true,
        )

    /** The rectangle with its third wall 30 mm too long, so the shape awaits confirmation. */
    val unconfirmed =
        Room(
            id = RoomId("gap"),
            name = "",
            walls = walls("g", 4000, 3000, 4030, 3000),
            corners = corners(4),
            isClosed = true,
        )

    /** Triangle 1 m, 1 m, 5 m, contradictory. */
    val conflict =
        Room(
            id = RoomId("conflict"),
            name = "Shed",
            walls = walls("x", 1000, 1000, 5000),
            corners = corners(3),
            isClosed = true,
        )

    /** Two walls, not closed. */
    val open = Room(id = RoomId("open"), name = "", walls = walls("o", 3000, 2000), corners = corners(2))

    /** A 40 m by 3 m room that fits only at 1:200 on A3. */
    val wide =
        Room(
            id = RoomId("wide"),
            name = "Hall",
            walls = walls("w", 40000, 3000, 40000, 3000),
            corners = corners(4),
            isClosed = true,
        )

    /** A 100 m by 3 m room that fits no page. */
    val huge =
        Room(
            id = RoomId("huge"),
            name = "Field",
            walls = walls("h", 100000, 3000, 100000, 3000),
            corners = corners(4),
            isClosed = true,
        )

    fun project(vararg rooms: Room): Project =
        Project(ProjectId("p1"), "Flat <1>", Length(2500), rooms.toList().toImmutableList(), 1L, 2L)
}

/** Dispatchers that all run on [dispatcher]. */
internal class SingleDispatchers(
    private val dispatcher: CoroutineDispatcher,
) : DispatcherProvider {
    override val main: CoroutineDispatcher get() = dispatcher
    override val default: CoroutineDispatcher get() = dispatcher
    override val io: CoroutineDispatcher get() = dispatcher
}

internal class FakeProjects(
    var result: DomainResult<Project>,
) : ProjectRepository {
    override suspend fun getProject(id: ProjectId): DomainResult<Project> = result

    override fun observeProjects(): Flow<ImmutableList<ProjectSummary>> = MutableStateFlow(persistentListOf())

    override suspend fun createProject(project: Project): DomainResult<Unit> = unused()

    override suspend fun renameProject(
        id: ProjectId,
        name: String,
    ): DomainResult<Unit> = unused()

    override suspend fun deleteProject(id: ProjectId): DomainResult<Unit> = unused()

    override suspend fun duplicateProject(id: ProjectId): DomainResult<ProjectId> = unused()

    override suspend fun importProject(input: InputStream): DomainResult<ProjectId> = unused()

    private fun unused(): Nothing = error("Not used by the export")
}

internal class FakeSettings(
    initial: AppSettings = AppSettings(),
) : SettingsRepository {
    private val state = MutableStateFlow(initial)
    override val settings: Flow<AppSettings> = state

    override suspend fun update(transform: (AppSettings) -> AppSettings): DomainResult<Unit> {
        state.value = transform(state.value)
        return DomainResult.Success(Unit)
    }
}

/** Exporter that records its requests, writes [bytes] and can be held back with [gate]. */
internal class FakeExporter(
    override val format: ExportFormat,
    var failure: DomainError? = null,
    var gate: CompletableDeferred<Unit>? = null,
    var throwOnWrite: IOException? = null,
) : Exporter {
    val requests = mutableListOf<ExportRequest>()

    private companion object {
        val PLAN =
            PlotPlan(PaperSize.A4, PageOrientation.LANDSCAPE, PlotScale.ONE_TO_50, WorldBounds(0.0, 0.0, 1.0, 1.0))
    }

    var bytes: ByteArray = "content-$format".toByteArray()

    override suspend fun export(
        request: ExportRequest,
        output: OutputStream,
    ): DomainResult<ExportSummary> {
        requests += request
        gate?.await()
        throwOnWrite?.let { throw it }
        failure?.let { return DomainResult.Failure(it) }
        output.write(bytes)
        val plan = if (format == ExportFormat.JSON) null else PLAN
        return DomainResult.Success(ExportSummary(format, plan, persistentSetOf()))
    }
}

/** [ExportFiles] that keeps everything in memory. */
internal class FakeFiles : ExportFiles {
    class Written(
        val name: String,
        val uri: Uri,
        val buffer: ByteArrayOutputStream,
        var closed: Boolean = false,
        var discarded: Boolean = false,
    )

    val targets = mutableListOf<Written>()
    var openFailure: IOException? = null

    override fun createCacheTarget(fileName: String): ExportTarget =
        open(fileName, Uri.parse("content://test/exports/$fileName"))

    override fun openDocument(uri: Uri): ExportTarget = open(uri.lastPathSegment.orEmpty(), uri)

    private fun open(
        name: String,
        uri: Uri,
    ): ExportTarget {
        openFailure?.let { throw it }
        val record = Written(name, uri, ByteArrayOutputStream())
        targets += record
        val stream =
            object : OutputStream() {
                override fun write(b: Int) = record.buffer.write(b)

                override fun write(
                    b: ByteArray,
                    off: Int,
                    len: Int,
                ) = record.buffer.write(b, off, len)

                override fun close() {
                    record.closed = true
                }
            }
        return ExportTarget(stream, uri) { record.discarded = true }
    }
}

internal fun notFound(): DomainError = DomainError.NotFound(EntityKind.PROJECT, "p1")
