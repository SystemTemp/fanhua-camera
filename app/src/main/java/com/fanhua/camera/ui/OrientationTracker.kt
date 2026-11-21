package com.fanhua.camera.ui

import android.content.Context
import android.hardware.SensorManager
import android.view.OrientationEventListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 物理朝向追踪：界面锁定竖屏后，布局不再随设备旋转，
 * 但拍摄时横握手机是自然姿势——此时只把界面上的文字转成
 * 与握持方向一致，其余控件位置保持不动。
 *
 * [OrientationEventListener] 上报的是设备与自然竖直方向的夹角，
 * 按 90° 量化成 0 / 90 / 180 / 270：
 *  - 0   竖握（顶部朝上）
 *  - 90  顺时针横握（顶部朝右），文字转 -90°
 *  - 270 逆时针横握（顶部朝左），文字转 +90°
 *  - 180 倒握，文字转 180°
 */
class OrientationTracker(context: Context) {

    private val _angle = MutableStateFlow(0)
    val angle: StateFlow<Int> = _angle

    private val listener = object : OrientationEventListener(
        context,
        SensorManager.SENSOR_DELAY_UI
    ) {
        override fun onOrientationChanged(orientation: Int) {
            if (orientation == ORIENTATION_UNKNOWN) return
            val quantized = when {
                orientation < 45 || orientation >= 315 -> 0
                orientation < 135 -> 90
                orientation < 225 -> 180
                else -> 270
            }
            if (quantized != _angle.value) _angle.value = quantized
        }
    }

    fun start() {
        listener.enable()
    }

    fun stop() {
        listener.disable()
    }
}
