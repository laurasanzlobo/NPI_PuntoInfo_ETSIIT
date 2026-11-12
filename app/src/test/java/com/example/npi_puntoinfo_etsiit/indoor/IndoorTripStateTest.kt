package com.example.npi_puntoinfo_etsiit.indoor

import com.example.npi_puntoinfo_etsiit.Nodo
import com.example.npi_puntoinfo_etsiit.RutaCalculada
import org.junit.Assert.*
import org.junit.Test

class IndoorTripStateTest {

    @Test
    fun testIndoorTripLifecycleAndStepProgression() {
        val nodo1 = Nodo("n1", "Vestibulo", "PB", "ETSIIT", "PASILLO", 100, 100)
        val nodo2 = Nodo("n2", "Escalera_PB", "PB", "ETSIIT", "ESCALERA", 200, 100)
        val nodo3 = Nodo("n3", "Aula_0_1", "PB", "ETSIIT", "AULA", 300, 100)

        val ruta = RutaCalculada(nodo1, nodo3, listOf(nodo1, nodo2, nodo3), 20, 15)
        var trip = IndoorTripState.buildFromRoute("test_trip_01", ruta)

        assertEquals("test_trip_01", trip.tripId)
        assertEquals(2, trip.steps.size)
        assertEquals(0, trip.currentStepIndex)
        assertFalse(trip.isCompleted)

        // Probar avance de tramos mediante la acción manual de siguiente paso
        trip = trip.nextStepManual()
        assertEquals(1, trip.currentStepIndex)
        assertFalse(trip.isCompleted)

        // Probar completado del recorrido al llegar al último tramo
        trip = trip.nextStepManual()
        assertTrue(trip.isCompleted)

        // Probar retroceso manual de tramo (vuelve del tramo 1 al tramo 0)
        trip = trip.previousStepManual()
        assertFalse(trip.isCompleted)
        assertEquals(0, trip.currentStepIndex)
    }
}
