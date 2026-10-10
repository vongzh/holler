package com.vongzh.holler.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.util.Log
import java.io.File

/**
 * 车外扬声器定位器。
 *
 * Holler 的核心难点只有一个：把音频定向播到「车外喇叭」，而不是车内音响。
 * 本类按车载音频路由策略依次尝试：
 *
 *   1. 设备名匹配 —— 枚举 AudioManager 输出设备，按关键字匹配车外扬声器；
 *   2. 配置解析   —— 读 car_audio_configuration.xml，找出车外设备及其 usage；
 *   3. usage 遍历 —— 逐个 usage 试播，人工听哪个从车外出来（UsageSweep 策略）。
 *
 * 三条路径都失败时，退化为默认输出（至少能出声）。
 */
object AudioRouteResolver {

    private const val TAG = "Holler/Route"

    /** 车机音频配置文件的标准位置，按优先级排列 */
    private val CONFIG_PATHS = listOf(
        "/vendor/etc/car_audio_configuration.xml",
        "/odm/etc/car_audio_configuration.xml",
        "/system/etc/car_audio_configuration.xml",
    )

    /** 车外扬声器常见关键字（用于从设备名中匹配） */
    private val EXTERNAL_KEYWORDS = listOf(
        "external", "outside", "exterior", "avns_external", "speaker_out",
        "车外", "外置", "external_speaker", "esl", "pedestrian",
    )

    /**
     * 候选音频用途（usage）。
     *
     * Hey! 的做法是「全量遍历 + 人工听」，范围 0~100（其日志：
     * `范围: 0 ~ 100, 每个播放 800ms`）。
     * 这里整理出**实际可用的枚举值**作为遍历清单 ——
     * Android 的 USAGE_* 是离散常量，不是连续 0~100，
     * 直接枚举常量更有意义（Hey! 的 0~100 是它自己的私有 usage 空间）。
     */
    // ------------------------------------------------------------------
    // 「系统 usage」常量 —— 公开 SDK 不导出符号，这里按 AOSP 数值硬编码
    // ------------------------------------------------------------------
    //
    // Android 11 引入了 4 个「系统 usage」（标记 @SystemApi）：
    //   USAGE_EMERGENCY / USAGE_SAFETY / USAGE_VEHICLE_STATUS / USAGE_ANNOUNCEMENT
    // 数值来源：AOSP frameworks/base/media/java/android/media/AudioAttributes.java
    //
    // ⚠ 两个硬约束 —— 它们直接决定了 Holler 的技术路线：
    //   1) 必须用 AudioAttributes.Builder#setSystemUsage() 设置；
    //      用普通的 setUsage() 传这些值会抛 IllegalArgumentException（运行期崩溃）。
    //   2) 需要 MODIFY_AUDIO_ROUTING 权限（signature|privileged 级），
    //      普通 App 申请不生效 —— 只能靠 app_process 提权（见 shell/DeviceProbe.kt）。
    //
    // 所以它们**只在提权路径下才真正可用**：遍历/播放时必须异常保护，
    // 否则一遇到这几个 usage 就崩。
    const val USAGE_EMERGENCY = 1000
    const val USAGE_SAFETY = 1001
    const val USAGE_VEHICLE_STATUS = 1002
    const val USAGE_ANNOUNCEMENT = 1003

    /** 是否为「系统 usage」（需 MODIFY_AUDIO_ROUTING + setSystemUsage）。 */
    fun isSystemUsage(usage: Int): Boolean = usage in 1000..1003

