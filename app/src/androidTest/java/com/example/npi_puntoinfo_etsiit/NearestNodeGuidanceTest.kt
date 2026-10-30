package com.example.npi_puntoinfo_etsiit

import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.npi_puntoinfo_etsiit.outdoor.EtsiitAuxiliaryRoute
import com.example.npi_puntoinfo_etsiit.sensors.*
import com.example.npi_puntoinfo_etsiit.ui.screens.OutdoorNavigationScreen
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.flow.flow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Regresión del atasco: una sola fijación, sin corredor estrecho ni tres muestras iniciales. */
@RunWith(AndroidJUnit4::class)
class NearestNodeGuidanceTest {
    @get:Rule val compose = createComposeRule()

    private fun showWithSingleFix(fix: DeviceCoordinates) {
        val reader = GpsLocationReader(object : DeviceLocationSource {
            override fun permission() = LocationPermission.PRECISE
            override fun isLocationEnabled() = true
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = null
            override fun updates(permission: LocationPermission, intervalMillis: Long) = flow {
                emit(fix)
                awaitCancellation() // No llegará una segunda ni tercera muestra para desbloquearlo.
            }
        })
        compose.setContent { MaterialTheme {
            KeepTestScreenAwake()
            OutdoorNavigationScreen("nearest-node-test", {}, {}, gpsReader = reader, nowMillis = { 1_000L })
        } }
    }

    @Test fun oneFixWithTwentyFiveMeterAccuracyAndLateralOffsetActivatesCorrectInstruction() {
        val point = EtsiitAuxiliaryRoute.route.waypoints[1].pathToNext[5]
        showWithSingleFix(DeviceCoordinates(point.latitude + 20.0 / 111_195.0802335, point.longitude, 25f, 1_000))
        compose.onNodeWithText("Paso 2 de 6").assertIsDisplayed()
        compose.onNodeWithTag("waypoint-eduardo-molina").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText(EtsiitAuxiliaryRoute.route.waypoints[1].continuationInstruction!!).assertIsDisplayed()
        compose.onNodeWithText("Esperando la posición del móvil…").assertDoesNotExist()
        compose.onNodeWithTag("open-indoor-guidance").assertIsDisplayed()
    }

    @Test fun poorAccuracyShowsNearestStepWithWarningRatherThanEndlessSearch() {
        val point = EtsiitAuxiliaryRoute.route.waypoints[3].point!!
        showWithSingleFix(DeviceCoordinates(point.latitude, point.longitude, 120f, 1_000))
        compose.onNodeWithText("Paso 4 de 6").assertIsDisplayed()
        compose.onNodeWithTag("waypoint-rafael-gomez").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText("Ubicación poco precisa", substring = true).assertIsDisplayed()
    }

    @Test fun invalidFixExplainsTheProblemAndManualControlsStillWork() {
        showWithSingleFix(DeviceCoordinates(Double.NaN, 0.0, 5f, 1_000))
        compose.onNodeWithText("Ubicación no disponible · usa el avance manual").assertIsDisplayed()
        compose.onNodeWithText("Esperando la posición del móvil…").assertDoesNotExist()
        compose.onNodeWithText("Siguiente paso").performClick()
        compose.onNodeWithText("Paso 2 de 6").assertIsDisplayed()
        compose.onNodeWithText("Avance manual · no confirmado por GPS").assertIsDisplayed()
    }
}
