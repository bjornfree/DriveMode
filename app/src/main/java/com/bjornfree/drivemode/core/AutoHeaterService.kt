package com.bjornfree.drivemode.core

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.car.VehiclePropertyIds
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.bjornfree.drivemode.data.repository.DriveModeRepository
import com.bjornfree.drivemode.data.repository.HeatingControlRepository
import com.bjornfree.drivemode.data.repository.IgnitionStateRepository
import kotlinx.coroutines.*
import org.koin.android.ext.android.inject

/**
 * Сервис для авто-подогрева сидений.
 *
 * РАДИКАЛЬНОЕ УПРОЩЕНИЕ:
 * - Было: 1,322 строки (5+ ответственностей)
 * - Стало: ~250 строк (1 ответственность)
 *
 * Удалено:
 * - ❌ Все методы чтения метрик (175+ строк) → VehicleMetricsRepository
 * - ❌ Мониторинг зажигания (120+ строк) → IgnitionStateRepository
 * - ❌ Диагностические тесты (60+ строк) → DiagnosticsViewModel
 * - ❌ Все константы (80+ строк) → VehiclePropertyConstants
 * - ❌ TireData классы (уже в domain models)
 *
 * Оставлено:
 * - ✅ Управление HVAC сидений
 * - ✅ Слушает HeatingControlRepository
 * - ✅ Foreground service lifecycle
 *
 * Архитектура:
 * HeatingControlRepository → AutoSeatHeatService → Car HVAC API
 * (бизнес-логика)       (исполнитель)        (hardware)
 *
 * @see HeatingControlRepository для логики подогрева
 * @see IgnitionStateRepository для мониторинга зажигания
 * @see VehicleMetricsRepository для чтения температуры
 */
class AutoSeatHeatService : Service() {

