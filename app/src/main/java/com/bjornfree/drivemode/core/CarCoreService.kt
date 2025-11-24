package com.bjornfree.drivemode.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log

/**
 * CarCoreService — центральная точка работы с машиной.
 *
 * Задачи:
 * 1) Определить окружение (есть ли automotive, какие system properties и т.п.).
 * 2) Попробовать подключиться к android.car.Car и получить CarPropertyManager.
 * 3) Давать удобные методы проверки прав и чтения базовых свойств (зажигание, режим вождения и т.п.).
 *
 * Все обращения к android.car.* и VehicleProperty сделаны через reflection,
 * чтобы класс не падал на обычном Android без car-фреймворка.
 */
class CarCoreService(
    private val appContext: Context
) {

    companion object {
        private const val TAG = "CarCoreService"

        // Имена классов/констант, чтобы не ссылаться на них напрямую
        private const val CLASS_CAR = "android.car.Car"
        private const val CLASS_VEHICLE_PROPERTY =
            "android.hardware.automotive.vehicle.V2_0.VehicleProperty"

        // Значение Car.PROPERTY_SERVICE, но строкой чтобы не тянуть сам класс
        private const val CAR_PROPERTY_SERVICE_NAME = "property"

        // Часто используемые permission-ы (строки можно использовать даже если perm не объявлен в SDK)
        private const val PERM_CAR_CONTROL_APP = "android.car.permission.CAR_CONTROL_APP"
        private const val PERM_CAR_PROPERTY_ACCESS = "android.car.permission.CAR_PROPERTY_ACCESS"
        private const val PERM_CAR_PROPERTY_READ_WRITE =
            "android.car.permission.CAR_PROPERTY_READ_WRITE"

        // VehicleProperty.* поля, которые нас интересуют
        private const val FIELD_IGNITION_STATE = "IGNITION_STATE"
        private const val FIELD_DRIVER_MODE = "INFO_ID_VDRIVEINFO_DRIVER_MODE"

        /**
         * Остальные сервисы должны по возможности использовать эти константы,
         * а не raw-строки.
         */
    }

    private var carInstance: Any? = null
    private var carPropertyManager: Any? = null
    private var isCarConnected: Boolean =
        false // логический флаг, отражающий успешный вызов connect() (если он есть)

    /**
     * Простая обёртка для состояния зажигания.
     */
    data class IgnitionSnapshot(
        val rawState: Int
    ) {
        /**
         * true, если зажигание не в положении OFF.
         * В большинстве прошивок 0 = OFF, остальные значения = какие-то активные состояния.
         */
        val isOnLike: Boolean
            get() = rawState != 0
    }

    /**
     * Удобный enum для режимов движения.
     *
     * Маппинг основан на договорённостях прошивки:
     *  0 — NORMAL
     *  1 — COMFORT
     *  2 — SPORT
     *  3 — ECO
     *  4 — OFFROAD
     *  5 — SNOW
     *
     * Если пришло неизвестное значение — используем UNKNOWN, но raw сохраняем.
     */
    enum class DriveMode(val raw: Int) {
        NORMAL(0),
        COMFORT(1),
        SPORT(2),
        ECO(3),
        OFFROAD(4),
        SNOW(5),
        UNKNOWN(-1);

        companion object {
            fun fromRaw(raw: Int?): DriveMode {
                if (raw == null) return UNKNOWN
                return values().firstOrNull { it.raw == raw } ?: UNKNOWN
            }
        }
    }

    /**
     * Инициализация. Нужно вызвать один раз (например, из onCreate() сервиса/активити).
     *
     * Ничего не бросает наружу — все ошибки логируются.
     */
    fun init() {
        val hasAutomotive =
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)
        Log.i(
            TAG,
            "init(): FEATURE_AUTOMOTIVE = $hasAutomotive, sdk=${Build.VERSION.SDK_INT}, product=${Build.PRODUCT}"
        )

        if (!hasAutomotive) {
            // На эмуляторе/обычном телефоне это нормально — просто работаем без прямого доступа к машине.
            return
        }

        tryConnectCarApi()
    }

    /**
     * Освободить ресурсы.
     * Вызывать при остановке сервиса/активити.
     */
    fun release() {
        try {
            val car = carInstance
            if (car != null) {
                try {
                    val clazz = car.javaClass
                    val disconnect = clazz.getMethod("disconnect")
                    disconnect.invoke(car)
                    Log.i(TAG, "release(): Car.disconnect() invoked")
                } catch (t: Throwable) {
                    Log.w(TAG, "release(): Car.disconnect() failed or not available", t)
                }
            }
        } catch (t: Throwable) {
            Log.e(TAG, "release(): error while disconnecting car", t)
        } finally {
            carInstance = null
            carPropertyManager = null
            isCarConnected = false
        }
    }

    /**
     * Проверка, доступен ли car API на этом девайсе.
     */
    private fun isCarApiAvailable(): Boolean {
        return try {
            Class.forName(CLASS_CAR)
            true
        } catch (_: ClassNotFoundException) {
            false
        }
    }

    /**
     * Попытка подключиться к Car API и получить CarPropertyManager.
     */
    fun tryConnectCarApi() {
        if (!isCarApiAvailable()) {
            Log.w(TAG, "tryConnectCarApi(): android.car.Car not found on this build")
            return
        }
        if (isCarConnected && carPropertyManager != null) {
            return
        }

        try {
            val carCls = Class.forName(CLASS_CAR)

            // Car.createCar(Context)
            val createCar = carCls.getMethod("createCar", Context::class.java)
            val car = createCar.invoke(null, appContext)
            carInstance = car

            // car.connect()
            try {
                val connect = carCls.getMethod("connect")
                connect.invoke(car)
                isCarConnected = true
                Log.i(TAG, "tryConnectCarApi(): Car.connect() invoked")
            } catch (t: Throwable) {
                // На некоторых прошивках connect() может не требоваться/быть другой реализации
                Log.w(TAG, "tryConnectCarApi(): Car.connect() failed or not available", t)
            }

            // val mgr = car.getCarManager(Car.PROPERTY_SERVICE)
            try {
                val getCarManager =
                    carCls.getMethod("getCarManager", String::class.java)
                val mgr = getCarManager.invoke(car, CAR_PROPERTY_SERVICE_NAME)
                carPropertyManager = mgr
                Log.i(TAG, "tryConnectCarApi(): CarPropertyManager obtained via PROPERTY_SERVICE")
            } catch (t: Throwable) {
                Log.e(TAG, "tryConnectCarApi(): failed to obtain CarPropertyManager", t)
            }
        } catch (t: Throwable) {
            Log.e(TAG, "tryConnectCarApi(): failed to create/connect Car", t)
        }
    }

    /**
     * Проверка на наличие прав доступа к car-свойствам.
     * Можно расширить, если нужно.
     */
    fun hasCarPermissions(): Boolean {
        val pm = appContext.packageManager
        val p1 = pm.checkPermission(PERM_CAR_CONTROL_APP, appContext.packageName)
        val p2 = pm.checkPermission(PERM_CAR_PROPERTY_ACCESS, appContext.packageName)
        val p3 = pm.checkPermission(PERM_CAR_PROPERTY_READ_WRITE, appContext.packageName)
        val has = (p1 == PackageManager.PERMISSION_GRANTED
                || p2 == PackageManager.PERMISSION_GRANTED
                || p3 == PackageManager.PERMISSION_GRANTED)
        Log.i(TAG, "hasCarPermissions() = $has (p1=$p1, p2=$p2, p3=$p3)")
        return has
    }

    /**
     * Базовый helper для чтения int-свойств по VehicleProperty.FIELD_NAME.
     *
     * @param vehiclePropertyFieldName Имя поля в VehicleProperty (например, "IGNITION_STATE").
     * @param areaId Обычно 0, если не используется разделение по зонам.
     */
    fun readIntPropertyOrNull(
        vehiclePropertyFieldName: String,
        areaId: Int = 0,
    ): Int? {
        val mgr = carPropertyManager ?: run {
            Log.w(TAG, "readIntPropertyOrNull(): carPropertyManager is null, trying to reconnect")
            tryConnectCarApi()
            carPropertyManager
        } ?: return null

        return try {
            val vpCls = Class.forName(CLASS_VEHICLE_PROPERTY)
            val field = vpCls.getField(vehiclePropertyFieldName)
            val propId = field.getInt(null)

            val method = mgr.javaClass.getMethod(
                "getIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            val value = method.invoke(mgr, propId, areaId)
            (value as? Int)?.also {
                Log.d(
                    TAG,
                    "readIntPropertyOrNull($vehiclePropertyFieldName, area=$areaId) = $it (propId=0x${
                        Integer.toHexString(propId)
                    })"
                )
            }
        } catch (cnf: ClassNotFoundException) {
            Log.w(TAG, "readIntPropertyOrNull(): VehicleProperty class not found", cnf)
            null
        } catch (noField: NoSuchFieldException) {
            Log.w(
                TAG,
                "readIntPropertyOrNull(): field $vehiclePropertyFieldName not found in VehicleProperty",
                noField
            )
            null
        } catch (t: Throwable) {
            Log.e(TAG, "readIntPropertyOrNull(): failed for $vehiclePropertyFieldName", t)
            null
        }
    }

    /**
     * Сырый int-стейт зажигания.
     */
    fun readIgnitionStateRawOrNull(): Int? =
        readIntPropertyOrNull(FIELD_IGNITION_STATE)

    /**
     * Обёртка над readIgnitionStateRawOrNull(), возвращающая IgnitionSnapshot.
     * Удобно использовать в сервисах (AutoHeaterService и т.п.), чтобы не работать с "магическими" числами.
     */
    fun readIgnitionSnapshotOrNull(): IgnitionSnapshot? =
        readIgnitionStateRawOrNull()?.let { IgnitionSnapshot(it) }

    /**
     * Сырый int-код режима вождения.
     *
     * Ожидаемые значения:
     *  0 — NORMAL
     *  1 — COMFORT
     *  2 — SPORT
     *  3 — ECO
     *  4 — OFFROAD
     *  5 — SNOW
     * но конкретный маппинг можно делать снаружи, в более высокоуровневом слое.
     */
    fun readDriveModeRawOrNull(): Int? = readIntPropertyOrNull(FIELD_DRIVER_MODE)

    /**
     * Типизированный helper для режима вождения.
     * Даже если прошивка вернула неизвестное значение — вернётся DriveMode.UNKNOWN,
     * а сырое значение будет доступно в поле raw.
     */
    fun readDriveModeTyped(): DriveMode =
        DriveMode.fromRaw(readDriveModeRawOrNull())
}