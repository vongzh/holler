package com.vongzh.holler.ui

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import androidx.core.content.ContextCompat
import com.vongzh.holler.R
import com.vongzh.holler.audio.dsp.VoiceEffect
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.sin

/**
 * 车规级流光微晶悬浮对讲与播报控件（Flyme Auto 2.0 Alive Crystal Design）。
 *
 * 核心设计：
 *   1. 自由拖动贴边图标（COLLAPSED）：
 *      - 使用官方精美软件图标作为主体，搭配微晶倒角与状态呼吸光环；
 *      - 自由平滑拖动：屏幕任意位置均可停留；拖到屏幕左/右边缘 56dp 内自动磁吸贴边；
 *   2. 双功能控制面板（EXPANDED）：
 *      - 【🎙️ 快速喊话】：操作逻辑全面升级，兼顾「长按对讲」与「单击录音/再次单击发送」，
 *        具备熔岩红高亮状态与实时声浪律动柱，驾驶盲操绝不漏发；
 *      - 【📢 快捷语音】：直观展示当前常用语（去除尾部杂乱标点），支持轻点一键车外播报，
 *        并支持【⟳ 换一句】快速轮换预设短语；
 *      - 右上角带【✕】微型收拢按钮，5 秒闲置或双击自动优雅收回。
 */
class FloatingTalkView(context: Context) : View(context) {

    enum class Mode {
        COLLAPSED,     // 贴边悬浮图标（全屏自由拖动，边缘贴边，轻触展开）
        EXPANDED,      // 展开双功能控制面板（快速喊话 + 快捷语音）

        // 向后兼容保留
        DOCKED_TOP,
        DOCKED_LEFT,
        DOCKED_RIGHT,
        ORB
    }

    /**
     * 新势力车规收起态形态方案：
     * 1. PEBBLE: 特斯拉 / 小鹏天玑风格 —— 极简一体化微晶纯圆纽（纯单层，彻底去除双层内圈）
     * 2. MINI_PILL: 理想 Mind GPT / 灵动卡片风格 —— 同构灵动贴边迷你小胶囊（58 × 42dp，R21）
     * 3. EDGE_TAB: 蔚来 Banyan 风格 —— 贴边磁吸半岛拉手（44 × 48dp，平直贴边，内侧圆角）
     */
    enum class DockStyle {
        PEBBLE,
        MINI_PILL,
        EDGE_TAB
    }

    companion object {
        // 深色模式色彩令牌（Flyme Auto 2.0 曜石黑晶）
        private const val DARK_BG_START = 0xF8151F2C.toInt()
        private const val DARK_BG_END = 0xF40D141E.toInt()
        private const val DARK_STROKE_TOP = 0x4DFFFFFF.toInt()
        private const val DARK_STROKE_BOT = 0x1A476385.toInt()
        private const val DARK_CHIP_BG = 0xFF182433.toInt()
        private const val DARK_CHIP_STROKE = 0xFF25364B.toInt()
        private const val DARK_CHIP_PRESSED = 0xFF223145.toInt()

        private const val DARK_IDLE = 0xFF1AE5A3.toInt()        // 翡翠绿
        private const val DARK_REC_START = 0xFFE11D48.toInt()   // 熔岩红
        private const val DARK_REC_END = 0xFF9F1239.toInt()
        private const val DARK_PLAY_START = 0xFF0284C7.toInt()  // 极光青
        private const val DARK_PLAY_END = 0xFF075985.toInt()
        private const val DARK_TEXT = 0xFFF8FAFC.toInt()
        private const val DARK_TEXT_SEC = 0xFF94A3B8.toInt()

        // 浅色模式色彩令牌（Flyme Auto 2.0 珍珠瓷白，拒绝暗沉脏灰）
        private const val LIGHT_BG_START = 0xFFFFFFFF.toInt()
        private const val LIGHT_BG_END = 0xFFF8FAFC.toInt()
        private const val LIGHT_STROKE_TOP = 0xFFFFFFFF.toInt()
        private const val LIGHT_STROKE_BOT = 0xFFCBD5E1.toInt()
        private const val LIGHT_CHIP_BG = 0xFFF1F5F9.toInt()
        private const val LIGHT_CHIP_STROKE = 0xFFE2E8F0.toInt()
        private const val LIGHT_CHIP_PRESSED = 0xFFE2E8F0.toInt()

        private const val LIGHT_IDLE = 0xFF00A86B.toInt()       // 车规翠绿
        private const val LIGHT_REC_START = 0xFFE11D48.toInt()  // 宝石熔岩红
        private const val LIGHT_REC_END = 0xFFBE123C.toInt()
        private const val LIGHT_PLAY_START = 0xFF0284C7.toInt() // 钴蓝青
        private const val LIGHT_PLAY_END = 0xFF0369A1.toInt()
        private const val LIGHT_TEXT = 0xFF0F172A.toInt()       // 高对比曜石黑
        private const val LIGHT_TEXT_SEC = 0xFF64748B.toInt()   // 清晰次级灰
    }

    private val density = resources.displayMetrics.density
    private fun dp(v: Float) = v * density

    /** 软件原生应用图标 */
    private val appIconDrawable: Drawable? by lazy {
        runCatching {
            context.packageManager.getApplicationIcon(context.packageName)
        }.getOrNull() ?: ContextCompat.getDrawable(context, R.mipmap.ic_launcher_round)
    }

    /** 悬浮图标基准尺寸 (dp) - 方案 1 特斯拉/小鹏天玑极简一体微晶纯纽定稿 (48 × 48dp) */
    var iconSizeDp: Int = 48
        set(v) {
            field = v
            invalidate()
        }

    val iconSizePx: Int get() = dp(iconSizeDp.toFloat()).toInt()
    var dockStyle: DockStyle = DockStyle.PEBBLE
        set(v) {
            field = v
            invalidate()
        }

    val topBarWidthPx: Int get() = dp(232f).toInt()
    val topBarHeightPx: Int get() = dp(38f).toInt()
    val dockedWidthPx: Int get() = when (dockStyle) {
        DockStyle.MINI_PILL -> dp(58f).toInt()
        DockStyle.EDGE_TAB -> dp(44f).toInt()
        else -> iconSizePx
    }
    val dockedHeightPx: Int get() = when (dockStyle) {
        DockStyle.MINI_PILL -> dp(42f).toInt()
        DockStyle.EDGE_TAB -> dp(48f).toInt()
        else -> iconSizePx
    }
    val expandedWidthPx: Int get() = dp(276f).toInt()
    val expandedHeightPx: Int get() = dp(52f).toInt()

    /** 当前运行模式，默认贴边悬浮图标 */
    var mode: Mode = Mode.COLLAPSED
        set(v) {
            field = v
            invalidate()
        }

    /** 是否靠屏幕左半边（决定向左还是向右展开） */
    var isDockedLeft: Boolean = true
        set(v) {
            field = v
            invalidate()
        }

    /** 是否深色模式 */
    var isDarkTheme: Boolean = true
        set(v) {
            field = v
            invalidate()
        }

    /** 喊话引擎状态：0=待命, 1=录音中, 2=播报中 */
    var state: Int = 0
        set(v) {
            field = v
            postInvalidate()
        }

    /** 电平 0~100，驱动声波律动条 */
    var level: Int = 0
        set(v) {
            field = v
            if (state == 1 || mode == Mode.EXPANDED) {
                invalidate()
            }
        }

    /** 变声效果 */
    var effect: VoiceEffect = VoiceEffect.NATIVE
        set(v) {
            field = v
            invalidate()
        }

    /** 预设快捷语列表 */
    var phraseList: List<String> = emptyList()
        set(v) {
            field = v
            if (v.isNotEmpty() && currentPhraseIndex >= v.size) {
                currentPhraseIndex = 0
            }
            invalidate()
        }

    /** 当前选中的快捷短语索引 */
    var currentPhraseIndex: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    /** 预设快捷语音短标（清理末尾中英文逗号与句号） */
    var phraseSnippet: String = "文明行车"
        get() {
            val raw = if (phraseList.isNotEmpty() && currentPhraseIndex in phraseList.indices) {
                phraseList[currentPhraseIndex]
            } else {
                field
            }
            return raw.trimEnd('，', ',', '。', '.', '！', '!')
        }
        set(v) {
            field = v.trimEnd('，', ',', '。', '.', '！', '!')
            invalidate()
        }

    /** 兼容字段 */
    var dockMode: Int = 1
    var autoDock: Boolean = true

    // 回调事件
    var onWindowLayout: ((width: Int, height: Int, x: Int, y: Int) -> Unit)? = null
    var onSavePosition: ((x: Int, y: Int) -> Unit)? = null
    var onStartTalk: (() -> Unit)? = null
    var onEndTalk: (() -> Unit)? = null
    var onSpeakPhrase: ((text: String?) -> Unit)? = null

