package com.bjornfree.drivemode.ui.theme

import android.graphics.Rect
import android.graphics.Typeface

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

    // Цвета подсветки по режимам
    // ОПТИМИЗАЦИЯ: Прямые integer значения вместо Color.parseColor()
    // Избегаем парсинг строк и создание Color объектов при инициализации
    private val modeColors: Map<String, Int> = mapOf(
        "eco" to 0xFF00E676.toInt(),        // яркий зелёный (neon eco)
        "comfort" to 0xFF00B0FF.toInt(),    // насыщенный голубой
        "sport" to 0xFFFF0033.toInt(),      // агрессивный красный
        "adaptive" to 0xFFB388FF.toInt()    // яркий фиолетовый
    )

    /**
     * Показать рамку для указанного режима.
     * Рамка пульсирует 2.5 секунды и автоматически скрывается.
     */
    fun showMode(mode: String) {
        ensureAttached()

        val v = borderView ?: return
        val modeLower = mode.lowercase()
        val color = modeColors[modeLower] ?: Color.WHITE
        v.setColor(color)
        v.setMode(modeLower)

        // Останавливаем предыдущую анимацию, если была
        animator?.cancel()

        // Начальное состояние анимации
        v.setIntensity(0f)
        container?.visibility = View.VISIBLE

        // Одинаковая скорость анимации для всех режимов
        val durationMs = 2600L

        // Анимируем прогресс [0f..1f], а в самой BorderView
        // интерпретируем его по-разному в зависимости от режима.
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

        // Авто-скрытие через 3.5 секунды
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

        wm.addView(root, lp)

        container = root
        borderView = view
    }
}

/**
 * View, рисующая рамку по периметру экрана.
 * Интенсивность управляет альфой и толщиной линии.
 */
private class BorderView(context: Context) : View(context) {

    private var intensity: Float = 0f
    private var mode: String = "eco"

    // Флаг активности анимации и момент старта
    private var isActive: Boolean = false
    private var startTimeMs: Long = SystemClock.elapsedRealtime()

    private var color: Int = Color.WHITE

    fun setMode(m: String) {
        mode = m
        // При смене режима перезапускаем отсчёт фазы
        startTimeMs = SystemClock.elapsedRealtime()
        invalidate()
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Дополнительная кисть для лёгкого свечения вокруг рамки
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Базовая толщина линии в dp (делаем более аккуратной)
    private val baseStrokePx: Float = 6f * resources.displayMetrics.density

    // Радиус скругления углов рамки в dp
    private val cornerRadiusPx: Float = 24f * resources.displayMetrics.density

    // Прямоугольник, внутри которого рисуем рамку
    private val rect = RectF()

    // Толщина стенок "сосуда" (dp)
    private val wallThicknessPx: Float = 6f * resources.displayMetrics.density

    // Толщина "трубы" с жидкостью (ширина полосы по периметру)
    private val tubeWidthPx: Float = 36f * resources.displayMetrics.density

    // Внутренний прямоугольник для "жидкости"
    private val innerRect = RectF()

    // Путь "кольца-трубы" по периметру
    private val ringPath = Path()

    // Центр экрана — для градиента по кругу
    private var centerX: Float = 0f
    private var centerY: Float = 0f

    // Матрица для анимации градиента
    private val shaderMatrix = Matrix()

    // Кисть для жидкостей (заливка внутри рамки)
    private val liquidPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    // Кисть для блика (имитация стекла)
    private val highlightPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }

    // Плотность экрана
    private val density: Float = resources.displayMetrics.density

    // Кисти и вспомогательные объекты для текста режима
    private val labelTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        // Яркий контрастный текст поверх "жидкости"
        color = Color.WHITE
        textAlign = Paint.Align.CENTER

        // Более "автомобильный" шрифт: узкий, жирный
        // (sans-serif-condensed обычно есть на всех Android)
        typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

        // Лёгкое увеличение межбуквенного интервала, чтобы надпись читалась чище
        letterSpacing = 0.08f

