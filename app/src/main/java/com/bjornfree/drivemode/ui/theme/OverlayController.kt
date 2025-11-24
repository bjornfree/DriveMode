// ui/OverlayController.kt
package com.bjornfree.drivemode.ui

import android.content.Context
import android.graphics.PixelFormat
import android.graphics.Color
import android.os.Build
import android.view.*
import android.widget.FrameLayout
import androidx.annotation.RequiresApi
import androidx.core.content.getSystemService
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.audio.AudioAttributes
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.ui.AspectRatioFrameLayout
import com.google.android.exoplayer2.DefaultRenderersFactory
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.C

/**
 * Контроллер оверлея с видео без рывков:
 *  - Один экземпляр ExoPlayer на всё время жизни сервиса.
 *  - Не пересоздаём/не release() при каждом показе — только stop()+setMediaItem()+prepare()+play().
 *  - Контейнер добавляется в WindowManager один раз; при скрытии ставим GONE, при показе — VISIBLE.
 */
class OverlayController(private val appContext: Context) {

    private val wm: WindowManager = appContext.getSystemService()!!
    private var container: FrameLayout? = null
    private var playerView: PlayerView? = null
    private var muted: Boolean = true

    private val indexByKey = mutableMapOf<String, Int>()
    private var playlistPrepared = false

    // Рендереры с fallback декодера (важно для старых чипов на Android 9)
    private val renderersFactory = DefaultRenderersFactory(appContext)
        .setEnableDecoderFallback(true)

    // Адаптированный LoadControl для локальных коротких клипов (~5 сек, ~20 МБ)
    private val loadControl = DefaultLoadControl.Builder()
        // небольшие буферы, чтобы быстрее стартовать, но переживать micro-стопы IO
        .setBufferDurationsMs(
            /* minBufferMs = */ 1500,
            /* maxBufferMs = */ 5000,
            /* bufferForPlaybackMs = */ 250,
            /* bufferForPlaybackAfterRebufferMs = */ 500
        )
        .build()

    // Один плеер на весь жизненный цикл контроллера
    private val player: ExoPlayer = ExoPlayer.Builder(appContext, renderersFactory)
        .setLoadControl(loadControl)
        .build().apply {
            // Аудио-атрибуты без агрессивного захвата фокуса
            setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(com.google.android.exoplayer2.C.USAGE_MEDIA)
                    .setContentType(com.google.android.exoplayer2.C.AUDIO_CONTENT_TYPE_MOVIE)
                    .build(),
                /* handleAudioFocus= */ false
            )
            // Аппаратное ускорение + корректный масштаб без лишних расчётов
            setVideoScalingMode(C.VIDEO_SCALING_MODE_DEFAULT)
            repeatMode = Player.REPEAT_MODE_OFF
            playWhenReady = true
            volume = 0f
        }

    /**
     * Предзагружаем плейлист из фиксированного набора режимов.
     * Ключи: "eco", "comfort", "sport", "adaptive" (в нижнем регистре).
     */
    fun setPlaylist(urisByKey: Map<String, String>) {
        val items = ArrayList<MediaItem>(urisByKey.size)
        indexByKey.clear()
        var idx = 0
        // фиксируем порядок, чтобы seekTo был мгновенным
        for ((k, u) in urisByKey) {
            indexByKey[k.lowercase()] = idx++
            items.add(MediaItem.fromUri(u))
        }
        player.setMediaItems(items, /*resetPosition=*/true)
        player.prepare()
        playlistPrepared = true
    }

    /**
     * Мгновенно переключаемся по ключу плейлиста, если он подготовлен.
     * Если плейлист ещё не подготовлен — падаем обратно на одиночный показ по URI.
     */
    fun playByKeyOrUri(keyLower: String, fallbackUri: String) {
        ensureAttached()
        player.volume = 0f
        val idx = indexByKey[keyLower]
        if (playlistPrepared && idx != null) {
            player.pause()
            player.seekTo(idx, /*positionMs=*/0)
            player.playWhenReady = true
            player.play()
            container?.visibility = View.VISIBLE
        } else {
            // фоллбек на одиночный режим
            player.stop()
            player.setMediaItem(MediaItem.fromUri(fallbackUri))
            player.prepare()
            player.play()
            container?.visibility = View.VISIBLE
        }
    }

    // Публичный показ видео: только переключаем mediaItem и показываем контейнер
    fun showVideo(uriString: String) {
        ensureAttached()
        player.volume = 0f
        // Готовим новый ролик без пересоздания плеера/вьюхи
        player.stop()
        player.setMediaItem(MediaItem.fromUri(uriString))
        player.prepare()
        player.play()
        // Показываем контейнер
        container?.visibility = View.VISIBLE
    }

    /**
     * Включает/выключает звук плеера. Вызывается сервисом перед показом ролика.
     */
    fun setMuted(value: Boolean) {
        muted = true
        player.volume = 0f
    }

    // Скрыть (не удаляя из WindowManager) — минимальные накладные расходы
    fun hide() {
        player.pause()
        container?.visibility = View.GONE
    }

    // Полное уничтожение ресурсов — зови из Service.onDestroy()
    fun destroy() {
        try {
            playerView?.player = null
            player.release()
        } catch (_: Exception) {}
        playerView = null
        container?.let { c ->
            try { wm.removeView(c) } catch (_: Exception) {}
        }
        container = null
    }

    // --- Внутреннее ---

    private fun ensureAttached() {
        if (container != null && container?.windowToken != null) return

        val layoutType = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else
            WindowManager.LayoutParams.TYPE_PHONE

        val lp = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            layoutType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                    WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON,
            PixelFormat.OPAQUE
        ).apply {
            // Избавляемся от лишних перекомпоновок
            gravity = Gravity.CENTER
            flags = flags or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
            windowAnimations = 0
        }

        val root = FrameLayout(appContext).apply {
            clipToPadding = false
            clipChildren = false
            layoutTransition = null
            setPadding(0, 0, 0, 0)
            // Прозрачный фон; контейнер перекрывает весь экран
            setBackgroundColor(0x00000000)
            visibility = View.GONE
            // Максимально полноэкранный режим поверх системных панелей
            systemUiVisibility = (
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE or
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_FULLSCREEN or
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION or
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            )
            ViewCompat.setOnApplyWindowInsetsListener(this) { _, _ ->
                // Игнорируем инсетсы, чтобы видео занимало весь экран на API < 30
                WindowInsetsCompat.CONSUMED
            }
        }

        val pv = PlayerView(appContext).apply {
            useController = false
            keepScreenOn = true
            player = this@OverlayController.player
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
            setShutterBackgroundColor(Color.BLACK)
            resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FILL
            setKeepContentOnPlayerReset(true)
        }

        root.addView(pv)
        wm.addView(root, lp)

        container = root
        playerView = pv
    }
}