package com.bjornfree.drivemode.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.bjornfree.drivemode.data.repository.VehicleMetricsRepository
import com.bjornfree.drivemode.domain.model.VehicleMetrics
import kotlinx.coroutines.flow.*

data class MainMetrics(
    val speed: Float,
    val rpm: Int,
    val gear: String
)

data class FuelMetrics(
    val fuel: com.bjornfree.drivemode.domain.model.FuelData?,
    val rangeRemaining: Float?,
    val averageFuel: Float?
)

data class TripMetrics(
    val odometer: Float?,
    val tripMileage: Float?,
    val tripTime: Int?
)

data class TireMetrics(
    val tirePressure: com.bjornfree.drivemode.domain.model.TirePressureData?
)

data class TemperatureMetrics(
    val cabinTemperature: Float?,
    val ambientTemperature: Float?,
    val engineOilTemp: Float?,
    val coolantTemp: Float?
)

class VehicleInfoViewModel(
    private val metricsRepo: VehicleMetricsRepository
) : ViewModel() {

    val vehicleMetrics: StateFlow<VehicleMetrics> = metricsRepo.vehicleMetrics
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            VehicleMetrics()
        )

    private val baseMetrics = vehicleMetrics

    val mainMetrics: StateFlow<MainMetrics> = baseMetrics
        .map { m ->
            MainMetrics(
                speed = m.speed,
                rpm = m.rpm,
                gear = m.gear ?: "P"
            )
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            MainMetrics(0f, 0, "P")
        )

    val fuelMetrics: StateFlow<FuelMetrics> = baseMetrics
        .map { m ->
            FuelMetrics(
                fuel = m.fuel,
                rangeRemaining = m.rangeRemaining,
                averageFuel = m.averageFuel
            )
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            FuelMetrics(null, null, null)
        )

    val tripMetrics: StateFlow<TripMetrics> = baseMetrics
        .map { m ->
            TripMetrics(
                odometer = m.odometer,
                tripMileage = m.tripMileage,
                tripTime = m.tripTime
            )
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TripMetrics(null, null, null)
        )

    val tireMetrics: StateFlow<TireMetrics> = baseMetrics
        .map { m ->
            TireMetrics(m.tirePressure)
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TireMetrics(null)
        )

    val temperatureMetrics: StateFlow<TemperatureMetrics> = baseMetrics
        .map { m ->
            TemperatureMetrics(
                cabinTemperature = m.cabinTemperature,
                ambientTemperature = m.ambientTemperature,
                engineOilTemp = m.engineOilTemp,
                coolantTemp = m.coolantTemp
            )
        }
        .distinctUntilChanged()
        .stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5000),
            TemperatureMetrics(null, null, null, null)
        )

    init {
        startMonitoring()
    }

    private fun startMonitoring() {
        metricsRepo.startMonitoring()
    }
}