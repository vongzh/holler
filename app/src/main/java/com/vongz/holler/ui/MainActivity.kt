package com.vongz.holler.ui

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vongz.holler.audio.AudioRouteResolver
import com.vongz.holler.audio.FloatingTalkService
import com.vongz.holler.audio.HollerCommandReceiver
import com.vongz.holler.audio.HollerEngine
import com.vongz.holler.audio.HollerForegroundService
import com.vongz.holler.audio.LogcatTriggerService
import com.vongz.holler.audio.PhraseSpeaker
import com.vongz.holler.audio.PhraseStore
import com.vongz.holler.audio.UsageSweeper
import com.vongz.holler.audio.dsp.VoiceEffect
import com.vongz.holler.ui.theme.HollerShapes
import com.vongz.holler.ui.theme.HollerSpacing
import com.vongz.holler.ui.theme.HollerTheme
import com.vongz.holler.ui.theme.HollerType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Holler 主界面。
 *
 * 结构（三页导航 + 常驻顶栏 + 底部 DOCK）：
 *   喊话页  —— 按住说话 + 变声效果 + 增益（对标 Hey! 的主交互）
 *   语音页  —— TTS 快捷语条目管理（对标 Hey! 的 tts_items）
 *   设置页  —— 悬浮窗 / 日志触发 / 音频路由诊断（对标 Hey! 的服务能力）
 *
 * 视觉：严格沿用 Flyme Auto 令牌（Theme.kt），控件来自 Controls.kt。
 * 引擎：全局单例（HollerEngine.get），与服务侧共享同一状态。
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        val prefs = applicationContext.getSharedPreferences("holler_phrases", android.content.Context.MODE_PRIVATE)
        
        setContent {
            var themeMode by remember { androidx.compose.runtime.mutableStateOf(prefs.getInt("theme_mode", 0)) }
            DisposableEffect(prefs) {
                val listener = android.content.SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
                    if (key == "theme_mode") themeMode = prefs.getInt("theme_mode", 0)
                }
                prefs.registerOnSharedPreferenceChangeListener(listener)
                onDispose { prefs.unregisterOnSharedPreferenceChangeListener(listener) }
            }
            HollerTheme(themeMode = themeMode) { 
                HollerApp() 
            } 
        }
        // 前台保活服务：保证喊话随时可用
        runCatching {
            startForegroundService(Intent(this, HollerForegroundService::class.java))
        }.onFailure { Log.w("Holler", "前台服务启动失败: ${it.message}") }
    }
}

/** 页面枚举（替代旧的 dockPage Int，语义更清晰）。 */
private enum class Page(val label: String) {
    TALK("喊话"),
    PHRASE("语音"),
    SETTINGS("设置"),
}

@Composable
fun HollerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val engine = remember { HollerEngine.get(context) }
    val store = remember { PhraseStore(context) }
    val speaker = remember { PhraseSpeaker(context) }

    var page by remember { mutableStateOf(Page.TALK) }
    var stage by remember { mutableStateOf(HollerEngine.Stage.IDLE) }
    var route by remember { mutableStateOf<AudioRouteResolver.RouteInfo?>(null) }
    var level by remember { mutableStateOf(0) }
    var elapsed by remember { mutableStateOf(0f) }
    var hint by remember { mutableStateOf("准备就绪") }

    // ------ 引擎配置回灌（把持久化设置同步进引擎） ------
    fun pushEngineConfig() {
        engine.setVoiceEffect(store.voiceEffect)
        engine.voiceBridge.gain = store.volumeGain
        engine.voiceBridge.agcEnabled = store.agcEnabled
    }

    /** 重新解析音频路由：手动指定优先，其次自动探测。副作用已含 UI 状态与引擎同步。 */
    fun refreshRouting(context: Context) {
        val r = AudioRouteResolver.resolve(
            context,
            preferredUsage = store.usageOverride.takeIf { it >= 0 },
            preferredDeviceId = store.outputDeviceId.takeIf { it >= 0 },
        )
        route = r
        engine.targetDevice = r.device
        engine.usage = r.usage
        // 录音设备：手动指定的独立解析（resolve 只管输出）
        engine.inputDevice = engine.findInputDevice(store.inputDeviceId)
    }

    val micPerm = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        hint = if (granted) "麦克风已授权，按住开始喊话" else "需要麦克风权限才能喊话"
    }

    // TTS 初始化（异步，不阻塞界面）
    LaunchedEffect(Unit) { speaker.init() }

    // 启动时恢复配置 + 探测一次音频路由
    LaunchedEffect(Unit) {
        pushEngineConfig()
        withContext(Dispatchers.Default) { refreshRouting(context) }
        val r = route
        hint = if (r?.device != null) {
            "已定位车外扬声器（${r.source}）"
        } else if (store.outputDeviceId >= 0) {
            "指定的设备 id=${store.outputDeviceId} 不存在，请重新选择"
        } else {
            "未识别车外喇叭，走默认输出"
        }

        // 启动自动播报（对齐 Hey! 的 launch_auto_play）
        // 延迟 1.2s 等音频通道就绪，避免首次播放被吞掉
        if (store.launchAutoPlay) {
            delay(1200)
            val items = store.items()
            val target = items.firstOrNull { it.id == store.launchIndex } ?: items.firstOrNull()
            if (target != null) {
                if (!speaker.isReady) speaker.init()
                var waited = 0
                while (!speaker.isReady && waited < 3000) {
                    delay(100)
                    waited += 100
                }
                val pcm = if (speaker.isReady) {
                    speaker.synthesize(target.text, store.speechRate, store.pitch)
                } else {
                    ByteArray(0)
                }
                if (pcm.isNotEmpty()) {
                    hint = "启动自动播报：${target.text}"
                    engine.playPcm(
                        if (store.voiceEffect != VoiceEffect.NATIVE) engine.voiceBridge.process(pcm)
                        else pcm
                    )
                } else {
                    hint = "启动自动播报失败（TTS 未就绪）"
                }
            }
        }
    }

    // 引擎状态回调 → 界面：用 Listener 替代手动回写，避免状态不一致
    DisposableEffect(engine) {
        val l = object : HollerEngine.Listener {
            // 参数名沿用接口的旧写法 s：改成 stage 会 shadow 外层的 stage 状态，
            // 而匿名对象里没有属性可承接，反而更糟。
            @Suppress("PARAMETER_NAME_CHANGED_ON_OVERRIDE")
            override fun onStageChanged(s: HollerEngine.Stage) { stage = s }
            override fun onPhraseResult(message: String) { hint = message }
        }
        engine.registerListener(l)
        onDispose { engine.unregisterListener(l) }
    }

    // 电平 + 倒计时轮询驱动声波动画
    LaunchedEffect(stage) {
        when (stage) {
            HollerEngine.Stage.RECORDING -> {
                while (engine.isRecording) {
                    level = engine.peakLevel
                    elapsed = engine.elapsedSeconds
                    delay(60)
                }
                elapsed = 0f
            }
            HollerEngine.Stage.PLAYING -> {
                while (engine.isPlaying) {
                    level = (30..85).random()
                    delay(80)
                }
            }
            else -> {
                level = 0
                elapsed = 0f
            }
        }
    }

    Surface(color = HollerTheme.colors.BgBase, modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(HollerSpacing.Page),
            verticalArrangement = Arrangement.spacedBy(HollerSpacing.CardGap),
        ) {
            TopBar(stage)

            // 页面内容区：占据剩余空间
            Box(modifier = Modifier.weight(1f)) {
                when (page) {
                    Page.TALK -> TalkPage(
                        engine = engine,
                        stage = stage,
                        level = level,
                        elapsed = elapsed,
                        hint = hint,
                        store = store,
                        onHint = { hint = it },
                        onConfigChanged = { pushEngineConfig() },
                        onRequestMic = { micPerm.launch(Manifest.permission.RECORD_AUDIO) },
                    )
                    Page.PHRASE -> PhrasePage(
                        store = store,
                        speaker = speaker,
                        engine = engine,
                        onHint = { hint = it },
                    )
                    Page.SETTINGS -> SettingsPage(
                        context = context,
                        store = store,
                        engine = engine,
                        route = route,
                        onReresolve = { refreshRouting(context) },
                        onHint = { hint = it },
                    )
                }
            }

            DockBar(page = page, onPage = { page = it })
        }
    }
}

