package com.bjornfree.drivemode.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class BootReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "drivemode_prefs"
        private const val KEY_AUTOSTART = "autostart_on_boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action ?: return

        if (
            action != Intent.ACTION_BOOT_COMPLETED &&
            action != Intent.ACTION_LOCKED_BOOT_COMPLETED &&
            action != Intent.ACTION_MY_PACKAGE_REPLACED
        ) {
            return
        }

        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(KEY_AUTOSTART, true)
        if (!autoStart) return

        // Единственная точка старта core-сервиса
        DriveModeService.restartService(context.applicationContext)
    }
}