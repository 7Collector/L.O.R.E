package collector.freya.app.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.longPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PreferencesRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val LAST_MEDIA_DATE_ADDED = longPreferencesKey("last_media_date_added")


    fun getLastDateAddedSyncedForMediaCollection(): Flow<Long> =
        dataStore.data.map { preferences ->
            preferences[LAST_MEDIA_DATE_ADDED] ?: 0
        }

    suspend fun updateLastMediaDateAdded(new: Long) {
        dataStore.updateData {
            it.toMutablePreferences().apply {
                set(LAST_MEDIA_DATE_ADDED, new)
            }
        }
    }
}