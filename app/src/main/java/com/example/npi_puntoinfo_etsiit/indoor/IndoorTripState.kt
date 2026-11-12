package com.example.npi_puntoinfo_etsiit.indoor

import android.content.Context
import com.example.npi_puntoinfo_etsiit.Nodo
import com.example.npi_puntoinfo_etsiit.R
import com.example.npi_puntoinfo_etsiit.RutaCalculada
import com.example.npi_puntoinfo_etsiit.outdoor.Maneuver
import kotlin.math.roundToInt

/**
 * Representa un paso/tramo individual en la navegación interior guiada por pedómetro.
 */
data class IndoorStep(
    val stepIndex: Int,
    val fromNode: Nodo,
    val toNode: Nodo,
    val instructionText: String,
    val distanceMeters: Int,
    val targetSteps: Int,
    val maneuver: Maneuver = Maneuver.STRAIGHT,
    val photoAssetPath: String? = null,
    val heading: Float? = null,
    val ejeXCamino: Float = 0.5f,
    val accion: String? = null,
    val tipoTramo: String? = null
)

/**
 * Réplica del ciclo de vida y estado de un recorrido de navegación (NavigationTrip)
 * adaptado a interiores mediante pedómetro.
 * Mantiene la progresión secuencial de instrucciones, imágenes de guiado, maniobras
 * y la transición automática por conteo de pasos o anulación manual.
 */
