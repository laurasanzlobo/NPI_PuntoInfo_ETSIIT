package com.example.npi_puntoinfo_etsiit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.data.ScheduleRepository
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba del callback compartido por el icono y el gesto; no simula un movimiento físico. */
@RunWith(AndroidJUnit4::class)
class OutdoorNavigationIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    @Test fun diningTakeMeThereOpensOutdoorAndIndoorTabRemainsSeparate() {
        compose.onNodeWithText("Comedores").performClick()
        compose.onAllNodesWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").onFirst().performClick()
        compose.waitUntil(timeoutMillis = 10_000) {
            compose.onNodeWithTag("outdoor-screen").isDisplayed()
        }
        compose.onNodeWithTag("outdoor-screen").assertIsDisplayed()
        dismissOptionalConsent()
        compose.onNodeWithText("Mapa exterior · A pie").assertIsDisplayed()
        compose.onNodeWithText("Comedor Aynadamar · Planta -1").assertIsDisplayed()
        compose.onNodeWithText("ETSIIT", substring = false).performClick()
        compose.onNodeWithText("Comedor PTS").performClick()
        compose.onNodeWithText("Comedor PTS").assertIsDisplayed()
        compose.onNodeWithContentDescription("Atrás").performClick()
        compose.onNodeWithTag("outdoor-screen").assertDoesNotExist()
        compose.onNodeWithText("Desliza horizontalmente para consultar otros días").assertIsDisplayed()
        compose.onNodeWithText("Guiado").performClick()
        compose.onNodeWithTag("outdoor-screen").assertDoesNotExist()
        compose.onNodeWithText("Mapa").assertIsDisplayed()
    }

    @Test fun indoorRouteContractAndRealGraphAreUnchanged() {
        val route = Screen.Navigation.createRoute("Biblioteca", "ETSIIT")
        assertTrue(route.startsWith("navigation?destination="))
        assertFalse(route.startsWith("outdoor"))
        val grafo = GrafoRepository.getGrafo(compose.activity)
        val start = grafo.obtenerNodoInicioPredeterminado()
        val destination = grafo.buscarNodoPorDestino("Biblioteca")
        assertNotNull("La biblioteca interior debe seguir disponible", destination)
        val calculated = grafo.calcularRutaCompleta(start.id, destination!!.id)
        assertNotNull(calculated)
        assertTrue(calculated!!.nodos.isNotEmpty())
        assertEquals(start.id, calculated.nodos.first().id)
        assertEquals(destination.id, calculated.nodos.last().id)
    }

    @Test fun professorKeepsDestinationAndRequiresConfirmationBeforeUsingIndoorGraph() {
        compose.onNodeWithText("Profesores").performClick()
        compose.onNodeWithText("Roberto Pérez").performClick()
        compose.onNodeWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("outdoor-screen").isDisplayed() }
        dismissOptionalConsent()
        compose.onNodeWithText("Información de navegación").assertDoesNotExist()
        compose.onNodeWithText("Ya estoy dentro").assertDoesNotExist()
        openManualEntry()
        compose.onNodeWithText("Aún estoy fuera").performClick()
        compose.onNodeWithTag("outdoor-screen").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onNodeWithTag("outdoor-screen").isDisplayed() }
        compose.onNodeWithText("Despacho D-24 · Roberto Pérez · Planta 2").assertIsDisplayed()
        compose.onNodeWithText("Ya estoy dentro").performClick()
        compose.onNodeWithTag("outdoor-screen").assertDoesNotExist()
        compose.onNodeWithText("Despacho D-24").assertIsDisplayed()
        compose.onNodeWithText("Roberto Pérez · Planta 2").assertIsDisplayed()
        compose.onNodeWithText("Volver al exterior").performClick()
        compose.onNodeWithTag("outdoor-screen").assertIsDisplayed()
        repeat(3) {
            compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
            compose.onNodeWithText("Ya estoy dentro").performClick()
            compose.onNodeWithText("Despacho D-24").assertIsDisplayed()
            compose.onNodeWithText("Volver al exterior").performClick()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("compact-indoor-entry").assertIsDisplayed()
        compose.onNodeWithContentDescription("Información y privacidad").assertIsDisplayed()
    }

    @Test fun selectingOtherCampusCancelsProfessorHandoff() {
        compose.onNodeWithText("Profesores").performClick()
        compose.onNodeWithText("María Gómez").performClick()
        compose.onNodeWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("outdoor-screen").isDisplayed() }
        dismissOptionalConsent()
        compose.onNodeWithText("ETSIIT", substring = false).performClick()
        compose.onNodeWithText("Comedor PTS").performClick()
        compose.onNodeWithText("Ya estoy dentro").assertDoesNotExist()
        compose.onNodeWithText("Continuar dentro").assertDoesNotExist()
    }

    @Test fun scheduleClassroomOpensGpsAndPreservesTargetThroughRecreationAndIndoorHandoff() {
        val today = LocalDate.now()
        val offset = (0..6).first { ScheduleRepository.getClassesForDay(today.plusDays(it.toLong()).dayOfWeek).isNotEmpty() }
        val item = ScheduleRepository.getClassesForDay(today.plusDays(offset.toLong()).dayOfWeek).first()
        val name = item.room.substringBefore("·").trim()
        val location = item.room.substringAfter("·").trim()
        compose.onNodeWithText("Horario").performClick()
        if (offset > 0) compose.onNode(hasScrollToIndexAction()).performScrollToIndex(offset)
        compose.waitUntil(10_000) {
            compose.onAllNodesWithText(item.subject).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText(item.subject).onFirst().performClick()
        compose.onNodeWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("outdoor-screen").isDisplayed() }
        dismissOptionalConsent()
        compose.onNodeWithText("$name · $location").assertIsDisplayed()
        compose.onNodeWithText("Información de navegación").assertDoesNotExist()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        compose.onNodeWithTag("compact-indoor-entry").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        compose.waitUntil(10_000) { compose.onNodeWithTag("outdoor-screen").isDisplayed() }
        compose.onNodeWithText("$name · $location").assertIsDisplayed()
        openManualEntry()
        compose.onNodeWithText("Continuar dentro").performClick()
        compose.onNodeWithTag("outdoor-screen").assertDoesNotExist()
        compose.onNodeWithText(name).assertIsDisplayed()
        compose.onNodeWithText(location).assertIsDisplayed()
        compose.onNodeWithText("Volver al exterior").performClick()
        compose.onNodeWithTag("outdoor-screen").assertIsDisplayed()
        compose.onNodeWithTag("compact-indoor-entry").assertIsDisplayed()
    }

    @Test fun diningIndoorRouteSurvivesRepeatedTransitionsAndNewJourneyStartsClean() {
        compose.onNodeWithText("Comedores").performClick()
        compose.onAllNodesWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("outdoor-screen").isDisplayed() }
        dismissOptionalConsent()
        compose.onNodeWithText("Comedor Aynadamar · Planta -1").assertIsDisplayed()
        openManualEntry()
        compose.onNodeWithText("Continuar dentro").performClick()
        compose.onNodeWithText("Comedor Aynadamar").assertIsDisplayed()
        compose.onNodeWithText("Volver al exterior").performClick()
        repeat(3) {
            compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
            compose.onNodeWithText("Ya estoy dentro").performClick()
            compose.onNodeWithText("Comedor Aynadamar").assertIsDisplayed()
            compose.onNodeWithText("Volver al exterior").performClick()
        }
        compose.onNodeWithContentDescription("Atrás").performClick()
        compose.onAllNodesWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("outdoor-screen").isDisplayed() }
        dismissOptionalConsent()
        compose.onNodeWithTag("compact-indoor-entry").assertDoesNotExist()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        compose.onNodeWithText("Comedor Aynadamar · Planta -1").assertIsDisplayed()
    }

    @Test fun cancellingIndoorTripClearsHandoffAndNextDiningTripHasNoPreviousArrivalState() {
        compose.onNodeWithText("Comedores").performClick()
        compose.onAllNodesWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("outdoor-screen").isDisplayed() }
        dismissOptionalConsent()
        openManualEntry()
        compose.onNodeWithText("Continuar dentro").performClick()
        compose.onNodeWithContentDescription("Cancelar ruta").performClick()
        compose.onNodeWithText("Volver al exterior").assertDoesNotExist()
        compose.onNodeWithText("Comedores").performClick()
        compose.onAllNodesWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").onFirst().performClick()
        compose.waitUntil(10_000) { compose.onNodeWithTag("outdoor-screen").isDisplayed() }
        dismissOptionalConsent()
        compose.onNodeWithTag("compact-indoor-entry").assertDoesNotExist()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
    }

    private fun openManualEntry() {
        compose.onNodeWithContentDescription("Información y privacidad").performClick()
        compose.onNodeWithText("Continuar dentro manualmente").performClick()
    }

    private fun dismissOptionalConsent() {
        compose.waitForIdle()
        val dismiss = compose.onNodeWithText("Ahora no")
        if (dismiss.isDisplayed()) dismiss.performClick()
    }
}
