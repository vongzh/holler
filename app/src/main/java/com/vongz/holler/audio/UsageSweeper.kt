package com.vongz.holler.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.util.Log
import com.vongz.holler.audio.dsp.DspChain
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext

/**
 * Usage 遍历测试器。
 *
 * 对齐 Hey! 的 UsageSweepWorker —— 这是 Hey! 定位车外喇叭的核心手段。
 * Hey! 的做法（其运行时日志）：
 *   ── Usage 遍历测试 ──
 *   范围: 0 ~ 100, 每个播放 800ms
 *   启动子进程: usage N
 *
 * 原理：逐个 usage 播一个短促的特征音，用户坐在车里听
 * 「哪个 usage 的声音从车外喇叭出来」，从而确定正确取值。
 *
 * 与 Hey! 的差异（诚实说明）：
 *   Hey! 把每个 usage 放进独立 app_process 子进程跑，因为部分 usage
 *   在 AudioTrack 创建时就会让进程崩（其日志：`→ ✗ NATIVE CRASH (进程被杀)`）。
 *   Holler 同样做**逐 usage 独立建轨 + 异常隔离**，但在同一进程内，
 *   用 try-catch 包住；若车机上确实出现 native crash，再升级为子进程方案。
 *   （这是个已知风险点，在车上实测后决定是否需要加强。）
 */
object UsageSweeper {

    private const val TAG = "Holler/Sweep"

    /** 每个 usage 的播放时长（对齐 Hey! 的 800ms） */
    private const val TONE_MS = 800

    /** 每个 usage 用的是第几个音高，便于盲听区分序号 */
    private const val BASE_FREQ = 440.0

    data class Result(
        val usage: Int,
        val usageName: String,
        val created: Boolean,
        val played: Boolean,
        val routedDevice: String,
        val error: String? = null,
    )

    /**
     * 遍历所有候选 usage，每个播一声。
     *
     * @param onProgress 每完成一个 usage 回调（用于 UI 实时显示）
     * @return 每个 usage 的结果列表
     */
    suspend fun sweep(
        context: Context,
        onProgress: (index: Int, total: Int, result: Result) -> Unit = { _, _, _ -> },
    ): List<Result> = withContext(Dispatchers.IO) {
        val list = AudioRouteResolver.CANDIDATE_USAGES
        val out = mutableListOf<Result>()
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager

        list.forEachIndexed { idx, (usage, name) ->
            if (!isActive) return@withContext out

            // 音高随序号递增，用户可凭音高判断当前是第几个
            val freq = BASE_FREQ * (1.0 + idx * 0.12)
            val pcm = DspChain.beep(seconds = TONE_MS / 1000.0, freqHz = freq)

            val r = playOne(usage, name, pcm)
            out.add(r)
            Log.i(TAG, "usage=$usage($name) created=${r.created} played=${r.played} " +
                "device=${r.routedDevice} err=${r.error}")

            onProgress(idx, list.size, r)
            delay(320)   // 留间隔，避免声音糊在一起
        }
        out
    }

    /** 用单个 usage 播一段 PCM，返回结果（异常一律捕获，不让进程崩）。 */
    private fun playOne(usage: Int, name: String, pcm: ByteArray): Result {
        var track: AudioTrack? = null
        return try {
            // 系统 usage（1000~1003）不能走 setUsage()，统一由 applyUsage 分发
            val attrsBuilder = AudioAttributes.Builder()
            val usageApplied = AudioRouteResolver.applyUsage(attrsBuilder, usage)
            val attrs = attrsBuilder
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
            // 系统 usage 在未提权时设置必然失败，音会以默认 usage 播出 ——
            // 结果不可信，直接标注出来，别让用户误判成「这个 usage 就是走车内」
            if (!usageApplied) {
                return Result(usage, name, false, false, "-", "usage 设置失败（系统 usage 需提权）")
            }

            val format = AudioFormat.Builder()
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .setSampleRate(16_000)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .build()

            val minBuf = AudioTrack.getMinBufferSize(
                16_000, AudioFormat.CHANNEL_OUT_MONO, AudioFormat.ENCODING_PCM_16BIT
            )
            val bufSize = maxOf(if (minBuf > 0) minBuf else 4096, pcm.size)

            track = AudioTrack.Builder()
                .setAudioAttributes(attrs)
                .setAudioFormat(format)
                .setBufferSizeInBytes(bufSize)
                .setTransferMode(AudioTrack.MODE_STREAM)
                .build()

            val created = track.state == AudioTrack.STATE_INITIALIZED
            if (!created) {
                return Result(usage, name, false, false, "-", "state=${track.state}")
            }

            // 播放后才有 routedDevice（音频路由是动态的）
            track.play()
            var off = 0
            while (off < pcm.size) {
                val n = minOf(2048, pcm.size - off)
                val w = track.write(pcm, off, n)
                if (w < 0) break
                off += w
            }
            // 等一小会让路由稳定
            Thread.sleep(120)
            val routed = runCatching {
                val d = track.routedDevice
                if (d != null) "${d.id}/${AudioRouteResolver.typeName(d.type)}/${d.productName}" else "-"
            }.getOrDefault("-")

            runCatching { track.stop() }
            Result(usage, name, true, true, routed)
        } catch (t: Throwable) {
            // 关键：连 Throwable 一起接住，防止 usage 非法导致进程崩
            Log.e(TAG, "usage=$usage 播放失败: ${t.message}", t)
            Result(usage, name, false, false, "-", t.message ?: t.javaClass.simpleName)
        } finally {
            runCatching { track?.release() }
        }
    }
}
