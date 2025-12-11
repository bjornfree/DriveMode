package com.bjornfree.drivemode.ui.theme

import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.provider.Settings
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.bjornfree.drivemode.R

class ModePanelOverlayController(
    private val appContext: Context
) {

    private val prefs = appContext.getSharedPreferences("mode_panel_overlay", Context.MODE_PRIVATE)

    companion object {
        private const val PREF_GRAVITY = "gravity"
        private const val PREF_X = "x"
        private const val PREF_Y = "y"
        private const val PREF_ENABLED = "enabled"

        private const val PANEL_BG_COLOR = 0xE6000000.toInt()
        private const val PANEL_STROKE_COLOR_DEFAULT = 0xFFFFFFFF.toInt()
    }

    private val wm: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val density: Float = appContext.resources.displayMetrics.density
    private val screenWidth: Int = appContext.resources.displayMetrics.widthPixels

    private var overlayEnabled: Boolean = prefs.getBoolean(PREF_ENABLED, true)

    private var layoutParams: WindowManager.LayoutParams? = null

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

    fun isOverlayEnabled(): Boolean = overlayEnabled

    fun setOverlayEnabled(enabled: Boolean) {
        overlayEnabled = enabled
        prefs.edit()
            .putBoolean(PREF_ENABLED, enabled)
            .apply()

        if (!enabled) {
            destroy()
        } else {
            showCompact()
        }
    }

    fun showMode(mode: String) {
        if (!overlayEnabled) return
        ensureAttached()

        container?.visibility = View.VISIBLE

        val modeLower = mode.lowercase()
        val baseColor = modeColors[modeLower] ?: PANEL_STROKE_COLOR_DEFAULT

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

        tvLetter?.text = letter
        tvTitle?.text = title
        tvSubtitle?.text = subtitle

        val iconResId = modeIconRes[modeLower]
        if (iconResId != null) {
            iconContainer?.background = ContextCompat.getDrawable(appContext, iconResId)
            tvLetter?.visibility = View.GONE
        } else {
            tvLetter?.visibility = View.VISIBLE
            val iconBg = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(baseColor)
                setStroke((2f * density).toInt(), PANEL_STROKE_COLOR_DEFAULT)
            }
            iconContainer?.background = iconBg
        }

        panelRoot?.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 24f * density
            setColor(PANEL_BG_COLOR)
            setStroke((2f * density).toInt(), baseColor)
        }

        expandPanel()

        val root = panelRoot ?: return
        autoCollapseRunnable?.let { root.removeCallbacks(it) }
        autoCollapseRunnable = Runnable {
            collapsePanel()
        }
        root.postDelayed(autoCollapseRunnable!!, 3000L)
    }

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

    fun hide() {
        val root = panelRoot
        autoCollapseRunnable?.let { root?.removeCallbacks(it) }
        autoCollapseRunnable = null
        container?.visibility = View.GONE
    }

    fun showCompact(initialMode: String = "comfort") {
        showMode(initialMode)
        collapsePanel()
    }

    fun destroy() {
        val root = panelRoot
        autoCollapseRunnable?.let { runnable ->
            root?.removeCallbacks(runnable)
        }
        autoCollapseRunnable = null

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
            }
        }
    }

    private fun expandPanel() {
        if (currentState == PanelState.EXPANDED) return
        currentState = PanelState.EXPANDED

        val ext = extendedContainer ?: return

        ext.visibility = View.VISIBLE
        ext.alpha = 0f
        ext.translationX = 6f * density
        ext.translationY = 4f * density
        ext.scaleX = 0.9f
        ext.scaleY = 0.9f

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
        if (!overlayEnabled) return

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
            val defaultGravity = Gravity.BOTTOM or Gravity.END
            val margin = (12f * density).toInt()
            val defaultX = margin
            val defaultY = margin

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
                setColor(PANEL_BG_COLOR)
            }
        }

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
            setTextColor(0xCCFFFFFF.toInt())
            textSize = 14f
        }

        extended.addView(titleView)
        extended.addView(subtitleView)
        extended.visibility = View.GONE

        panel.addView(iconFrame)
        panel.addView(extended)

        rootContainer.addView(panel)

        wm.addView(rootContainer, layoutParams ?: lp)

        container = rootContainer
        panelRoot = panel
        iconContainer = iconFrame
        tvLetter = letterView
        tvTitle = titleView
        tvSubtitle = subtitleView
        extendedContainer = extended

        panel.setOnTouchListener { _, event ->
            val lpCurrent = layoutParams ?: return@setOnTouchListener false
            val rootView = container ?: return@setOnTouchListener false

            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
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

                    lpCurrent.x = when {
                        isStart -> initialX + dx
                        isEnd -> initialX - dx
                        else -> initialX - dx
                    }

                    lpCurrent.y = initialY - dy

                    layoutParams = lpCurrent
                    wm.updateViewLayout(rootView, lpCurrent)
                    true
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    val lpNow = layoutParams ?: return@setOnTouchListener false
                    val rootViewNow = container ?: return@setOnTouchListener false

                    val rootWidth = rootViewNow.width
                    if (rootWidth > 0 && screenWidth > 0) {
                        val isEndGravity = (lpNow.gravity and Gravity.END) == Gravity.END
                        val absoluteLeft = if (isEndGravity) {
                            screenWidth - lpNow.x - rootWidth
                        } else {
                            lpNow.x
                        }

                        val panelCenterX = absoluteLeft + rootWidth / 2f
                        val stickToLeft = panelCenterX < screenWidth / 2f

                        if (stickToLeft) {
                            lpNow.gravity = Gravity.BOTTOM or Gravity.START
                            lpNow.x = absoluteLeft.coerceAtLeast(0)
                        } else {
                            lpNow.gravity = Gravity.BOTTOM or Gravity.END
                            val fromRight =
                                (screenWidth - absoluteLeft - rootWidth).coerceAtLeast(0)
                            lpNow.x = fromRight
                        }

                        layoutParams = lpNow
                        wm.updateViewLayout(rootViewNow, lpNow)

                        prefs.edit()
                            .putInt(PREF_GRAVITY, lpNow.gravity)
                            .putInt(PREF_X, lpNow.x)
                            .putInt(PREF_Y, lpNow.y)
                            .apply()
                    }

                    true
                }
                else -> false
            }
        }
    }
}

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

