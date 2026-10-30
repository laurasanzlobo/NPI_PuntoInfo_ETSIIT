package com.example.npi_puntoinfo_etsiit

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.npi_puntoinfo_etsiit.outdoor.*
import com.example.npi_puntoinfo_etsiit.ui.components.IndoorArrivalDialog
import com.example.npi_puntoinfo_etsiit.ui.screens.OutdoorGuidanceContent
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class IndoorArrivalPresentationTest {
    @get:Rule val compose = createComposeRule()

    @Test fun allInstructionsRemainVisibleInTheListAndActiveOneIsHighlightedAndScrolled() {
        val tracker = FixedRouteTracker(EtsiitAuxiliaryRoute.route).apply { start() }
        var state by mutableStateOf(tracker.state)
        var indoorClicks = 0
        compose.setContent { MaterialTheme {
            KeepTestScreenAwake()
            OutdoorGuidanceContent(tracker.route, state, false,
                onStart = { tracker.start(); state = tracker.state },
                onNext = { tracker.next(); state = tracker.state },
                onPrevious = { tracker.previous(); state = tracker.state },
                onRestart = { tracker.restart(); tracker.start(); state = tracker.state }, onGps = {}, onFinish = {},
                onOpenIndoors = { indoorClicks++ },
                onConfirmSuggestedStep = { tracker.confirmSuggestedStep(); state = tracker.state },
                onDismissSuggestedStep = { tracker.dismissSuggestedStep(); state = tracker.state })
        } }
        tracker.route.waypoints.forEach { compose.onNodeWithTag("waypoint-${it.id}").assertExists() }
        compose.onNodeWithTag("waypoint-auxiliary-access").assertIsSelected()
        repeat(2) { compose.onNodeWithText("Siguiente paso").performClick() }
        compose.onNodeWithTag("waypoint-roundabout").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithTag("waypoint-auxiliary-access").assertExists().assertIsNotSelected()
        compose.onNodeWithTag("waypoint-principal-access").assertExists()
        compose.onNodeWithTag("open-indoor-guidance").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(1, indoorClicks) }
        compose.onNodeWithTag("indoor-arrival-dialog").assertDoesNotExist()
        compose.onNodeWithText("Continuar dentro").assertDoesNotExist()
        compose.onNodeWithText("Paso anterior").performClick()
        compose.onNodeWithTag("waypoint-eduardo-molina").assertIsSelected().assertIsDisplayed()
        compose.onNodeWithText("Reiniciar recorrido").performClick()
        compose.onNodeWithText("Paso 1 de 6").assertIsDisplayed()
    }

    @Test fun validatedManualArrivalIsExplicitlyNotGpsArrival() {
        val route = FixedOutdoorRoute("synthetic-ui", "Solo prueba sintética", listOf(
            RouteWaypoint("a", GeoPoint(0.0, 0.0), "Salida simulada", Maneuver.DEPART),
            RouteWaypoint("b", GeoPoint(0.0, 0.001), "Destino simulado", Maneuver.ARRIVAL)
        ), true)
        val tracker = FixedRouteTracker(route).apply { start(); next() }
        compose.setContent { MaterialTheme {
            KeepTestScreenAwake()
            OutdoorGuidanceContent(route, tracker.state, false, {}, {}, {}, {}, {}, {}, {}, {}, {})
        } }
        compose.onNodeWithText("Llegada a la ETSIIT marcada manualmente.").assertIsDisplayed()
        compose.onNodeWithText("Progreso detectado mediante GPS").assertDoesNotExist()
    }

    @Test fun existingIndoorConfirmationComponentStillRequiresExplicitAction() {
        var transitions = 0
        compose.setContent { MaterialTheme {
            KeepTestScreenAwake()
            IndoorArrivalDialog(false, onContinue = { transitions++ }, onStayOutside = {})
        } }
        compose.runOnIdle { assertEquals(0, transitions) }
        compose.onNodeWithText("Continuar dentro").performClick()
        compose.runOnIdle { assertEquals(1, transitions) }
    }
}
