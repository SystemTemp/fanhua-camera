package com.fanhua.camera

import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.fanhua.camera.camera.core.CameraXEngine
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

    override fun onDestroy() {
        engine?.release()
        engine = null
        super.onDestroy()
    }
}
