package com.example.myapplication

import android.content.Context
import com.google.gson.Gson
import java.io.File
import java.util.PriorityQueue

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

