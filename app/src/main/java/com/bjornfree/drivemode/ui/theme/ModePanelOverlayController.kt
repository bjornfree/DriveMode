package com.bjornfree.drivemode.ui.theme

import androidx.core.content.ContextCompat

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import com.bjornfree.drivemode.R

/**
 * Контроллер плавающей панели с текущим режимом.
 *
 * Состояния:
 * - COMPACT  — маленькая иконка режима в углу (отображается постоянно).
 * - EXPANDED — расширенная панель с названием и описанием режима (3 сек после переключения).
 *
 * Панель добавляется как системный оверлей через WindowManager.
 */
class ModePanelOverlayController(
    private val appContext: Context
) {

    private val prefs = appContext.getSharedPreferences("mode_panel_overlay", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_GRAVITY = "gravity"
        private const val PREF_X = "x"
        private const val PREF_Y = "y"
        private const val PREF_ENABLED = "enabled"
    }

    private val wm: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val density: Float = appContext.resources.displayMetrics.density
    // Размеры экрана нужны, чтобы правильно "прилипать" к левому/правому краю
    private val screenWidth: Int = appContext.resources.displayMetrics.widthPixels

    // Включён ли сейчас оверлей (читаем из настроек, по умолчанию включён)
    private var overlayEnabled: Boolean = prefs.getBoolean(PREF_ENABLED, true)

    // Текущие LayoutParams оверлея
    private var layoutParams: WindowManager.LayoutParams? = null

    // Параметры перетаскивания
    private var initialX: Int = 0
    private var initialY: Int = 0
    private var initialTouchX: Float = 0f
    private var initialTouchY: Float = 0f

    private var container: FrameLayout? = null
    private var panelRoot: LinearLayout? = null
    private var iconContainer: FrameLayout? = null
    private var tvLetter: TextView? = null
    private var tvTitle: TextView? = null
    private var tvSubtitle: TextView? = null
    private var extendedContainer: LinearLayout? = null

    private var currentState: PanelState = PanelState.COMPACT

    private var autoCollapseRunnable: Runnable? = null

    private val modeColors: Map<String, Int> = mapOf(
        "eco" to Color.parseColor("#4CAF50"),
        "comfort" to Color.parseColor("#2196F3"),
        "sport" to Color.parseColor("#FF1744"),
        "adaptive" to Color.parseColor("#7E57C2")
    )

    private val modeSubtitles: Map<String, String> = mapOf(
        "eco" to "Экономичный расход",
        "sport" to "Максимальная отзывчивость",
        "comfort" to "Сбалансированный режим",
        "adaptive" to "Режим подстраивается под стиль"
    )

    // Отдельные иконки для режимов (нужно добавить соответствующие ресурсы в res/drawable)
    // ic_mode_eco.xml, ic_mode_comfort.xml, ic_mode_sport.xml, ic_mode_adaptive.xml
    private val modeIconRes: Map<String, Int> = mapOf(
        "eco" to R.drawable.eco,
        "comfort" to R.drawable.comfort,
        "sport" to R.drawable.sport,
        "adaptive" to R.drawable.adaptive
    )

    private enum class PanelState {
        COMPACT,
        EXPANDED
    }

    /**
     * Включён ли сейчас оверлей (для привязки к галочке в UI).
     */
    fun isOverlayEnabled(): Boolean = overlayEnabled

    /**
     * Включить или выключить оверлей.
     *
     * Если выключаем — убираем панель с экрана.
     * Если включаем — показываем компактное состояние (иконка режима).
     */
    fun setOverlayEnabled(enabled: Boolean) {
        overlayEnabled = enabled
        prefs.edit()
            .putBoolean(PREF_ENABLED, enabled)
            .apply()

        if (!enabled) {
            // Прячем и удаляем оверлей
            destroy()
        } else {
            // При включении — просто убеждаемся, что оверлей создан
            // и показываем компактное состояние.
            showCompact()
        }
    }

    /**
     * Показать информацию о режиме:
     * - обновляет цвет/иконки/текст,
     * - разворачивает панель в состояние EXPANDED,
     * - через ~3 сек сворачивает обратно в COMPACT.
     */
    fun showMode(mode: String) {
        if (!overlayEnabled) return
        ensureAttached()

        val modeLower = mode.lowercase()
        val baseColor = modeColors[modeLower] ?: Color.WHITE

        val letter = when (modeLower) {
            "eco" -> "E"
            "sport" -> "S"
            "comfort" -> "C"
            "adaptive" -> "A"
            else -> mode.take(1).uppercase()
        }
        val title = when (modeLower) {
            "eco" -> "ECO"
            "sport" -> "SPORT"
            "comfort" -> "COMFORT"
            "adaptive" -> "ADAPTIVE"
            else -> mode.uppercase()
        }
        val subtitle = modeSubtitles[modeLower] ?: ""

        // Обновляем UI
        tvLetter?.text = letter
        tvTitle?.text = title
        tvSubtitle?.text = subtitle

        // Пытаемся использовать отдельную иконку для режима, если она есть.
        val iconResId = modeIconRes[modeLower]
        if (iconResId != null) {
            // Используем готовый ресурс-иконку и скрываем букву
            iconContainer?.background = ContextCompat.getDrawable(appContext, iconResId)
            tvLetter?.visibility = View.GONE
        } else {
            // Fallback: рисуем цветной круг с буквой
            tvLetter?.visibility = View.VISIBLE
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(baseColor)
                setStroke((2f * density).toInt(), Color.WHITE)
            }
            iconContainer?.background = iconBg
        }

        // Обводка основной панели
        panelRoot?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(Color.parseColor("#E6000000")) // тёмный фон панели
            setStroke((2f * density).toInt(), baseColor)
        }

        // Разворачиваем панель
        expandPanel()

        // Переустанавливаем таймер сворачивания
        val root = panelRoot ?: return
        autoCollapseRunnable?.let { root.removeCallbacks(it) }
        autoCollapseRunnable = Runnable {
            collapsePanel()
        }
        root.postDelayed(autoCollapseRunnable!!, 3000L)
    }

    /**
     * Явно свернуть панель в компактное состояние (оставить только кружок).
     */
    fun collapsePanel() {
        if (currentState == PanelState.COMPACT) return

        currentState = PanelState.COMPACT

        val ext = extendedContainer ?: return

        ext.animate()
            .alpha(0f)
            .setDuration(200L)
            .withEndAction {
                ext.visibility = View.GONE
            }
            .start()
    }

    /**
     * Принудительно показать только компактное состояние (например, при старте сервиса).
     */
    fun showCompact(initialMode: String = "comfort") {
        // выставляем режим и сразу сворачиваемся до компактного
        showMode(initialMode)
        collapsePanel()
    }

    /**
     * Полностью убрать панель и освободить ресурсы.
     */
    fun destroy() {
        val c = container
        layoutParams = null
        container = null
        panelRoot = null
        iconContainer = null
        tvLetter = null
        tvTitle = null
        tvSubtitle = null
        extendedContainer = null

        if (c != null) {
            try {
                wm.removeView(c)
            } catch (_: Throwable) {
                // игнорируем
            }
        }
    }

    // ---------------- Внутренняя реализация ----------------

    private fun expandPanel() {
        if (currentState == PanelState.EXPANDED) return
        currentState = PanelState.EXPANDED

        val ext = extendedContainer ?: return

        // Делаем расширенную часть видимой, но прозрачной,
        // чуть уменьшенной и слегка смещённой от иконки.
        ext.visibility = View.VISIBLE
        ext.alpha = 0f
        ext.translationX = 6f * density
        ext.translationY = 4f * density
        ext.scaleX = 0.9f
        ext.scaleY = 0.9f

        // Плавное появление текста: лёгкий zoom‑in + выезд к своему месту
        ext.animate()
            .alpha(1f)
            .translationX(0f)
            .translationY(0f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(220L)
            .start()
    }

    private fun ensureAttached() {
        if (container != null && panelRoot != null && container?.windowToken != null) {
            return
        }
        // Если оверлей выключен — не создаём его.
        if (!overlayEnabled) {
            return
        }

        val layoutType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            // значения по умолчанию
            val defaultGravity = Gravity.BOTTOM or Gravity.END
            val margin = (12f * density).toInt()
            val defaultX = margin
            val defaultY = margin

            // восстанавливаем сохранённые значения, если есть
            gravity = prefs.getInt(PREF_GRAVITY, defaultGravity)
            x = prefs.getInt(PREF_X, defaultX)
            y = prefs.getInt(PREF_Y, defaultY)
        }

        layoutParams = lp

        val rootContainer = FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        // Создаём корневую "пилюлю" программно
        val panel = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            )
            setPadding(
                (8f * density).toInt(),
                (8f * density).toInt(),
                (12f * density).toInt(),
                (8f * density).toInt()
            )
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 24f * density
                setColor(Color.parseColor("#E6000000"))
            }
        }

        // Левая иконка-круг
        val iconSize = (56f * density).toInt()
        val iconFrame = FrameLayout(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(iconSize, iconSize)
        }

        val letterView = TextView(appContext).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            text = "C"
            setTextColor(Color.WHITE)
            textSize = 20f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            gravity = Gravity.CENTER
        }
        iconFrame.addView(letterView)

        // Правая расширенная часть
        val extended = LinearLayout(appContext).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                leftMargin = (8f * density).toInt()
            }
        }

        val titleView = TextView(appContext).apply {
            text = "COMFORT"
            setTextColor(Color.WHITE)
            textSize = 18f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        }

        val subtitleView = TextView(appContext).apply {
            text = "Сбалансированный режим"
            setTextColor(Color.parseColor("#CCFFFFFF"))
            textSize = 14f
        }

        extended.addView(titleView)
        extended.addView(subtitleView)

        // В компактном состоянии расширенная часть скрыта
        extended.visibility = View.GONE

        panel.addView(iconFrame)
        panel.addView(extended)

        rootContainer.addView(panel)

        // Добавляем оверлей в WindowManager
        wm.addView(rootContainer, layoutParams ?: lp)

        container = rootContainer
        panelRoot = panel
        iconContainer = iconFrame
        tvLetter = letterView
        tvTitle = titleView
        tvSubtitle = subtitleView
        extendedContainer = extended

        // Обработчик перетаскивания панели пальцем
        panel.setOnTouchListener { _, event ->
            val lpCurrent = layoutParams ?: return@setOnTouchListener false
            val rootView = container ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    // Запоминаем стартовую позицию окна и точку касания
                    initialX = lpCurrent.x
                    initialY = lpCurrent.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - initialTouchX).toInt()
                    val dy = (event.rawY - initialTouchY).toInt()

                    val isStart = (lpCurrent.gravity and Gravity.START) == Gravity.START
                    val isEnd = (lpCurrent.gravity and Gravity.END) == Gravity.END

                    // Для START: x — отступ от левого края, двигаем в ту же сторону, что и палец.
                    // Для END:   x — отступ от правого края, двигаем в противоположную сторону.
                    lpCurrent.x = when {
                        isStart -> initialX + dx
                        isEnd -> initialX - dx
                        else -> initialX - dx
                    }

                    // Для BOTTOM: y — отступ от нижнего края — здесь логика остаётся прежней.
                    lpCurrent.y = initialY - dy

                    layoutParams = lpCurrent
                    wm.updateViewLayout(rootView, lpCurrent)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val lpCurrent = layoutParams ?: return@setOnTouchListener false
                    val rootView = container ?: return@setOnTouchListener false

                    // Фактическая левая граница панели на экране
                    val rootWidth = rootView.width
                    if (rootWidth > 0 && screenWidth > 0) {
                        val isEndGravity = (lpCurrent.gravity and Gravity.END) == Gravity.END
                        val absoluteLeft = if (isEndGravity) {
                            // При gravity END значение x — отступ от правого края
                            screenWidth - lpCurrent.x - rootWidth
                        } else {
                            // При gravity START значение x — отступ от левого края
                            lpCurrent.x
                        }

                        // Центр панели относительно экрана
                        val panelCenterX = absoluteLeft + rootWidth / 2f
                        val stickToLeft = panelCenterX < screenWidth / 2f

                        if (stickToLeft) {
                            // Прилипаем к левому краю: gravity START, x = отступ от левого края
                            lpCurrent.gravity = Gravity.BOTTOM or Gravity.START
                            lpCurrent.x = absoluteLeft.coerceAtLeast(0)
                        } else {
                            // Прилипаем к правому краю: gravity END, x = отступ от правого края
                            lpCurrent.gravity = Gravity.BOTTOM or Gravity.END
                            val fromRight = (screenWidth - absoluteLeft - rootWidth).coerceAtLeast(0)
                            lpCurrent.x = fromRight
                        }

                        layoutParams = lpCurrent
                        wm.updateViewLayout(rootView, lpCurrent)

                        // Сохраняем положение панели для следующего запуска
                        prefs.edit()
                            .putInt(PREF_GRAVITY, lpCurrent.gravity)
                            .putInt(PREF_X, lpCurrent.x)
                            .putInt(PREF_Y, lpCurrent.y)
                            .apply()
                    }

                    true
                }
                else -> false
            }
        }
    }
}