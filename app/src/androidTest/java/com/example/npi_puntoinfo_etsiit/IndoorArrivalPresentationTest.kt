package com.example.npi_puntoinfo_etsiit

import android.content.Context
import android.content.ContextWrapper
import android.Manifest
import android.content.pm.PackageManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.npi_puntoinfo_etsiit.outdoor.ArrivalDetector
import com.example.npi_puntoinfo_etsiit.outdoor.ArrivalPresentation
import com.example.npi_puntoinfo_etsiit.outdoor.RouteConsentStore
import com.example.npi_puntoinfo_etsiit.outdoor.NavigationTrip
import com.example.npi_puntoinfo_etsiit.outdoor.NavigationJourney
import com.example.npi_puntoinfo_etsiit.outdoor.IndoorTarget
import com.example.npi_puntoinfo_etsiit.outdoor.OutdoorDestinations
import com.example.npi_puntoinfo_etsiit.outdoor.TripPhase
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import com.example.npi_puntoinfo_etsiit.ui.components.CompactIndoorEntryButton
import com.example.npi_puntoinfo_etsiit.ui.components.IndoorArrivalDialog
import com.example.npi_puntoinfo_etsiit.ui.screens.OutdoorNavigationScreen
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** Lecturas simuladas para la presentación; no valida recepción GPS ni gestos físicos. */
@RunWith(AndroidJUnit4::class)
class IndoorArrivalPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun simulatedArrivalShowsCentralPromptOnceAndKeepsCompactActionAcrossRepeatedReturns() {
        val indoor = NavigationJourney.Indoor(IndoorTarget("Comedor Aynadamar", "Planta -1", "comedor"), "entrada")
        var trip by mutableStateOf(NavigationTrip.start(
            NavigationJourney.Combined(OutdoorDestinations.find("etsiit")!!, indoor)))
        val detector = ArrivalDetector()
        compose.setContent {
            MaterialTheme {
                if (trip.phase == TripPhase.INDOOR) {
                    TextButton(onClick = { trip = trip.returnOutdoors() }) { Text("Volver al exterior") }
                } else {
                    if (trip.presentation == ArrivalPresentation.PROMPT) IndoorArrivalDialog(false,
                        onContinue = { trip = trip.markPromptShown().enterIndoors() },
                        onStayOutside = { trip = trip.markPromptShown().stayOutside() })
                    if (trip.showCompactEntry) CompactIndoorEntryButton(onClick = { trip = trip.enterIndoors() })
                }
            }
        }
        compose.runOnIdle {
            if (detector.observe(50.0, 80.0, 100_000, 125_000)) trip = trip.onArrival()
        }
        compose.onNodeWithText("Estás llegando a la ETSIIT").assertIsDisplayed()
        compose.onNodeWithText("Continuar dentro").performClick()
        compose.onNodeWithText("Volver al exterior").performClick()
        repeat(3) {
            compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
            compose.onNodeWithTag("compact-indoor-entry").assertIsDisplayed().performClick()
            compose.onNodeWithText("Volver al exterior").performClick()
            compose.runOnIdle {
                trip = NavigationTrip.restore(trip.savedValues()).onArrival()
                assertEquals(indoor, trip.indoor)
            }
        }
        compose.onNodeWithTag("compact-indoor-entry").assertIsDisplayed()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
    }

    @Test fun twoReliableReadingsShowModalThenDeclineLeavesOnlyCompactAction() {
        val detector = ArrivalDetector()
        var presentation by mutableStateOf(ArrivalPresentation.WAITING)
        var transitions = 0
        compose.setContent {
            MaterialTheme {
                if (presentation == ArrivalPresentation.PROMPT) IndoorArrivalDialog(false,
                    onContinue = { transitions++; presentation = presentation.continueIndoors() },
                    onStayOutside = { presentation = presentation.defer() })
                if (presentation == ArrivalPresentation.DEFERRED) CompactIndoorEntryButton(onClick = {
                    transitions++
                    presentation = presentation.continueIndoors()
                })
            }
        }
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        compose.runOnIdle {
            if (detector.observe(150.0, 5.0, 0)) presentation = presentation.onArrival(true)
        }
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        compose.runOnIdle {
            if (detector.observe(150.0, 5.0, 12_000)) presentation = presentation.onArrival(true)
        }
        compose.onNodeWithText("Estás llegando a la ETSIIT").assertIsDisplayed()
        compose.onNodeWithText("Continuar dentro").assertIsDisplayed()
        compose.onNodeWithText("Aún estoy fuera").performClick()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        compose.onNodeWithText("¿Quieres continuar la navegación por el interior?").assertDoesNotExist()
        compose.onNodeWithTag("compact-indoor-entry").assertIsDisplayed()
        compose.runOnIdle {
            assertEquals(0, transitions)
            if (detector.observe(10.0, 5.0, 24_000)) presentation = presentation.onArrival(true)
        }
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        compose.onNodeWithText("Ya estoy dentro").performClick()
        compose.runOnIdle { assertEquals(1, transitions) }
    }

    @Test fun primaryActionConfirmsOnlyAfterExplicitClick() {
        var transitions = 0
        compose.setContent {
            MaterialTheme { IndoorArrivalDialog(false, onContinue = { transitions++ }, onStayOutside = {}) }
        }
        compose.runOnIdle { assertEquals(0, transitions) }
        compose.onNodeWithText("Continuar dentro").performClick()
        compose.runOnIdle { assertEquals(1, transitions) }
    }

    @Test fun realOutdoorScreenOffersVisibleManualEntryWithoutGpsOrRoute() {
        // Permisos simulados mediante contexto local: no altera los ajustes del teléfono.
        val indoor = NavigationJourney.Indoor(IndoorTarget("Comedor Aynadamar", "Planta -1", "comedor"), "entrada")
        var trip by mutableStateOf(NavigationTrip.start(
            NavigationJourney.Combined(OutdoorDestinations.find("etsiit")!!, indoor)))
        var transitions = 0
        compose.setContent {
            val activityContext = LocalContext.current
            val context = remember(activityContext) {
                object : ContextWrapper(activityContext) {
                    override fun getApplicationContext(): Context = this
                    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                        if (permission in listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                            PackageManager.PERMISSION_DENIED else super.checkPermission(permission, pid, uid)
                    override fun getSharedPreferences(name: String, mode: Int) =
                        super.getSharedPreferences("${name}_proximity_test", mode)
                }
            }
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme {
                    OutdoorNavigationScreen(destinationId = trip.destinationId, trip = trip,
                        onTripChanged = { trip = it }, onSelectOutdoorDestination = {},
                        onContinueIndoors = { assertEquals(indoor, it); transitions++ })
                }
            }
        }
        compose.waitForIdle()
        if (compose.onNodeWithText("Ahora no").isDisplayed()) compose.onNodeWithText("Ahora no").performClick()
        compose.onNodeWithText("Sin permiso de ubicación. Se admite ubicación precisa o aproximada.").assertIsDisplayed()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        compose.onNodeWithTag("manual-indoor-entry").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(0, transitions) }
        compose.onNodeWithText("Aún estoy fuera").performClick()
        compose.onNodeWithTag("compact-indoor-entry").assertIsDisplayed().performClick()
        compose.runOnIdle {
            assertEquals(1, transitions)
            trip = trip.returnOutdoors()
        }
        compose.onNodeWithTag("compact-indoor-entry").assertIsDisplayed()
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
    }

    @Test fun consentIsAbsentByDefaultAndValidAcrossNewScreenStores() {
        val context = object : ContextWrapper(ApplicationProvider.getApplicationContext<Context>()) {
            override fun getSharedPreferences(name: String, mode: Int) = super.getSharedPreferences("${name}_test", mode)
        }
        val preferences = context.getSharedPreferences("outdoor_privacy", Context.MODE_PRIVATE)
        preferences.edit().clear().commit()
        try {
            assertFalse(RouteConsentStore(context).isGranted)
            RouteConsentStore(context).grant()
            assertTrue(RouteConsentStore(context).isGranted)
            preferences.edit().putInt("ors_consent_version", 0).commit()
            assertFalse(RouteConsentStore(context).isGranted)
        } finally {
            preferences.edit().clear().commit()
        }
    }
}
