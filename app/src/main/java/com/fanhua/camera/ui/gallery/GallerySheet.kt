package com.fanhua.camera.ui.gallery

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.media.MediaEntry
import com.fanhua.camera.media.MediaRepository
import com.fanhua.camera.media.PhotoProcessor
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.common.HyperConfirmDialog
import com.fanhua.camera.ui.common.HyperSheet
import com.fanhua.camera.ui.common.SheetHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.roundToLong

/** 每页条数：首次 60，滚到底部再加载 60。 */
private const val PAGE_SIZE = 60

/**
 * 相册面板：Google Material 3 风格。
 *
 * 视觉要点：
 * - 网格项 12dp 圆角，选中态 primary 2dp 描边（原先的金色描边已移除）；
 * - 视频角标 / 播放图标用中性黑底 + primary 图标；
 * - 预览区 20dp 圆角，操作区主按钮 primary 填充、次按钮描边、删除用 error 色。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySheet(
    visible: Boolean,
    controller: CameraController,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val repository = remember(context) { MediaRepository(context) }
    val photoProcessor = remember(context) { PhotoProcessor(context) }

    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var loading by remember { mutableStateOf(false) }
    var hasMore by remember { mutableStateOf(true) }
    var loadedOnce by remember { mutableStateOf(false) }
    var selectedIndex by remember { mutableStateOf<Int?>(null) }
    var processing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    /** 加载一页；reset=true 时从第 0 条重新加载。 */
    suspend fun loadPage(reset: Boolean) {
        if (loading) return
        if (!reset && !hasMore) return
        loading = true
        try {
            val offset = if (reset) 0 else entries.size
            val page = withContext(Dispatchers.IO) { repository.recent(PAGE_SIZE, offset) }
            entries = if (reset) page else entries + page
            // 返回不足一页说明已经到底
            hasMore = page.size >= PAGE_SIZE
            loadedOnce = true
        } finally {
            loading = false
        }
    }

    LaunchedEffect(visible) {
        if (visible) {
            loadPage(reset = true)
        }
    }

    HyperSheet(onDismiss = onDismiss) {
        SheetHeader(
            title = stringResource(R.string.gallery_title),
            icon = FanhuaIcons.Gallery,
            onDismiss = onDismiss
        )
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            if (entries.isEmpty() && loadedOnce && !loading) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.gallery_empty),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(430.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    contentPadding = PaddingValues(bottom = 20.dp)
                ) {
                    itemsIndexed(
                        items = entries,
                        key = { _, entry -> entry.uri.toString() }
                    ) { index, entry ->
                        GalleryCell(
                            entry = entry,
                            selected = selectedIndex == index,
                            repository = repository,
                            onClick = { selectedIndex = index }
                        )
                    }
                    // 末尾哨兵：进入组合即表示滚动到了底部，触发下一页。
                    // 只在已有内容时出现，避免首屏还没加载完就误触发。
                    if (hasMore && entries.isNotEmpty()) {
                        item(key = "load_more_sentinel") {
                            LaunchedEffect(entries.size) {
                                loadPage(reset = false)
                            }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(64.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    val current = selectedIndex?.let { entries.getOrNull(it) }
    if (current != null && selectedIndex != null) {
        PreviewSheet(
            entries = entries,
            initialIndex = selectedIndex ?: 0,
            processing = processing,
            repository = repository,
            onDismiss = { selectedIndex = null },
            onIndexChange = {
                selectedIndex = it
                processing = false
            },
            onDelete = { confirmDelete = true },
            onShare = {
                val intent = repository.shareIntent(current.uri, current.isVideo)
                try {
                    context.startActivity(
                        android.content.Intent.createChooser(
                            intent,
                            context.getString(R.string.gallery_share)
                        )
                    )
                } catch (e: Exception) {
                    Toast.makeText(
                        context,
                        context.getString(R.string.processing_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            },
            onReprocess = {
                if (current.isVideo || processing) return@PreviewSheet
                processing = true
                scope.launch {
                    val state = controller.state.value
                    val preset = state.preset
                    val intensity = state.filterIntensity
                    val upscale = state.superResEnabled
                    val newUri = withContext(Dispatchers.IO) {
                        val file = File(
                            context.cacheDir,
                            "fanhua_reprocess_" + System.currentTimeMillis() + ".jpg"
                        )
                        try {
                            context.contentResolver.openInputStream(current.uri)?.use { input ->
                                file.outputStream().use { output -> input.copyTo(output) }
                            }
                            // 重新处理不重复施加画幅裁切与镜像，否则会二次裁切/二次翻转
                            photoProcessor.process(file, preset, intensity, upscale)
                        } catch (e: Exception) {
                            null
                        } finally {
                            file.delete()
                        }
                    }
                    processing = false
                    if (newUri != null) {
                        Toast.makeText(
                            context,
                            context.getString(R.string.reprocess_done),
                            Toast.LENGTH_SHORT
                        ).show()
                        loadPage(reset = true)
                    } else {
                        Toast.makeText(
                            context,
                            context.getString(R.string.message_error_save),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            },
            onOpenSystem = {
                try {
                    context.startActivity(repository.openIntent(current.uri, current.isVideo))
                } catch (e: Exception) {
                }
            }
        )
    }

    if (confirmDelete && current != null) {
        HyperConfirmDialog(
            title = stringResource(R.string.confirm_delete_title),
            message = stringResource(R.string.confirm_delete_message),
            confirmText = stringResource(R.string.action_confirm),
            dismissText = stringResource(R.string.action_cancel),
            // 删除是不可恢复的危险操作，主按钮用 error 色
            destructive = true,
            onConfirm = {
                confirmDelete = false
                scope.launch {
                    withContext(Dispatchers.IO) { repository.delete(current.uri) }
                    selectedIndex = null
                    loadPage(reset = true)
                }
            },
            onDismiss = { confirmDelete = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSheet(
    entries: List<MediaEntry>,
    initialIndex: Int,
    processing: Boolean,
    repository: MediaRepository,
    onDismiss: () -> Unit,
    onIndexChange: (Int) -> Unit,
    onDelete: () -> Unit,
    onShare: () -> Unit,
    onReprocess: () -> Unit,
    onOpenSystem: () -> Unit
) {
    var index by remember(initialIndex) { mutableStateOf(initialIndex) }
    val entry = entries.getOrNull(index) ?: entries.firstOrNull() ?: return
    val threshold = with(LocalDensity.current) { 56.dp.toPx() }

    // 双指缩放状态：切换图片时自动重置
    var scale by remember(index) { mutableStateOf(1f) }
    var offset by remember(index) { mutableStateOf(Offset.Zero) }

    LaunchedEffect(index) {
        onIndexChange(index)
    }

    HyperSheet(onDismiss = onDismiss) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
        ) {
            AnimatedContent(
                targetState = index,
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(durationMillis = 240)) { it / 3 } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(durationMillis = 220)) { -it / 3 } + fadeOut(tween(200)))
                    } else {
                        (slideInHorizontally(tween(durationMillis = 240)) { -it / 3 } + fadeIn(tween(240))) togetherWith
                            (slideOutHorizontally(tween(durationMillis = 220)) { it / 3 } + fadeOut(tween(200)))
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .clipToBounds()
                    .pointerInput(entries.size, index) {
                        // 单指拖动翻页；双指缩放/平移优先。
                        // 一旦进入缩放（双指或 scale>1），就不再翻页，避免手势打架。
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            var totalX = 0f
                            var multiTouch = false
                            var currentScale = scale
                            while (true) {
                                val event = awaitPointerEvent()
                                if (event.changes.size > 1) multiTouch = true
                                if (multiTouch || currentScale > 1f) {
                                    val zoom = event.calculateZoom()
                                    val pan = event.calculatePan()
                                    currentScale = (currentScale * zoom).coerceIn(1f, 5f)
                                    scale = currentScale
                                    offset = if (currentScale > 1f) offset + pan else Offset.Zero
                                    // 缩放/平移必须消费，否则底部面板会跟着一起移动
                                    event.changes.forEach { it.consume() }
                                } else {
                                    // 单指拖动只累加位移用于翻页判断，不消费事件，
                                    // 以免把面板的下滑关闭手势一起吃掉
                                    totalX += event.calculatePan().x
                                }
                                if (event.changes.none { it.pressed }) break
                            }
                            if (!multiTouch && currentScale <= 1f) {
                                if (totalX < -threshold && index < entries.size - 1) {
                                    index += 1
                                } else if (totalX > threshold && index > 0) {
                                    index -= 1
                                }
                            }
                        }
                    },
                label = "previewSwipe"
            ) { target ->
                PreviewImage(
                    entry = entries.getOrNull(target) ?: entry,
                    scale = scale,
                    offset = offset,
                    repository = repository
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                if (entry.isVideo) {
                    FanhuaButton(
                        text = stringResource(R.string.open_with_system),
                        onClick = onOpenSystem,
                        style = FanhuaButtonStyle.Outline,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    FanhuaButton(
                        text = stringResource(R.string.reprocess),
                        onClick = onReprocess,
                        enabled = !processing,
                        style = FanhuaButtonStyle.Solid,
                        modifier = Modifier.weight(1f)
                    )
                }
                FanhuaButton(
                    text = stringResource(R.string.gallery_share),
                    onClick = onShare,
                    style = FanhuaButtonStyle.Outline,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            // 删除：文字按钮 + error 色，符合 Material 3 危险操作规范
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.End
            ) {
                TextButton(onClick = onDelete) {
                    Text(
                        text = stringResource(R.string.delete),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.labelLarge
                    )
                }
            }
            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
private fun PreviewImage(
    entry: MediaEntry,
    scale: Float,
    offset: Offset,
    repository: MediaRepository
) {
    val context = LocalContext.current
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = entry.uri) {
        value = if (entry.isVideo) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeSampled(context, entry.uri, 1400)?.asImageBitmap()
            }
        }
    }
    // 视频在预览区显示系统缩略图 + 播放图标
    val videoThumb by produceState<ImageBitmap?>(initialValue = null, key1 = entry.uri) {
        value = if (entry.isVideo) {
            withContext(Dispatchers.IO) {
                repository.loadThumbnail(entry.uri, true, 720)?.asImageBitmap()
            }
        } else {
            null
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(max = 420.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        val bitmap = image ?: videoThumb
        if (bitmap != null) {
            Image(
                bitmap = bitmap,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offset.x
                        translationY = offset.y
                    }
            )
            if (entry.isVideo) {
                Box(
                    modifier = Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FanhuaIcons.Play,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
        } else {
            Icon(
                imageVector = if (entry.isVideo) FanhuaIcons.Play else FanhuaIcons.Gallery,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(48.dp)
            )
        }
    }
}

/**
 * 网格单元：12dp 圆角，选中态 primary 2dp 描边，未选中无描边。
 */
@Composable
private fun GalleryCell(
    entry: MediaEntry,
    selected: Boolean,
    repository: MediaRepository,
    onClick: () -> Unit
) {
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = entry.uri) {
        value = withContext(Dispatchers.IO) {
            repository.loadThumbnail(entry.uri, entry.isVideo, 320)?.asImageBitmap()
        }
    }
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .then(
                if (selected) {
                    Modifier.border(2.dp, MaterialTheme.colorScheme.primary, shape)
                } else {
                    Modifier
                }
            )
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val image = thumbnail
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
            )
        } else {
            Icon(
                imageVector = if (entry.isVideo) FanhuaIcons.Play else FanhuaIcons.Gallery,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(24.dp)
            )
        }
        if (entry.isVideo) {
            // 播放图标（左下）
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(20.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FanhuaIcons.Play,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(12.dp)
                )
            }
            // 时长角标（右下）
            if (entry.durationMs > 0L) {
                Text(
                    text = formatDuration(entry.durationMs),
                    color = Color.White,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(6.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.6f))
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
            }
        }
    }
}

/** 把毫秒时长格式化成 mm:ss 或 h:mm:ss。 */
private fun formatDuration(durationMs: Long): String {
    val totalSeconds = (durationMs / 1000.0).roundToLong().coerceAtLeast(0L)
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

/** 按目标边长做采样解码，避免把 4K 原图整张读进内存。 */
private fun decodeSampled(context: Context, uri: Uri, target: Int): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, bounds)
    }
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > target || bounds.outHeight / sample > target) {
        sample *= 2
    }
    val options = BitmapFactory.Options().apply { inSampleSize = sample }
    return context.contentResolver.openInputStream(uri)?.use { stream ->
        BitmapFactory.decodeStream(stream, null, options)
    }
}
