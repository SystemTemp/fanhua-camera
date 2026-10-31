package com.fanhua.camera.media

import android.content.ContentUris
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import com.fanhua.camera.debug.DebugLog

/**
 * 媒体库中的一条记录（图片或视频）。
 *
 * @param uri 内容 URI
 * @param isVideo 是否为视频
 * @param dateTaken 拍摄时间（毫秒）；缺失时回退到 DATE_ADDED
 * @param displayName 文件名
 * @param durationMs 视频时长（毫秒），图片恒为 0
 */
data class MediaEntry(
    val uri: Uri,
    val isVideo: Boolean,
    val dateTaken: Long,
    val displayName: String,
    val durationMs: Long = 0L
)

/**
 * 媒体查询层：相册面板与底部缩略图共用同一套查询/分享/删除逻辑。
 *
 * 只查询本 App 产出的媒体（文件名前缀 `FH_`），避免把用户其它照片也列进来。
 *
 * ## 关于 API 26 与 29+ 的差异
 * - `MediaStore.MediaColumns.RELATIVE_PATH` 只有 API 29+ 才有，API 26–28 只能用
 *   `MediaColumns.DATA`（绝对路径）。
 * - 本仓库**故意不用路径做过滤条件**：API 26–28 时代本 App 是通过
 *   `EXTERNAL_CONTENT_URI` 直接 insert 的，文件落在系统默认目录（DCIM）而不是
 *   `Pictures/FanhuaCamera`，一旦按路径过滤就会把老照片全部隐藏。因此统一用
 *   `DISPLAY_NAME LIKE 'FH_%'` 作为归属判据，两条 API 分支行为一致。
 * - API 29+ 保存时我们额外写了 `RELATIVE_PATH`，只是为了让新照片归档到
 *   `Pictures/FanhuaCamera`，查询侧不依赖它。
 */
class MediaRepository(context: Context) {

    private val appContext = context.applicationContext
    private val resolver get() = appContext.contentResolver

    /**
     * 查询最近的媒体，图片与视频合并后按时间倒序。
     *
     * @param limit 本次返回的最大条数
     * @param offset 跳过前多少条（用于分页）
     */
    fun recent(limit: Int, offset: Int = 0): List<MediaEntry> {
        if (limit <= 0) return emptyList()
        // 分页的正确做法：两边各多取 offset+limit 条，合并排序后再切窗口，
        // 否则会漏掉跨类型交错的条目。
        val fetch = offset + limit
        val merged = ArrayList<MediaEntry>(fetch * 2)
        merged += queryImages(fetch)
        merged += queryVideos(fetch)
        merged.sortByDescending { it.dateTaken }
        return if (offset >= merged.size) {
            emptyList()
        } else {
            merged.subList(offset, minOf(offset + limit, merged.size)).toList()
        }
    }

