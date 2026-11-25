package com.opencapture.pocketglance.connection

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.glanceDataStore by preferencesDataStore("pocketglance")

class GlancePrefs(private val context: Context) {
    private val startupKey = stringPreferencesKey("startup_preference")
    private val lastModeKey = stringPreferencesKey("last_connection_mode")
    private val lastCameraIdKey = stringPreferencesKey("last_camera_id")

    val startupPreference: Flow<StartupPreference> =
        context.glanceDataStore.data.map { prefs ->
            when (prefs[startupKey]) {
                StartupPreference.Wireless.name -> StartupPreference.Wireless
                StartupPreference.Wired.name -> StartupPreference.Wired
                else -> StartupPreference.Last
            }
        }

    val lastMode: Flow<ConnectionMode?> =
        context.glanceDataStore.data.map { prefs ->
            when (prefs[lastModeKey]) {
                ConnectionMode.Wireless.name -> ConnectionMode.Wireless
                ConnectionMode.Wired.name -> ConnectionMode.Wired
                else -> null
            }
        }

    val lastCameraId: Flow<String?> =
        context.glanceDataStore.data.map { prefs -> prefs[lastCameraIdKey] }

    suspend fun setStartupPreference(value: StartupPreference) {
        context.glanceDataStore.edit { it[startupKey] = value.name }
    }

    suspend fun setLastMode(mode: ConnectionMode) {
        context.glanceDataStore.edit { it[lastModeKey] = mode.name }
    }

    suspend fun setLastCameraId(id: String) {
        context.glanceDataStore.edit { it[lastCameraIdKey] = id }
    }

    suspend fun resolveStartupMode(
        preference: StartupPreference,
        last: ConnectionMode?,
    ): ConnectionMode =
        when (preference) {
            StartupPreference.Wireless -> ConnectionMode.Wireless
            StartupPreference.Wired -> ConnectionMode.Wired
            StartupPreference.Last -> last ?: ConnectionMode.Wireless
        }
}