// ======================================================================
// 顶栏
// ======================================================================

@Composable
private fun TopBar(stage: HollerEngine.Stage) {
    val (dotColor, label) = when (stage) {
        HollerEngine.Stage.IDLE -> HollerTheme.colors.TextSecondary to "待命"
        HollerEngine.Stage.RECORDING -> HollerTheme.colors.Danger to "喊话中"
        HollerEngine.Stage.PLAYING -> HollerTheme.colors.Accent to "播报中"
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "Holler",
            color = HollerTheme.colors.TextPrimary,
            fontSize = HollerType.Title,
            fontWeight = FontWeight.Bold,
        )
        // 状态点常驻顶栏：任何页面都能看到当前是否在喊话
        StatusPill(color = dotColor, label = label)
    }
}

// ======================================================================
// 页面一：喊话
// ======================================================================

@Composable
private fun TalkPage(
    engine: HollerEngine,
    stage: HollerEngine.Stage,
    level: Int,
    elapsed: Float,
    hint: String,
    store: PhraseStore,
    onHint: (String) -> Unit,
    onConfigChanged: () -> Unit,
    onRequestMic: () -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var effect by remember { mutableStateOf(store.voiceEffect) }
    var gain by remember { mutableStateOf(store.volumeGain) }
    var agc by remember { mutableStateOf(store.agcEnabled) }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(HollerSpacing.CardGap),
    ) {
        // --- 主卡片：按住说话 ---
        TalkCard(
            stage = stage,
            level = level,
            elapsed = elapsed,
            hint = hint,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            onPress = press@{
                if (!hasMic(context)) {
                    onRequestMic()
                    return@press
                }
                if (engine.startRecording()) {
                    onHint("正在喊话…松开发送")
                } else {
                    onHint("录音启动失败，请检查权限")
                }
            },
            onRelease = release@{
                if (stage != HollerEngine.Stage.RECORDING) return@release
                onHint("正在处理并播报…")
                scope.launch {
                    val n = engine.stopAndPlay()
                    onHint(
                        if (n > 0) "已播报（${n / 2 / 16} 毫秒）"
                        else "录音太短，已忽略"
                    )
                }
            },
            onCancel = {
                engine.cancelRecording()
                onHint("已取消")
            },
        )

        // --- 变声效果（对齐 Hey! 的 5 种）---
        HollerCard(title = "变声效果", subtitle = effect.label) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                VoiceEffect.entries.forEach { e ->
                    HollerChip(
                        label = e.label,
                        selected = e == effect,
                        modifier = Modifier.weight(1f),
                    ) {
                        effect = e
                        store.voiceEffect = e
                        engine.setVoiceEffect(e)
                        onConfigChanged()
                        onHint(e.desc)
                    }
                }
            }
        }

        // --- 增益 / AGC ---
        HollerCard(title = "音量与增益") {
            HollerSlider(
                value = gain,
                onValueChange = {
                    gain = it
                    store.volumeGain = it
                    engine.voiceBridge.gain = it
                },
                range = 0.2f..2.0f,
                valueLabel = "${(gain * 100).toInt()}%",
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("自动增益 (AGC)", color = HollerTheme.colors.TextPrimary, fontSize = HollerType.Body)
                    Text(
                        "对齐 Hey! 参数 target=0.45 attack=0.15",
                        color = HollerTheme.colors.TextSecondary,
                        fontSize = HollerType.Caption,
                    )
                }
                HollerSwitch(
                    checked = agc,
                    onCheckedChange = {
                        agc = it
                        store.agcEnabled = it
                        engine.voiceBridge.agcEnabled = it
                    },
                )
            }
        }
    }
}

