package com.example.npi_puntoinfo_etsiit

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.PriorityQueue
import kotlin.math.roundToInt

// 1. Data Classes que calcan la estructura del archivo JSON
data class Nodo(
    val id: String,
    val nombre: String,
    val planta: String,
    val edificio: String,
    val tipo: String,
    val x: Int,
    val y: Int
)

data class AristaJson(
    val origen: String,
    val destino: String,
    val distancia_metros: Int
)

data class EstructuraJson(
    val nodos: List<Nodo>,
    val aristas: List<AristaJson>
)

// Data class interna para el cálculo de Dijkstra
data class AristaDijkstra(val destinoId: String, val distancia: Int)

/**
 * Modelo que agrupa el resultado completo del cálculo de Dijkstra
 */
data class RutaCalculada(
    val origen: Nodo,
    val destino: Nodo,
    val nodos: List<Nodo>,
    val distanciaMetros: Int,
    val tiempoEstimadoSegundos: Int
) {
    val tiempoEstimadoFormateado: String
        get() {
            val minutos = (distanciaMetros / 70.0).roundToInt().coerceAtLeast(1)
            return if (minutos <= 1) "~1 min a pie" else "~$minutos min a pie"
        }
}

class GrafoETSIIT {
    private val conexiones = mutableMapOf<String, MutableList<AristaDijkstra>>()
    private val directorioNodos = mutableMapOf<String, Nodo>()

    // =========================================================================
    // FUNCIÓN A: Para usar dentro de la app Android
    // =========================================================================
    fun cargarDesdeAssets(context: Context, nombreArchivo: String) {
        val jsonString = context.assets.open(nombreArchivo).bufferedReader().use { it.readText() }
        procesarJson(jsonString)
    }

    // =========================================================================
    // FUNCIÓN B: Para usar en pruebas locales en mi ordenador (Scratch / Main)
    // =========================================================================
    fun cargarDesdeJsonLocal(rutaAbsoluta: String) {
        val jsonString = File(rutaAbsoluta).readText()
        procesarJson(jsonString)
    }

    // Función auxiliar oculta que hace el trabajo sucio para no repetir código
    private fun procesarJson(jsonString: String) {
        val datos = Gson().fromJson(jsonString, EstructuraJson::class.java)

        datos.nodos.forEach { nodo ->
            directorioNodos[nodo.id] = nodo
            if (!conexiones.containsKey(nodo.id)) {
                conexiones[nodo.id] = mutableListOf()
            }
        }

        datos.aristas.forEach { arista ->
            agregarArista(arista.origen, arista.destino, arista.distancia_metros)
        }

        println("¡Grafo cargado! ${directorioNodos.size} nodos procesados.")
    }

    fun buscarIdPorNombre(nombreBuscado: String): String? {
        // Busca en todos los nodos y nos devuelve el ID del que coincida con el nombre
        return directorioNodos.values.find { it.nombre == nombreBuscado }?.id
    }

    fun obtenerNodoPorId(id: String): Nodo? {
        return directorioNodos[id]
    }

    /**
     * Nodo de inicio predeterminado (Entrada Principal Exterior en Planta Baja)
     */
    fun obtenerNodoInicioPredeterminado(): Nodo {
        return directorioNodos.values.find { it.nombre.equals("Entrada_Exterior", ignoreCase = true) }
            ?: directorioNodos.values.find { it.nombre.equals("Vestibulo", ignoreCase = true) }
            ?: directorioNodos.values.first()
    }

