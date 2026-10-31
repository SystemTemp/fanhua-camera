package com.fanhua.camera.camera.detect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import androidx.annotation.StringRes
import com.fanhua.camera.R
import java.io.File

/** 检测到的目标类别（COCO 80 类中的摄影常见主体）。 */
enum class SubjectKind(@StringRes val labelRes: Int) {
    PERSON(R.string.subject_person),
    ANIMAL(R.string.subject_animal),
    VEHICLE(R.string.subject_vehicle),
    FOOD(R.string.subject_food),
    PLANT(R.string.subject_plant),
    OBJECT(R.string.subject_object);

    /** 是否是「人像/宠物」这类需要避开中心、讲究三分构图的主体。 */
    val prefersRuleOfThirds: Boolean
        get() = this == PERSON || this == ANIMAL
}

/**
 * 归一化矩形（0..1）。
 *
 * 之所以不直接用 [android.graphics.RectF]，是因为它在 JVM 单元测试里是桩实现，
 * 构造与取值全部返回 0，导致构图算法无法被测试覆盖。这里用纯 Kotlin 数据类，
 * 只在需要与 Android 对接时再转换。
 */
data class NormRect(
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f
    val area: Float get() = width * height

    fun toRectF(): RectF = RectF(left, top, right, bottom)

    companion object {
        fun from(rect: RectF): NormRect = NormRect(rect.left, rect.top, rect.right, rect.bottom)
        fun ofCenter(cx: Float, cy: Float, w: Float, h: Float): NormRect =
            NormRect(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
    }
}

/** 一次检测的结果：主体框 + 类别 + 置信度。 */
data class Detection(
    val kind: SubjectKind,
    val label: String,
    val confidence: Float,
    /** 归一化坐标（0..1），相对输入图像。 */
    val rect: NormRect
) {
    val centerX: Float get() = rect.centerX
    val centerY: Float get() = rect.centerY
    val area: Float get() = rect.area
}

/** 模型下载源。多源按优先级排列，下载器会依次尝试。 */
data class ModelSource(
    val name: String,
    val url: String,
    /** 期望的文件大小（字节），用于校验下载完整性；0 表示不校验。 */
    val expectedBytes: Long = 0L
)

/**
 * 内置可选模型清单。
 *
 * 实测（2026-10）：
 * - Google CDN 在国内可达且速度约 10MB/s，因此作为首选；
 * - HuggingFace 与多数 GitHub 加速站在国内不可达，故不作为默认源，
 *   仅保留官方 GitHub Release 直链作为备用（部分网络环境可用）。
 */
object DetectorModels {

    /** EfficientDet-Lite0（int8，4.4MB，COCO 80 类，速度优先）。 */
    val EFFICIENTDET_LITE0 = ModelDefinition(
        id = "efficientdet_lite0",
        displayNameRes = R.string.model_efficientdet_lite0,
        sizeBytes = 4_602_795L,
        inputSize = 320,
        sources = listOf(
            ModelSource(
                name = "Google CDN",
                url = "https://storage.googleapis.com/mediapipe-models/object_detector/" +
                    "efficientdet_lite0/int8/1/efficientdet_lite0.tflite",
                expectedBytes = 4_602_795L
            )
        )
    )

    /** EfficientDet-Lite2（int8，7.5MB，精度优先）。 */
    val EFFICIENTDET_LITE2 = ModelDefinition(
        id = "efficientdet_lite2",
        displayNameRes = R.string.model_efficientdet_lite2,
        sizeBytes = 7_515_971L,
        inputSize = 448,
        sources = listOf(
            ModelSource(
                name = "Google CDN",
                url = "https://storage.googleapis.com/mediapipe-models/object_detector/" +
                    "efficientdet_lite2/int8/1/efficientdet_lite2.tflite",
                expectedBytes = 7_515_971L
            )
        )
    )

    /** SSDMobileNetV2（float32，11.3MB，兼容性最好）。 */
    val SSD_MOBILENET_V2 = ModelDefinition(
        id = "ssd_mobilenet_v2",
        displayNameRes = R.string.model_ssd_mobilenet,
        sizeBytes = 11_316_189L,
        inputSize = 320,
        sources = listOf(
            ModelSource(
                name = "Google CDN",
                url = "https://storage.googleapis.com/mediapipe-models/object_detector/" +
                    "ssd_mobilenet_v2/float32/1/ssd_mobilenet_v2.tflite",
                expectedBytes = 11_316_189L
            )
        )
    )

    /** 用户自行导入的模型（例如自己转换的 YOLOv8n TFLite）。 */
    const val CUSTOM_ID = "custom"

    val all: List<ModelDefinition> =
        listOf(EFFICIENTDET_LITE0, EFFICIENTDET_LITE2, SSD_MOBILENET_V2)

    val default: ModelDefinition = EFFICIENTDET_LITE0

    fun byId(id: String?): ModelDefinition? =
        all.firstOrNull { it.id == id }
}

/** 一个可选模型的完整定义。 */
data class ModelDefinition(
    val id: String,
    @StringRes val displayNameRes: Int,
    val sizeBytes: Long,
    val inputSize: Int,
    val sources: List<ModelSource>
) {
    val sizeMb: String
        get() = String.format(java.util.Locale.US, "%.1f MB", sizeBytes / 1024f / 1024f)
}

/** 模型在本地磁盘上的管理。 */
object DetectorStore {

    private const val DIR = "detectors"
    private const val CUSTOM_FILE = "custom.tflite"

    fun dir(context: Context): File =
        File(context.filesDir, DIR).apply { if (!exists()) mkdirs() }

    fun fileOf(context: Context, id: String): File = File(dir(context), "$id.tflite")

    fun isDownloaded(context: Context, id: String): Boolean {
        val file = fileOf(context, id)
        if (!file.exists() || file.length() < MIN_VALID_BYTES) return false
        // 校验 TFLite 魔数：偏移 4 处的 4 字节应为 "TFL3"
        return hasTfliteMagic(file)
    }

    /** 已下载的模型 id 列表。 */
    fun downloaded(context: Context): List<String> {
        val dir = dir(context)
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("tflite", true) }
            ?.filter { hasTfliteMagic(it) }
            ?.map { it.nameWithoutExtension }
            ?: emptyList()
    }

    fun sizeOf(context: Context, id: String): Long {
        val file = fileOf(context, id)
        return if (file.exists()) file.length() else 0L
    }

    /** 当前选中的模型 id（存在 SharedPreferences）。 */
    fun selected(context: Context): String? {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val id = prefs.getString(KEY_SELECTED, null) ?: return null
        return if (isDownloaded(context, id)) id else null
    }

    fun select(context: Context, id: String?) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .edit()
            .apply {
                if (id == null) remove(KEY_SELECTED) else putString(KEY_SELECTED, id)
            }
            .apply()
    }

    fun delete(context: Context, id: String): Boolean {
        val file = fileOf(context, id)
        val ok = if (file.exists()) file.delete() else true
        if (selected(context) == id) select(context, null)
        return ok
    }

    /** 自定义模型导入：校验魔数后落盘。 */
    fun importCustom(context: Context, bytes: ByteArray): Boolean {
        if (bytes.size < MIN_VALID_BYTES) return false
        if (!hasTfliteMagic(bytes)) return false
        val file = File(dir(context), CUSTOM_FILE)
        return try {
            file.outputStream().use { it.write(bytes) }
            true
        } catch (e: Exception) {
            false
        }
    }

    private fun hasTfliteMagic(file: File): Boolean {
        return try {
            if (file.length() < 8) return false
            file.inputStream().use { input ->
                val header = ByteArray(8)
                if (input.read(header) < 8) return false
                hasTfliteMagic(header)
            }
        } catch (e: Exception) {
            false
        }
    }

    private fun hasTfliteMagic(bytes: ByteArray): Boolean {
        if (bytes.size < 8) return false
        // TFLite flatbuffer: 前 4 字节为 root table offset，接着 4 字节是 "TFL3"
        return bytes[4] == 'T'.code.toByte() &&
            bytes[5] == 'F'.code.toByte() &&
            bytes[6] == 'L'.code.toByte() &&
            bytes[7] == '3'.code.toByte()
    }

    private const val PREFS = "fanhua_detector"
    private const val KEY_SELECTED = "selected_model"

    /** 小于该大小的文件一定不是有效模型。 */
    private const val MIN_VALID_BYTES = 100_000L
}

