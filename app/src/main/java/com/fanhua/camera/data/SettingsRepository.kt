package com.fanhua.camera.data

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.fanhua.camera.camera.core.PhotoAspect
import com.fanhua.camera.camera.core.SuperMoonMode
import com.fanhua.camera.camera.core.WatermarkMode
import com.fanhua.camera.debug.DebugLog
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import java.io.IOException

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
    val timerSeconds: Int = 0,
    val photoAspectKey: String = PhotoAspect.FULL.name,
    val mirrorSelfie: Boolean = false,
    val superMoonMode: String = SuperMoonMode.AUTO.name,
    val watermarkMode: String = WatermarkMode.FRAME.name,
    val shutterSoundEnabled: Boolean = true
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
    private val keyAspect = stringPreferencesKey("photo_aspect")
    private val keyMirror = booleanPreferencesKey("mirror_selfie")
    private val keyWatermark = stringPreferencesKey("watermark")
    private val keySuperMoon = stringPreferencesKey("super_moon")
    private val keyShutterSound = booleanPreferencesKey("shutter_sound")

    /**
     * 设置流。
     *
     * DataStore 在文件损坏 / 磁盘不可读时会抛 [IOException] 或 CorruptionException，
     * 若直接传播会导致 `settings.first()` 抛异常、进而让 CameraXEngine 初始化失败，
     * 表现为「一打开就闪退」。这里统一兜底为默认值，保证任何情况下都能进主界面。
     */
    val settings: Flow<AppSettings> = context.settingsDataStore.data
        .catch { e ->
            DebugLog.log("Settings", "读取设置失败，回退默认值: ${e.javaClass.simpleName}: ${e.message}")
            emit(emptyPreferences())
        }
        .map { prefs ->
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
            timerSeconds = prefs[keyTimer] ?: 0,
            photoAspectKey = prefs[keyAspect] ?: PhotoAspect.FULL.name,
            mirrorSelfie = prefs[keyMirror] ?: false,
            superMoonMode = run {
                val raw = prefs[keySuperMoon]
                SuperMoonMode.entries.firstOrNull { it.name == raw }?.name
                    ?: SuperMoonMode.AUTO.name
            },
            // 水印模式以枚举名字符串持久化；旧版本若该 key 不存在则默认开启画框水印
            watermarkMode = run {
                val raw = prefs[keyWatermark]
                WatermarkMode.entries.firstOrNull { it.name == raw }?.name
                    ?: WatermarkMode.FRAME.name
            },
            shutterSoundEnabled = prefs[keyShutterSound] ?: true
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

    suspend fun setPhotoAspect(value: PhotoAspect) {
        context.settingsDataStore.edit { it[keyAspect] = value.name }
    }

    suspend fun setMirrorSelfie(value: Boolean) {
        context.settingsDataStore.edit { it[keyMirror] = value }
    }

    suspend fun setSuperMoonMode(value: String) {
        context.settingsDataStore.edit { it[keySuperMoon] = value }
    }

    suspend fun setWatermarkMode(value: WatermarkMode) {
        context.settingsDataStore.edit { it[keyWatermark] = value.name }
    }

    /** 兼容旧调用方：布尔开关映射到三态（开→FRAME，关→OFF）。 */
    suspend fun setWatermarkEnabled(value: Boolean) {
        setWatermarkMode(if (value) WatermarkMode.FRAME else WatermarkMode.OFF)
    }

    suspend fun setShutterSoundEnabled(value: Boolean) {
        context.settingsDataStore.edit { it[keyShutterSound] = value }
    }

    /** 清空全部拍摄偏好（照片与视频不受影响）。 */
    suspend fun resetAll() {
        context.settingsDataStore.edit { it.clear() }
    }
}
