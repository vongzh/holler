package com.vongzh.holler.audio

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 外部命令接收器。
 *
 * 对齐 Hey! 的 HeyCommandReceiver，三个 action 完全一致：
 *   com.vongzh.holler.action.START_TALK   开始喊话（按下）
 *   com.vongzh.holler.action.STOP_TALK    结束喊话（松开 → 定向外放）
 *   com.vongzh.holler.action.TOGGLE_TALK  切换（按一下开，再按一下关）
 *
 * 用途：
 *   - 方控按键映射（方向盘自定义键 → 发广播）
 *   - 第三方 App / 自动化工具（Tasker、MacroDroid）联动
 *   - adb 手动触发：adb shell am broadcast -a com.vongzh.holler.action.START_TALK
 *
 * 为什么用广播而不是 AIDL：
 *   广播是 Android 最松耦合的跨应用触发方式，
 *   不需要对方集成 SDK，车机上最实用。
 */
class HollerCommandReceiver : BroadcastReceiver() {

    companion object {
        private const val TAG = "Holler/Cmd"

        const val ACTION_START = "com.vongzh.holler.action.START_TALK"
        const val ACTION_STOP = "com.vongzh.holler.action.STOP_TALK"
        const val ACTION_TOGGLE = "com.vongzh.holler.action.TOGGLE_TALK"
        const val ACTION_PLAY_PHRASE = "com.vongzh.holler.action.PLAY_PHRASE"

        /** 携带文本时直接 TTS 播放该文本（可扩展能力） */
        const val EXTRA_TEXT = "text"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return
        Log.i(TAG, "收到命令: $action")

        val engine = HollerEngine.get(context)

        when (action) {
            ACTION_START -> {
                if (!engine.startRecording()) {
                    Log.w(TAG, "START_TALK 失败（可能无权限或正在播放）")
                }
            }

            ACTION_STOP -> {
                if (engine.isRecording) {
                    // 广播接收器生命周期极短（约 10s），必须用 goAsync 保活
                    val pending = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                        try {
                            engine.stopAndPlay()
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }

            ACTION_TOGGLE -> {
                if (engine.isRecording) {
                    val pending = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                        try {
                            engine.stopAndPlay()
                        } finally {
                            pending.finish()
                        }
                    }
                } else {
                    engine.startRecording()
                }
            }

            ACTION_PLAY_PHRASE -> {
                val store = PhraseStore(context)
                val items = store.items()
                val target = items.firstOrNull { it.id == store.launchIndex } ?: items.firstOrNull()
                if (target != null) {
                    val pending = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                        try {
                            val speaker = PhraseSpeaker(context)
                            speaker.init()
                            var waited = 0
                            while (!speaker.isReady && waited < 3000) {
                                kotlinx.coroutines.delay(100)
                                waited += 100
                            }
                            val pcm = speaker.synthesize(target.text, store.speechRate, store.pitch)
                            if (pcm.isNotEmpty()) {
                                engine.playPcm(engine.voiceBridge.process(pcm))
                            } else {
                                engine.playHintBeep(720.0)
                            }
                            speaker.shutdown()
                        } finally {
                            pending.finish()
                        }
                    }
                } else {
                    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                        engine.playHintBeep(720.0)
                    }
                }
            }

            else -> {
                // 支持直接携带文本朗读
                val text = intent.getStringExtra(EXTRA_TEXT)
                if (!text.isNullOrBlank()) {
                    val pending = goAsync()
                    CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
                        try {
                            // 复用引擎单例的变声桥，与手动喊话听觉一致
                            val speaker = PhraseSpeaker(context)
                            speaker.init()
                            // 等待 TTS 就绪（最多 3 秒）
                            var waited = 0
                            while (!speaker.isReady && waited < 3000) {
                                kotlinx.coroutines.delay(100)
                                waited += 100
                            }
                            val pcm = speaker.synthesize(text, 1.0f, 1.0f)
                            if (pcm.isNotEmpty()) {
                                engine.playPcm(engine.voiceBridge.process(pcm))
                            } else {
                                engine.playHintBeep(720.0)
                            }
                            speaker.shutdown()
                        } finally {
                            pending.finish()
                        }
                    }
                }
            }
        }
    }
}
