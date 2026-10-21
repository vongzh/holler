package com.vongzh.holler.audio

import android.content.Context
import android.content.SharedPreferences
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import com.vongzh.holler.audio.dsp.VoiceEffect
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.Locale

/**
 * 喊话条目的持久化存储。
 *
 * 对齐 Hey! 的 tts_items 能力：
 *   - 预置交通场景语料（直接借用 Hey! 的 7 条）
 *   - 支持添加 / 重命名 / 删除
 *   - 音调、语速可调
 *   - 可设置「启动时自动播放」的条目
 *
 * 用 SharedPreferences 存（简单键值，不引 Room，控制包体）。
 * 数据格式：每个条目一行 "id\u0001文本"，避免 JSON 依赖。
 */
class PhraseStore(private val context: Context) {

    companion object {
        private const val TAG = "Holler/Phrase"
        private const val PREF = "holler_phrases"
        private const val KEY_ITEMS = "tts_items"
        private const val KEY_RATE = "tts_speech_rate"
        private const val KEY_PITCH = "tts_pitch"
        private const val KEY_LAUNCH_INDEX = "launch_tts_index"
        private const val KEY_REPEAT_COUNT = "tts_repeat_count"
        private const val KEY_CHIME_ENABLED = "tts_chime_enabled"
        private const val SEP = "\u0001"

        /**
         * 预置车规语料（按交通出行、文明礼让、警示避险场景精心扩充）
         */
        val DEFAULT_PHRASES = listOf(
            "路口礼让行人，请您先过，谢谢！",
            "文明行车，跟车行驶，请关闭远光灯，谢谢！",
            "窄路会车，请减速慢行，保持安全车距",
            "前方施工拥堵，请减速慢行",
            "时速低于100请勿长时间占用快车道，谢谢！",
            "车辆正在倒车中，请注意避让！",
            "临时停靠下客，马上驶离，抱歉耽误您时间",
            "请注意车辆盲区，注意行车安全",
        )
    }

    data class Item(val id: Long, val text: String)

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    // ------------------------------------------------------------------
    // 条目 CRUD
    // ------------------------------------------------------------------

    /** 读取全部条目。首次使用时写入预置语料。 */
    fun items(): MutableList<Item> {
        val raw = prefs.getString(KEY_ITEMS, null)
        if (raw.isNullOrBlank()) {
            val seeded = DEFAULT_PHRASES.mapIndexed { i, s -> Item(i + 1L, s) }
            save(seeded)
            Log.i(TAG, "首次使用，写入 ${seeded.size} 条预置语料")
            return seeded.toMutableList()
        }
        return raw.split("\n").mapNotNull { line ->
            val p = line.split(SEP)
            if (p.size == 2) runCatching { Item(p[0].toLong(), p[1]) }.getOrNull() else null
        }.toMutableList()
    }

    fun save(list: List<Item>) {
        val text = list.joinToString("\n") { "${it.id}$SEP${it.text}" }
        prefs.edit().putString(KEY_ITEMS, text).apply()
    }

    fun add(text: String): Item? {
        val t = text.trim()
        if (t.isEmpty()) return null
        val list = items()
        val id = (list.maxOfOrNull { it.id } ?: 0L) + 1L
        val item = Item(id, t)
        list.add(item)
        save(list)
        Log.i(TAG, "已添加: $t")
        return item
    }

    fun rename(id: Long, newText: String): Boolean {
        val t = newText.trim()
        if (t.isEmpty()) return false
        val list = items()
        val idx = list.indexOfFirst { it.id == id }
        if (idx < 0) return false
        list[idx] = list[idx].copy(text = t)
        save(list)
        return true
    }

    fun remove(id: Long): Boolean {
        val list = items()
        val removed = list.removeAll { it.id == id }
        if (removed) save(list)
        return removed
    }

    fun reset(): MutableList<Item> {
        val seeded = DEFAULT_PHRASES.mapIndexed { i, s -> Item(i + 1L, s) }
        save(seeded)
        return seeded.toMutableList()
    }

    // ------------------------------------------------------------------
    // TTS 参数
    // ------------------------------------------------------------------