data class IndoorTripState(
    val tripId: String,
    val route: RutaCalculada,
    val steps: List<IndoorStep>,
    val currentStepIndex: Int = 0,
    val currentStepProgress: Int = 0, // Pasos dados en el paso actual
    val totalStepsTaken: Int = 0,
    val isCompleted: Boolean = false
) {
    val currentStep: IndoorStep? get() = steps.getOrNull(currentStepIndex)
    val nextStep: IndoorStep? get() = steps.getOrNull(currentStepIndex + 1)
    val totalRouteSteps: Int get() = steps.sumOf { it.targetSteps }

    fun onStepDetected(): IndoorTripState {
        if (isCompleted || currentStep == null) return this
        val step = currentStep!!
        val newProgress = currentStepProgress + 1
        val newTotal = totalStepsTaken + 1

        return if (newProgress >= step.targetSteps) {
            val nextIdx = currentStepIndex + 1
            if (nextIdx >= steps.size) {
                copy(currentStepProgress = newProgress, totalStepsTaken = newTotal, isCompleted = true)
            } else {
                copy(currentStepIndex = nextIdx, currentStepProgress = 0, totalStepsTaken = newTotal)
            }
        } else {
            copy(currentStepProgress = newProgress, totalStepsTaken = newTotal)
        }
    }

    fun nextStepManual(): IndoorTripState {
        if (steps.isEmpty()) return this
        val nextIdx = currentStepIndex + 1
        if (nextIdx >= steps.size) {
            return copy(isCompleted = true)
        }
        return copy(currentStepIndex = nextIdx, currentStepProgress = 0, isCompleted = false)
    }

    fun previousStepManual(): IndoorTripState {
        if (currentStepIndex <= 0) return this
        return copy(currentStepIndex = currentStepIndex - 1, currentStepProgress = 0, isCompleted = false)
    }

    companion object {
        fun buildFromRoute(
            tripId: String,
            route: RutaCalculada,
            context: Context? = null,
            isAccessibleRouting: Boolean = false
        ): IndoorTripState {
            if (context != null) {
                //val isComedorToAula11 = (route.origen.nombre.contains("Comedor", ignoreCase = true) || route.origen.id.contains("comedor", ignoreCase = true)) &&
                //        (route.destino.nombre.contains("1.1", ignoreCase = true) || route.destino.nombre.contains("A_11", ignoreCase = true) || route.destino.id.contains("A_11", ignoreCase = true))

                // CAMBIO TEMPORAL PARA PRUEBAS: Carga guidance_interior.json para cualquier ruta
                // para que puedas probar el guiado fotográfico desde cualquier origen/destino.
                val isComedorToAula11 = true
                if (isComedorToAula11) {
                    val customTrip = loadFromGuidanceJson(context, route, isAccessibleRouting)
                    if (customTrip != null) {
                        return customTrip
                    }
                }
            }

            val nodos = route.nodos
            val stepsList = mutableListOf<IndoorStep>()

            if (nodos.size < 2) {
                return IndoorTripState(tripId, route, emptyList(), isCompleted = true)
            }

            for (i in 0 until nodos.size - 1) {
                val from = nodos[i]
                val to = nodos[i + 1]

                val dx = to.x - from.x
                val dy = to.y - from.y
                val approxDistPixels = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                val approxDistMeters = (approxDistPixels * 0.15).roundToInt().coerceAtLeast(3)

                // ~1.3 pasos por metro (zancada de 0.75m)
                val targetSteps = (approxDistMeters * 1.3).roundToInt().coerceAtLeast(3)

                val nextNode = if (i + 2 < nodos.size) nodos[i + 2] else null
                val maneuver = determineManeuver(from, to, nextNode)
                val instructionText = buildInstructionText(from, to, maneuver, context)

                stepsList.add(
                    IndoorStep(
                        stepIndex = i,
                        fromNode = from,
                        toNode = to,
                        instructionText = instructionText,
                        distanceMeters = approxDistMeters,
                        targetSteps = targetSteps,
                        maneuver = maneuver
                    )
                )
            }

            return IndoorTripState(
                tripId = tripId,
                route = route,
                steps = stepsList
            )
        }

        fun loadFromGuidanceJson(
            context: Context,
            route: RutaCalculada,
            isAccessibleRouting: Boolean = false
        ): IndoorTripState? {
            try {
                val jsonString = context.assets.open("guidance_interior.json").bufferedReader().use { it.readText() }
                val root = org.json.JSONObject(jsonString)

                val routeObj = if (isAccessibleRouting && root.has("ascensor")) {
                    root.getJSONObject("ascensor")
                } else if (root.has("escaleras")) {
                    root.getJSONObject("escaleras")
                } else {
                    return null
                }

                val jsonNodos = routeObj.getJSONArray("nodos")
                if (jsonNodos.length() < 2) return null

                val stepsList = mutableListOf<IndoorStep>()

                for (i in 0 until jsonNodos.length() - 1) {
                    val curr = jsonNodos.getJSONObject(i)
                    val next = jsonNodos.getJSONObject(i + 1)

                    val fromNode = Nodo(
                        id = curr.optString("id", "node_$i"),
                        nombre = curr.optString("nombre", "Punto $i"),
                        planta = curr.optString("planta", "PB"),
                        edificio = "ETSIIT",
                        tipo = curr.optString("tipo_tramo", "PASILLO"),
                        x = curr.optInt("x", 100),
                        y = curr.optInt("y", 100)
                    )

                    val toNode = Nodo(
                        id = next.optString("id", "node_${i+1}"),
                        nombre = next.optString("nombre", "Punto ${i+1}"),
                        planta = next.optString("planta", "PB"),
                        edificio = "ETSIIT",
                        tipo = next.optString("tipo_tramo", "PASILLO"),
                        x = next.optInt("x", 100),
                        y = next.optInt("y", 100)
                    )

                    val dx = toNode.x - fromNode.x
                    val dy = toNode.y - fromNode.y
                    val approxDistPixels = kotlin.math.sqrt((dx * dx + dy * dy).toDouble())
                    val approxDistMeters = (approxDistPixels * 0.15).roundToInt().coerceAtLeast(3)
                    val targetSteps = (approxDistMeters * 1.3).roundToInt().coerceAtLeast(3)

                    // La acción y la foto del tramo corresponden al nodo de destino
                    val nextAccion = next.optString("accion", curr.optString("accion", "RECTO")).trim()
                    val nextAccionUpper = nextAccion.uppercase()
                    val isFinalStep = (i == jsonNodos.length() - 2) || nextAccionUpper.contains("FIN") || nextAccionUpper.contains("LLEGADA")

                    val accion = if (isFinalStep) "FIN" else nextAccion
                    val maneuver = if (isFinalStep) Maneuver.ARRIVAL else when {
                        nextAccionUpper.contains("IZQ") || nextAccionUpper.contains("LEFT") -> Maneuver.LEFT
                        nextAccionUpper.contains("DER") || nextAccionUpper.contains("RIGHT") -> Maneuver.RIGHT
                        else -> Maneuver.STRAIGHT
                    }

                    val rawInstructionEs = if (next.has("instruccion") && !next.isNull("instruccion")) next.getString("instruccion") else curr.optString("instruccion", "")
                    val rawInstructionEn = if (next.has("instruccion_en") && !next.isNull("instruccion_en")) next.getString("instruccion_en") else (if (curr.has("instruccion_en") && !curr.isNull("instruccion_en")) curr.getString("instruccion_en") else "")
                    val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
                    val cleanFrom = fromNode.nombre.replace('_', ' ')
                    val cleanTo = toNode.nombre.replace('_', ' ')

                    val instruction = if (isFinalStep) {
                        if (isEnglish) "Arriving at destination: $cleanTo (${toNode.planta})" else "¡Llegada a Destino! Has alcanzado $cleanTo (${toNode.planta})"
                    } else if (isEnglish && rawInstructionEn.isNotBlank()) {
                        rawInstructionEn
                    } else if (!isEnglish && rawInstructionEs.isNotBlank()) {
                        rawInstructionEs
                    } else if (isEnglish) {
                        when (accion) {
                            "GIRO_IZQUIERDA" -> "Turn left towards $cleanTo (${toNode.planta})"
                            "GIRO_DERECHA" -> "Turn right towards $cleanTo (${toNode.planta})"
                            "SUBIR_ESCALERAS" -> "Take the stairs to $cleanTo (${toNode.planta})"
                            "SUBIR_ASCENSOR" -> "Take the elevator to $cleanTo (${toNode.planta})"
                            else -> "Go straight towards $cleanTo (${toNode.planta})"
                        }
                    } else {
                        rawInstructionEs.ifBlank { buildInstructionText(fromNode, toNode, maneuver, context) }
                    }

                    val photo = if (next.has("foto") && !next.isNull("foto")) next.getString("foto") else (if (curr.has("foto") && !curr.isNull("foto")) curr.getString("foto") else null)
                    val headingVal = if (next.has("rumbo") && !next.isNull("rumbo")) next.getDouble("rumbo").toFloat() else (if (curr.has("rumbo") && !curr.isNull("rumbo")) curr.getDouble("rumbo").toFloat() else null)
                    val ejeXVal = if (next.has("eje_x_camino") && !next.isNull("eje_x_camino")) next.getDouble("eje_x_camino").toFloat() else (if (curr.has("eje_x_camino") && !curr.isNull("eje_x_camino")) curr.getDouble("eje_x_camino").toFloat() else 0.5f)
                    val tipoTramoVal = if (next.has("tipo_tramo") && !next.isNull("tipo_tramo")) next.getString("tipo_tramo") else (if (curr.has("tipo_tramo") && !curr.isNull("tipo_tramo")) curr.getString("tipo_tramo") else null)

                    stepsList.add(
                        IndoorStep(
                            stepIndex = i,
                            fromNode = fromNode,
                            toNode = toNode,
                            instructionText = instruction,
                            distanceMeters = approxDistMeters,
                            targetSteps = targetSteps,
                            maneuver = maneuver,
                            photoAssetPath = if (photo.isNullOrBlank()) null else photo,
                            heading = headingVal,
                            ejeXCamino = ejeXVal,
                            accion = accion,
                            tipoTramo = tipoTramoVal
                        )
                    )
                }

                return IndoorTripState(
                    tripId = root.optString("ruta_id", "indoor_json"),
                    route = route,
                    steps = stepsList
                )
            } catch (_: Exception) {
                return null
            }
        }

        private fun determineManeuver(from: Nodo, to: Nodo, next: Nodo?): Maneuver {
            if (from.planta != to.planta) return Maneuver.STRAIGHT
            if (next == null) return Maneuver.ARRIVAL

            val v1x = to.x - from.x
            val v1y = to.y - from.y
            val v2x = next.x - to.x
            val v2y = next.y - to.y

            val crossProduct = v1x * v2y - v1y * v2x
            return when {
                crossProduct > 150 -> Maneuver.RIGHT
                crossProduct < -150 -> Maneuver.LEFT
                else -> Maneuver.STRAIGHT
            }
        }

        private fun buildInstructionText(from: Nodo, to: Nodo, maneuver: Maneuver, context: Context? = null): String {
            val fromClean = from.nombre.replace('_', ' ')
            val toClean = to.nombre.replace('_', ' ')

            if (context != null) {
                if (from.planta != to.planta) {
                    return context.getString(R.string.indoor_step_change_floor, from.planta, to.planta, toClean)
                }

                return when (maneuver) {
                    Maneuver.RIGHT -> context.getString(R.string.indoor_step_turn_right, fromClean, toClean)
                    Maneuver.LEFT -> context.getString(R.string.indoor_step_turn_left, fromClean, toClean)
                    Maneuver.ARRIVAL -> context.getString(R.string.indoor_step_arrival, toClean)
                    else -> context.getString(R.string.indoor_step_straight, fromClean, toClean)
                }
            }

            if (from.planta != to.planta) {
                return "Cambia de la ${from.planta} a la ${to.planta} por escaleras o ascensor hacia $toClean"
            }

            return when (maneuver) {
                Maneuver.RIGHT -> "Gira a la derecha tras $fromClean dirigiéndote hacia $toClean"
                Maneuver.LEFT -> "Gira a la izquierda tras $fromClean dirigiéndote hacia $toClean"
                Maneuver.ARRIVAL -> "Llegada inminente a $toClean"
                else -> "Sigue recto por el pasillo desde $fromClean hacia $toClean"
            }
        }
    }
}