/** 图像预处理的工具函数，便于单元测试。 */
object DetectorImageUtils {

    /**
     * 把任意尺寸的位图等比缩放并居中裁剪到 [size]x[size]，用于模型输入。
     *
     * 用「先等比缩放到覆盖目标框，再居中裁剪」而不是直接拉伸，
     * 避免主体变形导致检测精度下降。
     */
    fun letterbox(source: Bitmap, size: Int): Bitmap {
        val scale = maxOf(size.toFloat() / source.width, size.toFloat() / source.height)
        val scaledW = (source.width * scale).toInt().coerceAtLeast(1)
        val scaledH = (source.height * scale).toInt().coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(source, scaledW, scaledH, true)
        val left = ((scaledW - size) / 2).coerceAtLeast(0)
        val top = ((scaledH - size) / 2).coerceAtLeast(0)
        val width = minOf(size, scaled.width - left)
        val height = minOf(size, scaled.height - top)
        val cropped = Bitmap.createBitmap(scaled, left, top, width, height)
        if (cropped.width == size && cropped.height == size) {
            if (scaled !== cropped && scaled !== source) scaled.recycle()
            return cropped
        }
        val out = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(out).apply {
            drawColor(android.graphics.Color.BLACK)
            drawBitmap(
                cropped,
                null,
                RectF(0f, 0f, size.toFloat(), size.toFloat()),
                Paint(Paint.FILTER_BITMAP_FLAG)
            )
        }
        if (cropped !== scaled && cropped !== source) cropped.recycle()
        if (scaled !== source) scaled.recycle()
        return out
    }

