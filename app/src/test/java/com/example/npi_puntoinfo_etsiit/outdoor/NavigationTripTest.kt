package com.example.npi_puntoinfo_etsiit.outdoor

import org.junit.Assert.*
import org.junit.Test

class NavigationTripTest {
    private val target = NavigationJourney.Indoor(IndoorTarget("Comedor Aynadamar", "Planta -1", "comedor"), "entrada")
    private fun start() = NavigationTrip.start(NavigationJourney.Combined(OutdoorDestinations.find("etsiit")!!, target))

    @Test fun firstArrivalRequestsPromptOnlyOnce() {
        val trip = start()
        assertFalse(trip.arrivalNotified)
        assertFalse(trip.showCompactEntry)
        val arrived = trip.onArrival()
        assertEquals(ArrivalPresentation.PROMPT, arrived.presentation)
        assertEquals(arrived, arrived.onArrival())
    }

    @Test fun shownPromptIsNotRequestedAgainAfterRecreationEvenWithoutUserAction() {
        val shown = start().onArrival().markPromptShown()
        val restored = NavigationTrip.restore(shown.savedValues())
        assertEquals(ArrivalPresentation.SHOWN, restored.onArrival().presentation)
        assertTrue(restored.showCompactEntry)
    }

    @Test fun stayOutsideKeepsCompactActionAndOriginalTarget() {
        val outside = start().onArrival().markPromptShown().stayOutside()
        assertEquals(ArrivalPresentation.DEFERRED, outside.presentation)
        assertTrue(outside.showCompactEntry)
        assertEquals(target, outside.indoor)
        assertEquals(outside, outside.onArrival())
    }

    @Test fun directContinueThenReturnKeepsRedButton() {
        val trip = start().onArrival().markPromptShown().enterIndoors()
        assertEquals(TripPhase.INDOOR, trip.phase)
        assertFalse(trip.showCompactEntry)
        val returned = trip.returnOutdoors()
        assertEquals(ArrivalPresentation.RETURNED, returned.presentation)
        assertTrue(returned.showCompactEntry)
    }

    @Test fun repeatedTransitionsAndRestorationPreserveSameTripAndNode() {
        var trip = start().stayOutside()
        val id = trip.id
        repeat(5) {
            trip = NavigationTrip.restore(trip.enterIndoors().savedValues()).returnOutdoors()
            assertEquals(id, trip.id)
            assertEquals(target, trip.indoor)
            assertTrue(trip.showCompactEntry)
            assertEquals(trip, trip.onArrival())
        }
    }

    @Test fun manualEntryBeforeGpsStillEnablesReturnWithoutAutomaticPrompt() {
        val trip = start().enterIndoors().returnOutdoors()
        assertTrue(trip.arrivalNotified)
        assertTrue(trip.showCompactEntry)
        assertEquals(trip, trip.onArrival())
    }

    @Test fun finishingDisablesActionAndNewTripNeverInheritsPreviousState() {
        val finished = start().stayOutside().finish()
        assertFalse(finished.showCompactEntry)
        assertEquals(finished, finished.enterIndoors())
        assertEquals(finished, finished.onArrival())
        val next = start()
        assertNotEquals(finished.id, next.id)
        assertEquals(ArrivalPresentation.WAITING, next.presentation)
        assertFalse(next.arrivalNotified)
        assertFalse(next.showCompactEntry)
    }

    @Test fun outdoorOnlyTripNeverOffersIndoorAction() {
        var trip = NavigationTrip.start(NavigationJourney.Outdoor(OutdoorDestinations.find("dining-pts")!!))
        trip = trip.onArrival().stayOutside().enterIndoors().returnOutdoors()
        assertNull(trip.indoor)
        assertEquals(TripPhase.OUTDOOR, trip.phase)
        assertEquals(ArrivalPresentation.WAITING, trip.presentation)
        assertFalse(trip.showCompactEntry)
        assertEquals(trip, NavigationTrip.restore(trip.savedValues()))
    }
}
