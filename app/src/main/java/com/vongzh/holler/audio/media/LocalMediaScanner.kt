package com.vongzh.holler.audio.media

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Environment
import android.provider.OpenableColumns
import android.util.Log
import java.io.File

/**
 * 车规级本地与 U 盘音频扫描工具。
 *
 * 扫描目标：
 * 1. 车机内部存储音乐/下载目录（/sdcard/Music, /sdcard/Download）；
 * 2. 插入车机扶手箱/中控的 USB 外接存储设备（/storage/XXXX-XXXX/）；
 * 3. 用户手动通过 SAF 选取的任意单项音频。
 */
object LocalMediaScanner {

    private const val TAG = "Holler/MediaScanner"

    private val AUDIO_EXTENSIONS = setOf("mp3", "wav", "flac", "aac", "m4a", "ogg")

    /**
     * 自动扫描内部存储与可读 U 盘中的所有音频文件。
     */
    fun scanAll(context: Context): List<MediaItem> {
        val result = mutableListOf<MediaItem>()
        val seenPaths = mutableSetOf<String>()

        // 1. 扫描内部存储常用目录
        val internalDirs = listOfNotNull(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC),
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS),
            File("/sdcard/Music"),
            File("/sdcard/Download")
        ).filter { it.exists() && it.canRead() }

        for (dir in internalDirs) {
            scanDirectory(dir, MediaItem.SourceType.INTERNAL_STORAGE, result, seenPaths)
        }

        // 2. 扫描外接 U 盘存储路径 (/storage/)
        try {
            val storageRoot = File("/storage")
            if (storageRoot.exists() && storageRoot.canRead()) {
                val subDirs = storageRoot.listFiles() ?: emptyArray()
                for (sub in subDirs) {
                    val name = sub.name.lowercase()
                    // 排除系统虚拟目录
                    if (name != "emulated" && name != "self" && sub.isDirectory && sub.canRead()) {
                        scanDirectory(sub, MediaItem.SourceType.USB_DRIVE, result, seenPaths)
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "扫描 U 盘根目录受限: ${e.message}")
        }

        Log.i(TAG, "扫描完成，共找到 ${result.size} 首车外可用曲目")
        return result
    }

    private fun scanDirectory(
        dir: File,
        sourceType: MediaItem.SourceType,
        outList: MutableList<MediaItem>,
        seen: MutableSet<String>,
        maxDepth: Int = 3
    ) {
        if (maxDepth <= 0 || !dir.exists() || !dir.canRead()) return
        val files = dir.listFiles() ?: return

        for (file in files) {
            if (file.isDirectory && !file.name.startsWith(".")) {
                scanDirectory(file, sourceType, outList, seen, maxDepth - 1)
            } else if (file.isFile) {
                val ext = file.extension.lowercase()
                if (ext in AUDIO_EXTENSIONS && file.length() > 4096) { // 过滤过小破损文件
                    val abs = file.absolutePath
                    if (seen.add(abs)) {
                        val item = parseAudioFile(file, sourceType)
                        outList.add(item)
                    }
                }
            }
        }
    }

    private fun parseAudioFile(file: File, sourceType: MediaItem.SourceType): MediaItem {
        var title = file.nameWithoutExtension
        var artist = if (sourceType == MediaItem.SourceType.USB_DRIVE) "U盘歌曲" else "本地音频"
        var durationMs = 0L

        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(file.absolutePath)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let {
                    title = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let {
                    artist = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                    durationMs = it
                }
            }
        }

        return MediaItem(
            id = file.absolutePath,
            title = title,
            artist = artist,
            durationMs = durationMs,
            uri = Uri.fromFile(file),
            path = file.absolutePath,
            sourceType = sourceType
        )
    }

    /**
     * 将用户通过系统 SAF 文件选择器（OpenDocument）返回的 URI 解析为 MediaItem。
     */
    fun parseFromSafUri(context: Context, uri: Uri): MediaItem {
        var name = "自定义导入音频"
        var size = 0L

        runCatching {
            context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                val nameIdx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                val sizeIdx = cursor.getColumnIndex(OpenableColumns.SIZE)
                if (cursor.moveToFirst()) {
                    if (nameIdx >= 0) name = cursor.getString(nameIdx)
                    if (sizeIdx >= 0) size = cursor.getLong(sizeIdx)
                }
            }
        }

        var title = name.substringBeforeLast(".")
        var artist = "导入曲目"
        var durationMs = 0L

        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, uri)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)?.takeIf { it.isNotBlank() }?.let {
                    title = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)?.takeIf { it.isNotBlank() }?.let {
                    artist = it
                }
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()?.let {
                    durationMs = it
                }
            }
        }

        return MediaItem(
            id = uri.toString(),
            title = title,
            artist = artist,
            durationMs = durationMs,
            uri = uri,
            path = "",
            sourceType = MediaItem.SourceType.CUSTOM_IMPORTED
        )
    }
}
