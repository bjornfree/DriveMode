package com.bjornfree.drivemode.ui.theme

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RectF
import android.graphics.SweepGradient
import android.os.Build
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout

/**
 * Лёгкий оверлей для отображения сияющей рамки по периметру экрана
 * в цвете текущего режима (eco / comfort / sport / adaptive).
 * Работает поверх всех окон, но не перехватывает фокус и тач.
 */
class BorderOverlayController(private val appContext: Context) {

    private val wm: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private var container: FrameLayout? = null
    private var borderView: BorderView? = null
    private var animator: ValueAnimator? = null

    // Цвета подсветки по режимам (без Color.parseColor)
    private val modeColors: Map<String, Int> = mapOf(
        "eco" to 0xFF00E676.toInt(),        // яркий зелёный (neon eco)
        "comfort" to 0xFF00B0FF.toInt(),    // насыщенный голубой
        "sport" to 0xFFFF0033.toInt(),      // агрессивный красный
        "adaptive" to 0xFFB388FF.toInt()    // яркий фиолетовый
    )

    /**
     * Показать рамку для указанного режима.
     * Рамка пульсирует и автоматически скрывается.
     */
    fun showMode(mode: String) {
        // без разрешения на оверлеи даже не пробуем добавлять view
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(appContext)
        ) {
            return
        }

        ensureAttached()

        val v = borderView ?: return
        val modeLower = mode.lowercase()
        val color = modeColors[modeLower] ?: Color.WHITE
        v.setColor(color)
        v.setMode(modeLower)

        // Останавливаем предыдущую анимацию, если была
        animator?.cancel()

        // Начальное состояние
        v.setIntensity(0f)
        container?.visibility = View.VISIBLE

        val durationMs = 2600L

        // Анимируем прогресс [0f..1f], внутри BorderView используется для фазы
        animator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = durationMs
            repeatCount = ValueAnimator.INFINITE
            repeatMode = ValueAnimator.RESTART
            addUpdateListener {
                val value = it.animatedValue as Float
                v.setIntensity(value)
            }
            start()
        }

        // Авто-скрытие
        v.removeCallbacks(autoHideRunnable)
        v.postDelayed(autoHideRunnable, 3500L)
    }

    /**
     * Скрыть рамку и остановить анимацию.
     */
    fun hide() {
        animator?.cancel()
        animator = null
        borderView?.setIntensity(0f)
        container?.visibility = View.GONE
    }

    /**
     * Полное уничтожение оверлея. Вызывать из onDestroy сервиса.
     */
    fun destroy() {
        // снимаем отложенный autoHide
        borderView?.removeCallbacks(autoHideRunnable)

        hide()
        container?.let {
            try {
                wm.removeView(it)
            } catch (_: Throwable) {
            }
        }
        container = null
        borderView = null
    }

    private val autoHideRunnable = Runnable {
        hide()
    }

    // Создаём окно-оверлей и BorderView при первом использовании
    private fun ensureAttached() {
        val existing = container
        if (existing != null && existing.windowToken != null) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(appContext)
        ) {
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val root = FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
        }

        val view = BorderView(appContext)
        root.addView(
            view,
            FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        )

        try {
            wm.addView(root, lp)
        } catch (_: Throwable) {
            return
        }

        container = root
        borderView = view
    }
}

/**
 * View, рисующая рамку по периметру экрана.
 * Интенсивность управляет включением/выключением анимации.
 */
private class BorderView(context: Context) : View(context) {

    private var intensity: Float = 0f
    private var mode: String = "eco"

    private var isActive: Boolean = false
    private var startTimeMs: Long = SystemClock.elapsedRealtime()

    private var color: Int = Color.WHITE

    // Радиус скругления внутренних углов "трубы"
    private val cornerRadiusPx: Float = 24f * resources.displayMetrics.density

    // Толщина "трубы" по периметру
    private val tubeWidthPx: Float = 36f * resources.displayMetrics.density

    private val rect = RectF()
    private val innerRect = RectF()
    private val ringPath = Path()

    private var centerX: Float = 0f
    private var centerY: Float = 0f

    private val shaderMatrix = Matrix()
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Кэш градиента, чтобы не пересоздавать на каждом кадре
    private var shader: SweepGradient? = null
    private var shaderBaseColor: Int = Color.WHITE
    private var shaderMode: String = "eco"
    private var shaderWidth: Int = 0
    private var shaderHeight: Int = 0