    /**
     * 把模型输出的归一化框转回原图坐标，并裁掉 letterbox 带来的黑边。
     *
     * 因为 [letterbox] 是先覆盖缩放再居中裁剪，坐标映射需要按裁剪区域反算。
     */
    fun mapToOriginal(
        rect: RectF,
        sourceWidth: Int,
        sourceHeight: Int,
        inputSize: Int
    ): RectF {
        if (sourceWidth <= 0 || sourceHeight <= 0 || inputSize <= 0) return RectF(rect)
        val scale = maxOf(
            inputSize.toFloat() / sourceWidth,
            inputSize.toFloat() / sourceHeight
        )
        val scaledW = sourceWidth * scale
        val scaledH = sourceHeight * scale
        val offsetX = (scaledW - inputSize) / 2f
        val offsetY = (scaledH - inputSize) / 2f
        val left = (rect.left * inputSize + offsetX) / scaledW
        val top = (rect.top * inputSize + offsetY) / scaledH
        val right = (rect.right * inputSize + offsetX) / scaledW
        val bottom = (rect.bottom * inputSize + offsetY) / scaledH
        return RectF(
            left.coerceIn(0f, 1f),
            top.coerceIn(0f, 1f),
            right.coerceIn(0f, 1f),
            bottom.coerceIn(0f, 1f)
        )
    }

    /** COCO 类别名 → 摄影语境下的主体分组。 */
    fun kindOf(label: String): SubjectKind {
        val name = label.lowercase(java.util.Locale.US)
        return when {
            name == "person" -> SubjectKind.PERSON
            name in ANIMALS -> SubjectKind.ANIMAL
            name in VEHICLES -> SubjectKind.VEHICLE
            name in FOODS -> SubjectKind.FOOD
            name in PLANTS -> SubjectKind.PLANT
            else -> SubjectKind.OBJECT
        }
    }

    private val ANIMALS = setOf(
        "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
        "giraffe", "teddy bear"
    )
    private val VEHICLES = setOf(
        "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck", "boat"
    )
    private val FOODS = setOf(
        "banana", "apple", "sandwich", "orange", "broccoli", "carrot", "hot dog",
        "pizza", "donut", "cake", "bowl", "cup", "wine glass", "bottle", "fork",
        "knife", "spoon"
    )
    private val PLANTS = setOf("potted plant", "vase", "flower")
}
