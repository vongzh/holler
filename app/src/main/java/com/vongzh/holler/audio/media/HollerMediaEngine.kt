package com.vongzh.holler.audio.media

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.vongzh.holler.audio.AudioRouteResolver
import com.vongzh.holler.audio.HollerEngine
import java.util.concurrent.CopyOnWriteArrayList

/**
 * 车外媒体播放引擎（Boombox Audio Engine）。
 *
 * 职责：
 * 1. 车外扬声器定向：使用与喊话链路一致的 AudioDeviceInfo 与车外 usage，将音乐定向播到车外；
 * 2. 状态机与播放模式：列表循环 / 单曲循环 / 随机播放；
 * 3. 智能音频鸭音（Audio Ducking）：当车内按下说话或触发快捷语播报时，自动压低外放音量至 20%，播毕自动淡入恢复；
 * 4. 线程安全与前台保活支持。
 */
class HollerMediaEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "Holler/MediaEngine"

        @Volatile
        private var INSTANCE: HollerMediaEngine? = null

        fun get(context: Context): HollerMediaEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HollerMediaEngine(context.applicationContext).also { INSTANCE = it }
            }
    }

    enum class PlayMode(val label: String) {
        LIST_LOOP("列表循环"),
        SINGLE_LOOP("单曲循环"),
        SHUFFLE("随机播放")
    }

    interface Listener {
        fun onPlaybackStateChanged(isPlaying: Boolean, track: MediaItem?)
        fun onProgressUpdate(positionMs: Long, durationMs: Long)
        fun onPlaylistChanged(tracks: List<MediaItem>)
        fun onPlayModeChanged(mode: PlayMode)
    }

    private val listeners = CopyOnWriteArrayList<Listener>()
    private val mainHandler = Handler(Looper.getMainLooper())

    private var mediaPlayer: MediaPlayer? = null
    private val playlist = mutableListOf<MediaItem>()
    private var currentIndex: Int = -1

    @Volatile
    var isPlaying: Boolean = false
        private set

    @Volatile
    var isDucked: Boolean = false
        private set

    /** 车外媒体播放时的音频闪避（避让）总开关：喊话或播报时是否自动压低音乐音量至 20% */
    var isDuckingEnabled: Boolean = true
        set(value) {
            field = value
            applyCurrentVolume(smooth = true, durationMs = 160L)
        }

    var playMode: PlayMode = PlayMode.LIST_LOOP
        set(value) {
            field = value
            notifyPlayMode(value)
        }

    /** 独立车外音乐音量 (0.0f ~ 1.0f) */
    var musicVolume: Float = 0.85f
        set(value) {
            field = value.coerceIn(0f, 1f)
            applyCurrentVolume(smooth = false)
        }

    @Volatile
    private var currentAppliedVolume: Float = 0.85f
    private var fadeRunnable: Runnable? = null

    val currentTrack: MediaItem?
        get() = if (currentIndex in playlist.indices) playlist[currentIndex] else null

    val currentPosition: Long
        get() = runCatching { mediaPlayer?.currentPosition?.toLong() ?: 0L }.getOrDefault(0L)

    val duration: Long
        get() = runCatching { mediaPlayer?.duration?.toLong() ?: 0L }.getOrDefault(0L)

    val tracks: List<MediaItem>
        get() = playlist.toList()

    private val progressRunnable = object : Runnable {
        override fun run() {
            if (isPlaying && mediaPlayer != null) {
                val pos = currentPosition
                val dur = duration
                listeners.forEach { it.onProgressUpdate(pos, dur) }
                mainHandler.postDelayed(this, 500)
            }
        }
    }

    // ==================================================================
    // 监听器注册
    // ==================================================================

    fun registerListener(listener: Listener) {
        if (!listeners.contains(listener)) {
            listeners.add(listener)
            listener.onPlaylistChanged(tracks)
            listener.onPlayModeChanged(playMode)
            listener.onPlaybackStateChanged(isPlaying, currentTrack)
        }
    }

    fun unregisterListener(listener: Listener) {
        listeners.remove(listener)
    }

    // ==================================================================
    // 播放列表控制
    // ==================================================================

    fun setPlaylist(newTracks: List<MediaItem>, autoPlayIndex: Int = -1) {
        playlist.clear()
        playlist.addAll(newTracks)
        notifyPlaylist()

        if (autoPlayIndex in playlist.indices) {
            playAtIndex(autoPlayIndex)
        } else if (currentIndex !in playlist.indices && playlist.isNotEmpty()) {
            currentIndex = 0
            notifyPlaybackState()
        }
    }

    fun addTrack(item: MediaItem, playImmediately: Boolean = false) {
        val existingIndex = playlist.indexOfFirst { it.id == item.id }
        if (existingIndex >= 0) {
            if (playImmediately) playAtIndex(existingIndex)
            return
        }
        playlist.add(item)
        notifyPlaylist()
        if (playImmediately) {
            playAtIndex(playlist.size - 1)
        }
    }

    // ==================================================================
    // 播放核心控制
    // ==================================================================

    fun playAtIndex(index: Int) {
        if (index !in playlist.indices) return
        currentIndex = index
        val item = playlist[index]
        val targetUri = item.uri ?: if (item.path.isNotBlank()) Uri.fromFile(java.io.File(item.path)) else null
        if (targetUri != null) {
            startPlayback(targetUri)
        }
    }

    fun togglePlayPause() {
        val mp = mediaPlayer
        if (mp != null && isPlaying) {
            pause()
        } else if (mp != null && !isPlaying) {
            resume()
        } else if (currentIndex in playlist.indices) {
            playAtIndex(currentIndex)
        } else if (playlist.isNotEmpty()) {
            playAtIndex(0)
        }
    }

    fun pause() {
        runCatching {
            mediaPlayer?.pause()
            isPlaying = false
            mainHandler.removeCallbacks(progressRunnable)
            notifyPlaybackState()
        }
    }

    fun resume() {
        runCatching {
            mediaPlayer?.start()
            isPlaying = true
            mainHandler.post(progressRunnable)
            notifyPlaybackState()
        }
    }

    fun stop() {
        runCatching {
            fadeRunnable?.let { mainHandler.removeCallbacks(it) }
            mediaPlayer?.apply {
                setOnPreparedListener(null)
                setOnCompletionListener(null)
                setOnErrorListener(null)
                runCatching { stop() }
                runCatching { release() }
            }
            mediaPlayer = null
            isPlaying = false
            mainHandler.removeCallbacks(progressRunnable)
            notifyPlaybackState()
        }
    }

    fun nextTrack() {
        if (playlist.isEmpty()) return
        when (playMode) {
            PlayMode.SINGLE_LOOP -> playAtIndex(currentIndex)
            PlayMode.SHUFFLE -> {
                val next = playlist.indices.random()
                playAtIndex(next)
            }
            PlayMode.LIST_LOOP -> {
                val next = (currentIndex + 1) % playlist.size
                playAtIndex(next)
            }
        }
    }

    fun playTrack(item: MediaItem) {
        val idx = playlist.indexOfFirst { it.id == item.id }
        if (idx >= 0) playAtIndex(idx)
    }

    fun playNext() = nextTrack()
    fun playPrevious() = prevTrack()

    fun prevTrack() {
        if (playlist.isEmpty()) return
        val prev = if (currentIndex - 1 < 0) playlist.size - 1 else currentIndex - 1
        playAtIndex(prev)
    }

    fun seekTo(positionMs: Long) {
        runCatching {
            mediaPlayer?.seekTo(positionMs.toInt())
            listeners.forEach { it.onProgressUpdate(positionMs, duration) }
        }
    }

    // ==================================================================
    // 车载音频闪避 (Audio Ducking) 与音量平滑渐变 (Volume Ramp)
    // ==================================================================

    /**
     * 设置音频闪避（避让）状态。
     * 当车外麦克风开始录音或播报快捷语时调用 duck(true)；播完调用 duck(false)。
     * 采用 140ms/220ms 平滑插值渐变，避免车外大音量突变产生“啪嗒”硬切音或听觉不适。
     */
    fun setDucking(duck: Boolean) {
        if (isDucked == duck) return
        isDucked = duck
        Log.i(TAG, "车外媒体音频闪避状态切换: duck=$duck (enabled=$isDuckingEnabled)")
        applyCurrentVolume(smooth = true, durationMs = if (duck) 140L else 220L)
    }

    /**
     * 平滑应用当前音量（支持插值渐变 Ramp）。
     */
    private fun applyCurrentVolume(smooth: Boolean = true, durationMs: Long = 160L) {
        val target = if (isDucked && isDuckingEnabled) musicVolume * 0.20f else musicVolume
        if (!smooth) {
            fadeRunnable?.let { mainHandler.removeCallbacks(it) }
            currentAppliedVolume = target
            runCatching { mediaPlayer?.setVolume(target, target) }
            return
        }
        fadeVolumeTo(target, durationMs)
    }

    private fun fadeVolumeTo(targetVolume: Float, durationMs: Long) {
        fadeRunnable?.let { mainHandler.removeCallbacks(it) }
        val mp = mediaPlayer
        if (mp == null) {
            currentAppliedVolume = targetVolume
            return
        }

        val start = currentAppliedVolume
        val steps = 8
        val intervalMs = (durationMs / steps).coerceAtLeast(10L)
        val delta = (targetVolume - start) / steps
        var stepCount = 0

        val runnable = object : Runnable {
            override fun run() {
                stepCount++
                if (stepCount >= steps) {
                    currentAppliedVolume = targetVolume
                    runCatching { mp.setVolume(targetVolume, targetVolume) }
                } else {
                    currentAppliedVolume = start + delta * stepCount
                    runCatching { mp.setVolume(currentAppliedVolume, currentAppliedVolume) }
                    mainHandler.postDelayed(this, intervalMs)
                }
            }
        }
        fadeRunnable = runnable
        mainHandler.post(runnable)
    }

    /**
     * 动态刷新车外音频路由设备。
     * 当用户在设置页切换输出设备时调用，使播放中的媒体流无缝迁至新外设。
     */
    fun refreshAudioRouting() {
        val engine = HollerEngine.get(appContext)
        val dev = engine.targetDevice
        runCatching {
            mediaPlayer?.let { mp ->
                val ok = mp.setPreferredDevice(dev)
                Log.i(TAG, "动态刷新车外媒体路由设备: ${dev?.productName ?: "默认"} -> $ok")
            }
        }
    }

    // ==================================================================
    // 内部实现与车外路由定向
    // ==================================================================

    private fun startPlayback(uri: Uri) {
        runCatching {
            fadeRunnable?.let { mainHandler.removeCallbacks(it) }
            mediaPlayer?.apply {
                setOnPreparedListener(null)
                setOnCompletionListener(null)
                setOnErrorListener(null)
                runCatching { stop() }
                runCatching { release() }
            }
            mediaPlayer = null
            mainHandler.removeCallbacks(progressRunnable)

            val mp = MediaPlayer()

            // 1. 继承主引擎已校准的车外 usage
            val engine = HollerEngine.get(appContext)
            val attrsBuilder = AudioAttributes.Builder()
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            AudioRouteResolver.applyUsage(attrsBuilder, engine.usage)
            mp.setAudioAttributes(attrsBuilder.build())

            // 2. 继承车外扬声器定向设备
            engine.targetDevice?.let { dev ->
                val ok = mp.setPreferredDevice(dev)
                Log.i(TAG, "车外媒体输出定向 ${dev.productName} -> $ok")
            }

            // 3. 设置数据源与监听器
            mp.setDataSource(appContext, uri)
            mp.setOnPreparedListener { player ->
                applyCurrentVolume(smooth = false)
                player.start()
                this.isPlaying = true
                mainHandler.post(progressRunnable)
                notifyPlaybackState()
                HollerMediaService.start(appContext)
            }
            mp.setOnCompletionListener {
                nextTrack()
            }
            mp.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error: what=$what extra=$extra")
                isPlaying = false
                notifyPlaybackState()
                false
            }
            mp.prepareAsync()
            mediaPlayer = mp
        }.onFailure {
            Log.e(TAG, "启动媒体播放失败: ${it.message}", it)
            isPlaying = false
            notifyPlaybackState()
        }
    }

    private fun notifyPlaybackState() {
        val playing = isPlaying
        val track = currentTrack
        listeners.forEach { it.onPlaybackStateChanged(playing, track) }
    }

    private fun notifyPlaylist() {
        val list = tracks
        listeners.forEach { it.onPlaylistChanged(list) }
    }

    private fun notifyPlayMode(mode: PlayMode) {
        listeners.forEach { it.onPlayModeChanged(mode) }
    }
}