/** 主卡片：按住喊话。按下缩放 + 描边变色，全部用 Flyme Auto 的克制动效。 */
@Composable
private fun TalkCard(
    stage: HollerEngine.Stage,
    level: Int,
    elapsed: Float,
    hint: String,
    modifier: Modifier = Modifier,
    onPress: () -> Unit,
    onRelease: () -> Unit,
    onCancel: () -> Unit,
) {
    val active = stage == HollerEngine.Stage.RECORDING
    val playing = stage == HollerEngine.Stage.PLAYING

    val scale by animateFloatAsState(
        targetValue = if (active) 0.985f else 1f,
        animationSpec = spring(dampingRatio = 0.55f, stiffness = 700f),
        label = "cardScale",
    )
    val borderColor by animateColorAsState(
        targetValue = when {
            active -> HollerTheme.colors.Danger
            playing -> HollerTheme.colors.Accent
            else -> HollerTheme.colors.Stroke
        },
        label = "border",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .scale(scale)
            .clip(HollerShapes.Card)
            .background(
                Brush.verticalGradient(
                    listOf(HollerTheme.colors.BrandElevated, HollerTheme.colors.Brand)
                )
            )
            .border(BorderStroke(1.dp, borderColor), HollerShapes.Card)
            .pointerInput(stage) {
                detectTapGestures(
                    onPress = {
                        onPress()
                        val released = tryAwaitRelease()
                        if (released) onRelease() else onCancel()
                    }
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            ) {
            // 声波与倒计时环叠放：环在外圈指示剩余时间，波在中心指示音量
            Box(contentAlignment = Alignment.Center) {
                CountdownRing(
                    elapsedSeconds = elapsed,
                    maxSeconds = HollerEngine.MAX_SECONDS,
                    active = active,
                    modifier = Modifier.size(212.dp),
                )
                WaveformView(
                    level = level,
                    active = active,
                    playing = playing,
                    modifier = Modifier.size(148.dp),
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                when (stage) {
                    HollerEngine.Stage.IDLE -> "按住喊话"
                    HollerEngine.Stage.RECORDING -> "松开发送"
                    HollerEngine.Stage.PLAYING -> "播报中"
                },
                color = HollerTheme.colors.TextPrimary,
                fontSize = 32.sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                hint,
                color = HollerTheme.colors.TextSecondary,
                fontSize = HollerType.Body,
                textAlign = TextAlign.Center,
            )
        }
    }
}

// ======================================================================
// 页面二：语音（TTS 快捷语）
// ======================================================================

@Composable
private fun PhrasePage(
    store: PhraseStore,
    speaker: PhraseSpeaker,
    engine: HollerEngine,
    onHint: (String) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var phrases by remember { mutableStateOf(store.items()) }
    var editing by remember { mutableStateOf<PhraseStore.Item?>(null) }
    var rate by remember { mutableStateOf(store.speechRate) }
    var pitch by remember { mutableStateOf(store.pitch) }

    /** 试听：TTS 合成 → 定向播放（走车外喇叭）。 */
    fun preview(text: String) {
        onHint("合成中：$text")
        scope.launch {
            if (!speaker.isReady) speaker.init()
            var waited = 0
            while (!speaker.isReady && waited < 3000) {
                delay(100)
                waited += 100
            }
            if (!speaker.isReady) {
                onHint("TTS 未就绪，改用提示音")
                engine.playHintBeep(700.0)
                return@launch
            }
            val pcm = speaker.synthesize(text, rate, pitch)
            if (pcm.isEmpty()) {
                onHint("合成失败，检查系统 TTS 引擎")
                return@launch
            }
            engine.playPcm(pcm)
            onHint("已播放：$text")
        }
    }

    /** 新增 / 重命名用同一个弹层，这里做成内联编辑行。 */
    var draft by remember { mutableStateOf("") }
    var renamingId by remember { mutableStateOf(-1L) }

    fun commitDraft() {
        val t = draft.trim()
        if (t.isEmpty()) {
            onHint("内容为空，已忽略")
            return
        }
        if (renamingId >= 0) {
            store.rename(renamingId, t)
            onHint("已修改")
        } else {
            store.add(t)
            onHint("已添加")
        }
        phrases = store.items()
        draft = ""
        renamingId = -1L
        HollerForegroundService.update(context)
    }

    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(HollerSpacing.CardGap),
    ) {
        // --- 参数卡片 ---
        HollerCard(title = "播报参数", subtitle = "${store.items().size} 条快捷语") {
            HollerSlider(
                value = rate,
                onValueChange = { rate = it; store.speechRate = it },
                range = 0.5f..2.0f,
                valueLabel = "语速 ${"%.1f".format(rate)}",
            )
            Spacer(Modifier.height(6.dp))
            HollerSlider(
                value = pitch,
                onValueChange = { pitch = it; store.pitch = it },
                range = 0.5f..2.0f,
                valueLabel = "音调 ${"%.1f".format(pitch)}",
            )
        }

        // --- 编辑框 ---
        HollerCard(title = if (renamingId >= 0) "修改快捷语" else "新增快捷语") {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .height(46.dp)
                        .clip(HollerShapes.Control)
                        .background(HollerTheme.colors.BgBase.copy(alpha = 0.5f))
                        .border(BorderStroke(1.dp, HollerTheme.colors.Stroke), HollerShapes.Control)
                        .padding(horizontal = 14.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    // 用系统输入法弹窗编辑（车机上可用语音/手写输入）
                    Text(
                        if (draft.isEmpty()) "点此输入喊话内容…" else draft,
                        color = if (draft.isEmpty()) HollerTheme.colors.TextSecondary else HollerTheme.colors.TextPrimary,
                        fontSize = HollerType.Body,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier
                            .fillMaxWidth()
                            .pointerInput(Unit) {
                                detectTapGestures { editing = PhraseStore.Item(-1L, "") }
                            },
                    )
                }
                HollerButton(if (renamingId >= 0) "保存" else "添加", onClick = { commitDraft() })
                if (renamingId >= 0) {
                    HollerButton("取消", primary = false, onClick = {
                        renamingId = -1L
                        draft = ""
                    })
                }
            }
        }

        // --- 条目列表 ---
        HollerCard(
            title = "快捷语列表",
            subtitle = "点条目播放，长按设置",
            modifier = Modifier.weight(1f),
        ) {
            if (phrases.isEmpty()) {
                EmptyHint("暂无快捷语，请先添加")
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(phrases, key = { it.id }) { item ->
                        PhraseRow(
                            item = item,
                            isLaunch = store.launchIndex == item.id,
                            onPlay = { preview(item.text) },
                            onEdit = {
                                renamingId = item.id
                                draft = item.text
                            },
                            onLaunch = {
                                store.launchIndex = item.id
                                phrases = store.items()
                                HollerForegroundService.update(context)
                                onHint("已设为快捷播放：${item.text}")
                            },
                            onDelete = {
                                store.remove(item.id)
                                phrases = store.items()
                                HollerForegroundService.update(context)
                                onHint("已删除")
                            },
                        )
                    }
                }
            }
        }

        // --- 底部动作 ---
        Row(horizontalArrangement = Arrangement.spacedBy(HollerSpacing.CardGap)) {
            HollerButton(
                "恢复预置语料",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    phrases = store.reset()
                    HollerForegroundService.update(context)
                    onHint("已恢复 ${phrases.size} 条预置语料")
                },
            )
            HollerButton(
                "测试音",
                primary = false,
                modifier = Modifier.weight(1f),
                onClick = {
                    onHint("播放测试音，请听声音从哪出")
                    scope.launch { engine.playTone() }
                },
            )
        }
    }

    // 输入弹窗：用系统对话框承载文本输入（车机兼容性最好）
    editing?.let { _ ->
        TextInputDialog(
            initial = draft,
            onDismiss = { editing = null },
            onConfirm = {
                draft = it
                editing = null
                commitDraft()
            },
        )
    }
}

