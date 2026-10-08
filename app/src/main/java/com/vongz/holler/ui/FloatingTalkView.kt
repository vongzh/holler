package com.vongz.holler.ui

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.view.MotionEvent
import android.view.View
import android.view.animation.DecelerateInterpolator
import com.vongz.holler.audio.dsp.VoiceEffect

/**
 * 悬浮喊话按钮（自绘 View）。
 *
 * 设计（延续 Flyme Auto 语言）：
 *   - 圆形磨砂底盘（深海蓝）+ 细描边
 *   - 中心是「声波喇叭」图形，用同心弧表达
 *   - 待命：青绿描边；录音中：红；播报中：青绿填充
 *   - 按下有轻微缩放（物理仿真，克制）
 *
 * 为什么自绘而不是用 Compose：
 *   悬浮窗在 Service 里，用普通 View 更轻、启动更快，
 *   且避免 Compose 在 WindowManager 里的生命周期复杂度。
 *   视觉令牌与 Compose 侧保持一致（见 Theme.kt）。
 *
 * 交互：
 *   - 单击 → 喊话（短按 0.8s：播放 TTS 短句；长按：按住说话）
 *   - 拖动 → 移动位置（松手吸附到最近的左右边缘）
 *   - 双击 → 隐藏（收起成一个半透明小点）
 */
class FloatingTalkView(context: Context) : View(context) {

    companion object {
        // 深色模式默认色彩
        private const val DARK_BG = 0xCC0A2A43.toInt()
        private const val DARK_STROKE = 0xFF1E3A52.toInt()
        private const val DARK_IDLE = 0xFF21D4A5.toInt()
        private const val DARK_REC = 0xFFC8324B.toInt()
        private const val DARK_TEXT = 0xFFF2F6F9.toInt()

        // 浅色模式色彩（对齐 LightHollerColors）
        private const val LIGHT_BG = 0xEEFFFFFF.toInt()
        private const val LIGHT_STROKE = 0xFFCBD5E0.toInt()
        private const val LIGHT_IDLE = 0xFF00A985.toInt()
        private const val LIGHT_REC = 0xFFD32F2F.toInt()
        private const val LIGHT_TEXT = 0xFF1A202C.toInt()
    }

    /** 是否深色模式（0=系统, 1=浅色, 2=深色） */
    var isDarkTheme: Boolean = true
        set(v) {
            field = v
            updateColors()
            invalidate()
        }

    private var colorBg = DARK_BG
    private var colorStroke = DARK_STROKE
    private var colorIdle = DARK_IDLE
    private var colorRec = DARK_REC
    private var colorText = DARK_TEXT

    private fun updateColors() {
        if (isDarkTheme) {
            colorBg = DARK_BG
            colorStroke = DARK_STROKE
            colorIdle = DARK_IDLE
            colorRec = DARK_REC
            colorText = DARK_TEXT
        } else {
            colorBg = LIGHT_BG
            colorStroke = LIGHT_STROKE
            colorIdle = LIGHT_IDLE
            colorRec = LIGHT_REC
            colorText = LIGHT_TEXT
        }
        fillPaint.color = colorBg
        strokePaint.color = colorStroke
        textPaint.color = colorText
    }

    /** 内部状态：与 HollerEngine.Stage 对应，但用 int 避免跨包依赖 */
    var state: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    /** 电平 0~100，驱动声波动画 */
    var level: Int = 0
        set(v) {
            field = v
            invalidate()
        }

    /** 当前变声效果，仅在按钮上显示一个短标 */
    var effect: VoiceEffect = VoiceEffect.NATIVE
        set(v) {
            field = v
            invalidate()
        }

    /** 是否处于「隐藏」态（收起成小点） */
    var collapsed: Boolean = false
        set(v) {
            field = v
            invalidate()
        }

