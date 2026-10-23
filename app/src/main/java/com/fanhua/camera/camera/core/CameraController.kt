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
    fun attachProPreview(surfaceView: SurfaceView)
    fun setMode(mode: CaptureMode)
    fun setLens(lensId: String)
    fun setZoomRatio(ratio: Float)
    fun setFlashMode(mode: FlashMode)
    fun setPreset(preset: FilterPreset)
    fun setFilterIntensity(intensity: Float)
    fun setStabilizationEnabled(enabled: Boolean)
    fun setHorizonEnabled(enabled: Boolean)
    fun setSuperResEnabled(enabled: Boolean)
    fun setLivePhotoEnabled(enabled: Boolean)
    fun setAutoCompositionEnabled(enabled: Boolean)
    fun setGridEnabled(enabled: Boolean)
    fun setTimerSeconds(seconds: Int)
    fun cancelCountdown()
    fun setRecordingSpeed(speed: RecordingSpeed)
    fun selectSpec(spec: OutputSpec)
    fun tapToFocus(x: Float, y: Float)
    fun setManualFocus(position: Float)
    fun takePhoto()
    fun startRecording()
    fun stopRecording()
    fun switchCamera()
    fun switchToPhotoFromPreview()
    fun release()
}