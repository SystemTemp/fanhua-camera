package com.fanhua.camera.camera.core

import android.content.Context
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.util.Size

class CapabilityProbe(private val context: Context) {

    fun probe(facing: Int): CapabilityReport {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var characteristics: CameraCharacteristics? = null
        for (id in manager.cameraIdList) {
            val item = manager.getCameraCharacteristics(id)
            if (item.get(CameraCharacteristics.LENS_FACING) == facing) {
                characteristics = item
                break
            }
        }
        val camera = characteristics ?: return CapabilityReport(notes = listOf("no camera for facing $facing"))
        val map = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val hevc = videoCapabilities("video/hevc")
        val avc = videoCapabilities("video/avc")

        var maxSize = Size(0, 0)
        var maxFps = 0
        if (map != null) {
            val sizes = map.getOutputSizes(MediaRecorder::class.java) ?: emptyArray()
            for (size in sizes) {
                if (size.width.toLong() * size.height > maxSize.width.toLong() * maxSize.height) {
                    maxSize = size
                }
            }
            if (maxSize.width > 0) {
                val duration = map.getOutputMinFrameDuration(MediaRecorder::class.java, maxSize)
                if (duration > 0) {
                    maxFps = (1_000_000_000L / duration).toInt()
                }
            }
        }

        val highSpeedSizes = map?.highSpeedVideoSizes ?: emptyArray()
        val highSpeedProfiles = mutableListOf<String>()
        var highSpeedFps = 0
        for (size in highSpeedSizes) {
            val ranges = map?.getHighSpeedVideoFpsRangesFor(size) ?: continue
            for (range in ranges) {
                highSpeedProfiles += "${size.width}x${size.height}@${range.upper}"
                if (range.upper > highSpeedFps) highSpeedFps = range.upper
            }
        }

        val stabilization = camera.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES)
        val stabilizationNames = mutableListOf<String>()
        if (stabilization != null) {
            if (stabilization.contains(0)) stabilizationNames += "OFF"
            if (stabilization.contains(1)) stabilizationNames += "ON"
            if (stabilization.contains(2)) stabilizationNames += "PREVIEW"
        }
        val optical = camera.get(CameraCharacteristics.LENS_INFO_AVAILABLE_OPTICAL_STABILIZATION)
        if (optical != null && optical.contains(1)) stabilizationNames += "OIS"

        val fourK30 = supports(hevc, 3840, 2160, 30) || supports(avc, 3840, 2160, 30)
        val fourK60 = supports(hevc, 3840, 2160, 60) || supports(avc, 3840, 2160, 60)
        val fourK120 = supports(hevc, 3840, 2160, 120) || supports(avc, 3840, 2160, 120)

        val proSpecs = buildSpecs(hevc ?: avc)
        val superResFps = when {
            fourK60 -> 60
            fourK30 -> 30
            else -> 0
        }

        val notes = mutableListOf<String>()
        if (hevc != null) {
            notes += "HEVC encoder detected"
        } else {
            notes += "HEVC encoder missing, AVC only"
        }
        if (!fourK120 && highSpeedFps >= 120) {
            notes += "SoC encoder cannot reach 4K120, slow motion limited to $highSpeedFps FPS capture"
        }
        if (highSpeedProfiles.isEmpty()) {
            notes += "No constrained high speed capture profile"
        }

        return CapabilityReport(
            maxVideoWidth = maxSize.width,
            maxVideoHeight = maxSize.height,
            maxVideoFps = if (maxFps > 240) 30 else maxFps,
            hevcSupported = hevc != null,
            fourK30Encode = fourK30,
            fourK60Encode = fourK60,
            fourK120Encode = fourK120,
            highSpeedSupported = highSpeedProfiles.isNotEmpty(),
            highSpeedProfiles = highSpeedProfiles,
            stabilizationModes = stabilizationNames,
            superResTargetWidth = if (fourK30) 3840 else 0,
            superResTargetHeight = if (fourK30) 2160 else 0,
            superResFps = superResFps,
            proSpecs = proSpecs,
            notes = notes
        )
    }

    private fun buildSpecs(caps: android.media.MediaCodecInfo.VideoCapabilities?): List<OutputSpec> {
        val result = mutableListOf<OutputSpec>()
        if (caps == null) return result
        val candidates = listOf(
            Triple(3840, 2160, intArrayOf(30, 60, 120)),
            Triple(1920, 1080, intArrayOf(30, 60, 120, 240)),
            Triple(1280, 720, intArrayOf(30, 60, 120, 240))
        )
        for ((width, height, rates) in candidates) {
            for (rate in rates) {
                if (supports(caps, width, height, rate)) {
                    result += OutputSpec(width, height, rate, false)
                }
            }
        }
        return result
    }

    private fun videoCapabilities(mime: String): android.media.MediaCodecInfo.VideoCapabilities? {
        val list = MediaCodecList(MediaCodecList.ALL_CODECS)
        for (info in list.codecInfos) {
            if (!info.isEncoder) continue
            for (type in info.supportedTypes) {
                if (type.equals(mime, ignoreCase = true)) {
                    return try {
                        info.getCapabilitiesForType(type).videoCapabilities
                    } catch (e: Exception) {
                        null
                    }
                }
            }
        }
        return null
    }

    private fun supports(
        caps: android.media.MediaCodecInfo.VideoCapabilities?,
        width: Int,
        height: Int,
        fps: Int
    ): Boolean {
        if (caps == null) return false
        return try {
            caps.areSizeAndRateSupported(width, height, fps.toDouble())
        } catch (e: Exception) {
            false
        }
    }
}