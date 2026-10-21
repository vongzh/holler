package com.vongzh.holler.audio.dsp

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
        if (n <= 1) return pcm16
        val buf = FloatArray(n)
        decode(pcm16, buf, n)

        // 1) 变声（会改变波形成分）
        when (effect) {
            VoiceEffect.NATIVE -> Unit
            VoiceEffect.UNCLE -> pitchShift(buf, n, factor = 0.78f)
            VoiceEffect.LOLI -> pitchShift(buf, n, factor = 1.32f)
            VoiceEffect.ROBOT -> ringModulate(buf, n, freqHz = 48.0)
            VoiceEffect.ELECTRO -> electro(buf, n)
            VoiceEffect.MONSTER -> monster(buf, n)
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
     * 车规级颗粒重叠保留时长的时域音调变换（Granular Overlap-Add Pitch Shifter）。
     *
     * 针对车机低延迟自研实现：
     *   - 采用 512 采样（32ms @ 16kHz）颗粒度，覆盖 60Hz~400Hz 人声基频范围；
     *   - 50% 步长重叠累加，结合严格补对称的 Hann 窗，保持时域连续性与平滑能量和；
     *   - 保持原始发音语速与语音总时长不变，彻底避免末端样本冻结产生的嗡鸣声。
     *
     *   factor < 1.0f → 颗粒内波形拉伸，基频降低（大叔）
     *   factor > 1.0f → 颗粒内波形压缩，基频升高（萝莉）
     */
    private fun pitchShift(buf: FloatArray, n: Int, factor: Float) {
        if (n <= 1 || factor == 1.0f) return
        val src = buf.copyOf(n)
        val out = FloatArray(n)

        val winSize = 512
        val hop = winSize / 2

        // 预建 Hann 窗（50% 重叠下和为 1.0）
        val window = FloatArray(winSize) { k ->
            0.5f * (1f - kotlin.math.cos(2.0 * PI * k / winSize).toFloat())
        }

        var p = 0
        while (p < n) {
            val center = p + winSize / 2
            for (k in 0 until winSize) {
                val outIdx = p + k
                if (outIdx >= n) break

                val readPos = center + (k - winSize / 2) * factor
                val i0 = readPos.toInt()
                val frac = readPos - i0

                val sample = if (i0 in 0 until (n - 1)) {
                    src[i0] * (1f - frac) + src[i0 + 1] * frac
                } else if (i0 == n - 1) {
                    src[n - 1]
                } else {
                    0f
                }

                out[outIdx] += sample * window[k]
            }
            p += hop
        }

        System.arraycopy(out, 0, buf, 0, n)
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
     * 电音效果（律动修音 / 电子合唱）。
     *
     * 原理：采用连续正弦 LFO 调制微时延（8~16ms 范围平滑扫描），
     * 形成平滑移动的梳状滤波与轻微多普勒颤音，产生自然灵动的电子混响质感，
     * 杜绝周期性跳变带来的固定高频啸叫。
     */
    private fun electro(buf: FloatArray, n: Int) {
        if (n <= 1) return
        val src = buf.copyOf(n)
        val baseDelay = (0.012 * 16_000).toInt()   // 12ms 基准延迟
        val maxDelay = (n - 1).coerceAtLeast(1)
        for (i in 0 until n) {
            // 平滑 4.5Hz 低频调制，避免周期阶梯噪音
            val lfo = sin(2.0 * PI * 4.5 * i / 16_000.0)
            val jitter = (lfo * 10.0).toInt()
            val d = (baseDelay + jitter).coerceIn(1, maxDelay)
            val delayed = if (i - d >= 0) src[i - d] else 0f
            buf[i] = src[i] * 0.70f + delayed * 0.45f
        }
    }

    /**
     * 怪兽效果（次低频谐波叠加 + 空间混响 + 低频震颤）。
     *
     * 算法链：
     *   1. 降低基频至 0.68f，构建低沉骨架；
     *   2. 提取过零点生成次八度谐波（Sub-octave 40~80Hz）并经一阶低通滤波注入声浪厚度；
     *   3. 叠加 28ms 短反射混响与 18Hz 微颤，营造沉重洞穴怪兽压迫感。
     */
    private fun monster(buf: FloatArray, n: Int) {
        if (n <= 1) return
        pitchShift(buf, n, factor = 0.68f)

        // 次低频谐波发生器（过零点极性翻转法，自研生成精准 1/2 次八度音）
        val sub = FloatArray(n)
        var flip = 1.0f
        var prev = 0f
        var lp = 0f
        for (i in 0 until n) {
            if (buf[i] >= 0f && prev < 0f) flip = -flip
            prev = buf[i]
            // 一阶平滑低通，滤除高次方波杂音，仅留饱满次低频
            lp += 0.22f * (buf[i] * flip - lp)
            sub[i] = lp
        }

        val slapbackDelay = (0.028 * 16_000).toInt() // 28ms 短反射
        for (i in 0 until n) {
            val delayed = if (i - slapbackDelay >= 0) buf[i - slapbackDelay] else 0f
            // 18Hz 极轻微幅值震颤，增添机械怪兽生命感
            val tremolo = 1.0f + 0.15f * sin(2.0 * PI * 18.0 * i / 16_000.0).toFloat()
            buf[i] = (buf[i] * 0.68f + sub[i] * 0.42f + delayed * 0.30f) * tremolo
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
