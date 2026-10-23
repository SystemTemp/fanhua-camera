package com.fanhua.camera.camera.stabilize

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.view.Surface
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.tanh

class GyroStabilizer(
    context: Context,
    private val rotationProvider: () -> Int
) : SensorEventListener {

    private val manager = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val gyroscope = manager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
    private val rotationVector = manager.getDefaultSensor(Sensor.TYPE_ROTATION_VECTOR)

    private val bufferA = FloatArray(9).apply { setIdentity(this) }
    private val bufferB = FloatArray(9).apply { setIdentity(this) }
    private var writeBuffer = bufferA

    @Volatile
    private var readBuffer = bufferB

    private val sensorMatrix = FloatArray(9)
    private val remappedMatrix = FloatArray(9)
    private val orientation = FloatArray(3)

    private var rawX = 0f
    private var rawY = 0f
    private var rawZ = 0f
    private var smoothX = 0f
    private var smoothY = 0f
    private var smoothZ = 0f
    private var lastGyroTimestamp = 0L
    private var lastVectorTimestamp = 0L

    private var horizonMode = false
    private var rollInitialized = false
    private var rollSmooth = 0f
    private var rollApplied = 0f
    private var deviceRoll = 0f

    private var shiftX = 0f
    private var shiftY = 0f

    @Volatile
    var rollDegrees: Float = 0f
        private set

    @Volatile
    var horizonActive: Boolean = false
        private set

    private var active = false

    val available: Boolean
        get() = gyroscope != null

    val horizonAvailable: Boolean
        get() = rotationVector != null

    init {
        publishMatrix(0f, 0f, 0f)
    }

    fun setHorizonMode(enabled: Boolean) {
        if (horizonMode == enabled) return
        horizonMode = enabled
        resetMotion()
        if (active) {
            manager.unregisterListener(this)
            registerSensors()
        }
    }

    fun start() {
        if (active) return
        if (gyroscope == null && rotationVector == null) return
        active = true
        resetMotion()
        registerSensors()
    }

    fun stop() {
        if (!active) return
        active = false
        manager.unregisterListener(this)
        resetMotion()
    }

    fun matrix(): FloatArray = readBuffer

    fun cropScale(aspect: Float): Float {
        val a = aspect.coerceIn(0.3f, 3f)
        val c = abs(cos(rollApplied))
        val s = abs(sin(rollApplied))
        val rotationScale = max(c + s / a, c + s * a)
        return (rotationScale * CROP_SHIFT).coerceIn(1f, CROP_MAX)
    }

    private fun registerSensors() {
        gyroscope?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        if (horizonMode) {
            rotationVector?.let { manager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        when (event.sensor.type) {
            Sensor.TYPE_GYROSCOPE -> onGyro(event)
            Sensor.TYPE_ROTATION_VECTOR -> onRotationVector(event)
        }
    }

    private fun onGyro(event: SensorEvent) {
        val now = event.timestamp
        val dt = if (lastGyroTimestamp == 0L) 0f else (now - lastGyroTimestamp) * 1e-9f
        lastGyroTimestamp = now
        if (dt <= 0f || dt > 0.2f) return

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

        val alpha = 1f - exp(-dt / TAU_PAN)
        smoothX += (rawX - smoothX) * alpha
        smoothY += (rawY - smoothY) * alpha
        smoothZ += (rawZ - smoothZ) * alpha

        val corrX = rawX - smoothX
        val corrY = rawY - smoothY

        shiftX = (corrY * PAN_GAIN).coerceIn(-MAX_SHIFT, MAX_SHIFT)
        shiftY = (-corrX * PAN_GAIN).coerceIn(-MAX_SHIFT, MAX_SHIFT)

        publishMatrix(shiftX, shiftY, if (horizonMode) rollApplied else 0f)
    }

    private fun onRotationVector(event: SensorEvent) {
        if (!horizonMode) return
        val now = event.timestamp
        val dt = if (lastVectorTimestamp == 0L) 0f else (now - lastVectorTimestamp) * 1e-9f
        lastVectorTimestamp = now

        SensorManager.getRotationMatrixFromVector(sensorMatrix, event.values)
        remapSensorMatrix()
        SensorManager.getOrientation(remappedMatrix, orientation)

        val pitch = orientation[1]
        val roll = orientation[2]
        deviceRoll = roll
        if (abs(pitch) > PITCH_LIMIT) {
            rollDegrees = 0f
            horizonActive = false
            return
        }

        val target = -roll
        if (!rollInitialized) {
            rollSmooth = target
            rollInitialized = true
        } else if (dt > 0f && dt <= 0.2f) {
            var diff = target - rollSmooth
            while (diff > PI) diff -= TWO_PI
            while (diff < -PI) diff += TWO_PI
            val alpha = 1f - exp(-dt / TAU_ROLL)
            rollSmooth += diff * alpha
        }

        val magnitude = abs(rollSmooth)
        val effective = if (magnitude <= ROLL_DEADBAND) {
            0f
        } else {
            if (rollSmooth < 0f) -(magnitude - ROLL_DEADBAND) else (magnitude - ROLL_DEADBAND)
        }
        rollApplied = ROLL_LIMIT * tanh(effective / ROLL_LIMIT)
        rollDegrees = (deviceRoll + rollApplied) * 180f / PI
        horizonActive = true

        publishMatrix(shiftX, shiftY, rollApplied)
    }

    private fun remapSensorMatrix() {
        when (rotationProvider()) {
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

    private fun publishMatrix(tx: Float, ty: Float, angle: Float) {
        val target = writeBuffer
        val c = cos(angle)
        val s = sin(angle)
        target[0] = c
        target[1] = s
        target[2] = 0f
        target[3] = -s
        target[4] = c
        target[5] = 0f
        target[6] = tx
        target[7] = ty
        target[8] = 1f
        readBuffer = target
        writeBuffer = if (target === bufferA) bufferB else bufferA
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
    }

    private fun resetMotion() {
        rawX = 0f
        rawY = 0f
        rawZ = 0f
        smoothX = 0f
        smoothY = 0f
        smoothZ = 0f
        shiftX = 0f
        shiftY = 0f
        lastGyroTimestamp = 0L
        lastVectorTimestamp = 0L
        rollInitialized = false
        rollSmooth = 0f
        rollApplied = 0f
        deviceRoll = 0f
        rollDegrees = 0f
        horizonActive = false
        publishMatrix(0f, 0f, 0f)
    }

    private fun setIdentity(target: FloatArray) {
        target[0] = 1f
        target[1] = 0f
        target[2] = 0f
        target[3] = 0f
        target[4] = 1f
        target[5] = 0f
        target[6] = 0f
        target[7] = 0f
        target[8] = 1f
    }

    private companion object {
        const val PI = 3.1415927f
        const val TWO_PI = 6.2831855f
        const val TAU_PAN = 0.35f
        const val TAU_ROLL = 0.12f
        const val PAN_GAIN = 1.0f
        const val MAX_SHIFT = 0.055f
        const val ROLL_DEADBAND = 0.0044f
        const val ROLL_LIMIT = 0.1396f
        const val PITCH_LIMIT = 1.309f
        const val CROP_SHIFT = 1.13f
        const val CROP_MAX = 1.5f
    }
}
