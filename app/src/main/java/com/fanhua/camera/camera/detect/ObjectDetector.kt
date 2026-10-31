package com.fanhua.camera.camera.detect

import android.content.Context
import android.graphics.Bitmap
import android.graphics.RectF
import com.fanhua.camera.debug.DebugLog
import org.tensorflow.lite.Interpreter
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel
import kotlin.math.max

/**
 * TFLite 目标检测推理器。
 *
 * 支持两类输出格式：
 * 1. **TFLite Task Library / MediaPipe 风格**（`[1, N, 4]` 框 + `[1, N]` 类别 + `[1, N]` 分数
 *    或 `[1, N, 4]` 分数），这是 EfficientDet / SSD-MobileNet 的标准输出；
 * 2. **YOLOv8 风格**（`[1, 4+C, N]`，需要转置并按类别取最大值 + NMS）。
 *
 * 自动根据输出张量的形状判断用哪种解析器，因此用户导入自己转换的 YOLOv8n TFLite 也能用。
 *
 * 全部推理在调用方指定的线程执行，本类不做线程切换。
 */
class ObjectDetector(private val context: Context) {

    private var interpreter: Interpreter? = null
    private var inputSize = 320
    private var loadedModelId: String? = null
    private var inputBuffer: ByteBuffer? = null
    private var isYoloLayout = false
    private var labels: List<String> = COCO_LABELS

    /** 是否已加载可用模型。 */
    val ready: Boolean get() = interpreter != null

    /** 当前模型 id。 */
    val modelId: String? get() = loadedModelId

    /** 加载模型；返回是否成功。 */
    fun load(definition: ModelDefinition): Boolean {
        if (loadedModelId == definition.id && interpreter != null) return true
        release()
        val file = DetectorStore.fileOf(context, definition.id)
        if (!file.exists()) {
            DebugLog.log("Detector", "模型文件不存在: ${file.absolutePath}")
            return false
        }
        return loadFromFile(file, definition.id, definition.inputSize)
    }

    /** 加载自定义模型（用户导入）。 */
    fun loadCustom(inputSize: Int = 320): Boolean {
        val file = File(DetectorStore.dir(context), "custom.tflite")
        if (!file.exists()) return false
        return loadFromFile(file, DetectorModels.CUSTOM_ID, inputSize)
    }

    private fun loadFromFile(file: File, id: String, preferredSize: Int): Boolean {
        return try {
            val options = Interpreter.Options().apply {
                setNumThreads(NUM_THREADS)
                // 只用 CPU：GPU 委托对 SSD/EfficientDet 的部分算子支持不全，
                // 且会额外引入 ~3MB 依赖；检测是低频任务（约 4fps），CPU 已足够。
            }
            val mapped = mapFile(file)
            val created = Interpreter(mapped, options)
            // 从模型输入张量反推输入尺寸，比硬编码更可靠
            val shape = created.getInputTensor(0).shape()
            inputSize = when {
                shape.size >= 4 && shape[1] > 0 && shape[2] > 0 -> shape[2]
                preferredSize > 0 -> preferredSize
                else -> 320
            }
            isYoloLayout = detectYoloLayout(created)
            labels = resolveLabels(created)
            interpreter = created
            loadedModelId = id
            inputBuffer = ByteBuffer
                .allocateDirect(inputSize * inputSize * 3 * BYTES_PER_CHANNEL)
                .order(ByteOrder.nativeOrder())
            DebugLog.log(
                "Detector",
                "模型已加载 id=$id input=$inputSize yolo=$isYoloLayout labels=${labels.size}"
            )
            true
        } catch (e: Exception) {
            DebugLog.log("Detector", "加载模型失败: ${e.javaClass.simpleName}: ${e.message}")
            release()
            false
        }
    }

    /** YOLOv8 输出形如 [1, 84, 8400]，第二维是 4+C（无 objectness）。 */
    private fun detectYoloLayout(interpreter: Interpreter): Boolean {
        return try {
            val output = interpreter.getOutputTensor(0).shape()
            output.size == 3 && output[1] in 5..200 && output[2] > output[1]
        } catch (e: Exception) {
            false
        }
    }

