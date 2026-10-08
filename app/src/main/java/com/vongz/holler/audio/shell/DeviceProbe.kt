package com.vongz.holler.audio.shell

import android.util.Log

/**
 * app_process 提权入口（预留）。
 *
 * Hey! 的核心技巧之一：把 Java 类打进 APK，再用
 *   app_process /system/bin com.vongz.holler.audio.shell.DeviceProbe
 * 以 shell/root 身份运行 —— 从而枚举普通 App 无权访问的音频设备。
 *
 * 本类暂不实现逻辑，仅占位保留包路径（已在 proguard 中 keep）。
 * 待你从车机 dump 出真实的 car_audio_configuration.xml 后，
 * 再按实际 usage 补全这里的探测代码。
 */
object DeviceProbe {
    @JvmStatic
    fun main(args: Array<String>) {
        Log.i("Holler/Probe", "DeviceProbe 待实现，args=${args.joinToString()}")
    }
}
