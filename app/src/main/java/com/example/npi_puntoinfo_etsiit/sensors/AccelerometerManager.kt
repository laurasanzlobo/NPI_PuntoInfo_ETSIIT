package com.example.npi_puntoinfo_etsiit.sensors

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.util.Log
import kotlin.math.abs

/**
 * Representa la postura física del terminal.
 */
enum class DeviceOrientation(val label: String) {
    VERTICAL("En la mano (Vertical)"),
    FLAT("Horizontal / Lectura en mano"),
    TILTED_LEFT("Inclinado a la izquierda (Gesto Llévame)"),
    TILTED_RIGHT("Inclinado a la derecha"),
    UNKNOWN("En movimiento")
}

/**
 * Gestor avanzado del Acelerómetro y Giroscopio para Android / Jetpack Compose.
 *
 * Todos los gestos siguen la directriz de diseño de interacción de ejecutar el cambio
 * ÚNICAMENTE cuando el móvil regresa a la posición inicial de visión del usuario:
 *
 * 1. Gesto de Consulta de Clase Actual (3 inclinaciones hacia adelante y volver):
 *    - Echar la punta superior del móvil hacia adelante 3 veces (1, 2, 3) y volver a la posición inicial.
 *    - Acción: Abre la ventana de Clase en Curso / Próxima Clase.
 * 2. Gesto «¡Llévame!» (Inclinar a la izquierda y volver a la posición inicial):
 *    - Inclinar el móvil hacia la izquierda y regresar a la posición erguida inicial.
 *    - Acción: Inicia de inmediato la ruta hacia el aula, comedor o despacho.
 * 3. Gesto de Acción Rápida (1 inclinación hacia mí y volver a la posición inicial):
 *    - Echar la punta superior del móvil 1 vez hacia el usuario y regresar a la posición inicial.
 *    - Acción: Encargar menú para llevar en Comedores o enviar correo al profesor.
 * 4. Gesto de Levantar el móvil (Lift-to-Orient):
 *    - Levantar el móvil en vertical frente a los ojos para guiado paso a paso o bajarlo en horizontal para ver el mapa 2D.
 */
class AccelerometerManager(context: Context) : SensorEventListener {

