package com.example.npi_puntoinfo_etsiit.outdoor

import org.junit.Assert.*
import org.junit.Test

class ArrivalDetectorTest {
    @Test fun freshTwoSecondUpdatesConfirmInTwoSecondsWithoutChangingSpatialThresholds() {
        val detector = ArrivalDetector()
        assertEquals(45.0, detector.policy.radiusMeters, 0.0)
        assertEquals(25.0, detector.policy.maxAccuracyMeters, 0.0)
        assertFalse(detector.observe(10.0, 5.0, 100_000, 100_050))
        assertTrue(detector.observe(10.0, 5.0, 102_000, 102_070))
        assertEquals(100_000L, detector.firstValidFixMillis)
        assertEquals(102_070L, detector.confirmedAtMillis)
    }

    @Test fun repeatedCachedFixCannotBecomeTwoReadingsWithDifferentObservationTimes() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(10.0, 5.0, 100_000, 100_000))
        assertFalse(detector.observe(10.0, 5.0, 100_000, 102_000))
        assertFalse(detector.observe(10.0, 5.0, 99_000, 104_000))
        assertTrue(detector.observe(10.0, 5.0, 104_000, 104_010))
    }

    @Test fun staleOrFutureFixCannotContributeEvidence() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(10.0, 5.0, 0, 20_000))
        assertFalse(detector.observe(10.0, 5.0, 30_000, 20_000))
        assertNull(detector.firstValidFixMillis)
        assertFalse(detector.observe(10.0, 5.0, 20_000, 20_010))
        assertTrue(detector.observe(10.0, 5.0, 22_000, 22_010))
    }

    @Test fun lowAccuracyBetweenFastFixesRequiresTwoNewReliableFixes() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        assertFalse(detector.observe(10.0, 60.0, 2_000))
        assertFalse(detector.observe(10.0, 5.0, 4_000))
        assertTrue(detector.observe(10.0, 5.0, 6_000))
        assertEquals(4_000L, detector.firstValidFixMillis)
    }

    @Test fun missingTimestampOrUnavailableLocationInterruptsEvidence() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(10.0, 5.0, 100_000))
        assertFalse(detector.observe(Double.NaN, Double.NaN, -1, 102_000))
        assertFalse(detector.observe(10.0, 5.0, 104_000))
        assertTrue(detector.observe(10.0, 5.0, 106_000))
    }

    @Test fun frequentFixesStillNeedMinimumTemporalSeparation() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        (1..19).forEach { assertFalse(detector.observe(10.0, 5.0, it * 100L)) }
        assertTrue(detector.observe(10.0, 5.0, 2_000))
    }

    @Test fun approachingFrequencyChangesBeforeArrivalButDoesNotChangeArrivalPolicy() {
        assertEquals(12_000L, ActiveLocationPolicy.interval(500.0, 10.0))
        assertEquals(2_000L, ActiveLocationPolicy.interval(140.0, 10.0))
        assertEquals(12_000L, ActiveLocationPolicy.interval(Double.NaN, 10.0))
        assertFalse(ArrivalDetector().observe(140.0, 10.0, 0))
    }
    @Test fun twoReliableSeparatedReadingsAreRequired() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(15.0, 8.0, 0))
        assertFalse(detector.observe(15.0, 8.0, 1_000))
        assertTrue(detector.observe(15.0, 8.0, 12_000))
    }

    @Test fun poorAccuracyNeverConfirmsArrival() {
        val detector = ArrivalDetector()
        repeat(10) { assertFalse(detector.observe(0.0, 70.0, it * 12_000L)) }
    }

    @Test fun accuracyMarginMustAlsoFitInsideRadius() {
        val detector = ArrivalDetector()
        repeat(3) { assertFalse(detector.observe(35.0, 15.0, it * 12_000L)) }
    }

    @Test fun invalidOrUnavailableFixInterruptsEvidence() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { invalid ->
            val detector = ArrivalDetector()
            assertFalse(detector.observe(10.0, 5.0, 0))
            assertFalse(detector.observe(invalid, 5.0, 12_000))
            assertFalse(detector.observe(10.0, 5.0, 24_000))
            assertTrue(detector.observe(10.0, 5.0, 36_000))
        }
    }

    @Test fun unreliableAccuracyInterruptsEvidence() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        assertFalse(detector.observe(10.0, Double.NaN, 12_000))
        assertFalse(detector.observe(10.0, 5.0, 24_000))
    }

    @Test fun movingAwayRequiresNewEvidence() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        assertFalse(detector.observe(150.0, 5.0, 12_000))
        assertFalse(detector.observe(10.0, 5.0, 24_000))
        assertTrue(detector.observe(10.0, 5.0, 36_000))
    }

    @Test fun returningAfterLongPauseDoesNotUseOldFix() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        assertFalse(detector.observe(10.0, 5.0, 60_000))
        assertTrue(detector.observe(10.0, 5.0, 72_000))
    }

    @Test fun decliningArrivalNeverRepeatsDuringSameJourneyEvenAfterLeavingArea() {
        val detector = ArrivalDetector()
        detector.observe(10.0, 5.0, 0)
        assertTrue(detector.observe(10.0, 5.0, 12_000))
        assertFalse(detector.observe(200.0, 5.0, 24_000))
        repeat(10) { assertFalse(detector.observe(10.0, 5.0, 36_000L + it * 12_000)) }
    }

    @Test fun savedNotificationSuppressesPromptAfterRecreation() {
        val detector = ArrivalDetector(alreadyNotified = true)
        repeat(3) { assertFalse(detector.observe(0.0, 5.0, it * 12_000L)) }
    }

    @Test fun policyCanBeConfiguredCentrally() {
        val detector = ArrivalDetector(ArrivalPolicy(radiusMeters = 20.0, exitRadiusMeters = 40.0,
            requiredReadings = 3))
        assertFalse(detector.observe(5.0, 5.0, 0))
        assertFalse(detector.observe(5.0, 5.0, 12_000))
        assertTrue(detector.observe(5.0, 5.0, 24_000))
    }
}
