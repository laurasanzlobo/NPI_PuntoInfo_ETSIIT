package com.example.npi_puntoinfo_etsiit.outdoor

import org.junit.Assert.*
import org.junit.Test

class ArrivalPresentationTest {
    @Test fun waitsForBothReliableReadingsBeforeShowingPromptOnce() {
        val detector = ArrivalDetector()
        var presentation = ArrivalPresentation.WAITING
        if (detector.observe(10.0, 5.0, 0)) presentation = presentation.onArrival(true)
        assertEquals(ArrivalPresentation.WAITING, presentation)
        if (detector.observe(10.0, 5.0, 12_000)) presentation = presentation.onArrival(true)
        assertEquals(ArrivalPresentation.PROMPT, presentation)
        assertFalse(detector.observe(10.0, 5.0, 24_000))
    }

    @Test fun inaccurateReadingsNeverShowPrompt() {
        val detector = ArrivalDetector()
        var presentation = ArrivalPresentation.WAITING
        repeat(5) {
            if (detector.observe(10.0, 60.0, it * 12_000L)) presentation = presentation.onArrival(true)
        }
        assertEquals(ArrivalPresentation.WAITING, presentation)
    }

    @Test fun declineHidesModalAndEnablesCompactButtonWithoutReopening() {
        val presentation = ArrivalPresentation.WAITING.onArrival(true).defer()
        assertEquals(ArrivalPresentation.DEFERRED, presentation)
        assertEquals(presentation, presentation.onArrival(true))
    }

    @Test fun compactButtonUsesSameConfirmedTransition() {
        assertEquals(ArrivalPresentation.PROMPT.continueIndoors(), ArrivalPresentation.DEFERRED.continueIndoors())
        assertEquals(ArrivalPresentation.CONTINUED, ArrivalPresentation.DEFERRED.continueIndoors())
    }

    @Test fun outdoorOnlyDestinationNeverShowsIndoorPrompt() {
        assertEquals(ArrivalPresentation.WAITING, ArrivalPresentation.WAITING.onArrival(false))
    }

    @Test fun confirmedJourneyDoesNotPromptAgain() {
        assertEquals(ArrivalPresentation.CONTINUED, ArrivalPresentation.CONTINUED.onArrival(true))
    }

    @Test fun decliningManualEntryAlsoSuppressesFutureAutomaticPrompt() {
        assertEquals(ArrivalPresentation.DEFERRED, ArrivalPresentation.WAITING.defer().onArrival(true))
    }
}
