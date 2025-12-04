package com.bjornfree.drivemode.di

import org.koin.dsl.module

/**
 * Главный Koin модуль приложения DriveMode.
 *
 * Содержит все определения зависимостей:
 * - Singletons (CarPropertyManagerSingleton, PreferencesManager)
 * - Repositories (VehicleMetrics, IgnitionState, HeatingControl, DriveMode)
 * - ViewModels (VehicleInfo, AutoHeating, Diagnostics, Console, Settings)
 *
 * TODO: Будет полностью заполнен в Фазе 6 после создания всех компонентов.
 */
val appModule = module {
    // Временная заглушка - будет заполнена в Фазе 6
    // После создания всех singletons, repositories и viewmodels
}
