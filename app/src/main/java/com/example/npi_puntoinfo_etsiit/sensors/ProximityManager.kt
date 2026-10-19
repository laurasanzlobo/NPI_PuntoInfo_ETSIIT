package com.example.npi_puntoinfo_etsiit.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager

/**
 * Gestor del Sensor de Proximidad para Android / Jetpack Compose.
 *
 * Permite activar acciones físicas sin contacto (como el asistente de voz en Guiado):
 * - Acercar la mano por encima de la pantalla (gesto de paso o cobertura).
 * - Acercar el móvil a la oreja/cara estilo walkie-talkie para hablar en privado.
 */
class ProximityManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val proximitySensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PROXIMITY)

    val isAvailable: Boolean
        get() = proximitySensor != null

    // Callbacks a Compose
    var onNear: (() -> Unit)? = null
    var onFar: (() -> Unit)? = null
    var onStateChanged: ((isNear: Boolean, distance: Float) -> Unit)? = null

    private var isCurrentlyNear = false
    private var lastNearTriggerTime = 0L
    private val COOLDOWN_MS = 2500L

    fun startListening() {
        if (!isAvailable) return
        proximitySensor?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_UI)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        isCurrentlyNear = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PROXIMITY) return

        val distance = event.values[0]
        val maxRange = proximitySensor?.maximumRange ?: 5.0f

        val isNear = if (maxRange <= 1.0f) {
            // Sensor puramente binario (0 = Cerca, 1 = Lejos)
            distance < 0.5f
        } else {
            // Sensor analógico en centímetros (ej. 0.0 cm vs 5.0 cm)
            distance < maxRange && distance < 4.0f
        }

        val now = System.currentTimeMillis()
        onStateChanged?.invoke(isNear, distance)

        if (isNear) {
            if (!isCurrentlyNear) {
                isCurrentlyNear = true
                if (now - lastNearTriggerTime > COOLDOWN_MS) {
                    lastNearTriggerTime = now
                    onNear?.invoke()
                }
            }
        } else {
            if (isCurrentlyNear) {
                isCurrentlyNear = false
                onFar?.invoke()
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No aplicable
    }
}

