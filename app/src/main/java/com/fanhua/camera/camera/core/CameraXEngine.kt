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
import android.util.Rational
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
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
import androidx.camera.core.SurfaceRequest
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
import com.fanhua.camera.camera.analyzer.FaceAnalyzer
import com.fanhua.camera.camera.capture.FilterCameraEffect
import com.fanhua.camera.camera.capture.FilterSurfaceProcessor
import com.fanhua.camera.camera.capture.RenderState
import com.fanhua.camera.camera.filters.FilterPreset
import com.fanhua.camera.camera.stabilize.GyroStabilizer
import com.fanhua.camera.data.SettingsRepository
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
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraXEngine(
    private val context: Context,
    private val lifecycleOwner: LifecycleOwner,
    private val scope: CoroutineScope
) : CameraController {

    private val _state = MutableStateFlow(CameraUiState())
    override val state: StateFlow<CameraUiState> = _state.asStateFlow()

    private val mainExecutor = ContextCompat.getMainExecutor(context)
    private val glExecutor: ExecutorService = Executors.newSingleThreadExecutor()
    private val analysisExecutor: ExecutorService = Executors.newSingleThreadExecutor()

    private val settings = SettingsRepository(context)
    private val capabilityProbe = CapabilityProbe(context)
    private val photoProcessor = PhotoProcessor(context)
    private val stabilizer = GyroStabilizer(context) { displayRotation() }

    private var previewView: PreviewView? = null
    private var proSurfaceView: SurfaceView? = null
    private var pendingProRequest: SurfaceRequest? = null
    private var proHolderCallbackAttached = false

    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private var preview: Preview? = null
    private var imageCapture: ImageCapture? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var imageAnalysis: ImageAnalysis? = null
    private var faceAnalyzer: FaceAnalyzer? = null
    private var processor: FilterSurfaceProcessor? = null
    private var recording: Recording? = null

    private var lensFacing = CameraSelector.LENS_FACING_BACK
    private var capability: CapabilityReport = CapabilityReport()
    private var renderState = RenderState(FilterPreset.DREAM, 1f, false)
    private var statusJob: Job? = null
    private var livePhotoJob: Job? = null
    private var viewPortListenerAttached = false
    private var rotationListener: View.OnLayoutChangeListener? = null
    private var rotationListenerView: View? = null
    private var glFilterDowngraded = false
    private var providerStarted = false
    private var probeStarted = false
    private var released = false

    init {
        scope.launch {
            val stored = settings.settings.first()
            renderState = RenderState(FilterPreset.fromKey(stored.presetKey), stored.filterIntensity, false)
            _state.update {
                it.copy(
                    preset = renderState.preset,
                    filterIntensity = renderState.intensity,
                    stabilizationEnabled = stored.stabilizationEnabled,
                    superResEnabled = stored.superResEnabled,
                    autoCompositionEnabled = stored.autoCompositionEnabled
                )
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
                bind()
            } catch (e: Exception) {
                Log.e(TAG, "ProcessCameraProvider init failed", e)
                _state.update { it.copy(cameraAvailable = false) }
                setStatus(R.string.message_error_camera, true)
            }
        }, mainExecutor)
    }

    override fun attachCameraXPreview(previewView: PreviewView) {
        this.previewView = previewView
        maybeBind()
    }

    override fun attachProPreview(surfaceView: SurfaceView) {
        this.proSurfaceView = surfaceView
        if (!proHolderCallbackAttached) {
            proHolderCallbackAttached = true
            surfaceView.holder.addCallback(object : SurfaceHolder.Callback {
                override fun surfaceCreated(holder: SurfaceHolder) {
                    val request = pendingProRequest
                    pendingProRequest = null
                    if (request != null && holder.surface.isValid) {
                        request.provideSurface(holder.surface, mainExecutor) { }
                    }
                }

                override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                }

                override fun surfaceDestroyed(holder: SurfaceHolder) {
                    pendingProRequest?.willNotProvideSurface()
                    pendingProRequest = null
                }
            })
        }
        maybeBind()
    }

    private fun maybeBind() {
        if (released) return
        if (provider == null) return
        if (!_state.value.permissionGranted) return
        bind()
    }

    private fun useProSurface(): Boolean {
        val mode = _state.value.mode
        return mode == CaptureMode.PRO || mode == CaptureMode.SUPER_RES
    }

    private fun bind() {
        val cameraProvider = provider ?: return
        if (released) return
        val mode = _state.value.mode
        val rotation = displayRotation()
        val livePhoto = mode == CaptureMode.PHOTO && _state.value.livePhotoEnabled
        val needsVideo = mode == CaptureMode.VIDEO || mode == CaptureMode.PRO || livePhoto
        val stabilization = _state.value.stabilizationEnabled &&
            capability.stabilizationModes.contains("ON")
        val softwareStabilization = _state.value.stabilizationEnabled
        val useEffect = !glFilterDowngraded
        val view = if (useProSurface()) proSurfaceView else previewView
        Log.d(
            TAG,
            "bind mode=$mode rotation=$rotation livePhoto=$livePhoto needsVideo=$needsVideo " +
                "useEffect=$useEffect stabilization=$stabilization " +
                "viewSize=${view?.width}x${view?.height} lensFacing=$lensFacing"
        )

        stopRecordingInternal()
        detachAnalysis()
        processor?.release()
        processor = null
        if (softwareStabilization) {
            stabilizer.start()
        } else {
            stabilizer.stop()
        }

        try {
            cameraProvider.unbindAll()
        } catch (e: Exception) {
            setStatus(R.string.message_error_camera, true)
        }

        val useCases = buildUseCases(
            mode,
            rotation,
            needsVideo,
            livePhoto,
            stabilization,
            IMAGE_FULL_RES
        )

        val surfaceProcessor: FilterSurfaceProcessor? = if (useEffect) {
            FilterSurfaceProcessor(
                glExecutor,
                {
                    if (softwareStabilization) {
                        renderState.copy(stabilization = stabilizer.matrix())
                    } else {
                        renderState
                    }
                },
                { error -> setStatus(R.string.message_error_camera, true) }
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

        val viewPort = buildViewPort()
        val selector = CameraSelector.Builder().requireLensFacing(lensFacing).build()
        camera = tryBind(cameraProvider, selector, useCases, effect, viewPort)
        if (camera == null) {
            camera = tryBind(cameraProvider, selector, useCases, effect, null)
        }
        if (camera == null && effect != null) {
            surfaceProcessor?.release()
            processor = null
            glFilterDowngraded = true
            camera = tryBind(cameraProvider, selector, useCases, null, viewPort)
            if (camera == null) {
                camera = tryBind(cameraProvider, selector, useCases, null, null)
            }
            if (camera != null) {
                setStatus(R.string.message_filter_degraded, false)
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
            camera = tryBind(cameraProvider, selector, lowCases, null, viewPort)
            if (camera == null) {
                camera = tryBind(cameraProvider, selector, lowCases, null, null)
            }
            if (camera != null) {
                Log.d(TAG, "bind ok with reduced image resolution")
                setStatus(R.string.message_quality_reduced, true)
            }
        }

        if (camera == null) {
            Log.e(TAG, "bind failed for mode=$mode, camera is null")
            _state.update {
                it.copy(cameraAvailable = false, ready = false, filterPreviewActive = false)
            }
            setStatus(R.string.message_error_camera, true)
            return
        }

        Log.d(TAG, "bind ok mode=$mode filterPreviewActive=$useEffect")
        _state.update { it.copy(filterPreviewActive = useEffect) }

        if (viewPort == null) {
            requestViewPortRebind()
        }
        attachRotationListener()

        applyFlash()
        publishZoomState()
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
        val previewHost = if (useProSurface()) proSurfaceView else previewView
        if (previewHost != null && previewHost.width > 0 && previewHost.height > 0) {
            try {
                previewBuilder.setTargetResolution(Size(previewHost.width, previewHost.height))
            } catch (e: Exception) {
            }
        }
        val previewUseCase = previewBuilder.build()
        attachPreviewProvider(previewUseCase)
        preview = previewUseCase
        useCases += previewUseCase

        if (needsVideo) {
            val recorder = Recorder.Builder()
                .setQualitySelector(buildQualitySelector())
                .build()
            val videoBuilder = VideoCapture.Builder(recorder).setTargetRotation(rotation)
            if (stabilization) {
                try {
                    videoBuilder.setVideoStabilizationEnabled(true)
                } catch (e: Exception) {
                }
            }
            val videoUseCase = videoBuilder.build()
            videoCapture = videoUseCase
            useCases += videoUseCase
            if (livePhoto) {
                val captureBuilder = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
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
                    if (mode == CaptureMode.SUPER_RES) {
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
        if (_state.value.autoCompositionEnabled) {
            val analysisBuilder = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setTargetRotation(rotation)
            val analysis = analysisBuilder.build()
            val analyzer = FaceAnalyzer { faces, _, _ -> onFaces(faces) }
            analysis.setAnalyzer(analysisExecutor, analyzer)
            faceAnalyzer = analyzer
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
                "bindToLifecycle failed: useCases=${useCases.size} effect=${effect != null} viewPort=${viewPort != null} mode=${_state.value.mode}",
                e
            )
            null
        }
    }

    private fun attachPreviewProvider(previewUseCase: Preview) {
        if (useProSurface()) {
            val surfaceView = proSurfaceView
            if (surfaceView == null) {
                previewView?.let { previewUseCase.setSurfaceProvider(it.surfaceProvider) }
                return
            }
            previewUseCase.setSurfaceProvider { request ->
                val holder = surfaceView.holder
                if (holder.surface.isValid) {
                    request.provideSurface(holder.surface, mainExecutor) { }
                } else {
                    pendingProRequest?.willNotProvideSurface()
                    pendingProRequest = request
                }
            }
        } else {
            val target = previewView
            if (target != null) {
                previewUseCase.setSurfaceProvider(target.surfaceProvider)
            } else {
                proSurfaceView?.let { surfaceView ->
                    previewUseCase.setSurfaceProvider { request ->
                        val holder = surfaceView.holder
                        if (holder.surface.isValid) {
                            request.provideSurface(holder.surface, mainExecutor) { }
                        } else {
                            pendingProRequest?.willNotProvideSurface()
                            pendingProRequest = request
                        }
                    }
                }
            }
        }
    }

    private fun requestViewPortRebind() {
        if (viewPortListenerAttached) return
        val view: View? = if (useProSurface()) proSurfaceView else previewView
        if (view == null) return
        viewPortListenerAttached = true
        view.addOnLayoutChangeListener(object : View.OnLayoutChangeListener {
            override fun onLayoutChange(
                v: View,
                left: Int,
                top: Int,
                right: Int,
                bottom: Int,
                oldLeft: Int,
                oldTop: Int,
                oldRight: Int,
                oldBottom: Int
            ) {
                if (v.width > 0 && v.height > 0) {
                    v.removeOnLayoutChangeListener(this)
                    viewPortListenerAttached = false
                    bind()
                }
            }
        })
    }

    private fun attachRotationListener() {
        if (released) return
        val view: View? = if (useProSurface()) proSurfaceView else previewView
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
                val aspect = width.toFloat() / height
                val lastAspect = if (lastHeight > 0) lastWidth.toFloat() / lastHeight else aspect
                val changed = lastWidth != width || lastHeight != height
                lastWidth = width
                lastHeight = height
                if (changed && kotlin.math.abs(aspect - lastAspect) > 0.1f && !released) {
                    bind()
                }
            }
        }
        view.addOnLayoutChangeListener(listener)
        rotationListener = listener
    }

    private fun buildViewPort(): ViewPort? {
        return null
    }

    private fun displayRotation(): Int {
        val view: View? = if (useProSurface()) proSurfaceView else previewView
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

    private fun flashModeValue(mode: FlashMode): Int = when (mode) {
        FlashMode.AUTO -> ImageCapture.FLASH_MODE_AUTO
        FlashMode.ON -> ImageCapture.FLASH_MODE_ON
        FlashMode.OFF -> ImageCapture.FLASH_MODE_OFF
    }

    private fun applyFlash() {
        imageCapture?.flashMode = flashModeValue(_state.value.flashMode)
        val torch = _state.value.flashMode == FlashMode.ON &&
            (_state.value.mode == CaptureMode.VIDEO || _state.value.mode == CaptureMode.PRO)
        try {
            camera?.cameraControl?.enableTorch(torch)
        } catch (e: Exception) {
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
        if (ratio < 1f) String.format(Locale.US, "%.1fx", ratio) else String.format(Locale.US, "%.0fx", ratio)

    private fun onFaces(faces: List<FaceBox>) {
        if (!_state.value.autoCompositionEnabled) {
            if (_state.value.compositionHint != null) {
                _state.update { it.copy(compositionHint = null) }
            }
            return
        }
        val hint = CompositionEngine.evaluate(faces)
        _state.update { it.copy(compositionHint = hint) }
    }

    override fun setMode(mode: CaptureMode) {
        if (_state.value.mode == mode) return
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
        }
        val active = _state.value.lensOptions
            .minByOrNull { kotlin.math.abs(it.zoomRatio - clamped) }?.id ?: _state.value.activeLensId
        _state.update { it.copy(zoomRatio = clamped, activeLensId = active) }
    }

    override fun setFlashMode(mode: FlashMode) {
        _state.update { it.copy(flashMode = mode) }
        applyFlash()
    }

    override fun setPreset(preset: FilterPreset) {
        renderState = renderState.copy(preset = preset)
        _state.update { it.copy(preset = preset) }
        scope.launch { settings.setPreset(preset.name) }
    }

    override fun setFilterIntensity(intensity: Float) {
        val value = intensity.coerceIn(0f, 1f)
        renderState = renderState.copy(intensity = value)
        _state.update { it.copy(filterIntensity = value) }
        scope.launch { settings.setFilterIntensity(value) }
    }

    override fun setStabilizationEnabled(enabled: Boolean) {
        if (_state.value.stabilizationEnabled == enabled) return
        _state.update { it.copy(stabilizationEnabled = enabled) }
        scope.launch { settings.setStabilizationEnabled(enabled) }
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
        _state.update { it.copy(autoCompositionEnabled = enabled, compositionHint = null) }
        scope.launch { settings.setAutoCompositionEnabled(enabled) }
        bind()
    }

    override fun setRecordingSpeed(speed: RecordingSpeed) {
        _state.update { it.copy(recordingSpeed = speed) }
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
        val view: View? = if (useProSurface()) proSurfaceView else previewView
        val width = view?.width ?: 0
        val height = view?.height ?: 0
        if (width <= 0 || height <= 0) return
        try {
            val factory = SurfaceOrientedMeteringPointFactory(width.toFloat(), height.toFloat())
            val point = factory.createPoint(x * width, y * height)
            val action = FocusMeteringAction.Builder(point).build()
            activeCamera.cameraControl.startFocusAndMetering(action)
        } catch (e: Exception) {
        }
    }

    override fun setManualFocus(position: Float) {
        val value = position.coerceIn(0f, 1f)
        _state.update { it.copy(manualFocusPosition = value, manualFocusActive = true) }
        try {
            camera?.cameraControl?.setLinearZoom(value)
        } catch (e: Exception) {
        }
    }

    override fun takePhoto() {
        Log.d(TAG, "takePhoto called mode=${_state.value.mode} busy=${_state.value.busy} livePhoto=${_state.value.livePhotoEnabled} capture=${imageCapture != null}")
        if (_state.value.livePhotoEnabled && _state.value.mode == CaptureMode.PHOTO) {
            startLivePhoto()
            return
        }
        val capture = imageCapture ?: return
        if (_state.value.busy) return
        playShutterSound()
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
        }
    }

    private fun savePhotoFile(file: File) {
        val preset = _state.value.preset
        val intensity = _state.value.filterIntensity
        val upscale = _state.value.superResEnabled || _state.value.mode == CaptureMode.SUPER_RES
        scope.launch {
            val uri = withContext(Dispatchers.IO) {
                photoProcessor.process(file, preset, intensity, upscale)
            }
            file.delete()
            if (uri == null) {
                _state.update { it.copy(busy = false) }
                setStatus(R.string.message_error_save, true)
            } else {
                _state.update { it.copy(busy = false, captureCount = it.captureCount + 1) }
                setStatus(R.string.message_photo_saved, false)
            }
        }
    }

    private fun startLivePhoto() {
        if (_state.value.busy || _state.value.recording || recording != null) return
        val capture = videoCapture ?: return
        val photo = imageCapture ?: return
        playShutterSound()
        _state.update { it.copy(busy = true) }
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
                        scope.launch {
                            val uri = withContext(Dispatchers.IO) {
                                photoProcessor.process(file, preset, intensity, upscale)
                            }
                            file.delete()
                            if (uri != null) {
                                _state.update { it.copy(captureCount = it.captureCount + 1) }
                            }
                        }
                    }

                    override fun onError(exception: ImageCaptureException) {
                        file.delete()
                    }
                }
            )
        } catch (e: Exception) {
            file.delete()
        }
        startRecording()
        livePhotoJob?.cancel()
        livePhotoJob = scope.launch {
            delay(LIVE_PHOTO_DURATION_MS)
            stopRecording()
        }
    }

    override fun startRecording() {
        if (_state.value.recording || recording != null) return
        val capture = videoCapture ?: return
        val name = "FH_" + System.currentTimeMillis() + ".mp4"
        var pending = try {
            buildPendingRecording(capture, name)
        } catch (e: Exception) {
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
            setStatus(R.string.message_error_record, true)
            return
        }
        recording = handle
        _state.update { it.copy(recording = true, recordingDurationMs = 0L) }
        setStatus(R.string.message_recording_started, false)
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
                ?: throw IllegalStateException("media store insert failed")
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
                _state.update { it.copy(recording = true, recordingDurationMs = 0L) }
            }

            is VideoRecordEvent.Status -> {
                val duration = event.recordingStats.recordedDurationNanos / 1_000_000L
                _state.update { it.copy(recordingDurationMs = duration) }
            }

            is VideoRecordEvent.Finalize -> {
                recording = null
                _state.update { it.copy(recording = false, recordingDurationMs = 0L) }
                if (event.error != VideoRecordEvent.Finalize.ERROR_NONE) {
                    event.outputResults.outputUri?.let { uri ->
                        try {
                            context.contentResolver.delete(uri, null, null)
                        } catch (e: Exception) {
                        }
                    }
                    setStatus(R.string.message_error_record, true)
                } else {
                    setStatus(R.string.message_video_saved, false)
                }
            }

            else -> {
            }
        }
    }

    private fun stopRecordingInternal() {
        val handle = recording
        recording = null
        if (handle != null) {
            try {
                handle.stop()
            } catch (e: Exception) {
            }
            try {
                handle.close()
            } catch (e: Exception) {
            }
        }
        if (_state.value.recording) {
            _state.update { it.copy(recording = false, recordingDurationMs = 0L) }
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
        _state.update { it.copy(lensOptions = emptyList(), activeLensId = "", compositionHint = null, busy = true) }
        bind()
    }

    override fun switchToPhotoFromPreview() {
        setMode(CaptureMode.PHOTO)
    }

    override fun release() {
        released = true
        stopRecordingInternal()
        livePhotoJob?.cancel()
        livePhotoJob = null
        detachAnalysis()
        stabilizer.stop()
        try {
            provider?.unbindAll()
        } catch (e: Exception) {
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
        pendingProRequest = null
        viewPortListenerAttached = false
        rotationListenerView?.removeOnLayoutChangeListener(rotationListener)
        rotationListenerView = null
        rotationListener = null
    }

    private fun detachAnalysis() {
        try {
            imageAnalysis?.clearAnalyzer()
        } catch (e: Exception) {
        }
        imageAnalysis = null
        faceAnalyzer?.release()
        faceAnalyzer = null
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
