package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.GrafoETSIIT
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class OutdoorRoutingTest {
    private fun graph() = GrafoETSIIT().apply {
        cargarDesdeJsonLocal(File("src/main/assets/grafo_etsiit_completo.json").absolutePath)
    }

    @Test fun onlyFixedRouteIdCanStartTheSavedItinerary() {
        val graph = graph()
        assertEquals(OutdoorEntry.FixedRoute, OutdoorEntryPolicy.resolve(graph, EtsiitAuxiliaryRoute.destinationId))
        listOf("etsiit", "dining-pts", "dining-cartuja", "dining-fuentenueva", "unknown", null).forEach {
            assertEquals(OutdoorEntry.Unavailable, OutdoorEntryPolicy.resolve(graph, it))
        }
        assertEquals(OutdoorEntry.Unavailable, OutdoorEntryPolicy.resolve(graph,
            EtsiitAuxiliaryRoute.destinationId, "Biblioteca"))
    }

    @Test fun professorClassroomAndDiningKeepTheirIndoorTargetAcrossBothModesAndRestoration() {
        val graph = graph()
        listOf("Biblioteca", "A_0.1", "D_1.1").forEach { name ->
            val node = graph.buscarNodoPorDestino(name) ?: return@forEach
            val result = OutdoorEntryPolicy.resolve(graph, "etsiit", name, node.planta, node.id)
            assertTrue(result is OutdoorEntry.EtsiitTarget)
            val indoor = (result as OutdoorEntry.EtsiitTarget).indoor
            assertEquals(node.id, indoor.target.nodeId)
            assertNotNull(graph.calcularRutaCompleta(indoor.entryNodeId, indoor.target.nodeId))
            val trip = NavigationTrip.startFixedOutdoor(indoor).selectIndoorMode()
            assertFalse(trip.arrivalNotified)
            val restored = NavigationTrip.restore(trip.selectOutdoorMode().savedValues()).selectIndoorMode()
            assertEquals(indoor, restored.indoor)
            assertEquals(TripPhase.INDOOR, restored.phase)
            assertFalse(restored.arrivalNotified)
        }
        val dining = OutdoorEntryPolicy.resolve(graph, "dining-aynadamar") as OutdoorEntry.EtsiitTarget
        assertEquals("Comedor_PSS", graph.obtenerNodoPorId(dining.indoor.target.nodeId)!!.nombre)
        assertNull(NavigationTrip.startFixedOutdoor().indoor)
        assertEquals(EtsiitAuxiliaryRoute.destinationId, NavigationTrip.startFixedOutdoor().destinationId)
    }

    @Test fun noCartographicOrRemoteRouteCodeOrDependencyRemains() {
        val source = File("src/main/java").walkTopDown().filter { it.extension == "kt" }.joinToString("\n") { it.readText() }
        listOf("api.openrouteservice.org", "tiles.openfreemap.org", "org.maplibre", "HttpsRouteTransport", "WalkingRouteSource").forEach {
            assertFalse("Proveedor externo encontrado: $it", source.contains(it))
        }
        assertFalse(File("build.gradle.kts").readText().contains("org.maplibre"))
        assertFalse(File("build.gradle.kts").readText().contains("ORS_API_KEY"))
    }

    @Test fun destinationArgumentsAreStillSafe() {
        assertEquals("outdoor?destinationId=auxiliary-etsiit", OutdoorNavigationRoute.create(EtsiitAuxiliaryRoute.destinationId))
        assertTrue(OutdoorNavigationRoute.forProfessor("D_1 & ñ", "P1").contains("%26"))
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectQueryInjection() { OutdoorNavigationRoute.create("id&destination=Aula") }
}
