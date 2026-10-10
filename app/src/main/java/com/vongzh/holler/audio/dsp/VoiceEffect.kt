package com.vongzh.holler.audio.dsp

/**
 * 车规级实时变声效果类型（六种音色）。
 *
 * 纯 Kotlin 高性能 DSP 引擎实现：
 *   原声 / 大叔 / 萝莉 / 机器人 / 电音 / 怪兽
 */
enum class VoiceEffect(val label: String, val desc: String) {
    NATIVE("原声", "原生音质，直接外放"),
    UNCLE("大叔", "低沉雄浑，磁性声线"),
    LOLI("萝莉", "提升音调，轻快灵动"),
    ROBOT("机器人", "环形调制，科幻金属"),
    ELECTRO("电音", "谐波叠加，律动修音"),
    MONSTER("怪兽", "重低震颤，震撼声浪"),
}
