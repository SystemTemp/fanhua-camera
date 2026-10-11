package com.fanhua.camera.camera.capture

import androidx.camera.core.CameraEffect
import androidx.camera.core.SurfaceProcessor
import androidx.core.util.Consumer
import java.util.concurrent.Executor

class FilterCameraEffect(
    targets: Int,
    executor: Executor,
    processor: SurfaceProcessor,
    errorListener: Consumer<Throwable>
) : CameraEffect(
    targets,
    CameraEffect.TRANSFORMATION_CAMERA_AND_SURFACE_ROTATION,
    CameraEffect.OUTPUT_OPTION_ONE_FOR_EACH_TARGET,
    executor,
    processor,
    errorListener
)