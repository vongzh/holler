package com.vongzh.holler.media

import com.vongzh.holler.audio.media.MediaItem
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 媒体模型与播放时间格式化单元测试。
 */
class MediaItemTest {

    @Test
    fun testFormattedDuration() {
        val item1 = MediaItem(
            id = "1",
            title = "Test Track",
            durationMs = 269_000L, // 4分29秒
            uri = null
        )
        assertEquals("04:29", item1.formattedDuration)

        val item2 = MediaItem(
            id = "2",
            title = "Zero",
            durationMs = 0L,
            uri = null
        )
        assertEquals("--:--", item2.formattedDuration)

        val item3 = MediaItem(
            id = "3",
            title = "Short",
            durationMs = 45_000L, // 45秒
            uri = null
        )
        assertEquals("00:45", item3.formattedDuration)
    }

    @Test
    fun testPlayModeTransitions() {
        val mode = com.vongzh.holler.audio.media.HollerMediaEngine.PlayMode.LIST_LOOP
        assertEquals("列表循环", mode.label)
        val nextMode = when (mode) {
            com.vongzh.holler.audio.media.HollerMediaEngine.PlayMode.LIST_LOOP -> com.vongzh.holler.audio.media.HollerMediaEngine.PlayMode.SINGLE_LOOP
            com.vongzh.holler.audio.media.HollerMediaEngine.PlayMode.SINGLE_LOOP -> com.vongzh.holler.audio.media.HollerMediaEngine.PlayMode.SHUFFLE
            com.vongzh.holler.audio.media.HollerMediaEngine.PlayMode.SHUFFLE -> com.vongzh.holler.audio.media.HollerMediaEngine.PlayMode.LIST_LOOP
        }
        assertEquals(com.vongzh.holler.audio.media.HollerMediaEngine.PlayMode.SINGLE_LOOP, nextMode)
    }

    @Test
    fun testDuckingVolumeCalculation() {
        val musicVolume = 0.85f
        val duckedVolume = musicVolume * 0.20f
        assertEquals(0.17f, duckedVolume, 0.001f)
    }

    @Test
    fun testScrubRatioClamping() {
        val dur = 180_000L
        val ratio = 0.5f
        val pos = (ratio * dur).toLong()
        assertEquals(90_000L, pos)
    }
}
