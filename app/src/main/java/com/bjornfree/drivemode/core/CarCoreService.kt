package com.bjornfree.drivemode.core

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build

class CarCoreService(
    private val appContext: Context
) {

    companion object {
        private const val CLASS_CAR = "android.car.Car"
        private const val CLASS_VEHICLE_PROPERTY =
            "android.hardware.automotive.vehicle.V2_0.VehicleProperty"

        private const val CAR_PROPERTY_SERVICE_NAME = "property"

        private const val PERM_CAR_CONTROL_APP = "android.car.permission.CAR_CONTROL_APP"
        private const val PERM_CAR_PROPERTY_ACCESS = "android.car.permission.CAR_PROPERTY_ACCESS"
        private const val PERM_CAR_PROPERTY_READ_WRITE =
            "android.car.permission.CAR_PROPERTY_READ_WRITE"

        private const val FIELD_IGNITION_STATE = "IGNITION_STATE"
        private const val FIELD_DRIVER_MODE = "INFO_ID_VDRIVEINFO_DRIVER_MODE"
    }

    private var carInstance: Any? = null
    private var carPropertyManager: Any? = null
    private var isCarConnected: Boolean = false

    data class IgnitionSnapshot(val rawState: Int) {
        val isOnLike: Boolean get() = rawState != 0
    }

    enum class DriveMode(val raw: Int) {
        NORMAL(0), COMFORT(1), SPORT(2), ECO(3), OFFROAD(4), SNOW(5), UNKNOWN(-1);

        companion object {
            fun fromRaw(raw: Int?): DriveMode =
                values().firstOrNull { it.raw == raw } ?: UNKNOWN
        }
    }

    fun init() {
        val hasAutomotive =
            appContext.packageManager.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE)

        if (!hasAutomotive) return
        tryConnectCarApi()
    }

    fun release() {
        try {
            carInstance?.let { car ->
                try {
                    val disconnect = car.javaClass.getMethod("disconnect")
                    disconnect.invoke(car)
                } catch (_: Throwable) {
                }
            }
        } finally {
            carInstance = null
            carPropertyManager = null
            isCarConnected = false
        }
    }

    private fun isCarApiAvailable(): Boolean {
        return try {
            Class.forName(CLASS_CAR)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun tryConnectCarApi() {
        if (!isCarApiAvailable()) return
        if (isCarConnected && carPropertyManager != null) return

        try {
            val carCls = Class.forName(CLASS_CAR)
            val createCar = carCls.getMethod("createCar", Context::class.java)
            val car = createCar.invoke(null, appContext)
            carInstance = car

            try {
                val connect = carCls.getMethod("connect")
                connect.invoke(car)
                isCarConnected = true
            } catch (_: Throwable) {
            }

            try {
                val getCarManager = carCls.getMethod("getCarManager", String::class.java)
                carPropertyManager = getCarManager.invoke(car, CAR_PROPERTY_SERVICE_NAME)
            } catch (_: Throwable) {
                carPropertyManager = null
            }
        } catch (_: Throwable) {
            carInstance = null
            carPropertyManager = null
            isCarConnected = false
        }
    }

    fun hasCarPermissions(): Boolean {
        val pm = appContext.packageManager
        val p1 = pm.checkPermission(PERM_CAR_CONTROL_APP, appContext.packageName)
        val p2 = pm.checkPermission(PERM_CAR_PROPERTY_ACCESS, appContext.packageName)
        val p3 = pm.checkPermission(PERM_CAR_PROPERTY_READ_WRITE, appContext.packageName)
        return (p1 == PackageManager.PERMISSION_GRANTED
                || p2 == PackageManager.PERMISSION_GRANTED
                || p3 == PackageManager.PERMISSION_GRANTED)
    }

    fun readIntPropertyOrNull(
        vehiclePropertyFieldName: String,
        areaId: Int = 0,
    ): Int? {
        val mgr = carPropertyManager ?: run {
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
            method.invoke(mgr, propId, areaId) as? Int
        } catch (_: Throwable) {
            null
        }
    }

    fun readIgnitionStateRawOrNull(): Int? =
        readIntPropertyOrNull(FIELD_IGNITION_STATE)

    fun readIgnitionSnapshotOrNull(): IgnitionSnapshot? =
        readIgnitionStateRawOrNull()?.let { IgnitionSnapshot(it) }

    fun readDriveModeRawOrNull(): Int? =
        readIntPropertyOrNull(FIELD_DRIVER_MODE)

    fun readDriveModeTyped(): DriveMode =
        DriveMode.fromRaw(readDriveModeRawOrNull())
}