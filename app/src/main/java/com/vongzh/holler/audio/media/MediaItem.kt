package com.vongzh.holler.audio.media

import android.net.Uri

/**
 * 车规媒体播放项数据模型。
 *
 * @param id 唯一标识符（文件路径或 URI 字符串）
 * @param title 曲目或音效标题
 * @param artist 艺术家或来源标注
 * @param durationMs 音频总时长（毫秒）
 * @param uri 音频 URI
 * @param path 本地磁盘文件物理路径（若适用）
 * @param sourceType 音频来源类型
 */
data class MediaItem(
    val id: String,
    val title: String,
    val artist: String = "本地音频",
    val durationMs: Long = 0L,
    val uri: Uri? = null,
    val path: String = "",
    val sourceType: SourceType = SourceType.INTERNAL_STORAGE,
) {
    enum class SourceType(val label: String) {
        INTERNAL_STORAGE("内部存储"),
        USB_DRIVE("U盘外接"),
        CUSTOM_IMPORTED("自定义导入"),
        PRESET_EFFECT("预设音效"),
    }

    /** 格式化后的时间字符串 (mm:ss) */
    val formattedDuration: String
        get() {
            if (durationMs <= 0) return "--:--"
            val totalSec = durationMs / 1000
            val min = totalSec / 60
            val sec = totalSec % 60
            return "%02d:%02d".format(min, sec)
        }
}