    /** 语速：0.5 ~ 2.0，默认 1.0 */
    var speechRate: Float
        get() = prefs.getFloat(KEY_RATE, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_RATE, v.coerceIn(0.5f, 2.0f)).apply()

    /** 音调：0.5 ~ 2.0，默认 1.0 */
    var pitch: Float
        get() = prefs.getFloat(KEY_PITCH, 1.0f)
        set(v) = prefs.edit().putFloat(KEY_PITCH, v.coerceIn(0.5f, 2.0f)).apply()

    /** 启动时自动播放的条目 id，-1 表示不自动播 */
    var launchIndex: Long
        get() = prefs.getLong(KEY_LAUNCH_INDEX, -1L)
        set(v) = prefs.edit().putLong(KEY_LAUNCH_INDEX, v).apply()

    /** 快捷语音播报重复次数：1 ~ 3 次，默认 1 次 */
    var repeatCount: Int
        get() = prefs.getInt(KEY_REPEAT_COUNT, 1)
        set(v) = prefs.edit().putInt(KEY_REPEAT_COUNT, v.coerceIn(1, 3)).apply()

    /** 播报前是否先播放一声文明提示音（叮咚），默认开启 */
    var chimeEnabled: Boolean
        get() = prefs.getBoolean(KEY_CHIME_ENABLED, true)
        set(v) = prefs.edit().putBoolean(KEY_CHIME_ENABLED, v).apply()

    // ------------------------------------------------------------------
    // 悬浮窗设置
    // ------------------------------------------------------------------

    var floatingEnabled: Boolean
        get() = prefs.getBoolean("enable_floating", true)
        set(v) = prefs.edit().putBoolean("enable_floating", v).apply()

    var logcatTriggerEnabled: Boolean
        get() = prefs.getBoolean("enable_logcat_trigger", false)
        set(v) = prefs.edit().putBoolean("enable_logcat_trigger", v).apply()

    var logcatKeyword: String
        get() = prefs.getString("logcat_trigger_keyword", "请倒车") ?: "请倒车"
        set(v) = prefs.edit().putString("logcat_trigger_keyword", v).apply()

    var floatingX: Int
        get() = prefs.getInt("floating_x", 60)
        set(v) = prefs.edit().putInt("floating_x", v).apply()

    var floatingY: Int
        get() = prefs.getInt("floating_y", 320)
        set(v) = prefs.edit().putInt("floating_y", v).apply()

    var floatingAlpha: Float
        get() = prefs.getFloat("floating_alpha", 0.85f)
        set(v) = prefs.edit().putFloat("floating_alpha", v.coerceIn(0.3f, 1f)).apply()

    var floatingSize: Int
        get() = prefs.getInt("floating_size", 48).let { if (it !in 44..64) 48 else it }
        set(v) = prefs.edit().putInt("floating_size", v.coerceIn(44, 64)).apply()

    /** 是否开启贴边智能收纳（闲置时自动收纳为贴边灵动图标） */
    var floatingAutoDock: Boolean
        get() = prefs.getBoolean("floating_autodock", true)
        set(v) = prefs.edit().putBoolean("floating_autodock", v).apply()

    /**
     * 悬浮快捷控制形态（固定为贴边灵动悬浮图标：自由拖动、松手贴边、点击展开快速喊话与快捷语音）
     */
    var floatingDockMode: Int
        get() = prefs.getInt("floating_dock_mode", 1)
        set(v) = prefs.edit().putInt("floating_dock_mode", v.coerceIn(0, 2)).apply()

    /** 变声效果选择 */
    var voiceEffect: VoiceEffect
        get() = runCatching {
            VoiceEffect.valueOf(prefs.getString("voice_effect_type", VoiceEffect.NATIVE.name)!!)
        }.getOrDefault(VoiceEffect.NATIVE)
        set(v) = prefs.edit().putString("voice_effect_type", v.name).apply()

    /** 车外总音量 (0.0f ~ 1.0f)，默认 75% 黄金车外音量 */
    var outputVolume: Float
        get() = prefs.getFloat("output_volume", 0.75f)
        set(v) = prefs.edit().putFloat("output_volume", v.coerceIn(0f, 1f)).apply()

