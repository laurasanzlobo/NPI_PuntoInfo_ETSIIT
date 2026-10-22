package com.example.npi_puntoinfo_etsiit

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.npi_puntoinfo_etsiit.ui.navigation.Screen
import com.example.npi_puntoinfo_etsiit.data.ScheduleRepository
import com.example.npi_puntoinfo_etsiit.outdoor.EtsiitAccess
import com.example.npi_puntoinfo_etsiit.outdoor.IndoorTarget
import com.example.npi_puntoinfo_etsiit.ui.screens.Professor
import com.example.npi_puntoinfo_etsiit.ui.screens.loadProfessors
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Prueba del callback compartido por el icono y el gesto; no simula un movimiento físico. */
@RunWith(AndroidJUnit4::class)
class OutdoorNavigationIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()
    private var gpsOfferedEntry = false

    @Test fun diningTakeMeThereOpensOutdoorAndIndoorTabRemainsSeparate() {
        compose.onNodeWithText("Comedores").performClick()
        openDiningNavigation()
        waitForOutdoor()
        compose.onNodeWithTag("outdoor-screen").assertIsDisplayed()
        dismissOptionalConsent()
        compose.onNodeWithText("Mapa exterior").assertIsDisplayed()
        compose.onNodeWithText("Comedor Aynadamar · Planta -1").assertIsDisplayed()
        compose.onNodeWithText("ETSIIT", substring = false).performClick()
        compose.onNodeWithText("Comedor PTS").performClick()
        compose.onNodeWithText("Comedor PTS").assertIsDisplayed()
        compose.onNodeWithContentDescription("Atrás").performClick()
        compose.onNodeWithTag("outdoor-screen").assertDoesNotExist()
        compose.onNodeWithText("Desliza horizontalmente para consultar otros días").assertIsDisplayed()
        compose.onNodeWithText("Guiado").performClick()
        compose.onNodeWithTag("outdoor-screen").assertDoesNotExist()
        compose.onNodeWithText("Mapa ETSIIT").assertIsDisplayed()
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
        val (professor, target) = existingProfessorTarget()
        compose.onNodeWithText("Profesores").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(professor.name))
        compose.onNodeWithText(professor.name).performClick()
        compose.onNodeWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").performClick()
        waitForOutdoor()
        dismissOptionalConsent()
        compose.onNodeWithText("Información de navegación").assertDoesNotExist()
        if (!gpsOfferedEntry) compose.onNodeWithText("Ya estoy dentro").assertDoesNotExist()
        openManualEntry()
        compose.onNodeWithText("Aún estoy fuera").performClick()
        compose.onNodeWithTag("outdoor-screen").assertIsDisplayed()
        compose.activityRule.scenario.recreate()
        waitForOutdoor()
        compose.onNodeWithText("${target.name} · ${target.location}").assertIsDisplayed()
        compose.onNodeWithText("Ya estoy dentro").performClick()
        compose.onNodeWithTag("outdoor-screen").assertDoesNotExist()
        compose.onNode(hasText(target.name) or hasText("Despacho ${target.name.removePrefix("D_").removePrefix("D-")}", substring = true)).assertIsDisplayed()
        compose.onNodeWithText(target.location).assertIsDisplayed()
        compose.onNodeWithText("Volver al exterior").performClick()
        compose.onNodeWithTag("outdoor-screen").assertIsDisplayed()
        repeat(3) {
            compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
            compose.onNodeWithText("Ya estoy dentro").performClick()
            compose.onNode(hasText(target.name) or hasText("Despacho ${target.name.removePrefix("D_").removePrefix("D-")}", substring = true)).assertIsDisplayed()
            compose.onNodeWithText("Volver al exterior").performClick()
        }
        compose.activityRule.scenario.recreate()
        compose.onNodeWithTag("compact-indoor-entry").assertIsDisplayed()
        compose.onNodeWithContentDescription("Información y privacidad").assertIsDisplayed()
    }

    @Test fun selectingOtherCampusCancelsProfessorHandoff() {
        val (professor, _) = existingProfessorTarget()
        compose.onNodeWithText("Profesores").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(professor.name))
        compose.onNodeWithText(professor.name).performClick()
        compose.onNodeWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").performClick()
        waitForOutdoor()
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
            compose.onAllNodesWithText(item.subject, substring = true).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onAllNodesWithText(item.subject, substring = true).onFirst().performClick()
        compose.onNodeWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").performClick()
        waitForOutdoor()
        dismissOptionalConsent()
        compose.onNodeWithText("$name · $location").assertIsDisplayed()
        compose.onNodeWithText("Información de navegación").assertDoesNotExist()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        if (!gpsOfferedEntry) compose.onNodeWithTag("compact-indoor-entry").assertDoesNotExist()
        compose.activityRule.scenario.recreate()
        waitForOutdoor()
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
        openDiningNavigation()
        waitForOutdoor()
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
        openDiningNavigation()
        waitForOutdoor()
        dismissOptionalConsent()
        if (!gpsOfferedEntry) compose.onNodeWithTag("compact-indoor-entry").assertDoesNotExist()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        compose.onNodeWithText("Comedor Aynadamar · Planta -1").assertIsDisplayed()
    }

    @Test fun cancellingIndoorTripClearsHandoffAndNextDiningTripHasNoPreviousArrivalState() {
        compose.onNodeWithText("Comedores").performClick()
        openDiningNavigation()
        waitForOutdoor()
        dismissOptionalConsent()
        openManualEntry()
        compose.onNodeWithText("Continuar dentro").performClick()
        compose.onNodeWithContentDescription("Cancelar ruta").performClick()
        compose.onNodeWithText("Volver al exterior").assertDoesNotExist()
        compose.onNodeWithText("Comedores").performClick()
        openDiningNavigation()
        waitForOutdoor()
        dismissOptionalConsent()
        if (!gpsOfferedEntry) compose.onNodeWithTag("compact-indoor-entry").assertDoesNotExist()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
    }

    private fun openDiningNavigation() {
        compose.onAllNodesWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").onFirst()
            .performScrollTo().assertIsDisplayed().performClick()
    }

    private fun waitForOutdoor() {
        // Al añadir/quitar el diálogo puede no haber raíces de Compose durante un fotograma.
        compose.waitUntil(10_000) {
            runCatching { compose.onNodeWithTag("outdoor-screen").isDisplayed() }.getOrDefault(false)
        }
    }

    private fun existingProfessorTarget(): Pair<Professor, IndoorTarget> {
        val graph = GrafoRepository.getGrafo(compose.activity)
        return loadProfessors(compose.activity).firstNotNullOf { professor ->
            val name = professor.office.substringBefore("·").trim()
            val floor = professor.office.substringAfter("·", "ETSIIT").trim()
            val location = if (floor.isNotBlank() && floor != "ETSIIT") "${professor.name} · $floor" else professor.name
            EtsiitAccess.journey(graph, name, location)?.let { professor to it.indoor.target }
        }
    }

    private fun openManualEntry() {
        if (compose.onNodeWithTag("manual-indoor-entry").isDisplayed()) {
            compose.onNodeWithTag("manual-indoor-entry").performClick()
        } else {
            compose.onNodeWithContentDescription("Información y privacidad").performClick()
            compose.onNodeWithText("Continuar dentro manualmente").performClick()
        }
    }

    private fun dismissOptionalConsent() {
        compose.waitForIdle()
        val dismiss = compose.onNodeWithText("Ahora no")
        if (dismiss.isDisplayed()) dismiss.performClick()
        // La ubicación real o simulada del dispositivo puede proponer la entrada sin una ruta ORS.
        compose.waitUntil(25_000) {
            runCatching {
                compose.onAllNodes(hasText("Ubicación", substring = true) or
                    hasText("Sin permiso de ubicación", substring = true) or
                    hasText("La ubicación del teléfono", substring = true)).fetchSemanticsNodes().isNotEmpty()
            }.getOrDefault(false)
        }
        compose.waitForIdle()
        gpsOfferedEntry = compose.onNodeWithText("Estás llegando a la ETSIIT").isDisplayed()
        if (gpsOfferedEntry) compose.onNodeWithText("Aún estoy fuera").performClick()
    }
}
