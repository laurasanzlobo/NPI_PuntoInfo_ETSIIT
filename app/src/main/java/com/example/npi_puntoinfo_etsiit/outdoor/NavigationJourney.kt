package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.GrafoETSIIT

data class IndoorTarget(val name: String, val location: String, val nodeId: String)

sealed interface NavigationJourney {
    data class Outdoor(val destination: OutdoorDestination) : NavigationJourney
    data class Indoor(val target: IndoorTarget, val entryNodeId: String) : NavigationJourney
    data class Combined(val destination: OutdoorDestination, val indoor: Indoor) : NavigationJourney
}

/** Solo el acceso PB definido por el grafo. No se equipara con Entrada_Exterior_P1. */
object EtsiitAccess {
    const val outdoorId = "etsiit"
    const val nodeName = "Entrada_Exterior"

    fun journey(graph: GrafoETSIIT, name: String, location: String, targetNodeId: String? = null): NavigationJourney.Combined? {
        // El resolver histórico acepta cualquier texto que contenga «comedor».
        // En la integración GPS solo está acreditado el alias de develop para Aynadamar.
        if (name.contains("comedor", ignoreCase = true) && name.trim().lowercase() !in
            setOf("comedor", "comedor aynadamar", "comedor_pss")) return null
        val entryId = graph.buscarIdPorNombre(nodeName) ?: return null
        val target = (if (targetNodeId != null) graph.obtenerNodoPorId(targetNodeId)
            else graph.buscarNodoPorDestino(name)) ?: return null
        if (name.contains("comedor", ignoreCase = true) && target.nombre != "Comedor_PSS") return null
        if (graph.calcularRutaCompleta(entryId, target.id) == null) return null
        return NavigationJourney.Combined(OutdoorDestinations.find(outdoorId)!!,
            NavigationJourney.Indoor(IndoorTarget(name, location, target.id), entryId))
    }

    /** Correspondencia explícita de DiningScreen y voz en develop, no por proximidad ni por nombre parcial. */
    fun diningJourney(graph: GrafoETSIIT, diningId: String): NavigationJourney.Combined? {
        if (diningId != "dining-aynadamar") return null
        val nodeId = graph.buscarIdPorNombre("Comedor_PSS") ?: return null
        return journey(graph, "Comedor Aynadamar", "Planta -1", nodeId)
    }

    /** Clasifica los nodos existentes; una consulta de aula sin resolver no se convierte en despacho. */
    fun isClassroom(graph: GrafoETSIIT, name: String): Boolean {
        val node = graph.buscarNodoPorDestino(name)
        return node?.nombre?.let { it.startsWith("A_") || it.startsWith("L_") } == true ||
            Regex("""^(aula|clase|laboratorio|lab)\b""", RegexOption.IGNORE_CASE).containsMatchIn(name.trim())
    }
}

/** Un cambio de marcador cancela el tramo interior, nunca lo traslada a otro campus. */
fun NavigationJourney.forOutdoorSelection(id: String): NavigationJourney? {
    val destination = OutdoorDestinations.find(id) ?: return null
    return if (this is NavigationJourney.Combined && this.destination.id == id) this
    else NavigationJourney.Outdoor(destination)
}
