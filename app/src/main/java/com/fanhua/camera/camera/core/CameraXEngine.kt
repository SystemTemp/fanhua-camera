package com.fanhua.camera.camera.core

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.View
import androidx.camera.core.Camera
import androidx.camera.core.CameraEffect
import androidx.camera.core.CameraSelector
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceOrientedMeteringPointFactory
import androidx.camera.core.UseCase
import androidx.camera.core.UseCaseGroup
import androidx.camera.core.ViewPort
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.MediaStoreOutputOptions
import androidx.camera.video.PendingRecording
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.util.Consumer
import androidx.lifecycle.LifecycleOwner
import com.fanhua.camera.R
import com.fanhua.camera.camera.analyzer.CompositionEngine
import com.fanhua.camera.camera.analyzer.SubjectAnalyzer
import com.fanhua.camera.camera.detect.DetectorStore
import com.fanhua.camera.camera.detect.ModelDownloader
import com.fanhua.camera.camera.detect.Detection as DetectDetection
import com.fanhua.camera.camera.detect.DetectorModels
import com.fanhua.camera.camera.detect.ObjectDetector
import com.fanhua.camera.camera.capture.FilterCameraEffect
import com.fanhua.camera.camera.capture.FilterSurfaceProcessor
import com.fanhua.camera.camera.capture.RenderState
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.camera.stabilize.GyroStabilizer
import com.fanhua.camera.data.SettingsRepository
import com.fanhua.camera.debug.DebugLog
import com.fanhua.camera.lut.LutStore
import com.fanhua.camera.media.MotionPhotoBuilder
import com.fanhua.camera.media.PhotoProcessor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * CameraX 引擎实现。
 *
 * 设计要点：
 * - 所有公开方法都是「幂等 + 可重入」的，UI 层可以随意调用；
 * - 状态集中在 [CameraUiState]，UI 只订阅不判断；
 * - 绑定失败时按「有特效 → 无特效 → 降分辨率 → 仅照片」四级降级，
 *   保证任何设备上都能至少拍照。
 */
class CameraXEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope
) : CameraController {

    private val _state = MutableStateFlow(CameraUiState())
    override val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val _horizonRoll = MutableStateFlow(0f)
    override val horizonRoll: StateFlow<Float> = _horizonRoll.asStateFlow()
    private var horizonPollJob: Job? = null
    private var countdownJob: Job? = null
    private var recordingTimerJob: Job? = null

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val glExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val settings = SettingsRepository(context)
    private val capabilityProbe = CapabilityProbe(context)
    private val photoProcessor = PhotoProcessor(context)
    private val stabilizer = GyroStabilizer(context) { displayRotation() }

    private var previewView: PreviewView? = null

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var subjectAnalyzer: SubjectAnalyzer? = null
    private val detector = ObjectDetector(context)
    private var processor: FilterSurfaceProcessor? = null
    private var recording: Recording? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var capability: CapabilityReport = CapabilityReport()
    private var renderState = RenderState(FilterPreset.DREAM, 1f, false)
    private var statusJob: Job? = null
    private var livePhotoJob: Job? = null
    private var rotationListener: View.OnLayoutChangeListener? = null
    private var rotationListenerView: View? = null
    private var livePhotoSource: Uri? = null
    private var livePhotoReady = false
    private var glFilterDowngraded = false
    private var providerStarted = false
    private var probeStarted = false
    private var released = false

    /** 记录最近一次绑定的参数，用于判断是否需要重新绑定，避免无谓的重建。 */
    private var boundSignature: String = ""

    init {
        scope.launch {
            // 启动恢复设置。任何异常都不能让协程抛出——未捕获的协程异常会直接
            // 崩溃进程，表现为「一打开就闪退」。这里整体兜底，失败就用默认值继续。
            try {
                val stored = settings.settings.first()
                DebugLog.enabled = stored.debugEnabled
                if (stored.lutPath.isNotBlank()) {
                    try {
                        LutStore.select(context, stored.lutPath)
                    } catch (e: Exception) {
                        DebugLog.log("Engine", "恢复自定义 LUT 失败: ${e.message}")
                    }
                }
                val preset = FilterPreset.fromKey(stored.presetKey)
                renderState = RenderState(
                    preset,
                    stored.filterIntensity,
                    false,
                    lut = try {
                        resolveLut(preset)
                    } catch (e: Exception) {
                        DebugLog.log("Engine", "解析 LUT 失败: ${e.message}")
                        null
                    }
                )
                _state.update {
                    it.copy(
                        preset = preset,
                        filterIntensity = stored.filterIntensity,
                        stabilizationEnabled = stored.stabilizationEnabled,
                        horizonEnabled = stored.horizonEnabled,
                        superResEnabled = stored.superResEnabled,
                        autoCompositionEnabled = stored.autoCompositionEnabled,
                        gridEnabled = stored.gridEnabled,
                        timerSeconds = stored.timerSeconds,
                        photoAspect = PhotoAspect.fromKey(stored.photoAspectKey),
                        mirrorSelfie = stored.mirrorSelfie,
                        watermarkMode = com.fanhua.camera.camera.core.WatermarkMode.entries
                            .firstOrNull { it.name == stored.watermarkMode } ?: com.fanhua.camera.camera.core.WatermarkMode.FRAME,
                        shutterSoundEnabled = stored.shutterSoundEnabled
                    )
                }
                DebugLog.log(
                    "Engine",
                    "初始化 preset=$preset intensity=${stored.filterIntensity} debug=${DebugLog.enabled} " +
                        "lut=${stored.lutPath.ifBlank { "(无)" }}"
                )
                // 加载已选中的检测模型（如果有）
                try {
                    loadSelectedDetector()
                } catch (e: Exception) {
                    DebugLog.log("Engine", "加载检测模型失败: ${e.message}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "启动恢复设置失败，使用默认值", e)
                DebugLog.log("Engine", "启动恢复设置失败: ${e.javaClass.simpleName}: ${e.message}")
            }
        }
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.CAMERA
            ) == PackageManager.PERMISSION_GRANTED
        ) {
            onPermissionResult(true)
        }
    }

    override fun onPermissionResult(granted: Boolean) {
        _state.update { it.copy(permissionGranted = granted, cameraAvailable = granted) }
        if (!granted) return
        startProvider()
        probeAsync()
    }

    private fun probeAsync() {
        if (probeStarted || released) return
        probeStarted = true
        scope.launch {
            val report = withContext(Dispatchers.IO) { capabilityProbe.probe(lensFacing) }
            val previous = capability
            capability = report
            val specs = buildVideoSpecs(report)
            _state.update { current ->
                val kept = current.selectedSpec?.let { sel ->
                    specs.firstOrNull {
                        it.width == sel.width && it.height == sel.height && it.fps == sel.fps
                    }
                }
                current.copy(
                    availableSpecs = specs,
                    selectedSpec = kept ?: specs.firstOrNull()
                )
            }
            publishExposure()
            val needRebind = _state.value.stabilizationEnabled &&
                report.stabilizationModes.contains("ON") &&
                !previous.stabilizationModes.contains("ON")
            if (needRebind && provider != null && !released) {
                bind()
            }
        }
    }

    private fun startProvider() {
        if (providerStarted || released) return
        providerStarted = true
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (released) return@addListener
            try {
                provider = future.get()
                if (previewView == null) return@addListener
                bind()
            } catch (e: Exception) {
                Log.e(TAG, "ProcessCameraProvider 初始化失败", e)
                _state.update { it.copy(cameraAvailable = false) }
                setStatus(R.string.message_error_camera, true)
            }
        }, mainExecutor)
    }

    override fun attachCameraXPreview(previewView: PreviewView) {
        val firstAttach = this.previewView == null
        this.previewView = previewView
        // 预览视图尺寸变化时（旋转、分屏）需要重新计算 targetResolution，否则画面会被拉伸。
        attachRotationListener()
        if (firstAttach || provider != null) {
            maybeBind()
        }
    }

    private fun maybeBind() {
        if (released) return
        if (provider == null) return
        if (!_state.value.permissionGranted) return
        bind()
    }

    /**
     * 绑定用例。
     *
     * 降级链：带 GL 特效 → 去掉特效 → 降低照片分辨率 → 只保留拍照。
     * 每一级都记录日志，方便线上排查。
     */
    private fun bind() {
        val cameraProvider = provider ?: return
        if (released) return
        val mode = _state.value.mode
        val rotation = displayRotation()
        val livePhoto = mode == CaptureMode.PHOTO && _state.value.livePhotoEnabled
        val needsVideo = mode != CaptureMode.SUPER_RES
        val stabilization = _state.value.stabilizationEnabled &&
            capability.stabilizationModes.contains("ON")
        val softwareStabilization = _state.value.stabilizationEnabled || _state.value.horizonEnabled
        val useEffect = !glFilterDowngraded
        val view = previewView

        val signature = listOf(
            mode, rotation, livePhoto, needsVideo, stabilization, softwareStabilization,
            useEffect, lensFacing, _state.value.autoCompositionEnabled,
            _state.value.selectedSpec?.label, _state.value.frontCamera
        ).joinToString("|")

        Log.d(
            TAG,
            "bind mode=$mode rotation=$rotation livePhoto=$livePhoto needsVideo=$needsVideo " +
                "useEffect=$useEffect stabilization=$stabilization horizon=${_state.value.horizonEnabled} " +
                "viewSize=${view?.width}x${view?.height} lensFacing=$lensFacing"
        )
        DebugLog.log(
            "Engine",
            "bind mode=$mode rotation=$rotation livePhoto=$livePhoto needsVideo=$needsVideo " +
                "useEffect=$useEffect stabilization=$stabilization horizon=${_state.value.horizonEnabled} " +
                "superRes=${_state.value.superResEnabled} " +
                "viewSize=${view?.width}x${view?.height} lensFacing=$lensFacing"
        )

        stopRecordingInternal()
        detachAnalysis()
        processor?.release()
        processor = null
        stabilizer.setHorizonMode(_state.value.horizonEnabled)
        if (softwareStabilization) {
            stabilizer.start()
        } else {
            stabilizer.stop()
        }

        try {
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "unbindAll 失败", e)
        }

        val aspect = _state.value.photoAspect
        val useCases = buildUseCases(
            mode,
            rotation,
            needsVideo,
            livePhoto,
            stabilization,
            IMAGE_FULL_RES
        )

        val surfaceProcessor: FilterSurfaceProcessor? = if (useEffect) {
            val previewAspect = previewView?.let {
                if (it.height > 0) it.width.toFloat() / it.height else 0f
            } ?: 0f
            val cropAspect = if (previewAspect > 0f) previewAspect else 0.75f
            FilterSurfaceProcessor(
                glExecutor,
                {
                    if (softwareStabilization) {
                        renderState.copy(
                            stabilization = stabilizer.matrix(),
                            stabilizationCrop = stabilizer.cropScale(cropAspect)
                        )
                    } else {
                        renderState
                    }
                },
                { error ->
                    // 预览流错误：记录详情，但不要直接判定「相机不可用」
                    // （录制时 CameraX 会新增输出 Surface，偶发失败不应影响拍照）
                    Log.e(TAG, "GL 预览流异常", error)
                    DebugLog.log(
                        "Engine",
                        "GL 预览流异常: ${error.javaClass.simpleName}: ${error.message}"
                    )
                },
                { error ->
                    Log.e(TAG, "GL 输出流异常", error)
                    DebugLog.log(
                        "Engine",
                        "GL 输出流异常（录制可能受影响）: ${error.javaClass.simpleName}: ${error.message}"
                    )
                    setStatus(R.string.message_error_record, true)
                }
            ).also { processor = it }
        } else {
            null
        }
        val effect: FilterCameraEffect? = surfaceProcessor?.let {
            FilterCameraEffect(
                if (needsVideo) {
                    CameraEffect.PREVIEW or CameraEffect.VIDEO_CAPTURE
                } else {
                    CameraEffect.PREVIEW
                },
                glExecutor,
                it,
                Consumer { error -> setStatus(R.string.message_error_camera, true) }
            )
        }

        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        camera = tryBind(cameraProvider, selector, useCases, effect, buildViewPort())
        if (camera == null) {
            camera = tryBind(cameraProvider, selector, useCases, effect, null)
        }
        if (camera == null && effect != null) {
            surfaceProcessor?.release()
            processor = null
            glFilterDowngraded = true
            camera = tryBind(cameraProvider, selector, useCases, null, buildViewPort())
            if (camera == null) {
                camera = tryBind(cameraProvider, selector, useCases, null, null)
            }
            if (camera != null) {
                setStatus(R.string.message_filter_degraded, false)
            }
        }
        if (camera == null && mode == CaptureMode.PHOTO) {
            val photoOnlyCases = buildUseCases(
                mode,
                rotation,
                false,
                livePhoto,
                stabilization,
                IMAGE_FULL_RES
            )
            camera = tryBind(cameraProvider, selector, photoOnlyCases, null, buildViewPort())
            if (camera == null) {
                camera = tryBind(cameraProvider, selector, photoOnlyCases, null, null)
            }
            if (camera != null) {
                Log.d(TAG, "已在不含视频用例的情况下绑定成功，长按录像不可用")
            }
        }
        if (camera == null) {
            processor?.release()
            processor = null
            val lowCases = buildUseCases(
                mode,
                rotation,
                needsVideo,
                livePhoto,
                stabilization,
                IMAGE_FALLBACK_RES
            )
            camera = tryBind(cameraProvider, selector, lowCases, null, buildViewPort())
            if (camera == null) {
                camera = tryBind(cameraProvider, selector, lowCases, null, null)
            }
            if (camera != null) {
                Log.d(TAG, "已用降低后的照片分辨率绑定成功")
                setStatus(R.string.message_quality_reduced, true)
            }
        }

        if (camera == null) {
            Log.e(TAG, "bind 失败 mode=$mode，camera 为空")
            _state.update {
                it.copy(cameraAvailable = false, ready = false, filterPreviewActive = false)
            }
            setStatus(R.string.message_error_camera, true)
            return
        }

        boundSignature = signature
        Log.d(TAG, "bind 成功 mode=$mode filterPreviewActive=$useEffect")
        _state.update { it.copy(filterPreviewActive = useEffect) }

        refreshHorizonPolling()
        attachRotationListener()

        applyFlash()
        publishZoomState()
        publishExposure()
        _state.update {
            it.copy(
                cameraAvailable = true,
                ready = true,
                busy = false,
                compositionHint = if (it.autoCompositionEnabled) it.compositionHint else null
            )
        }
    }

    private fun buildUseCases(
        mode: CaptureMode,
        rotation: Int,
        needsVideo: Boolean,
        livePhoto: Boolean,
        stabilization: Boolean,
        imageResolution: Size?
    ): List<UseCase> {
        val useCases = mutableListOf<UseCase>()

        val previewBuilder = Preview.Builder().setTargetRotation(rotation)
        val previewHost = previewView
        if (previewHost != null && previewHost.width > 0 && previewHost.height > 0) {
            try {
                previewBuilder.setTargetResolution(Size(previewHost.width, previewHost.height))
            } catch (e: Exception) {
                DebugLog.log("Engine", "setTargetResolution(预览) 失败: ${e.message}")
            }
        }
        val previewUseCase = previewBuilder.build()
        attachPreviewProvider(previewUseCase)
        preview = previewUseCase
        useCases += previewUseCase

        if (needsVideo) {
            val recorderBuilder = Recorder.Builder()
                .setQualitySelector(buildQualitySelector())
            try {
                recorderBuilder.setTargetVideoEncodingBitRate(buildBitRate())
            } catch (e: Exception) {
                DebugLog.log("Engine", "设置码率失败: ${e.message}")
            }
            val recorder = recorderBuilder.build()
            val videoBuilder = VideoCapture.Builder(recorder).setTargetRotation(rotation)
            if (stabilization) {
                try {
                    videoBuilder.setVideoStabilizationEnabled(true)
                } catch (e: Exception) {
                    DebugLog.log("Engine", "开启硬件防抖失败: ${e.message}")
                }
            }
            val videoUseCase = videoBuilder.build()
            videoCapture = videoUseCase
            useCases += videoUseCase
            if (livePhoto || mode == CaptureMode.PHOTO) {
                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(
                        if (mode == CaptureMode.PHOTO && !livePhoto) {
                            ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                        } else {
                            ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                        }
                    )
                    .setTargetRotation(rotation)
                imageResolution?.let { captureBuilder.setTargetResolution(it) }
                val captureUseCase = captureBuilder.build()
                captureUseCase.flashMode = flashModeValue(_state.value.flashMode)
                imageCapture = captureUseCase
                useCases += captureUseCase
            } else {
                imageCapture = null
            }
        } else {
            val captureBuilder = ImageCapture.Builder()
                .setCaptureMode(
                    if (mode == CaptureMode.SUPER_RES || mode == CaptureMode.PHOTO) {
                        ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY
                    } else {
                        ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY
                    }
                )
                .setTargetRotation(rotation)
            imageResolution?.let { captureBuilder.setTargetResolution(it) }
            val captureUseCase = captureBuilder.build()
            captureUseCase.flashMode = flashModeValue(_state.value.flashMode)
            imageCapture = captureUseCase
            videoCapture = null
            useCases += captureUseCase
        }

        imageAnalysis = null
        // 只有检测模型就绪时才挂分析器，否则白耗电且给不出提示
        val detectorReady = detector.ready
        if (_state.value.autoCompositionEnabled && detectorReady) {
            val analysisBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
            val analysis = analysisBuilder.build()
            val analyzer = SubjectAnalyzer(
                detectorProvider = { detector },
                useFaceDetection = true,
                onResult = { detections, faces -> onDetections(detections, faces) }
            )
            analysis.setAnalyzer(analysisExecutor, analyzer)
            subjectAnalyzer = analyzer
            imageAnalysis = analysis
            useCases += analysis
        }
        return useCases
    }

    private fun tryBind(
        cameraProvider: ProcessCameraProvider,
        selector: CameraSelector,
        useCases: List<UseCase>,
        effect: CameraEffect?,
        viewPort: ViewPort?
    ): Camera? {
        val builder = UseCaseGroup.Builder()
        useCases.forEach { builder.addUseCase(it) }
        if (effect != null) {
            builder.addEffect(effect)
        }
        if (viewPort != null) {
            builder.setViewPort(viewPort)
        }
        return try {
            cameraProvider.bindToLifecycle(lifecycleOwner, selector, builder.build())
        } catch (e: Exception) {
            Log.e(
                TAG,
                "bindToLifecycle 失败: useCases=${useCases.size} effect=${effect != null} " +
                    "viewPort=${viewPort != null} mode=${_state.value.mode}",
                e
            )
            DebugLog.log(
                "Engine",
                "bindToLifecycle 失败: ${e.javaClass.simpleName}: ${e.message}"
            )
            null
        }
    }

    private fun attachPreviewProvider(previewUseCase: Preview) {
        val target = previewView
        if (target != null) {
            previewUseCase.setSurfaceProvider(target.surfaceProvider)
        }
    }

    private fun attachRotationListener() {
        if (released) return
        val view: View? = previewView
        if (view == null) return
        if (rotationListenerView === view && rotationListener != null) return
        rotationListenerView?.removeOnLayoutChangeListener(rotationListener)
        rotationListenerView = view
        var lastWidth = view.width
        var lastHeight = view.height
        val listener = View.OnLayoutChangeListener { _, left, top, right, bottom, _, _, _, _ ->
            val width = right - left
            val height = bottom - top
            if (width > 0 && height > 0) {
                val changed = lastWidth != width || lastHeight != height
                lastWidth = width
                lastHeight = height
                if (changed && !released) {
                    applyRotation()
                }
            }
        }
        view.addOnLayoutChangeListener(listener)
        rotationListener = listener
    }

    private fun applyRotation() {
        val rotation = displayRotation()
        try {
            preview?.targetRotation = rotation
            imageCapture?.targetRotation = rotation
            videoCapture?.targetRotation = rotation
            imageAnalysis?.targetRotation = rotation
        } catch (e: Exception) {
            Log.e(TAG, "applyRotation 失败", e)
        }
    }

    private fun buildViewPort(): ViewPort? = null

    private fun displayRotation(): Int {
        val view: View? = previewView
        val display = view?.display
            ?: (context as? android.app.Activity)?.windowManager?.defaultDisplay
        return display?.rotation ?: Surface.ROTATION_0
    }

    private fun buildQualitySelector(): QualitySelector {
        val spec = _state.value.selectedSpec
        val preferred = when {
            spec != null && spec.height >= 2160 -> Quality.UHD
            spec != null && spec.height >= 1080 -> Quality.FHD
            capability.fourK30Encode -> Quality.UHD
            else -> Quality.HD
        }
        return QualitySelector.from(
            preferred,
            FallbackStrategy.higherQualityOrLowerThan(preferred)
        )
    }

    private fun buildBitRate(): Int {
        val spec = _state.value.selectedSpec
        val height = spec?.height ?: if (capability.fourK30Encode) 2160 else 720
        val fps = spec?.fps?.coerceAtLeast(24) ?: 30
        return when {
            height >= 2160 -> (48_000_000L * fps / 30L).toInt()
            height >= 1080 -> (16_000_000L * fps / 30L).toInt()
            else -> (6_000_000L * fps / 30L).toInt()
        }
    }

    private fun flashModeValue(mode: FlashMode): Int = when (mode) {
        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashMode.ON -> ImageCapture.FLASH_MODE_ON
        FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    }

    private fun buildVideoSpecs(report: CapabilityReport): List<OutputSpec> {
        val list = mutableListOf<OutputSpec>()
        if (report.fourK60Encode) {
            list += OutputSpec(3840, 2160, 60, false)
        }
        if (report.fourK30Encode) {
            list += OutputSpec(3840, 2160, 30, false)
        }
        list += OutputSpec(1920, 1080, 60, false)
        list += OutputSpec(1920, 1080, 30, false)
        val maxFps = report.maxVideoFps
        if (maxFps >= 120) {
            list += OutputSpec(1280, 720, 120, false)
        } else if (maxFps >= 60) {
            list += OutputSpec(1280, 720, 60, false)
        }
        list += OutputSpec(1280, 720, 30, false)
        return list.distinctBy { Triple(it.width, it.height, it.fps) }
    }

    private fun applyFlash() {
        imageCapture?.flashMode = flashModeValue(_state.value.flashMode)
        val wantTorch = when {
            _state.value.torchEnabled -> true
            _state.value.flashMode == FlashMode.ON &&
                (_state.value.mode == CaptureMode.VIDEO || _state.value.mode == CaptureMode.PRO) -> true
            else -> false
        }
        try {
            camera?.cameraControl?.enableTorch(wantTorch)
        } catch (e: Exception) {
            DebugLog.log("Engine", "切换补光灯失败: ${e.message}")
        }
    }

    private fun publishZoomState() {
        val zoomState = camera?.cameraInfo?.zoomState?.value ?: return
        val min = zoomState.minZoomRatio
        val max = zoomState.maxZoomRatio
        val current = zoomState.zoomRatio.coerceIn(min, max)
        val options = buildLensOptions(min, max)
        val active = options.minByOrNull { kotlin.math.abs(it.zoomRatio - current) }?.id ?: ""
        _state.update {
            it.copy(
                lensOptions = options,
                activeLensId = active,
                zoomRatio = current,
                minZoomRatio = min,
                maxZoomRatio = max
            )
        }
    }

    private fun buildLensOptions(min: Float, max: Float): List<LensOption> {
        val candidates = listOf(0.5f, 1f, 2f, 3f, 5f, 10f, 20f)
        val stops = candidates.filter { it >= min - 0.001f && it <= max + 0.001f }.take(5)
        val effective = if (stops.size >= 2) stops else listOf(min, max).distinct()
        return effective.map { ratio ->
            LensOption(id = ratio.toString(), label = zoomLabel(ratio), zoomRatio = ratio)
        }
    }

    private fun zoomLabel(ratio: Float): String =
        if (ratio < 1f) {
            String.format(Locale.US, "%.1fx", ratio)
        } else {
            String.format(Locale.US, "%.0fx", ratio)
        }

    /** 同步曝光补偿能力到 UI 状态。 */
    private fun publishExposure() {
        val range = try {
            capabilityProbe.exposureRange(lensFacing)
        } catch (e: Exception) {
            null
        }
        if (range == null) {
            _state.update { it.copy(exposure = ExposureState()) }
            return
        }
        val step = try {
            capabilityProbe.exposureStep(lensFacing)
        } catch (e: Exception) {
            1f / 6f
        }
        _state.update {
            it.copy(
                exposure = ExposureState(
                    index = 0,
                    minIndex = range.lower,
                    maxIndex = range.upper,
                    stepEv = if (step > 0f) step else 1f / 6f
                )
            )
        }
    }

    private fun onDetections(detections: List<DetectDetection>, faces: List<FaceBox>) {
        if (!_state.value.autoCompositionEnabled) {
            if (_state.value.compositionHint != null) {
                _state.update { it.copy(compositionHint = null) }
            }
            return
        }
        val hint = CompositionEngine.evaluateDetections(detections, faces)
        val label = detections.maxByOrNull { it.area }?.let {
            context.getString(it.kind.labelRes)
        }
        _state.update { it.copy(compositionHint = hint, subjectLabel = label) }
    }

    override fun setMode(mode: CaptureMode) {
        if (_state.value.mode == mode) return
        cancelCountdown()
        _state.update { it.copy(mode = mode, compositionHint = null, busy = true) }
        if (mode == CaptureMode.SUPER_RES) {
            _state.update { it.copy(superResEnabled = true) }
            scope.launch { settings.setSuperResEnabled(true) }
        }
        bind()
    }

    override fun setLens(lensId: String) {
        val ratio = lensId.toFloatOrNull() ?: return
        setZoomRatio(ratio)
    }

    override fun setZoomRatio(ratio: Float) {
        val min = _state.value.minZoomRatio
        val max = _state.value.maxZoomRatio
        val clamped = ratio.coerceIn(min, max)
        try {
            camera?.cameraControl?.setZoomRatio(clamped)
        } catch (e: Exception) {
            DebugLog.log("Engine", "设置变焦失败: ${e.message}")
        }
        val active = _state.value.lensOptions
            .minByOrNull { kotlin.math.abs(it.zoomRatio - clamped) }?.id ?: _state.value.activeLensId
        _state.update { it.copy(zoomRatio = clamped, activeLensId = active) }
    }

    override fun setFlashMode(mode: FlashMode) {
        _state.update { it.copy(flashMode = mode) }
        applyFlash()
    }

    override fun setTorchEnabled(enabled: Boolean) {
        if (_state.value.torchEnabled == enabled) return
        _state.update { it.copy(torchEnabled = enabled) }
        applyFlash()
    }

    override fun setExposureCompensation(index: Int) {
        val exposure = _state.value.exposure
        if (!exposure.available) {
            setStatus(R.string.message_exposure_unavailable, true)
            return
        }
        val clamped = index.coerceIn(exposure.minIndex, exposure.maxIndex)
        try {
            camera?.cameraControl?.setExposureCompensationIndex(clamped)
        } catch (e: Exception) {
            DebugLog.log("Engine", "设置曝光补偿失败: ${e.message}")
        }
        _state.update { it.copy(exposure = it.exposure.copy(index = clamped)) }
    }

    override fun setPreset(preset: FilterPreset) {
        renderState = renderState.copy(
            preset = preset,
            lut = resolveLut(preset)
        )
        _state.update { it.copy(preset = preset) }
        scope.launch { settings.setPreset(preset.name) }
    }

    override fun setFilterIntensity(intensity: Float) {
        val value = intensity.coerceIn(0f, 1f)
        renderState = renderState.copy(intensity = value)
        _state.update { it.copy(filterIntensity = value) }
        scope.launch { settings.setFilterIntensity(value) }
    }

    /**
     * 解析当前预设对应的 LUT。
     *
     * - 带 [FilterPreset.lutAsset] 的预设：加载 res/raw 下的内置真实 LUT
     * - CUSTOM_LUT：使用用户导入的 LUT
     * - 其余：null（纯参数化调色）
     */
    private fun resolveLut(preset: FilterPreset): com.fanhua.camera.lut.LutData? {
        return when {
            preset.lutAsset != null -> {
                LutStore.selectBuiltin(context, preset.lutAsset)
                LutStore.cached
            }
            preset == FilterPreset.CUSTOM_LUT -> LutStore.cached
            else -> null
        }
    }

    override fun setStabilizationEnabled(enabled: Boolean) {
        if (_state.value.stabilizationEnabled == enabled) return
        _state.update { it.copy(stabilizationEnabled = enabled) }
        scope.launch { settings.setStabilizationEnabled(enabled) }
        bind()
    }

    override fun setHorizonEnabled(enabled: Boolean) {
        if (_state.value.horizonEnabled == enabled) return
        _state.update { it.copy(horizonEnabled = enabled) }
        scope.launch { settings.setHorizonEnabled(enabled) }
        if (!enabled) refreshHorizonPolling()
        bind()
    }

    override fun setSuperResEnabled(enabled: Boolean) {
        _state.update { it.copy(superResEnabled = enabled) }
        scope.launch { settings.setSuperResEnabled(enabled) }
    }

    override fun setLivePhotoEnabled(enabled: Boolean) {
        if (_state.value.livePhotoEnabled == enabled) return
        _state.update { it.copy(livePhotoEnabled = enabled) }
        bind()
    }

    override fun setAutoCompositionEnabled(enabled: Boolean) {
        if (_state.value.autoCompositionEnabled == enabled) return
        // 打开但没有模型时，先尝试加载；仍失败就提示去设置里下载，不静默失败
        if (enabled && !detector.ready) {
            loadSelectedDetector()
            if (!detector.ready) {
                setStatus(R.string.detector_need_model, true)
            }
        }
        _state.update { it.copy(autoCompositionEnabled = enabled, compositionHint = null) }
        scope.launch { settings.setAutoCompositionEnabled(enabled) }
        bind()
    }

    /* ------------------------------------------------------------ 检测模型管理 */

    /** 加载当前选中的模型；没有选中或文件损坏时把状态置为未就绪。 */
    private fun loadSelectedDetector() {
        val selected = DetectorStore.selected(context)
        if (selected == null) {
            _state.update { it.copy(detectorReady = false, detectorModelId = null) }
            return
        }
        val definition = DetectorModels.byId(selected)
        val ok = if (definition != null) {
            detector.load(definition)
        } else {
            detector.loadCustom()
        }
        _state.update {
            it.copy(detectorReady = ok, detectorModelId = if (ok) selected else null)
        }
        DebugLog.log("Engine", "加载检测模型 $selected -> $ok")
    }

    override suspend fun downloadDetectorModel(modelId: String): Boolean {
        val definition = DetectorModels.byId(modelId) ?: return false
        _state.update { it.copy(detectorDownloadPercent = 0) }
        val downloader = ModelDownloader(context)
        val result = withContext(Dispatchers.IO) {
            downloader.download(definition) { progress ->
                _state.update {
                    it.copy(
                        detectorDownloadPercent = progress.percent,
                        detectorDownloadSpeed = progress.bytesPerSecond,
                        detectorDownloadedBytes = progress.downloadedBytes,
                        detectorTotalBytes = progress.totalBytes
                    )
                }
            }
        }
        _state.update {
            it.copy(
                detectorDownloadPercent = null,
                detectorDownloadSpeed = 0L,
                detectorDownloadedBytes = 0L,
                detectorTotalBytes = 0L
            )
        }
        return when (result) {
            is ModelDownloader.Result.Success -> {
                DetectorStore.select(context, modelId)
                loadSelectedDetector()
                setStatus(R.string.detector_download_done, false)
                if (_state.value.autoCompositionEnabled) bind()
                true
            }
            is ModelDownloader.Result.Cancelled -> false
            is ModelDownloader.Result.Failed -> {
                DebugLog.log("Engine", "模型下载失败: ${result.reason}")
                setStatus(R.string.detector_download_failed, true)
                false
            }
        }
    }

    override fun useDetectorModel(modelId: String) {
        if (!DetectorStore.isDownloaded(context, modelId)) return
        DetectorStore.select(context, modelId)
        loadSelectedDetector()
        if (_state.value.autoCompositionEnabled) bind()
    }

    override fun deleteDetectorModel(modelId: String) {
        if (_state.value.detectorModelId == modelId) {
            detector.release()
            _state.update { it.copy(detectorReady = false, detectorModelId = null) }
        }
        DetectorStore.delete(context, modelId)
        loadSelectedDetector()
        if (_state.value.autoCompositionEnabled) bind()
    }

    override suspend fun importDetectorModel(bytes: ByteArray): Boolean {
        val ok = withContext(Dispatchers.IO) {
            DetectorStore.importCustom(context, bytes)
        }
        if (!ok) return false
        DetectorStore.select(context, DetectorModels.CUSTOM_ID)
        loadSelectedDetector()
        if (_state.value.autoCompositionEnabled) bind()
        return _state.value.detectorReady
    }

    /* ------------------------------------------------------------ 自动构图 */

    /**
     * 点击即构图：开启检测 → 等一帧结果 → 按建议裁切。
     *
     * 若模型未就绪，先提示下载；若已有构图建议，直接应用。
     */
    override fun applyAutoComposition() {
        // 已开启引导时，直接用最新的建议裁切
        val existing = _state.value.compositionHint
        if (existing?.cropRect != null) {
            _state.update { it.copy(autoCropRect = existing.cropRect) }
            setStatus(R.string.composition_aligned, false)
            return
        }
        if (!detector.ready) {
            loadSelectedDetector()
            if (!detector.ready) {
                setStatus(R.string.detector_need_model, true)
                return
            }
        }
        // 临时开启检测，等待一帧结果后再裁切
        _state.update { it.copy(autoComposing = true) }
        val wasEnabled = _state.value.autoCompositionEnabled
        if (!wasEnabled) {
            _state.update { it.copy(autoCompositionEnabled = true) }
            bind()
        }
        scope.launch {
            // 最多等 1.5 秒拿到一次检测结果
            var waited = 0
            while (waited < 1500 && _state.value.compositionHint == null) {
                delay(100)
                waited += 100
            }
            val hint = _state.value.compositionHint
            val crop = hint?.cropRect
            if (crop != null) {
                _state.update { it.copy(autoCropRect = crop, autoComposing = false) }
                setStatus(R.string.composition_aligned, false)
            } else {
                _state.update { it.copy(autoComposing = false) }
                setStatus(R.string.detector_no_subject, true)
            }
            // 若原本没开，用完就关掉，避免持续耗电
            if (!wasEnabled) {
                _state.update { it.copy(autoCompositionEnabled = false) }
                bind()
            }
        }
    }

    override fun clearAutoComposition() {
        if (_state.value.autoCropRect == null) return
        _state.update { it.copy(autoCropRect = null) }
    }

    override fun setGridEnabled(enabled: Boolean) {
        if (_state.value.gridEnabled == enabled) return
        _state.update { it.copy(gridEnabled = enabled) }
        scope.launch { settings.setGridEnabled(enabled) }
    }

    override fun setTimerSeconds(seconds: Int) {
        val value = seconds.coerceIn(0, 10)
        if (_state.value.timerSeconds == value) return
        if (value == 0) cancelCountdown()
        _state.update { it.copy(timerSeconds = value) }
        scope.launch { settings.setTimerSeconds(value) }
    }

    override fun setPhotoAspect(aspect: PhotoAspect) {
        if (_state.value.photoAspect == aspect) return
        _state.update { it.copy(photoAspect = aspect) }
        scope.launch { settings.setPhotoAspect(aspect) }
    }

    override fun setMirrorSelfie(enabled: Boolean) {
        if (_state.value.mirrorSelfie == enabled) return
        _state.update { it.copy(mirrorSelfie = enabled) }
        scope.launch { settings.setMirrorSelfie(enabled) }
    }

    override fun setWatermarkEnabled(enabled: Boolean) {
        setWatermarkMode(if (enabled) WatermarkMode.FRAME else WatermarkMode.OFF)
    }

    override fun setWatermarkMode(mode: WatermarkMode) {
        if (_state.value.watermarkMode == mode) return
        _state.update { it.copy(watermarkMode = mode) }
        scope.launch { settings.setWatermarkMode(mode) }
    }

    override fun setShutterSoundEnabled(enabled: Boolean) {
        if (_state.value.shutterSoundEnabled == enabled) return
        _state.update { it.copy(shutterSoundEnabled = enabled) }
        scope.launch { settings.setShutterSoundEnabled(enabled) }
    }

    override fun selectSpec(spec: OutputSpec) {
        _state.update { it.copy(selectedSpec = spec, busy = true) }
        scope.launch { settings.setSelectedSpecLabel(spec.label) }
        if (_state.value.mode == CaptureMode.PRO || _state.value.mode == CaptureMode.VIDEO) {
            bind()
        }
    }

    override fun tapToFocus(x: Float, y: Float) {
        val activeCamera = camera ?: return
        val view = previewView
        val width = view?.width ?: 0
        val height = view?.height ?: 0
        if (width <= 0 || height <= 0) return
        try {
            val factory = SurfaceOrientedMeteringPointFactory(width.toFloat(), height.toFloat())
            val point = factory.createPoint(x * width, y * height)
            val action = FocusMeteringAction.Builder(point).build()
            activeCamera.cameraControl.startFocusAndMetering(action)
            // 手动对焦生效后，标记退出「手动对焦」状态，避免 UI 误显示。
            if (_state.value.manualFocusActive) {
                _state.update { it.copy(manualFocusActive = false) }
            }
        } catch (e: Exception) {
            DebugLog.log("Engine", "对焦失败: ${e.message}")
        }
    }

    override fun setManualFocus(position: Float) {
        val value = position.coerceIn(0f, 1f)
        _state.update { it.copy(manualFocusPosition = value, manualFocusActive = true) }
        try {
            camera?.cameraControl?.setLinearZoom(value)
        } catch (e: Exception) {
            DebugLog.log("Engine", "手动对焦失败: ${e.message}")
        }
    }

    override fun resetManualFocus() {
        if (!_state.value.manualFocusActive) return
        _state.update { it.copy(manualFocusActive = false, manualFocusPosition = 0.5f) }
        setStatus(R.string.message_focus_reset, false)
    }

    override fun takePhoto() {
        Log.d(
            TAG,
            "takePhoto mode=${_state.value.mode} busy=${_state.value.busy} " +
                "livePhoto=${_state.value.livePhotoEnabled} capture=${imageCapture != null}"
        )
        if (_state.value.livePhotoEnabled && _state.value.mode == CaptureMode.PHOTO) {
            startLivePhoto()
            return
        }
        val seconds = _state.value.timerSeconds
        if (seconds > 0 && imageCapture != null && !_state.value.busy) {
            startCountdown(seconds)
            return
        }
        capturePhotoNow()
    }

    private fun startCountdown(seconds: Int) {
        if (countdownJob?.isActive == true) return
        countdownJob = scope.launch {
            var remain = seconds
            _state.update { it.copy(countdownRemaining = remain) }
            while (remain > 0) {
                delay(1000)
                if (!isActive) return@launch
                remain -= 1
                _state.update { it.copy(countdownRemaining = remain) }
            }
            capturePhotoNow()
        }
    }

    override fun cancelCountdown() {
        countdownJob?.cancel()
        countdownJob = null
        if (_state.value.countdownRemaining != 0) {
            _state.update { it.copy(countdownRemaining = 0) }
        }
    }

    private fun capturePhotoNow() {
        val capture = imageCapture ?: return
        if (_state.value.busy) return
        if (_state.value.shutterSoundEnabled) playShutterSound()
        _state.update { it.copy(busy = true) }
        setStatus(R.string.message_saving, false)
        val file = File(context.cacheDir, "fanhua_capture_" + System.currentTimeMillis() + ".jpg")
        val options = ImageCapture.OutputFileOptions.Builder(file).build()
        capture.takePicture(options, mainExecutor, object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                savePhotoFile(file)
            }

            override fun onError(exception: ImageCaptureException) {
                file.delete()
                _state.update { it.copy(busy = false) }
                setStatus(R.string.message_error_save, true)
                DebugLog.log("Engine", "拍照失败: ${exception.message}")
            }
        })
    }

    private fun playShutterSound() {
        try {
            val candidates = arrayOf(
                "/system/media/audio/ui/camera_click.ogg",
                "/system/media/audio/ui/camera_click_48k.ogg",
                "/system/media/audio/ui/camera_shutter.ogg",
                "/system/media/audio/ui/Shutter_01.ogg"
            )
            val path = candidates.firstOrNull { File(it).exists() } ?: return
            val player = MediaPlayer()
            player.setDataSource(path)
            player.setOnCompletionListener { p -> p.release() }
            player.setOnErrorListener { p, _, _ -> p.release(); true }
            player.prepare()
            player.start()
        } catch (e: Exception) {
            DebugLog.log("Engine", "播放快门音失败: ${e.message}")
        }
    }

    private fun savePhotoFile(file: File) {
        val preset = _state.value.preset
        val intensity = _state.value.filterIntensity
        val upscale = _state.value.superResEnabled || _state.value.mode == CaptureMode.SUPER_RES
        val aspect = _state.value.photoAspect
        val mirror = _state.value.frontCamera && _state.value.mirrorSelfie
        val watermarkMode = _state.value.watermarkMode
        val srcSize = try {
            val bounds = android.graphics.BitmapFactory.Options().apply { inJustDecodeBounds = true }
            android.graphics.BitmapFactory.decodeFile(file.absolutePath, bounds)
            "${bounds.outWidth}x${bounds.outHeight}"
        } catch (e: Exception) {
            "未知"
        }
        DebugLog.log(
            "Engine",
            "拍照已保存 file=${file.absolutePath} size=$srcSize preset=${preset.name} " +
                "intensity=$intensity upscale=$upscale aspect=$aspect mirror=$mirror " +
                "watermarkMode=$watermarkMode mode=${_state.value.mode}"
        )
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                photoProcessor.process(
                    file, preset, intensity, upscale, aspect, mirror, watermarkMode,
                    autoCrop = _state.value.autoCropRect
                )
            }
            file.delete()
            if (uri == null) {
                _state.update { it.copy(busy = false) }
                setStatus(R.string.message_error_save, true)
            } else {
                _state.update {
                    it.copy(
                        busy = false,
                        captureCount = it.captureCount + 1,
                        lastCaptureUri = uri.toString()
                    )
                }
                setStatus(R.string.message_photo_saved, false)
            }
        }
    }

    /**
     * 实况照片。
     *
     * 顺序很关键：**先启动录像，等 Start 事件后再拍照**。
     * 早先的实现是「拍照和录像同时发起」，两者抢占相机资源，
     * 表现为按快门后卡住几秒、随后报「相机不可用」。
     */
    private fun startLivePhoto() {
        if (_state.value.busy || _state.value.recording || recording != null) return
        if (videoCapture == null || imageCapture == null) {
            setStatus(R.string.message_error_record, true)
            return
        }
        if (_state.value.shutterSoundEnabled) playShutterSound()
        _state.update { it.copy(busy = true) }

        // 1) 先启动录像
        startRecordingInternal(livePhoto = true)
        if (recording == null) {
            // 录像没起来，直接退出，避免卡在 busy 状态
            _state.update { it.copy(busy = false) }
            setStatus(R.string.message_error_record, true)
            return
        }

        // 2) 录像起来后再拍照（延迟 260ms 让编码器稳定）
        scope.launch {
            delay(260)
            captureLivePhotoStill()
        }

        // 3) 定时停止
        livePhotoJob?.cancel()
        livePhotoJob = scope.launch {
            delay(LIVE_PHOTO_DURATION_MS)
            stopRecording()
        }
    }

    /** 实况照片的静态帧。 */
    private fun captureLivePhotoStill() {
        val photo = imageCapture ?: run {
            _state.update { it.copy(busy = false) }
            return
        }
        val file = File(context.cacheDir, "fanhua_live_" + System.currentTimeMillis() + ".jpg")
        try {
            photo.takePicture(
                ImageCapture.OutputFileOptions.Builder(file).build(),
                mainExecutor,
                object : ImageCapture.OnImageSavedCallback {
                    override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                        val preset = _state.value.preset
                        val intensity = _state.value.filterIntensity
                        val upscale = _state.value.superResEnabled
                        val aspect = _state.value.photoAspect
                        val mirror = _state.value.frontCamera && _state.value.mirrorSelfie
                        val watermarkMode = _state.value.watermarkMode
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) {
                                photoProcessor.process(
                                    file, preset, intensity, upscale, aspect, mirror, watermarkMode
                                )
                            }
                            file.delete()
                            if (uri != null) {
                                livePhotoSource = uri
                                livePhotoReady = true
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                        _state.update { it.copy(busy = false) }
                    }
                }
            )
        } catch (e: Exception) {
            file.delete()
            _state.update { it.copy(busy = false) }
            return
        }
    }

    private fun finalizeLivePhoto(videoUri: Uri) {
        val source = livePhotoSource
        livePhotoSource = null
        livePhotoReady = false
        if (source == null) return
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                MotionPhotoBuilder.build(context, uriToFile(source), videoUri)
            }
            withContext(Dispatchers.IO) {
                try {
                    context.contentResolver.delete(source, null, null)
                    context.contentResolver.delete(videoUri, null, null)
                } catch (e: Exception) {
                    DebugLog.log("Engine", "清理实况临时文件失败: ${e.message}")
                }
            }
            _state.update {
                it.copy(
                    busy = false,
                    captureCount = if (uri != null) it.captureCount + 1 else it.captureCount,
                    lastCaptureUri = uri?.toString() ?: it.lastCaptureUri
                )
            }
            if (uri == null) {
                setStatus(R.string.message_error_save, true)
            } else {
                setStatus(R.string.live_photo_saved, false)
            }
        }
    }

    private fun uriToFile(uri: Uri): File {
        val file = File(context.cacheDir, "fanhua_live_merge_" + System.currentTimeMillis() + ".jpg")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        } catch (e: Exception) {
            DebugLog.log("Engine", "读取实况源文件失败: ${e.message}")
        }
        return file
    }

    override fun startRecording() {
        startRecordingInternal(livePhoto = false)
    }

    private fun startRecordingInternal(livePhoto: Boolean = false) {
        if (_state.value.recording || recording != null) return
        val capture = videoCapture ?: return
        val name = "FH_" + System.currentTimeMillis() + ".mp4"
        var pending = try {
            buildPendingRecording(capture, name)
        } catch (e: Exception) {
            DebugLog.log("Engine", "准备录制失败: ${e.message}")
            null
        }
        if (pending == null) {
            setStatus(R.string.message_error_record, true)
            return
        }
        if (hasAudioPermission()) {
            pending = try {
                pending.withAudioEnabled()
            } catch (e: Exception) {
                pending
            }
        }
        val handle = try {
            pending.start(mainExecutor) { event -> onVideoEvent(event) }
        } catch (e: Exception) {
            DebugLog.log("Engine", "开始录制失败: ${e.message}")
            setStatus(R.string.message_error_record, true)
            return
        }
        recording = handle
        _state.update { it.copy(recording = true, recordingPaused = false, recordingDurationMs = 0L) }
        if (!livePhoto) setStatus(R.string.message_recording_started, false)
    }

    private fun buildPendingRecording(capture: VideoCapture<Recorder>, name: String): PendingRecording {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val values = ContentValues().apply {
                put(MediaStore.Video.Media.DISPLAY_NAME, name)
                put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
                put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/FanhuaCamera")
                put(MediaStore.Video.Media.IS_PENDING, 1)
            }
            val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
            val uri: Uri = context.contentResolver.insert(collection, values)
                ?: throw IllegalStateException("媒体库插入失败")
            val options = MediaStoreOutputOptions.Builder(context.contentResolver, uri).build()
            return capture.output.prepareRecording(context, options)
        }
        val dir = File(
            context.getExternalFilesDir(Environment.DIRECTORY_MOVIES) ?: context.filesDir,
            "FanhuaCamera"
        )
        if (!dir.exists()) dir.mkdirs()
        val options = FileOutputOptions.Builder(File(dir, name)).build()
        return capture.output.prepareRecording(context, options)
    }

    private fun onVideoEvent(event: VideoRecordEvent) {
        when (event) {
            is VideoRecordEvent.Start -> {
                _state.update { it.copy(recording = true, recordingPaused = false) }
                startRecordingTimer()
            }

            is VideoRecordEvent.Status -> {
                val duration = event.recordingStats.recordedDurationNanos / 1_000_000L
                _state.update { it.copy(recordingDurationMs = duration) }
            }

            is VideoRecordEvent.Pause -> {
                _state.update { it.copy(recordingPaused = true) }
            }

            is VideoRecordEvent.Resume -> {
                _state.update { it.copy(recordingPaused = false) }
            }

            is VideoRecordEvent.Finalize -> {
                recording = null
                stopRecordingTimer()
                val live = livePhotoReady
                livePhotoReady = false
                _state.update { it.copy(recording = false, recordingPaused = false, recordingDurationMs = 0L) }
                if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                    val errName = when (event.error) {
                        VideoRecordEvent.Finalize.ERROR_INVALID_OUTPUT_OPTIONS -> "INVALID_OUTPUT_OPTIONS"
                        VideoRecordEvent.Finalize.ERROR_ENCODING_FAILED -> "ENCODING_FAILED"
                        VideoRecordEvent.Finalize.ERROR_RECORDER_ERROR -> "RECORDER_ERROR"
                        VideoRecordEvent.Finalize.ERROR_NO_VALID_DATA -> "NO_VALID_DATA"
                        VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> "DURATION_LIMIT"
                        VideoRecordEvent.Finalize.ERROR_FILE_SIZE_LIMIT_REACHED -> "FILE_SIZE_LIMIT"
                        VideoRecordEvent.Finalize.ERROR_INSUFFICIENT_STORAGE -> "INSUFFICIENT_STORAGE"
                        VideoRecordEvent.Finalize.ERROR_SOURCE_INACTIVE -> "SOURCE_INACTIVE"
                        VideoRecordEvent.Finalize.ERROR_DURATION_LIMIT_REACHED -> "DURATION_LIMIT"
                        else -> "code=${event.error}"
                    }
                    Log.e(TAG, "录制 Finalize 失败: $errName cause=${event.cause?.message}")
                    DebugLog.log("Engine", "录制 Finalize 失败: $errName cause=${event.cause?.message}")
                    event.outputResults.outputUri?.let { uri ->
                        try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                            DebugLog.log("Engine", "删除失败录像失败: ${e.message}")
                        }
                    }
                    if (live) {
                        livePhotoSource?.let { src ->
                            try {
                                context.contentResolver.delete(src, null, null)
                            } catch (e: Exception) {
                                DebugLog.log("Engine", "删除实况源失败: ${e.message}")
                            }
                        }
                        livePhotoSource = null
                        _state.update { it.copy(busy = false) }
                    }
                    setStatus(R.string.message_error_record, true)
                } else if (live) {
                    event.outputResults.outputUri?.let { uri ->
                        finalizeLivePhoto(uri)
                    } ?: run {
                        livePhotoSource?.let { src ->
                            try {
                                context.contentResolver.delete(src, null, null)
                            } catch (e: Exception) {
                                DebugLog.log("Engine", "删除实况源失败: ${e.message}")
                            }
                        }
                        livePhotoSource = null
                        _state.update { it.copy(busy = false) }
                        setStatus(R.string.message_error_record, true)
                    }
                } else {
                    setStatus(R.string.message_video_saved, false)
                }
            }

            else -> Unit
        }
    }

    private fun startRecordingTimer() {
        stopRecordingTimer()
        recordingTimerJob = scope.launch {
            while (isActive) {
                delay(500)
                if (!_state.value.recording) break
            }
        }
    }

    private fun stopRecordingTimer() {
        recordingTimerJob?.cancel()
        recordingTimerJob = null
    }

    private fun stopRecordingInternal() {
        val handle = recording
        recording = null
        stopRecordingTimer()
        if (handle != null) {
            try {
                handle.stop()
            } catch (e: Exception) {
                Log.e(TAG, "停止录制失败", e)
            }
            try {
                handle.close()
            } catch (e: Exception) {
                Log.e(TAG, "关闭录制失败", e)
            }
        }
        if (_state.value.recording) {
            _state.update { it.copy(recording = false, recordingPaused = false, recordingDurationMs = 0L) }
        }
    }

    override fun pauseRecording() {
        val handle = recording ?: return
        if (_state.value.recordingPaused) return
        try {
            handle.pause()
            _state.update { it.copy(recordingPaused = true) }
            setStatus(R.string.message_recording_paused, false)
        } catch (e: Exception) {
            DebugLog.log("Engine", "暂停录制失败: ${e.message}")
        }
    }

    override fun resumeRecording() {
        val handle = recording ?: return
        if (!_state.value.recordingPaused) return
        try {
            handle.resume()
            _state.update { it.copy(recordingPaused = false) }
            setStatus(R.string.message_recording_resumed, false)
        } catch (e: Exception) {
            DebugLog.log("Engine", "恢复录制失败: ${e.message}")
        }
    }

    override fun stopRecording() {
        if (recording == null) return
        stopRecordingInternal()
        setStatus(R.string.message_recording_stopped, false)
    }

    override fun switchCamera() {
        lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
            CameraSelector.LENS_FACING_FRONT
        } else {
            CameraSelector.LENS_FACING_BACK
        }
        capability = capabilityProbe.probe(lensFacing)
        _state.update {
            it.copy(
                lensOptions = emptyList(),
                activeLensId = "",
                compositionHint = null,
                busy = true,
                frontCamera = lensFacing == CameraSelector.LENS_FACING_FRONT,
                torchEnabled = false
            )
        }
        bind()
        probeAsync()
    }

    private fun refreshHorizonPolling() {
        horizonPollJob?.cancel()
        horizonPollJob = null
        if (!_state.value.horizonEnabled || released) {
            _horizonRoll.value = 0f
            return
        }
        horizonPollJob = scope.launch {
            while (true) {
                delay(50)
                val value = stabilizer.rollDegrees
                if (kotlin.math.abs(value - _horizonRoll.value) > 0.2f) {
                    _horizonRoll.value = value
                }
            }
        }
    }

    override fun release() {
        released = true
        stopRecordingInternal()
        livePhotoJob?.cancel()
        livePhotoJob = null
        horizonPollJob?.cancel()
        horizonPollJob = null
        countdownJob?.cancel()
        countdownJob = null
        detachAnalysis()
        stabilizer.stop()
        try {
            provider?.unbindAll()
        } catch (e: Exception) {
            Log.e(TAG, "unbindAll 失败", e)
        }
        camera = null
        imageCapture = null
        videoCapture = null
        preview = null
        processor?.release()
        processor = null
        statusJob?.cancel()
        statusJob = null
        glExecutor.shutdown()
        analysisExecutor.shutdown()
        rotationListenerView?.removeOnLayoutChangeListener(rotationListener)
        rotationListenerView = null
        rotationListener = null
        detector.release()
    }

    private fun detachAnalysis() {
        try {
            imageAnalysis?.clearAnalyzer()
        } catch (e: Exception) {
            DebugLog.log("Engine", "清除分析器失败: ${e.message}")
        }
        imageAnalysis = null
        subjectAnalyzer?.release()
        subjectAnalyzer = null
    }

    private fun hasAudioPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED

    private fun setStatus(resId: Int, isError: Boolean) {
        _state.update { it.copy(statusMessage = StatusMessage(resId, isError)) }
        statusJob?.cancel()
        statusJob = scope.launch {
            delay(2200)
            _state.update { it.copy(statusMessage = null) }
        }
    }

    private companion object {
        const val TAG = "FanhuaCamera"
        const val LIVE_PHOTO_DURATION_MS = 3000L
        val IMAGE_FULL_RES = Size(3840, 2160)
        val IMAGE_FALLBACK_RES = Size(1920, 1080)
    }
}
