package app.plansnap.core.data.repository

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.units.Length
import app.plansnap.core.data.db.PlanSnapDatabase
import app.plansnap.core.document.DocumentCodec
import app.plansnap.core.document.JsonDocumentCodec
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.domain.validation.ProjectValidator
import app.plansnap.core.model.DockLink
import app.plansnap.core.model.Project
import app.plansnap.core.model.Room
import app.plansnap.core.model.RoomId
import app.plansnap.testing.fixtures.generators.ProjectGenerator
import app.plansnap.testing.fixtures.runtime.FakeEpochClock
import app.plansnap.testing.fixtures.runtime.SequentialIdGenerator
import kotlinx.collections.immutable.toImmutableList
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import org.junit.Assert.assertTrue
import org.robolectric.RuntimeEnvironment
import java.util.concurrent.atomic.AtomicLong
import androidx.room.Room as RoomDb

/** Runs everything on the calling thread, so the tests stay deterministic. */
internal object UnconfinedDispatchers : DispatcherProvider {
    override val main: CoroutineDispatcher = Dispatchers.Unconfined
    override val default: CoroutineDispatcher = Dispatchers.Unconfined
    override val io: CoroutineDispatcher = Dispatchers.Unconfined
}

/** Clock that returns a new, larger value on every call. */
internal class TickingClock(
    start: Long = 10_000L,
) : EpochClock {
    private val next = AtomicLong(start)

    override fun nowEpochMillis(): Long = next.getAndIncrement()
}

internal val acceptAll = ProjectValidator { DomainResult.Success(Unit) }

internal fun fileDatabase(name: String = "repo-test.db"): PlanSnapDatabase =
    RoomDb
        .databaseBuilder(RuntimeEnvironment.getApplication(), PlanSnapDatabase::class.java, name)
        .allowMainThreadQueries()
        .build()

/** The repositories of one database, wired with fakes for time and ids. */
internal class Stores(
    val database: PlanSnapDatabase,
    val clock: FakeEpochClock = FakeEpochClock(),
    val codec: DocumentCodec = JsonDocumentCodec(),
    validator: ProjectValidator = acceptAll,
    saverClock: EpochClock = clock,
    saverCodec: DocumentCodec = codec,
) {
    val projects: ProjectRepository =
        RoomProjectRepository(database, codec, validator, clock, SequentialIdGenerator("copy"), UnconfinedDispatchers)
    val saver: ProjectSaver = RoomProjectSaver(database, saverCodec, saverClock, UnconfinedDispatchers)
    val drafts: EditorDraftRepository = RoomEditorDraftRepository(database, clock, UnconfinedDispatchers)
}

internal fun sampleProject(
    seed: Long = 1L,
    rooms: Int = 3,
    idPrefix: String = "gen",
): Project = ProjectGenerator(seed, SequentialIdGenerator(idPrefix)).project(rooms)

/** The project with [room] replaced by a version whose name changed, same ids. */
internal fun Project.withRoomName(
    id: RoomId,
    name: String,
): Project = copy(rooms = rooms.map { if (it.id == id) it.copy(name = name) else it }.toImmutableList())

/** Docks the second room to the first wall of the first room. */
internal fun Project.withDockedSecondRoom(): Project {
    val host = rooms[0]
    val guest = rooms[1]
    val dock = DockLink(host.id, host.walls[0].id, guest.walls[0].id, Length(100L))
    val docked: Room = guest.copy(placement = guest.placement.copy(dock = dock))
    return copy(rooms = rooms.mapIndexed { index, room -> if (index == 1) docked else room }.toImmutableList())
}

internal fun Project.roomIds(): List<RoomId> = rooms.map { it.id }

internal fun DomainResult<*>.assertSuccess() =
    assertTrue("Expected success but was $this", this is DomainResult.Success)

internal fun <T> DomainResult<T>.value(): T = (this as DomainResult.Success).value

internal suspend fun ProjectRepository.observeProjectsOnce() = observeProjects().first()
