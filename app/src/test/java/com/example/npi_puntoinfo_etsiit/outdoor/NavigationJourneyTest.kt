package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.GrafoETSIIT
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.net.URI
import java.net.URLDecoder

class NavigationJourneyTest {
    private fun graph() = GrafoETSIIT().apply {
        val asset = File("src/main/assets/grafo_etsiit_completo.json")
        cargarDesdeJsonLocal(asset.absolutePath)
    }

    @Test fun allExistingProfessorMappingsUseExistingResolverAndRealRoutes() {
        val graph = graph()
        listOf("Despacho D-24", "Despacho D-12", "Despacho D-08").forEach { office ->
            val journey = EtsiitAccess.journey(graph, office, "Profesor · Planta")!!
            assertEquals("etsiit", journey.destination.id)
            assertEquals(graph.buscarNodoPorDestino(office)!!.id, journey.indoor.target.nodeId)
            val route = graph.calcularRutaCompleta(journey.indoor.entryNodeId, journey.indoor.target.nodeId)!!
            assertEquals("Entrada_Exterior", route.origen.nombre)
            assertEquals("B", route.origen.planta)
            assertEquals(journey.indoor.target.nodeId, route.nodos.last().id)
        }
    }

    @Test fun nonexistentOfficeOrMissingEntryNeverInventsNodes() {
        assertNull(EtsiitAccess.journey(graph(), "Despacho D-99999", ""))
        assertNull(EtsiitAccess.journey(GrafoETSIIT(), "Despacho D-24", ""))
    }

    @Test fun confirmationRetainsFinalNameLocationAndNode() {
        val combined = EtsiitAccess.journey(graph(), "Despacho D-24", "Roberto Pérez · Planta 2")!!
        val indoor = combined.indoor
        assertEquals("Despacho D-24", indoor.target.name)
        assertEquals("Roberto Pérez · Planta 2", indoor.target.location)
        assertNotEquals(indoor.target.nodeId, indoor.entryNodeId)
        assertEquals(combined, combined.forOutdoorSelection("etsiit"))
    }

    @Test fun differentCampusSelectionDropsIndoorTransition() {
        val combined = EtsiitAccess.journey(graph(), "Despacho D-24", "Roberto Pérez")!!
        listOf("aynadamar", "fuentenueva", "cartuja", "pts").forEach { id ->
            val next = combined.forOutdoorSelection("dining-$id")
            assertTrue(next is NavigationJourney.Outdoor)
            assertEquals("dining-$id", (next as NavigationJourney.Outdoor).destination.id)
        }
        assertNull(combined.forOutdoorSelection("missing"))
    }

    @Test fun exteriorDestinationNeverGainsIndoorTargetFromProximityOrSelection() {
        val exterior = NavigationJourney.Outdoor(OutdoorDestinations.forDiningHall("pts")!!)
        assertTrue(exterior.forOutdoorSelection("etsiit") is NavigationJourney.Outdoor)
    }

    @Test fun routeArgumentsPreserveProfessorAndOfficeWithSpecialCharacters() {
        val name = "Despacho D-08"
        val location = "Alberto Ruiz · Planta Baja & acceso + ñ"
        val route = OutdoorNavigationRoute.forProfessor(name, location)
        val params = URI("app://$route").rawQuery.split("&").associate {
            val pair = it.split("=", limit = 2)
            pair[0] to URLDecoder.decode(pair[1], "UTF-8")
        }
        assertEquals("etsiit", params["destinationId"])
        assertEquals(name, params["finalDestination"])
        assertEquals(location, params["finalLocation"])
    }

    @Test fun indoorAccessDirectFromOtherScreensStillHasExistingDefaultOrigin() {
        val graph = graph()
        val destination = graph.buscarNodoPorDestino("Biblioteca")!!
        val route = graph.calcularRutaCompleta(graph.obtenerNodoInicioPredeterminado().id, destination.id)
        assertNotNull(route)
    }

    @Test fun classroomsAndLabsKeepRealNodeAndMainEntranceAcrossHandoff() {
        val graph = graph()
        listOf("Aula -1", "Aula -2", "Aula 0.1", "Aula 1.8", "Laboratorio 2.1", "Laboratorio 3.4").forEach { name ->
            val node = graph.buscarNodoPorDestino(name)!!
            assertTrue(EtsiitAccess.isClassroom(graph, name))
            val combined = EtsiitAccess.journey(graph, name, "Ubicación original", node.id)!!
            assertEquals(node.id, combined.indoor.target.nodeId)
            assertEquals(name, combined.indoor.target.name)
            assertEquals("Ubicación original", combined.indoor.target.location)
            val route = graph.calcularRutaCompleta(combined.indoor.entryNodeId, node.id)!!
            assertEquals("Entrada_Exterior", route.nodos.first().nombre)
            assertEquals(node.id, route.nodos.last().id)
        }
    }

    @Test fun classroomArgumentsPreserveIdentityIndependentlyOfDisplayName() {
        val graph = graph()
        val node = graph.buscarNodoPorDestino("Aula 0.1")!!
        val route = OutdoorNavigationRoute.forIndoorTarget("Nombre visual & ñ", "Planta baja", node.id)
        val params = URI("app://$route").rawQuery.split("&").associate {
            val pair = it.split("=", limit = 2)
            pair[0] to URLDecoder.decode(pair[1], "UTF-8")
        }
        val combined = EtsiitAccess.journey(graph, params["finalDestination"]!!,
            params["finalLocation"]!!, params["finalNodeId"])!!
        assertEquals(node.id, combined.indoor.target.nodeId)
        assertEquals("etsiit", params["destinationId"])
    }

    @Test fun invalidExplicitNodeNeverFallsBackToAnotherClassroom() {
        assertNull(EtsiitAccess.journey(graph(), "Aula 0.1", "", "missing-node"))
    }

    @Test fun missingClassroomHasNoInventedIndoorRoute() {
        assertTrue(EtsiitAccess.isClassroom(graph(), "Aula 9.99"))
        assertNull(EtsiitAccess.journey(graph(), "Aula 9.99", ""))
    }

    @Test fun nonClassroomFeaturesKeepDirectIndoorNavigation() {
        listOf("Biblioteca", "Comedor", "Cafetería", "Despacho D-24").forEach {
            assertFalse(EtsiitAccess.isClassroom(graph(), it))
        }
    }

    @Test fun voiceClassroomUsesSameNodeAsCombinedJourney() {
        val graph = graph()
        val result = graph.resolverDestinoPorVoz("aula cero uno") as com.example.npi_puntoinfo_etsiit.ResultadoVoz.Exito
        assertTrue(EtsiitAccess.isClassroom(graph, result.destino.titulo))
        assertEquals(result.destino.nodo.id, EtsiitAccess.journey(graph, result.destino.titulo,
            result.destino.ubicacion, result.destino.nodo.id)!!.indoor.target.nodeId)
    }
}