@Composable
private fun PhraseRow(
    item: PhraseStore.Item,
    isLaunch: Boolean,
    onPlay: () -> Unit,
    onEdit: () -> Unit,
    onLaunch: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HollerShapes.Control)
            .background(
                if (isLaunch) HollerTheme.colors.AccentSoft else HollerTheme.colors.BrandElevated.copy(alpha = 0.5f)
            )
            .border(
                BorderStroke(1.dp, if (isLaunch) HollerTheme.colors.Accent else HollerTheme.colors.Stroke),
                HollerShapes.Control,
            )
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                item.text,
                color = HollerTheme.colors.TextPrimary,
                fontSize = HollerType.Body,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (isLaunch) {
                Text(
                    "快捷播放（悬浮键单击 / 日志触发）",
                    color = HollerTheme.colors.Accent,
                    fontSize = HollerType.Caption,
                )
            }
        }
        MiniAction("▶", onPlay)
        MiniAction("改", onEdit)
        MiniAction(if (isLaunch) "★" else "☆", onLaunch)
        MiniAction("×", onDelete)
    }
}

/** 列表行内的小动作按钮（方块 + 字符，车机上易点）。 */
@Composable
private fun MiniAction(label: String, onClick: () -> Unit) {
    // 两字以上（如「选用 / 已选」）需要更宽的触控区，单字（▶ 改 ★ ×）保持方块
    val wide = label.length > 1
    Box(
        modifier = Modifier
            .then(
                if (wide) Modifier.width(56.dp).height(34.dp)
                else Modifier.size(34.dp)
            )
            .clip(HollerShapes.Control)
            .background(HollerTheme.colors.BgBase.copy(alpha = 0.45f))
            .border(BorderStroke(1.dp, HollerTheme.colors.Stroke), HollerShapes.Control)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(label, color = HollerTheme.colors.TextPrimary, fontSize = HollerType.Body)
    }
}

// ======================================================================
// 页面三：设置
// ======================================================================

