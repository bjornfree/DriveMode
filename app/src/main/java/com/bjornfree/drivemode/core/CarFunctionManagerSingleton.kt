package com.bjornfree.drivemode.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ecarx.xui.adaptapi.FunctionStatus
import com.ecarx.xui.adaptapi.binder.IConnectable
import com.ecarx.xui.adaptapi.car.Car
import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.base.ICarFunction

/**
 * Централизованный менеджер для ECARX ICarFunction.
 *
 * Возможности:
 * - одна Car/ICarFunction-инстанция на всё приложение;
 * - автопереподключение при onDisConnected;
 * - регистрация нескольких functionId и нескольких слушателей на каждый functionId;
 * - доставка колбэков на main-потоке;
 * - возможность получить текущее значение functionId.
 *
 * В будущем через него можно вешать свет, двери, ремни, AC-режимы, ADAS и т.д.
 */

/**
 * Пример использования
 *
 * val unsubscribeLights = CarFunctionManagerSingleton.registerFunctionListener(
 *     context,
 *     FUNCTION_LIGHTS
 * ) { value ->
 *     // обработка значения
 * }
 *
 * при уничтожении
 *
 * unsubscribeLights()
 */
object CarFunctionManagerSingleton {

    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var iCar: ICar? = null

    @Volatile
    private var iCarFunction: ICarFunction? = null

    private var connectWatcher: IConnectable.IConnectWatcher? = null

    @Volatile
    private var isConnected: Boolean = false

    @Volatile
    private var reconnectScheduled: Boolean = false

    /**
     * Одна запись для functionId:
     * - сам watcher AdaptAPI;
     * - множество слушателей, которым рассылаем value.
     */
    private data class FunctionWatch(
        val functionId: Int,
        val watcher: ICarFunction.IFunctionValueWatcher,
        val listeners: MutableSet<(Int) -> Unit>
    )

    private val functionWatches = mutableMapOf<Int, FunctionWatch>()

    /**
     * Обеспечить созданный Car/ICarFunction и зарегистрированный connectWatcher.
     */
    @Synchronized
    private fun ensureCarConnectedLocked(context: Context) {
        val currentCar = iCar
        if (currentCar != null && isConnected) {
            return
        }

        val appCtx = context.applicationContext
        appContext = appCtx

        val car = try {
            Car.create(appCtx)
        } catch (_: Throwable) {
            null
        } ?: return

        iCar = car

        if (car is IConnectable) {
            if (connectWatcher == null) {
                connectWatcher = object : IConnectable.IConnectWatcher {
                    override fun onConnected() {
                        iCarFunction = iCar?.iCarFunction
                        isConnected = true
                        // После переподключения пере-регистрируем все watcher'ы
                        reregisterAllFunctions()
                    }

                    override fun onDisConnected() {
                        isConnected = false
                        iCarFunction = null
                        scheduleReconnect()
                    }
                }
                car.registerConnectWatcher(connectWatcher)
            }

            try {
                car.connect()
            } catch (_: Throwable) {
            }
        }
    }

    private fun scheduleReconnect() {
        val ctx = appContext ?: return
        if (reconnectScheduled) return
        reconnectScheduled = true

        mainHandler.postDelayed({
            reconnectScheduled = false
            ensureCarConnectedLocked(ctx)
        }, 2000L)
    }

    /**
     * Зарегистрировать слушателя на конкретный functionId.
     *
     * Возвращает функцию-отписку, которую нужно вызвать при destroy.
     */
    fun registerFunctionListener(
        context: Context,
        functionId: Int,
        listener: (Int) -> Unit
    ): () -> Unit {
        synchronized(this) {
            ensureCarConnectedLocked(context)

            val existing = functionWatches[functionId]
            if (existing != null) {
                existing.listeners.add(listener)
                return {
                    unregisterListener(functionId, listener)
                }
            }

            // Создаём новый watcher для этого functionId
            val newListeners = mutableSetOf(listener)

            val watcher = object : ICarFunction.IFunctionValueWatcher {
                override fun onFunctionValueChanged(id: Int, zone: Int, value: Int) {
                    if (id != functionId) return

                    val snapshot: List<(Int) -> Unit> = synchronized(this@CarFunctionManagerSingleton) {
                        functionWatches[functionId]?.listeners?.toList().orEmpty()
                    }

                    if (snapshot.isEmpty()) return

                    snapshot.forEach { l ->
                        mainHandler.post {
                            l(value)
                        }
                    }
                }

                override fun onCustomizeFunctionValueChanged(
                    i: Int,
                    i1: Int,
                    v: Float
                ) {
                    // не используем
                }

                override fun onFunctionChanged(i: Int) {
                    // не используем
                }

                override fun onSupportedFunctionStatusChanged(
                    functionId: Int,
                    zone: Int,
                    status: FunctionStatus
                ) {
                    // не используем
                }

                override fun onSupportedFunctionValueChanged(
                    functionId: Int,
                    ints: IntArray?
                ) {
                    // не используем
                }
            }

            val watch = FunctionWatch(
                functionId = functionId,
                watcher = watcher,
                listeners = newListeners
            )
            functionWatches[functionId] = watch

            // Если уже есть iCarFunction – сразу регистрируем watcher
            val carFunction = iCarFunction
            if (carFunction != null) {
                try {
                    carFunction.registerFunctionValueWatcher(functionId, watcher)
                } catch (_: Throwable) {
                }
            }

            return {
                unregisterListener(functionId, listener)
            }
        }
    }

    @Synchronized
    private fun unregisterListener(
        functionId: Int,
        listener: (Int) -> Unit
    ) {
        val watch = functionWatches[functionId] ?: return
        watch.listeners.remove(listener)
        if (watch.listeners.isEmpty()) {
            // никого не осталось — снимаем watcher с ICarFunction
            try {
                iCarFunction?.unregisterFunctionValueWatcher(watch.watcher)
            } catch (_: Throwable) {
            }
            functionWatches.remove(functionId)
        }
    }

    /**
     * Повторно регистрирует все watcher'ы после переподключения ICarFunction.
     */
    @Synchronized
    private fun reregisterAllFunctions() {
        val carFunction = iCarFunction ?: return
        functionWatches.values.forEach { watch ->
            try {
                carFunction.registerFunctionValueWatcher(watch.functionId, watch.watcher)
            } catch (_: Throwable) {
            }
        }
    }

    /**
     * Прямое чтение текущего значения функции, если ICarFunction уже есть.
     */
    fun getCurrentValue(functionId: Int, zone: Int = 0): Int? {
        val carFunction = iCarFunction ?: return null
        return try {
            carFunction.getFunctionValue(functionId, zone)
        } catch (_: Throwable) {
            null
        }
    }

    /**
     * Полный сброс. Можно вызвать при завершении приложения.
     */
    @Synchronized
    fun release() {
        functionWatches.values.forEach { watch ->
            try {
                iCarFunction?.unregisterFunctionValueWatcher(watch.watcher)
            } catch (_: Throwable) {
            }
        }
        functionWatches.clear()

        val car = iCar
        if (car is IConnectable) {
            try {
                connectWatcher?.let { car.unregisterConnectWatcher() }
            } catch (_: Throwable) {
            }
        }

        connectWatcher = null
        iCarFunction = null
        iCar = null
        isConnected = false
        reconnectScheduled = false
        appContext = null
    }
}