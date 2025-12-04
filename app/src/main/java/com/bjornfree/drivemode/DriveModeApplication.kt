package com.bjornfree.drivemode

import android.app.Application
import android.util.Log
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import org.koin.core.logger.Level
import com.bjornfree.drivemode.di.appModule

/**
 * Application класс для инициализации Koin DI.
 *
 * Инициализирует Koin при старте приложения и предоставляет
 * dependency injection для всех компонентов приложения.
 */
class DriveModeApplication : Application() {

    override fun onCreate() {
        super.onCreate()

        Log.i(TAG, "Initializing DriveMode Application...")

        try {
            startKoin {
                // Логирование Koin (только в debug режиме)
                androidLogger(Level.ERROR)

                // Android контекст для Koin
                androidContext(this@DriveModeApplication)

                // Модули с зависимостями
                modules(appModule)
            }

            Log.i(TAG, "Koin DI initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize Koin DI", e)
        }
    }

    companion object {
        private const val TAG = "DriveModeApplication"
    }
}
