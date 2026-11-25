package app.plansnap.di

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import app.plansnap.core.common.runtime.DispatcherProvider
import app.plansnap.core.common.runtime.EpochClock
import app.plansnap.core.common.runtime.IdGenerator
import app.plansnap.core.data.db.PlanSnapDatabase
import app.plansnap.core.data.repository.RoomEditorDraftRepository
import app.plansnap.core.data.repository.RoomProjectRepository
import app.plansnap.core.data.repository.RoomProjectSaver
import app.plansnap.core.data.settings.DataStoreSettingsRepository
import app.plansnap.core.document.DocumentCodec
import app.plansnap.core.document.JsonDocumentCodec
import app.plansnap.core.domain.repository.EditorDraftRepository
import app.plansnap.core.domain.repository.ProjectRepository
import app.plansnap.core.domain.repository.ProjectSaver
import app.plansnap.core.domain.repository.SettingsRepository
import app.plansnap.core.domain.validation.ProjectValidator
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.plus
import javax.inject.Inject
import javax.inject.Singleton

private const val SETTINGS_FILE = "settings"

/**
 * Owner of the single on-disk database. The holder keeps [PlanSnapDatabase] out of the signatures of the Hilt-generated
 * Java code, because `:core:data` does not expose the Room runtime to `:app`.
 */
@Singleton
class DatabaseHolder
    @Inject
    constructor(
        @ApplicationContext private val context: Context,
    ) {
        /** The on-disk database, opened on first use. */
        internal val database: PlanSnapDatabase by lazy(LazyThreadSafetyMode.SYNCHRONIZED) {
            PlanSnapDatabase.open(context)
        }
    }

/** Persistence: database, document codec, repositories, the project saver and the settings store. */
@Module
@InstallIn(SingletonComponent::class)
object DataModule {
    /** Codec of the project document format (ADR 0006). */
    @Provides
    @Singleton
    fun documentCodec(): DocumentCodec = JsonDocumentCodec()

    /** Project storage with the production [ProjectValidator]. */
    @Provides
    @Singleton
    @Suppress("LongParameterList") // Wiring of the constructor of RoomProjectRepository.
    fun projectRepository(
        storage: DatabaseHolder,
        codec: DocumentCodec,
        validator: ProjectValidator,
        clock: EpochClock,
        ids: IdGenerator,
        dispatchers: DispatcherProvider,
    ): ProjectRepository = RoomProjectRepository(storage.database, codec, validator, clock, ids, dispatchers)

    /** Serial project writer. It runs in the [ApplicationScope] of its callers and outlives every screen. */
    @Provides
    @Singleton
    fun projectSaver(
        storage: DatabaseHolder,
        codec: DocumentCodec,
        clock: EpochClock,
        dispatchers: DispatcherProvider,
    ): ProjectSaver = RoomProjectSaver(storage.database, codec, clock, dispatchers)

    /** Storage of unconfirmed editor input. */
    @Provides
    @Singleton
    fun editorDraftRepository(
        storage: DatabaseHolder,
        clock: EpochClock,
        dispatchers: DispatcherProvider,
    ): EditorDraftRepository = RoomEditorDraftRepository(storage.database, clock, dispatchers)

    /** Preferences store of the settings, with the corruption handler of [createSettingsDataStore]. */
    @Provides
    @Singleton
    fun settingsDataStore(
        @ApplicationContext context: Context,
        @ApplicationScope scope: CoroutineScope,
        dispatchers: DispatcherProvider,
    ): DataStore<Preferences> =
        createSettingsDataStore(context.preferencesDataStoreFile(SETTINGS_FILE), scope + dispatchers.io)

    /** Settings storage. */
    @Provides
    @Singleton
    fun settingsRepository(
        dataStore: DataStore<Preferences>,
        dispatchers: DispatcherProvider,
    ): SettingsRepository = DataStoreSettingsRepository(dataStore, dispatchers)
}
