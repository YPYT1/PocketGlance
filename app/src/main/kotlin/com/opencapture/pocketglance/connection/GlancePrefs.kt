package com.opencapture.pocketglance.connection

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.opencapture.pocketglance.monitor.MonitorOrientation
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.glanceDataStore by preferencesDataStore("pocketglance")

class GlancePrefs(private val context: Context) {
    private val startupKey = stringPreferencesKey("startup_preference")
    private val lastModeKey = stringPreferencesKey("last_connection_mode")
    private val lastCameraIdKey = stringPreferencesKey("last_camera_id")
    private val monitorOrientationKey = stringPreferencesKey("monitor_orientation")
    private val favoriteLutsKey = stringSetPreferencesKey("favorite_lut_ids")
    private val galleryTagPrefix = "gallery_tags:"

    val favoriteLutIds: Flow<Set<String>> =
        context.glanceDataStore.data.map { prefs -> prefs[favoriteLutsKey].orEmpty() }

    suspend fun setFavoriteLut(id: String, favorite: Boolean) {
        context.glanceDataStore.edit { preferences ->
            val current = preferences[favoriteLutsKey].orEmpty()
            preferences[favoriteLutsKey] =
                if (favorite) current + id else current - id
        }
    }

    val galleryTags: Flow<Map<String, Set<String>>> = context.glanceDataStore.data.map { preferences ->
        preferences.asMap().entries.mapNotNull { (key, value) ->
            if (!key.name.startsWith(galleryTagPrefix) || value !is Set<*>) null
            else key.name.removePrefix(galleryTagPrefix) to value.filterIsInstance<String>().toSet()
        }.toMap()
    }

    suspend fun updateGalleryTag(keys: Set<String>, tag: String, add: Boolean) {
        context.glanceDataStore.edit { preferences ->
            for (id in keys) {
                val key = stringSetPreferencesKey(galleryTagPrefix + id)
                val current = preferences[key].orEmpty()
                val updated = if (add) current + tag else current - tag
                if (updated.isEmpty()) preferences.remove(key) else preferences[key] = updated
            }
        }
    }

    val monitorOrientation: Flow<MonitorOrientation> = context.glanceDataStore.data.map { prefs ->
        if (prefs[monitorOrientationKey] == MonitorOrientation.AutoRotate.name) MonitorOrientation.AutoRotate
        else MonitorOrientation.LandscapeLocked
    }

    suspend fun setMonitorOrientation(value: MonitorOrientation) {
        context.glanceDataStore.edit { it[monitorOrientationKey] = value.name }
    }

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