    var volumeGain: Float
        get() = prefs.getFloat("volume_gain", 1.0f)
        set(v) = prefs.edit().putFloat("volume_gain", v.coerceIn(0.2f, 2.0f)).apply()

    var agcEnabled: Boolean
        get() = prefs.getBoolean("enable_agc", true)
        set(v) = prefs.edit().putBoolean("enable_agc", v).apply()

    /** 车外媒体播放时的音频闪避（避让）开关：麦克风喊话或播报时是否自动压低音乐音量 */
    var duckingEnabled: Boolean
        get() = prefs.getBoolean("media_ducking_enabled", true)
        set(v) = prefs.edit().putBoolean("media_ducking_enabled", v).apply()

    // ------------------------------------------------------------------
    // 手动指定音频设备（对齐 Hey! 的 output_device_id / input_device_id）
    // ------------------------------------------------------------------

    /**
     * 手动指定的输出设备 id，-1 表示自动探测。
     *
     * 这是车外喇叭定位的**最后兜底手段**：
     * 自动三路径（设备名 / 配置 / usage）都失败时，用户可遍历设备清单手动选定。
     * 与 app_process 提权绑定 —— 普通 App 的 getDevices() 在车机上
     * 未必能看到车外设备，需提权后才能枚举完整列表。
     */
    var outputDeviceId: Int
        get() = prefs.getInt("output_device_id", -1)
        set(v) = prefs.edit().putInt("output_device_id", v).apply()

    /** 手动指定的录音设备 id，-1 表示自动。 */
    var inputDeviceId: Int
        get() = prefs.getInt("input_device_id", -1)
        set(v) = prefs.edit().putInt("input_device_id", v).apply()

    /** 手动指定的音频 usage，-1 表示用默认优先级。 */
    var usageOverride: Int
        get() = prefs.getInt("usage_override", -1)
        set(v) = prefs.edit().putInt("usage_override", v).apply()

    // ------------------------------------------------------------------
    // 播报行为
    // ------------------------------------------------------------------

    /**
     * 应用启动时自动播报一次（对齐 Hey! 的 launch_auto_play）。
     *
     * ⚠ 默认关闭：车机上开机自动对外喊话容易造成打扰，
     * 应当由用户明确开启。
     */
    var launchAutoPlay: Boolean
        get() = prefs.getBoolean("launch_auto_play", false)
        set(v) = prefs.edit().putBoolean("launch_auto_play", v).apply()

    /** 日志触发冷却秒数（对齐 Hey! 的固定 8s，这里做成可调）。 */
    var logcatCooldownSec: Int
        get() = prefs.getInt("logcat_cooldown_sec", 8)
        set(v) = prefs.edit().putInt("logcat_cooldown_sec", v.coerceIn(3, 60)).apply()

    /** 开机/系统启动后自动保活服务（默认开启，免去手动启动 App） */
    var bootAutoStart: Boolean
        get() = prefs.getBoolean("boot_auto_start", true)
        set(v) = prefs.edit().putBoolean("boot_auto_start", v).apply()

    /** 通知栏常驻快捷控制开关（下拉顶栏直接喊话/播报快捷语） */
    var notifyQuickActions: Boolean
        get() = prefs.getBoolean("notify_quick_actions", true)
        set(v) = prefs.edit().putBoolean("notify_quick_actions", v).apply()

    // ------------------------------------------------------------------
    // 外观设置
    // ------------------------------------------------------------------

    /**
     * 主题模式：0=跟随系统，1=浅色模式，2=深色模式
     */
    var themeMode: Int
        get() = prefs.getInt("theme_mode", 0)
        set(v) = prefs.edit().putInt("theme_mode", v.coerceIn(0, 2)).apply()
}

