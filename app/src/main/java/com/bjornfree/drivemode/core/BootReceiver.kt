package com.bjornfree.drivemode.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock

class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        // Можно дать настройку в префах: включен ли автозапуск
        val prefs = context.getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean("autostart_on_boot", true)
        if (!autoStart) return

        when (action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_LOCKED_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                // Небольшая задержка — даём системе поднять логи/радио
                Thread {
                    SystemClock.sleep(4000) // 4 сек; при желании вынести в константу/настройку
                    startServiceSafe(context)
                }.start()
            }
        }
    }

    private fun startServiceSafe(context: Context) {
        // Стартуем сервис визуализации режима
        val serviceIntent = Intent(context, DriveModeService::class.java)
        context.startForegroundService(serviceIntent)

        val serviceIntentHeat = Intent(context, AutoSeatHeatService::class.java)
        context.startForegroundService(serviceIntentHeat)
    }
}