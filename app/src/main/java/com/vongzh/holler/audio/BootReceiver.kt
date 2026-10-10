package com.vongzh.holler.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log

/**
 * 车机开机与唤醒自启接收器。
 *
 * 场景：车机通电、点火冷启动或重启后，自动拉起前台保活与通知栏快捷控制，
 * 确保车主在驾车过程中下拉顶栏即可一键喊话，无需手动点开 App。
 */
class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "Holler/Boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val store = PhraseStore(context)
        if (!store.bootAutoStart) {
            Log.i(TAG, "用户已关闭开机自启，忽略")
            return
        }

        Log.i(TAG, "车机启动完成，拉起 Holler 喊话前台服务")
        val fgIntent = Intent(context, HollerForegroundService::class.java)
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(fgIntent)
            } else {
                context.startService(fgIntent)
            }
        }.onFailure { Log.w(TAG, "自启前台服务失败: ${it.message}") }

        // 若用户开启了桌面悬浮球，且已授予悬浮窗权限，则一并拉起
        if (store.floatingEnabled && android.provider.Settings.canDrawOverlays(context)) {
            FloatingTalkService.start(context)
        }
    }
}
