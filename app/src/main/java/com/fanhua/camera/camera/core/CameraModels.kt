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

/**
 * 徕卡水印模式（参照「相机整合」徕卡一瞬包，支持多种可切换）。
 *
 * - [OFF]：不加任何水印；
 * - [FRAME]：白边画框（徕卡 92 号，水印在照片外部，照片被白色边框包围）；
 * - [EMBEDDED]：照片内叠加水印（标准 7 号，品牌 logo + 机型 + 时间叠在画面底部）。
 *
 * 仅当滤镜属于 [com.fanhua.camera.camera.filters.FilterGroup.LEICA] 组时才生效。
 */
enum class WatermarkMode(@StringRes val labelRes: Int) {
    OFF(R.string.watermark_off),
    FRAME(R.string.watermark_frame),
    EMBEDDED(R.string.watermark_embedded)
}

enum class FlashMode(@StringRes val labelRes: Int) {
    AUTO(R.string.flash_auto),
    ON(R.string.flash_on),
    OFF(R.string.flash_off)
}

/** 照片画幅：取景所见即所得，保存时按画幅居中裁切。 */
enum class PhotoAspect(@StringRes val labelRes: Int) {
    FULL(R.string.aspect_full),
    RATIO_4_3(R.string.aspect_4_3),
    RATIO_16_9(R.string.aspect_16_9),
    RATIO_1_1(R.string.aspect_1_1);

    /** 宽高比；FULL 表示不裁切，返回 0f。 */
    val ratio: Float
        get() = when (this) {
            FULL -> 0f
            RATIO_4_3 -> 4f / 3f
            RATIO_16_9 -> 16f / 9f
            RATIO_1_1 -> 1f
        }

    companion object {
        fun fromKey(key: String?): PhotoAspect =
            entries.firstOrNull { it.name == key } ?: FULL
    }
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
    val cropRect: com.fanhua.camera.camera.detect.NormRect?
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

/** 曝光补偿状态，index 为 CameraX 曝光补偿档位，ev 为换算后的档数。 */
data class ExposureState(
    val index: Int = 0,
    val minIndex: Int = 0,
    val maxIndex: Int = 0,
    val stepEv: Float = 1f / 6f
) {
    val ev: Float get() = index * stepEv
    val available: Boolean get() = maxIndex > minIndex
    val fraction: Float
        get() = if (maxIndex > minIndex) {
            (index - minIndex).toFloat() / (maxIndex - minIndex).toFloat()
        } else {
            0.5f
        }
}

data class CapabilityReport(
    val maxVideoWidth: Int = 0,
    val maxVideoHeight: Int = 0,
    val maxVideoFps: Int = 0,
    val maxPhotoWidth: Int = 0,
    val maxPhotoHeight: Int = 0,
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
    val torchEnabled: Boolean = false,
    val exposure: ExposureState = ExposureState(),
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
    /** 当前检测到的主体名称（人物 / 动物 / 美食…），无主体时为 null。 */
    val subjectLabel: String? = null,
    /** AI 检测模型是否已就绪（已下载并成功加载）。 */
    val detectorReady: Boolean = false,
    /** 当前使用的模型 id。 */
    val detectorModelId: String? = null,
    /** 正在下载模型时的进度（0..100），未下载时为 null。 */
    val detectorDownloadPercent: Int? = null,
    /** 下载速度（字节/秒），用于 UI 展示。 */
    val detectorDownloadSpeed: Long = 0L,
    /** 已下载字节数。 */
    val detectorDownloadedBytes: Long = 0L,
    /** 总字节数。 */
    val detectorTotalBytes: Long = 0L,
    val recording: Boolean = false,
    val recordingPaused: Boolean = false,
    val recordingDurationMs: Long = 0L,
    val selectedSpec: OutputSpec? = null,
    val availableSpecs: List<OutputSpec> = emptyList(),
    val manualFocusPosition: Float = 0.5f,
    val manualFocusActive: Boolean = false,
    val photoAspect: PhotoAspect = PhotoAspect.FULL,
    /** 自动构图产生的裁切框（归一化），null 表示未裁切。 */
    val autoCropRect: com.fanhua.camera.camera.detect.NormRect? = null,
    /** 自动构图是否正在计算。 */
    val autoComposing: Boolean = false,
    val mirrorSelfie: Boolean = false,
    val watermarkMode: WatermarkMode = WatermarkMode.FRAME,
    val shutterSoundEnabled: Boolean = true,
    val busy: Boolean = false,
    val statusMessage: StatusMessage? = null,
    val captureCount: Int = 0,
    val lastCaptureUri: String? = null
) {
    /** 是否可用物理闪光灯（前置通常没有）。 */
    val flashAvailable: Boolean get() = !frontCamera

    /** 派生布尔：是否启用水印（OFF 之外都算启用），便于兼容旧引用。 */
    val watermarkEnabled: Boolean get() = watermarkMode != WatermarkMode.OFF
}
