package com.example.myapplication

import org.tensorflow.lite.Interpreter
import java.nio.MappedByteBuffer

class MaintenancePredictor(modelBuffer: MappedByteBuffer) {

    private val tflite = Interpreter(modelBuffer)

    /**
     * Predicts health percentage / failure probability.
     * Inputs: [Engine Temp, Battery Voltage, Oil Level, Mileage]
     */
    fun predictHealth(inputFeatures: FloatArray): Float {
        val output = Array(1) { FloatArray(1) }
        tflite.run(arrayOf(inputFeatures), output)
        return output[0][0] // Returns probability or remaining useful life (RUL)
    }
}