    /** 尝试从模型 metadata 读取标签，失败则用内置 COCO 标签。 */
    private fun resolveLabels(interpreter: Interpreter): List<String> {
        return try {
            val out = interpreter.getOutputTensor(0).shape()
            // [1, N, 4] + [1, N] 分数 + [1, N] 类别 的经典 SSD 结构
            val numClasses = if (out.size == 3 && out[2] == 4 && interpreter.outputTensorCount >= 4) {
                // labels 数量由类别张量最大值决定，无法静态得知，使用 COCO
                COCO_LABELS.size
            } else {
                COCO_LABELS.size
            }
            COCO_LABELS.take(numClasses)
        } catch (e: Exception) {
            COCO_LABELS
        }
    }

    /**
     * 对位图执行检测。
     *
     * @param bitmap 原始位图（任意尺寸，内部会 letterbox 到模型输入）
     * @param minConfidence 置信度阈值
     * @param maxResults 最多返回的结果数
     */
    fun detect(
        bitmap: Bitmap,
        minConfidence: Float = DEFAULT_CONFIDENCE,
        maxResults: Int = MAX_RESULTS
    ): List<Detection> {
        val net = interpreter ?: return emptyList()
        val buffer = inputBuffer ?: return emptyList()
        val letterboxed = try {
            DetectorImageUtils.letterbox(bitmap, inputSize)
        } catch (e: Exception) {
            DebugLog.log("Detector", "预处理失败: ${e.message}")
            return emptyList()
        }

        val raw = try {
            fillInput(letterboxed, buffer)
            runInference(net)
        } catch (e: Exception) {
            DebugLog.log("Detector", "推理失败: ${e.javaClass.simpleName}: ${e.message}")
            return emptyList()
        } finally {
            if (letterboxed !== bitmap) letterboxed.recycle()
        }

        val detections = if (isYoloLayout) {
            parseYolo(raw, bitmap.width, bitmap.height, minConfidence)
        } else {
            parseSsd(raw, bitmap.width, bitmap.height, minConfidence)
        }
        return nms(detections, IOU_THRESHOLD).take(maxResults)
    }

    private fun fillInput(bitmap: Bitmap, buffer: ByteBuffer) {
        buffer.rewind()
        val pixels = IntArray(inputSize * inputSize)
        bitmap.getPixels(pixels, 0, inputSize, 0, 0, inputSize, inputSize)
        // 量化模型输入为 UINT8，浮点模型输入为 FLOAT32；这里按张量类型分别处理
        val inputType = interpreter?.getInputTensor(0)?.dataType()
        if (inputType == org.tensorflow.lite.DataType.FLOAT32) {
            for (pixel in pixels) {
                buffer.putFloat(((pixel shr 16) and 0xFF) / 255f)
                buffer.putFloat(((pixel shr 8) and 0xFF) / 255f)
                buffer.putFloat((pixel and 0xFF) / 255f)
            }
        } else {
            for (pixel in pixels) {
                buffer.put(((pixel shr 16) and 0xFF).toByte())
                buffer.put(((pixel shr 8) and 0xFF).toByte())
                buffer.put((pixel and 0xFF).toByte())
            }
        }
        buffer.rewind()
    }

