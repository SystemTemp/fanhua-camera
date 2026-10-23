package com.fanhua.camera.camera.core

import android.graphics.RectF
import androidx.annotation.StringRes
import com.fanhua.camera.R
import com.fanhua.camera.camera.filters.FilterPreset

enum class CaptureMode(@StringRes val labelRes: Int) {
    PHOTO(R.string.mode_photo),
    VIDEO(R.string.mode_video),
    PRO(R.string.mode_pro),
    SUPER_RES(R.string.mode_superres)
}

enum class FlashMode(@StringRes val labelRes: Int) {
    AUTO(R.string.flash_auto),
    ON(R.string.flash_on),
    OFF(R.string.flash_off)
}

enum class RecordingSpeed(@StringRes val labelRes: Int) {
    NORMAL(R.string.pro_speed_normal),
    SLOW_MOTION(R.string.pro_speed_slow_motion)
}

data class StatusMessage(@StringRes val resId: Int, val isError: Boolean = false)

data class LensOption(
    val id: String,
    val label: String,
    val zoomRatio: Float
)

data class FaceBox(
    val rect: RectF,
    val trackingId: Int
)

data class CompositionHint(
    val faces: List<FaceBox>,
    val offsetX: Float,
    val offsetY: Float,
    val aligned: Boolean,
    @StringRes val suggestionRes: Int,
    val cropRect: RectF?
)

data class OutputSpec(
    val width: Int,
    val height: Int,
    val fps: Int,
    val upscaled: Boolean
) {
    val label: String
        get() = "${width}x${height} ${fps}FPS" + if (upscaled) " SR" else ""
}

data class CapabilityReport(
    val maxVideoWidth: Int = 0,
    val maxVideoHeight: Int = 0,
    val maxVideoFps: Int = 0,
    val hevcSupported: Boolean = false,
    val fourK30Encode: Boolean = false,
    val fourK60Encode: Boolean = false,
    val fourK120Encode: Boolean = false,
    val highSpeedSupported: Boolean = false,
    val highSpeedProfiles: List<String> = emptyList(),
    val stabilizationModes: List<String> = emptyList(),
    val superResTargetWidth: Int = 0,
    val superResTargetHeight: Int = 0,
    val superResFps: Int = 0,
    val proSpecs: List<OutputSpec> = emptyList(),
    val notes: List<String> = emptyList()
)

data class CameraUiState(
    val permissionGranted: Boolean = false,
    val cameraAvailable: Boolean = true,
    val ready: Boolean = false,
    val filterPreviewActive: Boolean = true,
    val mode: CaptureMode = CaptureMode.PHOTO,
    val lensOptions: List<LensOption> = emptyList(),
    val activeLensId: String = "",
    val zoomRatio: Float = 1f,
    val minZoomRatio: Float = 1f,
    val maxZoomRatio: Float = 1f,
    val flashMode: FlashMode = FlashMode.AUTO,
    val preset: FilterPreset = FilterPreset.DREAM,
    val filterIntensity: Float = 1f,
    val stabilizationEnabled: Boolean = true,
    val horizonEnabled: Boolean = false,
    val superResEnabled: Boolean = false,
    val livePhotoEnabled: Boolean = false,
    val autoCompositionEnabled: Boolean = false,
    val gridEnabled: Boolean = false,
    val timerSeconds: Int = 0,
    val countdownRemaining: Int = 0,
    val frontCamera: Boolean = false,
    val compositionHint: CompositionHint? = null,
    val recording: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val recordingSpeed: RecordingSpeed = RecordingSpeed.NORMAL,
    val selectedSpec: OutputSpec? = null,
    val availableSpecs: List<OutputSpec> = emptyList(),
    val manualFocusPosition: Float = 0.5f,
    val manualFocusActive: Boolean = false,
    val busy: Boolean = false,
    val statusMessage: StatusMessage? = null,
    val captureCount: Int = 0
)