package com.vongz.holler.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import android.view.Gravity
import android.view.WindowManager
import com.vongz.holler.audio.dsp.VoiceEffect
import com.vongz.holler.ui.FloatingTalkView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * 悬浮喊话按钮服务。
 *
 * 对齐 Hey! 的 FloatingWindowService（Manifest 描述：
 * "Floating overlay talk button for quick access"）。
 *
 * 交互：
 *   单击        → 播放「启动时设定的快捷语」
 *   长按        → 按住说话（松开即定向外放）
 *   拖动        → 移动按钮，松手吸附边缘
 *   双击        → 收起 / 展开（收起后为半透明小点，避免遮挡）
 *
 * 位置、透明度、尺寸持久化在 PhraseStore 的 floating_* 配置中。
 */
class FloatingTalkService : Service(), HollerEngine.Listener {

    companion object {
        private const val TAG = "Holler/Floating"
        private const val CHANNEL_ID = "holler_floating"
        private const val NOTIFY_ID = 1002
        private const val ACTION_STOP = "com.vongz.holler.action.STOP_FLOATING"
        private const val ACTION_REFRESH = "com.vongz.holler.action.REFRESH_FLOATING"

        @Volatile
        var running = false
            private set

        fun start(context: android.content.Context) {
            val i = Intent(context, FloatingTalkService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(i)
                } else {
                    context.startService(i)
                }
            }.onFailure { Log.w(TAG, "启动悬浮服务失败: ${it.message}") }
        }

        fun stop(context: android.content.Context) {
            context.stopService(Intent(context, FloatingTalkService::class.java))
        }