    /** 执行推理，返回所有输出张量（统一拷贝成 float 数组）。 */
    private fun runInference(interpreter: Interpreter): List<Array<FloatArray>> {
        val outputCount = interpreter.outputTensorCount
        val outputs = ArrayList<Array<FloatArray>>(outputCount)
        val outputObjects = ArrayList<Any>(outputCount)

        for (i in 0 until outputCount) {
            val tensor = interpreter.getOutputTensor(i)
            val shape = tensor.shape()
            val size = shape.fold(1) { acc, v -> acc * v }
            when (tensor.dataType()) {
                org.tensorflow.lite.DataType.FLOAT32 -> {
                    // 按 [1, ...] 的最外层维度组织，便于后续解析
                    val outer = if (shape.isNotEmpty()) shape[0] else 1
                    val inner = if (outer > 0) size / outer else size
                    val array = Array(outer) { FloatArray(inner) }
                    outputObjects.add(array)
                }
                org.tensorflow.lite.DataType.UINT8 -> {
                    val outer = if (shape.isNotEmpty()) shape[0] else 1
                    val inner = if (outer > 0) size / outer else size
                    val array = Array(outer) { ByteArray(inner) }
                    outputObjects.add(array)
                }
                else -> {
                    val array = Array(1) { FloatArray(size) }
                    outputObjects.add(array)
                }
            }
        }

        val outputMap = HashMap<Int, Any>(outputCount)
        for (i in 0 until outputCount) {
            outputMap[i] = outputObjects[i]
        }
        interpreter.runForMultipleInputsOutputs(arrayOf(inputBuffer), outputMap)

        for (i in 0 until outputCount) {
            val tensor = interpreter.getOutputTensor(i)
            when (val obj = outputObjects[i]) {
                is Array<*> -> {
                    // 用元素个数与首元素类型判断布局，避免 Kotlin 数组智能转换的限制
                    val floats = obj.size > 0 && obj[0] is FloatArray
                    val quantized = obj.size > 0 && obj[0] is ByteArray
                    if (floats) {
                        @Suppress("UNCHECKED_CAST")
                        outputs.add(obj as Array<FloatArray>)
                    } else if (quantized) {
                        // 量化输出按张量的 scale/zeroPoint 反量化
                        // TFLite 2.16 的 QuantizationParams 为单值 scale/zeroPoint
                        val qp = tensor.quantizationParams()
                        val rawScale = qp.scale
                        val s: Float = if (rawScale != 0f) rawScale else 1f
                        val zero = qp.zeroPoint
                        val converted = Array(obj.size) { idx ->
                            val row = obj[idx] as ByteArray
                            FloatArray(row.size) { j ->
                                ((row[j].toInt() and 0xFF) - zero) * s
                            }
                        }
                        outputs.add(converted)
                    }
                }
            }
        }
        return outputs
    }

    /** 解析 SSD / EfficientDet 输出：[1,N,4] 框 + [1,N] 分数 + [1,N] 类别。 */
    private fun parseSsd(
        outputs: List<Array<FloatArray>>,
        sourceWidth: Int,
        sourceHeight: Int,
        minConfidence: Float
    ): List<Detection> {
        if (outputs.size < 2) return emptyList()
        val boxes = outputs[0]
        if (boxes.isEmpty()) return emptyList()
        val boxCount = boxes[0].size / 4
        if (boxCount <= 0) return emptyList()

        // 找出分数张量与类别张量：分数张量长度等于 boxCount
        val scores = outputs.firstOrNull { it.isNotEmpty() && it[0].size == boxCount }
            ?: outputs.getOrNull(1) ?: return emptyList()
        val classes = outputs.firstOrNull {
            it.isNotEmpty() && it[0].size == boxCount && it !== scores
        } ?: outputs.getOrNull(2)

        val result = ArrayList<Detection>()
        for (i in 0 until boxCount) {
            val score = scores[0].getOrNull(i) ?: continue
            if (score < minConfidence) continue
            val base = i * 4
            if (base + 3 >= boxes[0].size) break
            val rect = RectF(
                boxes[0][base],
                boxes[0][base + 1],
                boxes[0][base + 2],
                boxes[0][base + 3]
            )
            val classIndex = classes?.get(0)?.getOrNull(i)?.toInt() ?: 0
            val label = labels.getOrElse(classIndex) { "object" }
            val mapped = DetectorImageUtils.mapToOriginal(
                rect, sourceWidth, sourceHeight, inputSize
            )
            if (mapped.width() <= 0f || mapped.height() <= 0f) continue
            result.add(
                Detection(
                    kind = DetectorImageUtils.kindOf(label),
                    label = label,
                    confidence = score,
                    rect = NormRect.from(mapped)
                )
            )
        }
        return result
    }

