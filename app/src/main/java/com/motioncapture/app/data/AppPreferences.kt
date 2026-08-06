package com.motioncapture.app.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.motionDataStore by preferencesDataStore(name = "motion_capture")

enum class Sensitivity(val label: String, val motionThresholdPx: Float) {
    LOW("Low", 48f),
    MEDIUM("Medium", 26f),
    HIGH("High", 12f);

    companion object {
        fun fromName(name: String): Sensitivity =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: MEDIUM
    }
}

enum class SaveDestination(val label: String) {
    PHOTOS("Photos"),
    APP("App Storage");

    companion object {
        fun fromName(name: String): SaveDestination =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: PHOTOS
    }
}

data class AppSettings(
    val sensitivity: Sensitivity = Sensitivity.MEDIUM,
    val peopleOnly: Boolean = false,
    val burstCount: Int = 1,
    val saveTo: SaveDestination = SaveDestination.PHOTOS,
    val notifications: Boolean = false,
)

class AppPreferences(private val context: Context) {

    private object Keys {
        val SENSITIVITY = stringPreferencesKey("sensitivity")
        val PEOPLE_ONLY = booleanPreferencesKey("people_only")
        val BURST_COUNT = intPreferencesKey("burst_count")
        val SAVE_TO = stringPreferencesKey("save_to")
        val NOTIFICATIONS = booleanPreferencesKey("notifications")
        val LAST_CAPTURE = longPreferencesKey("last_capture")
    }

    val settings: Flow<AppSettings> = context.motionDataStore.data.map { prefs ->
        AppSettings(
            sensitivity = Sensitivity.fromName(prefs[Keys.SENSITIVITY] ?: ""),
            peopleOnly = prefs[Keys.PEOPLE_ONLY] ?: false,
            burstCount = (prefs[Keys.BURST_COUNT] ?: 1).coerceIn(1, 5),
            saveTo = SaveDestination.fromName(prefs[Keys.SAVE_TO] ?: ""),
            notifications = prefs[Keys.NOTIFICATIONS] ?: false,
        )
    }

    val lastCaptureTime: Flow<Long> = context.motionDataStore.data.map { it[Keys.LAST_CAPTURE] ?: 0L }

    suspend fun setSensitivity(value: Sensitivity) {
        context.motionDataStore.edit { it[Keys.SENSITIVITY] = value.name }
    }

    suspend fun setPeopleOnly(value: Boolean) {
        context.motionDataStore.edit { it[Keys.PEOPLE_ONLY] = value }
    }

    suspend fun setBurstCount(value: Int) {
        context.motionDataStore.edit { it[Keys.BURST_COUNT] = value.coerceIn(1, 5) }
    }

    suspend fun setSaveTo(value: SaveDestination) {
        context.motionDataStore.edit { it[Keys.SAVE_TO] = value.name }
    }

    suspend fun setNotifications(value: Boolean) {
        context.motionDataStore.edit { it[Keys.NOTIFICATIONS] = value }
    }

    suspend fun setLastCaptureTime(value: Long) {
        context.motionDataStore.edit { it[Keys.LAST_CAPTURE] = value }
    }
}
