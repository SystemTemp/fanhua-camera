package com.fanhua.camera.camera.stabilize

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.cos
import kotlin.math.sin

class GyroStabilizer(
    context: Context,
    private val rotationProvider: () -> Int
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVector = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val matrix = FloatArray(9)

    // 抖动防抖（高通平滑差分）
    private var rawX = 0f
    private var rawY = 0f
    private var rawZ = 0f
    private var smoothX = 0f
    private var smoothY = 0f
    private var smoothZ = 0f
    private var lastTimestamp = 0L

    // 地平线防抖（旋转矢量，无漂移）
    private var horizonMode = false
    private val sensorMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)
    private var horizonRollSmooth = 0f

    private var active = false

    val available: Boolean
        get() = gyroscope != null

    init {
        resetMatrix()
    }

    fun setHorizonMode(enabled: Boolean) {
        if (horizonMode == enabled) return
        horizonMode = enabled
        reset()
        if (active) {
            // 重新注册（旋转矢量仅在需要时启用）
            manager.unregisterListener(this)
            registerSensors()
        }
    }

    fun start() {
        if (active) return
        if (gyroscope == null && rotationVector == null) return
        reset()
        registerSensors()
        active = true
    }

    fun stop() {
        if (!active) return
        active = false
        manager.unregisterListener(this)
        reset()
    }

    private fun registerSensors() {
        gyroscope?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (horizonMode) {
            rotationVector?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    fun matrix(): FloatArray = matrix

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> onGyro(event)
            Sensor.TYPE_ROTATION_VECTOR -> onRotationVector(event)
        }
    }

    private fun onGyro(event: SensorEvent) {
        val now = event.timestamp
        val dt = if (lastTimestamp == 0L) 0f else (now - lastTimestamp) * 1e-9f
        lastTimestamp = now
        if (dt <= 0f || dt > 0.1f) return

        val gx = event.values[0]
        val gy = event.values[1]
        val gz = event.values[2]
        val sx: Float
        val sy: Float
        when (rotationProvider()) {
            Surface.ROTATION_90 -> {
                sx = gy
                sy = -gx
            }

            Surface.ROTATION_180 -> {
                sx = -gx
                sy = -gy
            }

            Surface.ROTATION_270 -> {
                sx = -gy
                sy = gx
            }

            else -> {
                sx = gx
                sy = gy
            }
        }

        rawX += sx * dt
        rawY += sy * dt
        rawZ += gz * dt
        smoothX += (rawX - smoothX) * (1f - SMOOTHING)
        smoothY += (rawY - smoothY) * (1f - SMOOTHING)
        smoothZ += (rawZ - smoothZ) * (1f - SMOOTHING)

        if (horizonMode) return

        val corrX = rawX - smoothX
        val corrY = rawY - smoothY
        val corrZ = rawZ - smoothZ

        val tx = (corrY * GAIN).coerceIn(-MAX_SHIFT, MAX_SHIFT)
        val ty = (-corrX * GAIN).coerceIn(-MAX_SHIFT, MAX_SHIFT)
        val angle = -corrZ * ROLL_GAIN
        val c = cos(angle)
        val s = sin(angle)

        matrix[0] = c
        matrix[1] = s
        matrix[2] = 0f
        matrix[3] = -s
        matrix[4] = c
        matrix[5] = 0f
        matrix[6] = tx
        matrix[7] = ty
        matrix[8] = 1f
    }

    private fun onRotationVector(event: SensorEvent) {
        if (!horizonMode) return
        SensorManager.getRotationMatrixFromVector(sensorMatrix, event.values)
        remapSensorMatrix()
        SensorManager.getOrientation(remappedMatrix, orientation)
        // roll：绕屏幕 z 轴的横滚角；反向旋转画面以保持地平线水平
        val target = -orientation[2]
        horizonRollSmooth += (target - horizonRollSmooth) * HORIZON_SMOOTH

        val angle = (horizonRollSmooth * HORIZON_GAIN).coerceIn(-HORIZON_MAX, HORIZON_MAX)
        val c = cos(angle)
        val s = sin(angle)
        matrix[0] = c
        matrix[1] = s
        matrix[2] = 0f
        matrix[3] = -s
        matrix[4] = c
        matrix[5] = 0f
        matrix[6] = 0f
        matrix[7] = 0f
        matrix[8] = 1f
    }

    private fun remapSensorMatrix() {
        val rotation = rotationProvider()
        when (rotation) {
            Surface.ROTATION_90 -> SensorManager.remapCoordinateSystem(
                sensorMatrix,
                SensorManager.AXIS_Y,
                SensorManager.AXIS_MINUS_X,
                remappedMatrix
            )

            Surface.ROTATION_180 -> SensorManager.remapCoordinateSystem(
                sensorMatrix,
                SensorManager.AXIS_MINUS_X,
                SensorManager.AXIS_MINUS_Y,
                remappedMatrix
            )

            Surface.ROTATION_270 -> SensorManager.remapCoordinateSystem(
                sensorMatrix,
                SensorManager.AXIS_MINUS_Y,
                SensorManager.AXIS_X,
                remappedMatrix
            )

            else -> sensorMatrix.copyInto(remappedMatrix)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun reset() {
        rawX = 0f
        rawY = 0f
        rawZ = 0f
        smoothX = 0f
        smoothY = 0f
        smoothZ = 0f
        lastTimestamp = 0L
        horizonRollSmooth = 0f
        resetMatrix()
    }

    private fun resetMatrix() {
        matrix[0] = 1f
        matrix[1] = 0f
        matrix[2] = 0f
        matrix[3] = 0f
        matrix[4] = 1f
        matrix[5] = 0f
        matrix[6] = 0f
        matrix[7] = 0f
        matrix[8] = 1f
    }

    private companion object {
        const val SMOOTHING = 0.90f
        const val GAIN = 0.90f
        const val ROLL_GAIN = 0.60f
        const val MAX_SHIFT = 0.075f
        const val HORIZON_SMOOTH = 0.10f
        const val HORIZON_GAIN = 0.85f
        const val HORIZON_MAX = 0.20f // 约 ±11.5°
    }
}