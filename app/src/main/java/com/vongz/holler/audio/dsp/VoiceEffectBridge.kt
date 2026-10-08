package com.vongz.holler.audio.dsp

import android.util.Log

/**
 * 变声桥接层（对齐 Hey! 的 VoiceEffectBridge 职责）。
 *
 * Hey! 的实现是 JNI 调 libvoiceeffect.so：
 *   nativeCreate / nativeDestroy / nativeFlush / nativeProcess / nativeSetEffect
 * Holler 用纯 Kotlin 实现等价语义，接口刻意保持一致，
 * 未来若要换回原生 DSP，只需替换本类实现，上层无感。
 */
class VoiceEffectBridge {

    companion object {
        private const val TAG = "Holler/VoiceBridge"
    }

    /** 当前效果（对齐 Hey! 的 voiceEffectType 配置项语义） */
    @Volatile
    var effect: VoiceEffect = VoiceEffect.NATIVE
        private set

    /** 是否启用 AGC（对齐 Hey! 的 enable_agc） */
    @Volatile
    var agcEnabled: Boolean = true

    /** 增益倍率（对齐 Hey! 的 volume_gain） */
    @Volatile
    var gain: Float = 1.0f

    /** 是否就绪。Hey! 的 nativeCreate 语义。 */
    @Volatile
    var ready: Boolean = false
        private set

    fun create() {
        ready = true
        Log.i(TAG, "变声桥已就绪（纯 Kotlin 实现），effect=$effect")
    }

    /** 对齐 nativeSetEffect */
    fun setEffect(e: VoiceEffect) {
        effect = e
        Log.i(TAG, "变声效果: ${e.label}")
    }

    /** 对齐 nativeProcess：处理一段 PCM */
    fun process(pcm16: ByteArray): ByteArray =
        DspChain.process(pcm16, effect, gain, agcEnabled)

    /** 对齐 nativeFlush：清空内部状态（当前实现无跨帧状态，保留语义） */
    fun flush() {
        Log.d(TAG, "flush")
    }

    /** 对齐 nativeDestroy */
    fun destroy() {
        ready = false
        Log.i(TAG, "变声桥已销毁")
    }
}
