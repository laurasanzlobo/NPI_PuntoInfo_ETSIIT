package com.example.npi_puntoinfo_etsiit

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.npi_puntoinfo_etsiit.sensors.*
import com.example.npi_puntoinfo_etsiit.ui.components.AutomaticOutdoorEntry
import kotlinx.coroutines.flow.flow
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutomaticOutdoorEntryTest {
    @get:Rule val compose = createComposeRule()

    @Test fun guidanceDetectionOpensOutdoorsAndKeepsBothModeButtonsAvailable() {
        var openings = 0
        val reader = GpsLocationReader(object : DeviceLocationSource {
            override fun permission() = LocationPermission.PRECISE
            override fun isLocationEnabled() = true
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = null
            override fun updates(permission: LocationPermission, intervalMillis: Long) = flow {
                for (i in 0..3) emit(DeviceCoordinates(37.20, -3.64, 5f, 1_000L + i * 2_000))
            }
        })
        compose.setContent { MaterialTheme {
            KeepTestScreenAwake()
            AutomaticOutdoorEntry(false, {}, { openings++ }, {}, {}, gpsReader = reader,
                nowMillis = { 10_000L })
        } }
        compose.runOnIdle { assertEquals(1, openings) }
        compose.onNodeWithTag("open-indoor-guidance").assertIsDisplayed()
        compose.onNodeWithTag("open-outdoor-guidance").assertIsDisplayed()
    }

    @Test fun simulatedOutsideOpensGuidanceOnceAndManualIndoorOverridePreventsReopening() {
        var subscriptions = 0
        var openings = 0
        var manual by mutableStateOf(true)
        val reader = GpsLocationReader(object : DeviceLocationSource {
            override fun permission() = LocationPermission.PRECISE
            override fun isLocationEnabled() = true
            override suspend fun currentLocation(permission: LocationPermission): DeviceCoordinates? = null
            override fun updates(permission: LocationPermission, intervalMillis: Long) = flow {
                subscriptions++
                // Coordenada ajena al edificio principal; no representa un waypoint del itinerario.
                for (i in 0..4) emit(DeviceCoordinates(37.20, -3.64, 5f, 1_000L + i * 2_000))
            }
        })
        compose.setContent { MaterialTheme {
            KeepTestScreenAwake()
            AutomaticOutdoorEntry(manual, onAutomatic = { manual = false }, onOutside = { openings++ },
                onOpenOutdoor = {}, onOpenIndoor = { manual = true }, gpsReader = reader, nowMillis = { 10_000L })
        } }
        compose.onNodeWithText("Guiado interior seleccionado").assertIsDisplayed()
        compose.runOnIdle { assertEquals(0, subscriptions); assertEquals(0, openings) }
        compose.onNodeWithText("Detectar ubicación").performClick()
        compose.runOnIdle { assertEquals(1, subscriptions); assertEquals(1, openings); manual = true }
        compose.runOnIdle { assertEquals(1, openings) }
        compose.onNodeWithText("Guiado interior seleccionado").assertIsDisplayed()
    }
}