@Composable
private fun SettingsPage(
    context: Context,
    store: PhraseStore,
    engine: HollerEngine,
    route: AudioRouteResolver.RouteInfo?,
    onReresolve: () -> Unit,
    onHint: (String) -> Unit,
) {
    val scope = rememberCoroutineScope()

    var floating by remember { mutableStateOf(FloatingTalkService.running) }
    var logcat by remember { mutableStateOf(LogcatTriggerService.running) }
    var keyword by remember { mutableStateOf(store.logcatKeyword) }
    var cooldown by remember { mutableStateOf(store.logcatCooldownSec.toFloat()) }
    var sweepRunning by remember { mutableStateOf(false) }
    var sweepResults by remember { mutableStateOf<List<UsageSweeper.Result>>(emptyList()) }
    var sweepIndex by remember { mutableStateOf(0) }
    var themeModeState by remember { androidx.compose.runtime.mutableIntStateOf(store.themeMode) }

    // 悬浮窗外观（对齐 Hey! 的 floating_alpha / floating_size）
    var alpha by remember { mutableStateOf(store.floatingAlpha) }
    var floatSize by remember { mutableStateOf(store.floatingSize.toFloat()) }

    // 通知栏与自启配置
    var notifyQuick by remember { mutableStateOf(store.notifyQuickActions) }
    var bootAuto by remember { mutableStateOf(store.bootAutoStart) }

    // 需要文本输入的对话框（关键词自定义 / 设备 id 手填）
    var dialogKind by remember { mutableStateOf(DialogKind.NONE) }

    val storagePerm = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        val ok = Settings.canDrawOverlays(context)
        if (ok) {
            store.floatingEnabled = true
            FloatingTalkService.start(context)
            floating = true
            onHint("悬浮按钮已启动")
        } else {
            floating = false
            store.floatingEnabled = false
            onHint("未授予悬浮窗权限")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(HollerSpacing.CardGap),
    ) {
        // ---------------- 外观与主题 ----------------
        item {
            HollerCard(title = "外观与主题") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("界面模式", color = HollerTheme.colors.TextPrimary, fontSize = HollerType.Body)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("跟随系统", "浅色", "深色").forEachIndexed { index, label ->
                            val selected = themeModeState == index
                            Box(
                                modifier = Modifier
                                    .clip(HollerShapes.Control)
                                    .background(if (selected) HollerTheme.colors.AccentSoft else Color.Transparent)
                                    .border(1.dp, if (selected) HollerTheme.colors.Accent else HollerTheme.colors.Stroke, HollerShapes.Control)
                                    .clickable {
                                        store.themeMode = index
                                        themeModeState = index
                                        onHint("界面模式已更改为：$label")
                                    }
                                    .padding(horizontal = 12.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    label,
                                    color = if (selected) HollerTheme.colors.Accent else HollerTheme.colors.TextSecondary,
                                    fontSize = HollerType.Caption,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------- 快捷触发方式 ----------------
        item {
            HollerCard(title = "快捷触发方式", subtitle = "通知栏快捷控制 / 桌面悬浮按钮") {
                ToggleRow(
                    title = "通知栏常驻控制",
                    desc = "从中控顶栏下拉即可快捷喊话或播报快捷语，无遮挡、不占屏幕、无需悬浮权限",
                    checked = notifyQuick,
                    onCheckedChange = { want ->
                        store.notifyQuickActions = want
                        notifyQuick = want
                        HollerForegroundService.update(context)
                        onHint(if (want) "已开启通知栏常驻控制" else "已关闭通知栏常驻控制")
                    },
                )

                Spacer(Modifier.height(12.dp))

                ToggleRow(
                    title = "桌面悬浮按钮",
                    desc = "全局悬浮球：单击播快捷语 / 长按说话 / 拖动移动 / 双击收起",
                    checked = floating,
                    onCheckedChange = { want ->
                        if (!want) {
                            store.floatingEnabled = false
                            FloatingTalkService.stop(context)
                            floating = false
                            onHint("悬浮按钮已停止")
                        } else if (Settings.canDrawOverlays(context)) {
                            store.floatingEnabled = true
                            FloatingTalkService.start(context)
                            floating = true
                            onHint("悬浮按钮已启动")
                        } else {
                            onHint("请先授予「显示在其他应用上层」权限")
                            storagePerm.launch(
                                Intent(
                                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                                    Uri.parse("package:${context.packageName}"),
                                )
                            )
                        }
                    },
                )

                if (floating) {
                    // ---- 外观参数：透明度 / 尺寸（改动立即生效，无需重启服务）----
                    Spacer(Modifier.height(14.dp))
                    HollerSlider(
                        value = alpha,
                        onValueChange = {
                            alpha = it
                            store.floatingAlpha = it
                            FloatingTalkService.refreshAppearance(context)
                        },
                        range = 0.3f..1f,
                        valueLabel = "${(alpha * 100).toInt()}%",
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "悬浮球透明度",
                        color = HollerTheme.colors.TextSecondary,
                        fontSize = HollerType.Caption,
                    )
                    Spacer(Modifier.height(10.dp))
                    HollerSlider(
                        value = floatSize,
                        onValueChange = {
                            floatSize = it
                            store.floatingSize = it.toInt()
                            FloatingTalkService.refreshAppearance(context)
                        },
                        range = 96f..220f,
                        valueLabel = "${floatSize.toInt()}dp",
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "悬浮球尺寸",
                        color = HollerTheme.colors.TextSecondary,
                        fontSize = HollerType.Caption,
                    )
                }
            }
        }

        // ---------------- 系统与启动行为 ----------------
        item {
            HollerCard(title = "系统与启动行为", subtitle = "开机自启 / 启动自动播报") {
                ToggleRow(
                    title = "车机点火开机自启",
                    desc = "车机通电点火或启动后自动常驻后台与通知栏，随时待命，无需手动点开 App",
                    checked = bootAuto,
                    onCheckedChange = {
                        store.bootAutoStart = it
                        bootAuto = it
                        onHint(if (it) "已开启车机开机自启" else "已关闭开机自启")
                    },
                )

                Spacer(Modifier.height(12.dp))

                ToggleRow(
                    title = "应用启动时自动播报",
                    desc = "打开应用后自动播报「★ 标记」的那条快捷语。" +
                        "车机上开机对外喊话容易打扰，默认关闭，请按需开启",
                    checked = store.launchAutoPlay,
                    onCheckedChange = {
                        store.launchAutoPlay = it
                        onHint(if (it) "已开启：启动后自动播报一次" else "已关闭自动播报")
                    },
                )
            }
        }

        // ---------------- 日志触发 ----------------
        item {
            HollerCard(title = "日志关键词触发", subtitle = "系统事件自动喊话") {
                ToggleRow(
                    title = "启用日志监听",
                    desc = "命中关键词自动播报快捷语，冷却 ${cooldown.toInt()} 秒",
                    checked = logcat,
                    onCheckedChange = { want ->
                        if (want) {
                            store.logcatTriggerEnabled = true
                            LogcatTriggerService.start(context)
                            logcat = true
                            onHint(
                                if (LogcatTriggerService.hasLogPermission(context))
                                    "日志监听已启动（全系统）"
                                else "已启动，但仅能读本应用日志，请见下方授权命令"
                            )
                        } else {
                            store.logcatTriggerEnabled = false
                            LogcatTriggerService.stop(context)
                            logcat = false
                            onHint("日志监听已停止")
                        }
                    },
                )
                Spacer(Modifier.height(12.dp))
                LabelValueRow("触发关键词", keyword)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("请倒车", "碰撞", "行人", "车道偏离").forEach { k ->
                        HollerChip(label = k, selected = keyword == k) {
                            keyword = k
                            store.logcatKeyword = k
                            onHint("关键词已设为「$k」")
                        }
                    }
                    // 自定义关键词（对齐 Hey! 的自由输入）
                    HollerChip(label = "自定义…", selected = false) {
                        dialogKind = DialogKind.KEYWORD
                    }
                }
                Spacer(Modifier.height(12.dp))
                HollerSlider(
                    value = cooldown,
                    onValueChange = {
                        cooldown = it
                        store.logcatCooldownSec = it.toInt()
                    },
                    range = 3f..60f,
                    valueLabel = "${cooldown.toInt()}s",
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    "触发冷却（避免一次事件狂播）",
                    color = HollerTheme.colors.TextSecondary,
                    fontSize = HollerType.Caption,
                )
                Spacer(Modifier.height(12.dp))
                val granted = LogcatTriggerService.hasLogPermission(context)
                LabelValueRow(
                    label = "系统日志权限",
                    value = if (granted) "已授予" else "未授予（需 adb 一次）",
                    valueColor = if (granted) HollerTheme.colors.Accent else HollerTheme.colors.Danger,
                )
                if (!granted) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        LogcatTriggerService.grantCommand(context.packageName),
                        color = HollerTheme.colors.TextSecondary,
                        fontSize = HollerType.Caption,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(HollerShapes.Control)
                            .background(HollerTheme.colors.BgBase.copy(alpha = 0.6f))
                            .padding(10.dp),
                    )
                }
            }
        }

        // ---------------- 音频路由 ----------------
        item {
            HollerCard(title = "音频路由", subtitle = route?.source ?: "未探测") {
                LabelValueRow("识别来源", route?.source ?: "-")
                Spacer(Modifier.height(6.dp))
                LabelValueRow("当前 usage", route?.usageName ?: AudioRouteResolver.defaultUsage().toString())
                Spacer(Modifier.height(6.dp))
                LabelValueRow(
                    "目标设备",
                    route?.device?.productName?.toString() ?: "未识别（走默认输出）",
                    valueColor = if (route?.device != null) HollerTheme.colors.Accent else HollerTheme.colors.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                LabelValueRow(
                    "手动指定输出",
                    if (store.outputDeviceId >= 0) "id=${store.outputDeviceId}" else "未指定（自动）",
                    valueColor = if (store.outputDeviceId >= 0) HollerTheme.colors.Accent else HollerTheme.colors.TextSecondary,
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    HollerButton(
                        "重新探测",
                        primary = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            scope.launch {
                                withContext(Dispatchers.Default) { onReresolve() }
                                onHint(
                                    if (route?.device != null) "已定位车外扬声器"
                                    else "仍未识别车外喇叭"
                                )
                            }
                        },
                    )
                    HollerButton(
                        "播放测试音",
                        modifier = Modifier.weight(1f),
                        onClick = {
                            onHint("播放测试音，请听声音从哪出")
                            scope.launch { engine.playTone() }
                        },
                    )
                }
                if (store.outputDeviceId >= 0) {
                    Spacer(Modifier.height(8.dp))
                    HollerButton(
                        "清除手动指定（恢复自动探测）",
                        primary = false,
                        modifier = Modifier.fillMaxWidth(),
                        onClick = {
                            store.outputDeviceId = -1
                            store.inputDeviceId = -1
                            store.usageOverride = -1
                            scope.launch {
                                withContext(Dispatchers.Default) { onReresolve() }
                                onHint("已恢复自动探测：${route?.source ?: "未知"}")
                            }
                        },
                    )
                }
            }
        }

        // ---------------- usage 遍历 ----------------
        item {
            HollerCard(
                title = "Usage 遍历测试",
                subtitle = if (sweepRunning) "$sweepIndex / ${AudioRouteResolver.CANDIDATE_USAGES.size}" else "定位车外喇叭",
            ) {
                Text(
                    "逐个音频用途播一个音高递增的短音，请听哪个从车外喇叭出来，" +
                        "记下序号，即可锁定车外喇叭的真实 usage（对齐 Hey! 的遍历法）。",
                    color = HollerTheme.colors.TextSecondary,
                    fontSize = HollerType.Caption,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(12.dp))
                HollerButton(
                    if (sweepRunning) "遍历中…" else "开始遍历",
                    enabled = !sweepRunning,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        sweepRunning = true
                        sweepResults = emptyList()
                        sweepIndex = 0
                        onHint("开始遍历，注意听车外声音")
                        scope.launch {
                            val results = UsageSweeper.sweep(context) { idx, total, r ->
                                sweepIndex = idx + 1
                                sweepResults = sweepResults + r
                                if (r.routedDevice != "-") {
                                    // 发现路由到非默认设备，记录便于研判
                                    Log.i("Holler", "usage ${r.usageName} → ${r.routedDevice}")
                                }
                                if (idx == total - 1) sweepRunning = false
                            }
                            sweepRunning = false
                            onHint("遍历结束，共 ${results.size} 项")
                        }
                    },
                )
                if (sweepResults.isNotEmpty()) {
                    Spacer(Modifier.height(12.dp))
                    Text("结果", color = HollerTheme.colors.TextPrimary, fontSize = HollerType.Body)
                    Spacer(Modifier.height(6.dp))
                    sweepResults.takeLast(8).forEach { r ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Text(
                                "${AudioRouteResolver.CANDIDATE_USAGES.indexOfFirst { it.first == r.usage } + 1}. ${r.usageName}",
                                color = HollerTheme.colors.TextSecondary,
                                fontSize = HollerType.Caption,
                                fontFamily = FontFamily.Monospace,
                            )
                            Text(
                                if (r.played) r.routedDevice else (r.error ?: "失败"),
                                color = if (r.played) HollerTheme.colors.TextPrimary else HollerTheme.colors.Danger,
                                fontSize = HollerType.Caption,
                                fontFamily = FontFamily.Monospace,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        // ---------------- 外部触发 ----------------
        item {
            HollerCard(title = "外部触发命令", subtitle = "方控 / 自动化联动") {
                Text(
                    "可通过广播触发喊话，便于方向盘按键映射或 Tasker 等自动化工具调用：",
                    color = HollerTheme.colors.TextSecondary,
                    fontSize = HollerType.Caption,
                )
                Spacer(Modifier.height(8.dp))
                listOf(
                    HollerCommandReceiver.ACTION_START,
                    HollerCommandReceiver.ACTION_STOP,
                    HollerCommandReceiver.ACTION_TOGGLE,
                ).forEach { a ->
                    Text(
                        a,
                        color = HollerTheme.colors.TextPrimary,
                        fontSize = HollerType.Caption,
                        fontFamily = FontFamily.Monospace,
                        modifier = Modifier.padding(vertical = 2.dp),
                    )
                }
                Spacer(Modifier.height(8.dp))
                Text(
                    "adb shell am broadcast -a ${HollerCommandReceiver.ACTION_TOGGLE}",
                    color = HollerTheme.colors.TextSecondary,
                    fontSize = HollerType.Caption,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(HollerShapes.Control)
                        .background(HollerTheme.colors.BgBase.copy(alpha = 0.6f))
                        .padding(10.dp),
                )
            }
        }

        // ---------------- 设备清单（可选择 = 手动指定设备） ----------------
        item {
            val outputs = route?.outputDevices.orEmpty()
            val inputs = route?.inputDevices.orEmpty()
            HollerCard(
                title = "音频设备清单",
                subtitle = "输出 ${outputs.size} / 输入 ${inputs.size}",
            ) {
                Text(
                    "车外喇叭自动识别失败时，点「选用」把设备 id 固定下来，作为兜底。" +
                        "若列表为空或看不到车外设备，说明需要提权（普通 App 枚举不到）。",
                    color = HollerTheme.colors.TextSecondary,
                    fontSize = HollerType.Caption,
                    lineHeight = 17.sp,
                )
                Spacer(Modifier.height(12.dp))

                if (outputs.isEmpty()) {
                    EmptyHint("未枚举到输出设备")
                } else {
                    outputs.forEach { d ->
                        DevicePickRow(
                            device = d,
                            selected = store.outputDeviceId == d.id,
                            onPick = {
                                store.outputDeviceId = d.id
                                // 走统一入口：重解析路由并同步 UI 状态 + 引擎
                                onReresolve()
                                onHint("输出设备已固定为 id=${d.id}")
                            },
                        )
                    }
                }

                if (inputs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("输入设备", color = HollerTheme.colors.TextPrimary, fontSize = HollerType.Body)
                    Spacer(Modifier.height(6.dp))
                    inputs.forEach { d ->
                        DevicePickRow(
                            device = d,
                            selected = store.inputDeviceId == d.id,
                            onPick = {
                                store.inputDeviceId = d.id
                                // 走统一入口：重解析路由并同步 UI 状态 + 引擎
                                onReresolve()
                                onHint("录音设备已固定为 id=${d.id}")
                            },
                        )
                    }
                }

                // 先取出局部变量：isNullOrEmpty() 带 contract，
                // 这样下面的 forEach 不需要 !! 也能智能转换为非空
                val cfgDevices = route?.configDevices
                if (!cfgDevices.isNullOrEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("车机音频配置", color = HollerTheme.colors.TextPrimary, fontSize = HollerType.Body)
                    Spacer(Modifier.height(6.dp))
                    cfgDevices.forEach { (dev, ctx) ->
                        Text(
                            "· $dev → ${ctx.joinToString("/")}",
                            color = HollerTheme.colors.TextSecondary,
                            fontSize = HollerType.Caption,
                            fontFamily = FontFamily.Monospace,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
        }

        // ---------------- 关于 ----------------
        item {
            HollerCard(title = "关于") {
                LabelValueRow("版本", "0.3.1")
                Spacer(Modifier.height(6.dp))
                LabelValueRow("DSP 链", engine.dspDescription(), mono = false)
                Spacer(Modifier.height(6.dp))
                LabelValueRow(
                    "变声实现",
                    "纯 Kotlin（替代 Hey! 的 libvoiceeffect.so）",
                )
            }
        }
    }

    // ---- 文本输入对话框：自定义关键词 ----
    if (dialogKind == DialogKind.KEYWORD) {
        TextInputDialog(
            initial = keyword,
            onDismiss = { dialogKind = DialogKind.NONE },
            onConfirm = { text ->
                val t = text.trim()
                dialogKind = DialogKind.NONE
                if (t.isEmpty()) {
                    onHint("关键词为空，已忽略")
                } else {
                    keyword = t
                    store.logcatKeyword = t
                    onHint("关键词已设为「$t」")
                }
            },
        )
    }
}

/** 设置页里需要弹出文本输入的场景。 */
private enum class DialogKind { NONE, KEYWORD }

/**
 * 设备行 + 选用按钮。
 *
 * 用按钮而非整行可点，是为了避免误触（车机上滚动列表很容易点错）。
 */
@Composable
private fun DevicePickRow(
    device: android.media.AudioDeviceInfo,
    selected: Boolean,
    onPick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HollerShapes.Control)
            .background(
                if (selected) HollerTheme.colors.AccentSoft else HollerTheme.colors.BgBase.copy(alpha = 0.35f)
            )
            .border(
                BorderStroke(1.dp, if (selected) HollerTheme.colors.Accent else HollerTheme.colors.Stroke),
                HollerShapes.Control,
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                "${device.productName}",
                color = HollerTheme.colors.TextPrimary,
                fontSize = HollerType.Caption,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "id=${device.id}  ${AudioRouteResolver.typeName(device.type)}",
                color = HollerTheme.colors.TextSecondary,
                fontSize = HollerType.Caption,
                fontFamily = FontFamily.Monospace,
            )
        }
        MiniAction(if (selected) "已选" else "选用", onPick)
    }
}

/** 设置项开关行（标题 + 说明 + 开关）。 */
@Composable
private fun ToggleRow(
    title: String,
    desc: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = HollerTheme.colors.TextPrimary, fontSize = HollerType.Body)
            Text(desc, color = HollerTheme.colors.TextSecondary, fontSize = HollerType.Caption)
        }
        Spacer(Modifier.width(12.dp))
        HollerSwitch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

// ======================================================================
// DOCK 栏
// ======================================================================

@Composable
private fun DockBar(page: Page, onPage: (Page) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(HollerShapes.Button)
            .background(HollerTheme.colors.Brand)
            .border(BorderStroke(1.dp, HollerTheme.colors.Stroke), HollerShapes.Button)
            .padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Page.entries.forEach { p ->
            DockItem(
                label = p.label,
                selected = p == page,
                modifier = Modifier.weight(1f),
            ) { onPage(p) }
        }
    }
}

@Composable
private fun DockItem(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxHeight()
            .padding(vertical = 8.dp)
            .clip(HollerShapes.Control)
            .background(if (selected) HollerTheme.colors.AccentSoft else Color.Transparent)
            .pointerInput(Unit) { detectTapGestures { onClick() } },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = if (selected) HollerTheme.colors.Accent else HollerTheme.colors.TextSecondary,
            fontSize = HollerType.Body,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            textAlign = TextAlign.Center,
        )
    }
}

// ======================================================================
// 文本输入（系统对话框）
// ======================================================================

/**
 * 用系统 AlertDialog 承载文本输入。
 *
 * 为什么不用 Compose 的 BasicTextField：
 *   车机输入法（语音/手写）对 Compose 自绘文本域的兼容性不稳，
 *   系统 EditText 走的是系统输入栈，最可靠。
 *
 * 生命周期：对话框在 LaunchedEffect 中创建，在 DisposableEffect 中关闭，
 * 避免 Composable 重组时弹出多个实例。
 */
@Composable
private fun TextInputDialog(
    initial: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val context = LocalContext.current
    var dialog by remember { mutableStateOf<android.app.AlertDialog?>(null) }

    val colors = HollerTheme.colors
    LaunchedEffect(Unit) {
        val pad = (20 * context.resources.displayMetrics.density).toInt()
        val input = android.widget.EditText(context).apply {
            setText(initial)
            setHint("输入喊话内容")
            setSingleLine(false)
            setTextColor(colors.TextPrimary.toArgb())
            setHintTextColor(colors.TextSecondary.toArgb())
            setPadding(pad, pad / 2, pad, pad / 2)
        }
        dialog = android.app.AlertDialog.Builder(context)
            .setTitle("快捷语内容")
            .setView(input)
            .setPositiveButton("确定") { _, _ ->
                onConfirm(input.text?.toString().orEmpty())
            }
            .setNegativeButton("取消") { _, _ -> onDismiss() }
            .setOnCancelListener { onDismiss() }
            .create()
            .also { it.show() }
    }

    DisposableEffect(Unit) {
        onDispose { runCatching { dialog?.dismiss() } }
    }
}

private fun hasMic(context: Context): Boolean =
    context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) ==
        PackageManager.PERMISSION_GRANTED
