package com.vongz.holler.audio

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.util.Log
import com.vongz.holler.ui.MainActivity

/**
 * 喊话保活与通知栏快捷控制服务。
 *
 * 核心职责：
 *   1. 前台保活：保持进程前台状态，防止车机休眠策略或省电机制冻结喊话通道；
 *   2. 通知栏快捷控制：车主无需打开应用或使用悬浮窗，从中控顶栏下拉通知中心即可：
 *      - 一键播报当前选中的交通快捷语（如“请关闭远光灯”、“车辆倒车请避让”）；
 *      - 一键点击录音，再次点击停止并发送至车外扬声器；
 *      - 实时感知录音/播报状态，实现零遮挡中控屏幕的原生级车机交互。
 */
class HollerForegroundService : Service(), HollerEngine.Listener {

    companion object {
        private const val TAG = "Holler/Fg"
        private const val CHANNEL_ID = "holler_service"
        private const val NOTIFY_ID = 1001

        const val ACTION_UPDATE = "com.vongz.holler.action.UPDATE_NOTIFICATION"

        /**
         * 当快捷语切换或设置修改时调用，即时刷新通知栏内容
         */
        fun update(context: Context) {
            val intent = Intent(context, HollerForegroundService::class.java).apply {
                action = ACTION_UPDATE
            }
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "刷新通知栏失败: ${it.message}") }
        }
    }

    private lateinit var engine: HollerEngine
    private lateinit var store: PhraseStore
    private var currentStage = HollerEngine.Stage.IDLE

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        engine = HollerEngine.get(this)
        store = PhraseStore(this)
        engine.registerListener(this)

        createChannel()
        val notification = buildNotification()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
            }.onFailure {
                Log.w(TAG, "特殊类型前台启动受限，回退通用前台: ${it.message}")
                startForeground(NOTIFY_ID, notification)
            }
        } else {
            startForeground(NOTIFY_ID, notification)
        }
        Log.i(TAG, "喊话前台通知服务已启动")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_UPDATE) {
            refreshNotification()
        }
        return START_STICKY
    }

    override fun onStageChanged(stage: HollerEngine.Stage) {
        currentStage = stage
        refreshNotification()
    }

    override fun onPhraseResult(message: String) {
        // 可选接收结果
    }

    private fun refreshNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFY_ID, buildNotification())
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (nm.getNotificationChannel(CHANNEL_ID) == null) {
            val ch = NotificationChannel(
                CHANNEL_ID,
                "Holler 车外喊话控制",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "车外喊话快捷操作与前台保活"
                setShowBadge(false)
            }
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotification(): Notification {
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
        }

        // 点击通知主体：打开主界面
        val openIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val contentPending = PendingIntent.getActivity(
            this,
            0,
            openIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.setContentIntent(contentPending)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setShowWhen(false)

        val items = store.items()
        val currentItem = items.firstOrNull { it.id == store.launchIndex } ?: items.firstOrNull()
        val currentPhrase = currentItem?.text ?: "前方施工，请减速慢行"

        if (!store.notifyQuickActions) {
            // 用户关闭了快捷控制，仅保留基础保活通知
            return builder
                .setContentTitle("Holler 车外喊话")
                .setContentText("服务待命中 · 点击打开控制台")
                .build()
        }

        when (currentStage) {
            HollerEngine.Stage.IDLE -> {
                builder.setContentTitle("Holler 车外喊话 · 待命")
                builder.setContentText("快捷语: $currentPhrase")

                // Action 1: 播报当前快捷语
                val playIntent = Intent(HollerCommandReceiver.ACTION_PLAY_PHRASE).apply {
                    setPackage(packageName)
                }
                val playPending = PendingIntent.getBroadcast(
                    this,
                    101,
                    playIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    builder.addAction(
                        Notification.Action.Builder(
                            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_play),
                            "📢 播报快捷语",
                            playPending
                        ).build()
                    )
                }

                // Action 2: 开始录音喊话
                val recordIntent = Intent(HollerCommandReceiver.ACTION_START).apply {
                    setPackage(packageName)
                }
                val recordPending = PendingIntent.getBroadcast(
                    this,
                    102,
                    recordIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    builder.addAction(
                        Notification.Action.Builder(
                            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_btn_speak_now),
                            "🎙️ 开始录音",
                            recordPending
                        ).build()
                    )
                }
            }

            HollerEngine.Stage.RECORDING -> {
                builder.setContentTitle("Holler 车外喊话 · 🔴 正在录音")
                builder.setContentText("请对麦克风说话，点击下方按钮完成并发送")

                // Action: 停止并发送
                val stopIntent = Intent(HollerCommandReceiver.ACTION_STOP).apply {
                    setPackage(packageName)
                }
                val stopPending = PendingIntent.getBroadcast(
                    this,
                    103,
                    stopIntent,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    builder.addAction(
                        Notification.Action.Builder(
                            android.graphics.drawable.Icon.createWithResource(this, android.R.drawable.ic_media_pause),
                            "⏹️ 停止并对外发送",
                            stopPending
                        ).build()
                    )
                }
            }

            HollerEngine.Stage.PLAYING -> {
                builder.setContentTitle("Holler 车外喊话 · 🔊 正在车外播报")
                builder.setContentText("音频正定向输出至车外扬声器...")
            }
        }

        return builder.build()
    }

    override fun onDestroy() {
        engine.unregisterListener(this)
        super.onDestroy()
        Log.i(TAG, "喊话前台通知服务已退出")
    }
}
