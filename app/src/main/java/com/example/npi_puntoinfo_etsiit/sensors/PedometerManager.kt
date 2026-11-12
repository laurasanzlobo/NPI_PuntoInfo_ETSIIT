package com.example.npi_puntoinfo_etsiit.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.sqrt

/**
 * Gestor del sensor de Pedómetro / Detector de Pasos (Pedometer / Step Detector).
 * Permite monitorear el movimiento del usuario paso a paso en interiores sin usar GPS.
 * Si el dispositivo no cuenta con sensor hardware de pasos (ej. emulador),
 * incluye un detector basado en la magnitud del Acelerómetro como respaldo.
 */
class PedometerManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepDetectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometerSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isHardwareStepSensorAvailable: Boolean
        get() = stepDetectorSensor != null

    var onStepDetected: (() -> Unit)? = null
    var onStepCountChanged: ((Int) -> Unit)? = null

    private var totalStepsRecorded: Int = 0
    private var lastAccelTimeMs: Long = 0L
    private var accelMagnitudeFilter: Float = 9.81f

    fun startListening() {
        if (stepDetectorSensor != null) {
            sensorManager?.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        if (accelerometerSensor != null) {
            sensorManager?.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    /**
     * Permite simular manualmente la detección de un paso (para pruebas y depuración).
     */
    fun simulateStep() {
        totalStepsRecorded++
        onStepDetected?.invoke()
        onStepCountChanged?.invoke(totalStepsRecorded)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            totalStepsRecorded++
            onStepDetected?.invoke()
            onStepCountChanged?.invoke(totalStepsRecorded)
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            // Algoritmo de detección de picos de aceleración para estimar pasos por movimiento/zancada
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            accelMagnitudeFilter = accelMagnitudeFilter * 0.8f + magnitude * 0.2f
            val now = System.currentTimeMillis()

            // Umbral optimizado (1.8 m/s²) e intervalo de 350 ms para respuesta rápida y precisa al caminar
            if (magnitude - accelMagnitudeFilter > 1.8f && (now - lastAccelTimeMs) > 350L) {
                lastAccelTimeMs = now
                totalStepsRecorded++
                onStepDetected?.invoke()
                onStepCountChanged?.invoke(totalStepsRecorded)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