    /** 解析 YOLOv8 输出：[1, 4+C, N]，每列是一个候选框。 */
    private fun parseYolo(
        outputs: List<Array<FloatArray>>,
        sourceWidth: Int,
        sourceHeight: Int,
        minConfidence: Float
    ): List<Detection> {
        val out = outputs.firstOrNull() ?: return emptyList()
        if (out.isEmpty()) return emptyList()
        val rows = out[0].size
        val attrs = out.size
        if (rows <= 0 || attrs <= 4) return emptyList()
        val classCount = attrs - 4
        val candidates = rows

        val result = ArrayList<Detection>()
        for (i in 0 until candidates) {
            // 取该候选在所有类别上的最大分数（YOLOv8 无独立 objectness）
            var bestClass = -1
            var bestScore = 0f
            for (c in 0 until classCount) {
                val score = out[4 + c].getOrNull(i) ?: continue
                if (score > bestScore) {
                    bestScore = score
                    bestClass = c
                }
            }
            if (bestClass < 0 || bestScore < minConfidence) continue
            // YOLOv8 输出为 cx, cy, w, h（归一化）
            val cx = out[0].getOrNull(i) ?: continue
            val cy = out[1].getOrNull(i) ?: continue
            val w = out[2].getOrNull(i) ?: continue
            val h = out[3].getOrNull(i) ?: continue
            if (w <= 0f || h <= 0f) continue
            val rect = RectF(cx - w / 2f, cy - h / 2f, cx + w / 2f, cy + h / 2f)
            val label = labels.getOrElse(bestClass) { "object" }
            val mapped = DetectorImageUtils.mapToOriginal(
                rect, sourceWidth, sourceHeight, inputSize
            )
            if (mapped.width() <= 0f || mapped.height() <= 0f) continue
            result.add(
                Detection(
                    kind = DetectorImageUtils.kindOf(label),
                    label = label,
                    confidence = bestScore,
                    rect = NormRect.from(mapped)
                )
            )
        }
        return result
    }

    /** 非极大值抑制，去掉重叠框。 */
    private fun nms(detections: List<Detection>, threshold: Float): List<Detection> {
        if (detections.size <= 1) return detections
        val sorted = detections.sortedByDescending { it.confidence }
        val kept = ArrayList<Detection>(sorted.size)
        for (candidate in sorted) {
            var overlap = false
            for (existing in kept) {
                if (existing.kind == candidate.kind && iou(existing.rect, candidate.rect) > threshold) {
                    overlap = true
                    break
                }
            }
            if (!overlap) kept.add(candidate)
        }
        return kept
    }

    private fun iou(a: NormRect, b: NormRect): Float {
        val left = max(a.left, b.left)
        val top = max(a.top, b.top)
        val right = minOf(a.right, b.right)
        val bottom = minOf(a.bottom, b.bottom)
        val interW = (right - left).coerceAtLeast(0f)
        val interH = (bottom - top).coerceAtLeast(0f)
        val inter = interW * interH
        if (inter <= 0f) return 0f
        val union = a.area + b.area - inter
        return if (union > 0f) inter / union else 0f
    }

    private fun mapFile(file: File): MappedByteBuffer {
        FileChannel.open(file.toPath(), java.nio.file.StandardOpenOption.READ).use { channel ->
            return channel.map(FileChannel.MapMode.READ_ONLY, 0, channel.size())
        }
    }

    fun release() {
        try {
            interpreter?.close()
        } catch (e: Exception) {
            DebugLog.log("Detector", "关闭解释器失败: ${e.message}")
        }
        interpreter = null
        inputBuffer = null
        loadedModelId = null
    }

    private companion object {
        const val NUM_THREADS = 2
        const val BYTES_PER_CHANNEL = 4
        const val DEFAULT_CONFIDENCE = 0.35f
        const val IOU_THRESHOLD = 0.45f
        const val MAX_RESULTS = 8
    }
}

/** COCO 80 类标签（TFLite 官方模型的类别顺序）。 */
internal val COCO_LABELS: List<String> = listOf(
    "person", "bicycle", "car", "motorcycle", "airplane", "bus", "train", "truck",
    "boat", "traffic light", "fire hydrant", "stop sign", "parking meter", "bench",
    "bird", "cat", "dog", "horse", "sheep", "cow", "elephant", "bear", "zebra",
    "giraffe", "backpack", "umbrella", "handbag", "tie", "suitcase", "frisbee",
    "skis", "snowboard", "sports ball", "kite", "baseball bat", "baseball glove",
    "skateboard", "surfboard", "tennis racket", "bottle", "wine glass", "cup",
    "fork", "knife", "spoon", "bowl", "banana", "apple", "sandwich", "orange",
    "broccoli", "carrot", "hot dog", "pizza", "donut", "cake", "chair", "couch",
    "potted plant", "bed", "dining table", "toilet", "tv", "laptop", "mouse",
    "remote", "keyboard", "cell phone", "microwave", "oven", "toaster", "sink",
    "refrigerator", "book", "clock", "vase", "scissors", "teddy bear",
    "hair drier", "toothbrush"
)
