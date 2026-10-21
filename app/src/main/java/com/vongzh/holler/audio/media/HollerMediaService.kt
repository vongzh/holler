package com.vongzh.holler.audio.media

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
import androidx.core.app.NotificationCompat
import com.vongzh.holler.ui.MainActivity

/**
 * 车外音乐媒体前台播放服务（Boombox Foreground Media Service）。
 *
 * 职责：
 * 1. 提供常驻媒体通知，支持车机切至后台、分屏导航或熄屏时持续外放音乐；
 * 2. 下拉通知栏提供【上一曲】【播放/暂停】【下一曲】快速车规控制；
 * 3. 监听 HollerMediaEngine 状态并同步通知栏内容。
 */
class HollerMediaService : Service(), HollerMediaEngine.Listener {

    companion object {
        private const val TAG = "Holler/MediaService"
        private const val CHANNEL_ID = "holler_media_playback"
        private const val NOTIFY_ID = 2002

        const val ACTION_PLAY_PAUSE = "com.vongzh.holler.media.PLAY_PAUSE"
        const val ACTION_PREV = "com.vongzh.holler.media.PREV"
        const val ACTION_NEXT = "com.vongzh.holler.media.NEXT"
        const val ACTION_STOP = "com.vongzh.holler.media.STOP"

        fun start(context: Context) {
            val intent = Intent(context, HollerMediaService::class.java)
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            }.onFailure { Log.w(TAG, "启动媒体前台服务失败: ${it.message}") }
        }

        fun stop(context: Context) {
            val intent = Intent(context, HollerMediaService::class.java)
            context.stopService(intent)
        }
    }

    private lateinit var mediaEngine: HollerMediaEngine

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        mediaEngine = HollerMediaEngine.get(this)
        mediaEngine.registerListener(this)

        createNotificationChannel()
        val notification = buildNotification()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            runCatching {
                startForeground(NOTIFY_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK)
            }.onFailure {
                Log.w(TAG, "媒体前台类型启动受限，回退通用前台: ${it.message}")
                startForeground(NOTIFY_ID, notification)
            }
        } else {
            startForeground(NOTIFY_ID, notification)
        }
        Log.i(TAG, "车外媒体前台服务已建立")
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY_PAUSE -> mediaEngine.togglePlayPause()
            ACTION_PREV -> mediaEngine.prevTrack()
            ACTION_NEXT -> mediaEngine.nextTrack()
            ACTION_STOP -> {
                mediaEngine.stop()
                stopSelf()
            }
        }
        refreshNotification()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaEngine.unregisterListener(this)
        Log.i(TAG, "车外媒体前台服务已销毁")
    }

    // ==================================================================
    // 监听回调
    // ==================================================================

    override fun onPlaybackStateChanged(isPlaying: Boolean, track: MediaItem?) {
        refreshNotification()
        if (!isPlaying && track == null) {
            stopSelf()
        }
    }

    override fun onProgressUpdate(positionMs: Long, durationMs: Long) {
        // 进度不打扰通知栏频繁重绘，仅在状态变化时更新
    }

    override fun onPlaylistChanged(tracks: List<MediaItem>) {
        refreshNotification()
    }

    override fun onPlayModeChanged(mode: HollerMediaEngine.PlayMode) {
        // 可选展示
    }

    // ==================================================================
    // 通知栏构建
    // ==================================================================

    private fun refreshNotification() {
        val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager ?: return
        nm.notify(NOTIFY_ID, buildNotification())
    }

    private fun buildNotification(): Notification {
        val track = mediaEngine.currentTrack
        val isPlaying = mediaEngine.isPlaying
        val title = track?.title ?: "车外音响待命中"
        val desc = if (isPlaying) "正在车外扬声器外放 · ${track?.artist ?: ""}" else "车外媒体已暂停"

        val openIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playPauseIntent = PendingIntent.getService(
            this, 1,
            Intent(this, HollerMediaService::class.java).apply { action = ACTION_PLAY_PAUSE },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val prevIntent = PendingIntent.getService(
            this, 2,
            Intent(this, HollerMediaService::class.java).apply { action = ACTION_PREV },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val nextIntent = PendingIntent.getService(
            this, 3,
            Intent(this, HollerMediaService::class.java).apply { action = ACTION_NEXT },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = PendingIntent.getService(
            this, 4,
            Intent(this, HollerMediaService::class.java).apply { action = ACTION_STOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val playActionTitle = if (isPlaying) "暂停" else "播放"

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title)
            .setContentText(desc)
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentIntent(openIntent)
            .setOngoing(isPlaying)
            .addAction(android.R.drawable.ic_media_previous, "上一曲", prevIntent)
            .addAction(if (isPlaying) android.R.drawable.ic_media_pause else android.R.drawable.ic_media_play, playActionTitle, playPauseIntent)
            .addAction(android.R.drawable.ic_media_next, "下一曲", nextIntent)
            .addAction(android.R.drawable.ic_menu_close_clear_cancel, "关闭外放", stopIntent)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .build()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "车外音乐与音效外放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "车外扬声器音乐播放与常驻控制"
                setShowBadge(false)
            }
            val nm = getSystemService(NOTIFICATION_SERVICE) as? NotificationManager
            nm?.createNotificationChannel(channel)
        }
    }
}