    // 绘图画笔
    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val chipPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val chipStrokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val accentPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        isFakeBoldText = true
        letterSpacing = 0.02f
    }
    private val subTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        letterSpacing = 0.01f
    }

    // 交互状态追踪
    private var pressedZone = 0 // 0=None, 1=Left Talk Keypad, 2=Right Phrase Keypad, 3=Close Button, 4=Switch Phrase Button
    private var currentWinX = 0
    private var currentWinY = 0
    private var downRawX = 0f
    private var downRawY = 0f
    private var lastTouchRawX = 0f
    private var lastTouchRawY = 0f
    private var isDragging = false
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val handler = Handler(Looper.getMainLooper())
    private var isTalking = false
    private var longPressRunnable: Runnable? = null
    private var autoCollapseRunnable: Runnable? = null
    private var lastTapTime = 0L

    // 微动与转场动画
    private var touchScale = 1f
    private var scaleAnimator: ValueAnimator? = null

    // 灵动形变展开/收起过渡动画 (0f = COLLAPSED, 1f = EXPANDED)
    private var morphProgress = 0f
    private var morphAnimator: ValueAnimator? = null

    // 车规座舱呼吸能量微光动画 (0f ~ 1f)
    private var breathPhase = 0f
    private var breathAnimator: ValueAnimator? = null

    init {
        startBreathAnimation()
    }

    private fun startBreathAnimation() {
        breathAnimator?.cancel()
        breathAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 1300
            repeatMode = ValueAnimator.REVERSE
            repeatCount = ValueAnimator.INFINITE
            interpolator = AccelerateDecelerateInterpolator()
            addUpdateListener {
                breathPhase = it.animatedValue as Float
                if (state != 0 || mode == Mode.EXPANDED || morphProgress > 0f) {
                    invalidate()
                }
            }
            start()
        }
    }

    fun setInitialWindowPosition(x: Int, y: Int) {
        currentWinX = x
        currentWinY = y
        val screenW = resources.displayMetrics.widthPixels
        isDockedLeft = (x + iconSizePx / 2) < (screenW / 2)
    }

    // ------------------------------------------------------------------
    // 绘图入口
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        Log.i("Holler/Floating", "onDraw called: w=$width, h=$height, mode=$mode, morphProgress=$morphProgress, state=$state")
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        canvas.save()
        if (touchScale != 1f) {
            canvas.scale(touchScale, touchScale, w / 2f, h / 2f)
        }

        if (mode == Mode.EXPANDED || morphProgress > 0f) {
            drawMorphingPanel(canvas, w, h, morphProgress)
        } else {
            drawDockedIcon(canvas, w, h)
        }

        canvas.restore()
    }

    // ------------------------------------------------------------------
    // 1. 自由拖动贴边微晶图标（高质感车规车外对讲悬浮球）
    // ------------------------------------------------------------------

    private fun drawDockedIcon(canvas: Canvas, w: Float, h: Float) {
        when (dockStyle) {
            DockStyle.MINI_PILL -> drawLiAutoMiniCapsule(canvas, w, h)
            DockStyle.EDGE_TAB -> drawNioEdgeTab(canvas, w, h)
            else -> drawTeslaPebble(canvas, w, h)
        }
    }

    /**
     * 方案 1（特斯拉 / 小鹏天玑风格）——「极简一体化微晶纯纽」
     * 特点：彻底剔除双层内圈与靶心结构，单层贯通微晶陶瓷质感，
     * 图标大比例居中，线条饱满，纯粹、高级、克制。
     */
    private fun drawTeslaPebble(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f - dp(2.0f)

        // (1) 真实悬浮软羽化双层阴影 (高级悬浮景深)
        val shadowAlpha = if (isDarkTheme) 0x48000000 else 0x1A000000
        val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = shadowAlpha
        }
        canvas.drawCircle(cx, cy + dp(2.2f), r + dp(0.8f), p1)
        p1.color = (shadowAlpha and 0x00FFFFFF) or (if (isDarkTheme) 0x2A000000 else 0x10000000)
        canvas.drawCircle(cx, cy + dp(1.0f), r, p1)

        // (2) 状态呼吸微光外晕 (待命翡翠微光，录音珊瑚红微光) - 驱动柔和呼吸动效
        when (state) {
            1 -> {
                val auraR = r + dp(1.5f + 3.2f * breathPhase)
                val auraAlpha = (if (isDarkTheme) 0x30 else 0x20) + (0x35 * breathPhase).toInt()
                accentPaint.color = (auraAlpha shl 24) or 0x00E11D48
                canvas.drawCircle(cx, cy, auraR, accentPaint)
            }
            2 -> {
                val auraR = r + dp(1.5f + 3.2f * breathPhase)
                val auraAlpha = (if (isDarkTheme) 0x30 else 0x20) + (0x35 * breathPhase).toInt()
                accentPaint.color = (auraAlpha shl 24) or 0x000284C7
                canvas.drawCircle(cx, cy, auraR, accentPaint)
            }
            else -> {
                val auraR = r + dp(1.0f + 1.6f * breathPhase)
                val auraAlpha = (if (isDarkTheme) 0x14 else 0x0C) + (0x16 * breathPhase).toInt()
                accentPaint.color = (auraAlpha shl 24) or 0x001AE5A3
                canvas.drawCircle(cx, cy, auraR, accentPaint)
            }
        }

        // (3) 单层微晶陶瓷圆盘底座 (纯净一体，绝无任何同心内圈)
        val bgShader = when (state) {
            1 -> LinearGradient(
                cx, cy - r, cx, cy + r,
                if (isDarkTheme) intArrayOf(DARK_REC_START, DARK_REC_END)
                else intArrayOf(LIGHT_REC_START, LIGHT_REC_END),
                null, Shader.TileMode.CLAMP
            )
            2 -> LinearGradient(
                cx, cy - r, cx, cy + r,
                if (isDarkTheme) intArrayOf(DARK_PLAY_START, DARK_PLAY_END)
                else intArrayOf(LIGHT_PLAY_START, LIGHT_PLAY_END),
                null, Shader.TileMode.CLAMP
            )
            else -> LinearGradient(
                cx, cy - r, cx, cy + r,
                if (isDarkTheme) intArrayOf(DARK_BG_START, DARK_BG_END)
                else intArrayOf(LIGHT_BG_START, LIGHT_BG_END),
                null, Shader.TileMode.CLAMP
            )
        }
        fillPaint.shader = bgShader
        canvas.drawCircle(cx, cy, r, fillPaint)
        fillPaint.shader = null

        // (4) 精致对讲车规 2-AC 麦克风图标 (居中、纯粹、高清晰)
        val emblemCol = when (state) {
            1, 2 -> 0xFFFFFFFF.toInt()
            else -> if (isDarkTheme) DARK_IDLE else LIGHT_IDLE
        }
        val slitCol = when (state) {
            1, 2 -> 0x55000000.toInt()
            else -> if (isDarkTheme) 0xFF0D141E.toInt() else 0xFFFFFFFF.toInt()
        }
        // 图标大小动态按 28/48 比例自适应（48dp 规格下为 28dp，黄金留白 10dp）
        val micSize = minOf(w, h) * (28f / 48f)
        drawIcon2AC(canvas, cx, cy, size = micSize, color = emblemCol, slitColor = slitCol)

        // (5) 外轮廓车规钛金/微光高光描边 (1.0dp)
        val strokeShader = when (state) {
            1 -> LinearGradient(
                cx, cy - r, cx, cy + r,
                intArrayOf(0x99FFFFFF.toInt(), 0x33FFFFFF.toInt()),
                null, Shader.TileMode.CLAMP
            )
            else -> LinearGradient(
                cx, cy - r, cx, cy + r,
                if (isDarkTheme) intArrayOf(DARK_STROKE_TOP, DARK_STROKE_BOT)
                else intArrayOf(LIGHT_STROKE_TOP, LIGHT_STROKE_BOT),
                null, Shader.TileMode.CLAMP
            )
        }
        strokePaint.shader = strokeShader
        strokePaint.color = 0
        strokePaint.strokeWidth = dp(1.0f)
        canvas.drawCircle(cx, cy, r - dp(0.5f), strokePaint)
        strokePaint.shader = null

        // 录音态时，外圈加上细腻的珊瑚红微光描边
        if (state == 1) {
            strokePaint.color = if (isDarkTheme) 0x80E11D48.toInt() else 0x66E11D48.toInt()
            strokePaint.strokeWidth = dp(1.0f)
            canvas.drawCircle(cx, cy, r - dp(0.5f), strokePaint)
        }
    }

    /**
     * 方案 2（理想 Mind GPT / 灵动卡片风格）——「同构灵动贴边迷你小胶囊」
     * 尺寸 58 × 42dp，R21 全圆角小胶囊。与展开态 276 × 52dp 灵动流体胶囊具有同构胶囊基因，
     * 宽屏车机视觉极度协调，单层微晶磨砂底，绝无内圈！
     */
    private fun drawLiAutoMiniCapsule(canvas: Canvas, w: Float, h: Float) {
        val rect = RectF(dp(2.0f), dp(2.0f), w - dp(2.0f), h - dp(2.0f))
        val r = rect.height() / 2f
        val cx = w / 2f
        val cy = h / 2f

        // (1) 软景深外阴影
        val shadowAlpha = if (isDarkTheme) 0x48000000 else 0x18000000
        val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = shadowAlpha
        }
        val shadowRect = RectF(rect.left, rect.top + dp(2f), rect.right, rect.bottom + dp(3.5f))
        canvas.drawRoundRect(shadowRect, r, r, p1)

        // (2) 状态呼吸微光
        when (state) {
            1 -> {
                accentPaint.color = if (isDarkTheme) 0x55E11D48.toInt() else 0x3DE11D48.toInt()
                canvas.drawRoundRect(RectF(rect.left - dp(2f), rect.top - dp(2f), rect.right + dp(2f), rect.bottom + dp(2f)), r + dp(2f), r + dp(2f), accentPaint)
            }
            2 -> {
                accentPaint.color = if (isDarkTheme) 0x550284C7.toInt() else 0x3D0284C7.toInt()
                canvas.drawRoundRect(RectF(rect.left - dp(2f), rect.top - dp(2f), rect.right + dp(2f), rect.bottom + dp(2f)), r + dp(2f), r + dp(2f), accentPaint)
            }
            else -> {
                accentPaint.color = if (isDarkTheme) 0x201AE5A3.toInt() else 0x1400A86B.toInt()
                canvas.drawRoundRect(RectF(rect.left - dp(1.5f), rect.top - dp(1.5f), rect.right + dp(1.5f), rect.bottom + dp(1.5f)), r + dp(1.5f), r + dp(1.5f), accentPaint)
            }
        }

        // (3) 单层微晶陶瓷小胶囊底座 (单层贯通，纯净无内圈)
        val bgShader = when (state) {
            1 -> LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                if (isDarkTheme) intArrayOf(DARK_REC_START, DARK_REC_END)
                else intArrayOf(LIGHT_REC_START, LIGHT_REC_END),
                null, Shader.TileMode.CLAMP
            )
            2 -> LinearGradient(
                rect.left, rect.top, rect.right, rect.bottom,
                if (isDarkTheme) intArrayOf(DARK_PLAY_START, DARK_PLAY_END)
                else intArrayOf(LIGHT_PLAY_START, LIGHT_PLAY_END),
                null, Shader.TileMode.CLAMP
            )
            else -> LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                if (isDarkTheme) intArrayOf(DARK_BG_START, DARK_BG_END)
                else intArrayOf(LIGHT_BG_START, LIGHT_BG_END),
                null, Shader.TileMode.CLAMP
            )
        }
        fillPaint.shader = bgShader
        canvas.drawRoundRect(rect, r, r, fillPaint)
        fillPaint.shader = null

        // (4) 居中 2-AC 麦克风图标 (与小胶囊纵横比完美契合)
        val emblemCol = when (state) {
            1, 2 -> 0xFFFFFFFF.toInt()
            else -> if (isDarkTheme) DARK_IDLE else LIGHT_IDLE
        }
        val slitCol = when (state) {
            1, 2 -> 0x55000000.toInt()
            else -> if (isDarkTheme) 0xFF0D141E.toInt() else 0xFFFFFFFF.toInt()
        }
        drawIcon2AC(canvas, cx, cy, size = dp(25f), color = emblemCol, slitColor = slitCol)

        // (5) 外轮廓钛金微光描边
        val strokeShader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            if (isDarkTheme) intArrayOf(DARK_STROKE_TOP, DARK_STROKE_BOT)
            else intArrayOf(LIGHT_STROKE_TOP, LIGHT_STROKE_BOT),
            null, Shader.TileMode.CLAMP
        )
        strokePaint.shader = strokeShader
        strokePaint.strokeWidth = dp(1.0f)
        val inset = dp(0.5f)
        canvas.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), r - inset, r - inset, strokePaint)
        strokePaint.shader = null

        // 录音态时，外圈加上细腻的珊瑚红微光描边
        if (state == 1) {
            strokePaint.color = if (isDarkTheme) 0x80E11D48.toInt() else 0x66E11D48.toInt()
            strokePaint.strokeWidth = dp(1.0f)
            canvas.drawRoundRect(RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset), r - inset, r - inset, strokePaint)
        }
    }

    /**
     * 方案 3（蔚来 Banyan 风格）——「贴边磁吸半岛拉手」
     * 贴合屏幕外边缘平直，朝向屏幕内侧全圆角（44 × 48dp，R22）
     * 像原车边缘呼出的精致抽屉拉手，不挡视野，浑然天成。
     */
    private fun drawNioEdgeTab(canvas: Canvas, w: Float, h: Float) {
        val cx = w / 2f
        val cy = h / 2f
        val r = dp(20f)
        val rect = RectF(0f, dp(2.0f), w, h - dp(2.0f))

        // 使用 Path 绘制贴边半圆角（靠右侧时左边全圆角右边平直，靠左侧时右边全圆角左边平直）
        val path = Path().apply {
            if (isDockedLeft) {
                // 左侧平直贴屏，右侧 R20 圆角
                moveTo(0f, rect.top)
                lineTo(w - r, rect.top)
                arcTo(RectF(w - 2 * r, rect.top, w, rect.top + 2 * r), -90f, 90f, false)
                lineTo(w, rect.bottom - r)
                arcTo(RectF(w - 2 * r, rect.bottom - 2 * r, w, rect.bottom), 0f, 90f, false)
                lineTo(0f, rect.bottom)
                close()
            } else {
                // 右侧平直贴屏，左侧 R20 圆角
                moveTo(w, rect.top)
                lineTo(r, rect.top)
                arcTo(RectF(0f, rect.top, 2 * r, rect.top + 2 * r), -90f, -90f, false)
                lineTo(0f, rect.bottom - r)
                arcTo(RectF(0f, rect.bottom - 2 * r, 2 * r, rect.bottom), 180f, -90f, false)
                lineTo(w, rect.bottom)
                close()
            }
        }

        // (1) 软景深阴影
        val shadowAlpha = if (isDarkTheme) 0x48000000 else 0x18000000
        val p1 = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            color = shadowAlpha
        }
        canvas.drawPath(path, p1)

        // (2) 单层微晶陶瓷底座
        val bgShader = when (state) {
            1 -> LinearGradient(
                0f, 0f, w, 0f,
                if (isDarkTheme) intArrayOf(DARK_REC_START, DARK_REC_END)
                else intArrayOf(LIGHT_REC_START, LIGHT_REC_END),
                null, Shader.TileMode.CLAMP
            )
            2 -> LinearGradient(
                0f, 0f, w, 0f,
                if (isDarkTheme) intArrayOf(DARK_PLAY_START, DARK_PLAY_END)
                else intArrayOf(LIGHT_PLAY_START, LIGHT_PLAY_END),
                null, Shader.TileMode.CLAMP
            )
            else -> LinearGradient(
                0f, rect.top, 0f, rect.bottom,
                if (isDarkTheme) intArrayOf(DARK_BG_START, DARK_BG_END)
                else intArrayOf(LIGHT_BG_START, LIGHT_BG_END),
                null, Shader.TileMode.CLAMP
            )
        }
        fillPaint.shader = bgShader
        canvas.drawPath(path, fillPaint)
        fillPaint.shader = null

        // (3) 居中 2-AC 麦克风图标
        val emblemCol = when (state) {
            1, 2 -> 0xFFFFFFFF.toInt()
            else -> if (isDarkTheme) DARK_IDLE else LIGHT_IDLE
        }
        val slitCol = when (state) {
            1, 2 -> 0x55000000.toInt()
            else -> if (isDarkTheme) 0xFF0D141E.toInt() else 0xFFFFFFFF.toInt()
        }
        drawIcon2AC(canvas, cx, cy, size = dp(24f), color = emblemCol, slitColor = slitCol)

        // (4) 外轮廓钛金微光描边
        strokePaint.shader = null
        strokePaint.color = if (isDarkTheme) DARK_STROKE_TOP else LIGHT_STROKE_BOT
        strokePaint.strokeWidth = dp(1.0f)
        canvas.drawPath(path, strokePaint)

        // 录音态时，加上细腻的珊瑚红微光描边
        if (state == 1) {
            strokePaint.color = if (isDarkTheme) 0x80E11D48.toInt() else 0x66E11D48.toInt()
            strokePaint.strokeWidth = dp(1.0f)
            canvas.drawPath(path, strokePaint)
        }
    }

    /**
     * 绘制 2-AC 车规矢量麦克风（2A对称声浪 + 2C双声学微缝）：
     * 居中、高对比、声波对称扩散、坚决不带笨重底托。
     */
    private fun drawIcon2AC(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        size: Float,
        color: Int,
        slitColor: Int? = null
    ) {
        val s = size / 48f

        // 1. 拾音头胶囊主体 (圆角矩形 x=18..30, y=9..29, rx=6)
        accentPaint.color = color
        val bodyRect = RectF(
            cx - 6f * s,
            cy - 15f * s,
            cx + 6f * s,
            cy + 5f * s
        )
        canvas.drawRoundRect(bodyRect, 6f * s, 6f * s, accentPaint)

        // 2. 双声学细缝切线 (来自 2-C，微拟物透气孔)
        val slitPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = slitColor ?: if (color == 0xFFFFFFFF.toInt()) {
                0x55000000.toInt()
            } else {
                if (isDarkTheme) 0xFF0D141E.toInt() else 0xFFFFFFFF.toInt()
            }
            strokeWidth = maxOf(dp(1.0f), 1.8f * s)
            strokeCap = Paint.Cap.ROUND
        }
        val slitHalfW = 3.5f * s
        val slit1Y = cy - 7.5f * s
        val slit2Y = cy - 2.5f * s
        canvas.drawLine(cx - slitHalfW, slit1Y, cx + slitHalfW, slit1Y, slitPaint)
        canvas.drawLine(cx - slitHalfW, slit2Y, cx + slitHalfW, slit2Y, slitPaint)

        // 3. U型声学金属托架 (圆弧 x=12..36, y=23..35, 半径 12, 下半圆 0..180)
        iconPaint.color = color
        iconPaint.strokeWidth = maxOf(dp(1.5f), 3.0f * s)
        iconPaint.style = Paint.Style.STROKE
        iconPaint.strokeCap = Paint.Cap.ROUND
        val cradleRect = RectF(
            cx - 12f * s,
            cy - 1f * s,
            cx + 12f * s,
            cy + 11f * s
        )
        canvas.drawArc(cradleRect, 0f, 180f, false, iconPaint)

        // 4. 精巧立柱把手 (自托架底 cy+11s 向下延伸 7s 到 cy+18s, 纯粹直立短柄)
        canvas.drawLine(cx, cy + 11f * s, cx, cy + 18f * s, iconPaint)

        // 5. 左右两组优雅对称声学能量声浪 (来自 2-A，具备呼吸动效)
        val wavePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.color = color
            style = Paint.Style.STROKE
            strokeWidth = maxOf(dp(1.2f), 2.2f * s)
            strokeCap = Paint.Cap.ROUND
            alpha = 165 // 65% opacity
        }
        val wavePulse = if (state == 1 || state == 2) dp(1.2f) * sin(breathPhase * Math.PI.toFloat()) else 0f
        val leftWaveRect = RectF(
            cx - 20f * s - wavePulse,
            cy - 7f * s,
            cx - 12f * s - wavePulse,
            cy + 7f * s
        )
        canvas.drawArc(leftWaveRect, 135f, 90f, false, wavePaint)

        val rightWaveRect = RectF(
            cx + 12f * s + wavePulse,
            cy - 7f * s,
            cx + 20f * s + wavePulse,
            cy + 7f * s
        )
        canvas.drawArc(rightWaveRect, -45f, 90f, false, wavePaint)
    }

    /**
     * 灵动流体形变转场绘制：在贴边纯纽与灵动胶囊面板之间丝滑插值形变，
     * 消除瞬间弹出的突兀跳针感，实现犹如 Flyme Auto / iOS 灵动岛的高级流体形态延展。
     */
    private fun drawMorphingPanel(canvas: Canvas, w: Float, h: Float, progress: Float) {
        if (progress >= 1f) {
            drawExpandedPanel(canvas, w, h)
            return
        }
        if (progress <= 0f) {
            drawDockedIcon(canvas, w, h)
            return
        }

        val curW = dockedWidthPx + (expandedWidthPx - dockedWidthPx) * progress
        val curH = dockedHeightPx + (expandedHeightPx - dockedHeightPx) * progress
        val curR = curH / 2f

        val rect = if (isDockedLeft) {
            RectF(0f, (h - curH) / 2f, curW, (h + curH) / 2f)
        } else {
            RectF(w - curW, (h - curH) / 2f, w, (h + curH) / 2f)
        }

        // (1) 软景深羽化阴影
        drawSoftShadow(canvas, rect, curR, if (isDarkTheme) 0x55000000 else 0x18000000)

        // (2) 单层微晶磨砂底座
        val bgShader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            if (isDarkTheme) intArrayOf(DARK_BG_START, DARK_BG_END)
            else intArrayOf(LIGHT_BG_START, LIGHT_BG_END),
            null,
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = bgShader
        canvas.drawRoundRect(rect, curR, curR, fillPaint)
        fillPaint.shader = null

        // (3) 外框钛金高光边框
        val strokeShader = LinearGradient(
            0f, rect.top, 0f, rect.bottom,
            if (isDarkTheme) intArrayOf(DARK_STROKE_TOP, DARK_STROKE_BOT)
            else intArrayOf(LIGHT_STROKE_TOP, LIGHT_STROKE_BOT),
            null,
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = strokeShader
        strokePaint.strokeWidth = dp(1.0f)
        val inset = dp(0.5f)
        val strokeRect = RectF(rect.left + inset, rect.top + inset, rect.right - inset, rect.bottom - inset)
        canvas.drawRoundRect(strokeRect, curR - inset, curR - inset, strokePaint)
        strokePaint.shader = null

        // (4) 麦克风图标沿轨迹平滑滑移动画
        val startMicCx = if (isDockedLeft) dockedWidthPx / 2f else (w - dockedWidthPx / 2f)
        val endMicCx = if (isDockedLeft) dp(23f) else (w - expandedWidthPx + dp(23f))
        val curMicCx = startMicCx + (endMicCx - startMicCx) * progress
        val micCy = h / 2f

        val brandCol = if (isDarkTheme) DARK_IDLE else LIGHT_IDLE
        val slitCol = if (isDarkTheme) 0xFF0D141E.toInt() else 0xFFFFFFFF.toInt()
        val micSize = dp(28f) + (dp(21f) - dp(28f)) * progress
        drawIcon2AC(canvas, curMicCx, micCy, size = micSize, color = brandCol, slitColor = slitCol)

        // (5) 展开态内联组件随 progress > 0.35 优雅淡入
        val contentAlpha = ((progress - 0.35f) / 0.65f).coerceIn(0f, 1f)
        if (contentAlpha > 0f) {
            canvas.save()
            canvas.clipRect(rect)
            val alphaInt = (contentAlpha * 255).toInt()

            val originX = if (isDockedLeft) 0f else (w - expandedWidthPx)
            canvas.translate(originX, 0f)

            // 左侧文字
            val talkTextX = dp(43f)
            textPaint.color = if (isDarkTheme) DARK_TEXT else LIGHT_TEXT
            textPaint.alpha = alphaInt
            textPaint.textSize = dp(12.5f)
            canvas.drawText("按住说话", talkTextX, micCy - dp(3.2f), textPaint)

            subTextPaint.color = if (isDarkTheme) DARK_TEXT_SEC else LIGHT_TEXT_SEC
            subTextPaint.alpha = alphaInt
            subTextPaint.textSize = dp(9.2f)
            canvas.drawText("车外对讲", talkTextX, micCy + dp(10.5f), subTextPaint)

            // 中央光学分割线
            drawOpticalDivider(canvas, dp(98f), dp(10f), h - dp(10f))

            // 右侧播报区域内容
            val chipLeft = dp(106f)
            val chipRight = dp(202f)
            val chipTop = dp(9f)
            val chipBottom = h - dp(9f)
            val chipR = (chipBottom - chipTop) / 2f
            val chipRect = RectF(chipLeft, chipTop, chipRight, chipBottom)
            chipPaint.color = if (isDarkTheme) DARK_CHIP_BG else LIGHT_CHIP_BG
            chipPaint.alpha = alphaInt
            canvas.drawRoundRect(chipRect, chipR, chipR, chipPaint)

            drawBroadcastHorn(canvas, chipLeft + dp(13f), micCy, if (isDarkTheme) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt(), 0.95f)

            textPaint.color = if (isDarkTheme) DARK_TEXT else LIGHT_TEXT
            textPaint.alpha = alphaInt
            textPaint.textSize = dp(11.2f)
            val displaySnippet = phraseSnippet.trimEnd('。', '！', '!', '？', '?', '；', ';', '，', ',')
            val maxTextW = dp(82f)
            val measuredSnippet = if (textPaint.measureText(displaySnippet) > maxTextW) {
                var trimmed = displaySnippet
                while (trimmed.isNotEmpty() && textPaint.measureText("$trimmed...") > maxTextW) {
                    trimmed = trimmed.dropLast(1)
                }
                "$trimmed..."
            } else {
                displaySnippet
            }
            canvas.drawText(measuredSnippet, chipLeft + dp(25f), micCy + dp(3.6f), textPaint)

            // 换一句按钮
            val switchCx = dp(222f)
            val switchCy = h / 2f
            drawRoundButton(canvas, switchCx, switchCy, isPressed = false) {
                subTextPaint.color = if (isDarkTheme) 0xFFCBD5E1.toInt() else 0xFF475569.toInt()
                subTextPaint.alpha = alphaInt
                subTextPaint.textSize = dp(12.5f)
                subTextPaint.isFakeBoldText = true
                val sw = subTextPaint.measureText("⟳")
                canvas.drawText("⟳", switchCx - sw / 2f, switchCy + dp(4.0f), subTextPaint)
                subTextPaint.isFakeBoldText = false
            }

            // 关闭按钮
            val closeCx = dp(250f)
            val closeCy = h / 2f
            drawCloseButton(canvas, closeCx, closeCy, isPressed = false)

            canvas.restore()

            textPaint.alpha = 255
            subTextPaint.alpha = 255
            chipPaint.alpha = 255
            strokePaint.alpha = 255
            iconPaint.alpha = 255
        }
    }

    // ------------------------------------------------------------------
    // 2. 方案 A：灵动流体胶囊控制面板（276 × 52dp 单层微晶磨砂玻璃）
    // ------------------------------------------------------------------

    private fun drawExpandedPanel(canvas: Canvas, w: Float, h: Float) {
        val r = h / 2f // R26 两端全圆角闭合
        val rect = RectF(0f, 0f, w, h)

        // (1) 豪华大范围景深阴影 (无死角柔化)
        drawSoftShadow(canvas, rect, r, if (isDarkTheme) 0x55000000 else 0x18000000)

        // 状态分流绘制：待命态 / 录音流体红 / 播报流体蓝
        when (state) {
            1 -> drawRecordingFluidCapsule(canvas, rect, r)
            2 -> drawPlayingFluidCapsule(canvas, rect, r)
            else -> drawIdleFluidCapsule(canvas, rect, r)
        }
    }

    /**
     * 待命态：单层贯通式微晶玻璃长胶囊
     */
    private fun drawIdleFluidCapsule(canvas: Canvas, rect: RectF, r: Float) {
        val w = rect.width()
        val h = rect.height()

        // 1. 单层微晶磨砂底座
        val bgShader = LinearGradient(
            0f, 0f, 0f, h,
            if (isDarkTheme) intArrayOf(DARK_BG_START, DARK_BG_END)
            else intArrayOf(LIGHT_BG_START, LIGHT_BG_END),
            null,
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = bgShader
        canvas.drawRoundRect(rect, r, r, fillPaint)
        fillPaint.shader = null

        // 2. 外框钛金高光边框 (1.0dp)
        val strokeShader = LinearGradient(
            0f, 0f, 0f, h,
            if (isDarkTheme) intArrayOf(DARK_STROKE_TOP, DARK_STROKE_BOT)
            else intArrayOf(LIGHT_STROKE_TOP, LIGHT_STROKE_BOT),
            null,
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = strokeShader
        strokePaint.strokeWidth = dp(1.0f)
        val inset = dp(0.5f)
        canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r - inset, r - inset, strokePaint)
        strokePaint.shader = null

        // --------------------------------------------------------------
        // 左操作区：【按住说话】大圆热区 (x: 0 .. 98dp)
        // --------------------------------------------------------------
        val talkHitRect = RectF(dp(4f), dp(4f), dp(96f), h - dp(4f))
        if (pressedZone == 1) {
            accentPaint.color = if (isDarkTheme) 0x2E1AE5A3.toInt() else 0x2200A86B.toInt()
            canvas.drawRoundRect(talkHitRect, dp(22f), dp(22f), accentPaint)
        }

        // 翡翠光斑底圆 + 2-AC 麦克风图标
        val micCx = dp(23f)
        val micCy = h / 2f
        accentPaint.color = if (isDarkTheme) 0x241AE5A3.toInt() else 0x1A00A86B.toInt()
        canvas.drawCircle(micCx, micCy, dp(15f), accentPaint)
        val brandCol = if (isDarkTheme) DARK_IDLE else LIGHT_IDLE
        drawIcon2AC(canvas, micCx, micCy, size = dp(21f), color = brandCol)

        // “按住说话” + “车外对讲”
        val talkTextX = dp(43f)
        textPaint.color = if (isDarkTheme) DARK_TEXT else LIGHT_TEXT
        textPaint.textSize = dp(12.5f)
        canvas.drawText("按住说话", talkTextX, micCy - dp(3.2f), textPaint)

        subTextPaint.color = if (isDarkTheme) DARK_TEXT_SEC else LIGHT_TEXT_SEC
        subTextPaint.textSize = dp(9.2f)
        canvas.drawText("车外对讲", talkTextX, micCy + dp(10.5f), subTextPaint)

        // --------------------------------------------------------------
        // 极细垂直羽化光学中缝 (x: 96dp)
        // --------------------------------------------------------------
        drawOpticalDivider(canvas, dp(96f), dp(16f), h - dp(16f))

        // --------------------------------------------------------------
        // 右操作区：【快捷播报】大热区 (x: 98 .. 208dp)
        // --------------------------------------------------------------
        val phraseHitRect = RectF(dp(98f), dp(4f), dp(208f), h - dp(4f))
        if (pressedZone == 2) {
            accentPaint.color = if (isDarkTheme) 0x2038BDF8.toInt() else 0x1A0284C7.toInt()
            canvas.drawRoundRect(phraseHitRect, dp(22f), dp(22f), accentPaint)
        }

        // 喇叭徽标
        val hornCx = dp(109f)
        val hornCy = h / 2f
        val hornCol = if (isDarkTheme) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()
        drawBroadcastHorn(canvas, hornCx, hornCy, hornCol, scale = 1.0f)

        // 常用语文本 + “轻点车外播报”
        val phraseTextX = dp(122f)
        val displaySnippet = phraseSnippet.ifEmpty { "文明行车" }
        textPaint.color = if (isDarkTheme) DARK_TEXT else LIGHT_TEXT
        textPaint.textSize = dp(12.0f)

        val maxTextW = dp(82f)
        val measuredSnippet = if (textPaint.measureText(displaySnippet) > maxTextW) {
            var trimmed = displaySnippet
            while (trimmed.isNotEmpty() && textPaint.measureText("$trimmed...") > maxTextW) {
                trimmed = trimmed.dropLast(1)
            }
            "$trimmed..."
        } else {
            displaySnippet
        }
        canvas.drawText(measuredSnippet, phraseTextX, hornCy - dp(3.2f), textPaint)

        subTextPaint.color = if (isDarkTheme) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()
        subTextPaint.textSize = dp(9.0f)
        canvas.drawText("轻点车外播报", phraseTextX, hornCy + dp(10.5f), subTextPaint)

        // --------------------------------------------------------------
        // 功能微按钮：【⟳ 换一句】(x: 210 .. 236dp) 与 【✕ 收拢】(x: 238 .. 268dp)
        // --------------------------------------------------------------
        val switchCx = dp(222f)
        val switchCy = h / 2f
        drawRoundButton(canvas, switchCx, switchCy, isPressed = (pressedZone == 4)) {
            // 绘制换一句符号 ⟳
            subTextPaint.color = if (isDarkTheme) 0xFFCBD5E1.toInt() else 0xFF475569.toInt()
            subTextPaint.textSize = dp(12.5f)
            subTextPaint.isFakeBoldText = true
            val sw = subTextPaint.measureText("⟳")
            canvas.drawText("⟳", switchCx - sw / 2f, switchCy + dp(4.0f), subTextPaint)
            subTextPaint.isFakeBoldText = false
        }

        val closeCx = dp(250f)
        val closeCy = h / 2f
        drawCloseButton(canvas, closeCx, closeCy, isPressed = (pressedZone == 3))
    }

    /**
     * 录音态：一体化高质感流体胶囊（微晶陶瓷底座 + 珊瑚红能量徽标 + 动态声浪）
     */
    private fun drawRecordingFluidCapsule(canvas: Canvas, rect: RectF, r: Float) {
        val w = rect.width()
        val h = rect.height()

        // 1. 微晶磨砂底座 (与待命态保持相同的雅致温润底质，坚决不使用大面积违和大红底)
        val bgShader = LinearGradient(
            0f, 0f, 0f, h,
            if (isDarkTheme) intArrayOf(DARK_BG_START, DARK_BG_END)
            else intArrayOf(LIGHT_BG_START, LIGHT_BG_END),
            null,
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = bgShader
        canvas.drawRoundRect(rect, r, r, fillPaint)
        fillPaint.shader = null

        // 2. 录音态专属：外圈呼吸式珊瑚红柔光环境描边 (1.2dp)
        val strokeShader = LinearGradient(
            0f, 0f, w, 0f,
            if (isDarkTheme) intArrayOf(0x99E11D48.toInt(), 0x4DE11D48.toInt())
            else intArrayOf(0x80E11D48.toInt(), 0x33E11D48.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = strokeShader
        strokePaint.strokeWidth = dp(1.2f)
        val inset = dp(0.6f)
        canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r - inset, r - inset, strokePaint)
        strokePaint.shader = null

        // 3. 左侧焦点：能量珊瑚红圆形徽章 (cx = 23dp, r = 15dp) + 发光 2-AC 麦克风
        val micCx = dp(23f)
        val micCy = h / 2f
        val badgeR = dp(15f)

        // 3a. 徽章外层微光光晕 (Aura halo) 动态呼吸
        val pulseR = badgeR + dp(1.8f + 3.2f * breathPhase)
        val pulseAlpha = (if (isDarkTheme) 0x26 else 0x18) + (0x28 * breathPhase).toInt()
        accentPaint.color = (pulseAlpha shl 24) or 0x00E11D48
        canvas.drawCircle(micCx, micCy, pulseR, accentPaint)

        // 3b. 徽章本体：珊瑚红渐变圆盘
        val micBadgeShader = LinearGradient(
            micCx - badgeR, micCy - badgeR, micCx + badgeR, micCy + badgeR,
            intArrayOf(0xFFF43F5E.toInt(), 0xFFE11D48.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        accentPaint.shader = micBadgeShader
        canvas.drawCircle(micCx, micCy, badgeR, accentPaint)
        accentPaint.shader = null

        // 3c. 徽章中心：纯白发光 2-AC 麦克风图标
        drawIcon2AC(canvas, micCx, micCy, size = dp(21f), color = 0xFFFFFFFF.toInt(), slitColor = 0x55000000.toInt())

        // --------------------------------------------------------------
        // 4. 中间主态：【● 正在录音】+ 5 柱珊瑚红动态声浪柱
        // --------------------------------------------------------------
        val textLeft = dp(45f)

        // 4a. 闪烁呼吸红点
        val dotCx = textLeft + dp(3.5f)
        val dotCy = micCy - dp(3.5f)
        accentPaint.color = 0xFFE11D48.toInt()
        canvas.drawCircle(dotCx, dotCy, dp(3.2f), accentPaint)
        accentPaint.color = 0x33E11D48.toInt()
        canvas.drawCircle(dotCx, dotCy, dp(5.5f), accentPaint)

        // 4b. “正在录音”主标题 (高对比度深色/浅色文本)
        textPaint.color = if (isDarkTheme) DARK_TEXT else LIGHT_TEXT
        textPaint.textSize = dp(12.8f)
        val titleX = dotCx + dp(7.5f)
        canvas.drawText("正在录音", titleX, micCy - dp(3.2f), textPaint)

        // 4c. 5 根动态跳动音频声浪条 (跟随 level 实时跳跃)
        val eqX = titleX + textPaint.measureText("正在录音") + dp(6f)
        drawMiniEqBars(canvas, eqX, micCy - dp(7.5f), 0xFFE11D48.toInt(), isRec = true)

        // 4d. 次级提示文字：“松开立即车外播报”
        subTextPaint.color = if (isDarkTheme) DARK_TEXT_SEC else LIGHT_TEXT_SEC
        subTextPaint.textSize = dp(9.2f)
        canvas.drawText("松开立即车外播报", textLeft, micCy + dp(10.5f), subTextPaint)

        // --------------------------------------------------------------
        // 5. 右侧操作区：精致的【完成发送】互动胶囊小药丸
        // --------------------------------------------------------------
        val sendW = dp(64f)
        val sendH = dp(28f)
        val sendR = sendH / 2f
        val sendRight = w - dp(12f)
        val sendLeft = sendRight - sendW
        val sendTop = micCy - sendH / 2f
        val sendBottom = micCy + sendH / 2f
        val sendRect = RectF(sendLeft, sendTop, sendRight, sendBottom)

        // 胶囊背景：珊瑚红轻底色
        accentPaint.color = if (isDarkTheme) 0x2EE11D48.toInt() else 0x1AE11D48.toInt()
        canvas.drawRoundRect(sendRect, sendR, sendR, accentPaint)

        // 胶囊边框：细腻红描边
        strokePaint.shader = null
        strokePaint.color = if (isDarkTheme) 0x66E11D48.toInt() else 0x4DE11D48.toInt()
        strokePaint.strokeWidth = dp(1.0f)
        canvas.drawRoundRect(sendRect, sendR, sendR, strokePaint)

        // 胶囊文字：“完成发送”
        subTextPaint.color = 0xFFE11D48.toInt()
        subTextPaint.textSize = dp(11.0f)
        subTextPaint.isFakeBoldText = true
        val sendTw = subTextPaint.measureText("完成发送")
        canvas.drawText("完成发送", sendLeft + (sendW - sendTw) / 2f, micCy + dp(3.8f), subTextPaint)
        subTextPaint.isFakeBoldText = false
    }

    /**
     * 播报态：一体化高质感流体胶囊（微晶陶瓷底座 + 极光钴蓝能量徽标）
     */
    private fun drawPlayingFluidCapsule(canvas: Canvas, rect: RectF, r: Float) {
        val w = rect.width()
        val h = rect.height()

        // 1. 微晶磨砂底座 (保持一致的高级车规微晶玻璃质感)
        val bgShader = LinearGradient(
            0f, 0f, 0f, h,
            if (isDarkTheme) intArrayOf(DARK_BG_START, DARK_BG_END)
            else intArrayOf(LIGHT_BG_START, LIGHT_BG_END),
            null,
            Shader.TileMode.CLAMP
        )
        fillPaint.shader = bgShader
        canvas.drawRoundRect(rect, r, r, fillPaint)
        fillPaint.shader = null

        // 2. 播报态：极光钴蓝环境呼吸描边
        val strokeShader = LinearGradient(
            0f, 0f, w, 0f,
            if (isDarkTheme) intArrayOf(0x990284C7.toInt(), 0x4D0284C7.toInt())
            else intArrayOf(0x800284C7.toInt(), 0x330284C7.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = strokeShader
        strokePaint.strokeWidth = dp(1.2f)
        val inset = dp(0.6f)
        canvas.drawRoundRect(RectF(inset, inset, w - inset, h - inset), r - inset, r - inset, strokePaint)
        strokePaint.shader = null

        // 3. 左侧焦点：极光青圆形徽章 (cx = 23dp, r = 15dp) + 发光喇叭
        val hornCx = dp(23f)
        val hornCy = h / 2f
        val badgeR = dp(15f)

        // 3a. 徽章外层微光光晕
        accentPaint.color = if (isDarkTheme) 0x3D0284C7.toInt() else 0x240284C7.toInt()
        canvas.drawCircle(hornCx, hornCy, badgeR + dp(2.5f), accentPaint)

        // 3b. 徽章本体：钴蓝青渐变圆盘
        val hornBadgeShader = LinearGradient(
            hornCx - badgeR, hornCy - badgeR, hornCx + badgeR, hornCy + badgeR,
            intArrayOf(0xFF38BDF8.toInt(), 0xFF0284C7.toInt()),
            null,
            Shader.TileMode.CLAMP
        )
        accentPaint.shader = hornBadgeShader
        canvas.drawCircle(hornCx, hornCy, badgeR, accentPaint)
        accentPaint.shader = null

        // 3c. 徽章中心：纯白发光喇叭
        drawBroadcastHorn(canvas, hornCx, hornCy, 0xFFFFFFFF.toInt(), scale = 1.05f)

        // 4. “车外定向播报中...” + 播报文本
        val textLeft = dp(45f)
        subTextPaint.color = if (isDarkTheme) 0xFF38BDF8.toInt() else 0xFF0284C7.toInt()
        subTextPaint.textSize = dp(9.2f)
        canvas.drawText("车外定向播报中...", textLeft, hornCy - dp(3.5f), subTextPaint)

        // 播报内容文本
        textPaint.color = if (isDarkTheme) DARK_TEXT else LIGHT_TEXT
        textPaint.textSize = dp(12.5f)
        val displaySnippet = "“${phraseSnippet.ifEmpty { "文明行车" }}”"
        val maxTextW = w - textLeft - dp(40f)
        val measuredSnippet = if (textPaint.measureText(displaySnippet) > maxTextW) {
            var trimmed = displaySnippet
            while (trimmed.isNotEmpty() && textPaint.measureText("$trimmed...") > maxTextW) {
                trimmed = trimmed.dropLast(1)
            }
            "$trimmed..."
        } else {
            displaySnippet
        }
        canvas.drawText(measuredSnippet, textLeft, hornCy + dp(10.5f), textPaint)

        // 5. 右侧关闭微按钮
        val closeCx = w - dp(20f)
        val closeCy = h / 2f
        drawCloseButton(canvas, closeCx, closeCy, isPressed = (pressedZone == 3))
    }

    /**
     * 圆形微按钮通用绘制基座
     */
    private inline fun drawRoundButton(
        canvas: Canvas,
        cx: Float,
        cy: Float,
        isPressed: Boolean,
        drawContent: () -> Unit
    ) {
        val r = dp(11f)
        val bgCol = if (isPressed) {
            if (isDarkTheme) 0xFF2D3E54.toInt() else 0xFFCBD5E1.toInt()
        } else {
            if (isDarkTheme) 0xFF1E2B3A.toInt() else 0xFFE2E8F0.toInt()
        }
        fillPaint.color = bgCol
        canvas.drawCircle(cx, cy, r, fillPaint)
        drawContent()
    }

    /**
     * 5根迷你声浪音频律动条柱 (对齐设计稿 5 柱动态声浪)
     */
    private fun drawMiniEqBars(canvas: Canvas, left: Float, centerY: Float, color: Int, isRec: Boolean) {
        val barW = dp(2.0f)
        val gap = dp(1.8f)
        val maxH = dp(13f)
        val ratio = (level / 100f).coerceIn(0f, 1f)

        val factors = floatArrayOf(0.40f, 0.75f, 1.00f, 0.70f, 0.35f)
        val baseH = floatArrayOf(dp(3f), dp(5f), dp(7f), dp(5f), dp(3f))

        accentPaint.color = color
        for (i in 0 until 5) {
            val flutter = if (isRec) dp(1.2f) * sin(breathPhase * 6.28f + i * 1.2f).toFloat() else 0f
            val h = (if (isRec) baseH[i] + maxH * factors[i] * ratio + flutter else baseH[i]).coerceAtLeast(dp(2.5f))
            val bx = left + i * (barW + gap)
            canvas.drawRoundRect(
                RectF(bx, centerY - h / 2f, bx + barW, centerY + h / 2f),
                barW / 2f, barW / 2f,
                accentPaint
            )
        }
    }

    /**
     * 右上角微型【✕】收拢按钮
     */
    private fun drawCloseButton(canvas: Canvas, cx: Float, cy: Float, isPressed: Boolean) {
        val r = dp(11f)
        val bgCol = if (isPressed) {
            if (isDarkTheme) 0xFF2D3E54.toInt() else 0xFFCBD5E1.toInt()
        } else {
            if (isDarkTheme) 0xFF1E2B3A.toInt() else 0xFFE2E8F0.toInt()
        }
        fillPaint.color = bgCol
        canvas.drawCircle(cx, cy, r, fillPaint)

        iconPaint.color = if (isDarkTheme) DARK_TEXT_SEC else 0xFF64748B.toInt()
        iconPaint.strokeWidth = dp(1.3f)
        val d = dp(3.5f)
        canvas.drawLine(cx - d, cy - d, cx + d, cy + d, iconPaint)
        canvas.drawLine(cx + d, cy - d, cx - d, cy + d, iconPaint)
    }

    /**
     * 车规矢量声波喇叭徽标
     */
    private fun drawBroadcastHorn(canvas: Canvas, cx: Float, cy: Float, color: Int, scale: Float) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val s = dp(4.8f) * scale
        val path = Path().apply {
            moveTo(cx - s * 0.65f, cy - s * 0.35f)
            lineTo(cx - s * 0.20f, cy - s * 0.35f)
            lineTo(cx + s * 0.40f, cy - s * 0.75f)
            lineTo(cx + s * 0.40f, cy + s * 0.75f)
            lineTo(cx - s * 0.20f, cy + s * 0.35f)
            lineTo(cx - s * 0.65f, cy + s * 0.35f)
            close()
        }
        canvas.drawPath(path, p)

        iconPaint.color = color
        iconPaint.strokeWidth = dp(1.3f) * scale
        val waveR1 = RectF(cx + s * 0.25f, cy - s * 0.48f, cx + s * 0.90f, cy + s * 0.48f)
        canvas.drawArc(waveR1, -38f, 76f, false, iconPaint)
    }

    /**
     * 中央光学微光分割线
     */
    private fun drawOpticalDivider(canvas: Canvas, x: Float, top: Float, bottom: Float) {
        val divShader = LinearGradient(
            x, top, x, bottom,
            if (isDarkTheme) intArrayOf(0x00FFFFFF, 0x33FFFFFF, 0x00FFFFFF)
            else intArrayOf(0x00CBD5E1, 0xFFCBD5E1.toInt(), 0x00CBD5E1),
            null,
            Shader.TileMode.CLAMP
        )
        strokePaint.shader = divShader
        strokePaint.strokeWidth = dp(1f)
        canvas.drawLine(x, top, x, bottom, strokePaint)
        strokePaint.shader = null
    }

    /**
     * 柔化景深微阴影
     */
    private fun drawSoftShadow(canvas: Canvas, rect: RectF, r: Float, shadowBaseColor: Int) {
        val passPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        passPaint.color = (shadowBaseColor and 0x00FFFFFF) or 0x14000000
        val r1 = RectF(rect.left - dp(1f), rect.top + dp(2f), rect.right + dp(1f), rect.bottom + dp(4.5f))
        canvas.drawRoundRect(r1, r + dp(1f), r + dp(1f), passPaint)

        passPaint.color = (shadowBaseColor and 0x00FFFFFF) or 0x20000000
        val r2 = RectF(rect.left, rect.top + dp(1f), rect.right, rect.bottom + dp(2f))
        canvas.drawRoundRect(r2, r, r, passPaint)
    }

    // ------------------------------------------------------------------
    // 触摸手势交互：平滑增量自由拖动 + 智能贴边 + 直观功能操作
    // ------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downRawX = event.rawX
                downRawY = event.rawY
                lastTouchRawX = event.rawX
                lastTouchRawY = event.rawY
                isDragging = false
                isTalking = false

                cancelAutoCollapseTimer()

                if (mode == Mode.EXPANDED) {
                    val isCloseBtn = (event.x >= dp(236f))
                    val isSwitchBtn = (event.x >= dp(208f) && event.x < dp(236f))

                    pressedZone = when {
                        isCloseBtn -> 3
                        isSwitchBtn -> 4
                        event.x < dp(96f) -> 1 // 左侧快速喊话 (按住说话)
                        else -> 2              // 中间快捷语音播报
                    }

                    // 左侧喊话支持长按 180ms 自动开启录音（兼顾对讲习惯）
                    if (pressedZone == 1 && state != 1) {
                        scheduleLongPressTalk()
                    }
                } else {
                    pressedZone = 0
                }

                invalidate()
                animateScale(0.96f)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastTouchRawX
                val dy = event.rawY - lastTouchRawY
                val totalDist = hypot((event.rawX - downRawX).toDouble(), (event.rawY - downRawY).toDouble())

                if (!isDragging && totalDist > touchSlop) {
                    isDragging = true
                    pressedZone = 0
                    cancelLongPressTalk()

                    if (isTalking) {
                        isTalking = false
                        onEndTalk?.invoke()
                    }

                    // 展开状态下一旦拖动，立即收拢为图标并跟随手指
                    if (mode == Mode.EXPANDED) {
                        mode = Mode.COLLAPSED
                        onWindowLayout?.invoke(iconSizePx, iconSizePx, currentWinX, currentWinY)
                    }
                    invalidate()
                }

                if (isDragging) {
                    val screenW = resources.displayMetrics.widthPixels
                    val screenH = resources.displayMetrics.heightPixels

                    // 增量精准移动，绝不跳针
                    currentWinX = (currentWinX + dx).toInt().coerceIn(0, screenW - width)
                    currentWinY = (currentWinY + dy).toInt().coerceIn(dp(30f).toInt(), screenH - height - dp(30f).toInt())

                    onWindowLayout?.invoke(width, height, currentWinX, currentWinY)
                    lastTouchRawX = event.rawX
                    lastTouchRawY = event.rawY
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val prevZone = pressedZone
                pressedZone = 0
                invalidate()
                animateScale(1f)
                cancelLongPressTalk()

                if (isDragging) {
                    isDragging = false
                    finishDrag()
                } else if (isTalking) {
                    // 长按对讲松手：立即结束录音并发送
                    isTalking = false
                    onEndTalk?.invoke()
                    scheduleAutoCollapseTimer()
                } else {
                    handleClick(event.x, event.y, prevZone)
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun scheduleLongPressTalk() {
        cancelLongPressTalk()
        longPressRunnable = Runnable {
            if (!isDragging && mode == Mode.EXPANDED) {
                isTalking = true
                onStartTalk?.invoke()
            }
        }.also { handler.postDelayed(it, 180) } // 180ms 灵敏响应长按
    }

    private fun cancelLongPressTalk() {
        longPressRunnable?.let { handler.removeCallbacks(it) }
        longPressRunnable = null
    }

    private fun handleClick(localX: Float, localY: Float, zone: Int) {
        val now = System.currentTimeMillis()
        if (now - lastTapTime < 260) {
            // 双击：立即收起面板
            lastTapTime = 0
            collapseToDock()
            return
        }
        lastTapTime = now

        if (mode == Mode.EXPANDED && morphProgress >= 0.7f) {
            when (zone) {
                3 -> {
                    // 点击【✕】：立即收起
                    collapseToDock()
                }
                4 -> {
                    // 点击【⟳ 换一句】：轮换下一条常用语
                    if (phraseList.isNotEmpty()) {
                        currentPhraseIndex = (currentPhraseIndex + 1) % phraseList.size
                        phraseSnippet = phraseList[currentPhraseIndex]
                    }
                    scheduleAutoCollapseTimer()
                }
                1 -> {
                    // 点击左侧【按住说话】：
                    // 如果正在录音，点击即发送；如果待命，点击即开始录音（与长按互补，兼顾所有习惯）
                    if (state == 1) {
                        onEndTalk?.invoke()
                    } else {
                        onStartTalk?.invoke()
                    }
                    scheduleAutoCollapseTimer()
                }
                2 -> {
                    // 点击右侧播报区：
                    if (state == 1) {
                        // 录音状态下点击任意右侧区域立即发送
                        onEndTalk?.invoke()
                    } else if (state == 2) {
                        // 播报状态下点击收起
                        collapseToDock()
                    } else {
                        onSpeakPhrase?.invoke(phraseSnippet)
                    }
                    scheduleAutoCollapseTimer()
                }
            }
        } else if (mode == Mode.COLLAPSED || morphProgress <= 0.3f) {
            // 收起态下轻触悬浮球：立即向内展开灵动流体胶囊！
            expandCapsule()
        }
    }

    /**
     * 拖动释放后处理：自由停放 + 靠近边缘智能磁吸贴边
     */
    private fun finishDrag() {
        val screenW = resources.displayMetrics.widthPixels
        val snapEdgeThreshold = dp(56f).toInt() // 靠近边缘 56dp 内磁吸贴边

        if (currentWinX < snapEdgeThreshold) {
            currentWinX = 0
            isDockedLeft = true
        } else if (currentWinX > screenW - dockedWidthPx - snapEdgeThreshold) {
            currentWinX = screenW - dockedWidthPx
            isDockedLeft = false
        } else {
            // 停在屏幕中间：完全自由保留位置！
            isDockedLeft = (currentWinX + dockedWidthPx / 2) < (screenW / 2)
        }

        mode = Mode.COLLAPSED
        morphProgress = 0f
        morphAnimator?.cancel()
        onWindowLayout?.invoke(dockedWidthPx, dockedHeightPx, currentWinX, currentWinY)
        onSavePosition?.invoke(currentWinX, currentWinY)
    }

    /**
     * 展开为单层灵动流体胶囊控制面板（276 × 52dp）
     */
    fun expandCapsule() {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val newW = expandedWidthPx
        val newH = expandedHeightPx
        val snapLeft = (currentWinX + iconSizePx / 2) < (screenW / 2)
        isDockedLeft = snapLeft

        val isAtLeftEdge = currentWinX <= dp(12f).toInt()
        val isAtRightEdge = currentWinX >= screenW - iconSizePx - dp(12f).toInt()

        val newX = when {
            isAtLeftEdge -> 0
            isAtRightEdge -> screenW - newW
            snapLeft -> currentWinX.coerceAtMost(screenW - newW)
            else -> (currentWinX + iconSizePx - newW).coerceAtLeast(0)
        }
        val newY = currentWinY.coerceIn(
            dp(20f).toInt(),
            screenH - newH - dp(20f).toInt()
        )

        Log.i("Holler/Floating", "expandCapsule: newX=$newX, newY=$newY, newW=$newW, newH=$newH, isAtRightEdge=$isAtRightEdge, curProgress=$morphProgress")
        currentWinX = newX
        currentWinY = newY
        mode = Mode.EXPANDED
        onWindowLayout?.invoke(newW, newH, newX, newY)

        morphAnimator?.cancel()
        morphAnimator = ValueAnimator.ofFloat(morphProgress, 1f).apply {
            duration = 260
            interpolator = DecelerateInterpolator(2.0f)
            addUpdateListener {
                morphProgress = it.animatedValue as Float
                Log.d("Holler/Floating", "morphProgress updated: $morphProgress")
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    Log.i("Holler/Floating", "morphAnimator START")
                }
                override fun onAnimationEnd(animation: Animator) {
                    Log.i("Holler/Floating", "morphAnimator END: morphProgress=$morphProgress")
                }
                override fun onAnimationCancel(animation: Animator) {
                    Log.i("Holler/Floating", "morphAnimator CANCEL: morphProgress=$morphProgress")
                }
            })
            start()
        }
        scheduleAutoCollapseTimer()
    }

    /**
     * 收回为贴边悬浮图标（根据选定 dockStyle 适配尺寸）
     */
    fun collapseToDock() {
        cancelAutoCollapseTimer()
        morphAnimator?.cancel()
        morphAnimator = ValueAnimator.ofFloat(morphProgress, 0f).apply {
            duration = 200
            interpolator = AccelerateInterpolator(1.8f)
            addUpdateListener {
                morphProgress = it.animatedValue as Float
                invalidate()
            }
            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    mode = Mode.COLLAPSED
                    val screenW = resources.displayMetrics.widthPixels
                    val newW = dockedWidthPx
                    val newH = dockedHeightPx
                    val snapLeft = isDockedLeft

                    val isAtLeftEdge = currentWinX <= dp(12f).toInt()
                    val isAtRightEdge = currentWinX >= screenW - expandedWidthPx - dp(12f).toInt()

                    val newX = when {
                        isAtLeftEdge -> 0
                        isAtRightEdge -> screenW - newW
                        snapLeft -> currentWinX.coerceIn(0, screenW - newW)
                        else -> (currentWinX + expandedWidthPx - newW).coerceIn(0, screenW - newW)
                    }

                    currentWinX = newX
                    onWindowLayout?.invoke(newW, newH, newX, currentWinY)
                    onSavePosition?.invoke(currentWinX, currentWinY)
                }
            })
            start()
        }
    }

    /** 兼容调用 */
    fun dockToTop() {
        collapseToDock()
    }

    private fun scheduleAutoCollapseTimer() {
        cancelAutoCollapseTimer()
        if (mode == Mode.EXPANDED && state == 0) {
            autoCollapseRunnable = Runnable {
                collapseToDock()
            }.also { handler.postDelayed(it, 8000) } // 8 秒闲置自动收拢
        }
    }

    private fun cancelAutoCollapseTimer() {
        autoCollapseRunnable?.let { handler.removeCallbacks(it) }
        autoCollapseRunnable = null
    }

    private fun animateScale(target: Float) {
        scaleAnimator?.cancel()
        scaleAnimator = ValueAnimator.ofFloat(touchScale, target).apply {
            duration = 100
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                touchScale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        Log.i("Holler/Floating", "onDetachedFromWindow called!")
        cancelLongPressTalk()
        cancelAutoCollapseTimer()
        scaleAnimator?.cancel()
        morphAnimator?.cancel()
        breathAnimator?.cancel()
        super.onDetachedFromWindow()
    }
}