    /** 最新一张本 App 拍摄的照片，用于底部缩略图。 */
    fun latestPhoto(): Uri? {
        return try {
            resolver.query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.MediaColumns._ID),
                OWNER_SELECTION,
                OWNER_ARGS,
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID))
                    ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
                } else {
                    null
                }
            }
        } catch (e: Exception) {
            DebugLog.log("Media", "查询最新照片失败: ${e.message}")
            null
        }
    }

    /** 删除一条媒体；返回是否成功。 */
    fun delete(uri: Uri): Boolean {
        return try {
            resolver.delete(uri, null, null) > 0
        } catch (e: Exception) {
            DebugLog.log("Media", "删除失败: ${e.javaClass.simpleName}: ${e.message}")
            false
        }
    }

    /** 系统分享（ACTION_SEND）。 */
    fun shareIntent(uri: Uri, isVideo: Boolean): Intent {
        val mime = if (isVideo) "video/*" else "image/*"
        return Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /** 用系统应用打开（ACTION_VIEW）。 */
    fun openIntent(uri: Uri, isVideo: Boolean): Intent {
        val mime = if (isVideo) "video/*" else "image/*"
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mime)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * 加载缩略图。API 29+ 用系统 `loadThumbnail`（自带方向纠正、走磁盘缓存），
     * 低版本退回 `MediaStore.Images.Thumbnails` / `MediaStore.Video.Thumbnails`。
     */
    fun loadThumbnail(uri: Uri, isVideo: Boolean, sizePx: Int): Bitmap? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                resolver.loadThumbnail(uri, Size(sizePx, sizePx), null)
            } else {
                // API 26–28：没有 loadThumbnail，退回旧的 Thumbnails 表
                val id = ContentUris.parseId(uri)
                @Suppress("DEPRECATION")
                if (isVideo) {
                    MediaStore.Video.Thumbnails.getThumbnail(
                        resolver,
                        id,
                        MediaStore.Video.Thumbnails.MINI_KIND,
                        null
                    )
                } else {
                    MediaStore.Images.Thumbnails.getThumbnail(
                        resolver,
                        id,
                        MediaStore.Images.Thumbnails.MINI_KIND,
                        null
                    )
                }
            }
        } catch (e: Exception) {
            // loadThumbnail 在文件损坏/被移除时会抛异常，忽略并让 UI 显示占位图标
            DebugLog.log("Media", "加载缩略图失败: ${e.javaClass.simpleName}: ${e.message}")
            null
        }
    }

    private fun queryImages(limit: Int): List<MediaEntry> = query(
        collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        isVideo = false,
        limit = limit
    )

    private fun queryVideos(limit: Int): List<MediaEntry> = query(
        collection = MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
        isVideo = true,
        limit = limit
    )

    private fun query(collection: Uri, isVideo: Boolean, limit: Int): List<MediaEntry> {
        val projection = if (isVideo) {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_TAKEN,
                MediaStore.Video.Media.DURATION
            )
        } else {
            arrayOf(
                MediaStore.MediaColumns._ID,
                MediaStore.MediaColumns.DISPLAY_NAME,
                MediaStore.MediaColumns.DATE_ADDED,
                MediaStore.MediaColumns.DATE_TAKEN
            )
        }
        val result = ArrayList<MediaEntry>(limit)
        try {
            resolver.query(
                collection,
                projection,
                OWNER_SELECTION,
                OWNER_ARGS,
                MediaStore.MediaColumns.DATE_ADDED + " DESC"
            )?.use { cursor ->
                val idCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns._ID)
                val nameCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DISPLAY_NAME)
                val addedCol = cursor.getColumnIndexOrThrow(MediaStore.MediaColumns.DATE_ADDED)
                val takenCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_TAKEN)
                val durationCol = if (isVideo) {
                    cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                } else {
                    -1
                }
                while (cursor.moveToNext() && result.size < limit) {
                    val id = cursor.getLong(idCol)
                    val addedSec = if (addedCol >= 0 && !cursor.isNull(addedCol)) {
                        cursor.getLong(addedCol)
                    } else {
                        0L
                    }
                    val takenMs = if (takenCol >= 0 && !cursor.isNull(takenCol)) {
                        cursor.getLong(takenCol)
                    } else {
                        0L
                    }
                    // DATE_TAKEN 单位是毫秒且经常为 0，DATE_ADDED 单位是秒
                    val date = if (takenMs > 0L) takenMs else addedSec * 1000L
                    val duration = if (durationCol >= 0 && !cursor.isNull(durationCol)) {
                        cursor.getLong(durationCol)
                    } else {
                        0L
                    }
                    result += MediaEntry(
                        uri = ContentUris.withAppendedId(collection, id),
                        isVideo = isVideo,
                        dateTaken = date,
                        displayName = if (nameCol >= 0 && !cursor.isNull(nameCol)) {
                            cursor.getString(nameCol) ?: ""
                        } else {
                            ""
                        },
                        durationMs = duration
                    )
                }
            }
        } catch (e: Exception) {
            DebugLog.log("Media", "查询媒体失败(${if (isVideo) "视频" else "图片"}): ${e.message}")
        }
        return result
    }

    private companion object {
        /** 只认本 App 产出的媒体（文件名统一以 FH_ 开头）。 */
        const val OWNER_SELECTION = MediaStore.MediaColumns.DISPLAY_NAME + " LIKE ?"
        val OWNER_ARGS = arrayOf("FH_%")
    }
}
