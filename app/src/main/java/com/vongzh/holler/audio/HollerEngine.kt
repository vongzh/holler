package com.vongzh.holler.audio

import android.annotation.SuppressLint
import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.AudioTrack
import android.media.MediaRecorder
import android.media.audiofx.NoiseSuppressor
import android.os.Build
import android.util.Log
import com.vongzh.holler.audio.dsp.DspChain
import com.vongzh.holler.audio.dsp.VoiceEffect
import com.vongzh.holler.audio.dsp.VoiceEffectBridge
import com.vongzh.holler.audio.media.HollerMediaEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.concurrent.CopyOnWriteArraySet

/**
 * 喊话引擎（Push-to-Talk）。
 *
 * 流程：
 *   按住 → 录音（AudioRecord）→ 松开 → DSP 处理 → 定向回放（AudioTrack → 车外喇叭）
 *
 * 采用「先录后放」而非实时转发，原因：
 *   1. 避免实时回授啸叫（麦克风与车外喇叭同车）；
 *   2. 变声是整段处理，先录后放天然契合；
 *   3. 实现简单可靠，先跑通链路。
 *
 * 单例化说明：
 *   悬浮窗服务、外部 Intent 接收器、主界面都要能喊话，
 *   因此引擎必须跨组件共享。用 applicationContext 避免泄漏。
 */
class HollerEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "Holler/Engine"

        const val SAMPLE_RATE = 16_000
        private const val CHANNEL = AudioFormat.CHANNEL_IN_MONO
        private const val ENCODING = AudioFormat.ENCODING_PCM_16BIT

        /** 录音上限（秒）。UI 的倒计时环需要它，故公开。 */
        const val MAX_SECONDS = 15

        /** 少于该时长视为误触（约 0.25 秒） */
        private const val MIN_VALID_BYTES = SAMPLE_RATE / 4

        @Volatile
        private var INSTANCE: HollerEngine? = null

        fun get(context: Context): HollerEngine =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: HollerEngine(context.applicationContext).also { INSTANCE = it }
            }
    }

    private val audioManager =
        appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager

    /** 变声桥（对齐 Hey! 的 VoiceEffectBridge） */
    val voiceBridge = VoiceEffectBridge().apply { create() }

    private var record: AudioRecord? = null
    private var recordThread: Thread? = null
    private var buffer: ByteArrayOutputStream? = null

    @Volatile
    private var recording = false

    @Volatile
    private var playing = false

    @Volatile
    var peakLevel: Int = 0
        private set

    /** 当前音频用途，决定车机路由。由 UI 根据探测结果设置。 */
    var usage: Int = AudioAttributes.USAGE_ASSISTANCE_SONIFICATION

    /** 目标输出设备（车外喇叭）。null = 系统默认。 */
    var targetDevice: AudioDeviceInfo? = null

    /** 目标录音设备。null = 系统默认。 */
    var inputDevice: AudioDeviceInfo? = null

    /** 录音开始时刻（用于倒计时环）。0 = 未在录。 */
    @Volatile
    var recordStartedAt: Long = 0L
        private set

    /** 已录秒数（供 UI 轮询画倒计时环）。 */
    val elapsedSeconds: Float
        get() {
            val t0 = recordStartedAt
            if (t0 == 0L) return 0f
            return ((System.currentTimeMillis() - t0) / 1000f).coerceIn(0f, MAX_SECONDS.toFloat())
        }

    /** 硬件降噪器（若车机系统支持，绑定 audioSessionId 消除风噪胎噪） */
    private var noiseSuppressor: NoiseSuppressor? = null

    /** 音频焦点请求对象（车机多媒体互斥/下潜） */
    private var audioFocusRequest: AudioFocusRequest? = null

    /** 监听器列表：支持 UI / 悬浮窗 / 通知栏多组件同时订阅状态 */
    private val listeners = CopyOnWriteArraySet<Listener>()

    fun registerListener(l: Listener) {
        listeners.add(l)
    }

    fun unregisterListener(l: Listener) {
        listeners.remove(l)
    }

    /** 兼容旧代码单个 listener 属性，建议使用 registerListener / unregisterListener */
    @Deprecated("Use registerListener / unregisterListener for multi-subscriber support")
    var listener: Listener?
        get() = listeners.firstOrNull()
        set(v) {
            v?.let { listeners.add(it) }
        }

    interface Listener {
        fun onStageChanged(stage: Stage)
        fun onPhraseResult(message: String)
    }

    enum class Stage { IDLE, RECORDING, PLAYING }

    val isRecording: Boolean get() = recording
    val isPlaying: Boolean get() = playing

    private fun notify(stage: Stage) {
        for (l in listeners) {
            runCatching { l.onStageChanged(stage) }
        }
    }

    /**
     * 请求车载音频焦点。
     * - 录音时：AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK（压低车内音乐，避免麦克风录入歌曲）
     * - 回放时：AUDIOFOCUS_GAIN_TRANSIENT（保证车外喊话拥有独占播放权，不被系统静音）
     */
    private fun requestAudioFocus(focusGain: Int) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            runCatching {
                val attrsBuilder = AudioAttributes.Builder()
                AudioRouteResolver.applyUsage(attrsBuilder, usage)
                val attrs = attrsBuilder
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
                val req = AudioFocusRequest.Builder(focusGain)
                    .setAudioAttributes(attrs)
                    .setAcceptsDelayedFocusGain(false)
                    .setOnAudioFocusChangeListener { /* 车载临时焦点监听 */ }
                    .build()
                audioFocusRequest = req
                val res = audioManager.requestAudioFocus(req)
                Log.d(TAG, "requestAudioFocus gain=$focusGain result=$res")
            }
        }
    }

    /** 释放音频焦点，恢复车内音乐正常播放 */
    private fun abandonAudioFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { req ->
                runCatching { audioManager.abandonAudioFocusRequest(req) }
                audioFocusRequest = null
                Log.d(TAG, "abandonAudioFocus")
            }
        }
    }

    // ------------------------------------------------------------------
    // 设备解析（手动指定优先）
    // ------------------------------------------------------------------

    /**
     * 按 id 找出输入设备；找不到返回 null。
     *
     * 注意：普通 App 的 getDevices() 在车机上未必能枚举到车外设备，
     * 这种情况下必须靠 app_process 提权（见 DeviceProbe）才能拿到。
     */
    fun findInputDevice(id: Int): AudioDeviceInfo? {
        if (id < 0) return null
        return runCatching {
            audioManager.getDevices(AudioManager.GET_DEVICES_INPUTS).firstOrNull { it.id == id }
        }.getOrNull()
    }

    // ------------------------------------------------------------------
    // 录音
    // ------------------------------------------------------------------

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (recording || playing) return false
        if (!hasMicPermission()) {
            Log.e(TAG, "缺少 RECORD_AUDIO 权限")
            return false
        }

        val minBuf = AudioRecord.getMinBufferSize(SAMPLE_RATE, CHANNEL, ENCODING)
        if (minBuf <= 0) {
            Log.e(TAG, "AudioRecord 缓冲区参数非法: $minBuf")
            return false
        }

        return try {
            val r = AudioRecord.Builder()
                .setAudioSource(MediaRecorder.AudioSource.VOICE_RECOGNITION)
                .setAudioFormat(
                    AudioFormat.Builder()
                        .setEncoding(ENCODING)
                        .setSampleRate(SAMPLE_RATE)
                        .setChannelMask(CHANNEL)
                        .build()
                )
                .setBufferSizeInBytes(minBuf * 4)
                .build()

            if (r.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord init failed, state=${r.state}")
                runCatching { r.release() }
                return false
            }

            // 输入设备定向：优先用用户手动指定的，否则用自动探测的
            (inputDevice ?: targetDevice)?.let { dev ->
                runCatching { r.setPreferredDevice(dev) }
            }

            // 压低车内多媒体声音，防止录入车内正在播放的音乐
            requestAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)

            // 若车机硬件支持，挂载 NoiseSuppressor 消除空调风噪和行车胎噪
            if (NoiseSuppressor.isAvailable()) {
                runCatching {
                    NoiseSuppressor.create(r.audioSessionId)?.apply {
                        enabled = true
                        noiseSuppressor = this
                        Log.i(TAG, "已挂载硬件降噪 NoiseSuppressor")
                    }
                }
            }

            record = r
            buffer = ByteArrayOutputStream(SAMPLE_RATE * 2 * 5)
            recording = true
            peakLevel = 0
            recordStartedAt = System.currentTimeMillis()

            recordThread = Thread({
                val chunk = ByteArray(minBuf)
                val maxBytes = SAMPLE_RATE * 2 * MAX_SECONDS
                try {
                    r.startRecording()
                    while (recording) {
                        val n = r.read(chunk, 0, chunk.size)
                        if (n > 0) {
                            buffer?.write(chunk, 0, n)
                            peakLevel = calcPeak(chunk, n)
                            if ((buffer?.size() ?: 0) >= maxBytes) {
                                Log.w(TAG, "达到最长录音 $MAX_SECONDS 秒，自动停止")
                                break
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "录音线程异常: ${e.message}", e)
                } finally {
                    runCatching {
                        if (r.recordingState == AudioRecord.RECORDSTATE_RECORDING) r.stop()
                    }
                }
            }, "holler-record").apply { start() }

            Log.i(TAG, "开始录音")
            HollerMediaEngine.get(appContext).setDucking(true)
            notify(Stage.RECORDING)
            true
        } catch (e: Exception) {
            Log.e(TAG, "startRecording 失败: ${e.message}", e)
            abandonAudioFocus()
            recording = false
            false
        }
    }

    /**
     * 停止录音 → DSP 变声 → 定向回放。返回录到的字节数（0 表示什么都没录到）。
     */
    suspend fun stopAndPlay(): Int {
        if (!recording && record == null) return 0
        recording = false

        recordThread?.join(800)
        recordThread = null

        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null

        record?.let { runCatching { it.release() } }
        record = null

        val raw = buffer?.toByteArray() ?: ByteArray(0)
        buffer = null
        peakLevel = 0
        recordStartedAt = 0L

        Log.i(TAG, "录音结束，共 ${raw.size} 字节")

        if (raw.size < MIN_VALID_BYTES) {
            Log.w(TAG, "录音过短，忽略")
            abandonAudioFocus()
            HollerMediaEngine.get(appContext).setDucking(false)
            notify(Stage.IDLE)
            return raw.size
        }

        // DSP 处理（变声 + 降噪 + 增益 + AGC）
        val pcm = withContext(Dispatchers.Default) {
            runCatching { voiceBridge.process(raw) }
                .onFailure { Log.e(TAG, "DSP 处理失败，回退原声: ${it.message}") }
                .getOrDefault(raw)
        }

        notify(Stage.PLAYING)
        playPcm(pcm)
        notify(Stage.IDLE)
        return raw.size
    }

    /** 放弃本次录音（如手指滑出取消）。 */
    fun cancelRecording() {
        recording = false
        recordThread?.join(500)
        recordThread = null
        runCatching { noiseSuppressor?.release() }
        noiseSuppressor = null
        runCatching { record?.release() }
        record = null
        buffer = null
        peakLevel = 0
        recordStartedAt = 0L
        abandonAudioFocus()
        HollerMediaEngine.get(appContext).setDucking(false)
        notify(Stage.IDLE)
        Log.i(TAG, "取消录音")
    }

    // ------------------------------------------------------------------
    // 回放
    // ------------------------------------------------------------------

    /** 播放一段 PCM 到车外喇叭。public 供 TTS / 测试音复用。 */
    suspend fun playPcm(pcm: ByteArray) = withContext(Dispatchers.IO) {
        if (pcm.isEmpty()) return@withContext
        playing = true
        HollerMediaEngine.get(appContext).setDucking(true)
        notify(Stage.PLAYING)

        // 播放前请求独占临时焦点，防止车内媒体混音或系统静音
        requestAudioFocus(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT)

        // ⚠ usage 可能是「系统 usage」（1000~1003），直接 setUsage() 会抛
        // IllegalArgumentException 把播放打崩。统一走 applyUsage()：
        // 系统 usage 反射调 setSystemUsage()，失败则静默降级为默认 usage。
        val attrsBuilder = AudioAttributes.Builder()
        AudioRouteResolver.applyUsage(attrsBuilder, usage)
        val attrs = attrsBuilder
            .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
            .build()
        val format = AudioFormat.Builder()
            .setEncoding(ENCODING)
            .setSampleRate(SAMPLE_RATE)
            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
            .build()

        val bufSize = maxOf(
            AudioTrack.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_OUT_MONO, ENCODING),
            pcm.size
        )

        val t = AudioTrack.Builder()
            .setAudioAttributes(attrs)
            .setAudioFormat(format)
            .setBufferSizeInBytes(bufSize)
            .setTransferMode(AudioTrack.MODE_STREAM)
            .build()

        targetDevice?.let {
            val ok = t.setPreferredDevice(it)
            Log.i(TAG, "输出定向 ${it.productName} -> $ok")
        } ?: Log.w(TAG, "未指定目标设备，走系统默认输出")

        try {
            t.play()
            var off = 0
            val chunk = 4096
            while (off < pcm.size) {
                val n = minOf(chunk, pcm.size - off)
                val w = t.write(pcm, off, n)
                if (w < 0) {
                    Log.e(TAG, "AudioTrack.write error: $w")
                    break
                }
                off += w
            }
            // 关键：AudioTrack.write() 在 MODE_STREAM 下把数据写入内部硬件缓冲区后立即返回，
            // 此时喇叭才刚开始发声！如果立即 t.stop() + t.release()，缓冲区未播放完的音频会直接被截断丢弃。
            // 因此必须等待当前音频根据帧数和采样率完整播完。
            val totalFrames = pcm.size / (2 * 1) // 16bit mono = 2 bytes per frame
            val estimatedPlayMs = (totalFrames * 1000L / SAMPLE_RATE) + 60L
            val playStartTime = System.currentTimeMillis()
            while (t.playState == AudioTrack.PLAYSTATE_PLAYING) {
                val head = runCatching { t.playbackHeadPosition }.getOrDefault(0)
                if (head >= totalFrames || System.currentTimeMillis() - playStartTime > estimatedPlayMs + 300L) {
                    break
                }
                delay(20)
            }
            runCatching { t.stop() }
        } catch (e: Exception) {
            Log.e(TAG, "回放失败: ${e.message}", e)
        } finally {
            runCatching { t.release() }
            playing = false
            abandonAudioFocus()
            HollerMediaEngine.get(appContext).setDucking(false)
            notify(Stage.IDLE)
        }
    }

    // ------------------------------------------------------------------
    // 工具
    // ------------------------------------------------------------------

    private fun hasMicPermission(): Boolean =
        appContext.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) ==
            android.content.pm.PackageManager.PERMISSION_GRANTED

    /** 计算一段 PCM 的峰值（0~100），用于声波可视化。 */
    private fun calcPeak(data: ByteArray, len: Int): Int {
        var max = 0
        var i = 0
        while (i + 1 < len) {
            val v = ((data[i + 1].toInt() shl 8) or (data[i].toInt() and 0xFF)).toShort()
            val abs = kotlin.math.abs(v.toInt())
            if (abs > max) max = abs
            i += 2
        }
        return (max * 100 / Short.MAX_VALUE).coerceIn(0, 100)
    }

    /** 生成测试音（正弦波），用于验证车外喇叭通路。 */
    fun tonePcm(freqHz: Double = 440.0, seconds: Double = 1.5): ByteArray {
        val total = (SAMPLE_RATE * seconds).toInt()
        val out = ByteArray(total * 2)
        for (i in 0 until total) {
            val t = i.toDouble() / total
            val env = when {
                t < 0.05 -> t / 0.05
                t > 0.95 -> (1.0 - t) / 0.05
                else -> 1.0
            }
            val v = kotlin.math.sin(2 * Math.PI * freqHz * i / SAMPLE_RATE) * 0.6 * env * Short.MAX_VALUE
            val s = v.toInt().toShort()
            out[i * 2] = (s.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    suspend fun playTone() = playPcm(tonePcm())

    /** 生成车规「叮咚」双音提示音（类似理想/小鹏车机广播前导音：高音 880Hz + 1175Hz），更加优雅礼貌。 */
    fun chimePcm(): ByteArray {
        val part1 = DspChain.beep(seconds = 0.14, freqHz = 784.0) // G5
        val silence = ByteArray((SAMPLE_RATE * 0.04 * 2).toInt())
        val part2 = DspChain.beep(seconds = 0.22, freqHz = 1046.5) // C6
        return part1 + silence + part2
    }

    suspend fun playChime() = playPcm(chimePcm())

    /** 车外短鸣笛音效（双音频和弦鸣笛：400Hz + 500Hz），温和提示前车起步。 */
    fun hornPcm(seconds: Double = 0.35): ByteArray {
        val total = (SAMPLE_RATE * seconds).toInt()
        val out = ByteArray(total * 2)
        for (i in 0 until total) {
            val t = i.toDouble() / total
            val env = when {
                t < 0.05 -> t / 0.05
                t > 0.90 -> (1.0 - t) / 0.10
                else -> 1.0
            }
            val v1 = kotlin.math.sin(2 * Math.PI * 400.0 * i / SAMPLE_RATE)
            val v2 = kotlin.math.sin(2 * Math.PI * 500.0 * i / SAMPLE_RATE)
            val sample = (v1 * 0.5 + v2 * 0.5) * 0.65 * env * Short.MAX_VALUE
            val s = sample.toInt().toShort()
            out[i * 2] = (s.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 警示蜂鸣音效（高亢断续哔哔警示：800Hz / 1200Hz 切换）。 */
    fun sirenPcm(cycles: Int = 3): ByteArray {
        var result = ByteArray(0)
        for (c in 0 until cycles) {
            val high = DspChain.beep(seconds = 0.12, freqHz = 1200.0)
            val silence = ByteArray((SAMPLE_RATE * 0.04 * 2).toInt())
            val low = DspChain.beep(seconds = 0.12, freqHz = 800.0)
            result += high + silence + low + silence
        }
        return result
    }

    /** 倒车提示蜂鸣（标准汽车倒车雷达节奏：连续叮叮声）。 */
    fun reverseWarningPcm(beeps: Int = 3): ByteArray {
        var result = ByteArray(0)
        for (i in 0 until beeps) {
            val b = DspChain.beep(seconds = 0.15, freqHz = 950.0)
            val gap = ByteArray((SAMPLE_RATE * 0.15 * 2).toInt())
            result += b + gap
        }
        return result
    }

    /** 口哨/呼哨音效（频率向上扫频，用于趣味互动）。 */
    fun whistlePcm(seconds: Double = 0.6): ByteArray {
        val total = (SAMPLE_RATE * seconds).toInt()
        val out = ByteArray(total * 2)
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / total
            val env = when {
                t < 0.1 -> t / 0.1
                t > 0.85 -> (1.0 - t) / 0.15
                else -> 1.0
            }
            // 频率从 800Hz 扫频到 1800Hz
            val freq = 800.0 + 1000.0 * (t * t)
            phase += 2 * Math.PI * freq / SAMPLE_RATE
            val sample = kotlin.math.sin(phase) * 0.6 * env * Short.MAX_VALUE
            val s = sample.toInt().toShort()
            out[i * 2] = (s.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 趣味鸭叫声（快速下滑扫频 + 丰富鼻音谐波，用于互动与避让行人）。 */
    fun duckPcm(seconds: Double = 0.42): ByteArray {
        val total = (SAMPLE_RATE * seconds).toInt()
        val out = ByteArray(total * 2)
        var phase = 0.0
        for (i in 0 until total) {
            val t = i.toDouble() / total
            val env = when {
                t < 0.08 -> t / 0.08
                t > 0.80 -> (1.0 - t) / 0.20
                else -> 1.0
            }
            val freq = 430.0 - 150.0 * (t * 1.3).coerceAtMost(1.0)
            phase += 2 * Math.PI * freq / SAMPLE_RATE
            val base = kotlin.math.sin(phase)
            val harmonic1 = kotlin.math.sin(phase * 2.2) * 0.45
            val harmonic2 = kotlin.math.sin(phase * 3.6) * 0.25
            val nasal = kotlin.math.sin(phase * 5.0) * 0.15
            val sample = (base + harmonic1 + harmonic2 + nasal) * 0.55 * env * Short.MAX_VALUE
            val s = sample.toInt().coerceIn(-32768, 32767).toShort()
            out[i * 2] = (s.toInt() and 0xFF).toByte()
            out[i * 2 + 1] = ((s.toInt() shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 播放一段不等长的提示音（用于状态反馈）。 */
    suspend fun playHintBeep(freqHz: Double = 880.0) =
        playPcm(DspChain.beep(seconds = 0.12, freqHz = freqHz))

    /** 当前 DSP 链参数描述，供诊断面板显示。 */
    fun dspDescription(): String =
        DspChain.describeChain(voiceBridge.effect, voiceBridge.gain, voiceBridge.agcEnabled)

    /** 便捷：设置变声效果。 */
    fun setVoiceEffect(e: VoiceEffect) = voiceBridge.setEffect(e)
}
