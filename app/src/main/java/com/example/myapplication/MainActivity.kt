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
import com.google.android.material.button.MaterialButton
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
    private lateinit var tvSpeed: TextView

    private lateinit var btnEngineToggle: MaterialButton
    private lateinit var btnGearP: MaterialButton
    private lateinit var btnGearR: MaterialButton
    private lateinit var btnGearN: MaterialButton
    private lateinit var btnGearD: MaterialButton
    private lateinit var btnDrive: MaterialButton
    private lateinit var btnWearBrakes: MaterialButton
    private lateinit var btnPuncture: MaterialButton
    private lateinit var btnReset: MaterialButton

    private var simulatedMileage = 0.0f
    private var currentSpeed = 0.0f
    private var simulatedTemp = 90.0f
    private var simulatedOil = 1.0f
    private var isFlPunctured = false
    private var simulatedBrakes = 100.0f
    private var simulatedTransTemp = 88.0f

    private var isEngineOn = true
    private var currentGear = "P"

    private var lastAlertSentTime = 0L
    private var lastWasWarning = false
    private var pulseAnimator: ObjectAnimator? = null

    private val physicsHandler = Handler(Looper.getMainLooper())
    private val physicsRunnable = object : Runnable {
        override fun run() {
            // Real-time Physics Loop running every 500ms
            if (currentSpeed > 0.0f) {
                // Mileage accumulates dynamically whenever vehicle speed > 0
                simulatedMileage += (currentSpeed / 3600.0f) * 0.5f

                // Engine cooling towards 90°C as car slows down or coasts
                if (simulatedTemp > 90.0f) {
                    simulatedTemp = maxOf(90.0f, simulatedTemp - 0.5f)
                }
                if (simulatedTransTemp > 88.0f) {
                    simulatedTransTemp = maxOf(88.0f, simulatedTransTemp - 0.4f)
                }

                // Natural coasting speed reduction if not pressing gas
                currentSpeed = maxOf(0.0f, currentSpeed - 0.8f)
            } else {
                // Engine cooling when stationary/idling
                if (simulatedTemp > 90.0f) {
                    simulatedTemp = maxOf(90.0f, simulatedTemp - 1.0f)
                }
                if (simulatedTransTemp > 88.0f) {
                    simulatedTransTemp = maxOf(88.0f, simulatedTransTemp - 0.8f)
                }
            }

            updateTelemetry()
            physicsHandler.postDelayed(this, 500)
        }
    }

    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { _: Map<String, Boolean> -> }

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
        tvSpeed = findViewById(R.id.tvSpeed)

        btnEngineToggle = findViewById(R.id.btnEngineToggle)
        btnGearP = findViewById(R.id.btnGearP)
        btnGearR = findViewById(R.id.btnGearR)
        btnGearN = findViewById(R.id.btnGearN)
        btnGearD = findViewById(R.id.btnGearD)
        btnDrive = findViewById(R.id.btnDrive)
        btnWearBrakes = findViewById(R.id.btnWearBrakes)
        btnPuncture = findViewById(R.id.btnPuncture)
        btnReset = findViewById(R.id.btnReset)

        vehicleDataCollector = VehicleDataCollector(this) { engineTemp, _, oilLevel, mileage, fl, fr, rl, rr, brakePadWear, transmissionTemp, healthScore, alertMessage ->
            runOnUiThread {
                val batteryPercent = maxOf(0.0f, 100.0f - ((mileage / 450.0f) * 100.0f))

                // Check for low battery warning (< 15%)
                val effectiveHealth = if (batteryPercent < 15.0f) 0.20f else healthScore
                val effectiveAlert = if (batteryPercent < 15.0f) "Maintenance Alert: EV Battery Low (${batteryPercent.toInt()}%)! Recharge required." else alertMessage

                val percentage = (effectiveHealth * 100).toInt()
                tvHealthScore.text = "$percentage%"

                val isWarning = effectiveHealth < 0.30f
                if (isWarning != lastWasWarning) {
                    lastWasWarning = isWarning
                    animateHealthTransition(isWarning)
                    if (isWarning) startPulseAnimation() else stopPulseAnimation()
                }

                if (isWarning) {
                    tvHealthScore.setTextColor(Color.rgb(239, 68, 68)) // Red
                    tvAlertBanner.setBackgroundColor(Color.rgb(127, 29, 29)) // Dark Red
                    tvAlertBanner.text = effectiveAlert ?: getString(R.string.maintenance_required)

                    val currentTime = System.currentTimeMillis()
                    if ((currentTime - lastAlertSentTime) > 10000L) {
                        lastAlertSentTime = currentTime
                        val message = effectiveAlert ?: "Maintenance Required!"
                        notificationHelper.showMaintenanceAlertNotification("Critical Vehicle Warning", message)
                        bluetoothAlertServer.sendAlertToPhone(message)
                        networkAlertServer.sendAlertToPhone(message)
                    }
                } else {
                    tvHealthScore.setTextColor(Color.rgb(16, 185, 129)) // Emerald Green
                    tvAlertBanner.setBackgroundColor(Color.rgb(6, 78, 59)) // Dark Green
                    tvAlertBanner.text = "All car parts & systems operating at peak performance."
                }

                tvEngineTemp.text = if (isEngineOn) String.format(Locale.getDefault(), "%.1f °C", engineTemp) else "OFF (Ambient)"
                tvBatteryVoltage.text = String.format(Locale.getDefault(), "%d%% (%.0f km left)", batteryPercent.toInt(), maxOf(0.0f, 450.0f - mileage))
                tvOilLevel.text = if (oilLevel < 0.3f) "Low ($oilLevel)" else "Optimal ($oilLevel)"

                tvFrontLeft.text = if (fl < 25f) String.format(Locale.getDefault(), "Front-Left: %.1f PSI (Low)", fl) else String.format(Locale.getDefault(), "Front-Left: %.1f PSI", fl)
                tvFrontRight.text = String.format(Locale.getDefault(), "Front-Right: %.1f PSI", fr)
                tvRearLeft.text = String.format(Locale.getDefault(), "Rear-Left: %.1f PSI", rl)
                tvRearRight.text = String.format(Locale.getDefault(), "Rear-Right: %.1f PSI", rr)

                tvBrakePads.text = if (brakePadWear < 15f) "Critical (${brakePadWear.toInt()}%)" else "${brakePadWear.toInt()}% Remaining"
                tvTransmissionTemp.text = if (isEngineOn) String.format(Locale.getDefault(), "%.1f °C", transmissionTemp) else "OFF"
                tvMileage.text = String.format(Locale.getDefault(), "%.2f km", mileage)
                tvSpeed.text = String.format(Locale.getDefault(), "%.0f km/h", currentSpeed)
            }
        }

        btnEngineToggle.setOnClickListener {
            isEngineOn = !isEngineOn
            if (isEngineOn) {
                btnEngineToggle.text = "Engine: ON"
                simulatedTemp = 90.0f
            } else {
                btnEngineToggle.text = "Engine: OFF"
                simulatedTemp = 25.0f
                currentSpeed = 0.0f
            }
            updateTelemetry()
        }

        btnGearP.setOnClickListener {
            currentGear = "P"
            highlightGear("P")
            currentSpeed = 0.0f
            updateTelemetry()
        }

        btnGearR.setOnClickListener {
            currentGear = "R"
            highlightGear("R")
            updateTelemetry()
        }

        btnGearN.setOnClickListener {
            currentGear = "N"
            highlightGear("N")
            updateTelemetry()
        }

        btnGearD.setOnClickListener {
            currentGear = "D"
            highlightGear("D")
            updateTelemetry()
        }

        btnDrive.setOnClickListener {
            if ((isEngineOn) && (currentGear == "D" || currentGear == "R")) {
                val maxSpeed = if (currentGear == "R") 35.0f else 140.0f
                currentSpeed = minOf(maxSpeed, currentSpeed + 8.0f)
                simulatedTemp = minOf(125.0f, simulatedTemp + 2.0f)
                simulatedTransTemp = minOf(115.0f, simulatedTransTemp + 1.5f)
                updateTelemetry()
            }
        }

        btnWearBrakes.setOnClickListener {
            currentSpeed = maxOf(0.0f, currentSpeed - 20.0f)
            simulatedBrakes = maxOf(0.0f, simulatedBrakes - 2.0f) // Gradual brake wear from 100%
            updateTelemetry()
        }

        btnPuncture.setOnClickListener {
            isFlPunctured = true
            updateTelemetry()
        }

        btnReset.setOnClickListener {
            simulatedMileage = 0.0f
            currentSpeed = 0.0f
            simulatedTemp = 90.0f
            simulatedOil = 1.0f
            isFlPunctured = false
            simulatedBrakes = 100.0f
            simulatedTransTemp = 88.0f
            isEngineOn = true
            currentGear = "P"
            btnEngineToggle.text = "Engine: ON"
            highlightGear("P")
            updateTelemetry()
        }

        // Initial state
        updateTelemetry()
    }

    private fun highlightGear(gear: String) {
        val defaultColor = Color.rgb(71, 85, 105)
        val activeColor = Color.rgb(37, 99, 235)
        btnGearP.setBackgroundColor(if (gear == "P") activeColor else defaultColor)
        btnGearR.setBackgroundColor(if (gear == "R") activeColor else defaultColor)
        btnGearN.setBackgroundColor(if (gear == "N") activeColor else defaultColor)
        btnGearD.setBackgroundColor(if (gear == "D") activeColor else defaultColor)
    }

    private fun updateTelemetry() {
        val effectiveTemp = if (isEngineOn) simulatedTemp else 25.0f
        val effectiveTrans = if (isEngineOn) simulatedTransTemp else 25.0f

        // Physics-based tire pressure warmup as speed increases
        val thermalPressureOffset = minOf(2.5f, (currentSpeed / 100.0f) * 1.5f)
        val flPressure = if (isFlPunctured) 20.0f else (32.0f + thermalPressureOffset)
        val frPressure = 32.0f + thermalPressureOffset
        val rlPressure = 32.0f + thermalPressureOffset
        val rrPressure = 32.0f + thermalPressureOffset

        vehicleDataCollector.simulateTelemetry(
            effectiveTemp,
            12.6f,
            simulatedOil,
            simulatedMileage,
            flPressure,
            frPressure,
            rlPressure,
            rrPressure,
            simulatedBrakes,
            effectiveTrans,
        )
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
        physicsHandler.post(physicsRunnable)
    }

    override fun onPause() {
        super.onPause()
        vehicleDataCollector.unregisterVehicleListeners()
        bluetoothAlertServer.stopServer()
        networkAlertServer.stopServer()
        physicsHandler.removeCallbacks(physicsRunnable)
    }
}
