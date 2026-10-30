package com.example.npi_puntoinfo_etsiit

import android.Manifest
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.npi_puntoinfo_etsiit.data.ScheduleRepository
import com.example.npi_puntoinfo_etsiit.outdoor.EtsiitAuxiliaryRoute
import com.example.npi_puntoinfo_etsiit.sensors.*
import com.example.npi_puntoinfo_etsiit.ui.navigation.AppNavigation
import com.example.npi_puntoinfo_etsiit.ui.screens.loadProfessors
import com.example.npi_puntoinfo_etsiit.ui.theme.NPI_PuntoInfo_ETSIITTheme
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/** Solo entradas a Guiado y cambios de modo, con GPS inyectado (no depende de dónde esté el teléfono). */
@RunWith(AndroidJUnit4::class)
class OutdoorNavigationIntegrationTest {
    @get:Rule val compose = createComposeRule()
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext
    private val restoration by lazy { StateRestorationTester(compose) }
    private var subscriptions = 0

    private val reader = GpsLocationReader(object : DeviceLocationSource {
        override fun permission() = LocationPermission.PRECISE
        override fun isLocationEnabled() = true
        override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = null
        override fun updates(permission: LocationPermission, intervalMillis: Long) = flow {
            subscriptions++
            val point = EtsiitAuxiliaryRoute.route.waypoints[1].pathToNext[5]
            repeat(4) { emit(DeviceCoordinates(point.latitude, point.longitude, 2f, 1_000L + it * 2_000)) }
            awaitCancellation()
        }
    })

    @Before fun allowDiningLocation() {
        // La pantalla de comedores usa su propio proveedor antes de pulsar «¡Llévame!».
        val automation = InstrumentationRegistry.getInstrumentation().uiAutomation
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_COARSE_LOCATION)
        automation.grantRuntimePermission(context.packageName, Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private fun showApp() {
        restoration.setContent { NPI_PuntoInfo_ETSIITTheme {
            KeepTestScreenAwake()
            AppNavigation(guidanceGpsReader = reader, guidanceNowMillis = { 8_000L })
        } }
    }

    private fun waitForOutdoor() {
        compose.waitUntil(10_000) {
            runCatching { compose.onAllNodesWithTag("outdoor-screen").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false)
        }
        compose.onNodeWithTag("open-indoor-guidance").assertIsDisplayed()
        compose.onNodeWithTag("open-outdoor-guidance").assertIsDisplayed()
    }

    @Test fun homeDoesNotSubscribeOrRedirectButGuidanceStartsAtActualPositionAndKeepsManualMode() {
        showApp()
        compose.mainClock.advanceTimeBy(8_000)
        compose.runOnIdle { assertEquals(0, subscriptions) }
        compose.onNodeWithTag("outdoor-screen").assertDoesNotExist()
        compose.onNodeWithTag("open-outdoor-guidance").assertDoesNotExist()
        compose.onNodeWithText("Guiado").performClick()
        waitForOutdoor()
        compose.onNodeWithText("Paso 2 de 6").assertIsDisplayed()
        compose.onNodeWithTag("waypoint-eduardo-molina").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText(EtsiitAuxiliaryRoute.route.waypoints[1].continuationInstruction!!).assertIsDisplayed()
        compose.onNodeWithText("Siguiente paso").performClick()
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithText("Paso 3 de 6").assertIsDisplayed()
        compose.onNodeWithTag("open-indoor-guidance").performClick()
        compose.onNodeWithTag("indoor-screen").assertIsDisplayed()
        compose.onNodeWithText("Guiado interior seleccionado").assertIsDisplayed()
        val before = subscriptions
        compose.mainClock.advanceTimeBy(8_000)
        compose.runOnIdle { assertEquals(before, subscriptions) }
        compose.onNodeWithTag("open-outdoor-guidance").performClick()
        waitForOutdoor()
        compose.onNodeWithText("Paso 3 de 6").assertIsDisplayed()
        compose.onNodeWithTag("open-indoor-guidance").performClick()
        // Una nueva petición explícita desde la pestaña vuelve a comprobar la posición real.
        compose.onNodeWithText("Guiado").performClick()
        waitForOutdoor()
        compose.onNodeWithText("Paso 2 de 6").assertIsDisplayed()
    }

    @Test fun professorRequestChecksOutsideAndPreservesOfficeThroughModeSwitches() {
        val graph = GrafoRepository.getGrafo(context)
        val professor = loadProfessors(context).first {
            graph.buscarNodoPorDestino(it.office.substringBefore("·").trim()) != null
        }
        showApp()
        compose.onNodeWithText("Profesores").performClick()
        compose.onNode(hasScrollToIndexAction()).performScrollToNode(hasText(professor.name))
        compose.onNodeWithText(professor.name).performClick()
        compose.onNodeWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").performClick()
        waitForOutdoor()
        compose.onNodeWithText("Destino interior:", substring = true).assertTextContains(professor.name, substring = true)
        compose.onNodeWithTag("open-indoor-guidance").performClick()
        compose.onNodeWithTag("indoor-screen").assertIsDisplayed()
        compose.onAllNodesWithText(professor.name, substring = true).onFirst().assertIsDisplayed()
        compose.onNodeWithTag("open-outdoor-guidance").performClick()
        waitForOutdoor()
        compose.onNodeWithText("Destino interior:", substring = true).assertTextContains(professor.name, substring = true)
    }

    @Test fun scheduleRequestChecksOutsideAndKeepsItsClassroom() {
        val today = LocalDate.now()
        val offset = (0..6).first {
            ScheduleRepository.getClassesForDay(context = context, dayOfWeek = today.plusDays(it.toLong()).dayOfWeek).isNotEmpty()
        }
        val item = ScheduleRepository.getClassesForDay(context = context, dayOfWeek = today.plusDays(offset.toLong()).dayOfWeek).first()
        val room = item.room.substringBefore("·").trim()
        showApp()
        compose.onNodeWithText("Horario").performClick()
        if (offset > 0) compose.onNode(hasScrollToIndexAction()).performScrollToIndex(offset)
        compose.onAllNodesWithText(item.subject, substring = true).onFirst().performClick()
        compose.onNodeWithContentDescription("Gesto «¡Llévame!»: Giro de muñeca").performClick()
        waitForOutdoor()
        compose.onNodeWithText("Destino interior:", substring = true).assertTextContains(room, substring = true)
        compose.onNodeWithTag("open-indoor-guidance").performClick()
        compose.onNodeWithTag("indoor-screen").assertIsDisplayed()
        compose.onNodeWithTag("open-outdoor-guidance").assertIsDisplayed()
    }

    @Test fun aynadamarRequestChecksOutsideAndPreservesDiningTarget() {
        showApp()
        compose.onNodeWithText("Comedores").performClick()
        compose.onAllNodesWithText("Comedor más cercano", substring = true).onFirst().performScrollTo().performClick()
        compose.onNode(hasText("Comedor Aynadamar") and hasAnyAncestor(isDialog())).performClick()
        waitForOutdoor()
        compose.onNodeWithText("Destino interior:", substring = true).assertTextContains("Comedor Aynadamar", substring = true)
        compose.onNodeWithTag("open-indoor-guidance").performClick()
        compose.onNodeWithTag("indoor-screen").assertIsDisplayed()
        compose.onNodeWithText("Comedor Aynadamar", substring = true).assertIsDisplayed()
    }
}
