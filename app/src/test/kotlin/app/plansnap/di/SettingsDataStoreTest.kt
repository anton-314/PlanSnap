package app.plansnap.di

import app.plansnap.core.common.result.DomainResult
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.data.settings.DataStoreSettingsRepository
import app.plansnap.core.domain.repository.AppSettings
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** REQ-DATA-003: a damaged settings file yields the defaults and later updates still succeed. */
class SettingsDataStoreTest {
    @get:Rule
    val folder = TemporaryFolder()

    private val job = Job()
    private val scope = CoroutineScope(job + Dispatchers.IO)
    private val dispatchers =
        object : DispatcherProvider {
            override val main: CoroutineDispatcher = Dispatchers.Unconfined
            override val default: CoroutineDispatcher = Dispatchers.Unconfined
            override val io: CoroutineDispatcher = Dispatchers.Unconfined
        }

    @After
    fun tearDown() = job.cancel()

    @Test
    fun corruptFileYieldsDefaultsAndUpdateSucceeds() =
        runBlocking {
            val file = folder.newFile("settings.preferences_pb")
            file.writeBytes(byteArrayOf(0x7F, 0x00, 0x13, 0x37, -1, -2, -3, 0x55))
            val repository = DataStoreSettingsRepository(createSettingsDataStore(file, scope), dispatchers)

            assertEquals(AppSettings(), repository.settings.first())

            val result = repository.update { it.copy(gridEnabled = false) }

            assertTrue(result.toString(), result is DomainResult.Success)
            assertEquals(false, repository.settings.first().gridEnabled)
        }

    @Test
    fun missingFileYieldsDefaults() =
        runBlocking {
            val file = folder.root.resolve("absent.preferences_pb")
            val repository = DataStoreSettingsRepository(createSettingsDataStore(file, scope), dispatchers)

            assertEquals(AppSettings(), repository.settings.first())
        }
}
