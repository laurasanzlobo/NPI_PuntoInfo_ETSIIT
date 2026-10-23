package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.*
import com.google.gson.Gson
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class IndoorRegressionTest {
    private val asset = File("src/main/assets/grafo_etsiit_completo.json")
    private fun graph() = GrafoETSIIT().apply { cargarDesdeJsonLocal(asset.absolutePath) }

    @Test fun aynadamarRecoversExactDevelopNodeAndReachableRouteFromPb() {
        val graph = graph()
        val combined = EtsiitAccess.diningJourney(graph, "dining-aynadamar")!!
        val node = graph.obtenerNodoPorId(combined.indoor.target.nodeId)!!
        assertEquals("Comedor_PSS", node.nombre)
        assertEquals("P-1", node.planta)
        assertEquals("despachos", node.edificio)
        assertEquals(graph.buscarNodoPorDestino("Comedor Aynadamar")!!.id, node.id)
        val route = graph.calcularRutaCompleta(combined.indoor.entryNodeId, node.id)!!
        assertEquals("Entrada_Exterior", route.origen.nombre)
        assertEquals(node.id, route.destino.id)
        assertEquals("etsiit", combined.destination.id)
        assertNotEquals(OutdoorDestinations.find("dining-aynadamar")!!.point, combined.destination.point)
    }

    @Test fun otherDiningHallsDoNotUsePermissiveHistoricalComedorResolver() {
        val graph = graph()
        listOf("fuentenueva", "cartuja", "pts", "unknown").forEach { id ->
            assertNull(EtsiitAccess.diningJourney(graph, "dining-$id"))
        }
        listOf("Comedor PTS", "Comedor Cartuja", "Comedor Fuentenueva", "Comedor desconocido").forEach {
            assertNull(EtsiitAccess.journey(graph, it, ""))
        }
        assertNull(EtsiitAccess.diningJourney(GrafoETSIIT(), "dining-aynadamar"))
        assertNull(EtsiitAccess.journey(graph, "Comedor Aynadamar", "",
            graph.buscarNodoPorDestino("Biblioteca")!!.id))
    }

    @Test fun allPreviouslyReachableNodesRemainReachableThroughHandoffWithExplicitIdentity() {
        val graph = graph()
        val data = Gson().fromJson(asset.readText(), EstructuraJson::class.java)
        val entry = graph.obtenerNodoInicioPredeterminado()
        var checked = 0
        data.nodos.forEach { node ->
            val original = graph.calcularRutaCompleta(entry.id, node.id)
            if (original != null) {
                val combined = EtsiitAccess.journey(graph, node.nombre, node.planta, node.id)!!
                val indoor = NavigationTrip.start(combined).enterIndoors().indoor!!
                val actual = graph.calcularRutaCompleta(indoor.entryNodeId, indoor.target.nodeId)!!
                assertEquals(original.nodos, actual.nodos)
                assertEquals(original.distanciaMetros, actual.distanciaMetros)
                checked++
            }
        }
        assertTrue(checked > 100)
    }

    @Test fun voiceServicesOfficesAndScheduleClassroomsPreserveDevelopResolvedDestination() {
        val graph = graph()
        listOf("comedor", "cafetería", "biblioteca", "salón de actos", "secretaría", "conserjería",
            "Alberto Ruiz", "Roberto Pérez", "María Gómez", "aula cero uno", "aula menos uno",
            "laboratorio dos uno").forEach { query ->
            val result = graph.resolverDestinoPorVoz(query) as ResultadoVoz.Exito
            val original = result.destino
            val combined = EtsiitAccess.journey(graph, original.titulo, original.ubicacion, original.nodo.id)!!
            val trip = NavigationTrip.start(combined).enterIndoors().returnOutdoors().enterIndoors()
            assertEquals(original.nodo.id, trip.indoor!!.target.nodeId)
            assertEquals(original.titulo, trip.indoor.target.name)
            assertEquals(original.ubicacion, trip.indoor.target.location)
        }
    }

    @Test fun explicitVoiceDiningCampusIsResolvedBeforeHistoricalGenericComedorAlias() {
        assertEquals("dining-pts", OutdoorDestinations.diningNamedIn("llévame al comedor PTS")?.id)
        assertEquals("dining-cartuja", OutdoorDestinations.diningNamedIn("comedor de Cartuja")?.id)
        assertEquals("dining-aynadamar", OutdoorDestinations.diningNamedIn("Comedor Aynadamar")?.id)
        assertNull(OutdoorDestinations.diningNamedIn("quiero ir al comedor"))
    }
}