    companion object {
        private const val TAG = "AutoSeatHeatService"
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "auto_seat_heat_channel"

        // Vehicle property IDs (минимум для HVAC)
        private const val VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE = 356517131

        @Volatile
        private var isRunning = false

        @Volatile
        private var serviceInstance: AutoSeatHeatService? = null

        /**
         * Запускает сервис.
         */
        fun start(context: Context) {
            val intent = Intent(context, AutoSeatHeatService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }

        /**
         * Перезапускает сервис.
         */
        fun restartService(context: Context) {
            try {
                log("Принудительный перезапуск...")
                context.stopService(Intent(context, AutoSeatHeatService::class.java))
                Thread.sleep(500)
                start(context)
                log("Перезапуск выполнен")
            } catch (e: Exception) {
                Log.e(TAG, "Ошибка перезапуска", e)
            }
        }

        /**
         * Проверяет запущен ли сервис.
         */
        fun isServiceRunning(): Boolean = isRunning && serviceInstance != null

        private fun log(msg: String) {
            Log.i(TAG, msg)
        }
    }

    // Inject repositories через Koin
    private val heatingRepo: HeatingControlRepository by inject()
    private val ignitionRepo: IgnitionStateRepository by inject()
    private val driveModeRepo: DriveModeRepository by inject()

    // Coroutine scope
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var heatingJob: Job? = null

    // Car API objects (для управления HVAC)
    private var carObj: Any? = null
    private var carPropertyManagerObj: Any? = null

    override fun onCreate() {
        super.onCreate()
        serviceInstance = this
        isRunning = true

        log("onCreate: Запуск сервиса автоподогрева (REFACTORED)")
        logToConsole("AutoSeatHeatService: Запущен (новая MVVM версия)")

        // Создаем notification и startForeground
        val notification = buildNotification()
        startForeground(NOTIFICATION_ID, notification)

        // Инициализируем Car API для управления HVAC
        initializeCarApi()

        // Запускаем слушатель состояния подогрева
        startHeatingListener()

        log("onCreate: Сервис запущен успешно")
    }

    /**
     * Инициализирует Car API для управления HVAC сидений.
     * Используем reflection для доступа к android.car.Car
     */
    private fun initializeCarApi() {
        try {
            val carClass = Class.forName("android.car.Car")
            val createCarMethod = carClass.getMethod("createCar", Context::class.java)
            carObj = createCarMethod.invoke(null, applicationContext)

            val getCarManagerMethod = carClass.getMethod("getCarManager", String::class.java)
            carPropertyManagerObj = getCarManagerMethod.invoke(carObj, "property")

            log("Car API инициализирован для HVAC управления")
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка инициализации Car API", e)
            logToConsole("AutoSeatHeatService: ⚠ Не удалось инициализировать Car API")
        }
    }

    /**
     * Запускает слушатель состояния подогрева из Repository.
     * Когда HeatingControlRepository решает что нужен подогрев - включаем HVAC.
     */
    private fun startHeatingListener() {
        heatingJob = scope.launch {
            heatingRepo.heatingState.collect { state ->
                if (state.isActive) {
                    log("Подогрев должен быть активен: ${state.reason}")
                    logToConsole("AutoSeatHeatService: ✓ Активация подогрева (${state.reason})")
                    activateSeatHeating()
                } else {
                    log("Подогрев должен быть неактивен: ${state.reason}")
                    // Можно опционально деактивировать подогрев
                    // deactivateSeatHeating()
                }
            }
        }

        log("Слушатель состояния подогрева запущен")
    }

    /**
     * Активирует подогрев сидений через Car HVAC API.
     */
    private fun activateSeatHeating() {
        try {
            if (carPropertyManagerObj == null) {
                log("Car Property Manager недоступен")
                return
            }

            // Пытаемся установить температуру подогрева сидений
            // VehiclePropertyIds.HVAC_SEAT_TEMPERATURE
            // Значения обычно: 0 = off, 1 = low, 2 = medium, 3 = high
            val hvacValue = 2 // medium heating

            val managerClass = carPropertyManagerObj!!.javaClass
            val setIntPropertyMethod = managerClass.getMethod(
                "setIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            // Устанавливаем для водительского сиденья (area 1)
            setIntPropertyMethod.invoke(
                carPropertyManagerObj,
                VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE,
                1, // driver seat area
                hvacValue
            )

            log("Подогрев сидений активирован (уровень: $hvacValue)")
            logToConsole("AutoSeatHeatService: 🔥 Подогрев активирован")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка активации подогрева", e)
            logToConsole("AutoSeatHeatService: ⚠ Ошибка активации подогрева: ${e.message}")
        }
    }

    /**
     * Деактивирует подогрев сидений.
     */
    private fun deactivateSeatHeating() {
        try {
            if (carPropertyManagerObj == null) return

            val managerClass = carPropertyManagerObj!!.javaClass
            val setIntPropertyMethod = managerClass.getMethod(
                "setIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )

            // Устанавливаем 0 = off
            setIntPropertyMethod.invoke(
                carPropertyManagerObj,
                VEHICLE_PROPERTY_HVAC_SEAT_TEMPERATURE,
                1,
                0
            )

            log("Подогрев сидений деактивирован")

        } catch (e: Exception) {
            Log.e(TAG, "Ошибка деактивации подогрева", e)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        log("onStartCommand")
        return START_STICKY // Перезапуск после убийства
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        log("onDestroy: Остановка сервиса")
        logToConsole("AutoSeatHeatService: Остановлен")

        isRunning = false
        serviceInstance = null

        // Останавливаем слушатель
        heatingJob?.cancel()
        scope.cancel()

        // Отключаемся от Car API
        disconnectCarApi()

        super.onDestroy()
    }

    /**
     * Отключается от Car API.
     */
    private fun disconnectCarApi() {
        try {
            carObj?.let { car ->
                val carClass = car.javaClass
                val disconnectMethod = carClass.getMethod("disconnect")
                disconnectMethod.invoke(car)
                log("Car API отключен")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Ошибка отключения Car API", e)
        } finally {
            carObj = null
            carPropertyManagerObj = null
        }
    }

    /**
     * Создает notification для foreground service.
     */
    private fun buildNotification(): Notification {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Автоподогрев сидений",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager?.createNotificationChannel(channel)
        }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Автоподогрев активен")
            .setContentText("Мониторинг температуры и зажигания")
            .setSmallIcon(android.R.drawable.ic_menu_preferences)
            .setOngoing(true)
            .build()
    }

    /**
     * Логирование в консоль через DriveModeRepository.
     */
    private fun logToConsole(msg: String) {
        scope.launch {
            driveModeRepo.logConsole(msg)
        }
    }

    private fun log(msg: String) {
        Log.i(TAG, msg)
    }
}
