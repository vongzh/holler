package com.vongz.holler.audio.dsp

import android.util.Log
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 纯 Kotlin 音频 DSP 链。
 *
 * 职责：把一段 16bit PCM 单声道语音，处理成「变声 + 增益合理 + 干净」的输出。
 * 对齐 Hey! 的处理链（从其运行时日志提取）：
 *   AGC限幅: target=0.45, minAtten=0.05, attack=0.15, release=0.02
 *   变声 / 混响 / 带通 / 噪声门 / 频移
 *
 * 设计原则：
 *   1. 全程原地改 float 数组，避免反复分配（车机内存敏感）；
 *   2. 采样率固定 16kHz（语音够用，CPU 友好）；
 *   3. 每个效果独立可测，链式组合。
 *
 * 线程模型：纯函数，无状态，可在任意线程调用。
 */
object DspChain {

    private const val TAG = "Holler/DSP"

    /** AGC 参数，直接对齐 Hey! 的实测值 */
    private const val AGC_TARGET = 0.45f
    private const val AGC_MIN_ATTEN = 0.05f
    private const val AGC_ATTACK = 0.15f
    private const val AGC_RELEASE = 0.02f

    /** 噪声门阈值：低于此值的样本视为底噪，直接压掉 */
    private const val NOISE_GATE = 0.012f

    // ==================================================================
    // 入口
    // ==================================================================

    /**
     * 主处理入口。
     *
     * @param pcm16   16bit 小端 PCM 单声道
     * @param effect  变声效果
     * @param gain    用户增益 0.0 ~ 2.0（1.0 = 原始）
     * @param enableAgc 是否启用自动增益
     * @return 处理后的 PCM，长度与输入一致
     */
    fun process(
        pcm16: ByteArray,
        effect: VoiceEffect,
        gain: Float = 1.0f,
        enableAgc: Boolean = true,
    ): ByteArray {
        if (pcm16.isEmpty()) return pcm16

        val n = pcm16.size / 2
        val buf = FloatArray(n)
        decode(pcm16, buf, n)

        // 1) 变声（会改变波形成分）
        when (effect) {
            VoiceEffect.NATIVE -> Unit
            VoiceEffect.UNCLE -> pitchShift(buf, n, factor = 0.78f)
            VoiceEffect.LOLI -> pitchShift(buf, n, factor = 1.32f)
            VoiceEffect.ROBOT -> ringModulate(buf, n, freqHz = 48.0)
            VoiceEffect.ELECTRO -> electro(buf, n)
        }

        // 2) 降噪：压在门限以下的底噪削平
        noiseGate(buf, n)

        // 3) 用户增益
        if (gain != 1.0f) {
            for (i in 0 until n) buf[i] *= gain
        }

        // 4) AGC 限幅（对齐 Hey! 参数）
        if (enableAgc) agc(buf, n)

        // 5) 硬限幅，防削顶爆音
        softClip(buf, n)

        return encode(buf, n, pcm16.size)
    }

    // ==================================================================
    // 编解码
    // ==================================================================

