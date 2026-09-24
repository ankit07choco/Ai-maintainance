package com.example.myapplication

import android.car.Car
import android.car.VehiclePropertyIds
import android.car.hardware.CarPropertyValue
import android.car.hardware.property.CarPropertyManager
import android.content.Context
import java.io.FileInputStream
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

@Suppress("unused", "SameParameterValue")
class VehicleDataCollector(
    context: Context,
    private val onTelemetryUpdated: (
        Float,
        Float,
        Float,
        Float,
        Float,
        String?,
    ) -> Unit = { _, _, _, _, _, _ -> },
) {

    private var car: Car? = null
    private var carPropertyManager: CarPropertyManager? = null
    private var maintenancePredictor: MaintenancePredictor? = null

    // Store latest sensor readings dynamically
    private var currentEngineTemp: Float = 90.0f
    private var currentBatteryVoltage: Float = 12.6f
    private var currentOilLevel: Float = 1.0f
    private var currentMileage: Float = 45200.0f

    private val propertyCallback = object : CarPropertyManager.CarPropertyEventCallback {
        override fun onChangeEvent(value: CarPropertyValue<*>) {
            when (value.propertyId) {
                VehiclePropertyIds.ENGINE_COOLANT_TEMP -> {
                    currentEngineTemp = (value.value as? Float) ?: currentEngineTemp
                }
                VehiclePropertyIds.FUEL_LEVEL -> {
                    val fuel = (value.value as? Float) ?: return
                    currentBatteryVoltage = if (fuel < 15.0f) 10.5f else 12.6f
                }
                VehiclePropertyIds.EV_BATTERY_LEVEL -> {
                    val level = (value.value as? Float) ?: return
                    currentBatteryVoltage = if (level < 15.0f) 10.5f else 12.6f
                }
                VehiclePropertyIds.ENGINE_OIL_LEVEL -> {
                    currentOilLevel = (value.value as? Float) ?: currentOilLevel
                }
            }
            runInferenceAndCheck()
        }

        override fun onErrorEvent(propId: Int, zone: Int) {
            // Handle sensor read error
        }
    }

    init {
        // Connect to Android Car Service
        try {
            car = Car.createCar(context)
            carPropertyManager = car?.getCarManager(Car.PROPERTY_SERVICE) as? CarPropertyManager
        } catch (e: Throwable) {
            // Ignored if car service is unavailable
        }

        // Load TFLite Model
        try {
            val modelBuffer = loadModelFile(context, "vehicle_maintenance.tflite")
            maintenancePredictor = MaintenancePredictor(modelBuffer)
        } catch (e: Throwable) {
            // Model file missing or invalid; running without ML predictor fallback
        }

        // Initial notification
        runInferenceAndCheck()
    }

    fun registerVehicleListeners() {
        val propertyIds = intArrayOf(
            VehiclePropertyIds.ENGINE_COOLANT_TEMP,
            VehiclePropertyIds.FUEL_LEVEL,
            VehiclePropertyIds.EV_BATTERY_LEVEL,
            VehiclePropertyIds.ENGINE_OIL_LEVEL,
        )
        for (propId in propertyIds) {
            try {
                val method = carPropertyManager?.javaClass?.getMethod(
                    "subscribePropertyEvents",
                    Int::class.java,
                    Float::class.java,
                    CarPropertyManager.CarPropertyEventCallback::class.java,
                )
                method?.invoke(
                    carPropertyManager,
                    propId,
                    CarPropertyManager.SENSOR_RATE_NORMAL,
                    propertyCallback,
                )
            } catch (e: Throwable) {
                @Suppress("DEPRECATION")
                carPropertyManager?.registerCallback(
                    propertyCallback,
                    propId,
                    CarPropertyManager.SENSOR_RATE_NORMAL,
                )
            }
        }
    }

    fun unregisterVehicleListeners() {
        try {
            val method = carPropertyManager?.javaClass?.getMethod(
                "unsubscribePropertyEvents",
                CarPropertyManager.CarPropertyEventCallback::class.java,
            )
            method?.invoke(carPropertyManager, propertyCallback)
        } catch (e: Throwable) {
            @Suppress("DEPRECATION")
            carPropertyManager?.unregisterCallback(propertyCallback)
        }
        car?.disconnect()
    }

    fun simulateTelemetry(engineTemp: Float, batteryVoltage: Float, oilLevel: Float, mileage: Float) {
        currentEngineTemp = engineTemp
        currentBatteryVoltage = batteryVoltage
        currentOilLevel = oilLevel
        currentMileage = mileage
        runInferenceAndCheck()
    }

    private fun runInferenceAndCheck() {
        // Construct dynamic feature vector: [Engine Temp, Battery Voltage, Oil Level, Mileage]
        val inputFeatures = floatArrayOf(
            currentEngineTemp,
            currentBatteryVoltage,
            currentOilLevel,
            currentMileage,
        )

        // Calculate health score using ML model, with fallback rules for specific failing parts
        val baseScore = maintenancePredictor?.predictHealth(inputFeatures) ?: 0.95f

        val healthScore: Float
        val alertMessage: String?

        when {
            currentEngineTemp > 110.0f -> {
                healthScore = 0.15f
                alertMessage = "Maintenance Alert: Engine Overheating Detected ($currentEngineTemp°C)! Coolant system inspection required."
            }
            currentBatteryVoltage < 11.5f -> {
                healthScore = 0.20f
                alertMessage = "Maintenance Alert: Low Battery/Fuel Voltage (${currentBatteryVoltage}V)! Service inspection needed."
            }
            currentOilLevel < 0.3f -> {
                healthScore = 0.18f
                alertMessage = "Maintenance Alert: Low Oil Level ($currentOilLevel)! Engine oil change recommended immediately."
            }
            else -> {
                healthScore = baseScore
                alertMessage = null
            }
        }

        onTelemetryUpdated(currentEngineTemp, currentBatteryVoltage, currentOilLevel, currentMileage, healthScore, alertMessage)
    }

    private fun showMaintenanceAlert(title: String, message: String) {
        println("ALERT: $title - $message")
    }

    // Loads .tflite file from assets directory into memory
    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }
}
