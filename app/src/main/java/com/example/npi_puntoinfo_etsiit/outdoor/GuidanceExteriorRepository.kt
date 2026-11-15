package com.example.npi_puntoinfo_etsiit.outdoor

import android.content.Context
import android.util.Log
import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import java.io.InputStreamReader

data class GuidanceExteriorData(
    val version: String,
    @SerializedName("ruta_id") val rutaId: String,
    val titulo: String,
    val ida: GuidanceTramo,
    val vuelta: GuidanceTramo
)

data class GuidanceTramo(
    val origen: String,
    val destino: String,
    val nodos: List<GuidanceNodo>
)

data class GuidanceNodo(
    val id: String,
    val orden: Int,
    val nombre: String,
    val lat: Double,
    val lng: Double,
    val rumbo: Int?,
    val foto: String?,
    val accion: String,
    val instruccion: String,
    @SerializedName("instruccion_en") val instruccionEn: String? = null,
    @SerializedName("eje_x_camino") val ejeXCamino: Double = 0.5
)

object GuidanceExteriorRepository {
    private const val TAG = "GuidanceExteriorRepo"
    private const val FILE_NAME = "guidance_exterior.json"

    private var cachedData: GuidanceExteriorData? = null

    /**
     * Carga y parsea el archivo guidance_exterior.json desde la carpeta assets.
     */
    fun getGuidanceData(context: Context): GuidanceExteriorData? {
        cachedData?.let { return it }
        return try {
            context.assets.open(FILE_NAME).use { inputStream ->
                InputStreamReader(inputStream).use { reader ->
                    val data = Gson().fromJson(reader, GuidanceExteriorData::class.java)
                    cachedData = data
                    data
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error al cargar $FILE_NAME desde assets", e)
            null
        }
    }

    /**
     * Determina si la ruta solicitada corresponde a la ruta local modelada por nodos.
     */
    fun isLocalGuidanceRoute(destinationId: String?): Boolean {
        if (destinationId == null) return false
        if (destinationId.startsWith("dining-") && destinationId != "dining-aynadamar") {
            return false
        }
        return true
    }

    /**
     * Genera un objeto WalkingRoute a partir de los nodos de un tramo (ida o vuelta).
     */
    fun tramoToWalkingRoute(tramo: GuidanceTramo): WalkingRoute {
        val nodos = tramo.nodos
        val points = nodos.map { GeoPoint(it.lat, it.lng) }

        // Calcular distancias entre nodos consecutivos
        val stepDistances = mutableListOf<Double>()
        var totalDistance = 0.0

        for (i in 0 until nodos.size) {
            val dist = if (i < nodos.size - 1) {
                OutdoorDistances.distanceMeters(
                    nodos[i].lat, nodos[i].lng,
                    nodos[i + 1].lat, nodos[i + 1].lng
                )
            } else {
                0.0
            }
            stepDistances.add(dist)
            totalDistance += dist
        }

        // Estimación de tiempo a velocidad peatonal estándar (~1.2 m/s o 4.3 km/h)
        val estimatedSeconds = if (totalDistance > 0) totalDistance / 1.2 else 0.0

        val isEnglish = java.util.Locale.getDefault().language.equals("en", ignoreCase = true)
        val steps = nodos.mapIndexed { index, nodo ->
            val stepInstruction = if (isEnglish && !nodo.instruccionEn.isNullOrBlank()) {
                nodo.instruccionEn
            } else {
                nodo.instruccion
            }
            WalkingStep(
                instruction = stepInstruction,
                distanceMeters = stepDistances[index],
                maneuver = mapAccionToManeuver(nodo.accion),
                photo = nodo.foto,
                heading = nodo.rumbo,
                ejeXCamino = nodo.ejeXCamino,
                nodeName = nodo.nombre,
                nodeId = nodo.id,
                point = GeoPoint(nodo.lat, nodo.lng)
            )
        }

        return WalkingRoute(
            points = points,
            distanceMeters = totalDistance,
            durationSeconds = estimatedSeconds,
            steps = steps
        )
    }

    /**
     * Obtiene la ruta de ida o vuelta según el origen y destino seleccionados.
     */
    fun getRouteForDestination(
        context: Context,
        destinationId: String?,
        origin: GeoPoint?
    ): WalkingRoute? {
        val data = getGuidanceData(context) ?: return null

        // Si el destino es específicamente el Edificio Auxiliar, guiamos de ETSIIT Principal -> Auxiliar (IDA)
        if (destinationId != null && destinationId.contains("auxiliar", ignoreCase = true)) {
            return tramoToWalkingRoute(data.ida)
        }

        // Para todo el resto de guiados hacia la ETSIIT (comedor, despacho, aula, vestíbulo, etc.):
        // Guiar desde el Edificio Auxiliar hasta la ETSIIT Principal (VUELTA)
        return tramoToWalkingRoute(data.vuelta)
    }

    private fun mapAccionToManeuver(accion: String): Maneuver {
        return when (accion.uppercase()) {
            "INICIO" -> Maneuver.DEPART
            "RECTO" -> Maneuver.STRAIGHT
            "GIRO_IZQUIERDA" -> Maneuver.LEFT
            "GIRO_DERECHA" -> Maneuver.RIGHT
            "GIRO_LIGERO_DERECHA", "SLIGHT_RIGHT" -> Maneuver.SLIGHT_RIGHT
            "GIRO_LIGERO_IZQUIERDA", "SLIGHT_LEFT" -> Maneuver.SLIGHT_LEFT
            "CRUCE" -> Maneuver.STRAIGHT
            "LLEGADA" -> Maneuver.ARRIVAL
            else -> Maneuver.UNKNOWN
        }
    }
}