    private fun decode(pcm: ByteArray, out: FloatArray, n: Int) {
        for (i in 0 until n) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            val s = ((hi shl 8) or lo).toShort().toInt()
            out[i] = s / 32768f
        }
    }

    private fun encode(buf: FloatArray, n: Int, byteLen: Int): ByteArray {
        val out = ByteArray(byteLen)
        for (i in 0 until n) {
            var v = (buf[i] * 32767f).toInt()
            if (v > 32767) v = 32767
            if (v < -32768) v = -32768
            out[i * 2] = (v and 0xFF).toByte()
            out[i * 2 + 1] = ((v shr 8) and 0xFF).toByte()
        }
        return out
    }

    // ==================================================================
    // 效果实现
    // ==================================================================

    /**
     * 音调变换（变速重采样法）。
     *
     * 原理：把波形按 factor 倍速重采样再拉伸回原长度。
     *   factor < 1 → 波形变长 → 音调降低（大叔）
     *   factor > 1 → 波形变短 → 音调升高（萝莉）
     *
     * 简化处理：只做线性插值重采样，不做相位声码器（车机场景语音清晰度足够）。
     */
    private fun pitchShift(buf: FloatArray, n: Int, factor: Float) {
        val src = buf.copyOf(n)
        for (i in 0 until n) {
            val pos = i * factor
            val i0 = pos.toInt()
            val frac = pos - i0
            buf[i] = if (i0 + 1 < n) {
                src[i0] * (1f - frac) + src[i0 + 1] * frac
            } else {
                src[n - 1]
            }
        }
    }

    /**
     * 环形调制（机器人音）。
     *
     * 原理：把语音乘以一个低频正弦载波，产生上下边带，
     * 听感金属、机械、无音调 —— 就是经典的「机器人声」。
     *
     * @param freqHz 载波频率，40~60Hz 区间机械感最强
     */
    private fun ringModulate(buf: FloatArray, n: Int, freqHz: Double) {
        val sr = 16_000.0
        for (i in 0 until n) {
            val carrier = sin(2.0 * PI * freqHz * i / sr)
            // 保留 65% 原声，混入 35% 调制信号，避免完全听不清词
            buf[i] = (buf[i] * 0.65f + buf[i] * carrier.toFloat() * 0.35f)
        }
    }

    /**
     * 电音效果。
     *
     * 原理：在极短延迟（约 12ms）上叠加原声，形成梳状滤波，
     * 产生「电子合唱」的金属尾音。
     * 延迟时间略抖动，避免形成固定音高。
     */
    private fun electro(buf: FloatArray, n: Int) {
        val src = buf.copyOf(n)
        val baseDelay = (0.012 * 16_000).toInt()   // 12ms
        for (i in 0 until n) {
            // 抖动 ±3 采样，破除固定音高感
            val jitter = ((i % 7) - 3)
            val d = (baseDelay + jitter).coerceIn(1, n - 1)
            val delayed = if (i - d >= 0) src[i - d] else 0f
            buf[i] = src[i] * 0.7f + delayed * 0.45f
        }
    }

    /**
     * 噪声门。
     * 低于门限的样本直接衰减，让喊话背景更干净（车内风噪/胎噪）。
     */
    private fun noiseGate(buf: FloatArray, n: Int) {
        for (i in 0 until n) {
            if (abs(buf[i]) < NOISE_GATE) buf[i] *= 0.05f
        }
    }

    /**
     * AGC 自动增益（对齐 Hey! 实测参数）。
     *
     * 逻辑：
     *   - 估计当前帧峰值
     *   - 若峰值高于 target，按 attack 速度衰减
     *   - 若峰值低于 target，按 release 速度回升（但只是「减少衰减」，不主动放大，
     *     避免把底噪抬起来）
     *   - 衰减量不超过 minAtten 对应的上限
     */
    private fun agc(buf: FloatArray, n: Int) {
        var atten = 1.0f
        for (i in 0 until n) {
            val peak = abs(buf[i])
            val desired = if (peak > AGC_TARGET) {
                AGC_TARGET / peak
            } else {
                1.0f
            }
            // 单向平滑：衰减快(attack)、恢复慢(release)
            atten = if (desired < atten) {
                atten + (desired - atten) * AGC_ATTACK
            } else {
                atten + (desired - atten) * AGC_RELEASE
            }
            atten = atten.coerceAtLeast(AGC_MIN_ATTEN)
            buf[i] *= atten
        }
    }

    /** 软限幅：把超出 [-1,1] 的部分平滑压回，避免硬削顶的爆音。 */
    private fun softClip(buf: FloatArray, n: Int) {
        for (i in 0 until n) {
            val x = buf[i]
            buf[i] = when {
                x > 0.95f -> 0.95f + (x - 0.95f) / (1f + (x - 0.95f) * 8f)
                x < -0.95f -> -0.95f + (x + 0.95f) / (1f - (x + 0.95f) * 8f)
                else -> x
            }
        }
    }

    // ==================================================================
    // 预设语料合成（供 TTS 不可用时兜底）
    // ==================================================================

    /**
     * 生成提示音序列（不是语音，是「嘀」声，用于状态提示）。
     * 真正的人声由系统 TTS 负责（见 PhraseStore）。
     */
    fun beep(seconds: Double = 0.12, freqHz: Double = 880.0, sampleRate: Int = 16_000): ByteArray {
        val total = (sampleRate * seconds).toInt()
        val out = ByteArray(total * 2)
        for (i in 0 until total) {
            // 首尾各 10% 做淡入淡出，避免咔哒声
            val t = i.toDouble() / total
            val env = when {
                t < 0.1 -> t / 0.1
                t > 0.9 -> (1.0 - t) / 0.1
                else -> 1.0
            }
            val v = sin(2.0 * PI * freqHz * i / sampleRate) * 0.5 * env * Short.MAX_VALUE
            val s = v.toInt()
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    /** 调试用：打印处理链参数，便于和 Hey! 日志对照。 */
    fun describeChain(effect: VoiceEffect, gain: Float, agc: Boolean): String =
        "变声=${effect.label} 增益=${"%.2f".format(gain)} AGC=${if (agc) "on" else "off"} " +
            "target=$AGC_TARGET attack=$AGC_ATTACK release=$AGC_RELEASE"
            .also { Log.d(TAG, "DSP: $it") }
}