    /**
     * 给 AudioAttributes.Builder 设置 usage，自动区分普通 / 系统 usage。
     *
     * - 普通 usage（0~999）：直接 setUsage()
     * - 系统 usage（1000~1003）：必须走 @SystemApi 的 setSystemUsage()。
     *   公开 SDK 里没有这个符号，只能反射调用；未提权时调用会失败（返回 false），
     *   此时 builder 保持原 usage，调用方按「降级」处理即可 —— 关键是别让它崩。
     *
     * @return 是否设置成功。任何异常都被吞掉，不向调用方抛。
     */
    fun applyUsage(builder: AudioAttributes.Builder, usage: Int): Boolean {
        if (!isSystemUsage(usage)) {
            // 普通 usage 也可能是非法数值（厂商私有空间），所以仍然 try
            return runCatching { builder.setUsage(usage) }.isSuccess
        }
        return runCatching {
            AudioAttributes.Builder::class.java
                .getMethod("setSystemUsage", Int::class.java)
                .invoke(builder, usage)
        }.isSuccess
    }

    // 注：下面 3 个 NOTIFICATION_COMMUNICATION_* 在 API 35 被标记废弃，
    // 但老车机（含 Android 11）上它们仍然实际生效，遍历清单必须保留。
    @Suppress("DEPRECATION")
    val CANDIDATE_USAGES: List<Pair<Int, String>> = listOf(
        AudioAttributes.USAGE_UNKNOWN to "UNKNOWN",
        AudioAttributes.USAGE_MEDIA to "MEDIA",
        AudioAttributes.USAGE_VOICE_COMMUNICATION to "VOICE_COMMUNICATION",
        AudioAttributes.USAGE_VOICE_COMMUNICATION_SIGNALLING to "VOICE_COMM_SIGNALLING",
        AudioAttributes.USAGE_ALARM to "ALARM",
        AudioAttributes.USAGE_NOTIFICATION to "NOTIFICATION",
        AudioAttributes.USAGE_NOTIFICATION_RINGTONE to "NOTIFICATION_RINGTONE",
        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_REQUEST to "NOTIF_COMM_REQUEST",
        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_INSTANT to "NOTIF_COMM_INSTANT",
        AudioAttributes.USAGE_NOTIFICATION_COMMUNICATION_DELAYED to "NOTIF_COMM_DELAYED",
        AudioAttributes.USAGE_NOTIFICATION_EVENT to "NOTIFICATION_EVENT",
        AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY to "ASSIST_ACCESSIBILITY",
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE to "ASSIST_NAV_GUIDANCE",
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION to "ASSIST_SONIFICATION",
        AudioAttributes.USAGE_GAME to "GAME",
        AudioAttributes.USAGE_ASSISTANT to "ASSISTANT",
        USAGE_EMERGENCY to "EMERGENCY",
        USAGE_SAFETY to "SAFETY",
        USAGE_VEHICLE_STATUS to "VEHICLE_STATUS",
        USAGE_ANNOUNCEMENT to "ANNOUNCEMENT",
    )

    /**
     * 默认优先尝试顺序。
     * 参考：车外行人警示（AVAS）通常走 SAFETY / EMERGENCY / ANNOUNCEMENT；
     * 车外喊话类功能在部分车机挂在 ASSISTANCE_SONIFICATION。
     */
    private val PRIORITY_USAGES = listOf(
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION,
        USAGE_SAFETY,
        USAGE_ANNOUNCEMENT,
        USAGE_EMERGENCY,
        USAGE_VEHICLE_STATUS,
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE,
        AudioAttributes.USAGE_UNKNOWN,
        AudioAttributes.USAGE_MEDIA,
    )

    data class RouteInfo(
        val device: AudioDeviceInfo?,
        val usage: Int,
        val usageName: String,
        val source: String,
        val configDevices: Map<String, List<String>> = emptyMap(),
        /** 实际枚举到的输出设备对象（供设置页做「选用」） */
        val outputDevices: List<AudioDeviceInfo> = emptyList(),
        /** 实际枚举到的输入设备对象 */
        val inputDevices: List<AudioDeviceInfo> = emptyList(),
    )

    // ------------------------------------------------------------------
    // 设备枚举
    // ------------------------------------------------------------------

