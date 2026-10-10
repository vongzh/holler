package com.vongzh.holler.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.BufferedReader
import java.io.InputStreamReader

/**
 * 日志关键词触发服务。
 *
 * 对齐 Hey! 的 LogcatTriggerService（Manifest 描述：
 * "Monitors system logcat for keyword to trigger talk"）。
 *
 * 场景：车机在倒车 / 碰撞预警 / 车道偏离时，系统会在 logcat 打特定日志。
 * 抓到关键词 → 自动播报对应喊话，实现「车自己会喊」。
 *
 * ⚠ 权限现实（必须诚实说明）：
 *   读系统 logcat 需要 android.permission.READ_LOGS，
 *   这是 signature|privileged 级权限，**普通 App 申请不生效**。
 *   Hey! 的解法是引导用户 adb 授权，Holler 同样：
 *
 *     1) 有 READ_LOGS 权限（adb 授权过）→ 读全系统日志 ✅
 *     2) 无权限 → 退化为读本应用日志（logcat -s Holler:*）⚠ 有限
 *
 *   adb 授权命令（用户需在 PC 上执行一次）：
 *     adb shell pm grant com.vongzh.holler android.permission.READ_LOGS
 *
 * 实现方式：
 *   用 Runtime.exec 起 `logcat` 子进程（对齐 Hey! 的子进程思路），
 *   逐行读，命中关键词就触发喊话。相比 Logcat.read 类库更轻。
 */
class LogcatTriggerService : Service() {

    companion object {
        private const val TAG = "Holler/Logcat"
        private const val CHANNEL_ID = "holler_logcat"
        private const val NOTIFY_ID = 1003

        @Volatile
        var running = false
            private set

        /**
         * 触发后的冷却时间（秒）。
         *
         * Hey! 固定 8 秒；Holler 做成可配置 —— 不同关键词的日志密度差异很大，
         * 「碰撞」这类事件一次就够，而某些系统日志可能刷屏。
         * 实际取值从 PhraseStore.logcatCooldownSec 读。
         */
        private const val DEFAULT_COOLDOWN_MS = 8000L

        fun start(context: android.content.Context) {
            val i = Intent(context, LogcatTriggerService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            }.onFailure { Log.w(TAG, "启动日志触发服务失败: ${it.message}") }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, LogcatTriggerService::class.java))
        }

        /**
         * 检查是否具备系统日志读取权限。
         * 注意：即使返回 false，服务仍可运行（只是只能看到本应用日志）。
         */
        fun hasLogPermission(context: android.content.Context): Boolean =
            context.checkSelfPermission(android.Manifest.permission.READ_LOGS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

        /** 给用户的授权引导命令（显示在设置页） */
        fun grantCommand(pkg: String) =
            "adb shell pm grant $pkg android.permission.READ_LOGS"
    }

    private lateinit var store: PhraseStore
    private lateinit var engine: HollerEngine
    private lateinit var speaker: PhraseSpeaker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchJob: Job? = null
    private var process: Process? = null

    @Volatile
    private var lastTriggerAt = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = PhraseStore(this)
        engine = HollerEngine.get(this)
        speaker = PhraseSpeaker(this)
        startForegroundSafely()
        startWatch()
        running = true
        Log.i(TAG, "日志关键词触发监听中（关键词=${store.logcatKeyword}）")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    // ------------------------------------------------------------------
    // 日志监听
    // ------------------------------------------------------------------

    private fun startWatch() {
        watchJob?.cancel()
        watchJob = scope.launch {
            // TTS 引擎初始化需要时间，这里后台等待就绪（不阻塞日志监听）
            speaker.init()
            var waited = 0
            while (!speaker.isReady && waited < 5000) {
                kotlinx.coroutines.delay(100)
                waited += 100
            }
            if (speaker.isReady) {
                Log.i(TAG, "TTS 就绪，命中关键词可播报文本")
            } else {
                Log.w(TAG, "TTS 未就绪，命中关键词退化为提示音")
            }

            val allowed = hasLogPermission(this@LogcatTriggerService)
            if (!allowed) {
                Log.w(TAG, "无 READ_LOGS 权限，仅监听本应用日志。请执行: " +
                    grantCommand(packageName))
            }

            // 有权限读全系统日志；无权限则只读本应用（仍能配合自身业务日志）
            val cmd = if (allowed) {
                arrayOf("logcat", "-v", "brief", "*:I", "*:W", "*:E")
            } else {
                arrayOf("logcat", "-v", "brief", "-s", "Holler:*")
            }

            try {
                val p = ProcessBuilder(*cmd).redirectErrorStream(true).start()
                process = p
                BufferedReader(InputStreamReader(p.inputStream)).use { reader ->
                    while (isActive) {
                        val line = reader.readLine() ?: break
                        onLogLine(line)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "日志监听异常: ${e.message}", e)
            } finally {
                runCatching { process?.destroy() }
                process = null
            }
        }
    }

    private fun onLogLine(line: String) {
        val kw = store.logcatKeyword
        if (kw.isBlank()) return
        if (!line.contains(kw, ignoreCase = true)) return

        // 冷却时间可配置（对齐 Hey! 的 8s 默认值）
        val cooldown = (store.logcatCooldownSec * 1000L).coerceAtLeast(DEFAULT_COOLDOWN_MS / 2)
        val now = System.currentTimeMillis()
        if (now - lastTriggerAt < cooldown) {
            Log.d(TAG, "命中但处于冷却期（${store.logcatCooldownSec}s），忽略")
            return
        }
        lastTriggerAt = now

        Log.i(TAG, "命中关键词「$kw」: $line")
        speakTriggered()
    }

    /**
     * 触发喊话：优先播 TTS 文本，其次播提示音。
     * 播报内容取自条目列表中「启动时播放」的那条；
     * 若是倒车类触发，用户通常会把它设成「车辆正在倒车，请保持安全距离」。
     */
    private fun speakTriggered() {
        val items = store.items()
        if (items.isEmpty()) {
            scope.launch { engine.playHintBeep(760.0) }
            return
        }
        val target = items.firstOrNull { it.id == store.launchIndex } ?: items.first()

        // 用服务自身的 scope：onDestroy 时统一取消，不会泄漏
        scope.launch {
            if (speaker.isReady) {
                val pcm = speaker.synthesize(target.text, store.speechRate, store.pitch)
                if (pcm.isNotEmpty()) {
                    engine.playPcm(engine.voiceBridge.process(pcm))
                    Log.i(TAG, "已播报: ${target.text}")
                    return@launch
                }
            }
            engine.playHintBeep(760.0)
        }
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    override fun onDestroy() {
        running = false
        watchJob?.cancel()
        runCatching { process?.destroy() }
        process = null
        scope.cancel()
        speaker.shutdown()
        Log.i(TAG, "日志触发服务已停止")
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        createChannel()
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(this)

        val n: Notification = builder
            .setContentTitle("Holler 日志触发")
            .setContentText("监听「${store.logcatKeyword}」以自动喊话")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(NOTIFY_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            } else {
                startForeground(NOTIFY_ID, n)
            }
        }.onFailure { Log.e(TAG, "startForeground 失败: ${it.message}") }
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            nm.createNotificationChannel(
                NotificationChannel(
                    CHANNEL_ID, "Holler 日志触发", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "监听系统日志关键词自动喊话" }
            )
        }
    }
}
