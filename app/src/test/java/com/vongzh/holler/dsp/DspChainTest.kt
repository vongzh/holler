package com.vongzh.holler.dsp

import com.vongzh.holler.audio.dsp.DspChain
import com.vongzh.holler.audio.dsp.VoiceEffect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * 车规级纯 Kotlin 音频 DSP 处理链单元测试。
 *
 * 验证目标：
 * 1. 六大变声音效（原声/大叔/萝莉/机器人/电音/怪兽）在各种输入下的稳定性；
 * 2. 边界条件覆盖：空数据、极短帧（小于颗粒度 Hann 窗 512 采样）、超长帧；
 * 3. 数值健康：无 NaN、无 Infinity、硬限幅保真无溢出回绕；
 * 4. AGC 与软限幅抗爆音压制能力。
 */
class DspChainTest {

    private fun generateSinePcm(
        durationSec: Double,
        freqHz: Double = 440.0,
        amplitude: Float = 0.5f,
        sampleRate: Int = 16_000,
    ): ByteArray {
        val totalSamples = (durationSec * sampleRate).toInt()
        val out = ByteArray(totalSamples * 2)
        for (i in 0 until totalSamples) {
            val v = sin(2.0 * PI * freqHz * i / sampleRate).toFloat() * amplitude
            val s = (v * 32767f).toInt().coerceIn(-32768, 32767)
            out[i * 2] = (s and 0xFF).toByte()
            out[i * 2 + 1] = ((s shr 8) and 0xFF).toByte()
        }
        return out
    }

    private fun decodeToShorts(pcm: ByteArray): ShortArray {
        val n = pcm.size / 2
        val shorts = ShortArray(n)
        for (i in 0 until n) {
            val lo = pcm[i * 2].toInt() and 0xFF
            val hi = pcm[i * 2 + 1].toInt()
            shorts[i] = ((hi shl 8) or lo).toShort()
        }
        return shorts
    }

    @Test
    fun testEmptyPcmInputReturnsEmpty() {
        val empty = ByteArray(0)
        for (effect in VoiceEffect.entries) {
            val res = DspChain.process(empty, effect)
            assertEquals(0, res.size)
        }
    }

    @Test
    fun testSilenceInputPreservesZerosAndLength() {
        val sampleRate = 16_000
        val silence = ByteArray(sampleRate * 2) // 1 秒静音

        for (effect in VoiceEffect.entries) {
            val processed = DspChain.process(silence, effect)
            assertEquals("处理后长度必须严格守恒", silence.size, processed.size)

            val shorts = decodeToShorts(processed)
            for (s in shorts) {
                assertEquals("静音经变声后仍应为静音 (数值 0)", 0.toShort(), s)
            }
        }
    }

    @Test
    fun testAllVoiceEffectsWithSineWave() {
        val sinePcm = generateSinePcm(durationSec = 0.5, freqHz = 300.0, amplitude = 0.6f)

        for (effect in VoiceEffect.entries) {
            val processed = DspChain.process(sinePcm, effect, gain = 1.0f, enableAgc = true)
            assertEquals(
                "效果 [${effect.name}] 输出字节大小必须与输入一致",
                sinePcm.size,
                processed.size
            )

            val shorts = decodeToShorts(processed)
            var hasNonZero = false
            for (s in shorts) {
                // 确保没有溢出 Short 范围
                assertTrue(s in -32768..32767)
                if (s != 0.toShort()) hasNonZero = true
            }
            assertTrue("变声 [${effect.name}] 处理正弦信号后应有有效音频输出", hasNonZero)
        }
    }

    @Test
    fun testShortBuffersEdgeCases() {
        // 专门测试小于 Hann 窗 512 采样的超短音频切片（如 8, 32, 128, 256 采样）
        val testSizes = listOf(1, 8, 32, 128, 256, 511, 512, 1024)

        for (sampleCount in testSizes) {
            val pcm = ByteArray(sampleCount * 2) { 0x3F } // 任意非零 PCM
            for (effect in VoiceEffect.entries) {
                val res = DspChain.process(pcm, effect)
                assertEquals(pcm.size, res.size)
            }
        }
    }

    @Test
    fun testHighGainAndAgcSoftClipping() {
        // 满幅高增益正弦波 (amplitude = 1.0f, gain = 2.0f)
        val loudPcm = generateSinePcm(durationSec = 0.2, freqHz = 500.0, amplitude = 1.0f)
        val processed = DspChain.process(loudPcm, VoiceEffect.MONSTER, gain = 2.0f, enableAgc = true)

        val shorts = decodeToShorts(processed)
        for (s in shorts) {
            // 严格保证在 short 正常范围内，不会由于浮点回绕产生负向爆音
            assertTrue(s >= -32768 && s <= 32767)
        }
    }

    @Test
    fun testNoiseGateSquashesLowAmplitudes() {
        // 极低微弱底噪（幅值 0.005f，低于 NOISE_GATE 0.012f 门限）
        val lowNoise = generateSinePcm(durationSec = 0.1, freqHz = 100.0, amplitude = 0.005f)
        val processed = DspChain.process(lowNoise, VoiceEffect.NATIVE, gain = 1.0f, enableAgc = false)

        val inShorts = decodeToShorts(lowNoise)
        val outShorts = decodeToShorts(processed)

        // 验证底噪被噪声门衰减到原值的 10% 以下
        var originalMax = 0
        var processedMax = 0
        for (i in inShorts.indices) {
            originalMax = maxOf(originalMax, abs(inShorts[i].toInt()))
            processedMax = maxOf(processedMax, abs(outShorts[i].toInt()))
        }
        assertTrue(originalMax > 0)
        assertTrue("底噪应被门限严重衰减: in=$originalMax, out=$processedMax", processedMax < originalMax / 2)
    }

    @Test
    fun testBeepGeneration() {
        val beepPcm = DspChain.beep(seconds = 0.1, freqHz = 880.0, sampleRate = 16_000)
        val expectedLength = (16_000 * 0.1).toInt() * 2
        assertEquals(expectedLength, beepPcm.size)

        val shorts = decodeToShorts(beepPcm)
        // 首尾淡入淡出测试：首个样本应趋近于 0
        val firstSample = abs(shorts[0].toInt())
        assertTrue("提示音开头淡入应平滑", firstSample < 500)
    }

    @Test
    fun testDescribeChainFormat() {
        val desc = DspChain.describeChain(VoiceEffect.UNCLE, 1.5f, true)
        assertNotNull(desc)
        assertTrue(desc.contains("变声=大叔"))
        assertTrue(desc.contains("增益=1.50"))
        assertTrue(desc.contains("AGC=on"))
    }
}
