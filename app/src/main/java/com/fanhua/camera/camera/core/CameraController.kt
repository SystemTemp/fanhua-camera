package com.fanhua.camera.camera.core

import android.view.SurfaceView
import androidx.camera.view.PreviewView
import com.fanhua.camera.camera.filters.FilterPreset
import kotlinx.coroutines.flow.StateFlow

interface CameraController {
    val state: StateFlow<CameraUiState>
    val horizonRoll: StateFlow<Float>

    fun onPermissionResult(granted: Boolean)
    fun attachCameraXPreview(previewView: PreviewView)

    fun setMode(mode: CaptureMode)
    fun setLens(lensId: String)
    fun setZoomRatio(ratio: Float)
    fun setFlashMode(mode: FlashMode)
    fun setTorchEnabled(enabled: Boolean)
    fun setExposureCompensation(index: Int)
    fun setPreset(preset: FilterPreset)
    fun setFilterIntensity(intensity: Float)
    fun setStabilizationEnabled(enabled: Boolean)
    fun setHorizonEnabled(enabled: Boolean)
    fun setSuperResEnabled(enabled: Boolean)
    fun setLivePhotoEnabled(enabled: Boolean)
    fun setAutoCompositionEnabled(enabled: Boolean)

    /** 下载并启用指定检测模型；下载失败返回 false。 */
    suspend fun downloadDetectorModel(modelId: String): Boolean

    /** 启用一个已下载的模型。 */
    fun useDetectorModel(modelId: String)

    /** 删除某个已下载模型。 */
    fun deleteDetectorModel(modelId: String)

    /** 导入用户自备的 .tflite 模型。 */
    suspend fun importDetectorModel(bytes: ByteArray): Boolean
    fun setGridEnabled(enabled: Boolean)
    fun setTimerSeconds(seconds: Int)
    fun cancelCountdown()
    fun setPhotoAspect(aspect: PhotoAspect)
    fun setMirrorSelfie(enabled: Boolean)
    fun setWatermarkEnabled(enabled: Boolean)
    fun setShutterSoundEnabled(enabled: Boolean)
    fun selectSpec(spec: OutputSpec)
    fun tapToFocus(x: Float, y: Float)
    fun setManualFocus(position: Float)
    fun resetManualFocus()
    fun takePhoto()
    fun startRecording()
    fun pauseRecording()
    fun resumeRecording()
    fun stopRecording()
    fun switchCamera()
    fun release()
}
