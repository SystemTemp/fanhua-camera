package com.fanhua.camera.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

val Context.settingsDataStore by preferencesDataStore(name = "fanhua_settings")

data class AppSettings(
    val presetKey: String = "DREAM",
    val filterIntensity: Float = 1f,
    val stabilizationEnabled: Boolean = true,
    val horizonEnabled: Boolean = false,
    val superResEnabled: Boolean = false,
    val autoCompositionEnabled: Boolean = false,
    val selectedSpecLabel: String = "",
    val debugEnabled: Boolean = false,
    val lutPath: String = "",
    val gridEnabled: Boolean = false,
    val timerSeconds: Int = 0
)

class SettingsRepository(private val context: Context) {

    private val keyPreset = stringPreferencesKey("preset")
    private val keyIntensity = floatPreferencesKey("filter_intensity")
    private val keyStabilization = booleanPreferencesKey("stabilization")
    private val keySuperRes = booleanPreferencesKey("super_res")
    private val keyAutoComposition = booleanPreferencesKey("auto_composition")
    private val keySpec = stringPreferencesKey("output_spec")
    private val keyDebug = booleanPreferencesKey("debug_mode")
    private val keyLut = stringPreferencesKey("lut_path")
    private val keyHorizon = booleanPreferencesKey("horizon")
    private val keyGrid = booleanPreferencesKey("grid")
    private val keyTimer = intPreferencesKey("timer")

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            presetKey = prefs[keyPreset] ?: "DREAM",
            filterIntensity = prefs[keyIntensity] ?: 1f,
            stabilizationEnabled = prefs[keyStabilization] ?: true,
            horizonEnabled = prefs[keyHorizon] ?: false,
            superResEnabled = prefs[keySuperRes] ?: false,
            autoCompositionEnabled = prefs[keyAutoComposition] ?: false,
            selectedSpecLabel = prefs[keySpec] ?: "",
            debugEnabled = prefs[keyDebug] ?: false,
            lutPath = prefs[keyLut] ?: "",
            gridEnabled = prefs[keyGrid] ?: false,
            timerSeconds = prefs[keyTimer] ?: 0
        )
    }

    suspend fun setPreset(key: String) {
        context.settingsDataStore.edit { it[keyPreset] = key }
    }

    suspend fun setFilterIntensity(value: Float) {
        context.settingsDataStore.edit { it[keyIntensity] = value }
    }

    suspend fun setStabilizationEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[keyStabilization] = value }
    }

    suspend fun setHorizonEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[keyHorizon] = value }
    }

    suspend fun setSuperResEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[keySuperRes] = value }
    }

    suspend fun setAutoCompositionEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[keyAutoComposition] = value }
    }

    suspend fun setSelectedSpecLabel(value: String) {
        context.settingsDataStore.edit { it[keySpec] = value }
    }

    suspend fun setDebugEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[keyDebug] = value }
    }

    suspend fun setLutPath(value: String) {
        context.settingsDataStore.edit { it[keyLut] = value }
    }

    suspend fun setGridEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[keyGrid] = value }
    }

    suspend fun setTimerSeconds(value: Int) {
        context.settingsDataStore.edit { it[keyTimer] = value }
    }
}