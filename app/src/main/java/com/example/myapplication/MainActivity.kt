package com.example.myapplication

import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var vehicleDataCollector: VehicleDataCollector

    private lateinit var tvHealthScore: TextView
    private lateinit var tvAlertBanner: TextView
    private lateinit var tvEngineTemp: TextView
    private lateinit var tvBatteryVoltage: TextView
    private lateinit var tvOilLevel: TextView
    private lateinit var tvMileage: TextView

    private val simulationHandler = Handler(Looper.getMainLooper())
    private var simulatedTemp = 90.0f
    private var isHeatingUp = true

    private val liveSimulationRunnable = object : Runnable {
        override fun run() {
            // Smoothly simulate real-world driving load (engine warming up and cooling down)
            if (isHeatingUp) {
                simulatedTemp += 1.5f
                if (simulatedTemp >= 118.0f) {
                    isHeatingUp = false
                }
            } else {
                simulatedTemp -= 1.5f
                if (simulatedTemp <= 90.0f) {
                    isHeatingUp = true
                }
            }

            // Simulate corresponding battery & oil fluctuations under heavy engine load
            val simulatedBattery = if (simulatedTemp > 110f) 11.1f else 12.6f
            val simulatedOil = if (simulatedTemp > 110f) 0.25f else 1.0f

            vehicleDataCollector.simulateTelemetry(
                engineTemp = simulatedTemp,
                batteryVoltage = simulatedBattery,
                oilLevel = simulatedOil,
                mileage = 45200.0f,
            )

            simulationHandler.postDelayed(this, 1500) // Stream updates every 1.5 seconds
        }
    }

    @Suppress("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        tvHealthScore = findViewById(R.id.tvHealthScore)
        tvAlertBanner = findViewById(R.id.tvAlertBanner)
        tvEngineTemp = findViewById(R.id.tvEngineTemp)
        tvBatteryVoltage = findViewById(R.id.tvBatteryVoltage)
        tvOilLevel = findViewById(R.id.tvOilLevel)
        tvMileage = findViewById(R.id.tvMileage)

        vehicleDataCollector = VehicleDataCollector(this) { engineTemp, batteryVoltage, oilLevel, mileage, healthScore, alertMessage ->
            runOnUiThread {
                val percentage = (healthScore * 100).toInt()
                tvHealthScore.text = String.format(Locale.getDefault(), "Health Score: %d%%", percentage)

                if (healthScore < 0.30f) {
                    tvHealthScore.setTextColor(Color.rgb(244, 67, 54)) // Red
                    tvAlertBanner.setBackgroundColor(Color.rgb(211, 47, 47)) // Dark Red
                    tvAlertBanner.text = alertMessage ?: getString(R.string.maintenance_required)
                } else {
                    tvHealthScore.setTextColor(Color.rgb(76, 175, 80)) // Green
                    tvAlertBanner.setBackgroundColor(Color.rgb(46, 125, 50)) // Dark Green
                    tvAlertBanner.text = getString(R.string.status_normal)
                }

                tvEngineTemp.text = String.format(Locale.getDefault(), "Engine Coolant Temp: %.1f °C", engineTemp)
                tvBatteryVoltage.text = String.format(Locale.getDefault(), "Battery Voltage: %.1f V", batteryVoltage)
                tvOilLevel.text = String.format(Locale.getDefault(), "Oil Level: Optimal (%.1f)", oilLevel)
                tvMileage.text = String.format(Locale.getDefault(), "Total Mileage: %,.0f km", mileage)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vehicleDataCollector.registerVehicleListeners()
        simulationHandler.post(liveSimulationRunnable)
    }

    override fun onPause() {
        super.onPause()
        vehicleDataCollector.unregisterVehicleListeners()
        simulationHandler.removeCallbacks(liveSimulationRunnable)
    }
}
