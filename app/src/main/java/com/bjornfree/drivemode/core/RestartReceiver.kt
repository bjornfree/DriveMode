package com.bjornfree.drivemode.core

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

class RestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val prefs = context.getSharedPreferences("drivemode_prefs", Context.MODE_PRIVATE)
        val autoStart = prefs.getBoolean("autostart_on_boot", true)

        if (!autoStart) return

        try {
            val serviceIntent = Intent(context, DriveModeService::class.java)
            context.startForegroundService(serviceIntent)
        } catch (_: Exception) {
        }
    }
}