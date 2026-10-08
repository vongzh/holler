package com.vongz.holler.audio.dsp

/**
 * 变声效果类型。
 *
 * 对齐 Hey! 的 5 种效果（从其 DEX 中提取的效果名）：
 *   原声 / 大叔 / 机器人 / 电音 / 萝莉
 *
 * 与 Hey! 的差异：Hey! 用原生 libvoiceeffect.so 做硬 DSP；
 * Holler 用纯 Kotlin 实现等价听感，代价是 CPU 略高（车机 SoC 足够），
 * 收益是不引 .so、不分架构、不增加包体。
 */
enum class VoiceEffect(val label: String, val desc: String) {
    NATIVE("原声", "不做处理，直接外放"),
    UNCLE("大叔", "降低音调，浑厚有力"),
    ROBOT("机器人", "环形调制，金属音色"),
    ELECTRO("电音", "谐波叠加，电子感"),
    LOLI("萝莉", "提升音调，轻快明亮"),
}
