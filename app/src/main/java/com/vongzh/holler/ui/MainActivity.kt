package com.vongzh.holler.ui

import android.Manifest
import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
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
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.VolumeUp
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Bolt
import androidx.compose.material.icons.rounded.Campaign
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.SmartToy
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarBorder
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material.icons.rounded.Whatshot
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.vongzh.holler.audio.AudioRouteResolver
import com.vongzh.holler.audio.FloatingTalkService
import com.vongzh.holler.audio.HollerCommandReceiver
import com.vongzh.holler.audio.HollerEngine
import com.vongzh.holler.audio.HollerForegroundService
import com.vongzh.holler.audio.LogcatTriggerService
import com.vongzh.holler.audio.PhraseSpeaker
import com.vongzh.holler.audio.PhraseStore
import com.vongzh.holler.audio.UsageSweeper
import com.vongzh.holler.audio.dsp.VoiceEffect
import com.vongzh.holler.audio.media.HollerMediaEngine
import com.vongzh.holler.audio.media.LocalMediaScanner
import com.vongzh.holler.audio.media.MediaItem
import kotlin.math.roundToInt
import com.vongzh.holler.ui.theme.HollerShapes
import com.vongzh.holler.ui.theme.HollerSpacing
import com.vongzh.holler.ui.theme.HollerTheme
import com.vongzh.holler.ui.theme.HollerType
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

        // 自动拉起车机常驻悬浮控制
        runCatching {
            val store = PhraseStore(this)
            if (store.floatingEnabled && Settings.canDrawOverlays(this)) {
                FloatingTalkService.start(this)
            }
        }.onFailure { Log.w("Holler", "悬浮服务启动失败: ${it.message}") }
    }
}



/**
 * 上滑取消的手势阈值（dp）：手指上移超过该距离即进入取消态，同时作为「上滑进度」的分母。
 *
 * 取 130dp ≈ 主按钮半径（Ø268dp/2 = 134dp）：语义与手感统一为「手指滑出圆钮 = 取消」，
 * 而不是「轻微抖动就取消」。用 dp 而非 px，保证不同 density 的车机上手感一致。
 */
private const val CANCEL_DRAG_THRESHOLD_DP = 130f

/**
 * Popover 气泡外形：圆角矩形主体 + 底部一枚指向锚点的小三角。
 *
 * 关键在「一次成型」——主体与小三角用 [PathOperation.Union] 合并成**一条连续轮廓**，
 * 而不是「一个圆角矩形 + 单独画一个三角」的叠加。好处是填充渐变与 1dp 高光描边
 * 在整个外轮廓上都是连续的：三角与主体的接缝处既不会露出矩形底边那条横线，
 * 也不会出现描边断口，投影也能按真实外轮廓（含三角）投出去。
 *
 * @param anchorX 三角尖端的水平位置（px，相对气泡左边缘），由调用方测量触发项中心得到
 */
private class PopoverBubbleShape(
    private val anchorX: Float,
    private val cornerDp: Float,
    private val arrowHalfWidthDp: Float,
    private val arrowHeightDp: Float,
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        with(density) {
            val corner = cornerDp.dp.toPx()
            val arrowH = arrowHeightDp.dp.toPx()
            val arrowHalf = arrowHalfWidthDp.dp.toPx()
            // 主体底边：整个高度减去三角区
            val bodyBottom = (size.height - arrowH).coerceAtLeast(corner)

            val body = Path().apply {
                addRoundRect(
                    RoundRect(
                        left = 0f,
                        top = 0f,
                        right = size.width,
                        bottom = bodyBottom,
                        cornerRadius = CornerRadius(corner, corner)
                    )
                )
            }

            // 三角尖端做圆角会失真，因此左右各自内缩一个圆角半径 + 半宽，
            // 保证三角完整落在主体底边的直线段内（不会因为落在圆角上而看着被啃掉一角）
            val minX = corner + arrowHalf
            val maxX = (size.width - corner - arrowHalf).coerceAtLeast(minX)
            val ax = anchorX.coerceIn(minX, maxX)

            val arrow = Path().apply {
                // 上边比主体底边抬高 1px，故意制造重叠区，union 后才不会留发丝缝
                moveTo(ax - arrowHalf, bodyBottom - 1f)
                lineTo(ax + arrowHalf, bodyBottom - 1f)
                lineTo(ax, size.height)
                close()
            }

            val outline = Path().apply { op(body, arrow, PathOperation.Union) }
            Outline.Generic(outline)
        }
}

/**
 * 玻璃饰面的光效参数。
 *
 * 深浅两套策略刻意分开：
 * · 深色 → **不用玻璃**：背景本身就是暗的，任何白色叠加都会退化成灰雾。深色只走
 *   令牌里的均匀表面色 + 冷色细描边（标准 elevation 语法），所以下面所有光效项传 0。
 * · 浅色 → **真·薄玻璃**：填充必须够薄才有「透」，玻璃的形体由顶部受光面、
 *   左上镜面光斑、底部内缘反光、上亮下暗描边、内缘厚度暗边共同撑起。
 */
private class GlassStyle(
    val fillColors: List<Color>,
    val sheen: Color,      // 顶部受光面峰值：光从上方来，玻璃上缘更亮
    val specular: Color,   // 左上镜面光斑峰值：光源在玻璃表面的一次反射
    val underGlow: Color,  // 底部内缘反光峰值：玻璃底边捕捉到的环境光
    val edgeColors: List<Color>,
    val innerRim: Color,   // 内侧厚度暗边：玻璃有厚度，内缘收一圈淡暗部才立得起来
)

/** 把任意 [Shape] 转成 [Path]：填充与描边必须共用同一条轮廓，玻璃才不会「开线」。 */
private fun Shape.toPath(size: Size, density: Density): Path =
    when (val o = createOutline(size, LayoutDirection.Ltr, density)) {
        is Outline.Generic -> o.path
        is Outline.Rounded -> Path().apply { addRoundRect(o.roundRect) }
        is Outline.Rectangle -> Path().apply { addRect(o.rect) }
    }

/**
 * 在 [shape] 的轮廓上自绘一整块玻璃饰面。用 [drawWithCache] 缓存轮廓 Path，
 * 仅在尺寸/形状变化时重建。深色传入全 0 光效时，退化为「均匀填充 + 细描边」的实心面。
 */
private fun Modifier.glassFinish(shape: Shape, style: GlassStyle): Modifier = drawWithCache {
    val outline = shape.toPath(size, this)
    onDrawBehind {
        val w = size.width
        val h = size.height
        // ① 玻璃本体：半透明填充，下层图形与背景渐变透出来
        drawPath(outline, Brush.verticalGradient(style.fillColors, 0f, h))
        clipPath(outline) {
            // ② 顶部受光面：光源自上而下
            if (style.sheen.alpha > 0f) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(style.sheen, Color.Transparent),
                        startY = 0f, endY = h * 0.56f
                    )
                )
            }
            // ③ 左上镜面光斑：光源位置略高于左上角，反射出一块柔和的高光
            if (style.specular.alpha > 0f) {
                drawRect(
                    Brush.radialGradient(
                        colors = listOf(style.specular, Color.Transparent),
                        center = Offset(w * 0.22f, -h * 0.18f),
                        radius = w * 0.62f
                    )
                )
            }
            // ④ 底部内缘反光：玻璃底边兜住的环境光
            if (style.underGlow.alpha > 0f) {
                drawRect(
                    Brush.verticalGradient(
                        listOf(Color.Transparent, style.underGlow),
                        startY = h * 0.70f, endY = h
                    )
                )
            }
        }
        // ⑤ 边缘描边：上亮下暗，勾出玻璃的厚度
        drawPath(
            outline,
            Brush.verticalGradient(style.edgeColors, 0f, h),
            style = Stroke(1.2.dp.toPx())
        )
        // ⑥ 内侧厚度暗边：clip 后描边只剩内半段，形成贴着内缘的一圈暗部——
        //    浅色主题下全靠它撑起「这是块实体玻璃」的体积感
        if (style.innerRim.alpha > 0f) {
            clipPath(outline) {
                drawPath(outline, style.innerRim, style = Stroke(2.5.dp.toPx()))
            }
        }
    }
}