    fun listOutputDevices(context: Context): List<AudioDeviceInfo> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return emptyList()
        return runCatching {
            am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).toList()
        }.getOrDefault(emptyList())
    }

    fun listInputDevices(context: Context): List<AudioDeviceInfo> {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
            ?: return emptyList()
        return runCatching {
            am.getDevices(AudioManager.GET_DEVICES_INPUTS).toList()
        }.getOrDefault(emptyList())
    }

    /** 把设备描述成人类可读的一行（对齐 Hey! 的 `输出设备 (扬声器)` 面板）。 */
    fun describe(d: AudioDeviceInfo): String {
        val name = d.productName?.toString() ?: "?"
        return "id=${d.id} type=${typeName(d.type)} name=$name"
    }

    /** 设备类型名（部分常量在低版本不存在，做安全降级）。 */
    fun typeName(type: Int): String = when (type) {
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> "听筒"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> "内置扬声器"
        AudioDeviceInfo.TYPE_WIRED_HEADSET -> "有线耳机"
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> "有线耳机"
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> "蓝牙SCO"
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP -> "蓝牙A2DP"
        AudioDeviceInfo.TYPE_HDMI -> "HDMI"
        AudioDeviceInfo.TYPE_USB_DEVICE -> "USB设备"
        AudioDeviceInfo.TYPE_USB_ACCESSORY -> "USB配件"
        AudioDeviceInfo.TYPE_USB_HEADSET -> "USB耳机"
        AudioDeviceInfo.TYPE_TELEPHONY -> "电话"
        AudioDeviceInfo.TYPE_LINE_ANALOG -> "AUX线路"
        AudioDeviceInfo.TYPE_LINE_DIGITAL -> "数字线路"
        AudioDeviceInfo.TYPE_FM -> "FM调谐器"
        AudioDeviceInfo.TYPE_BUS -> "BUS总线"
        AudioDeviceInfo.TYPE_IP -> "IP网络"
        AudioDeviceInfo.TYPE_REMOTE_SUBMIX -> "远程混音"
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE -> "内置扬声器(安全)"
        AudioDeviceInfo.TYPE_BLE_SPEAKER -> "BLE扬声器"
        else -> "type=$type"
    }

    private fun looksExternal(d: AudioDeviceInfo): Boolean {
        val name = (d.productName?.toString() ?: "").lowercase()
        if (EXTERNAL_KEYWORDS.any { it.lowercase() in name }) return true
        return d.type == AudioDeviceInfo.TYPE_BUS ||
            d.type == AudioDeviceInfo.TYPE_LINE_ANALOG
    }

    // ------------------------------------------------------------------
    // 配置解析
    // ------------------------------------------------------------------

    /**
     * 解析车机音频配置，返回「设备名 -> 用途名列表」的映射。
     * 该文件是车规 Android 定义音频路由的权威来源。
     */
    fun parseCarAudioConfig(): Map<String, List<String>> {
        val result = mutableMapOf<String, List<String>>()
        for (path in CONFIG_PATHS) {
            val f = File(path)
            if (!f.exists() || !f.canRead()) continue
            runCatching {
                result.putAll(parseConfigXml(f.readText()))
                if (result.isNotEmpty()) {
                    Log.i(TAG, "已读取车机音频配置: $path (${result.size} 个设备)")
                    return result
                }
            }.onFailure { Log.w(TAG, "解析音频配置失败: $path -> ${it.message}") }
        }
        if (result.isEmpty()) {
            Log.w(TAG, "未找到可读的 car_audio_configuration.xml（可能需要提权）")
        }
        return result
    }

    /**
     * 极简 XML 解析：只取 `<device name="..">` 与其内部的 `<context name=".."/>`。
     * 不引入 XML 依赖，避免 dex 膨胀；结构足够简单，正则可靠。
     *
     * 注意：正则里含双引号，因此**不用** Kotlin 原始字符串（`"""`），
     * 改用普通字符串 + 转义，避免 `"""` 被内容里的 `"` 提前闭合。
     */
    private fun parseConfigXml(xml: String): Map<String, List<String>> {
        val out = mutableMapOf<String, List<String>>()
        val deviceRegex = Regex(
            "<device\\s+[^>]*name\\s*=\\s*\"([^\"]+)\"[^>]*>(.*?)</device>",
            RegexOption.DOT_MATCHES_ALL
        )
        val contextRegex = Regex("<context\\s+[^>]*name\\s*=\\s*\"([^\"]+)\"")
        for (m in deviceRegex.findAll(xml)) {
            val deviceName = m.groupValues[1]
            val contexts = contextRegex.findAll(m.groupValues[2]).map { it.groupValues[1] }.toList()
            out[deviceName] = contexts
        }
        return out
    }

    // ------------------------------------------------------------------
    // 综合定位
    // ------------------------------------------------------------------

    /** 默认 usage（按优先级取第一个）。 */
    fun defaultUsage(): Int = PRIORITY_USAGES.first()

    fun usageName(usage: Int): String =
        CANDIDATE_USAGES.firstOrNull { it.first == usage }?.second ?: usage.toString()

    fun nameOf(usage: Int): String = when (usage) {
        AudioAttributes.USAGE_ASSISTANCE_SONIFICATION -> "ASSIST_SONIFICATION"
        USAGE_SAFETY -> "SAFETY"
        USAGE_ANNOUNCEMENT -> "ANNOUNCEMENT"
        USAGE_EMERGENCY -> "EMERGENCY"
        USAGE_VEHICLE_STATUS -> "VEHICLE_STATUS"
        AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE -> "ASSIST_NAV"
        AudioAttributes.USAGE_UNKNOWN -> "UNKNOWN"
        AudioAttributes.USAGE_MEDIA -> "MEDIA"
        else -> usageName(usage)
    }

    /**
     * 定位车外扬声器。
     *
     * @param preferredUsage 用户/上次探测选定的 usage，优先使用
     * @param preferredDeviceId 用户选定的设备 id，优先使用
     */
    fun resolve(
        context: Context,
        preferredUsage: Int? = null,
        preferredDeviceId: Int? = null,
    ): RouteInfo {
        val outputs = listOutputDevices(context)
        val inputs = listInputDevices(context)
        val described = outputs.map { describe(it) }
        val cfg = parseCarAudioConfig()

        // 路径 0（最高优先）：用户手动指定设备 id
        if (preferredDeviceId != null && preferredDeviceId >= 0) {
            outputs.firstOrNull { it.id == preferredDeviceId }?.let { dev ->
                val u = preferredUsage ?: defaultUsage()
                Log.i(TAG, "使用手动指定设备: ${describe(dev)}")
                return RouteInfo(dev, u, nameOf(u), "manual-pick", cfg, outputs, inputs)
            }
            Log.w(TAG, "手动指定的设备 id=$preferredDeviceId 不在输出列表中，回退自动探测")
        }

        // 路径 1：按设备名匹配
        val external = outputs.firstOrNull { looksExternal(it) }
        if (external != null) {
            val u = preferredUsage ?: defaultUsage()
            Log.i(TAG, "命中车外设备(名称匹配): ${describe(external)}")
            return RouteInfo(external, u, nameOf(u), "device-name", cfg, outputs, inputs)
        }

        // 路径 2：配置里有线索但设备枚举不到（常见于需要提权）
        if (cfg.isNotEmpty()) {
            val extCfg = cfg.keys.firstOrNull { k ->
                EXTERNAL_KEYWORDS.any { it.lowercase() in k.lowercase() }
            }
            if (extCfg != null) {
                Log.i(TAG, "配置中命中车外设备: $extCfg（但无法直接寻址，需 usage 路由）")
                val u = preferredUsage ?: defaultUsage()
                return RouteInfo(null, u, nameOf(u), "config-only", cfg, outputs, inputs)
            }
        }

        val u = preferredUsage ?: defaultUsage()
        Log.w(TAG, "未自动识别车外扬声器，可用输出设备: $described")
        return RouteInfo(null, u, nameOf(u), "fallback", cfg, outputs, inputs)
    }
}