/**
 * 文字转语音播放器（对齐 Hey! 的 TTS 能力）。
 *
 * 实现要点：
 *   1. 用系统 TextToSpeech 合成，再由 HollerEngine 定向播到车外喇叭；
 *      这里采用「TTS → AudioTrack」的方式而非 TTS 直出，
 *      原因：TTS 直出走系统默认路由（车内），无法定向车外。
 *   2. 合成音调/语速可调（对齐 Hey! 的 tts_pitch / tts_speech_rate）。
 *
 * 工作流：
 *   TextToSpeech.synthesizeToFile() → 得到 wav → 解码 PCM → engine.playPcm()
 */
class PhraseSpeaker(private val context: Context) {

    companion object {
        private const val TAG = "Holler/Speaker"
    }

    private var tts: TextToSpeech? = null
    private var ready = false

    /** 初始化 TTS 引擎。回调在主线程；ready=true 后才能 speak。 */
    fun init(onReady: (Boolean) -> Unit = {}) {
        if (tts != null) {
            onReady(ready)
            return
        }
        tts = TextToSpeech(context.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val res = tts?.setLanguage(Locale.CHINESE)
                if (res == TextToSpeech.LANG_MISSING_DATA || res == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.setLanguage(Locale.getDefault())
                }
                val currentLocale = runCatching { tts?.voice?.locale }.getOrNull() ?: Locale.getDefault()
                Log.i(TAG, "TTS 引擎就绪, locale=$currentLocale")
            } else {
                Log.e(TAG, "TTS 初始化失败, status=$status")
            }
            onReady(ready)
        }
    }

    val isReady: Boolean get() = ready

    /**
     * 把文本合成为 16kHz 单声道 PCM。
     * 优先走 synthesizeToFile（可控采样率），失败则回退到「不发声」。
     */
    suspend fun synthesize(text: String, rate: Float, pitch: Float): ByteArray =
        withContext(Dispatchers.IO) {
            val engine = tts ?: return@withContext ByteArray(0)
            if (!ready) return@withContext ByteArray(0)

            engine.setSpeechRate(rate.coerceIn(0.5f, 2.0f))
            engine.setPitch(pitch.coerceIn(0.5f, 2.0f))

            val done = CompletableDeferred<ByteArray>()
            val file = java.io.File(context.cacheDir, "tts_${System.currentTimeMillis()}.wav")

            Log.i(TAG, "开始合成文本: $text, rate=$rate, pitch=$pitch")
            engine.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) {
                    Log.i(TAG, "TTS 合成开始: $utteranceId")
                }
                // UtteranceProgressListener.onError(String) 在 API 21 起被
                // onError(String, int) 取代，但它仍是抽象方法，必须实现
                @Suppress("OVERRIDE_DEPRECATION")
                override fun onError(utteranceId: String?) {
                    Log.e(TAG, "TTS 合成错误: $utteranceId")
                    done.complete(ByteArray(0))
                }

                override fun onDone(utteranceId: String?) {
                    val fileSize = file.length()
                    Log.i(TAG, "TTS 合成完成: $utteranceId, 文件大小=${fileSize}B")
                    val pcm = runCatching { WavReader.readPcm16(file) }
                        .onFailure { Log.e(TAG, "WAV 解析失败: ${it.message}") }
                        .getOrDefault(ByteArray(0))
                    Log.i(TAG, "PCM 提取完成: ${pcm.size} 字节")
                    runCatching { file.delete() }
                    done.complete(pcm)
                }
            })

            // synthesizeToFile 需要 API 21+；参数用 Bundle 传（API 21+ 签名）
            val params = android.os.Bundle()
            val r = engine.synthesizeToFile(text, params, file, "holler_${System.nanoTime()}")
            if (r != TextToSpeech.SUCCESS) {
                Log.e(TAG, "synthesizeToFile 调用失败: $r")
                runCatching { file.delete() }
                return@withContext ByteArray(0)
            }

            // 超时保护：TTS 卡住不能拖垮喊话
            kotlinx.coroutines.withTimeoutOrNull(6000) { done.await() } ?: run {
                Log.w(TAG, "TTS 合成超时")
                runCatching { file.delete() }
                ByteArray(0)
            }
        }

    fun shutdown() {
        runCatching { tts?.stop() }
        runCatching { tts?.shutdown() }
        tts = null
        ready = false
    }
}

