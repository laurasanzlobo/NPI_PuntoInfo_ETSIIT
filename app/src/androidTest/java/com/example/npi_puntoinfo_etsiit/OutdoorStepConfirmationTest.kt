package com.example.npi_puntoinfo_etsiit

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.npi_puntoinfo_etsiit.outdoor.*
import com.example.npi_puntoinfo_etsiit.sensors.*
import com.example.npi_puntoinfo_etsiit.ui.screens.OutdoorNavigationScreen
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.atomic.AtomicInteger

/** Movimiento GPS sintético controlado: prueba el flujo real de pantalla → navegador → motor. */
@RunWith(AndroidJUnit4::class)
class OutdoorStepConfirmationTest {
    @get:Rule val compose = createComposeRule()
    private val fixes = Channel<DeviceCoordinates>(Channel.UNLIMITED)
    private val delivered = AtomicInteger()
    private var expected = 0
    private var time = 1_000L
    private var now = 1_000L
    private fun point(x: Double, y: Double) = GeoPoint(y / 111_195.0802335, x / 111_195.0802335)
    private val route = FixedOutdoorRoute("confirmation-test", "Solo prueba sintética", listOf(
        RouteWaypoint("start", point(0.0, 0.0), "Continúa hasta el giro simulado", Maneuver.DEPART,
            pathToNext = listOf(0.0, 25.0, 50.0, 75.0, 100.0).map { point(it, 0.0) }),
        RouteWaypoint("turn", point(100.0, 0.0), "Gira hacia el destino simulado", Maneuver.LEFT,
            pathToNext = listOf(0.0, 25.0, 50.0, 75.0, 100.0).map { point(100.0, it) }),
        RouteWaypoint("arrival", point(100.0, 100.0), "Fin del recorrido simulado", Maneuver.ARRIVAL)
    ), true)

    private fun show() {
        val reader = GpsLocationReader(object : DeviceLocationSource {
            override fun permission() = LocationPermission.PRECISE
            override fun isLocationEnabled() = true
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = null
            override fun updates(permission: LocationPermission, intervalMillis: Long) = flow {
                for (fix in fixes) { emit(fix); delivered.incrementAndGet() }
            }
        })
        compose.setContent { MaterialTheme {
            KeepTestScreenAwake()
            OutdoorNavigationScreen("confirmation-trip", {}, {}, route = route,
                gpsReader = reader, nowMillis = { now })
        } }
    }

    private fun send(x: Double, y: Double) {
        val position = point(x, y)
        compose.runOnIdle {
            now = time
            check(fixes.trySend(DeviceCoordinates(position.latitude, position.longitude, 2f, time)).isSuccess)
            time += 2_000
            expected++
        }
        compose.waitUntil(5_000) { delivered.get() >= expected }
        compose.waitForIdle()
    }

    private fun approachTurn() {
        show()
        send(0.0, 0.0)
        compose.onNodeWithTag("outdoor-step-confirmation").assertDoesNotExist()
        for (x in 5..100 step 5) send(x.toDouble(), 0.0)
        compose.onNodeWithTag("outdoor-step-confirmation").assertIsDisplayed()
        compose.onNodeWithText("¿Pasar al siguiente paso?").assertIsDisplayed()
        compose.onNodeWithTag("waypoint-start").assertIsSelected()
        compose.onNodeWithTag("waypoint-turn").assertIsNotSelected()
    }

    @Test fun detectsMovementAndChangesHighlightedInstructionOnlyAfterConfirmation() {
        approachTurn()
        compose.onNode(hasText("Gira hacia el destino simulado") and hasAnyAncestor(isDialog())).assertIsDisplayed()
        compose.onNodeWithTag("confirm-outdoor-step").performClick()
        compose.onNodeWithTag("outdoor-step-confirmation").assertDoesNotExist()
        compose.onNodeWithText("Paso 2 de 3").assertIsDisplayed()
        compose.onNodeWithTag("waypoint-turn").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText("Avance manual · no confirmado por GPS").assertDoesNotExist()
    }

    @Test fun decliningKeepsCurrentInstructionAndDoesNotRepeatPopupForSameNode() {
        approachTurn()
        compose.onNodeWithText("Ahora no").performClick()
        repeat(6) { send(100.0, 0.0) }
        compose.onNodeWithTag("outdoor-step-confirmation").assertDoesNotExist()
        compose.onNodeWithText("Paso 1 de 3").assertIsDisplayed()
        compose.onNodeWithTag("waypoint-start").assertIsSelected()
    }

    @Test fun confirmingOneStepKeepsTrackingAndAlsoAsksBeforeMarkingArrival() {
        approachTurn()
        compose.onNodeWithTag("confirm-outdoor-step").performClick()
        for (y in 5..100 step 5) send(100.0, y.toDouble())
        repeat(3) { send(100.0, 100.0) }
        compose.onNodeWithText("¿Confirmar llegada a la ETSIIT?").assertIsDisplayed()
        compose.onNodeWithTag("waypoint-turn").assertIsSelected()
        compose.onNodeWithTag("confirm-outdoor-step").performClick()
        compose.onNodeWithText("Paso 3 de 3").assertIsDisplayed()
        compose.onNodeWithText("Has llegado a la ETSIIT", substring = true).assertIsDisplayed()
    }
}
