package com.example.myapplication

import android.Manifest
import android.animation.ArgbEvaluator
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.cardview.widget.CardView
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var vehicleDataCollector: VehicleDataCollector
    private lateinit var notificationHelper: NotificationHelper
    private lateinit var bluetoothAlertServer: BluetoothAlertServer
    private lateinit var networkAlertServer: NetworkAlertServer

    private lateinit var cardHealthContainer: CardView
    private lateinit var tvHealthScore: TextView
    private lateinit var tvAlertBanner: TextView
    private lateinit var tvEngineTemp: TextView
    private lateinit var tvBatteryVoltage: TextView
    private lateinit var tvOilLevel: TextView
    private lateinit var tvFrontLeft: TextView
    private lateinit var tvFrontRight: TextView
    private lateinit var tvRearLeft: TextView
    private lateinit var tvRearRight: TextView
    private lateinit var tvBrakePads: TextView
    private lateinit var tvTransmissionTemp: TextView
    private lateinit var tvMileage: TextView

    private val simulationHandler = Handler(Looper.getMainLooper())
    private var simulationStep = 0
    private var simulatedMileage = 45200.0f
    private var lastAlertSentTime = 0L
    private var lastWasWarning = false
    private var pulseAnimator: ObjectAnimator? = null

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _: Map<String, Boolean> -> }

    private val liveSimulationRunnable = object : Runnable {
        override fun run() {
            // Accumulate odometer mileage steadily upwards as the car drives
            simulatedMileage += 0.5f

            when (simulationStep % 6) {
                0 -> {
                    // Normal State
                    vehicleDataCollector.simulateTelemetry(90.0f, 12.6f, 1.0f, simulatedMileage, 32.0f, 32.0f, 32.0f, 32.0f, 85.0f, 88.0f)
                }
                1 -> {
                    // Engine Overheat
                    vehicleDataCollector.simulateTelemetry(118.5f, 12.5f, 0.9f, simulatedMileage, 32.0f, 32.0f, 32.0f, 32.0f, 80.0f, 95.0f)
                }
                2 -> {
                    // Low Battery
                    vehicleDataCollector.simulateTelemetry(92.0f, 10.8f, 1.0f, simulatedMileage, 31.0f, 31.0f, 31.0f, 31.0f, 75.0f, 90.0f)
                }
                3 -> {
                    // Low Oil Level
                    vehicleDataCollector.simulateTelemetry(95.0f, 12.4f, 0.2f, simulatedMileage, 30.0f, 30.0f, 30.0f, 30.0f, 70.0f, 92.0f)
                }
                4 -> {
                    // Low Tire Pressure (Front-Left Puncture)
                    vehicleDataCollector.simulateTelemetry(91.0f, 12.5f, 1.0f, simulatedMileage, 21.0f, 32.0f, 32.0f, 32.0f, 65.0f, 89.0f)
                }
                5 -> {
                    // Worn Brake Pads
                    vehicleDataCollector.simulateTelemetry(93.0f, 12.6f, 1.0f, simulatedMileage, 32.0f, 32.0f, 32.0f, 32.0f, 10.0f, 94.0f)
                }
            }
            simulationStep++
            simulationHandler.postDelayed(this, 6000)
        }
    }

    @Suppress("SetTextI18n")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        notificationHelper = NotificationHelper(this)
        bluetoothAlertServer = BluetoothAlertServer(this)
        networkAlertServer = NetworkAlertServer()

        val permissions = arrayOf(
            Manifest.permission.POST_NOTIFICATIONS,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.BLUETOOTH_SCAN,
        )
        if (permissions.any { ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED }) {
            requestPermissionsLauncher.launch(permissions)
        }

        cardHealthContainer = findViewById(R.id.cardHealthContainer)
        tvHealthScore = findViewById(R.id.tvHealthScore)
        tvAlertBanner = findViewById(R.id.tvAlertBanner)
        tvEngineTemp = findViewById(R.id.tvEngineTemp)
        tvBatteryVoltage = findViewById(R.id.tvBatteryVoltage)
        tvOilLevel = findViewById(R.id.tvOilLevel)
        tvFrontLeft = findViewById(R.id.tvFrontLeft)
        tvFrontRight = findViewById(R.id.tvFrontRight)
        tvRearLeft = findViewById(R.id.tvRearLeft)
        tvRearRight = findViewById(R.id.tvRearRight)
        tvBrakePads = findViewById(R.id.tvBrakePads)
        tvTransmissionTemp = findViewById(R.id.tvTransmissionTemp)
        tvMileage = findViewById(R.id.tvMileage)

        vehicleDataCollector = VehicleDataCollector(this) { engineTemp, batteryVoltage, oilLevel, mileage, fl, fr, rl, rr, brakePadWear, transmissionTemp, healthScore, alertMessage ->
            runOnUiThread {
                val percentage = (healthScore * 100).toInt()
                tvHealthScore.text = "$percentage%"

                val isWarning = healthScore < 0.30f
                if (isWarning != lastWasWarning) {
                    lastWasWarning = isWarning
                    animateHealthTransition(isWarning)
                    if (isWarning) startPulseAnimation() else stopPulseAnimation()
                }

                if (isWarning) {
                    tvHealthScore.setTextColor(Color.rgb(239, 68, 68)) // Red
                    tvAlertBanner.setBackgroundColor(Color.rgb(127, 29, 29)) // Dark Red
                    tvAlertBanner.text = alertMessage ?: getString(R.string.maintenance_required)

                    val currentTime = System.currentTimeMillis()
                    if ((currentTime - lastAlertSentTime) > 10000L) {
                        lastAlertSentTime = currentTime
                        val message = alertMessage ?: "Maintenance Required!"
                        notificationHelper.showMaintenanceAlertNotification("Critical Vehicle Warning", message)
                        bluetoothAlertServer.sendAlertToPhone(message)
                        networkAlertServer.sendAlertToPhone(message)
                    }
                } else {
                    tvHealthScore.setTextColor(Color.rgb(16, 185, 129)) // Emerald Green
                    tvAlertBanner.setBackgroundColor(Color.rgb(6, 78, 59)) // Dark Green
                    tvAlertBanner.text = "All car parts & systems operating at peak performance."
                }

                tvEngineTemp.text = String.format(Locale.getDefault(), "%.1f °C", engineTemp)
                tvBatteryVoltage.text = String.format(Locale.getDefault(), "%.1f V", batteryVoltage)
                tvOilLevel.text = if (oilLevel < 0.3f) "Low ($oilLevel)" else "Optimal ($oilLevel)"

                tvFrontLeft.text = if (fl < 25f) "Front-Left: $fl PSI (Low)" else "Front-Left: $fl PSI"
                tvFrontRight.text = if (fr < 25f) "Front-Right: $fr PSI (Low)" else "Front-Right: $fr PSI"
                tvRearLeft.text = if (rl < 25f) "Rear-Left: $rl PSI (Low)" else "Rear-Left: $rl PSI"
                tvRearRight.text = if (rr < 25f) "Rear-Right: $rr PSI (Low)" else "Rear-Right: $rr PSI"

                tvBrakePads.text = if (brakePadWear < 15f) "Critical (${brakePadWear.toInt()}%)" else "${brakePadWear.toInt()}% Remaining"
                tvTransmissionTemp.text = String.format(Locale.getDefault(), "%.1f °C", transmissionTemp)
                tvMileage.text = String.format(Locale.getDefault(), "%,.0f km", mileage)
            }
        }
    }

    private fun animateHealthTransition(isWarning: Boolean) {
        val startColor = if (isWarning) Color.rgb(19, 28, 46) else Color.rgb(69, 10, 10)
        val endColor = if (isWarning) Color.rgb(69, 10, 10) else Color.rgb(19, 28, 46)

        val colorAnimator = ValueAnimator.ofObject(ArgbEvaluator(), startColor, endColor)
        colorAnimator.duration = 600
        colorAnimator.addUpdateListener { animator ->
            cardHealthContainer.setCardBackgroundColor(animator.animatedValue as Int)
        }
        colorAnimator.start()
    }

    private fun startPulseAnimation() {
        if (pulseAnimator == null) {
            pulseAnimator = ObjectAnimator.ofFloat(cardHealthContainer, View.ALPHA, 1.0f, 0.7f, 1.0f).apply {
                duration = 1000
                repeatCount = ObjectAnimator.INFINITE
            }
        }
        pulseAnimator?.start()
    }

    private fun stopPulseAnimation() {
        pulseAnimator?.cancel()
        cardHealthContainer.alpha = 1.0f
    }

    override fun onResume() {
        super.onResume()
        vehicleDataCollector.registerVehicleListeners()
        bluetoothAlertServer.startServer()
        networkAlertServer.startServer()
        simulationHandler.post(liveSimulationRunnable)
    }

    override fun onPause() {
        super.onPause()
        vehicleDataCollector.unregisterVehicleListeners()
        bluetoothAlertServer.stopServer()
        networkAlertServer.stopServer()
        simulationHandler.removeCallbacks(liveSimulationRunnable)
    }
}