/**
 * 极简 WAV 解析：只处理 16bit PCM。
 *
 * 为什么要自己写：车机环境不想引第三方音频库；
 * TTS 输出的 wav 结构固定（RIFF + fmt + data），扫块头即可。
 */
object WavReader {

    /**
     * 读取 16bit PCM 数据段。
     * 若采样率不是 16kHz，会做线性重采样到 16kHz（HollerEngine 固定 16k 播放）。
     */
    fun readPcm16(file: java.io.File): ByteArray {
        val bytes = file.readBytes()
        if (bytes.size < 44) return ByteArray(0)

        var pos = 12
        var sampleRate = 16_000
        var channels = 1
        var bitsPerSample = 16
        var dataOff = -1
        var dataLen = 0

        // 遍历 RIFF 块，找到 fmt 与 data
        while (pos + 8 <= bytes.size) {
            val id = String(bytes, pos, 4, Charsets.US_ASCII)
            val size = le32(bytes, pos + 4)
            if (size < 0) break
            when (id) {
                "fmt " -> {
                    if (pos + 8 + 16 <= bytes.size) {
                        channels = le16(bytes, pos + 10)
                        sampleRate = le32(bytes, pos + 12)
                        bitsPerSample = le16(bytes, pos + 22)
                    }
                }
                "data" -> {
                    dataOff = pos + 8
                    dataLen = size
                    break
                }
            }
            pos += 8 + size + (size and 1)   // 块按偶数字节对齐
        }

        if (dataOff < 0 || dataLen <= 0) return ByteArray(0)
        val end = minOf(dataOff + dataLen, bytes.size)
        var pcm = bytes.copyOfRange(dataOff, end)

        if (bitsPerSample != 16) return ByteArray(0)

        // 多声道降为单声道（TTS 一般已是单声道，这里防御处理）
        if (channels > 1) pcm = downmix(pcm, channels)

        // 采样率归一化到 16k
        if (sampleRate != 16_000 && sampleRate > 0) {
            pcm = resample(pcm, sampleRate, 16_000)
        }
        return pcm
    }

    private fun downmix(pcm: ByteArray, channels: Int): ByteArray {
        val frames = pcm.size / 2 / channels
        val out = ByteArray(frames * 2)
        for (i in 0 until frames) {
            var sum = 0
            for (c in 0 until channels) {
                val idx = (i * channels + c) * 2
                sum += ((pcm[idx + 1].toInt() shl 8) or (pcm[idx].toInt() and 0xFF)).toShort().toInt()
            }
            val avg = (sum / channels).coerceIn(-32768, 32767)
            out[i * 2] = (avg and 0xFF).toByte()
            out[i * 2 + 1] = ((avg shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun resample(pcm: ByteArray, from: Int, to: Int): ByteArray {
        val inFrames = pcm.size / 2
        if (inFrames == 0) return pcm
        val outFrames = (inFrames.toLong() * to / from).toInt()
        val out = ByteArray(outFrames * 2)
        val ratio = from.toDouble() / to
        for (i in 0 until outFrames) {
            val srcPos = i * ratio
            val i0 = srcPos.toInt().coerceIn(0, inFrames - 1)
            val i1 = (i0 + 1).coerceAtMost(inFrames - 1)
            val f = (srcPos - i0).toFloat()
            val a = ((pcm[i0 * 2 + 1].toInt() shl 8) or (pcm[i0 * 2].toInt() and 0xFF)).toShort().toInt()
            val b = ((pcm[i1 * 2 + 1].toInt() shl 8) or (pcm[i1 * 2].toInt() and 0xFF)).toShort().toInt()
            val v = (a * (1f - f) + b * f).toInt().coerceIn(-32768, 32767)
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun le16(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or ((b[o + 1].toInt() and 0xFF) shl 8)

    private fun le32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xFF) or
            ((b[o + 1].toInt() and 0xFF) shl 8) or
            ((b[o + 2].toInt() and 0xFF) shl 16) or
            ((b[o + 3].toInt() and 0xFF) shl 24)
}