    fun setMode(m: String) {
        if (mode == m) return
        mode = m
        startTimeMs = SystemClock.elapsedRealtime()
        // пересоздаём градиент при следующей отрисовке
        shader = null
        invalidate()
    }

    fun setColor(c: Int) {
        if (color == c) return
        color = c
        shader = null
        invalidate()
    }

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        if (intensity > 0f) {
            if (!isActive) {
                isActive = true
                startTimeMs = SystemClock.elapsedRealtime()
            }
        } else {
            isActive = false
        }
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        centerX = w.toFloat() / 2f
        centerY = h.toFloat() / 2f

        val margin = 0f

        rect.set(
            margin,
            margin,
            w.toFloat() - margin,
            h.toFloat() - margin
        )

        innerRect.set(
            rect.left + tubeWidthPx,
            rect.top + tubeWidthPx,
            rect.right - tubeWidthPx,
            rect.bottom - tubeWidthPx
        )

        ringPath.reset()
        ringPath.addRect(rect, Path.Direction.CW)
        ringPath.addRoundRect(
            innerRect,
            cornerRadiusPx,
            cornerRadiusPx,
            Path.Direction.CCW
        )
        ringPath.fillType = Path.FillType.EVEN_ODD

        shaderWidth = 0
        shaderHeight = 0
        shader = null
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isActive || intensity <= 0f) return

        val w = width
        val h = height
        if (w <= 0 || h <= 0) return

        ensureShader(w, h)

        val baseColor = color

        fun argb(a: Int, c: Int): Int = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

        val elapsedSec = (SystemClock.elapsedRealtime() - startTimeMs) / 1000f

        // Базовая скорость вращения по режимам (градусов в секунду)
        val baseSpeed = when (mode) {
            "eco" -> 200f
            "comfort" -> 200f
            "sport" -> 200f
            // adaptive: скорость меняется по синусу, эффект "живее"
            "adaptive" -> {
                val omega = (2.0 * Math.PI / 2.0).toFloat() // период ≈ 2 c
                val k = kotlin.math.sin(omega * elapsedSec).toFloat() // [-1..1]
                100f * k
            }
            else -> 500f
        }

        val angle = (elapsedSec * baseSpeed) % 360f

        shaderMatrix.reset()
        shaderMatrix.postRotate(angle, centerX, centerY)
        shader?.setLocalMatrix(shaderMatrix)

        liquidPaint.shader = shader
        liquidPaint.alpha = 220

        canvas.drawPath(ringPath, liquidPaint)
    }

    private fun ensureShader(w: Int, h: Int) {
        if (shader != null &&
            shaderBaseColor == color &&
            shaderMode == mode &&
            shaderWidth == w &&
            shaderHeight == h
        ) {
            return
        }

        shaderBaseColor = color
        shaderMode = mode
        shaderWidth = w
        shaderHeight = h

        fun argb(a: Int, c: Int): Int =
            Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

        val alpha = 220
        val dimAlpha = (alpha * 0.3f).toInt()

        val bright = argb(alpha, color)
        val dim = argb(dimAlpha, color)

        shader = if (mode == "adaptive") {
            val redBright = Color.argb(alpha, 255, 80, 80)
            val greenBright = Color.argb(alpha, 80, 255, 140)
            val blueBright = Color.argb(alpha, 80, 180, 255)

            val redDim = Color.argb(dimAlpha, 255, 80, 80)
            val greenDim = Color.argb(dimAlpha, 80, 255, 140)
            val blueDim = Color.argb(dimAlpha, 80, 180, 255)

            SweepGradient(
                centerX,
                centerY,
                intArrayOf(
                    redDim, redBright, redDim,
                    greenDim, greenBright, greenDim,
                    blueDim, blueBright, blueDim,
                    redDim
                ),
                floatArrayOf(
                    0f, 0.08f, 0.16f,
                    0.33f, 0.41f, 0.49f,
                    0.66f, 0.74f, 0.82f,
                    1f
                )
            )
        } else {
            SweepGradient(
                centerX,
                centerY,
                intArrayOf(
                    dim,
                    bright,
                    dim,
                    dim,
                    bright,
                    dim
                ),
                floatArrayOf(
                    0f,
                    0.16f,
                    0.33f,
                    0.5f,
                    0.66f,
                    0.83f
                )
            )
        }
    }
}