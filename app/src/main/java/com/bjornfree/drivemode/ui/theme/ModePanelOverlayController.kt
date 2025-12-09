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
import android.provider.Settings
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

    // ОПТИМИЗАЦИЯ: Прямые integer значения вместо Color.parseColor()
    // Избегаем парсинг строк при инициализации контроллера
    private val modeColors: Map<String, Int> = mapOf(
        "eco" to 0xFF4CAF50.toInt(),
        "comfort" to 0xFF2196F3.toInt(),
        "sport" to 0xFFFF1744.toInt(),
        "adaptive" to 0xFF7E57C2.toInt()
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

/**
 * Состояние нижней полосы статуса вождения.
 *
 * Используется для отображения ключевых метрик:
 * - режим вождения
 * - текущая передача (R/D/P/N)
 * - скорость
 * - запас хода
 * - температура в салоне и снаружи
 * - давление во всех колёсах
 */
data class DrivingStatusOverlayState(
    val modeTitle: String? = null,
    val gear: String? = null,
    val speedKmh: Int? = null,
    val rangeKm: Int? = null,
    val cabinTempC: Float? = null,
    val ambientTempC: Float? = null,
    val tirePressureFrontLeft: Int? = null,
    val tirePressureFrontRight: Int? = null,
    val tirePressureRearLeft: Int? = null,
    val tirePressureRearRight: Int? = null
)

/**
 * Контроллер нижней фиксированной полосы статуса.
 *
 * Полоса:
 * - занимает всю ширину экрана
 * - находится в самом низу
 * - имеет фиксированную высоту около 48dp
 * - отображается поверх остальных окон как системный оверлей
 * - поддерживает темную и светлую тему
 */
class DrivingStatusOverlayController(
    private val appContext: Context
) {

    companion object {
        // Единственный активный экземпляр контроллера полоски.
        // При создании нового экземпляра старый уничтожается, чтобы не было "залипших" оверлеев.
        private var currentInstance: DrivingStatusOverlayController? = null
    }

    private val wm: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val density: Float = appContext.resources.displayMetrics.density

    private var layoutParams: WindowManager.LayoutParams? = null
    private var container: FrameLayout? = null
    private var rootRow: LinearLayout? = null

    // Флаг, включена ли логически нижняя полоска (зависит от настроек/репозитория)
    private var enabled: Boolean = true

    private var tvMode: TextView? = null
    private var tvGear: TextView? = null
    private var tvSpeed: TextView? = null
    private var tvRange: TextView? = null
    private var tvTemps: TextView? = null
    private var tvTires: TextView? = null

    // Флаг, что полоса подключена к WindowManager
    private var attached: Boolean = false

    // Текущая позиция полоски ("bottom" или "top")
    private var position: String = "bottom"

    // Кэшированные цвета для темной/светлой темы
    private var isDarkTheme: Boolean = true
    private var bgColor: Int = Color.parseColor("#F01E1E1E")
    private var textColor: Int = Color.WHITE
    private var textSecondaryColor: Int = Color.parseColor("#CCFFFFFF")

    init {
        // Если до этого уже был создан другой контроллер полоски,
        // уничтожаем его оверлей, чтобы не оставалось нескольких полос одновременно.
        currentInstance?.destroy()
        currentInstance = this
    }

    /**
     * Логическое включение/выключение нижней полосы.
     * При выключении полоса уничтожается и больше не пересоздаётся,
     * пока снова не будет включена.
     */
    fun setEnabled(isEnabled: Boolean) {
        if (enabled == isEnabled) return
        enabled = isEnabled
        if (!enabled) {
            // Убираем полосу, чтобы она не "залипала" при выключении
            destroy()
        }
    }

    /**
     * Показать/гарантировать наличие полосы внизу экрана.
     * Вызывается перед обновлением статуса.
     */
    fun ensureVisible() {
        if (!enabled) return
        ensureAttached()
    }

    /**
     * Обновить тему полосы (темная/светлая).
     * Вызывается при изменении темы приложения.
     */
    fun setDarkTheme(dark: Boolean) {
        if (isDarkTheme == dark) return

        isDarkTheme = dark
        if (dark) {
            bgColor = Color.parseColor("#F01E1E1E")
            textColor = Color.WHITE
            textSecondaryColor = Color.parseColor("#CCFFFFFF")
        } else {
            bgColor = Color.parseColor("#F0F5F5F5")
            textColor = Color.parseColor("#1E1E1E")
            textSecondaryColor = Color.parseColor("#666666")
        }

        // Обновляем UI если полоса уже отображается
        rootRow?.background = createBackgroundDrawable()
        tvMode?.setTextColor(textColor)
        tvGear?.setTextColor(textColor)
        tvSpeed?.setTextColor(textColor)
        tvRange?.setTextColor(textColor)
        tvTemps?.setTextColor(textSecondaryColor)
        tvTires?.setTextColor(textSecondaryColor)
    }

    /**
     * Изменить позицию полоски ("bottom" или "top").
     * Требует пересоздания overlay.
     */
    fun setPosition(newPosition: String) {
        if (position == newPosition) return

        position = newPosition

        // Если полоска уже отображается, пересоздаем ее
        if (attached) {
            val wasAttached = attached
            destroy()
            if (wasAttached) {
                ensureAttached()
            }
        }
    }

    /**
     * Обновить отображаемые значения в нижней полосе.
     *
     * Можно вызывать из сервиса по мере обновления метрик автомобиля.
     */
    fun updateStatus(state: DrivingStatusOverlayState) {
        // Если логически отключено — не обновляем и не пересоздаём полосу.
        if (!enabled) {
            return
        }

        // Если нет разрешения на оверлеи, не обновляем статус,
        // чтобы избежать WindowManager$BadTokenException на обычных девайсах/эмуляторах.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(appContext)
        ) {
            return
        }

        try {
            ensureAttached()
        } catch (_: Throwable) {
            // Если система не позволяет добавить окно (нет прав и т.п.) — просто выходим без падения.
            return
        }

        tvMode?.text = state.modeTitle?.takeIf { it.isNotBlank() } ?: "—"
        tvGear?.text = state.gear?.takeIf { it.isNotBlank() } ?: "—"
        tvSpeed?.text = state.speedKmh?.let { "$it км/ч" } ?: "— км/ч"
        tvRange?.text = state.rangeKm?.let { "$it км" } ?: "— км"

        tvTemps?.text = buildTempsText(state)
        tvTires?.text = buildTiresText(state)
    }

    /**
     * Полностью убрать полосу и освободить ресурсы.
     */
    fun destroy() {
        val c = container

        layoutParams = null
        container = null
        rootRow = null
        tvMode = null
        tvGear = null
        tvSpeed = null
        tvRange = null
        tvTemps = null
        tvTires = null
        attached = false

        if (c != null) {
            try {
                wm.removeView(c)
            } catch (_: Throwable) {
                // игнорируем
            }
        }

        // Если уничтожаем текущий singleton‑экземпляр — очищаем ссылку,
        // чтобы следующий созданный контроллер корректно стал новым currentInstance.
        if (currentInstance === this) {
            currentInstance = null
        }
    }

    // ---------------- Внутренняя реализация ----------------

    private fun ensureAttached() {
        if (attached && container != null && container?.windowToken != null) {
            return
        }

        // Если логически отключено — не создаём полосу.
        if (!enabled) {
            return
        }

        // Если нет разрешения на оверлеи — не пытаемся добавлять окно,
        // чтобы избежать WindowManager$BadTokenException на обычных девайсах/эмуляторах.
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

        val heightPx = (48f * density).toInt()

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            heightPx,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = if (position == "top") {
                Gravity.TOP or Gravity.CENTER_HORIZONTAL
            } else {
                Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL
            }
            x = 0
            y = 0
        }

        layoutParams = lp

        val rootContainer = FrameLayout(appContext).apply {
            setBackgroundColor(Color.TRANSPARENT)
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val row = LinearLayout(appContext).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )

            val horizontalPadding = (12f * density).toInt()
            val verticalPadding = (8f * density).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

            background = createBackgroundDrawable()
        }

        // Общее правило: компактные блоки, разделённые лёгкими отступами
        tvMode = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                0.8f
            )
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            text = "—"
            setTextColor(textColor)
            textSize = 14f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
        }

        tvGear = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                0.4f
            )
            maxLines = 1
            text = "—"
            setTextColor(textColor)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        tvSpeed = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                0.9f
            )
            maxLines = 1
            text = "— км/ч"
            setTextColor(textColor)
            textSize = 14f
            gravity = Gravity.CENTER
        }

        tvRange = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                0.9f
            )
            maxLines = 1
            text = "— км"
            setTextColor(textColor)
            textSize = 14f
            gravity = Gravity.CENTER
        }

        tvTemps = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.2f
            )
            maxLines = 1
            text = "—° / —°"
            setTextColor(textSecondaryColor)
            textSize = 13f
            gravity = Gravity.CENTER
        }

        tvTires = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.4f
            )
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            text = "—"
            setTextColor(textSecondaryColor)
            textSize = 13f
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }

        row.addView(tvMode)
        row.addView(tvGear)
        row.addView(tvSpeed)
        row.addView(tvRange)
        row.addView(tvTemps)
        row.addView(tvTires)

        rootContainer.addView(row)

        try {
            wm.addView(rootContainer, layoutParams ?: lp)
        } catch (_: Throwable) {
            // Если система не позволяет добавить окно (нет прав и т.п.) — просто выходим без падения.
            return
        }

        container = rootContainer
        rootRow = row
        attached = true
    }

    private fun buildTempsText(state: DrivingStatusOverlayState): String {
        val cabin = state.cabinTempC
        val ambient = state.ambientTempC

        val cabinText = cabin?.toInt()?.let { "$it°" } ?: "—°"
        val ambientText = ambient?.toInt()?.let { "$it°" } ?: "—°"

        // Формат: "IN 21° / OUT 8°"
        return "IN $cabinText / OUT $ambientText"
    }

    private fun buildTiresText(state: DrivingStatusOverlayState): String {
        val fl = state.tirePressureFrontLeft
        val fr = state.tirePressureFrontRight
        val rl = state.tirePressureRearLeft
        val rr = state.tirePressureRearRight

        fun format(value: Int?): String =
            value?.let { "${it / 10}.${it % 10}" } ?: "—"

        // Формат компактной строки: "FL 2.3 · FR 2.3 · RL 2.3 · RR 2.3"
        return "FL ${format(fl)} · FR ${format(fr)} · RL ${format(rl)} · RR ${format(rr)}"
    }

    /**
     * Создает Drawable для фона полосы с учетом текущей темы и позиции.
     * Если полоса снизу - скругляем верхние углы.
     * Если полоса сверху - скругляем нижние углы.
     */
    private fun createBackgroundDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE

            val radius = 16f * density
            cornerRadii = if (position == "top") {
                // Для верхней позиции скругляем нижние углы
                floatArrayOf(
                    0f, 0f,       // левый верхний
                    0f, 0f,       // правый верхний
                    radius, radius, // правый нижний
                    radius, radius  // левый нижний
                )
            } else {
                // Для нижней позиции скругляем верхние углы
                floatArrayOf(
                    radius, radius, // левый верхний
                    radius, radius, // правый верхний
                    0f, 0f,       // правый нижний
                    0f, 0f        // левый нижний
                )
            }

            setColor(bgColor)
        }
    }
}