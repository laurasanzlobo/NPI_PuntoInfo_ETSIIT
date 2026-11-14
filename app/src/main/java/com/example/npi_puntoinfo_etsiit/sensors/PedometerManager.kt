package com.example.npi_puntoinfo_etsiit.sensors

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import androidx.core.content.ContextCompat
import kotlin.math.sqrt

/**
 * Gestor del sensor de Pedómetro / Detector de Pasos (Pedometer / Step Detector).
 * Permite monitorear el movimiento del usuario paso a paso en interiores sin usar GPS.
 * Si el dispositivo no cuenta con sensor hardware de pasos (ej. emulador) o no tiene
 * permiso ACTIVITY_RECOGNITION, conmuta automáticamente al acelerómetro como respaldo.
 */
class PedometerManager(context: Context) : SensorEventListener {

    private val appContext = context.applicationContext
    private val sensorManager = appContext.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val stepDetectorSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_STEP_DETECTOR)
    private val accelerometerSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)

    val isHardwareStepSensorAvailable: Boolean
        get() = stepDetectorSensor != null

    var onStepDetected: (() -> Unit)? = null
    var onStepCountChanged: ((Int) -> Unit)? = null

    private var totalStepsRecorded: Int = 0
    private var lastRecordedStepMs: Long = 0L
    private var lastHardwareStepMs: Long = 0L
    private var accelMagnitudeFilter: Float = 9.81f

    private fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                appContext,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    fun startListening() {
        lastRecordedStepMs = 0L
        lastHardwareStepMs = 0L
        accelMagnitudeFilter = 9.81f

        if (stepDetectorSensor != null && hasActivityRecognitionPermission()) {
            sensorManager?.registerListener(this, stepDetectorSensor, SensorManager.SENSOR_DELAY_FASTEST)
        }
        // Mantener siempre el acelerómetro como respaldo por si TYPE_STEP_DETECTOR tarda o no emite
        // en un terminal concreto, deduplicando eventos por ventana temporal para no contar doble.
        if (accelerometerSensor != null) {
            sensorManager?.registerListener(this, accelerometerSensor, SensorManager.SENSOR_DELAY_GAME)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    private fun recordStep(nowMs: Long = System.currentTimeMillis()) {
        lastRecordedStepMs = nowMs
        totalStepsRecorded++
        onStepDetected?.invoke()
        onStepCountChanged?.invoke(totalStepsRecorded)
    }

    /**
     * Permite simular manualmente la detección de un paso (para pruebas y depuración).
     */
    fun simulateStep() {
        recordStep()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        val now = System.currentTimeMillis()

        if (event.sensor.type == Sensor.TYPE_STEP_DETECTOR) {
            lastHardwareStepMs = now
            // Si el respaldo de acelerómetro ya contó este mismo paso justo antes, evitar doble conteo
            if (now - lastRecordedStepMs >= 420L) {
                recordStep(now)
            }
        } else if (event.sensor.type == Sensor.TYPE_ACCELEROMETER) {
            val x = event.values[0]
            val y = event.values[1]
            val z = event.values[2]
            val magnitude = sqrt((x * x + y * y + z * z).toDouble()).toFloat()

            accelMagnitudeFilter = accelMagnitudeFilter * 0.9f + magnitude * 0.1f

            // Solo actuar si el detector hardware no está emitiendo pasos recientes (> 1500 ms)
            val hardwareQuiet = lastHardwareStepMs == 0L || (now - lastHardwareStepMs) > 1500L
            if (hardwareQuiet &&
                (magnitude - accelMagnitudeFilter) > 1.20f &&
                (now - lastRecordedStepMs) > 450L
            ) {
                recordStep(now)
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}
}
