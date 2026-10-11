package com.fanhua.camera.ui.common

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

private fun vector(name: String, build: ImageVector.Builder.() -> Unit): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).apply(build).build()

private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    moveTo(cx + radius, cy)
    arcToRelative(radius, radius, 0f, false, true, -2f * radius, 0f)
    arcToRelative(radius, radius, 0f, false, true, 2f * radius, 0f)
    close()
}

object FanhuaIcons {

    val Shutter: ImageVector = vector("Shutter") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) { circle(12f, 12f, 8.2f) }
        path(fill = SolidColor(Color.Black)) { circle(12f, 12f, 2.3f) }
    }

    val Aperture: ImageVector = vector("Aperture") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            circle(12f, 12f, 8.3f)
            moveTo(17.2f, 12f)
            lineTo(14.6f, 16.5f)
            lineTo(9.4f, 16.5f)
            lineTo(6.8f, 12f)
            lineTo(9.4f, 7.5f)
            lineTo(14.6f, 7.5f)
            close()
            moveTo(12f, 3.7f)
            lineTo(12f, 7.5f)
            moveTo(20.3f, 12f)
            lineTo(16.5f, 12f)
            moveTo(12f, 20.3f)
            lineTo(12f, 16.5f)
            moveTo(3.7f, 12f)
            lineTo(7.5f, 12f)
        }
    }

    val FlashAuto: ImageVector = vector("FlashAuto") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13f, 2.2f)
            lineTo(5.8f, 12.8f)
            lineTo(11f, 12.8f)
            lineTo(10f, 21.8f)
            lineTo(18.2f, 10.6f)
            lineTo(13f, 10.6f)
            close()
        }
    }

    val FlashOn: ImageVector = vector("FlashOn") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(13f, 2.2f)
            lineTo(5.8f, 12.8f)
            lineTo(11f, 12.8f)
            lineTo(10f, 21.8f)
            lineTo(18.2f, 10.6f)
            lineTo(13f, 10.6f)
            close()
        }
    }

    val FlashOff: ImageVector = vector("FlashOff") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(13.2f, 4.4f)
            lineTo(7.4f, 12.8f)
            lineTo(11.4f, 12.8f)
            lineTo(10.6f, 20.2f)
            lineTo(17.4f, 10.6f)
            lineTo(13.4f, 10.6f)
        }
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(4f, 4f)
            lineTo(20f, 20f)
        }
    }

    val Settings: ImageVector = vector("Settings") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            circle(12f, 12f, 3.2f)
            circle(12f, 12f, 7f)
            moveTo(18.8f, 12f)
            lineTo(20.9f, 12f)
            moveTo(16.8f, 16.8f)
            lineTo(18.2f, 18.2f)
            moveTo(12f, 18.8f)
            lineTo(12f, 20.9f)
            moveTo(7.2f, 16.8f)
            lineTo(5.8f, 18.2f)
            moveTo(5.2f, 12f)
            lineTo(3.1f, 12f)
            moveTo(7.2f, 7.2f)
            lineTo(5.8f, 5.8f)
            moveTo(12f, 5.2f)
            lineTo(12f, 3.1f)
            moveTo(16.8f, 7.2f)
            lineTo(18.2f, 5.8f)
        }
    }

    val SwitchCamera: ImageVector = vector("SwitchCamera") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 9f)
            lineTo(18f, 9f)
            moveTo(15f, 6f)
            lineTo(18f, 9f)
            lineTo(15f, 12f)
            moveTo(20f, 15f)
            lineTo(6f, 15f)
            moveTo(9f, 12f)
            lineTo(6f, 15f)
            lineTo(9f, 18f)
        }
    }

    val Close: ImageVector = vector("Close") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round
        ) {
            moveTo(6f, 6f)
            lineTo(18f, 18f)
            moveTo(18f, 6f)
            lineTo(6f, 18f)
        }
    }

    val Check: ImageVector = vector("Check") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.9f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 12.6f)
            lineTo(10f, 17.6f)
            lineTo(19f, 6.6f)
        }
    }

    val Grid: ImageVector = vector("Grid") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4f, 4f)
            lineTo(20f, 4f)
            lineTo(20f, 20f)
            lineTo(4f, 20f)
            close()
            moveTo(9.33f, 4f)
            lineTo(9.33f, 20f)
            moveTo(14.67f, 4f)
            lineTo(14.67f, 20f)
            moveTo(4f, 9.33f)
            lineTo(20f, 9.33f)
            moveTo(4f, 14.67f)
            lineTo(20f, 14.67f)
        }
    }

    val ArrowLeft: ImageVector = vector("ArrowLeft") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(19f, 12f)
            lineTo(5f, 12f)
            moveTo(11f, 6f)
            lineTo(5f, 12f)
            lineTo(11f, 18f)
        }
    }

    val ArrowRight: ImageVector = vector("ArrowRight") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(5f, 12f)
            lineTo(19f, 12f)
            moveTo(13f, 6f)
            lineTo(19f, 12f)
            lineTo(13f, 18f)
        }
    }

    val ArrowUp: ImageVector = vector("ArrowUp") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 19f)
            lineTo(12f, 5f)
            moveTo(6f, 11f)
            lineTo(12f, 5f)
            lineTo(18f, 11f)
        }
    }

    val ArrowDown: ImageVector = vector("ArrowDown") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.8f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(12f, 5f)
            lineTo(12f, 19f)
            moveTo(6f, 13f)
            lineTo(12f, 19f)
            lineTo(18f, 13f)
        }
    }

    val Play: ImageVector = vector("Play") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(8f, 5f)
            lineTo(19f, 12f)
            lineTo(8f, 19f)
            close()
        }
    }

    val Record: ImageVector = vector("Record") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f
        ) { circle(12f, 12f, 8.5f) }
        path(fill = SolidColor(Color.Black)) { circle(12f, 12f, 4.6f) }
    }

    val Timer: ImageVector = vector("Timer") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            circle(12f, 13.6f, 7.4f)
            moveTo(12f, 13.6f)
            lineTo(12f, 9.8f)
            moveTo(12f, 13.6f)
            lineTo(15.2f, 13.6f)
            moveTo(9.4f, 3f)
            lineTo(14.6f, 3f)
            moveTo(12f, 3f)
            lineTo(12f, 6.2f)
        }
    }

    val LivePhoto: ImageVector = vector("LivePhoto") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            circle(9f, 12f, 5.8f)
            moveTo(11.8f, 14.8f)
            lineTo(15.8f, 18.2f)
            moveTo(17f, 11.6f)
            lineTo(20.8f, 11.6f)
            moveTo(13.4f, 7.6f)
            lineTo(16f, 4.9f)
        }
        path(fill = SolidColor(Color.Black)) { circle(9f, 12f, 1.5f) }
    }

    val Feedback: ImageVector = vector("Feedback") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            circle(9f, 13f, 4.8f)
            circle(14.8f, 9f, 1.9f)
            moveTo(15.8f, 7.4f)
            lineTo(17.8f, 5.4f)
            moveTo(14f, 7f)
            lineTo(13.8f, 4.8f)
            moveTo(5.6f, 10.6f)
            lineTo(3.8f, 9f)
            moveTo(5.6f, 13f)
            lineTo(3.6f, 13f)
            moveTo(5.6f, 15.4f)
            lineTo(3.8f, 17f)
            moveTo(12.4f, 10.6f)
            lineTo(14.2f, 9f)
            moveTo(12.4f, 13f)
            lineTo(14.4f, 13f)
            moveTo(12.4f, 15.4f)
            lineTo(14.2f, 17f)
        }
    }

    val Gallery: ImageVector = vector("Gallery") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(4.5f, 6f)
            lineTo(19.5f, 6f)
            lineTo(19.5f, 19.5f)
            lineTo(4.5f, 19.5f)
            close()
            moveTo(5.5f, 17.6f)
            lineTo(9.6f, 12.6f)
            lineTo(12.8f, 16f)
            lineTo(15.6f, 12.6f)
            lineTo(18.6f, 16.6f)
        }
        path(fill = SolidColor(Color.Black)) { circle(9f, 9.6f, 1.5f) }
    }

    val Language: ImageVector = vector("Language") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            circle(12f, 12f, 8.2f)
            moveTo(3.8f, 12f)
            lineTo(20.2f, 12f)
            moveTo(12f, 3.8f)
            arcToRelative(4.2f, 8.2f, 0f, false, true, 0f, 16.4f)
            arcToRelative(4.2f, 8.2f, 0f, false, true, 0f, -16.4f)
            close()
        }
    }

    val Sparkle: ImageVector = vector("Sparkle") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(11f, 3.4f)
            curveTo(11.8f, 9.4f, 13.6f, 11.2f, 19.6f, 12f)
            curveTo(13.6f, 12.8f, 11.8f, 14.6f, 11f, 20.6f)
            curveTo(10.2f, 14.6f, 8.4f, 12.8f, 2.4f, 12f)
            curveTo(8.4f, 11.2f, 10.2f, 9.4f, 11f, 3.4f)
            close()
            moveTo(19f, 3.2f)
            curveTo(19.3f, 5.2f, 19.9f, 5.8f, 21.9f, 6.1f)
            curveTo(19.9f, 6.4f, 19.3f, 7f, 19f, 9f)
            curveTo(18.7f, 7f, 18.1f, 6.4f, 16.1f, 6.1f)
            curveTo(18.1f, 5.8f, 18.7f, 5.2f, 19f, 3.2f)
            close()
        }
    }

    val Info: ImageVector = vector("Info") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.6f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            circle(12f, 12f, 8.4f)
            moveTo(12f, 11.2f)
            lineTo(12f, 16.6f)
        }
        path(fill = SolidColor(Color.Black)) { circle(12f, 8.1f, 1.1f) }
    }

    val Refresh: ImageVector = vector("Refresh") {
        path(
            stroke = SolidColor(Color.Black),
            strokeLineWidth = 1.7f,
            strokeLineCap = StrokeCap.Round,
            strokeLineJoin = StrokeJoin.Round
        ) {
            moveTo(17.7f, 6.3f)
            arcToRelative(8f, 8f, 0f, true, true, -5.7f, -2.3f)
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(11.6f, 1.4f)
            lineTo(15.9f, 4.3f)
            lineTo(11.1f, 6.6f)
            close()
        }
    }
}