        /**
         * 让运行中的悬浮窗重新读取 store 里的外观参数（尺寸 / 透明度 / 变声标记）。
         * 服务未运行时是空操作。
         */
        fun refreshAppearance(context: android.content.Context) {
            runCatching {
                context.startService(
                    Intent(context, FloatingTalkService::class.java).setAction(ACTION_REFRESH)
                )
            }.onFailure { Log.w(TAG, "刷新悬浮窗失败: ${it.message}") }
        }
    }

    private lateinit var wm: WindowManager
    private var view: FloatingTalkView? = null
    private var lp: WindowManager.LayoutParams? = null
    private lateinit var store: PhraseStore
    private lateinit var engine: HollerEngine
    private lateinit var speaker: PhraseSpeaker

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var levelJob: Job? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        store = PhraseStore(this)
        engine = HollerEngine.get(this)
        speaker = PhraseSpeaker(this)
        wm = getSystemService(WINDOW_SERVICE) as WindowManager

        if (!Settings.canDrawOverlays(this)) {
            Log.e(TAG, "无悬浮窗权限，服务退出")
            stopSelf()
            return
        }

        startForegroundSafely()
        engine.listener = this
        addOverlay()
        running = true
        // TTS 初始化放到后台，不阻塞悬浮按钮显示
        scope.launch { speaker.init() }
        Log.i(TAG, "悬浮喊话按钮已启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                stopSelf()
                return START_NOT_STICKY
            }
            ACTION_REFRESH -> {
                // 主界面刚改完外观参数，重新读取并应用（尺寸变化需重建 LayoutParams）
                applyAppearance()
                return START_STICKY
            }
        }
        // 服务已在运行时再次 startService（如换变声效果），刷新外观即可
        if (view != null) {
            engine.setVoiceEffect(store.voiceEffect)
            engine.voiceBridge.gain = store.volumeGain
            engine.voiceBridge.agcEnabled = store.agcEnabled
            applyAppearance()
        }
        return START_STICKY
    }

    /**
     * 应用引擎配置（变声 / 增益 / AGC）。
     * 主界面改设置后调用，让悬浮窗立刻生效，无需重启服务。
     */
    fun applyEngineConfig() {
        engine.setVoiceEffect(store.voiceEffect)
        engine.voiceBridge.gain = store.volumeGain
        engine.voiceBridge.agcEnabled = store.agcEnabled
        applyAppearance()
    }

    /**
     * 应用外观参数（尺寸 / 透明度 / 深浅主题）。
     * 尺寸变化需要改 LayoutParams 的宽高，不能只刷 alpha。
     */
    private fun applyAppearance() {
        val v = view ?: return
        val params = lp ?: return
        val size = store.floatingSize
        val s = if (v.collapsed) (size * 0.5f).toInt() else size
        params.width = s
        params.height = s
        params.alpha = store.floatingAlpha
        v.effect = store.voiceEffect

        val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        v.isDarkTheme = when (store.themeMode) {
            1 -> false
            2 -> true
            else -> isSystemDark
        }

        runCatching { wm.updateViewLayout(v, params) }
        Log.i(TAG, "悬浮窗外观已更新: size=$size alpha=${store.floatingAlpha}")
    }

    // ------------------------------------------------------------------
    // 悬浮窗
    // ------------------------------------------------------------------

    private fun addOverlay() {
        val size = store.floatingSize
        val v = FloatingTalkView(this)
        val params = WindowManager.LayoutParams(
            size, size,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            else
                @Suppress("DEPRECATION") WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = store.floatingX
            y = store.floatingY
            alpha = store.floatingAlpha
        }

        val isSystemDark = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
        v.isDarkTheme = when (store.themeMode) {
            1 -> false
            2 -> true
            else -> isSystemDark
        }
        v.effect = store.voiceEffect
        v.onLongPressStart = { startTalk() }
        v.onLongPressEnd = { endTalk() }
        v.onTap = { speakLaunchPhrase() }
        v.onDoubleTap = {
            v.collapsed = !v.collapsed
            // 收起后缩小占用面积，减少遮挡
            val s = if (v.collapsed) (size * 0.5f).toInt() else size
            params.width = s
            params.height = s
            runCatching { wm.updateViewLayout(v, params) }
        }
        v.onDragEnd = { x, y ->
            // 吸附到最近的左右边缘
            val screenW = resources.displayMetrics.widthPixels
            val snapX = if (x + size / 2 < screenW / 2) 8 else screenW - size - 8
            params.x = snapX
            params.y = y.coerceAtLeast(8)
            runCatching { wm.updateViewLayout(v, params) }
            store.floatingX = params.x
            store.floatingY = params.y
        }

        runCatching {
            wm.addView(v, params)
            view = v
            lp = params
        }.onFailure {
            Log.e(TAG, "添加悬浮窗失败: ${it.message}", it)
            stopSelf()
        }
    }

    // ------------------------------------------------------------------
    // 喊话动作
    // ------------------------------------------------------------------

    private fun startTalk() {
        if (engine.startRecording()) {
            Log.i(TAG, "悬浮键：开始录音")
        }
    }

    private fun endTalk() {
        if (!engine.isRecording) return
        scope.launch {
            engine.stopAndPlay()
        }
    }

    /** 单击：播放设定的快捷语（无设定则播放第一条）。 */
    private fun speakLaunchPhrase() {
        scope.launch {
            val items = store.items()
            if (items.isEmpty()) {
                engine.playHintBeep(660.0)
                return@launch
            }
            val idx = store.launchIndex
            val phrase = items.firstOrNull { it.id == idx } ?: items.first()

            // 优先 TTS 合成；不可用则用提示音示意
            val pcm = if (speaker.isReady) {
                speaker.synthesize(phrase.text, store.speechRate, store.pitch)
            } else {
                ByteArray(0)
            }
            if (pcm.isNotEmpty() && store.voiceEffect != VoiceEffect.NATIVE) {
                // 快捷键播报也套用当前变声效果，与按住说话保持一致
                engine.playPcm(engine.voiceBridge.process(pcm))
            } else if (pcm.isNotEmpty()) {
                engine.playPcm(pcm)
            } else {
                engine.playHintBeep(660.0)
            }
        }
    }

    private fun startLevelPolling() {
        levelJob?.cancel()
        levelJob = scope.launch {
            val v = view ?: return@launch
            while (engine.isRecording) {
                v.level = engine.peakLevel
                delay(70)
            }
            v.level = 0
        }
    }

    // ------------------------------------------------------------------
    // 引擎状态回调
    // ------------------------------------------------------------------

    override fun onStageChanged(stage: HollerEngine.Stage) {
        view?.state = when (stage) {
            HollerEngine.Stage.IDLE -> 0
            HollerEngine.Stage.RECORDING -> 1
            HollerEngine.Stage.PLAYING -> 2
        }
        if (stage == HollerEngine.Stage.RECORDING) startLevelPolling() else levelJob?.cancel()
        applyAppearance()
    }

    override fun onPhraseResult(message: String) {
        Log.i(TAG, "喊话结果: $message")
    }

    // ------------------------------------------------------------------
    // 生命周期
    // ------------------------------------------------------------------

    override fun onDestroy() {
        running = false
        if (engine.listener === this) engine.listener = null
        levelJob?.cancel()
        view?.let { runCatching { wm.removeView(it) } }
        view = null
        scope.cancel()
        speaker.shutdown()
        Log.i(TAG, "悬浮服务已停止")
        super.onDestroy()
    }

    private fun startForegroundSafely() {
        createChannel()
        val pi = PendingIntent.getService(
            this, 0,
            Intent(this, FloatingTalkService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CHANNEL_ID) else @Suppress("DEPRECATION") Notification.Builder(this)

        val n = builder
            .setContentTitle("Holler 悬浮喊话")
            .setContentText("悬浮按钮运行中，点按可收起通知")
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .addAction(Notification.Action.Builder(null, "停止", pi).build())
            .build()

        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                    NOTIFY_ID, n,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                )
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
                    CHANNEL_ID, "Holler 悬浮喊话", NotificationManager.IMPORTANCE_LOW
                ).apply { description = "悬浮喊话按钮常驻提示" }
            )
        }
    }
}
