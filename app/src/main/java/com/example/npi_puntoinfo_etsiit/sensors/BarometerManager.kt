package com.example.npi_puntoinfo_etsiit.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import com.example.npi_puntoinfo_etsiit.formatearPlanta
import com.example.npi_puntoinfo_etsiit.normalizarPlanta

data class BarometerFloorEstimate(
    val floorCode: String,      // "PSS", "PB", "P1", "P2", "P3", "P4"
    val floorName: String,      // "Planta Baja", "Planta 1", etc.
    val pressureHpa: Float,     // Presión medida en hPa
    val altitudeMeters: Float   // Altitud estimada
)

/**
 * Gestor del Sensor de Presión / Barómetro para estimar la planta del edificio ETSIIT.
 */
class BarometerManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val pressureSensor: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_PRESSURE)

    val isAvailable: Boolean
        get() = pressureSensor != null

    var onPressureChanged: ((BarometerFloorEstimate) -> Unit)? = null

    fun startListening() {
        if (pressureSensor != null) {
            sensorManager?.registerListener(this, pressureSensor, SensorManager.SENSOR_DELAY_UI)
        } else {
            // Si el dispositivo no tiene barómetro físico (ej. emulador), devolver estimación predeterminada (PB)
            val fallback = BarometerFloorEstimate(
                floorCode = "PB",
                floorName = formatearPlanta("PB"),
                pressureHpa = 935.0f,
                altitudeMeters = 700.0f
            )
            onPressureChanged?.invoke(fallback)
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null || event.sensor.type != Sensor.TYPE_PRESSURE) return

        val pressure = event.values[0]
        val altitude = SensorManager.getAltitude(SensorManager.PRESSURE_STANDARD_ATMOSPHERE, pressure)

        val estimate = estimateFloorFromPressure(pressure, altitude)
        onPressureChanged?.invoke(estimate)
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    companion object {
        /**
         * Mapeo de presión atmosférica en Granada ETSIIT (~700m altitud).
         * La variación promedio por planta de 3.5m es de ~0.4 hPa.
         */
        fun estimateFloorFromPressure(pressureHpa: Float, altitudeMeters: Float): BarometerFloorEstimate {
            val (code, name) = when {
                pressureHpa > 936.2f -> "PSS" to formatearPlanta("PSS")
                pressureHpa in 934.4f..936.2f -> "PB" to formatearPlanta("PB")
                pressureHpa in 933.0f..934.4f -> "P1" to formatearPlanta("P1")
                pressureHpa in 931.6f..933.0f -> "P2" to formatearPlanta("P2")
                pressureHpa in 930.2f..931.6f -> "P3" to formatearPlanta("P3")
                pressureHpa < 930.2f -> "P4" to formatearPlanta("P4")
                else -> "PB" to formatearPlanta("PB")
            }

            return BarometerFloorEstimate(
                floorCode = normalizarPlanta(code),
                floorName = name,
                pressureHpa = pressureHpa,
                altitudeMeters = altitudeMeters
            )
        }
    }
}
