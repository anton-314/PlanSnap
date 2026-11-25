package app.plansnap.di

import androidx.datastore.core.DataStore
import androidx.datastore.core.handlers.ReplaceFileCorruptionHandler
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.emptyPreferences
import kotlinx.coroutines.CoroutineScope
import java.io.File

/**
 * Creates the Preferences [DataStore] of the settings on [file].
 *
 * A corrupt file is replaced by empty preferences, so the settings fall back to their defaults and later updates
 * succeed. Without the handler every `update` would fail for good after a damaged file (REQ-DATA-003).
 *
 * @param file settings file, created on first write.
 * @param scope scope in which the store does its work.
 */
fun createSettingsDataStore(
    file: File,
    scope: CoroutineScope,
): DataStore<Preferences> =
    PreferenceDataStoreFactory.create(
        corruptionHandler = ReplaceFileCorruptionHandler { emptyPreferences() },
        scope = scope,
        produceFile = { file },
    )
