package com.bjornfree.drivemode.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build

class RestartReceiver : BroadcastReceiver() {

    companion object {
        private const val PREFS_NAME = "drivemode_prefs"
        private const val KEY_AUTOSTART = "autostart_on_boot"
    }

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean(KEY_AUTOSTART, true)
        if (!autoStart) return

        val appContext = context.applicationContext
        val serviceIntent = Intent(appContext, DriveModeService::class.java)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            appContext.startForegroundService(serviceIntent)
        } else {
            appContext.startService(serviceIntent)
        }
    }
}