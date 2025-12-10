package com.bjornfree.drivemode.data.car

import android.content.Context
import java.lang.reflect.Method
import java.util.concurrent.ConcurrentHashMap

class CarPropertyManagerSingleton(private val context: Context) {

    companion object {
        private const val CLASS_CAR = "android.car.Car"
        private const val CLASS_CAR_PROPERTY_MANAGER =
            "android.car.hardware.property.CarPropertyManager"
        private const val CLASS_CAR_PROPERTY_VALUE =
            "android.car.hardware.CarPropertyValue"
        private const val CAR_PROPERTY_SERVICE_NAME = "property"
    }

    @Volatile
    private var carInstance: Any? = null

    @Volatile
    private var managerInstance: Any? = null

    @Volatile
    private var isInitialized = false

    @Volatile
    private var carApiUnavailable = false

    private val methodCache = ConcurrentHashMap<String, Method>()

    private var carClass: Class<*>? = null
    private var managerClass: Class<*>? = null

    // Кэш для CarPropertyValue
    private var carPropertyValueClass: Class<*>? = null
    private var cpvGetPropertyIdMethod: Method? = null
    private var cpvGetValueMethod: Method? = null

    @Synchronized
    fun initialize(): Boolean {
        if (carApiUnavailable) return false
        if (isInitialized && carInstance != null && managerInstance != null) return true

        return try {
            val carCls = Class.forName(CLASS_CAR)
            carClass = carCls

            val createCarMethod = carCls.getMethod("createCar", Context::class.java)
            val car = createCarMethod.invoke(null, context)
            carInstance = car

            try {
                val isConnectedMethod = carCls.getMethod("isConnected")
                val isConnected = isConnectedMethod.invoke(car) as? Boolean ?: false
                if (!isConnected) {
                    val connectMethod = carCls.getMethod("connect")
                    connectMethod.invoke(car)
                }
            } catch (_: NoSuchMethodException) {
            } catch (_: IllegalStateException) {
            }

            val getCarManagerMethod = carCls.getMethod("getCarManager", String::class.java)
            val manager = getCarManagerMethod.invoke(car, CAR_PROPERTY_SERVICE_NAME)
            managerInstance = manager

            managerClass = Class.forName(CLASS_CAR_PROPERTY_MANAGER)

            cacheCommonMethods()

            isInitialized = true
            true
        } catch (_: ClassNotFoundException) {
            carApiUnavailable = true
            carInstance = null
            managerInstance = null
            isInitialized = false
            false
        } catch (_: Exception) {
            carInstance = null
            managerInstance = null
            isInitialized = false
            false
        }
    }

    private fun cacheCommonMethods() {
        val mgrClass = managerClass ?: return
        try {
            methodCache["getIntProperty"] = mgrClass.getMethod(
                "getIntProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
            methodCache["getFloatProperty"] = mgrClass.getMethod(
                "getFloatProperty",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType
            )
        } catch (_: Exception) {
        }
    }

    private fun getCachedMethod(methodName: String, vararg parameterTypes: Class<*>): Method? {
        val mgrClass = managerClass ?: return null
        return methodCache.getOrPut(methodName) {
            mgrClass.getMethod(methodName, *parameterTypes)
        }
    }

    fun readIntProperty(propertyId: Int, areaId: Int = 0): Int? {
        if (!ensureInitialized()) return null

        return try {
            val method = getCachedMethod(
                "getIntProperty",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            ) ?: return null
            val result = method.invoke(managerInstance, propertyId, areaId)
            result as? Int
        } catch (_: Exception) {
            null
        }
    }

    fun readFloatProperty(propertyId: Int, areaId: Int = 0): Float? {
        if (!ensureInitialized()) return null

        return try {
            val method = getCachedMethod(
                "getFloatProperty",
                Int::class.javaPrimitiveType!!,
                Int::class.javaPrimitiveType!!
            ) ?: return null
            val result = method.invoke(managerInstance, propertyId, areaId)
            result as? Float
        } catch (_: Exception) {
            null
        }
    }

    private fun ensureInitialized(): Boolean {
        if (carApiUnavailable) return false
        if (isInitialized && managerInstance != null) return true
        return initialize()
    }

    fun isCarApiAvailable(): Boolean {
        return !carApiUnavailable && isInitialized
    }

    fun getManagerInstance(): Any? {
        ensureInitialized()
        return managerInstance
    }

    fun getCarInstance(): Any? {
        ensureInitialized()
        return carInstance
    }

    @Synchronized
    fun release() {
        try {
            val car = carInstance
            val cls = carClass
            if (car != null && cls != null) {
                try {
                    val disconnectMethod = cls.getMethod("disconnect")
                    disconnectMethod.invoke(car)
                } catch (_: Exception) {
                }
            }
        } finally {
            carInstance = null
            managerInstance = null
            isInitialized = false
            methodCache.clear()
            carPropertyValueClass = null
            cpvGetPropertyIdMethod = null
            cpvGetValueMethod = null
        }
    }

    fun isReady(): Boolean = isInitialized && managerInstance != null

    fun registerPropertyCallback(
        propertyId: Int,
        callback: (Int, Any?) -> Unit,
        rate: Float = 0f
    ): Any? {
        if (!ensureInitialized()) return null

        return try {
            if (carPropertyValueClass == null) {
                carPropertyValueClass = Class.forName(CLASS_CAR_PROPERTY_VALUE)
                cpvGetPropertyIdMethod =
                    carPropertyValueClass?.getMethod("getPropertyId")
                cpvGetValueMethod =
                    carPropertyValueClass?.getMethod("getValue")
            }

            val callbackClass = Class.forName(
                "android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback"
            )
            val callbackInstance = java.lang.reflect.Proxy.newProxyInstance(
                callbackClass.classLoader,
                arrayOf(callbackClass)
            ) { _, method, args ->
                when (method.name) {
                    "onChangeEvent" -> {
                        val value = args?.getOrNull(0) ?: return@newProxyInstance null
                        val cls = carPropertyValueClass
                        val idMethod = cpvGetPropertyIdMethod
                        val valMethod = cpvGetValueMethod
                        if (cls != null && idMethod != null && valMethod != null &&
                            cls.isInstance(value)
                        ) {
                            try {
                                val propId = idMethod.invoke(value) as? Int ?: return@newProxyInstance null
                                val propValue = valMethod.invoke(value)
                                callback(propId, propValue)
                            } catch (_: Exception) {
                            }
                        }
                        null
                    }
                    "onErrorEvent" -> null
                    else -> null
                }
            }

            val registerMethod = getCachedMethod(
                "registerCallback",
                callbackClass,
                Int::class.javaPrimitiveType!!,
                Float::class.javaPrimitiveType!!
            ) ?: return null

            registerMethod.invoke(managerInstance, callbackInstance, propertyId, rate)
            callbackInstance
        } catch (_: Exception) {
            null
        }
    }

    fun unregisterPropertyCallback(callbackInstance: Any?) {
        if (callbackInstance == null || !ensureInitialized()) return

        try {
            val callbackClass = Class.forName(
                "android.car.hardware.property.CarPropertyManager\$CarPropertyEventCallback"
            )
            val unregisterMethod = getCachedMethod(
                "unregisterCallback",
                callbackClass
            ) ?: return

            unregisterMethod.invoke(managerInstance, callbackInstance)
        } catch (_: Exception) {
        }
    }
}