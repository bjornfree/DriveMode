package com.bjornfree.drivemode.core

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.ecarx.xui.adaptapi.FunctionStatus
import com.ecarx.xui.adaptapi.binder.IConnectable
import com.ecarx.xui.adaptapi.car.Car
import com.ecarx.xui.adaptapi.car.ICar
import com.ecarx.xui.adaptapi.car.base.ICarFunction

interface DriveModeListener {
    fun onDriveModeChanged(mode: String)
}

class DriveModeManager(
    private val context: Context,
    private val listener: DriveModeListener
) {

    companion object {
        private const val FUNCTION_DRIVE_MODE = 570491136

        private const val MODE_ECO = 570491137
        private const val MODE_COMFORT = 570491138
        private const val MODE_SPORT = 570491139
        private const val MODE_ADAPTIVE = 570491201

        private const val RECONNECT_DELAY_MS = 2000L
    }

    private val mainHandler = Handler(Looper.getMainLooper())

    private var iCar: ICar? = null
    private var iCarFunction: ICarFunction? = null
    private var functionValueWatcher: ICarFunction.IFunctionValueWatcher? = null
    private var connectWatcher: IConnectable.IConnectWatcher? = null

    private var reconnectPending = false

    fun connect() {
        try {
            val connectable = Car.create(context.applicationContext)
            iCar = connectable

            if (connectable is IConnectable) {
                connectWatcher = object : IConnectable.IConnectWatcher {
                    override fun onConnected() {
                        reconnectPending = false
                        iCarFunction = iCar?.iCarFunction
                        if (iCarFunction != null) {
                            registerListener()
                        } else {
                            scheduleReconnect()
                        }
                    }

                    override fun onDisConnected() {
                        iCarFunction = null
                        functionValueWatcher = null
                        scheduleReconnect()
                    }
                }

                connectable.registerConnectWatcher(connectWatcher)
                connectable.connect()
            }
        } catch (_: Throwable) {
            scheduleReconnect()
        }
    }

    private fun scheduleReconnect() {
        if (reconnectPending) return
        reconnectPending = true
        mainHandler.postDelayed({ reconnect() }, RECONNECT_DELAY_MS)
    }

    private fun reconnect() {
        reconnectPending = false
        try {
            val car = iCar
            if (car is IConnectable) {
                car.connect()
            } else {
                connect()
            }
        } catch (_: Exception) {
            scheduleReconnect()
        }
    }

    fun disconnect() {
        try {
            val carFunction = iCarFunction
            val watcher = functionValueWatcher
            if (carFunction != null && watcher != null) {
                try {
                    carFunction.unregisterFunctionValueWatcher(watcher)
                } catch (_: Exception) {
                }
            }

            val car = iCar
            val cw = connectWatcher
            if (car is IConnectable && cw != null) {
                try {
                    car.unregisterConnectWatcher()
                } catch (_: Exception) {
                }
            }

            if (car is IConnectable) {
                try {
                    car.disconnect()
                } catch (_: Exception) {
                }
            }
        } catch (_: Throwable) {
        } finally {
            functionValueWatcher = null
            iCarFunction = null
            connectWatcher = null
            iCar = null
            reconnectPending = false
            mainHandler.removeCallbacksAndMessages(null)
        }
    }

    private fun registerListener() {
        val carFunction = iCarFunction ?: return

        try {
            functionValueWatcher = object : ICarFunction.IFunctionValueWatcher {

                override fun onFunctionValueChanged(functionId: Int, zone: Int, value: Int) {
                    if (functionId == FUNCTION_DRIVE_MODE) {
                        val mode = mapValueToMode(value)
                        listener.onDriveModeChanged(mode)
                    }
                }

                override fun onCustomizeFunctionValueChanged(i: Int, i1: Int, v: Float) {
                }

                override fun onFunctionChanged(i: Int) {
                }

                override fun onSupportedFunctionStatusChanged(
                    functionId: Int,
                    zone: Int,
                    status: FunctionStatus
                ) {
                }

                override fun onSupportedFunctionValueChanged(functionId: Int, ints: IntArray?) {
                }
            }

            carFunction.registerFunctionValueWatcher(FUNCTION_DRIVE_MODE, functionValueWatcher)

            try {
                val currentValue = carFunction.getFunctionValue(FUNCTION_DRIVE_MODE, 0)
                listener.onDriveModeChanged(mapValueToMode(currentValue))
            } catch (_: Exception) {
            }
        } catch (_: Throwable) {
            scheduleReconnect()
        }
    }

    private fun mapValueToMode(value: Int): String =
        when (value) {
            MODE_ECO      -> "ECO"
            MODE_COMFORT  -> "COMFORT"
            MODE_SPORT    -> "SPORT"
            MODE_ADAPTIVE -> "ADAPTIVE"
            else          -> "UNKNOWN"
        }
}