class DrivingStatusOverlayController(
    private val appContext: Context
) {

    companion object {
        private var currentInstance: DrivingStatusOverlayController? = null

        private const val COLOR_BG_DARK = 0xF01E1E1E.toInt()
        private const val COLOR_BG_LIGHT = 0xF0F5F5F5.toInt()
        private const val COLOR_TEXT_DARK_PRIMARY = 0xFFFFFFFF.toInt()
        private const val COLOR_TEXT_DARK_SECONDARY = 0xCCFFFFFF.toInt()
        private const val COLOR_TEXT_LIGHT_PRIMARY = 0xFF1E1E1E.toInt()
        private const val COLOR_TEXT_LIGHT_SECONDARY = 0xFF666666.toInt()

        private const val COLOR_MODE_SPORT = 0xFFFF1744.toInt()
        private const val COLOR_MODE_ECO = 0xFF4CAF50.toInt()
        private const val COLOR_MODE_COMFORT = 0xFF2196F3.toInt()
        private const val COLOR_MODE_ADAPTIVE = 0xFF9C27B0.toInt()

        // Temp colors (dark theme)
        private const val TEMP_DARK_VERY_COLD = 0xFF00E5FF.toInt()
        private const val TEMP_DARK_COLD = 0xFF40C4FF.toInt()
        private const val TEMP_DARK_COOL = 0xFF00E676.toInt()
        private const val TEMP_DARK_COMFORT = 0xFFFFAB00.toInt()
        private const val TEMP_DARK_WARM = 0xFFFF6D00.toInt()
        private const val TEMP_DARK_HOT = 0xFFFF1744.toInt()

        // Temp colors (light theme)
        private const val TEMP_LIGHT_VERY_COLD = 0xFF00838F.toInt()
        private const val TEMP_LIGHT_COLD = 0xFF0277BD.toInt()
        private const val TEMP_LIGHT_COOL = 0xFF00897B.toInt()
        private const val TEMP_LIGHT_COMFORT = 0xFFEF6C00.toInt()
        private const val TEMP_LIGHT_WARM = 0xFFD84315.toInt()
        private const val TEMP_LIGHT_HOT = 0xFFC62828.toInt()

        // Tire colors (dark theme)
        private const val TIRE_DARK_LOW = 0xFFFF1744.toInt()
        private const val TIRE_DARK_HIGH = 0xFFFFAB00.toInt()
        private const val TIRE_DARK_NORMAL = 0xFF00E676.toInt()

        // Tire colors (light theme)
        private const val TIRE_LIGHT_LOW = 0xFFC62828.toInt()
        private const val TIRE_LIGHT_HIGH = 0xFFEF6C00.toInt()
        private const val TIRE_LIGHT_NORMAL = 0xFF00897B.toInt()

        private const val LONG_PRESS_HIDE_TIMEOUT_MS = 1000L
        private const val TEMP_HIDE_DURATION_MS = 5000L
    }
    private fun hideTemporarily() {
        val c = container ?: return

        c.visibility = View.GONE

        // Вернем панель через TEMP_HIDE_DURATION_MS, если она все еще включена
        c.postDelayed({
            if (enabled && container === c) {
                c.visibility = View.VISIBLE
            }
        }, TEMP_HIDE_DURATION_MS)
    }

    private fun setupLongPressToHide(targetView: View) {
        var longPressRunnable: Runnable? = null
        var longPressTriggered = false

        targetView.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    longPressTriggered = false
                    longPressRunnable?.let { v.removeCallbacks(it) }

                    longPressRunnable = Runnable {
                        longPressTriggered = true
                        Toast.makeText(
                            appContext,
                            "Панель скрыта на 5 секунд",
                            Toast.LENGTH_SHORT
                        ).show()
                        hideTemporarily()
                    }
                    v.postDelayed(longPressRunnable!!, LONG_PRESS_HIDE_TIMEOUT_MS)
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    longPressRunnable?.let { v.removeCallbacks(it) }
                    val consumed = longPressTriggered
                    longPressRunnable = null
                    consumed
                }
                else -> false
            }
        }
    }

    private val wm: WindowManager =
        appContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager

    private val density: Float = appContext.resources.displayMetrics.density

    private var layoutParams: WindowManager.LayoutParams? = null
    private var container: FrameLayout? = null
    private var rootRow: LinearLayout? = null

    private var enabled: Boolean = true

    private var tvMode: TextView? = null
    private var tvGear: TextView? = null
    private var tvSpeed: TextView? = null
    private var tvRange: TextView? = null
    private var tvTemps: TextView? = null
    private var tvTires: TextView? = null

    private var attached: Boolean = false

    private var position: String = "bottom"

    private var isDarkTheme: Boolean = true
    private var bgColor: Int = COLOR_BG_DARK
    private var textColor: Int = COLOR_TEXT_DARK_PRIMARY
    private var textSecondaryColor: Int = COLOR_TEXT_DARK_SECONDARY

    init {
        currentInstance?.destroy()
        currentInstance = this
    }

    fun setEnabled(isEnabled: Boolean) {
        if (enabled == isEnabled) return
        enabled = isEnabled
        if (!enabled) {
            destroy()
        }
    }

    fun ensureVisible() {
        if (!enabled) return
        ensureAttached()
    }

    fun setDarkTheme(dark: Boolean) {
        if (isDarkTheme == dark) return

        isDarkTheme = dark
        if (dark) {
            bgColor = COLOR_BG_DARK
            textColor = COLOR_TEXT_DARK_PRIMARY
            textSecondaryColor = COLOR_TEXT_DARK_SECONDARY
        } else {
            bgColor = COLOR_BG_LIGHT
            textColor = COLOR_TEXT_LIGHT_PRIMARY
            textSecondaryColor = COLOR_TEXT_LIGHT_SECONDARY
        }

        rootRow?.background = createBackgroundDrawable()
        tvMode?.setTextColor(textColor)
        tvGear?.setTextColor(textColor)
        tvSpeed?.setTextColor(textColor)
        tvRange?.setTextColor(textColor)
        tvTemps?.setTextColor(textSecondaryColor)
        tvTires?.setTextColor(textSecondaryColor)
    }

    fun setPosition(newPosition: String) {
        if (position == newPosition && !attached) return

        val wasAttached = attached
        destroy()

        position = newPosition

        if (wasAttached) {
            ensureAttached()
        }
    }

    fun updateStatus(state: DrivingStatusOverlayState) {
        if (!enabled) return

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M &&
            !Settings.canDrawOverlays(appContext)
        ) {
            return
        }

        // Проверяем что container жив - если windowToken null, значит view был уничтожен
        if (container != null && container?.windowToken == null) {
            // Container был уничтожен (например при закрытии приложения), очищаем состояние
            attached = false
            container = null
            rootRow = null
            tvMode = null
            tvGear = null
            tvSpeed = null
            tvRange = null
            tvTemps = null
            tvTires = null
        }

        try {
            ensureAttached()
        } catch (e: Throwable) {
            // Если ошибка создания overlay, очищаем состояние для следующей попытки
            android.util.Log.e("DrivingStatusOverlay", "Failed to attach overlay", e)
            attached = false
            container = null
            return
        }

        val modeText = state.modeTitle?.takeIf { it.isNotBlank() } ?: "—"
        tvMode?.text = modeText
        tvMode?.setTextColor(getModeColor(state.modeTitle))

        tvGear?.text = state.gear?.takeIf { it.isNotBlank() } ?: "—"
        tvSpeed?.text = state.speedKmh?.let { "$it км/ч" } ?: "— км/ч"
        tvRange?.text = state.rangeKm?.let { "$it км" } ?: "— км"

        updateTempsTextWithColor(state)
        updateTiresTextWithColor(state)
    }

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
            }
        }

        if (currentInstance === this) {
            currentInstance = null
        }
    }

    private fun ensureAttached() {
        if (attached && container != null && container?.windowToken != null) {
            return
        }

        if (!enabled) return

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

        val heightPx = (56f * density).toInt()

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

            val horizontalPadding = (16f * density).toInt()
            val verticalPadding = (10f * density).toInt()
            setPadding(horizontalPadding, verticalPadding, horizontalPadding, verticalPadding)

            background = createBackgroundDrawable()
        }

        tvRange = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.0f
            )
            maxLines = 1
            text = "— км"
            setTextColor(textColor)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        tvGear = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                0.5f
            )
            maxLines = 1
            text = "—"
            setTextColor(textColor)
            textSize = 22f
            typeface = Typeface.create("sans-serif-black", Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        tvSpeed = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.1f
            )
            maxLines = 1
            text = "— км/ч"
            setTextColor(textColor)
            textSize = 18f
            typeface = Typeface.create("sans-serif-medium", Typeface.BOLD)
            gravity = Gravity.CENTER
        }

        tvMode = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                0.9f
            )
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            text = "—"
            setTextColor(textColor)
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
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
            textSize = 16f
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            gravity = Gravity.CENTER
        }

        tvTires = TextView(appContext).apply {
            layoutParams = LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.MATCH_PARENT,
                1.5f
            )
            maxLines = 1
            ellipsize = android.text.TextUtils.TruncateAt.END
            text = "—"
            setTextColor(textSecondaryColor)
            textSize = 15f
            typeface = Typeface.MONOSPACE
            gravity = Gravity.CENTER_VERTICAL or Gravity.END
        }

        row.addView(tvRange)
        row.addView(tvGear)
        row.addView(tvSpeed)
        row.addView(tvMode)
        row.addView(tvTemps)
        row.addView(tvTires)

        setupLongPressToHide(row)

        rootContainer.addView(row)

        try {
            wm.addView(rootContainer, layoutParams ?: lp)
        } catch (_: Throwable) {
            return
        }

        container = rootContainer
        rootRow = row
        attached = true
    }

    private fun getModeColor(mode: String?): Int {
        return when (mode?.lowercase()) {
            "sport" -> COLOR_MODE_SPORT
            "eco" -> COLOR_MODE_ECO
            "comfort" -> COLOR_MODE_COMFORT
            "adaptive" -> COLOR_MODE_ADAPTIVE
            else -> textColor
        }
    }

    private fun getTempColor(tempC: Float?): Int {
        if (tempC == null) return textSecondaryColor

        return if (isDarkTheme) {
            when {
                tempC < 0 -> TEMP_DARK_VERY_COLD
                tempC < 10 -> TEMP_DARK_COLD
                tempC < 18 -> TEMP_DARK_COOL
                tempC < 24 -> TEMP_DARK_COMFORT
                tempC < 30 -> TEMP_DARK_WARM
                else -> TEMP_DARK_HOT
            }
        } else {
            when {
                tempC < 0 -> TEMP_LIGHT_VERY_COLD
                tempC < 10 -> TEMP_LIGHT_COLD
                tempC < 18 -> TEMP_LIGHT_COOL
                tempC < 24 -> TEMP_LIGHT_COMFORT
                tempC < 30 -> TEMP_LIGHT_WARM
                else -> TEMP_LIGHT_HOT
            }
        }
    }

    private fun getTirePressureColor(pressure: Int?): Int {
        if (pressure == null) return textSecondaryColor

        return if (isDarkTheme) {
            when {
                pressure < 200 -> TIRE_DARK_LOW
                pressure > 280 -> TIRE_DARK_HIGH
                else -> TIRE_DARK_NORMAL
            }
        } else {
            when {
                pressure < 200 -> TIRE_LIGHT_LOW
                pressure > 280 -> TIRE_LIGHT_HIGH
                else -> TIRE_LIGHT_NORMAL
            }
        }
    }

    private fun updateTempsTextWithColor(state: DrivingStatusOverlayState) {
        val cabin = state.cabinTempC
        val ambient = state.ambientTempC

        val cabinText = cabin?.toInt()?.let { "$it°" } ?: "—°"
        val ambientText = ambient?.toInt()?.let { "$it°" } ?: "—°"

        val cabinColor = getTempColor(cabin)
        val ambientColor = getTempColor(ambient)

        val fullText = "IN $cabinText / OUT $ambientText"
        val spannable = android.text.SpannableString(fullText)

        val cabinStart = fullText.indexOf(cabinText)
        if (cabinStart >= 0) {
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(cabinColor),
                cabinStart,
                cabinStart + cabinText.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val ambientStart = fullText.indexOf(ambientText, cabinStart + cabinText.length)
        if (ambientStart >= 0) {
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(ambientColor),
                ambientStart,
                ambientStart + ambientText.length,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        tvTemps?.text = spannable
    }

    private fun updateTiresTextWithColor(state: DrivingStatusOverlayState) {
        val fl = state.tirePressureFrontLeft
        val fr = state.tirePressureFrontRight
        val rl = state.tirePressureRearLeft
        val rr = state.tirePressureRearRight

        fun format(value: Int?): String =
            value?.let {
                val bar = it / 100.0
                String.format(java.util.Locale.US, "%.1f", bar)
            } ?: "-"

        val flText = format(fl)
        val frText = format(fr)
        val rlText = format(rl)
        val rrText = format(rr)

        val fullText = "↖ $flText ↗ $frText │ ↙ $rlText ↘ $rrText"
        val spannable = android.text.SpannableString(fullText)

        val flColor = getTirePressureColor(fl)
        val frColor = getTirePressureColor(fr)
        val rlColor = getTirePressureColor(rl)
        val rrColor = getTirePressureColor(rr)

        var start = 0
        var end = start + 2 + flText.length
        spannable.setSpan(
            android.text.style.ForegroundColorSpan(flColor),
            start,
            end,
            android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
        )

        start = fullText.indexOf("↗", end)
        if (start >= 0) {
            end = start + 2 + frText.length
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(frColor),
                start,
                end,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        start = fullText.indexOf("↙", end)
        if (start >= 0) {
            end = start + 2 + rlText.length
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(rlColor),
                start,
                end,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        start = fullText.indexOf("↘", end)
        if (start >= 0) {
            end = start + 2 + rrText.length
            spannable.setSpan(
                android.text.style.ForegroundColorSpan(rrColor),
                start,
                end,
                android.text.Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        tvTires?.text = spannable
    }

    private fun createBackgroundDrawable(): GradientDrawable {
        return GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE

            val radius = 16f * density
            cornerRadii = if (position == "top") {
                floatArrayOf(
                    0f, 0f,
                    0f, 0f,
                    radius, radius,
                    radius, radius
                )
            } else {
                floatArrayOf(
                    radius, radius,
                    radius, radius,
                    0f, 0f,
                    0f, 0f
                )
            }

            setColor(bgColor)
        }
    }
}