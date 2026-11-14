package com.fanhua.camera.camera.core

import android.content.Context
import android.graphics.ImageFormat
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.util.Range
import android.util.Size

/**
 * 相机能力探测。
 *
 * 通过 Camera2 的 [CameraCharacteristics] 与 [MediaCodecList] 交叉验证，
 * 得出「这台设备真正能做到什么」，避免 UI 上给出设备无法执行的选项。
 */
class CapabilityProbe(private val context: Context) {

    fun probe(facing: Int): CapabilityReport {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        var characteristics: CameraCharacteristics? = null
        for (id in manager.cameraIdList) {
            val item = try {
                manager.getCameraCharacteristics(id)
            } catch (e: Exception) {
                continue
            }
            if (item.get(CameraCharacteristics.LENS_FACING) == facing) {
                characteristics = item
                break
            }
        }
        val camera = characteristics
            ?: return CapabilityReport(notes = listOf("未找到该朝向的摄像头"))
        val map = camera.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val hevc = videoCapabilities("video/hevc")
        val avc = videoCapabilities("video/avc")

        var maxSize = Size(0, 0)
        var maxFps = 0
        var maxPhotoSize = Size(0, 0)
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
            val photoSizes = map.getOutputSizes(ImageFormat.JPEG) ?: emptyArray()
            for (size in photoSizes) {
                if (size.width.toLong() * size.height > maxPhotoSize.width.toLong() * maxPhotoSize.height) {
                    maxPhotoSize = size
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
            notes += "已检测到 HEVC 编码器"
        } else {
            notes += "缺少 HEVC 编码器，仅支持 AVC"
        }
        if (!fourK120 && highSpeedFps >= 120) {
            notes += "SoC 编码器无法达到 4K120，慢动作上限为 $highSpeedFps FPS 采集"
        }
        if (highSpeedProfiles.isEmpty()) {
            notes += "无受约束高速采集档位"
        }

        return CapabilityReport(
            maxVideoWidth = maxSize.width,
            maxVideoHeight = maxSize.height,
            maxVideoFps = if (maxFps > 240) 30 else maxFps,
            maxPhotoWidth = maxPhotoSize.width,
            maxPhotoHeight = maxPhotoSize.height,
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

    /** 该朝向的摄像头是否带闪光灯单元。 */
    fun hasFlash(facing: Int): Boolean {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in manager.cameraIdList) {
            val item = try {
                manager.getCameraCharacteristics(id)
            } catch (e: Exception) {
                continue
            }
            if (item.get(CameraCharacteristics.LENS_FACING) == facing) {
                return item.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true
            }
        }
        return false
    }

    /** 曝光补偿的可用区间。 */
    fun exposureRange(facing: Int): Range<Int>? {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in manager.cameraIdList) {
            val item = try {
                manager.getCameraCharacteristics(id)
            } catch (e: Exception) {
                continue
            }
            if (item.get(CameraCharacteristics.LENS_FACING) == facing) {
                return item.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE)
            }
        }
        return null
    }

    /** 曝光补偿每档的步长（EV）。 */
    fun exposureStep(facing: Int): Float {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        for (id in manager.cameraIdList) {
            val item = try {
                manager.getCameraCharacteristics(id)
            } catch (e: Exception) {
                continue
            }
            if (item.get(CameraCharacteristics.LENS_FACING) == facing) {
                val rational = item.get(CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP)
                if (rational != null && rational.denominator != 0) {
                    return rational.numerator.toFloat() / rational.denominator.toFloat()
                }
            }
        }
        return 1f / 6f
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

    /**
     * 探测该朝向所有可用镜头（含超广角 / 长焦），用于生成 0.6x / 1x / 3x 这类档位。
     *
     * CameraX 的 `zoomState.minZoomRatio` 通常只反映主摄逻辑范围（>=1.0），
     * 拿不到 0.6x 这类超广角倍率，所以这里直接读 Camera2 的
     * [CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS]，
     * 用「相对主摄的等效倍率」反推每颗物理镜头的档位。
     *
     * 做法：
     * 1. 枚举该朝向所有逻辑摄像头，记录焦距与是否为逻辑多摄；
     * 2. 取焦距中位数附近的那颗作为主摄基准（通常就是 1x）；
     * 3. 其余镜头按 focal / mainFocal 换算倍率，过滤掉过于接近（<8%）的重复档。
     *
     * 返回值为**由短焦到长焦排序**的倍率列表；设备只有单摄时返回 `[1f]`。
     */
    fun probeLensZoomRatios(facing: Int): List<Float> {
        val manager = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager
        data class Lens(val focal: Float, val logical: Boolean)

        val lenses = mutableListOf<Lens>()
        for (id in manager.cameraIdList) {
            val ch = try {
                manager.getCameraCharacteristics(id)
            } catch (e: Exception) {
                continue
            }
            if (ch.get(CameraCharacteristics.LENS_FACING) != facing) continue
            val focals = ch.get(CameraCharacteristics.LENS_INFO_AVAILABLE_FOCAL_LENGTHS)
                ?: continue
            if (focals.isEmpty()) continue
            // 逻辑多摄的焦距数组往往含多颗镜头，取最短的代表它的最广视角
            val focal = focals.minOrNull() ?: continue
            val caps = ch.get(CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES)
            val logical = caps?.contains(
                CameraCharacteristics.REQUEST_AVAILABLE_CAPABILITIES_LOGICAL_MULTI_CAMERA
            ) == true
            lenses += Lens(focal, logical)
        }
        if (lenses.isEmpty()) return listOf(1f)

        // 主摄基准：优先逻辑多摄里焦距居中的那颗，否则取最长焦距（主摄一般比超广角长）
        val sortedFocals = lenses.map { it.focal }.sorted()
        val mainFocal = sortedFocals.getOrNull(sortedFocals.size / 2)
            ?: return listOf(1f)
        if (mainFocal <= 0f) return listOf(1f)

        val ratios = linkedSetOf<Float>()
        ratios += 1f
        for (lens in lenses) {
            val r = lens.focal / mainFocal
            if (r.isFinite() && r > 0.1f && r < 20f) {
                // 与已有档位相差 8% 以内视为同一颗，避免 UI 出现 0.9x/1.1x 这种噪声
                val dup = ratios.any { kotlin.math.abs(it - r) / r < 0.08f }
                if (!dup) ratios += r
            }
        }
        return ratios.sorted().map { normalizeZoom(it) }
    }

    /** 把探测到的物理倍率对齐到相机 App 惯用的档位标签。 */
    private fun normalizeZoom(raw: Float): Float = when {
        raw < 0.75f -> 0.5f      // 超广角统一显示 0.5x / 0.6x 区间
        raw < 0.95f -> 0.6f
        raw < 1.6f -> 1f
        raw < 2.4f -> 2f
        raw < 3.6f -> 3f
        raw < 5.5f -> 5f
        else -> raw
    }
}
