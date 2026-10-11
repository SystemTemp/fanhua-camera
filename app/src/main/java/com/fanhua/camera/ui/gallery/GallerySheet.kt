package com.fanhua.camera.ui.gallery

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.fanhua.camera.R
import com.fanhua.camera.camera.core.CameraController
import com.fanhua.camera.media.PhotoProcessor
import com.fanhua.camera.ui.common.FanhuaButton
import com.fanhua.camera.ui.common.FanhuaButtonStyle
import com.fanhua.camera.ui.common.FanhuaDivider
import com.fanhua.camera.ui.common.FanhuaIcons
import com.fanhua.camera.ui.theme.FanhuaGold
import com.fanhua.camera.ui.theme.FanhuaSurface
import com.fanhua.camera.ui.theme.FanhuaText
import com.fanhua.camera.ui.theme.FanhuaTextDim
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private data class MediaEntry(val uri: Uri, val isVideo: Boolean)

private const val MAX_ITEMS = 48

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GallerySheet(
    visible: Boolean,
    controller: CameraController,
    onDismiss: () -> Unit
) {
    if (!visible) return
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()
    var entries by remember { mutableStateOf<List<MediaEntry>>(emptyList()) }
    var selected by remember { mutableStateOf<MediaEntry?>(null) }
    var processing by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    val photoProcessor = remember { PhotoProcessor(context) }

    suspend fun reload() {
        entries = withContext(Dispatchers.IO) { queryRecent(context, MAX_ITEMS) }
    }

    LaunchedEffect(visible) {
        if (visible) reload()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FanhuaSurface,
        scrimColor = Color(0xB3000000),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .navigationBarsPadding()
        ) {
            Spacer(modifier = Modifier.height(18.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = FanhuaIcons.Gallery,
                    contentDescription = null,
                    tint = FanhuaGold,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = stringResource(R.string.gallery_title),
                    color = FanhuaText,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.weight(1f))
                Box(
                    modifier = Modifier
                        .size(30.dp)
                        .clip(CircleShape)
                        .clickable(onClick = onDismiss),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = FanhuaIcons.Close,
                        contentDescription = stringResource(R.string.action_close),
                        tint = FanhuaTextDim,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            FanhuaDivider(width = 46.dp)
            Spacer(modifier = Modifier.height(16.dp))

            if (entries.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.gallery_empty),
                        color = FanhuaTextDim,
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
                    items(entries, key = { it.uri.toString() }) { entry ->
                        GalleryCell(entry = entry, onClick = { selected = entry })
                    }
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
        }
    }

    val current = selected
    if (current != null) {
        PreviewSheet(
            entry = current,
            processing = processing,
            onDismiss = { selected = null },
            onDelete = { confirmDelete = true },
            onReprocess = {
                if (current.isVideo || processing) return@PreviewSheet
                processing = true
                scope.launch {
                    val preset = controller.state.value.preset
                    val intensity = controller.state.value.filterIntensity
                    val upscale = controller.state.value.superResEnabled
                    val newUri = withContext(Dispatchers.IO) {
                        val file = File(
                            context.cacheDir,
                            "fanhua_reprocess_" + System.currentTimeMillis() + ".jpg"
                        )
                        try {
                            context.contentResolver.openInputStream(current.uri)?.use { input ->
                                file.outputStream().use { output -> input.copyTo(output) }
                            }
                            photoProcessor.process(file, preset, intensity, upscale)
                        } catch (e: Exception) {
                            null
                        } finally {
                            file.delete()
                        }
                    }
                    processing = false
                    if (newUri != null) {
                        Toast.makeText(context, context.getString(R.string.reprocess_done), Toast.LENGTH_SHORT).show()
                        reload()
                    } else {
                        Toast.makeText(context, context.getString(R.string.message_error_save), Toast.LENGTH_SHORT).show()
                    }
                }
            },
            onOpenSystem = {
                val intent = Intent(Intent.ACTION_VIEW).apply {
                    setDataAndType(current.uri, if (current.isVideo) "video/*" else "image/*")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                try {
                    context.startActivity(intent)
                } catch (e: Exception) {
                }
            }
        )
    }

    if (confirmDelete && current != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            containerColor = FanhuaSurface,
            title = {
                Text(text = stringResource(R.string.confirm_delete_title), color = FanhuaText)
            },
            text = {
                Text(text = stringResource(R.string.confirm_delete_message), color = FanhuaTextDim)
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        withContext(Dispatchers.IO) {
                            deleteEntry(context, current.uri)
                        }
                        selected = null
                        reload()
                    }
                }) {
                    Text(text = stringResource(R.string.action_confirm), color = FanhuaGold)
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(text = stringResource(R.string.action_cancel), color = FanhuaTextDim)
                }
            }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PreviewSheet(
    entry: MediaEntry,
    processing: Boolean,
    onDismiss: () -> Unit,
    onDelete: () -> Unit,
    onReprocess: () -> Unit,
    onOpenSystem: () -> Unit
) {
    val context = LocalContext.current
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val image by produceState<ImageBitmap?>(initialValue = null, key1 = entry.uri) {
        value = if (entry.isVideo) {
            null
        } else {
            withContext(Dispatchers.IO) {
                decodeSampled(context, entry.uri, 1400)?.asImageBitmap()
            }
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = FanhuaSurface,
        scrimColor = Color(0xB3000000),
        dragHandle = null
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp)
                    .padding(horizontal = 20.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0x12000000)),
                contentAlignment = Alignment.Center
            ) {
                val bitmap = image
                if (bitmap != null) {
                    Image(
                        bitmap = bitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Icon(
                        imageVector = if (entry.isVideo) FanhuaIcons.Play else FanhuaIcons.Gallery,
                        contentDescription = null,
                        tint = FanhuaTextDim,
                        modifier = Modifier.size(48.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(18.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
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
                    text = stringResource(R.string.delete),
                    onClick = onDelete,
                    style = FanhuaButtonStyle.Outline,
                    modifier = Modifier.weight(1f)
                )
            }
            Spacer(modifier = Modifier.height(22.dp))
        }
    }
}

@Composable
private fun GalleryCell(
    entry: MediaEntry,
    onClick: () -> Unit
) {
    val context = LocalContext.current
    val thumbnail by produceState<ImageBitmap?>(initialValue = null, key1 = entry.uri) {
        value = withContext(Dispatchers.IO) { loadThumbnail(context, entry) }
    }
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0x1AFFFFFF))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        val image = thumbnail
        if (image != null) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxWidth().aspectRatio(1f)
            )
        } else {
            Icon(
                imageVector = if (entry.isVideo) FanhuaIcons.Play else FanhuaIcons.Gallery,
                contentDescription = null,
                tint = FanhuaTextDim,
                modifier = Modifier.size(22.dp)
            )
        }
        if (entry.isVideo) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(6.dp)
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(Color(0xB3000000)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = FanhuaIcons.Play,
                    contentDescription = null,
                    tint = FanhuaGold,
                    modifier = Modifier.size(10.dp)
                )
            }
        }
    }
}

