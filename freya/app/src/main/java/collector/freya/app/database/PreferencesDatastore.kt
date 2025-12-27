package collector.freya.app.database

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject

class PreferencesRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {

    val LAST_MEDIA_DATE_ADDED = longPreferencesKey("last_media_date_added")
    val SERVER_BASE_URL = stringPreferencesKey("server_base_url")
    val API_KEY = stringPreferencesKey("api_key")

    fun getLastDateAddedSyncedForMediaCollection(): Flow<Long> =
        dataStore.data.map { preferences ->
            preferences[LAST_MEDIA_DATE_ADDED] ?: 0
        }

    fun getServerBaseUrl(): Flow<String> =
        dataStore.data.map { preferences ->
            preferences[SERVER_BASE_URL] ?: "freyaslittlehelper.loca.lt"
        }

    fun getApiKey(): Flow<String> =
        dataStore.data.map { preferences ->
            preferences[API_KEY] ?: "koala"
        }

    suspend fun updateLastMediaDateAdded(new: Long) {
        dataStore.edit { it[LAST_MEDIA_DATE_ADDED] = new }
    }

    suspend fun updateServerConfiguration(url: String, key: String) {
        dataStore.edit { preferences ->
            preferences[SERVER_BASE_URL] = url
            preferences[API_KEY] = key
        }
    }
}