    var onTap: (() -> Unit)? = null
    var onLongPressStart: (() -> Unit)? = null
    var onLongPressEnd: (() -> Unit)? = null
    var onDoubleTap: (() -> Unit)? = null
    var onDragEnd: ((Int, Int) -> Unit)? = null

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
        color = colorBg
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f
        color = colorStroke
    }
    private val arcPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = colorText
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var scale = 1f
    private var scaleAnim: ValueAnimator? = null

    private var downX = 0f
    private var downY = 0f
    private var lastX = 0f
    private var lastY = 0f
    private var dragging = false
    private var longPressFired = false
    private var lastTapTime = 0L

    // ------------------------------------------------------------------
    // 绘制
    // ------------------------------------------------------------------

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val baseR = minOf(width, height) / 2f - 6f

        canvas.save()
        canvas.scale(scale, scale, cx, cy)

        // 底盘
        canvas.drawCircle(cx, cy, baseR, fillPaint)

        // 描边：颜色随状态
        strokePaint.color = when (state) {
            1 -> colorRec      // 录音中
            2 -> colorIdle     // 播报中
            else -> colorStroke
        }
        canvas.drawCircle(cx, cy, baseR, strokePaint)

        if (collapsed) {
            canvas.restore()
            return
        }

        // 声波弧：状态驱动
        val accent = when (state) {
            1 -> colorRec
            2 -> colorIdle
            else -> colorIdle
        }
        drawWaves(canvas, cx, cy, baseR, accent)

        // 中心喇叭图形
        drawHorn(canvas, cx, cy, baseR, accent)

        // 变声短标（非原声时才显示）
        if (effect != VoiceEffect.NATIVE) {
            textPaint.textSize = baseR * 0.30f
            canvas.drawText(effect.label, cx, cy + baseR * 0.82f, textPaint)
        }

        canvas.restore()
    }

    /** 同心圆弧：录音时随电平扩散，播报时缓慢呼吸 */
    private fun drawWaves(canvas: Canvas, cx: Float, cy: Float, baseR: Float, color: Int) {
        val rings = 3
        val lvl = if (state == 1) level / 100f else 0.35f
        for (i in 0 until rings) {
            val t = i / rings.toFloat()
            val r = baseR * (0.52f + 0.20f * i + 0.18f * lvl)
            val alpha = (150 * (1f - t * 0.5f)).toInt().coerceIn(30, 190)
            arcPaint.color = (color and 0x00FFFFFF) or (alpha shl 24)
            arcPaint.strokeWidth = 3.5f - i * 0.6f
            // 只画两侧的弧，像声波从喇叭扩散
            canvas.drawArc(
                RectF(cx - r, cy - r, cx + r, cy + r),
                -50f, 100f, false, arcPaint
            )
            canvas.drawArc(
                RectF(cx - r, cy - r, cx + r, cy + r),
                130f, 100f, false, arcPaint
            )
        }
    }

    /** 中心喇叭图形 */
    private fun drawHorn(canvas: Canvas, cx: Float, cy: Float, baseR: Float, color: Int) {
        val p = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.FILL
            this.color = color
        }
        val s = baseR * 0.42f
        // 梯形喇叭身
        val path = android.graphics.Path().apply {
            moveTo(cx - s * 0.55f, cy - s * 0.28f)
            lineTo(cx - s * 0.05f, cy - s * 0.28f)
            lineTo(cx + s * 0.50f, cy - s * 0.72f)
            lineTo(cx + s * 0.50f, cy + s * 0.72f)
            lineTo(cx - s * 0.05f, cy + s * 0.28f)
            lineTo(cx - s * 0.55f, cy + s * 0.28f)
            close()
        }
        canvas.drawPath(path, p)
        // 出音弧
        val ap = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeCap = Paint.Cap.ROUND
            this.color = color
            strokeWidth = 3f
        }
        for (i in 1..2) {
            val r = s * (0.62f + 0.26f * i)
            canvas.drawArc(
                RectF(cx + s * 0.30f - r, cy - r, cx + s * 0.30f + r, cy + r),
                -42f, 84f, false, ap
            )
        }
    }

    // ------------------------------------------------------------------
    // 触摸
    // ------------------------------------------------------------------

    override fun onTouchEvent(event: MotionEvent): Boolean {
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                lastX = downX
                lastY = downY
                dragging = false
                longPressFired = false
                animateScale(0.92f)

                // 长按判定：350ms 后触发按住说话
                postDelayed({
                    if (!dragging && isPressed) {
                        longPressFired = true
                        onLongPressStart?.invoke()
                    }
                }, 350)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - lastX
                val dy = event.rawY - lastY
                if (!dragging && (kotlin.math.abs(event.rawX - downX) > 12 ||
                            kotlin.math.abs(event.rawY - downY) > 12)
                ) {
                    dragging = true
                    if (longPressFired) {
                        // 拖动前先结束说话，避免状态卡住
                        onLongPressEnd?.invoke()
                        longPressFired = false
                    }
                }
                if (dragging) {
                    x += dx
                    y += dy
                    lastX = event.rawX
                    lastY = event.rawY
                }
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                animateScale(1f)
                when {
                    dragging -> {
                        onDragEnd?.invoke(x.toInt(), y.toInt())
                    }
                    longPressFired -> {
                        onLongPressEnd?.invoke()
                    }
                    else -> {
                        val now = System.currentTimeMillis()
                        if (now - lastTapTime < 260) {
                            lastTapTime = 0
                            onDoubleTap?.invoke()
                        } else {
                            lastTapTime = now
                            postDelayed({
                                if (lastTapTime != 0L) {
                                    lastTapTime = 0
                                    onTap?.invoke()
                                }
                            }, 260)
                        }
                    }
                }
                return true
            }
        }
        return super.onTouchEvent(event)
    }

    private fun animateScale(target: Float) {
        scaleAnim?.cancel()
        scaleAnim = ValueAnimator.ofFloat(scale, target).apply {
            duration = 140
            interpolator = DecelerateInterpolator()
            addUpdateListener {
                scale = it.animatedValue as Float
                invalidate()
            }
            start()
        }
    }

    override fun onDetachedFromWindow() {
        scaleAnim?.cancel()
        scaleAnim = null
        super.onDetachedFromWindow()
    }
}