    private val sensorManager = context.getSystemService(Context.SENSOR_SERVICE) as? SensorManager
    private val accelerometer: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
    private val gyroscope: Sensor? = sensorManager?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)

    private val towardsUserNodDetector = TowardsUserNodDetector()
    private val tripleForwardTiltDetector = TripleForwardTiltDetector()

    val isAvailable: Boolean
        get() = accelerometer != null

    // Callbacks a la interfaz Compose
    var onSensorValuesChanged: ((x: Float, y: Float, z: Float) -> Unit)? = null
    var onShakeProgress: ((count: Int, total: Int) -> Unit)? = null
    var onShakeDetected: (() -> Unit)? = null       // 3 inclinaciones hacia adelante + volver
    var onTakeMeThereGesture: (() -> Unit)? = null  // Inclinar a la izquierda + volver a posición inicial
    var onNodStarted: (() -> Unit)? = null          // Fase 1 de inclinación hacia mí iniciada
    var onForwardTiltGesture: (() -> Unit)? = null  // 1 inclinación hacia mí + volver a posición inicial
    var onDeviceLifted: (() -> Unit)? = null        // Levantamiento a vertical estricto
    var onPostureModeChanged: ((isVertical: Boolean) -> Unit)? = null
    var onOrientationChanged: ((DeviceOrientation) -> Unit)? = null

    private val gestureTag = "NPI_GESTOS"

    // --- Parámetros Gesto «¡Llévame!»: Inclinar a la izquierda y volver a la posición inicial ---
    private val TILT_LEFT_THRESHOLD = 4.6f       // m/s² en eje +X al inclinar a la izquierda
    private val RETURN_NEUTRAL_MAX_X = 2.2f      // m/s² cerca de 0 al volver a posición inicial
    private val MIN_TILT_LEFT_DURATION_MS = 70L
    private val MAX_TILT_LEFT_WINDOW_MS = 1800L
    private val FLICK_COOLDOWN = 1600L

    private var isTiltedLeftForTakeMe = false
    private var tiltedLeftStartTime = 0L
    private var lastFlickTriggeredTime = 0L

    // Respaldo por acelerómetro para dispositivos sin giroscopio (emuladores)
    private var lastPitchGestureTime = 0L
    private var accelForwardTiltCount = 0
    private var accelFirstForwardTiltTime = 0L
    private var accelInForwardDip = false
    private var accelWaitingFinalReturn = false

    // --- Parámetros Gesto: Levantar el móvil (Guiado paso a paso) y bajarlo (Ver mapa) ---
    var verticalMaxZThreshold: Float = 3.0f
    var verticalMinYThreshold: Float = 7.8f
    var flatMinZThreshold: Float = 3.8f

    private var isCurrentlyVertical = false
    private var currentOrientation: DeviceOrientation = DeviceOrientation.UNKNOWN

    fun startListening() {
        accelerometer?.let {
            sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        // Registramos el giroscopio siempre que se escuche algún gesto de inclinación
        if (onForwardTiltGesture != null || onShakeDetected != null || onTakeMeThereGesture != null) {
            gyroscope?.let {
                sensorManager?.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
            }
        }
    }

    fun stopListening() {
        sensorManager?.unregisterListener(this)
        towardsUserNodDetector.reset()
        tripleForwardTiltDetector.reset()
        isTiltedLeftForTakeMe = false
        accelForwardTiltCount = 0
        accelInForwardDip = false
        accelWaitingFinalReturn = false
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return

        // =========================================================================
        // 1. PROCESAMIENTO DEL GIROSCOPIO (Inclinaciones hacia adelante y hacia mí)
        // =========================================================================
        if (event.sensor.type == Sensor.TYPE_GYROSCOPE) {
            val gx = event.values[0]
            val gy = event.values[1]
            val gz = event.values[2]
            val timestampNs = event.timestamp

            // A) Gesto de 3 inclinaciones hacia adelante y volver (Consulta de Clase Actual)
            if (onShakeDetected != null) {
                val prevCount = tripleForwardTiltDetector.currentCount
                val triggered = tripleForwardTiltDetector.onRotation(gx, gy, gz, timestampNs)
                val newCount = tripleForwardTiltDetector.currentCount
                if (newCount != prevCount) {
                    onShakeProgress?.invoke(newCount, 3)
                }
                if (triggered) {
                    Log.i(gestureTag, "3 inclinaciones hacia adelante y retorno completadas")
                    lastPitchGestureTime = System.currentTimeMillis()
                    isTiltedLeftForTakeMe = false
                    onShakeDetected?.invoke()
                }
            }

            // B) Gesto de 1 inclinación hacia mí y volver (Correo / Reserva Comedor)
            if (onForwardTiltGesture != null) {
                val previousPhase = towardsUserNodDetector.phaseName
                val detected = towardsUserNodDetector.onRotation(gx, gy, gz, timestampNs)
                if (previousPhase != towardsUserNodDetector.phaseName) {
                    if (previousPhase == "IDLE" && towardsUserNodDetector.phaseName == "TOWARDS_USER") {
                        onNodStarted?.invoke()
                    }
                }
                if (detected) {
                    Log.i(gestureTag, "Inclinación hacia el usuario y retorno completada")
                    lastPitchGestureTime = System.currentTimeMillis()
                    isTiltedLeftForTakeMe = false
                    onForwardTiltGesture?.invoke()
                }
            }
            return
        }

        if (event.sensor.type != Sensor.TYPE_ACCELEROMETER) return

        val x = event.values[0]
        val y = event.values[1]
        val z = event.values[2]
        val now = System.currentTimeMillis()

        // 1. Notificar valores brutos
        onSensorValuesChanged?.invoke(x, y, z)

        // =========================================================================
        // 2. RESPALDO CON ACELERÓMETRO (solo si el dispositivo no tiene giroscopio)
        // =========================================================================
        if (gyroscope == null && onShakeDetected != null) {
            if (accelForwardTiltCount > 0 && now - accelFirstForwardTiltTime > 3200L) {
                accelForwardTiltCount = 0
                accelInForwardDip = false
                accelWaitingFinalReturn = false
            }
            // Inclinación de la punta superior hacia adelante: Z baja por debajo de 1.8 m/s²
            if (!accelInForwardDip && !accelWaitingFinalReturn && z < 1.8f && abs(x) < 4.0f) {
                accelInForwardDip = true
                if (accelForwardTiltCount == 0) accelFirstForwardTiltTime = now
                accelForwardTiltCount++
                onShakeProgress?.invoke(accelForwardTiltCount, 3)
                if (accelForwardTiltCount >= 3) {
                    accelWaitingFinalReturn = true
                }
            } else if (accelInForwardDip && z > 3.2f) {
                accelInForwardDip = false
            }

            if (accelWaitingFinalReturn && z >= 3.5f && abs(x) < 3.5f && y >= 4.0f) {
                accelForwardTiltCount = 0
                accelInForwardDip = false
                accelWaitingFinalReturn = false
                lastPitchGestureTime = now
                onShakeDetected?.invoke()
            }
        }

        // =========================================================================
        // 3. GESTO «¡LLÉVAME!»: INCLINAR A LA IZQUIERDA Y VOLVER A POSICIÓN INICIAL
        // =========================================================================
        if (onTakeMeThereGesture != null) {
            val isPitchGestureActive = tripleForwardTiltDetector.currentCount > 0 ||
                    towardsUserNodDetector.phaseName != "IDLE" ||
                    (now - lastPitchGestureTime < 800L)

            if (isPitchGestureActive) {
                isTiltedLeftForTakeMe = false
            } else if (!isTiltedLeftForTakeMe) {
                // Fase 1: El usuario inclina el móvil hacia la izquierda (eje +X >= umbral)
                if (x >= TILT_LEFT_THRESHOLD && y > 1.5f && abs(z) < 8.5f) {
                    if (now - lastFlickTriggeredTime > FLICK_COOLDOWN) {
                        isTiltedLeftForTakeMe = true
                        tiltedLeftStartTime = now
                        Log.d(gestureTag, "Llévame Fase 1: móvil inclinado a la izquierda (x=$x)")
                    }
                }
            } else {
                // Fase 2: Esperando a que el móvil vuelva a la posición inicial de visión
                val elapsed = now - tiltedLeftStartTime
                if (elapsed > MAX_TILT_LEFT_WINDOW_MS || x < -4.2f) {
                    // Expiró el tiempo o se pasó de largo hacia la derecha bruscamente
                    isTiltedLeftForTakeMe = false
                } else if (elapsed >= MIN_TILT_LEFT_DURATION_MS &&
                    abs(x) <= RETURN_NEUTRAL_MAX_X &&
                    y >= 3.0f
                ) {
                    // El móvil ha regresado a la posición inicial frente al usuario: disparar acción
                    isTiltedLeftForTakeMe = false
                    lastFlickTriggeredTime = now
                    Log.i(gestureTag, "Llévame Fase 2 completada: móvil de vuelta en posición inicial (x=$x)")
                    onTakeMeThereGesture?.invoke()
                }
            }
        }

        // =========================================================================
        // 4. GESTO LEVANTAR EL MÓVIL (Guiado paso a paso) Y BAJARLO (Ver mapa)
        // =========================================================================
        val isVertical = abs(z) <= verticalMaxZThreshold && y >= verticalMinYThreshold && abs(x) < 4.0f
        val isLowered = abs(z) >= flatMinZThreshold || y < 7.2f || abs(x) >= 4.8f

        if (isVertical) {
            if (!isCurrentlyVertical) {
                isCurrentlyVertical = true
                onPostureModeChanged?.invoke(true)
                onDeviceLifted?.invoke()
            }
        } else if (isLowered) {
            if (isCurrentlyVertical) {
                isCurrentlyVertical = false
                onPostureModeChanged?.invoke(false)
            }
        }

        // Estimación de orientación textual para la UI
        val newOrientation = when {
            x > 4.6f -> DeviceOrientation.TILTED_LEFT
            x < -4.6f -> DeviceOrientation.TILTED_RIGHT
            isVertical -> DeviceOrientation.VERTICAL
            isLowered -> DeviceOrientation.FLAT
            else -> DeviceOrientation.UNKNOWN
        }

        if (newOrientation != currentOrientation) {
            currentOrientation = newOrientation
            onOrientationChanged?.invoke(newOrientation)
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {
        // No aplicable
    }
}

/**
 * Detector del gesto de Acción Rápida (Comedores / Correo a Profesor):
 * 1 sola inclinación del móvil HACIA EL USUARIO (hacia mí) y volver a la posición inicial.
 *
 * En el sistema de coordenadas de Android:
 * - Acercar/inclinar la punta superior del móvil hacia el usuario produce velocidad angular X POSITIVA (gx > 0).
 * - Devolver el móvil a su posición inicial produce velocidad angular X NEGATIVA (gx < 0).
 * - El cambio se dispara cuando el móvil termina de volver a la posición inicial.
 */
internal class TowardsUserNodDetector {
    private enum class Phase { IDLE, TOWARDS_USER, RETURNING }

    private var phase = Phase.IDLE
    val phaseName: String get() = phase.name

    private var previousTimestampNs = 0L
    private var gestureStartedNs = 0L
    private var lastMotionNs = 0L
    private var lastDetectionNs = -3_000_000_000L
    private var lastForwardAwayMotionNs = -3_000_000_000L

    private var towardsUserAngle = 0f
    private var returnAngle = 0f

    private val minimumSpeed = 0.42f       // rad/s
    private val minimumTiltAngle = 0.17f   // radianes (~9.7º)
    private val maxTiltAngle = 1.15f       // evita giros descontrolados
    private val maxGestureNs = 1_800_000_000L
    private val maxPauseNs = 550_000_000L
    private val cooldownNs = 1_500_000_000L
    // Si el usuario acaba de inclinar hacia adelante (alejando la punta), no confundir el rebote con "hacia mí"
    private val forwardGuardNs = 650_000_000L

    fun reset() {
        phase = Phase.IDLE
        previousTimestampNs = 0L
        gestureStartedNs = 0L
        lastMotionNs = 0L
        towardsUserAngle = 0f
        returnAngle = 0f
    }

    fun onRotation(x: Float, y: Float, z: Float, timestampNs: Long): Boolean {
        if (previousTimestampNs == 0L) {
            previousTimestampNs = timestampNs
            return false
        }
        val dt = (timestampNs - previousTimestampNs) / 1_000_000_000f
        previousTimestampNs = timestampNs
        if (dt <= 0f || dt > 0.25f) {
            reset()
            previousTimestampNs = timestampNs
            return false
        }
        if (timestampNs - lastDetectionNs < cooldownNs) return false

        val xDominant = abs(x) >= minimumSpeed &&
                abs(x) >= 0.85f * abs(y) &&
                abs(x) >= 0.85f * abs(z)

        if (phase != Phase.IDLE &&
            (timestampNs - gestureStartedNs > maxGestureNs ||
                    timestampNs - lastMotionNs > maxPauseNs)
        ) {
            reset()
            previousTimestampNs = timestampNs
        }

        when (phase) {
            Phase.IDLE -> {
                if (xDominant && x < -minimumSpeed) {
                    // Se está inclinando hacia adelante (alejándose del usuario): activar guarda
                    lastForwardAwayMotionNs = timestampNs
                } else if (xDominant && x > minimumSpeed) {
                    // Solo iniciar si no es el rebote de una inclinación hacia adelante
                    if (timestampNs - lastForwardAwayMotionNs > forwardGuardNs) {
                        phase = Phase.TOWARDS_USER
                        towardsUserAngle = x * dt
                        gestureStartedNs = timestampNs
                        lastMotionNs = timestampNs
                    }
                }
            }
            Phase.TOWARDS_USER -> {
                when {
                    xDominant && x > minimumSpeed -> {
                        towardsUserAngle += x * dt
                        lastMotionNs = timestampNs
                        if (towardsUserAngle > maxTiltAngle) {
                            reset()
                            previousTimestampNs = timestampNs
                        }
                    }
                    xDominant && x < -minimumSpeed -> {
                        if (towardsUserAngle >= minimumTiltAngle) {
                            phase = Phase.RETURNING
                            returnAngle = abs(x) * dt
                            lastMotionNs = timestampNs
                        } else {
                            reset()
                            previousTimestampNs = timestampNs
                        }
                    }
                }
            }
            Phase.RETURNING -> {
                if (x < -0.20f && abs(x) >= 0.75f * abs(y) && abs(x) >= 0.75f * abs(z)) {
                    returnAngle += abs(x) * dt
                    lastMotionNs = timestampNs
                }

                // Se dispara cuando ha acumulado el ángulo de retorno y vuelve a estabilizarse frente al usuario
                val returnedEnough = returnAngle >= minimumTiltAngle * 0.80f &&
                        returnAngle >= towardsUserAngle * 0.65f
                val settledInPosition = abs(x) < 0.45f || returnAngle >= towardsUserAngle * 0.85f

                if (returnedEnough && settledInPosition) {
                    lastDetectionNs = timestampNs
                    reset()
                    previousTimestampNs = timestampNs
                    return true
                } else if (xDominant && x > minimumSpeed) {
                    // Volvió a inclinar hacia sí mismo en lugar de estabilizarse
                    reset()
                    previousTimestampNs = timestampNs
                }
            }
        }
        return false
    }
}

/**
 * Detector del gesto de Consulta de Clase Actual:
 * Echar la punta superior del móvil hacia adelante 3 veces (1, 2, 3) y volver a la posición inicial.
 *
 * En el sistema de coordenadas de Android:
 * - Echar la punta superior hacia adelante produce velocidad angular X NEGATIVA (gx < 0).
 * - Volver hacia la posición del usuario produce velocidad angular X POSITIVA (gx > 0).
 * - El cambio ocurre únicamente cuando, tras la 3ª inclinación hacia adelante, el móvil regresa a la posición del usuario.
 */
internal class TripleForwardTiltDetector {
    private enum class State {
        WAITING_FORWARD_PUSH,
        IN_FORWARD_PUSH,
        WAITING_FINAL_RETURN
    }

    private var state = State.WAITING_FORWARD_PUSH
    var currentCount: Int = 0
        private set

    private var previousTimestampNs = 0L
    private var firstTiltNs = 0L
    private var lastActivityNs = 0L
    private var lastTriggeredNs = -3_000_000_000L
    private var lastTowardsUserStartNs = -3_000_000_000L

    private var currentForwardAngle = 0f
    private var finalReturnAngle = 0f

    private val minPushSpeed = 0.42f          // rad/s hacia adelante (gx < -0.42)
    private val minForwardAngle = 0.14f       // radianes (~8º) por cada inclinación
    private val minFinalReturnAngle = 0.13f   // radianes de retorno a la posición inicial
    private val maxPauseBetweenTiltsNs = 1_300_000_000L
    private val maxTotalSequenceNs = 3_800_000_000L
    private val cooldownNs = 1_600_000_000L
    private val towardsUserGuardNs = 550_000_000L

    fun reset() {
        state = State.WAITING_FORWARD_PUSH
        currentCount = 0
        previousTimestampNs = 0L
        firstTiltNs = 0L
        lastActivityNs = 0L
        currentForwardAngle = 0f
        finalReturnAngle = 0f
    }

    fun onRotation(x: Float, y: Float, z: Float, timestampNs: Long): Boolean {
        if (previousTimestampNs == 0L) {
            previousTimestampNs = timestampNs
            return false
        }
        val dt = (timestampNs - previousTimestampNs) / 1_000_000_000f
        previousTimestampNs = timestampNs
        if (dt <= 0f || dt > 0.25f) {
            reset()
            previousTimestampNs = timestampNs
            return false
        }
        if (timestampNs - lastTriggeredNs < cooldownNs) return false

        if (currentCount > 0 || state != State.WAITING_FORWARD_PUSH) {
            if (timestampNs - lastActivityNs > maxPauseBetweenTiltsNs ||
                (firstTiltNs > 0L && timestampNs - firstTiltNs > maxTotalSequenceNs)
            ) {
                reset()
                previousTimestampNs = timestampNs
            }
        }

        val xDominant = abs(x) >= 0.78f * abs(y) && abs(x) >= 0.78f * abs(z)

        when (state) {
            State.WAITING_FORWARD_PUSH -> {
                if (currentCount == 0 && xDominant && x > minPushSpeed) {
                    // Si empieza echando el móvil hacia sí mismo (gesto de acción), activamos guarda
                    lastTowardsUserStartNs = timestampNs
                } else if (xDominant && x < -minPushSpeed) {
                    if (currentCount > 0 || timestampNs - lastTowardsUserStartNs > towardsUserGuardNs) {
                        state = State.IN_FORWARD_PUSH
                        currentForwardAngle = abs(x) * dt
                        if (currentCount == 0) firstTiltNs = timestampNs
                        lastActivityNs = timestampNs
                    }
                }
            }
            State.IN_FORWARD_PUSH -> {
                if (x < -0.18f && xDominant) {
                    currentForwardAngle += abs(x) * dt
                    lastActivityNs = timestampNs
                } else if (x >= -0.12f) {
                    // Terminó el empuje hacia adelante (empieza a volver o hace pausa)
                    if (currentForwardAngle >= minForwardAngle) {
                        currentCount++
                        lastActivityNs = timestampNs
                        if (currentCount >= 3) {
                            state = State.WAITING_FINAL_RETURN
                            finalReturnAngle = if (x > 0f) x * dt else 0f
                        } else {
                            state = State.WAITING_FORWARD_PUSH
                            currentForwardAngle = 0f
                        }
                    } else if (x > minPushSpeed) {
                        // Movimiento demasiado pequeño, descartar este empuje
                        state = State.WAITING_FORWARD_PUSH
                        currentForwardAngle = 0f
                    }
                }
            }
            State.WAITING_FINAL_RETURN -> {
                if (x > 0.20f) {
                    finalReturnAngle += x * dt
                    lastActivityNs = timestampNs
                }
                // Cuando ha regresado hacia el usuario y el móvil se estabiliza en la posición de lectura
                if (finalReturnAngle >= minFinalReturnAngle && abs(x) < 0.50f) {
                    lastTriggeredNs = timestampNs
                    reset()
                    previousTimestampNs = timestampNs
                    return true
                }
            }
        }
        return false
    }
}
