package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.serverPrefsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "dplick_server_preferences"
)

class ServerPreferencesRepository(private val context: Context) {

    private object Keys {
        val LAST_SERVER_URL = stringPreferencesKey("last_server_url")
    }

    val lastServerUrl: Flow<String?> = context.serverPrefsDataStore.data.map { preferences ->
        preferences[Keys.LAST_SERVER_URL]
    }

    suspend fun saveLastServerUrl(url: String) {
        context.serverPrefsDataStore.edit { preferences ->
            preferences[Keys.LAST_SERVER_URL] = url
        }
    }
}