private fun queryRecent(context: Context, limit: Int): List<MediaEntry> {
    val result = mutableListOf<MediaEntry>()
    val selection = MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?"
    val args = arrayOf("FH_%")
    val sortOrder = MediaStore.MediaColumns.DATE_ADDED + " DESC"
    val projection = arrayOf(MediaStore.MediaColumns._ID)
    val images = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
    val videos = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
    try {
        context.contentResolver.query(images, projection, selection, args, sortOrder)?.use { cursor ->
            while (cursor.moveToNext() && result.size < limit) {
                val id = cursor.getLong(0)
                result += MediaEntry(
                    Uri.withAppendedPath(images, id.toString()),
                    false
                )
            }
        }
    } catch (e: Exception) {
    }
    try {
        context.contentResolver.query(videos, projection, selection, args, sortOrder)?.use { cursor ->
            while (cursor.moveToNext() && result.size < limit) {
                val id = cursor.getLong(0)
                result += MediaEntry(
                    Uri.withAppendedPath(videos, id.toString()),
                    true
                )
            }
        }
    } catch (e: Exception) {
    }
    return result
}

private fun deleteEntry(context: Context, uri: Uri) {
    try {
        context.contentResolver.delete(uri, null, null)
    } catch (e: Exception) {
    }
}

private fun loadThumbnail(context: Context, entry: MediaEntry): ImageBitmap? {
    return try {
        val bitmap: Bitmap? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(entry.uri, Size(320, 320), null)
        } else {
            if (entry.isVideo) {
                null
            } else {
                decodeSampled(context, entry.uri, 320)
            }
        }
        bitmap?.asImageBitmap()
    } catch (e: Exception) {
        null
    }
}

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
