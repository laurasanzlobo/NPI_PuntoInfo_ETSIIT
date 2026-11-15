package com.example.npi_puntoinfo_etsiit.outdoor

import org.junit.Assert.*
import org.junit.Test

class GuidanceExteriorRepositoryTest {

    @Test
    fun isLocalGuidanceRoute_recognizesCampusDestinations() {
        assertTrue(GuidanceExteriorRepository.isLocalGuidanceRoute("etsiit-auxiliar"))
        assertTrue(GuidanceExteriorRepository.isLocalGuidanceRoute("edificio-auxiliar"))
        assertTrue(GuidanceExteriorRepository.isLocalGuidanceRoute("etsiit"))
        assertTrue(GuidanceExteriorRepository.isLocalGuidanceRoute("dining-aynadamar"))

        assertFalse(GuidanceExteriorRepository.isLocalGuidanceRoute("dining-fuentenueva"))
        assertFalse(GuidanceExteriorRepository.isLocalGuidanceRoute("dining-cartuja"))
        assertFalse(GuidanceExteriorRepository.isLocalGuidanceRoute("dining-pts"))
        assertFalse(GuidanceExteriorRepository.isLocalGuidanceRoute(null))
    }

    @Test
    fun outdoorDestinations_containsEdificioAuxiliar() {
        val dest = OutdoorDestinations.find("etsiit-auxiliar")
        assertNotNull(dest)
        assertEquals("Edificio Auxiliar ETSIIT", dest!!.name)
        assertEquals(37.197846, dest.point.latitude, 0.0001)
        assertEquals(-3.630084, dest.point.longitude, 0.0001)
    }

    @Test
    fun tramoToWalkingRoute_convertsNodesProperly() {
        val nodos = listOf(
            GuidanceNodo(
                id = "ext_ida_01",
                orden = 1,
                nombre = "Punto de Información",
                lat = 37.196735,
                lng = -3.624428,
                rumbo = null,
                foto = null,
                accion = "INICIO",
                instruccion = "Inicia la ruta hacia la salida.",
                ejeXCamino = 0.5
            ),
            GuidanceNodo(
                id = "ext_ida_02",
                orden = 2,
                nombre = "Puerta Principal",
                lat = 37.196873,
                lng = -3.624742,
                rumbo = 337,
                foto = "rutas/ida/ida2.jpg",
                accion = "RECTO",
                instruccion = "Sal por la puerta principal.",
                ejeXCamino = 0.57
            ),
            GuidanceNodo(
                id = "ext_ida_03",
                orden = 3,
                nombre = "Llegada Auxiliar",
                lat = 37.197846,
                lng = -3.630084,
                rumbo = 51,
                foto = "rutas/ida/ida9.jpg",
                accion = "LLEGADA",
                instruccion = "Has llegado al Edificio Auxiliar.",
                ejeXCamino = 0.566
            )
        )

        val tramo = GuidanceTramo(
            origen = "ETSIIT",
            destino = "Auxiliar",
            nodos = nodos
        )

        val route = GuidanceExteriorRepository.tramoToWalkingRoute(tramo)

        assertEquals(3, route.points.size)
        assertEquals(3, route.steps.size)
        assertTrue(route.distanceMeters > 0)
        assertTrue(route.durationSeconds != null && route.durationSeconds!! > 0)

        // Comprobar primer paso
        assertEquals(Maneuver.DEPART, route.steps[0].maneuver)
        assertEquals("Inicia la ruta hacia la salida.", route.steps[0].instruction)
        assertNull(route.steps[0].photo)

        // Comprobar segundo paso con foto
        assertEquals(Maneuver.STRAIGHT, route.steps[1].maneuver)
        assertEquals("rutas/ida/ida2.jpg", route.steps[1].photo)
        assertEquals(337, route.steps[1].heading)
        assertEquals(0.57, route.steps[1].ejeXCamino, 0.001)

        // Comprobar paso de llegada
        assertEquals(Maneuver.ARRIVAL, route.steps[2].maneuver)
        assertEquals(0.0, route.steps[2].distanceMeters, 0.001)
    }
}