    /**
     * Busca de manera flexible un nodo a partir del texto de destino de la aplicación
     * (soporta aulas como "Aula 0.1", comedores como "Comedor", y despachos como "D-08" o "2.01").
     */
    fun buscarNodoPorDestino(query: String): Nodo? {
        if (query.isBlank()) return null
        val normalizado = query.trim().lowercase()

        // 1. Destinos temáticos directos
        if (normalizado.contains("comedor")) {
            return directorioNodos.values.find { it.nombre.equals("Comedor_PSS", ignoreCase = true) }
                ?: directorioNodos.values.find { it.nombre.contains("comedor", ignoreCase = true) }
        }
        if (normalizado.contains("cafeteria") || normalizado.contains("cafetería")) {
            return directorioNodos.values.find { it.nombre.equals("Cafeteria_PSS", ignoreCase = true) }
        }
        if (normalizado.contains("biblioteca")) {
            return directorioNodos.values.find { it.nombre.equals("Biblioteca", ignoreCase = true) }
                ?: directorioNodos.values.find { it.nombre.contains("biblioteca", ignoreCase = true) }
        }
        if (normalizado.contains("salon") || normalizado.contains("salón") || normalizado.contains("actos")) {
            return directorioNodos.values.find { it.nombre.equals("Salon_Actos_PSS", ignoreCase = true) }
        }

        // 2. Aulas con número decimal o estándar: ej. "Aula 0.1", "0.1", "A01", "A-01", "A_01", "1.2"
        val regexAula = Regex("""(?:aula\s*)?([0-4])\.(\d+)""", RegexOption.IGNORE_CASE)
        val matchAula = regexAula.find(normalizado)
        if (matchAula != null) {
            val planta = matchAula.groupValues[1]
            val num = matchAula.groupValues[2]
            val candidatos = listOf("A_${planta}${num}", "A_${planta}0${num}")
            for (cand in candidatos) {
                val nodo = directorioNodos.values.find { it.nombre.equals(cand, ignoreCase = true) }
                if (nodo != null) return nodo
            }
        }

        // 3. Aulas de sótano
        if (normalizado.contains("-1") || normalizado.contains("menos 1") || normalizado.contains("menos1")) {
            return directorioNodos.values.find { it.nombre.equals("A_Menos1", ignoreCase = true) }
        }
        if (normalizado.contains("-2") || normalizado.contains("menos 2") || normalizado.contains("menos2")) {
            return directorioNodos.values.find { it.nombre.equals("A_Menos2", ignoreCase = true) }
        }

        // 4. Despachos: ej. "Despacho D-08", "D-08", "D08", "D_201", "2.01", "D 208"
        val regexDespacho = Regex("""(?:despacho\s*)?(?:d[-_\s]?)?(\d{1,3})""", RegexOption.IGNORE_CASE)
        val matchDespacho = regexDespacho.find(normalizado)
        if (matchDespacho != null) {
            val numStr = matchDespacho.groupValues[1]
            val candidatas = listOf(
                "D_$numStr",
                "D_${numStr.padStart(3, '0')}",
                "D_2${numStr.padStart(2, '0')}",
                "D_3${numStr.padStart(2, '0')}",
                "D_4${numStr.padStart(2, '0')}"
            )
            for (c in candidatas) {
                val nodo = directorioNodos.values.find { it.nombre.equals(c, ignoreCase = true) }
                if (nodo != null) return nodo
            }
        }

        // 5. Coincidencia exacta por nombre
        val exacto = directorioNodos.values.find { it.nombre.equals(query.trim(), ignoreCase = true) }
        if (exacto != null) return exacto

        // 6. Contiene subcadena
        val parcial = directorioNodos.values.find {
            it.nombre.contains(query.trim(), ignoreCase = true) ||
            query.trim().contains(it.nombre, ignoreCase = true)
        }
        if (parcial != null) return parcial

        // 7. Fallback seguro: primera aula o servicio
        return directorioNodos.values.firstOrNull { it.tipo == "aula" || it.tipo == "despacho" }
    }

    /**
     * Calcula la distancia total en metros acumulando las aristas consecutivas del camino
     */
    fun calcularDistanciaRuta(ruta: List<Nodo>): Int {
        if (ruta.size < 2) return 0
        var total = 0
        for (i in 0 until ruta.size - 1) {
            val origen = ruta[i].id
            val destino = ruta[i + 1].id
            val arista = conexiones[origen]?.find { it.destinoId == destino }
            total += arista?.distancia ?: 0
        }
        return total
    }

    /**
     * Ejecuta Dijkstra y devuelve el objeto RutaCalculada con origen, destino, nodos y distancia
     */
    fun calcularRutaCompleta(origenId: String, destinoId: String): RutaCalculada? {
        val origen = directorioNodos[origenId] ?: return null
        val destino = directorioNodos[destinoId] ?: return null
        val nodos = calcularRutaDijkstra(origenId, destinoId)
        if (nodos.isEmpty()) return null
        val distancia = calcularDistanciaRuta(nodos)
        val tiempoSegundos = (distancia / 1.2).toInt()
        return RutaCalculada(origen, destino, nodos, distancia, tiempoSegundos)
    }

    // La función principal de Dijkstra
    fun calcularRutaDijkstra(origenId: String, destinoId: String): List<Nodo> {
        val distancias = mutableMapOf<String, Int>().withDefault { Int.MAX_VALUE }
        val nodosPrevios = mutableMapOf<String, String?>()
        val colaPrioridad = PriorityQueue<Pair<Int, String>>(compareBy { it.first })

        distancias[origenId] = 0
        colaPrioridad.add(Pair(0, origenId))

        while (colaPrioridad.isNotEmpty()) {
            val nodoExtraido = colaPrioridad.poll() ?: break
            val distanciaActual = nodoExtraido.first
            val nodoActual = nodoExtraido.second

            if (nodoActual == destinoId) break

            val vecinos = conexiones[nodoActual] ?: continue

            for (arista in vecinos) {
                val nuevaDistancia = distanciaActual + arista.distancia
                val distanciaConocida = distancias.getValue(arista.destinoId)

                if (nuevaDistancia < distanciaConocida) {
                    distancias[arista.destinoId] = nuevaDistancia
                    nodosPrevios[arista.destinoId] = nodoActual
                    colaPrioridad.add(Pair(nuevaDistancia, arista.destinoId))
                }
            }
        }

        val secuenciaOrdenada = mutableListOf<Nodo>()
        var actual: String? = destinoId

        while (actual != null) {
            directorioNodos[actual]?.let { secuenciaOrdenada.add(it) }
            actual = nodosPrevios[actual]
        }

        return secuenciaOrdenada.reversed()
    }

    private fun agregarArista(origenId: String, destinoId: String, distancia: Int) {
        conexiones[origenId]?.add(AristaDijkstra(destinoId, distancia))
        conexiones[destinoId]?.add(AristaDijkstra(origenId, distancia))
    }
}

/**
 * Singleton repositorio para cargar el grafo de la ETSIIT una única vez en memoria
 */
object GrafoRepository {
    private var instance: GrafoETSIIT? = null

    fun getGrafo(context: Context): GrafoETSIIT {
        return instance ?: synchronized(this) {
            instance ?: GrafoETSIIT().apply {
                cargarDesdeAssets(context, "grafo_etsiit_completo.json")
            }.also { instance = it }
        }
    }
}