@Composable
fun HollerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    val engine = remember { HollerEngine.get(context) }
    val store = remember { PhraseStore(context) }
    val speaker = remember { PhraseSpeaker(context) }

    val stageState = remember { mutableStateOf(HollerEngine.Stage.IDLE) }
    var stage by stageState
    var route by remember { mutableStateOf<AudioRouteResolver.RouteInfo?>(null) }
    var level by remember { mutableStateOf(0) }
    var elapsed by remember { mutableStateOf(0f) }
    var playingElapsed by remember { mutableStateOf(0f) }
    var playingDuration by remember { mutableStateOf(1f) }
    val defaultHint = "车载喊话系统已就绪"
    var hint by remember { mutableStateOf(defaultHint) }

    var showSettings by remember { mutableStateOf(false) }
    var showDeviceSelector by remember { mutableStateOf(false) }
    var showPhrase by remember { mutableStateOf(false) }
    var showVoiceSheet by remember { mutableStateOf(false) }
    var showSoundFxSheet by remember { mutableStateOf(false) }
    var showMusicSheet by remember { mutableStateOf(false) }
    var newPhraseInput by remember { mutableStateOf("") }
    var phraseList by remember { mutableStateOf(store.items()) }
    var voiceEffect by remember { mutableStateOf(store.voiceEffect) }
    var isCancelling by remember { mutableStateOf(false) }
    // 上滑取消进度 0→1（上滑 0→CANCEL_DRAG_THRESHOLD_DP），驱动绿色退场与红色标记升起的两段式就地反馈
    var cancelProgress by remember { mutableFloatStateOf(0f) }
    val haptic = LocalHapticFeedback.current
    var volumeFraction by remember { mutableFloatStateOf(store.outputVolume) }

    val mediaEngine = remember { HollerMediaEngine.get(context) }
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    LaunchedEffect(volumeFraction) {
        val maxVol = runCatching { audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC) }.getOrDefault(15).coerceAtLeast(1)
        val targetIndex = (volumeFraction * maxVol).roundToInt()
        runCatching { audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, targetIndex, 0) }
        mediaEngine.musicVolume = volumeFraction
    }

    // 车外多媒体播放状态监听
    var isMediaPlaying by remember { mutableStateOf(mediaEngine.isPlaying) }
    var currentTrack by remember { mutableStateOf(mediaEngine.currentTrack) }
    var mediaPos by remember { mutableStateOf(mediaEngine.currentPosition) }
    var mediaDur by remember { mutableStateOf(mediaEngine.duration) }
    var mediaTracks by remember { mutableStateOf(mediaEngine.tracks) }
    var mediaPlayMode by remember { mutableStateOf(mediaEngine.playMode) }

    DisposableEffect(mediaEngine) {
        val listener = object : HollerMediaEngine.Listener {
            override fun onPlaybackStateChanged(isPlaying: Boolean, track: MediaItem?) {
                isMediaPlaying = isPlaying
                currentTrack = track
            }
            override fun onProgressUpdate(positionMs: Long, durationMs: Long) {
                mediaPos = positionMs
                mediaDur = durationMs
            }
            override fun onPlaylistChanged(tracks: List<MediaItem>) {
                mediaTracks = tracks
            }
            override fun onPlayModeChanged(mode: HollerMediaEngine.PlayMode) {
                mediaPlayMode = mode
            }
        }
        mediaEngine.registerListener(listener)
        onDispose { mediaEngine.unregisterListener(listener) }
    }

    val safAudioPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch(Dispatchers.IO) {
                val item = LocalMediaScanner.parseFromSafUri(context, uri)
                withContext(Dispatchers.Main) {
                    mediaEngine.addTrack(item, playImmediately = true)
                    hint = "已导入并播放：${item.title}"
                }
            }
        }
    }

    val active = stage == HollerEngine.Stage.RECORDING
    val playing = stage == HollerEngine.Stage.PLAYING

    // 录音尾部告警：剩余 ≤ 3 秒时，整条录音视觉链路（波纹 / 声波柱 / 圆底 / 顶栏 / 倒计时环）
    // 由 Accent 统一切到 Danger。这样红色重新变回「时间压力」信号，而不是录音全程的装饰色。
    val urgent = active && (HollerEngine.MAX_SECONDS - elapsed) <= 3f
    val recordTone = if (urgent) HollerTheme.colors.Danger else HollerTheme.colors.Accent
    val recordToneText = if (urgent) HollerTheme.colors.DangerText else HollerTheme.colors.AccentText

    // 录音期间收起所有浮层：面板会盖住录音就地反馈，且下方 DOCK 同时被禁用（见 DOCK 的 enabled）
    LaunchedEffect(active) {
        if (active) {
            showVoiceSheet = false
            showPhrase = false
            showSoundFxSheet = false
            showMusicSheet = false
            showDeviceSelector = false
        }
    }

    // 状态提示自动恢复默认：当提示为操作反馈或播报结果时，显示 3.5 秒后平滑恢复默认就绪状态
    LaunchedEffect(hint, stage, isCancelling) {
        if (!active && !playing && !isCancelling && hint != defaultHint) {
            delay(3500)
            hint = defaultHint
        }
    }


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

    val playPhraseAudio: (PhraseStore.Item) -> Unit = { item ->
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        hint = "正在播报：${item.text}"
        scope.launch {
            if (!speaker.isReady) speaker.init()
            var waited = 0
            while (!speaker.isReady && waited < 3000) {
                delay(100)
                waited += 100
            }
            val rawPcm = speaker.synthesize(item.text, store.speechRate, store.pitch)
            if (rawPcm.isNotEmpty()) {
                val voicePcm = if (store.voiceEffect != VoiceEffect.NATIVE) {
                    engine.voiceBridge.process(rawPcm)
                } else rawPcm
                val chime = if (store.chimeEnabled) engine.chimePcm() else ByteArray(0)
                val gap = if (store.chimeEnabled) ByteArray(16000 * 2 * 120 / 1000) else ByteArray(0)
                val singlePhrase = chime + gap + voicePcm
                val repeatGap = ByteArray(16000 * 2 * 300 / 1000)
                var combinedAudio = ByteArray(0)
                for (r in 1..store.repeatCount) {
                    combinedAudio = if (combinedAudio.isEmpty()) singlePhrase else combinedAudio + repeatGap + singlePhrase
                }
                val totalDurationSec = combinedAudio.size.toFloat() / 32000f
                playingDuration = totalDurationSec.coerceAtLeast(0.8f)
                playingElapsed = 0f
                engine.playPcm(combinedAudio)
                hint = "播报完毕：${item.text}"
            } else {
                hint = "播报失败（TTS 未生成声音）"
            }
        }
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
        // 就绪状态不再写进顶栏：设备名已由右上角设备胶囊常驻展示，圆钮内亦有「按住 喊话」暗示，
        // 顶栏只留给事件提示（面板操作结果、播报结果等），避免三处重复。
        // 仅真正的异常才提示。
        val r = route
        if (r?.device == null && store.outputDeviceId >= 0) {
            hint = "指定的设备不存在"
        }


        // 启动自动播报
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
            override fun onStageChanged(stage: HollerEngine.Stage) { stageState.value = stage }
            override fun onPhraseResult(message: String) { hint = message }
        }
        engine.registerListener(l)
        onDispose { engine.unregisterListener(l) }
    }

    // 电平 + 倒计时与播报进度轮询驱动声波动画
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
                val startPlayTime = System.currentTimeMillis()
                while (engine.isPlaying) {
                    val passed = (System.currentTimeMillis() - startPlayTime) / 1000f
                    playingElapsed = passed
                    // 平滑自然的音频律动包络（正弦波合成，代替剧烈随机跳变）
                    val smoothWave = (kotlin.math.sin(passed * 4.5) * 0.5 + 0.5).toFloat()
                    level = (40 + smoothWave * 45).toInt().coerceIn(30, 95)
                    delay(30)
                }
                playingElapsed = 0f
            }
            else -> {
                level = 0
                elapsed = 0f
                playingElapsed = 0f
            }
        }
    }


    // 车机返回键拦截：优先关闭当前弹窗
    androidx.activity.compose.BackHandler(enabled = showPhrase || showSoundFxSheet || showMusicSheet || showVoiceSheet || showSettings || showDeviceSelector) {
        when {
            showDeviceSelector -> showDeviceSelector = false
            showSettings -> showSettings = false
            showVoiceSheet -> showVoiceSheet = false
            showPhrase -> showPhrase = false
            showSoundFxSheet -> showSoundFxSheet = false
            showMusicSheet -> showMusicSheet = false
        }
    }

    Surface(color = HollerTheme.colors.BgBase, modifier = Modifier.fillMaxSize()) {

        Box(
            modifier = Modifier
                .fillMaxSize()
                // 上亮下深的背景渐变：白卡/白圆边界更清晰，也让底部玻璃 DOCK 有真实的透射内容
                .background(
                    Brush.verticalGradient(
                        listOf(HollerTheme.colors.BgBaseTop, HollerTheme.colors.BgBase)
                    )
                )
        ) {
            // 车规级沉浸顶栏：顶部中央事件提示胶囊 + 右上角车外扬声器设备通道胶囊
            val isDefaultIdle = !active && !playing && !isCancelling && hint == defaultHint
            // 顶部胶囊只承担「事件提示」：默认就绪 / 录音中 / 取消中一律隐藏——
            // 这些状态在中央圆钮内已有就地反馈（按住 喊话 / 松开 发送 / ×），双处重复反而分散视线。
            val showTopPill = !isDefaultIdle && !active && !isCancelling

            // —— 上滑取消的两段式就地反馈（绿色系退场 / 红色系接管，严格互补不重叠）——
            // greenFade：绿色元素（声波柱、倒计时环、涟漪光晕、圆内文案）的残余透明度。
            //   二次曲线全程缓退：上滑过程中绿色渐弱但始终可见（红色绝不提前出现），
            //   手指出圈判定取消的瞬间，绿色体系随分支切换自然让位给「红 × + 红字」。
            val greenFade = 1f - cancelProgress * cancelProgress

            androidx.compose.animation.AnimatedVisibility(
                visible = showTopPill,
                enter = fadeIn(tween(200)) + slideInVertically(tween(220)) { -it / 2 },
                exit = fadeOut(tween(180)) + slideOutVertically(tween(180)) { -it / 2 },
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 20.dp)
            ) {
                // 顶部中央：事件提示胶囊
                // 宽度上限 360dp（≈屏宽 35%，可容纳约 21 个中文字）：事件文案长度不可控
                // （「正在播放车外音效：XXX」「播报完毕：XXX」等含用户自定义文本），
                // 196dp 时约 11 字就被省略号截断，信息看不全。
                // 与右上设备胶囊（220dp，左边缘最坏 x≈780）之间仍留 ≥88dp 间隙，不会挤在一起。
                Box(
                    modifier = Modifier
                        .widthIn(max = 360.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (playing) HollerTheme.colors.AccentSoft else HollerTheme.colors.Brand)
                        .border(
                            BorderStroke(
                                1.dp,
                                if (playing) HollerTheme.colors.Accent else HollerTheme.colors.Stroke
                            ),
                            RoundedCornerShape(20.dp)
                        )
                        .padding(horizontal = 18.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .clip(CircleShape)
                                .background(HollerTheme.colors.Accent)
                        )
                        Text(
                            text = hint,
                            color = if (playing) HollerTheme.colors.AccentText else HollerTheme.colors.TextPrimary,
                            fontSize = 14.5.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 右上角：车外扬声器设备常驻定位状态指示（常驻，不参与事件提示的显隐）
            // 长度不可控（设备商品名，如「Car on x86_64_emulator」），给 220dp 上限并省略号收尾，
            // 128dp 时商品名几乎必被截断；220dp 可与中央事件提示（360dp）并存不重叠。
            val isDeviceConnected = route?.device != null
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(horizontal = 24.dp, vertical = 20.dp)
            ) {
                Box(
                    modifier = Modifier
                        .widthIn(max = 220.dp)
                        .clip(RoundedCornerShape(20.dp))
                        .background(HollerTheme.colors.BrandElevated.copy(alpha = 0.85f))
                        .border(BorderStroke(1.dp, HollerTheme.colors.Stroke), RoundedCornerShape(20.dp))
                        .clickable {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            showDeviceSelector = !showDeviceSelector
                        }
                        .padding(horizontal = 14.dp, vertical = 7.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        androidx.compose.material3.Icon(
                            imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                            contentDescription = "车外喇叭通道",
                            tint = if (isDeviceConnected) HollerTheme.colors.AccentText else HollerTheme.colors.TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = if (isDeviceConnected) (route?.device?.productName?.toString() ?: "车外扬声器") else "车载音频通路",
                            fontSize = 12.sp,
                            fontWeight = if (isDeviceConnected) FontWeight.Medium else FontWeight.Normal,
                            color = if (isDeviceConnected) HollerTheme.colors.TextPrimary else HollerTheme.colors.TextSecondary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // 左侧：车规级高质感悬浮垂直音量条 (驱动车外音量、系统音量与媒体音量，与左下角齿轮按键共享中心垂直基准轴)
            VerticalVolumeBar(
                volume = volumeFraction,
                onVolumeChange = {
                    volumeFraction = it
                    store.outputVolume = it
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 26.dp)
            )

            // Center large circle button
            val scale by animateFloatAsState(
                targetValue = if (isCancelling) 0.92f else if (active) 0.96f else 1f,
                animationSpec = spring(dampingRatio = 0.55f, stiffness = 600f),
                label = "scale"
            )
            val isDarkTheme = HollerTheme.colors.BgBase.luminance() < 0.5f

            // 待机状态下微光呼吸
            val iconPulseTransition = rememberInfiniteTransition(label = "iconPulse")
            val idleGlowBreath by iconPulseTransition.animateFloat(
                initialValue = 0.14f,
                targetValue = 0.30f,
                animationSpec = infiniteRepeatable(
                    animation = tween(2200, easing = FastOutSlowInEasing),
                    repeatMode = RepeatMode.Reverse
                ),
                label = "idleGlowBreath"
            )

            // 居中喊话主麦克风容器（整体上移 38dp，视觉中心更佳）
            Box(
                modifier = Modifier
                    .align(Alignment.Center)
                    .offset(y = (-38).dp)
                    .size(320.dp),
                contentAlignment = Alignment.Center
            ) {
                // 1. 周围自由发散的声波水波纹特效
                AudioGlowRippleView(
                    level = level,
                    active = active,
                    playing = playing,
                    isCancelling = isCancelling,
                    urgent = urgent,
                    fade = if (active) greenFade else 1f,
                    baseRadiusDp = 134.dp,
                    modifier = Modifier.size(880.dp)
                )

                val recordTone = if (urgent) HollerTheme.colors.Danger else HollerTheme.colors.Accent
                val recordToneText = if (urgent) HollerTheme.colors.DangerText else HollerTheme.colors.AccentText

                // 2. 录音倒计时显示的动态进度环
                CountdownRing(
                    elapsedSeconds = elapsed,
                    maxSeconds = HollerEngine.MAX_SECONDS.toFloat(),
                    active = active && !isCancelling,
                    showText = false,
                    fade = greenFade,
                    customColor = recordTone,
                    modifier = Modifier.size(284.dp)
                )

                val borderColor = when {
                    isCancelling -> HollerTheme.colors.Danger.copy(alpha = 0.85f)
                    active -> recordTone.copy(alpha = 0.55f)
                    playing -> HollerTheme.colors.Accent.copy(alpha = 0.45f)
                    else -> HollerTheme.colors.Stroke
                }

                val shadowAmbient = when {
                    isCancelling -> Color(0x33FF3B30)
                    active -> recordTone.copy(alpha = 0.20f)
                    playing -> HollerTheme.colors.Accent.copy(alpha = 0.16f)
                    else -> if (isDarkTheme) Color(0x33000000) else Color(0x1F1C2C40)
                }
                val shadowSpot = when {
                    isCancelling -> Color(0x44FF453A)
                    active -> recordTone.copy(alpha = 0.26f)
                    playing -> HollerTheme.colors.Accent.copy(alpha = 0.20f)
                    else -> if (isDarkTheme) Color(0x4D000000) else Color(0x2E1C2C40)
                }

                val baseCardGradient = Brush.verticalGradient(
                    if (isDarkTheme)
                        listOf(Color(0xFF1E2836), Color(0xFF121922))
                    else
                        listOf(Color(0xFFFFFFFF), Color(0xFFF6F8FB))
                )

                // 3. 视觉展示层（随手势缩放、带有温润阴影与柔和高光）
                Box(
                    modifier = Modifier
                        .size(268.dp)
                        .scale(scale)
                        .shadow(
                            elevation = 14.dp,
                            shape = CircleShape,
                            ambientColor = shadowAmbient,
                            spotColor = shadowSpot,
                        )
                        .clip(CircleShape)
                        .background(baseCardGradient)
                        .border(
                            BorderStroke(if (isCancelling) 2.dp else 1.2.dp, borderColor),
                            CircleShape
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    // 状态高亮柔光层（极柔和的半透明染色，保证温润雅致，绝不刺眼）
                    if (isCancelling) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            Color(0xFFFF3B30).copy(alpha = 0.14f),
                                            Color(0xFFFF3B30).copy(alpha = 0.05f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    } else if (active) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            recordTone.copy(alpha = 0.16f),
                                            recordTone.copy(alpha = 0.06f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    } else if (playing) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(
                                    Brush.radialGradient(
                                        listOf(
                                            HollerTheme.colors.Accent.copy(alpha = 0.12f),
                                            HollerTheme.colors.Accent.copy(alpha = 0.04f),
                                            Color.Transparent
                                        )
                                    )
                                )
                        )
                    }

                    // 顶部 CNC 倒角物理高光反光弧
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        drawArc(
                            brush = Brush.verticalGradient(
                                listOf(
                                    Color.White.copy(alpha = if (active) 0.35f else if (isDarkTheme) 0.25f else 0.65f),
                                    Color.Transparent
                                ),
                                startY = 0f,
                                endY = size.height * 0.38f
                            ),
                            startAngle = 210f,
                            sweepAngle = 120f,
                            useCenter = false,
                            style = Stroke(width = 1.6.dp.toPx())
                        )
                    }

                    val cancelMarkAlpha by animateFloatAsState(
                        targetValue = if (isCancelling) 1f else 0f,
                        animationSpec = tween(140),
                        label = "cancelMark"
                    )
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        if (isCancelling) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                                modifier = Modifier.alpha(cancelMarkAlpha)
                            ) {
                                Box(
                                    contentAlignment = Alignment.Center,
                                    modifier = Modifier.size(72.dp)
                                ) {
                                    androidx.compose.foundation.Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    Color(0xFFFF453A).copy(alpha = 0.22f),
                                                    Color(0xFFFF453A).copy(alpha = 0.06f),
                                                    Color.Transparent
                                                )
                                            )
                                        )
                                    }
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "取消喊话",
                                        tint = Color(0xFFFF3B30),
                                        modifier = Modifier.size(52.dp)
                                    )
                                }
                                Spacer(Modifier.height(10.dp))
                                Text(
                                    "松开 取消喊话",
                                    color = Color(0xFFFF3B30),
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 0.6.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    "已放弃本次录音",
                                    color = HollerTheme.colors.TextSecondary.copy(alpha = 0.8f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal
                                )
                            }
                        } else if (active) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AudioEqualizerBars(
                                    color = recordTone,
                                    level = level,
                                    isRecording = true,
                                    modifier = Modifier
                                        .size(width = 108.dp, height = 64.dp)
                                        .alpha(greenFade)
                                )
                                Spacer(Modifier.height(14.dp))
                                Text(
                                    if (urgent) "松开  紧急广播" else "松开  发送广播",
                                    color = recordToneText,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 1.sp,
                                    modifier = Modifier.alpha(greenFade)
                                )
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    "↑ 上滑取消",
                                    color = HollerTheme.colors.TextSecondary.copy(alpha = 0.85f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium,
                                    modifier = Modifier.alpha(greenFade)
                                )
                            }
                        } else if (playing) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                AudioEqualizerBars(
                                    color = HollerTheme.colors.Accent,
                                    level = level,
                                    isRecording = false,
                                    modifier = Modifier.size(width = 118.dp, height = 66.dp)
                                )
                                Spacer(Modifier.height(14.dp))
                                Box(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(16.dp))
                                        .background(HollerTheme.colors.Accent.copy(alpha = 0.12f))
                                        .border(
                                            BorderStroke(1.dp, HollerTheme.colors.Accent.copy(alpha = 0.35f)),
                                            RoundedCornerShape(16.dp)
                                        )
                                        .padding(horizontal = 14.dp, vertical = 5.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .clip(CircleShape)
                                                .background(HollerTheme.colors.Accent)
                                        )
                                        Text(
                                            "车外广播中...",
                                            color = HollerTheme.colors.AccentText,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.Bold,
                                            letterSpacing = 0.6.sp
                                        )
                                    }
                                }
                            }
                        } else {
                            val accentCol = HollerTheme.colors.Accent
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    androidx.compose.foundation.Canvas(modifier = Modifier.size(140.dp)) {
                                        drawCircle(
                                            brush = Brush.radialGradient(
                                                colors = listOf(
                                                    accentCol.copy(alpha = idleGlowBreath * 0.6f),
                                                    accentCol.copy(alpha = idleGlowBreath * 0.15f),
                                                    Color.Transparent
                                                )
                                            ),
                                            radius = size.minDimension / 2f
                                        )
                                    }
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Rounded.Mic,
                                        contentDescription = "按住喊话",
                                        tint = accentCol,
                                        modifier = Modifier.size(84.dp)
                                    )
                                }
                                Spacer(Modifier.height(8.dp))
                                Text(
                                    text = "按 住  喊 话",
                                    color = HollerTheme.colors.TextPrimary,
                                    fontSize = 16.5.sp,
                                    fontWeight = FontWeight.Bold,
                                    letterSpacing = 2.sp
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(
                                    text = "松开发送 · 上滑取消",
                                    color = HollerTheme.colors.TextSecondary.copy(alpha = 0.75f),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Normal,
                                    letterSpacing = 0.4.sp
                                )
                            }
                        }
                    }
                }

                // 4. 独立的顶层手势捕获层（不受内层 scale、重组与动画干扰，杜绝意外断触）
                Box(
                    modifier = Modifier
                        .size(268.dp)
                        .clip(CircleShape)
                        .pointerInput(Unit) {
                            val cancelThresholdPx = CANCEL_DRAG_THRESHOLD_DP.dp.toPx()
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()

                                if (!hasMic(context)) {
                                    micPerm.launch(Manifest.permission.RECORD_AUDIO)
                                    return@awaitEachGesture
                                }

                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                isCancelling = false

                                if (engine.startRecording()) {
                                    hint = "正在喊话…松开发送"
                                } else {
                                    hint = "录音启动失败"
                                    return@awaitEachGesture
                                }

                                var pointerId = down.id
                                val startY = down.position.y

                                while (true) {
                                    val event = awaitPointerEvent()
                                    val change = event.changes.firstOrNull { it.id == pointerId } ?: break

                                    if (change.pressed) {
                                        val deltaY = startY - change.position.y
                                        val cancellingNow = deltaY > cancelThresholdPx
                                        cancelProgress = (deltaY / cancelThresholdPx).coerceIn(0f, 1f)

                                        if (cancellingNow != isCancelling) {
                                            isCancelling = cancellingNow
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            hint = if (isCancelling) "松开手指，取消喊话" else "正在喊话…松开发送"
                                        }
                                        change.consume()
                                    } else {
                                        change.consume()
                                        if (isCancelling) {
                                            engine.cancelRecording()
                                            hint = "已取消喊话"
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                        } else if (stage == HollerEngine.Stage.RECORDING) {
                                            hint = "正在发送至车外喇叭…"
                                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                            scope.launch {
                                                val n = engine.stopAndPlay()
                                                hint = if (n > 0) "已完成车外播报" else "录音太短"
                                            }
                                        }
                                        isCancelling = false
                                        break
                                    }
                                }
                                cancelProgress = 0f
                            }
                        }
                )
            }

            // ---- 玻璃材质 ----
            // 深色 → 不用玻璃：只走令牌里的均匀表面色 + 冷色描边，白高光项全部归零。
            // 浅色 → 真·薄玻璃：填充压到很薄，靠边缘亮线 / 内缘暗边 / 光斑 / 投影立形体。
            val glassDark = HollerTheme.colors.BgBase.luminance() < 0.5f
            val dockGlass = GlassStyle(
                fillColors = listOf(HollerTheme.colors.GlassSurface, HollerTheme.colors.GlassSurfaceBottom),
                sheen = Color.White.copy(alpha = if (glassDark) 0f else 0.30f),
                specular = Color.White.copy(alpha = if (glassDark) 0f else 0.18f),
                underGlow = Color.White.copy(alpha = if (glassDark) 0f else 0.16f),
                edgeColors = listOf(HollerTheme.colors.GlassEdgeTop, HollerTheme.colors.GlassEdgeBottom),
                innerRim = Color.Transparent
            )
            // 气泡比 DOCK 更薄更亮：它浮在纯背景上，可以透得更彻底，形体全交给边缘系统
            val bubbleGlass = GlassStyle(
                fillColors = if (glassDark)
                    listOf(HollerTheme.colors.GlassSurface, HollerTheme.colors.GlassSurfaceBottom)
                else
                    listOf(Color.White.copy(alpha = 0.34f), Color.White.copy(alpha = 0.10f)),
                sheen = Color.White.copy(alpha = if (glassDark) 0f else 0.46f),
                specular = Color.White.copy(alpha = if (glassDark) 0f else 0.34f),
                underGlow = Color.White.copy(alpha = if (glassDark) 0f else 0.30f),
                edgeColors = if (glassDark)
                    listOf(HollerTheme.colors.GlassEdgeTop, HollerTheme.colors.GlassEdgeBottom)
                else
                    // 上边受光亮线 + 底边折射反光线，都亮但底边弱一档——真实玻璃边缘的亮度分布
                    listOf(Color.White.copy(alpha = 0.95f), Color.White.copy(alpha = 0.55f)),
                innerRim = if (glassDark) Color.Transparent else Color(0x1A404E63)
            )
            val dockGlassShape = RoundedCornerShape(32.dp)

            // ---- 变声 popover 的锚点测量 ----
            // 气泡是 6 项的横条、DOCK 是 3 项，两者宽度不同却都相对屏幕居中，左边缘并不对齐。
            // 因此用窗口坐标做差，求出「DOCK 中变声项的中心」相对「气泡左边缘」的偏移，
            // 小三角就画在这个位置上——精确指回触发它的那一项，而不是笼统地指个中间。
            var voiceItemCenterPx by remember { mutableStateOf(0f) }
            var bubbleLeftPx by remember { mutableStateOf(0f) }
            var bubbleWidthPx by remember { mutableStateOf(0f) }
            val bubbleAnchorX = voiceItemCenterPx - bubbleLeftPx
            // 生长动画的原点：从三角所在的横向位置开始放大，气泡像从那一项「顶」出来
            val bubbleOriginX = if (bubbleWidthPx > 0f) {
                (bubbleAnchorX / bubbleWidthPx).coerceIn(0.12f, 0.88f)
            } else 0.5f

            // 变声面板展开时的「点击外部收起」遮罩：声明在 DOCK 之前（z 序更低），
            // 因此 DOCK 与气泡自身的点击不被拦截——再点一次「变声」即可收起
            if (showVoiceSheet) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showVoiceSheet = false }
                )
            }

            // Bottom pill row for Voice / Phrase / Sound Effects（距离主图标更近，车规操作流畅）
            // 录音期间整条 DOCK 禁用并压暗：避免误触弹出的面板盖住录音就地反馈
            // 玻璃拟态：半透明渐变填充 + 顶部高光描边 + 柔投影，透出背景渐变而非一块死白
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = 44.dp)
                    .alpha(if (active) 0.32f else 1f)
                    .shadow(
                        elevation = 12.dp,
                        shape = dockGlassShape,
                        ambientColor = Color(0x1F1C2C40),
                        spotColor = Color(0x2E1C2C40),
                    )
                    .glassFinish(dockGlassShape, dockGlass)
                    .clip(dockGlassShape)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // 功能 1：变声音色
                    val isCustomVoice = voiceEffect != VoiceEffect.NATIVE
                    val voiceIcon = when (voiceEffect) {
                        VoiceEffect.NATIVE -> Icons.Rounded.Tune
                        VoiceEffect.UNCLE -> Icons.Rounded.RecordVoiceOver
                        VoiceEffect.LOLI -> Icons.Rounded.AutoAwesome
                        VoiceEffect.ROBOT -> Icons.Rounded.SmartToy
                        VoiceEffect.ELECTRO -> Icons.Rounded.GraphicEq
                        VoiceEffect.MONSTER -> Icons.Rounded.Whatshot
                    }
                    Box(
                        modifier = Modifier
                            .onGloballyPositioned { it ->
                                voiceItemCenterPx = it.boundsInWindow().center.x
                            }
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (showVoiceSheet) HollerTheme.colors.BrandElevated else Color.Transparent)
                            .clickable(enabled = !active) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showPhrase = false
                                showSoundFxSheet = false
                                showMusicSheet = false
                                showVoiceSheet = !showVoiceSheet
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = voiceIcon,
                                contentDescription = voiceEffect.label,
                                tint = HollerTheme.colors.TextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                voiceEffect.label,
                                color = HollerTheme.colors.TextPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = if (showVoiceSheet) FontWeight.SemiBold else FontWeight.Medium
                            )
                            if (isCustomVoice) {
                                Box(
                                    modifier = Modifier
                                        .align(Alignment.Top)
                                        .offset(x = 1.dp, y = (-1).dp)
                                        .size(5.dp)
                                        .clip(CircleShape)
                                        .background(HollerTheme.colors.Accent)
                                )
                            }
                        }
                    }

                    // 功能 2：快捷短语
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (showPhrase) HollerTheme.colors.BrandElevated else Color.Transparent)
                            .clickable(enabled = !active) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showVoiceSheet = false
                                showSoundFxSheet = false
                                showMusicSheet = false
                                showPhrase = !showPhrase
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Rounded.Bolt,
                                contentDescription = "快捷短语",
                                tint = HollerTheme.colors.TextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                "快捷短语",
                                color = HollerTheme.colors.TextPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = if (showPhrase) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }

                    // 功能 3：趣味音效
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(24.dp))
                            .background(if (showSoundFxSheet) HollerTheme.colors.BrandElevated else Color.Transparent)
                            .clickable(enabled = !active) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showVoiceSheet = false
                                showPhrase = false
                                showMusicSheet = false
                                showSoundFxSheet = !showSoundFxSheet
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                contentDescription = "趣味音效",
                                tint = HollerTheme.colors.TextPrimary,
                                modifier = Modifier.size(17.dp)
                            )
                            Text(
                                "趣味音效",
                                color = HollerTheme.colors.TextPrimary,
                                fontSize = 14.5.sp,
                                fontWeight = if (showSoundFxSheet) FontWeight.SemiBold else FontWeight.Medium
                            )
                        }
                    }

                    // 功能 4：车外音乐 (Boombox) · 若媒体播放中展示灵动微岛
                    if (isMediaPlaying || currentTrack != null) {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(HollerTheme.colors.BrandElevated)
                                .border(
                                    BorderStroke(1.dp, if (isMediaPlaying) HollerTheme.colors.Accent.copy(alpha = 0.6f) else HollerTheme.colors.Stroke),
                                    RoundedCornerShape(24.dp)
                                )
                                .clickable(enabled = !active) {
                                    haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                    showVoiceSheet = false
                                    showPhrase = false
                                    showSoundFxSheet = false
                                    showMusicSheet = !showMusicSheet
                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                AudioEqualizerBars(
                                    color = if (isMediaPlaying) HollerTheme.colors.Accent else HollerTheme.colors.TextSecondary,
                                    level = if (isMediaPlaying) 65 else 0,
                                    isRecording = false,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = currentTrack?.title ?: "车外音乐",
                                    color = HollerTheme.colors.TextPrimary,
                                    fontSize = 13.5.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.widthIn(max = 110.dp)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(24.dp)
                                        .clip(CircleShape)
                                        .background(HollerTheme.colors.Brand)
                                        .clickable {
                                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                            mediaEngine.togglePlayPause()
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = if (isMediaPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                                        contentDescription = "播放暂停",
                                        tint = HollerTheme.colors.TextPrimary,
                                        modifier = Modifier.size(15.dp)
                                    )
                                }
                            }
                        }
                    } else {
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(24.dp))
                                .background(if (showMusicSheet) HollerTheme.colors.BrandElevated else Color.Transparent)
                            .clickable(enabled = !active) {
                                haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                showVoiceSheet = false
                                showPhrase = false
                                showSoundFxSheet = false
                                showMusicSheet = !showMusicSheet
                            }
                            .padding(horizontal = 16.dp, vertical = 11.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Rounded.MusicNote,
                                    contentDescription = "车外音乐",
                                    tint = HollerTheme.colors.TextPrimary,
                                    modifier = Modifier.size(17.dp)
                                )
                                Text(
                                    "车外音乐",
                                    color = HollerTheme.colors.TextPrimary,
                                    fontSize = 14.5.sp,
                                    fontWeight = if (showMusicSheet) FontWeight.SemiBold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            // ---- 变声面板：独立 popover 气泡 ----
            // 不是「操作栏被吸出一层」（那样面板与 DOCK 连成一块，边界消失、失去了「浮起来」的层级感），
            // 而是自成一体的浮层：圆角矩形 + 底部一枚小三角，三角尖指回 DOCK 的「变声」项，形成连接。
            // 材质仍是同一套玻璃（半透明渐变 + 顶部高光描边 + 柔投影），只是投影更重，明确它是浮在上层的东西。
            androidx.compose.animation.AnimatedVisibility(
                visible = showVoiceSheet,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    // DOCK 顶边在距底 100dp；气泡含箭尖的底边落在 109dp → 箭尖与 DOCK 之间留 9dp 呼吸
                    .padding(bottom = 109.dp),
                enter = fadeIn(tween(140)) + scaleIn(
                    animationSpec = tween(240, easing = FastOutSlowInEasing),
                    initialScale = 0.90f,
                    transformOrigin = TransformOrigin(bubbleOriginX, 1f)
                ),
                exit = fadeOut(tween(120)) + scaleOut(
                    animationSpec = tween(160, easing = FastOutSlowInEasing),
                    targetScale = 0.94f,
                    transformOrigin = TransformOrigin(bubbleOriginX, 1f)
                ),
            ) {
                val bubbleShape = PopoverBubbleShape(
                    anchorX = bubbleAnchorX,
                    cornerDp = 22f,
                    arrowHalfWidthDp = 9.5f,
                    arrowHeightDp = 9f,
                )
                Box(
                    modifier = Modifier
                        .onGloballyPositioned { it ->
                            bubbleLeftPx = it.boundsInWindow().left
                            bubbleWidthPx = it.size.width.toFloat()
                        }
                        // 投影：浅色薄玻璃需要远层（浮起高度）+ 近层（贴身接触阴影）双层才立得住；
                        // 深色是实心面，单层即可，多叠只会显闷
                        .shadow(
                            elevation = 20.dp,
                            shape = bubbleShape,
                            ambientColor = Color(0x261C2C40),
                            spotColor = Color(0x401C2C40),
                        )
                        .then(
                            if (glassDark) Modifier else Modifier.shadow(
                                elevation = 6.dp,
                                shape = bubbleShape,
                                ambientColor = Color(0x1A1C2C40),
                                spotColor = Color(0x241C2C40),
                            )
                        )
                        .glassFinish(bubbleShape, bubbleGlass)
                        // 底部留出三角的高度：内容只在「圆角矩形」部分内居中，不侵入箭尖
                        .padding(bottom = 9.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        VoiceEffect.entries.forEach { effect ->
                            val selected = effect == voiceEffect
                            val iconVector = when (effect) {
                                VoiceEffect.NATIVE -> Icons.Rounded.Mic
                                VoiceEffect.UNCLE -> Icons.Rounded.RecordVoiceOver
                                VoiceEffect.LOLI -> Icons.Rounded.AutoAwesome
                                VoiceEffect.ROBOT -> Icons.Rounded.SmartToy
                                VoiceEffect.ELECTRO -> Icons.Rounded.GraphicEq
                                VoiceEffect.MONSTER -> Icons.Rounded.Whatshot
                            }

                            // 选项块：未选中完全透空——底板就是气泡那块玻璃，不叠第二层实心底；
                            // 只有当前音效给出 AccentSoft 淡绿底 + Accent 描边，像气泡上被按下去的一项
                            Row(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(18.dp))
                                    .background(if (selected) HollerTheme.colors.AccentSoft else Color.Transparent)
                                    .border(
                                        BorderStroke(
                                            1.dp,
                                            if (selected) HollerTheme.colors.Accent else Color.Transparent
                                        ),
                                        RoundedCornerShape(18.dp)
                                    )
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        voiceEffect = effect
                                        store.voiceEffect = effect
                                        engine.setVoiceEffect(effect)
                                        pushEngineConfig()
                                        showVoiceSheet = false
                                        hint = "已切换为：${effect.label}（${effect.desc}）"
                                    }
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                androidx.compose.material3.Icon(
                                    imageVector = iconVector,
                                    contentDescription = effect.label,
                                    tint = if (selected) HollerTheme.colors.Accent else HollerTheme.colors.TextPrimary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = effect.label,
                                    color = if (selected) HollerTheme.colors.AccentText else HollerTheme.colors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }


            // Settings Gear Bottom Left (车规级大触控热区与饱满图标)
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    // 与底部功能栏共享视觉中心线：齿轮高 58dp → bottom 41dp，对齐功能栏（44dp + ~54dp/2）
                    .padding(start = 24.dp, bottom = 41.dp)
                    // 录音期间与 DOCK 同步禁用并压暗，避免误触弹出设置页盖住录音反馈
                    .alpha(if (active) 0.32f else 1f)
                    .size(58.dp)
                    .shadow(
                        elevation = 10.dp,
                        shape = CircleShape,
                        ambientColor = Color(0x1F1C2C40),
                        spotColor = Color(0x2E1C2C40),
                    )
                    .clip(CircleShape)
                    .background(
                        Brush.verticalGradient(
                            listOf(
                                HollerTheme.colors.GlassSurface,
                                HollerTheme.colors.GlassSurfaceBottom
                            )
                        )
                    )
                    .border(
                        BorderStroke(
                            1.dp,
                            Brush.verticalGradient(
                                listOf(
                                    HollerTheme.colors.GlassEdgeTop,
                                    HollerTheme.colors.GlassEdgeBottom
                                )
                            )
                        ),
                        CircleShape
                    )
                    .clickable(enabled = !active) {
                        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                        showVoiceSheet = false
                        showSettings = true
                    },
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.Settings,
                    contentDescription = "系统设置",
                    tint = HollerTheme.colors.TextPrimary,
                    modifier = Modifier.size(30.dp)
                )
            }


            // 快捷语音：类似 Hey 的居中悬浮卡片面板（即点即播）
            AnimatedVisibility(
                visible = showPhrase,
                enter = scaleIn(initialScale = 0.92f, animationSpec = tween(240, easing = FastOutSlowInEasing)) + fadeIn(tween(180)),
                exit = scaleOut(targetScale = 0.92f, animationSpec = tween(180, easing = FastOutSlowInEasing)) + fadeOut(tween(180)),
                modifier = Modifier.fillMaxSize()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showPhrase = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 620.dp)
                            .fillMaxWidth(0.85f)
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                            .clip(RoundedCornerShape(28.dp))
                            .background(HollerTheme.colors.BrandElevated)
                            .border(
                                BorderStroke(1.dp, HollerTheme.colors.Stroke),
                                RoundedCornerShape(28.dp)
                            )
                            .padding(horizontal = 24.dp, vertical = 22.dp)
                    ) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {


                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "快捷语音",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = HollerTheme.colors.TextPrimary
                                    )
                                    Spacer(Modifier.height(2.dp))
                                    Text(
                                        text = "点击条目即刻向车外扬声器播报",
                                        fontSize = 12.sp,
                                        color = HollerTheme.colors.TextSecondary
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(HollerTheme.colors.Brand)
                                        .clickable { showPhrase = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "关闭",
                                        tint = HollerTheme.colors.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(Modifier.height(18.dp))

                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 340.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(phraseList, key = { it.id }) { item ->
                                    val isLaunch = store.launchIndex == item.id
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(16.dp))
                                            .background(HollerTheme.colors.Brand)
                                            .border(
                                                BorderStroke(
                                                    1.dp,
                                                    HollerTheme.colors.Stroke.copy(alpha = 0.6f)
                                                ),
                                                RoundedCornerShape(16.dp)
                                            )
                                            .clickable {
                                                showPhrase = false
                                                playPhraseAudio(item)
                                            }
                                            .padding(horizontal = 16.dp, vertical = 12.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier
                                                .weight(1f)
                                                .padding(end = 12.dp)
                                        ) {
                                            if (isLaunch) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Rounded.Star,
                                                    contentDescription = "默认优先播报",
                                                    tint = Color(0xFFFFB300),
                                                    modifier = Modifier.size(17.dp)
                                                )
                                            }
                                            Text(
                                                text = item.text,
                                                color = HollerTheme.colors.TextPrimary,
                                                fontSize = 15.sp,
                                                fontWeight = if (isLaunch) FontWeight.SemiBold else FontWeight.Medium,
                                                modifier = Modifier.weight(1f, fill = false),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(HollerTheme.colors.BrandElevated),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.AutoMirrored.Rounded.VolumeUp,
                                                    contentDescription = "播报",
                                                    tint = HollerTheme.colors.Accent,
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .size(36.dp)
                                                    .clip(CircleShape)
                                                    .background(HollerTheme.colors.BrandElevated)
                                                    .clickable {
                                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                                        store.remove(item.id)
                                                        phraseList = store.items()
                                                        HollerForegroundService.update(context)
                                                        hint = "已删除快捷语"
                                                    },
                                                contentAlignment = Alignment.Center
                                            ) {
                                                androidx.compose.material3.Icon(
                                                    imageVector = Icons.Rounded.Delete,
                                                    contentDescription = "删除",
                                                    tint = HollerTheme.colors.TextSecondary.copy(alpha = 0.7f),
                                                    modifier = Modifier.size(18.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }

                            Spacer(Modifier.height(14.dp))

                            // 底部常驻单行输入栏（对齐 Hey! 设计：输入框 + 圆形加号按钮，无需语音按钮，即输即加）
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(48.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(24.dp))
                                        .background(HollerTheme.colors.Brand)
                                        .border(
                                            BorderStroke(1.dp, HollerTheme.colors.Stroke),
                                            RoundedCornerShape(24.dp)
                                        )
                                        .padding(horizontal = 16.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    if (newPhraseInput.isEmpty()) {
                                        Text(
                                            text = "输入自定义播报文本…",
                                            color = HollerTheme.colors.TextSecondary.copy(alpha = 0.55f),
                                            fontSize = 14.sp
                                        )
                                    }
                                    BasicTextField(
                                        value = newPhraseInput,
                                        onValueChange = { newPhraseInput = it },
                                        singleLine = true,
                                        textStyle = TextStyle(
                                            color = HollerTheme.colors.TextPrimary,
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.Normal
                                        ),
                                        cursorBrush = SolidColor(HollerTheme.colors.Accent),
                                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                        keyboardActions = KeyboardActions(
                                            onDone = {
                                                val t = newPhraseInput.trim()
                                                if (t.isNotEmpty()) {
                                                    store.add(t)
                                                    phraseList = store.items()
                                                    newPhraseInput = ""
                                                    HollerForegroundService.update(context)
                                                    hint = "已新增快捷语：$t"
                                                }
                                            }
                                        ),
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }

                                val canAdd = newPhraseInput.isNotBlank()
                                val btnBgColor by animateColorAsState(
                                    targetValue = if (canAdd) HollerTheme.colors.Accent else HollerTheme.colors.Brand,
                                    animationSpec = tween(180)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(48.dp)
                                        .clip(CircleShape)
                                        .background(btnBgColor)
                                        .border(
                                            BorderStroke(
                                                1.dp,
                                                if (canAdd) HollerTheme.colors.Accent else HollerTheme.colors.Stroke
                                            ),
                                            CircleShape
                                        )
                                        .clickable(enabled = canAdd) {
                                            val t = newPhraseInput.trim()
                                            if (t.isNotEmpty()) {
                                                store.add(t)
                                                phraseList = store.items()
                                                newPhraseInput = ""
                                                HollerForegroundService.update(context)
                                                hint = "已新增快捷语：$t"
                                            }
                                        },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Rounded.Add,
                                        contentDescription = "添加",
                                        tint = if (canAdd) Color.White else HollerTheme.colors.TextSecondary.copy(alpha = 0.45f),
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // 趣味车外音效专属弹窗 (Sound Fx Sheet)
            SoundFxSheet(
                visible = showSoundFxSheet,
                onDismiss = { showSoundFxSheet = false },
                engine = engine,
                scope = scope,
                onStatusChange = { hint = it }
            )

            // 车外音乐播放专属弹窗 (Exterior Music Sheet / Boombox)
            ExteriorMusicSheet(
                visible = showMusicSheet,
                onDismiss = { showMusicSheet = false },
                mediaEngine = mediaEngine,
                isPlaying = isMediaPlaying,
                currentTrack = currentTrack,
                positionMs = mediaPos,
                durationMs = mediaDur,
                tracks = mediaTracks,
                playMode = mediaPlayMode,
                duckingEnabled = store.duckingEnabled,
                onDuckingChange = {
                    store.duckingEnabled = it
                    mediaEngine.isDuckingEnabled = it
                    hint = if (it) "已开启车外音频闪避 (喊话时自动避让压低)" else "已关闭车外音频闪避"
                },
                onImportClick = { safAudioPicker.launch(arrayOf("audio/*")) },
                onScanClick = {
                    scope.launch(Dispatchers.IO) {
                        val scanned = LocalMediaScanner.scanAll(context)
                        withContext(Dispatchers.Main) {
                            mediaEngine.setPlaylist(scanned)
                            hint = "已重新扫描到 ${scanned.size} 首本地音乐"
                        }
                    }
                }
            )

            // 车外音频输出设备选择弹窗 (Device Selector Sheet)
            val availableDevices = remember {
                val am = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
                am.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter { dev ->
                    dev.type != AudioDeviceInfo.TYPE_TELEPHONY &&
                    dev.type != AudioDeviceInfo.TYPE_FM_TUNER &&
                    dev.type != AudioDeviceInfo.TYPE_TV_TUNER
                }
            }
            DeviceSelectorSheet(
                visible = showDeviceSelector,
                onDismiss = { showDeviceSelector = false },
                currentRoute = route,
                availableDevices = availableDevices,
                preferredDeviceId = store.outputDeviceId.takeIf { it >= 0 },
                onSelectDevice = { devId ->
                    store.outputDeviceId = devId ?: -1
                    refreshRouting(context)
                    hint = if (devId != null) "已切换车外广播输出设备" else "已恢复智能自动匹配通道"
                    showDeviceSelector = false
                },
                onOpenSettings = {
                    showDeviceSelector = false
                    showSettings = true
                }
            )
        }
        
        // Full screen dialog overlays
        // Full screen / Modal dialog overlays
        if (showSettings) {
            androidx.compose.ui.window.Dialog(
                onDismissRequest = { showSettings = false },
                properties = androidx.compose.ui.window.DialogProperties(usePlatformDefaultWidth = false)
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.65f))
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) { showSettings = false },
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 32.dp, vertical = 24.dp)
                            .clip(RoundedCornerShape(28.dp))
                            .background(HollerTheme.colors.BgBase)
                            .border(BorderStroke(1.dp, HollerTheme.colors.Stroke), RoundedCornerShape(28.dp))
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null
                            ) {}
                    ) {
                        Column(Modifier.fillMaxSize().padding(24.dp)) {
                            Row(
                                Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(36.dp)
                                            .clip(CircleShape)
                                            .background(HollerTheme.colors.BrandElevated),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        androidx.compose.material3.Icon(
                                            imageVector = Icons.Rounded.Settings,
                                            contentDescription = null,
                                            tint = HollerTheme.colors.Accent,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                    Column {
                                        Text(
                                            "系统设置与参数诊断",
                                            fontSize = 20.sp,
                                            color = HollerTheme.colors.TextPrimary,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            "悬浮窗 / 日志联动 / 车规级音频路由与设备自检",
                                            fontSize = 12.sp,
                                            color = HollerTheme.colors.TextSecondary
                                        )
                                    }
                                }
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .background(HollerTheme.colors.BrandElevated)
                                        .clickable { showSettings = false },
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Rounded.Close,
                                        contentDescription = "关闭",
                                        tint = HollerTheme.colors.TextSecondary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                            Spacer(Modifier.height(16.dp))
                            Box(Modifier.weight(1f)) {
                                SettingsPage(
                                    context = context,
                                    store = store,
                                    engine = engine,
                                    route = route,
                                    onReresolve = { refreshRouting(context) },
                                    onHint = { hint = it },
                                )
                            }
                        }
                    }
                }
            }
        }
        

    }
}


