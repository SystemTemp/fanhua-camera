package com.fanhua.camera

import android.os.Bundle
import android.view.KeyEvent
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fanhua.camera.camera.core.CameraXEngine
import com.fanhua.camera.camera.core.CaptureMode
import com.fanhua.camera.ui.FanhuaCameraApp
import com.fanhua.camera.ui.theme.FanhuaTheme

class MainActivity : AppCompatActivity() {

    private var engine: CameraXEngine? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val controller = CameraXEngine(this, this, lifecycleScope)
        engine = controller
        setContent {
            FanhuaTheme {
                FanhuaCameraApp(controller)
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
            val controller = engine
            if (controller != null) {
                when (controller.state.value.mode) {
                    CaptureMode.PHOTO, CaptureMode.SUPER_RES -> controller.takePhoto()
                    CaptureMode.VIDEO, CaptureMode.PRO ->
                        if (controller.state.value.recording) {
                            controller.stopRecording()
                        } else {
                            controller.startRecording()
                        }
                }
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onDestroy() {
        engine?.release()
        engine = null
        super.onDestroy()
    }
}