        // Едва заметная тень для читаемости на ярком фоне трубы
        setShadowLayer(
            3f,   // радиус
            0f,   // смещение по X
            2f,   // смещение по Y
            Color.argb(160, 0, 0, 0) // полупрозрачный чёрный
        )
    }

    private val labelBgPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }

    private var labelTextSizePx: Float = 0f
    private val labelTextBounds = Rect()

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return

        centerX = w.toFloat() / 2f
        centerY = h.toFloat() / 2f

        // Хотим "прилипание" к краям экрана — без внешних отступов
        val margin = 0f

        // Внешний прямоугольник чуть отступаем от краёв
        rect.set(
            margin,
            margin,
            w.toFloat() - margin,
            h.toFloat() - margin
        )

        // Внутренний прямоугольник — внутренняя стенка "трубы"
        innerRect.set(
            rect.left + tubeWidthPx,
            rect.top + tubeWidthPx,
            rect.right - tubeWidthPx,
            rect.bottom - tubeWidthPx
        )

        // Строим путь "кольца" с вырезанным центром
        ringPath.reset()
        // Внешний прямоугольник без скругления — острые углы у самого края экрана
        ringPath.addRect(
            rect,
            Path.Direction.CW
        )
        // Внутренний прямоугольник со скруглением — мягкие внутренние углы "трубы"
        ringPath.addRoundRect(
            innerRect,
            cornerRadiusPx,
            cornerRadiusPx,
            Path.Direction.CCW
        )
        ringPath.fillType = Path.FillType.EVEN_ODD

        // Подбираем размер шрифта под экран (чтобы надпись была читабельной, но не мешала контенту)
        val minSide = minOf(w, h).toFloat()
        labelTextSizePx = (minSide / 30f).coerceAtLeast(14f * resources.displayMetrics.scaledDensity)
        labelTextPaint.textSize = labelTextSizePx
    }

    fun setColor(c: Int) {
        color = c
        invalidate()
    }

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        // intensity > 0f означает, что анимация должна идти
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)

        if (!isActive || intensity <= 0f) return

        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f || h <= 0f) return

        val baseColor = color

        fun argb(a: Int, c: Int): Int = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

        // Единая интенсивность для всех режимов, разная цветовая схема для adaptive
        val alpha = 220
        val dimAlpha = (alpha * 0.3f).toInt()

        val bright = argb(alpha, baseColor)
        val dim = argb(dimAlpha, baseColor)

        // Выбираем схему градиента:
        // - для adaptive — разноцветные сегменты (красный, зелёный, голубой),
        // - для остальных — один цвет с яркими и тусклыми участками.
        val shader: SweepGradient = if (mode == "adaptive") {
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
                    0f,   0.08f, 0.16f,
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
                    dim,      // начало сегмента
                    bright,   // яркая часть
                    dim,      // переход
                    dim,      // спокойная часть
                    bright,   // ещё одно яркое пятно
                    dim       // замыкание круга
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

        // Поворачиваем градиент в зависимости от прошедшего времени:
        // создаёт эффект непрерывного "перетекания" без рывков от перезапуска анимации.
        val elapsedSec = (SystemClock.elapsedRealtime() - startTimeMs) / 1000f

        // Базовая скорость вращения по режимам (градусов в секунду)
        val baseSpeed = when (mode) {
            "eco" -> 200f
            "comfort" -> 200f
            "sport" -> 200f
            // adaptive: плавно меняем скорость от ~400 до ~800 за цикл ~2 c
            "adaptive" -> {
                val omega = (2.0 * Math.PI / 2.0).toFloat() // период ≈ 2 c
                val k = kotlin.math.sin(omega * elapsedSec).toFloat() // [-1..1]
                100f  * k // [400..800]
            }
            else -> 500f
        }

        val angle = (elapsedSec * baseSpeed) % 360f

        shaderMatrix.reset()
        shaderMatrix.postRotate(angle, centerX, centerY)
        shader.setLocalMatrix(shaderMatrix)

        liquidPaint.shader = shader
        liquidPaint.alpha = alpha

        // Рисуем только кольцо-трубу, центр остаётся полностью прозрачным
        canvas.drawPath(ringPath, liquidPaint)

    }
}