/** 列表行内的小动作按钮（车机触控优化版，支持图标 + 文字，精致高质感）。 */
@Composable
private fun MiniAction(
    label: String,
    onClick: () -> Unit,
    primary: Boolean = false,
    active: Boolean = false,
    danger: Boolean = false,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
) {
    val bg = when {
        primary -> HollerTheme.colors.Accent
        active -> HollerTheme.colors.AccentSoft
        danger -> HollerTheme.colors.Danger.copy(alpha = 0.15f)
        else -> HollerTheme.colors.BgBase.copy(alpha = 0.5f)
    }
    val fg = when {
        primary -> Color(0xFF06231B)
        // 文本档：浅色主题下把强调/警示色压深，保证 12sp 小字在浅底上的可读性
        active -> HollerTheme.colors.AccentText
        danger -> HollerTheme.colors.DangerText
        else -> HollerTheme.colors.TextPrimary
    }
    val border = when {
        primary -> HollerTheme.colors.Accent
        active -> HollerTheme.colors.Accent
        danger -> HollerTheme.colors.Danger.copy(alpha = 0.4f)
        else -> HollerTheme.colors.Stroke
    }

    Box(
        modifier = Modifier
            .height(36.dp)
            .clip(HollerShapes.Control)
            .background(bg)
            .border(BorderStroke(1.dp, border), HollerShapes.Control)
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(horizontal = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (icon != null) {
                androidx.compose.material3.Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = fg,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                label,
                color = fg,
                fontSize = 12.sp,
                fontWeight = if (primary || active) FontWeight.Bold else FontWeight.Medium
            )
        }
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

    // 播报参数与快捷语库状态
    var rate by remember { mutableStateOf(store.speechRate) }
    var pitch by remember { mutableStateOf(store.pitch) }
    var chime by remember { mutableStateOf(store.chimeEnabled) }
    var repeatCount by remember { mutableStateOf(store.repeatCount) }
    var phraseListItems by remember { mutableStateOf(store.items()) }

    // 悬浮窗外观（透明度与尺寸、贴边收纳）
    var alpha by remember { mutableStateOf(store.floatingAlpha) }
    var floatSize by remember { mutableStateOf(store.floatingSize.toFloat()) }
    var autoDock by remember { mutableStateOf(store.floatingAutoDock) }
    var dockMode by remember { androidx.compose.runtime.mutableIntStateOf(store.floatingDockMode) }

    // 通知栏与自启配置
    var notifyQuick by remember { mutableStateOf(store.notifyQuickActions) }
    var bootAuto by remember { mutableStateOf(store.bootAutoStart) }

    // 设备清单分类与精简折叠
    var deviceTab by remember { androidx.compose.runtime.mutableIntStateOf(0) }
    var deviceFilter by remember { mutableStateOf("") }
    var isDeviceListExpanded by remember { mutableStateOf(false) }

    // 需要文本输入的对话框（关键词自定义 / 设备 id 手填）
    var dialogKind by remember { mutableStateOf(DialogKind.NONE) }
    var editingPhraseItem by remember { mutableStateOf<PhraseStore.Item?>(null) }
    var isAddingPhrase by remember { mutableStateOf(false) }

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
                                    color = if (selected) HollerTheme.colors.AccentText else HollerTheme.colors.TextSecondary,
                                    fontSize = HollerType.Caption,
                                )
                            }
                        }
                    }
                }
            }
        }

        // ---------------- 快捷语播报参数 ----------------
        item {
            HollerCard(title = "快捷语播报参数", subtitle = "TTS 语音生成与防喧扰配置") {
                HollerSlider(
                    value = rate,
                    onValueChange = {
                        rate = it
                        store.speechRate = it
                    },
                    range = 0.5f..2.0f,
                    valueLabel = "语速 ${"%.1f".format(rate)}",
                )
                Spacer(Modifier.height(6.dp))
                HollerSlider(
                    value = pitch,
                    onValueChange = {
                        pitch = it
                        store.pitch = it
                    },
                    range = 0.5f..2.0f,
                    valueLabel = "音调 ${"%.1f".format(pitch)}",
                )
                Spacer(Modifier.height(14.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("优雅提示前导音（叮咚）", color = HollerTheme.colors.TextPrimary, fontSize = HollerType.Body)
                        Text("播报前先响一声轻柔和弦提示音，更显文明礼貌", color = HollerTheme.colors.TextSecondary, fontSize = HollerType.Caption)
                    }
                    Spacer(Modifier.width(12.dp))
                    HollerSwitch(
                        checked = chime,
                        onCheckedChange = {
                            chime = it
                            store.chimeEnabled = it
                            onHint(if (it) "已开启前导提示音" else "已关闭前导提示音")
                        }
                    )
                }
                Spacer(Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("自动连播次数", color = HollerTheme.colors.TextPrimary, fontSize = HollerType.Body)
                        Text("在嘈杂路况下可自动重复 2 次或 3 次", color = HollerTheme.colors.TextSecondary, fontSize = HollerType.Caption)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1, 2, 3).forEach { count ->
                            HollerChip(
                                label = "${count}次",
                                selected = repeatCount == count,
                                onClick = {
                                    repeatCount = count
                                    store.repeatCount = count
                                    onHint("已设置自动连播 ${count} 次")
                                }
                            )
                        }
                    }
                }
            }
        }

        // ---------------- 快捷语库管理 ----------------
        item {
            HollerCard(title = "快捷语库管理", subtitle = "共 ${phraseListItems.size} 条") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    phraseListItems.forEach { item ->
                        val isLaunch = store.launchIndex == item.id
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(HollerShapes.Control)
                                .background(HollerTheme.colors.BgBase.copy(alpha = 0.5f))
                                .border(
                                    BorderStroke(1.dp, HollerTheme.colors.Stroke),
                                    HollerShapes.Control
                                )
                                .clickable {
                                    editingPhraseItem = item
                                }
                                .padding(horizontal = 14.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                item.text,
                                color = HollerTheme.colors.TextPrimary,
                                fontSize = HollerType.Body,
                                fontWeight = FontWeight.Normal,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 12.dp)
                            )
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                // 设为常用（🌟星标）按钮
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            store.launchIndex = if (isLaunch) -1L else item.id
                                            phraseListItems = store.items()
                                            HollerForegroundService.update(context)
                                            FloatingTalkService.refreshAppearance(context)
                                            onHint(if (isLaunch) "已取消常用快捷语" else "已设为常用快捷语：${item.text}")
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = if (isLaunch) Icons.Rounded.Star else Icons.Rounded.StarBorder,
                                        contentDescription = if (isLaunch) "取消常用快捷语" else "设为常用快捷语",
                                        tint = if (isLaunch) Color(0xFFFFB300) else HollerTheme.colors.TextSecondary.copy(alpha = 0.45f),
                                        modifier = Modifier.size(20.dp)
                                    )
                                }

                                // 编辑按钮
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            editingPhraseItem = item
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Rounded.Edit,
                                        contentDescription = "编辑快捷短语",
                                        tint = HollerTheme.colors.TextSecondary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }

                                // 删除按钮（纯红垃圾桶，无红框）
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clip(CircleShape)
                                        .clickable {
                                            store.remove(item.id)
                                            phraseListItems = store.items()
                                            HollerForegroundService.update(context)
                                            FloatingTalkService.refreshAppearance(context)
                                            onHint("已删除快捷语")
                                        },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    androidx.compose.material3.Icon(
                                        imageVector = Icons.Rounded.Delete,
                                        contentDescription = "删除",
                                        tint = HollerTheme.colors.DangerText,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        HollerButton("添加快捷短语", primary = true, modifier = Modifier.weight(1f), onClick = {
                            isAddingPhrase = true
                        })
                        HollerButton("恢复预置常用语料", primary = false, modifier = Modifier.weight(1f), onClick = {
                            phraseListItems = store.reset()
                            HollerForegroundService.update(context)
                            FloatingTalkService.refreshAppearance(context)
                            onHint("已恢复预置语料")
                        })
                    }
                }
            }
        }

        // ---------------- 快捷触发方式 ----------------
        item {
            HollerCard(title = "快捷触发方式", subtitle = "通知栏快捷控制 / 桌面悬浮按钮") {
                ToggleRow(
                    title = "下拉通知中心快捷卡片",
                    desc = "在中控顶栏下滑展开的通知中心常驻快捷控制面板，支持一键播报与录音（注：Android系统机制限制顶栏仅支持下拉抽屉展示通知）",
                    checked = notifyQuick,
                    onCheckedChange = { want ->
                        store.notifyQuickActions = want
                        notifyQuick = want
                        HollerForegroundService.update(context)
                        onHint(if (want) "已开启下拉通知中心快捷卡片" else "已关闭下拉通知中心卡片")
                    },
                )

                Spacer(Modifier.height(12.dp))

                ToggleRow(
                    title = "车机常驻悬浮控制",
                    desc = "Flyme Auto 2.0 旗舰车规级微晶控制岛：常驻顶栏/贴边收纳/一触即喊",
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
                    Spacer(Modifier.height(14.dp))
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = HollerTheme.colors.Brand,
                        border = BorderStroke(1.dp, HollerTheme.colors.Stroke),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Column(modifier = Modifier.padding(14.dp)) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .background(HollerTheme.colors.Accent, CircleShape)
                                )
                                Text(
                                    "贴边灵动悬浮图标",
                                    color = HollerTheme.colors.TextPrimary,
                                    fontSize = 13.sp,
                                    fontWeight = androidx.compose.ui.text.font.FontWeight.SemiBold,
                                )
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "支持全屏自由拖动，松手自动贴边收纳；轻触图标立即展开，支持按住快速喊话与快捷常用语播报，4.5s 智能收纳不遮挡车机界面。",
                                color = HollerTheme.colors.TextSecondary,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                    }

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
                        "悬浮透明度",
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
                        range = 44f..80f,
                        valueLabel = "${floatSize.toInt()}dp",
                    )
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "悬浮图标基准尺寸",
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
                    // 自定义关键词（自由输入）
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
                    valueColor = if (granted) HollerTheme.colors.AccentText else HollerTheme.colors.DangerText,
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
                    valueColor = if (route?.device != null) HollerTheme.colors.AccentText else HollerTheme.colors.TextPrimary,
                )
                Spacer(Modifier.height(6.dp))
                LabelValueRow(
                    "手动指定输出",
                    if (store.outputDeviceId >= 0) "id=${store.outputDeviceId}" else "未指定（自动）",
                    valueColor = if (store.outputDeviceId >= 0) HollerTheme.colors.AccentText else HollerTheme.colors.TextSecondary,
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
                        "记下序号，即可锁定车外喇叭的真实 usage。",
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
                                color = if (r.played) HollerTheme.colors.TextPrimary else HollerTheme.colors.DangerText,
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
            val cfgDevices = route?.configDevices
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
                Spacer(Modifier.height(10.dp))

                // 1. 当前固定状态总览与一键恢复自动
                val fixedOutput = outputs.firstOrNull { it.id == store.outputDeviceId }
                val fixedInput = inputs.firstOrNull { it.id == store.inputDeviceId }
                val hasFixed = store.outputDeviceId >= 0 || store.inputDeviceId >= 0

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(HollerShapes.Control)
                        .background(if (hasFixed) HollerTheme.colors.AccentSoft.copy(alpha = 0.5f) else HollerTheme.colors.BgBase.copy(alpha = 0.45f))
                        .border(
                            BorderStroke(1.dp, if (hasFixed) HollerTheme.colors.Accent.copy(alpha = 0.5f) else HollerTheme.colors.Stroke.copy(alpha = 0.6f)),
                            HollerShapes.Control
                        )
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = if (fixedOutput != null) "固定输出: ${fixedOutput.productName} (id=${fixedOutput.id})" else "输出路由: 智能自动匹配通道 (推荐)",
                            color = if (fixedOutput != null) HollerTheme.colors.AccentText else HollerTheme.colors.TextPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        if (fixedInput != null) {
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "固定录音: ${fixedInput.productName} (id=${fixedInput.id})",
                                color = HollerTheme.colors.AccentText,
                                fontSize = 11.5.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    if (hasFixed) {
                        MiniAction(
                            label = "恢复自动",
                            onClick = {
                                store.outputDeviceId = -1
                                store.inputDeviceId = -1
                                onReresolve()
                                onHint("已恢复智能自动匹配")
                            }
                        )
                    }
                }

                Spacer(Modifier.height(10.dp))

                // 2. 类别分段选择器 (Segmented Tab)
                val tabs = buildList {
                    add("输出通道 (${outputs.size})")
                    add("输入通道 (${inputs.size})")
                    if (!cfgDevices.isNullOrEmpty()) {
                        add("车机拓扑 (${cfgDevices.size})")
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(18.dp))
                        .background(HollerTheme.colors.BgBase.copy(alpha = 0.6f))
                        .padding(3.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    tabs.forEachIndexed { index, tabTitle ->
                        val isSelected = deviceTab == index
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(15.dp))
                                .background(if (isSelected) HollerTheme.colors.BrandElevated else Color.Transparent)
                                .clickable {
                                    deviceTab = index
                                    deviceFilter = ""
                                }
                                .padding(vertical = 7.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = tabTitle,
                                color = if (isSelected) HollerTheme.colors.TextPrimary else HollerTheme.colors.TextSecondary,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }

                // 3. Tab 内容展示
                when (deviceTab) {
                    0, 1 -> {
                        val isOutput = deviceTab == 0
                        val currentList = if (isOutput) outputs else inputs
                        val selectedId = if (isOutput) store.outputDeviceId else store.inputDeviceId

                        // 快速过滤搜索框（当设备数量较多时展示）
                        if (currentList.size > 4) {
                            Spacer(Modifier.height(8.dp))
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(HollerShapes.Control)
                                    .background(HollerTheme.colors.BgBase.copy(alpha = 0.45f))
                                    .border(BorderStroke(1.dp, HollerTheme.colors.Stroke.copy(alpha = 0.5f)), HollerShapes.Control)
                                    .padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                BasicTextField(
                                    value = deviceFilter,
                                    onValueChange = { deviceFilter = it },
                                    singleLine = true,
                                    textStyle = TextStyle(
                                        color = HollerTheme.colors.TextPrimary,
                                        fontSize = 12.sp
                                    ),
                                    modifier = Modifier.weight(1f),
                                    decorationBox = { innerTextField ->
                                        if (deviceFilter.isEmpty()) {
                                            Text(
                                                "快速筛选设备名或 ID (如: 50, BUS)...",
                                                color = HollerTheme.colors.TextSecondary.copy(alpha = 0.65f),
                                                fontSize = 12.sp
                                            )
                                        }
                                        innerTextField()
                                    }
                                )
                                if (deviceFilter.isNotEmpty()) {
                                    Text(
                                        text = "清除",
                                        color = HollerTheme.colors.AccentText,
                                        fontSize = 11.5.sp,
                                        modifier = Modifier.clickable { deviceFilter = "" }
                                    )
                                }
                            }
                        }

                        val filteredList = remember(currentList, deviceFilter) {
                            if (deviceFilter.isBlank()) currentList
                            else currentList.filter {
                                it.productName.toString().contains(deviceFilter, ignoreCase = true) ||
                                it.id.toString().contains(deviceFilter.trim()) ||
                                AudioRouteResolver.typeName(it.type).contains(deviceFilter, ignoreCase = true)
                            }
                        }

                        // 排序：已固定的目标置顶展示
                        val sortedList = remember(filteredList, selectedId) {
                            filteredList.sortedByDescending { it.id == selectedId }
                        }

                        // 精简折叠：未搜索且超过 6 项时，默认只展示前 6 项（3 列 2 行）
                        val displayList = if (!isDeviceListExpanded && deviceFilter.isBlank() && sortedList.size > 6) {
                            sortedList.take(6)
                        } else {
                            sortedList
                        }

                        Spacer(Modifier.height(8.dp))

                        if (displayList.isEmpty()) {
                            EmptyHint(if (deviceFilter.isNotEmpty()) "未找到符合条件的通道" else "未枚举到通道")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                displayList.chunked(3).forEach { rowTriplet ->
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        rowTriplet.forEach { dev ->
                                            Box(modifier = Modifier.weight(1f)) {
                                                DevicePickCard(
                                                    device = dev,
                                                    selected = selectedId == dev.id,
                                                    onPick = {
                                                        if (isOutput) {
                                                            if (store.outputDeviceId == dev.id) {
                                                                store.outputDeviceId = -1
                                                                onReresolve()
                                                                onHint("已取消固定，恢复智能自动匹配")
                                                            } else {
                                                                store.outputDeviceId = dev.id
                                                                onReresolve()
                                                                onHint("输出设备已固定为 id=${dev.id}")
                                                            }
                                                        } else {
                                                            if (store.inputDeviceId == dev.id) {
                                                                store.inputDeviceId = -1
                                                                onReresolve()
                                                                onHint("已取消固定，恢复智能自动匹配")
                                                            } else {
                                                                store.inputDeviceId = dev.id
                                                                onReresolve()
                                                                onHint("录音设备已固定为 id=${dev.id}")
                                                            }
                                                        }
                                                    }
                                                )
                                            }
                                        }
                                        repeat(3 - rowTriplet.size) {
                                            Spacer(modifier = Modifier.weight(1f))
                                        }
                                    }
                                }
                            }
                        }

                        // 展开 / 收起折叠切换按钮
                        if (sortedList.size > 6 && deviceFilter.isBlank()) {
                            Spacer(Modifier.height(8.dp))
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(HollerShapes.Control)
                                    .background(HollerTheme.colors.BgBase.copy(alpha = 0.5f))
                                    .clickable { isDeviceListExpanded = !isDeviceListExpanded }
                                    .padding(vertical = 8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = if (isDeviceListExpanded) "收起" else "展开全部 (${sortedList.size}) ▾",
                                    color = HollerTheme.colors.AccentText,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    2 -> {
                        // 车机拓扑配置
                        Spacer(Modifier.height(8.dp))
                        if (cfgDevices.isNullOrEmpty()) {
                            EmptyHint("未获取到车机音频拓扑配置")
                        } else {
                            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                cfgDevices.forEach { (dev, ctx) ->
                                    Text(
                                        "· $dev → ${ctx.joinToString("/")}",
                                        color = HollerTheme.colors.TextSecondary,
                                        fontSize = HollerType.Caption,
                                        fontFamily = FontFamily.Monospace,
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(HollerShapes.Control)
                                            .background(HollerTheme.colors.BgBase.copy(alpha = 0.35f))
                                            .padding(horizontal = 10.dp, vertical = 6.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // ---------------- 关于与技术支持 ----------------
        item {
            HollerCard(title = "关于与技术支持") {
                // 版本从 PackageManager 读，避免与 build.gradle.kts 的 versionName 脱节
                val versionLabel = remember {
                    runCatching {
                        val pi = context.packageManager.getPackageInfo(context.packageName, 0)
                        "v${pi.versionName} (build ${pi.longVersionCode})"
                    }.getOrDefault("未知")
                }
                val openUrl: (String) -> Unit = { url ->
                    val ok = runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        )
                    }.isSuccess
                    if (!ok) onHint("未找到可打开链接的应用")
                }

                LabelValueRow("应用", "Holler · 车外喊话")
                Spacer(Modifier.height(6.dp))
                LabelValueRow("版本", versionLabel)
                Spacer(Modifier.height(6.dp))
                LabelValueRow("作者", "vongzh")
                Spacer(Modifier.height(2.dp))
                LinkRow(
                    label = "项目主页",
                    value = "github.com/vongzh/holler",
                    onClick = { openUrl("https://github.com/vongzh/holler") },
                )
                LinkRow(
                    label = "开源协议",
                    value = "Apache License 2.0",
                    onClick = { openUrl("https://www.apache.org/licenses/LICENSE-2.0") },
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    "Copyright 2026 vongzh，基于 Apache-2.0 协议开源。" +
                        "转载、修改或再分发时，请保留本版权与许可声明，并在改动过的文件中注明修改。",
                    color = HollerTheme.colors.TextSecondary,
                    fontSize = HollerType.Caption,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "第三方组件：AndroidX / Jetpack Compose、Kotlin 标准库、kotlinx.coroutines、" +
                        "Material Icons Extended（均为 Apache License 2.0）。" +
                        "界面语言参考 Flyme Auto，未包含其任何源码或专有素材。",
                    color = HollerTheme.colors.TextSecondary,
                    fontSize = HollerType.Caption,
                    lineHeight = 16.sp,
                )
                Spacer(Modifier.height(12.dp))
                LabelValueRow("音频架构", "自研车规级低延迟实时音频管道", mono = false)
                Spacer(Modifier.height(6.dp))
                LabelValueRow("DSP 引擎", engine.dspDescription(), mono = false)
                Spacer(Modifier.height(6.dp))
                LabelValueRow(
                    "变声核心",
                    "纯 Kotlin 车规级实时数字信号处理 (DSP) 引擎",
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    "免责声明与安全守则",
                    color = HollerTheme.colors.TextPrimary,
                    fontSize = HollerType.Body,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                Text(
                    "1. 功能域边界：本软件严格限定于座舱信息娱乐域（车外定向扬声与语音广播），绝不涉及、不介入、亦不篡改任何车辆底层动力系统、转向机构、制动控制、安全气囊或车身网关控制指令。\n" +
                        "2. 文明守则：使用车外外放与广播功能时，请严格遵守《中华人民共和国道路交通安全法》及所在地相关法律法规，文明用车，切勿在医院、学校等禁鸣区域或夜间扰民。\n" +
                        "3. 责任承担：驾驶人须对行车过程及周边安全负全部责任。本软件按开源协议“原样”提供，开发者不对因使用本软件产生的任何交通违规、行车事故、人身财产损失或争议承担法律责任。",
                    color = HollerTheme.colors.TextSecondary,
                    fontSize = HollerType.Caption,
                    lineHeight = 17.sp,
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

    // ---- 文本输入对话框：编辑已有快捷短语 ----
    editingPhraseItem?.let { item ->
        TextInputDialog(
            initial = item.text,
            onDismiss = { editingPhraseItem = null },
            onConfirm = { text ->
                val t = text.trim()
                editingPhraseItem = null
                if (t.isNotEmpty() && t != item.text) {
                    store.rename(item.id, t)
                    phraseListItems = store.items()
                    HollerForegroundService.update(context)
                    FloatingTalkService.refreshAppearance(context)
                    onHint("快捷短语已更新")
                }
            }
        )
    }

    // ---- 文本输入对话框：新增快捷短语 ----
    if (isAddingPhrase) {
        TextInputDialog(
            initial = "",
            onDismiss = { isAddingPhrase = false },
            onConfirm = { text ->
                val t = text.trim()
                isAddingPhrase = false
                if (t.isNotEmpty()) {
                    store.add(t)
                    phraseListItems = store.items()
                    HollerForegroundService.update(context)
                    FloatingTalkService.refreshAppearance(context)
                    onHint("已添加快捷短语：$t")
                }
            }
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

/**
 * 紧凑小卡片式设备项：
 * 点击整张卡片直接选中/取消，选中展示主题色边框与状态徽章，告别冗余按钮与横向长条。
 */
@Composable
private fun DevicePickCard(
    device: android.media.AudioDeviceInfo,
    selected: Boolean,
    onPick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(HollerShapes.Control)
            .background(
                if (selected) HollerTheme.colors.AccentSoft else HollerTheme.colors.BgBase.copy(alpha = 0.35f)
            )
            .border(
                BorderStroke(
                    if (selected) 1.5.dp else 1.dp,
                    if (selected) HollerTheme.colors.Accent else HollerTheme.colors.Stroke.copy(alpha = 0.6f)
                ),
                HollerShapes.Control,
            )
            .clickable { onPick() }
            .padding(horizontal = 11.dp, vertical = 9.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = "${device.productName}",
                color = HollerTheme.colors.TextPrimary,
                fontSize = 12.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(Modifier.height(2.dp))
            Text(
                text = "id=${device.id} · ${AudioRouteResolver.typeName(device.type)}",
                color = if (selected) HollerTheme.colors.AccentText else HollerTheme.colors.TextSecondary,
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        if (selected) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(HollerTheme.colors.Accent),
                contentAlignment = Alignment.Center
            ) {
                androidx.compose.material3.Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = "已固定",
                    tint = HollerTheme.colors.OnAccent,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

/** 关于页的「可点击跳转」行：左标签 + 右值 + 链接图标（整行车规触控热区）。 */
@Composable
private fun LinkRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(HollerShapes.Control)
            .clickable { onClick() }
            .padding(horizontal = 10.dp, vertical = 9.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = HollerTheme.colors.TextSecondary, fontSize = HollerType.Caption)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text(
                value,
                color = HollerTheme.colors.AccentText,
                fontSize = HollerType.Caption,
                fontWeight = FontWeight.Medium,
            )
            androidx.compose.material3.Icon(
                imageVector = Icons.Rounded.Link,
                contentDescription = null,
                tint = HollerTheme.colors.AccentText,
                modifier = Modifier.size(14.dp),
            )
        }
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
