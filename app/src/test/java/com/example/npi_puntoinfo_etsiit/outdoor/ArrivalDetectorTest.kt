package com.example.npi_puntoinfo_etsiit.outdoor

import org.junit.Assert.*
import org.junit.Test
import com.example.npi_puntoinfo_etsiit.sensors.OutdoorDistances
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.asin
import kotlin.math.atan2

class ArrivalDetectorTest {
    private fun twoReadingDetector() = ArrivalDetector(ArrivalPolicy(allowSingleReading = false))

    @Test fun freshTwoSecondUpdatesConfirmAtProximityBoundary() {
        val detector = ArrivalDetector()
        assertEquals(150.0, detector.policy.radiusMeters, 0.0)
        assertEquals(100.0, detector.policy.maxAccuracyMeters, 0.0)
        assertFalse(detector.observe(150.0, 5.0, 100_000, 100_050))
        assertTrue(detector.observe(150.0, 5.0, 102_000, 102_070))
        assertEquals(100_000L, detector.firstValidFixMillis)
        assertEquals(102_070L, detector.confirmedAtMillis)
    }

    @Test fun oneRecentUsefulFixAt100Or50MetersDoesNotWaitForAnotherReading() {
        listOf(100.0, 50.0).forEach { distance ->
            val detector = ArrivalDetector()
            assertTrue(detector.observe(distance, 10.0, 100_000, 100_050))
            assertEquals(100_000L, detector.firstValidFixMillis)
            assertEquals(100_050L, detector.confirmedAtMillis)
        }
    }

    @Test fun degradedIndoorAccuracyCanConfirmWithOneRecentUsefulFix() {
        listOf(30.0, 60.0, 100.0).forEach { accuracy ->
            assertTrue(ArrivalDetector().observe(100.0, accuracy, 100_000, 125_000))
        }
    }

    @Test fun uncertaintyExtendsBoundaryProportionallyButRequiresTwoReadings() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(175.0, 60.0, 100_000))
        assertTrue(detector.observe(175.0, 60.0, 102_000))
        val preciseOutside = ArrivalDetector()
        repeat(3) { assertFalse(preciseOutside.observe(175.0, 10.0, it * 2_000L)) }
    }

    @Test fun uncertaintyHasHardDistanceAndAccuracyLimits() {
        listOf(200.1, 250.0, 500.0, 5_000.0).forEach { distance ->
            val detector = ArrivalDetector()
            repeat(3) { assertFalse(detector.observe(distance, 100.0, it * 2_000L)) }
        }
        val atLimit = ArrivalDetector()
        assertFalse(atLimit.observe(200.0, 100.0, 100_000))
        assertTrue(atLimit.observe(200.0, 100.0, 102_000))
        val tooInaccurate = ArrivalDetector()
        repeat(3) { assertFalse(tooInaccurate.observe(0.0, 100.1, it * 2_000L)) }
    }

    @Test fun recentCacheConfirmsImmediatelyButOlderUsefulFixNeedsCorroboration() {
        assertTrue(ArrivalDetector().observe(50.0, 40.0, 100_000, 130_000))
        val detector = ArrivalDetector()
        assertFalse(detector.observe(50.0, 40.0, 100_000, 140_000))
        assertTrue(detector.observe(50.0, 40.0, 102_000, 142_000))
    }

    @Test fun simulatedInteriorAndSurroundingsAreCoveredInEveryDirection() {
        // Puntos sintéticos relativos al acceso del catálogo; no representan un perímetro medido.
        val access = OutdoorDestinations.find("etsiit")!!.point
        val lat = Math.toRadians(access.latitude)
        val lon = Math.toRadians(access.longitude)
        listOf(0.0, 50.0, 100.0, 150.0).forEach { meters ->
            (0 until 360 step 45).forEach { degrees ->
                val bearing = Math.toRadians(degrees.toDouble())
                val angular = meters / 6_371_008.8
                val pointLat = asin(sin(lat) * cos(angular) + cos(lat) * sin(angular) * cos(bearing))
                val pointLon = lon + atan2(sin(bearing) * sin(angular) * cos(lat),
                    cos(angular) - sin(lat) * sin(pointLat))
                val distance = OutdoorDistances.distanceMeters(access.latitude, access.longitude,
                    Math.toDegrees(pointLat), Math.toDegrees(pointLon))
                val detector = ArrivalDetector()
                val first = detector.observe(distance, 40.0, 100_000)
                assertTrue("Distancia=$meters rumbo=$degrees", first || detector.observe(distance, 40.0, 102_000))
            }
        }
    }

    @Test fun explicitTwoReadingPolicyStillWorks() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(10.0, 5.0, 100_000, 100_050))
        assertTrue(detector.observe(10.0, 5.0, 102_000, 102_070))
        assertEquals(100_000L, detector.firstValidFixMillis)
        assertEquals(102_070L, detector.confirmedAtMillis)
    }

    @Test fun repeatedCachedFixCannotBecomeTwoReadingsWithDifferentObservationTimes() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(10.0, 5.0, 100_000, 100_000))
        assertFalse(detector.observe(10.0, 5.0, 100_000, 102_000))
        assertFalse(detector.observe(10.0, 5.0, 99_000, 104_000))
        assertTrue(detector.observe(10.0, 5.0, 104_000, 104_010))
    }

    @Test fun staleOrFutureFixCannotContributeEvidence() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(10.0, 5.0, 0, 60_001))
        assertFalse(detector.observe(10.0, 5.0, 30_000, 20_000))
        assertNull(detector.firstValidFixMillis)
        assertFalse(detector.observe(10.0, 5.0, 20_000, 20_010))
        assertTrue(detector.observe(10.0, 5.0, 22_000, 22_010))
    }

    @Test fun lowAccuracyBetweenFastFixesRequiresTwoNewReliableFixes() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        assertFalse(detector.observe(10.0, 101.0, 2_000))
        assertFalse(detector.observe(10.0, 5.0, 4_000))
        assertTrue(detector.observe(10.0, 5.0, 6_000))
        assertEquals(4_000L, detector.firstValidFixMillis)
    }

    @Test fun missingTimestampOrUnavailableLocationInterruptsEvidence() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(10.0, 5.0, 100_000))
        assertFalse(detector.observe(Double.NaN, Double.NaN, -1, 102_000))
        assertFalse(detector.observe(10.0, 5.0, 104_000))
        assertTrue(detector.observe(10.0, 5.0, 106_000))
    }

    @Test fun frequentFixesStillNeedMinimumTemporalSeparation() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        (1..19).forEach { assertFalse(detector.observe(10.0, 5.0, it * 100L)) }
        assertTrue(detector.observe(10.0, 5.0, 2_000))
    }

    @Test fun approachingFrequencyChangesBeforeArrivalButDoesNotChangeArrivalPolicy() {
        assertEquals(12_000L, ActiveLocationPolicy.interval(500.0, 10.0))
        assertEquals(2_000L, ActiveLocationPolicy.interval(140.0, 10.0))
        assertEquals(12_000L, ActiveLocationPolicy.interval(Double.NaN, 10.0))
        assertTrue(ArrivalDetector().observe(140.0, 10.0, 0))
    }
    @Test fun twoReliableSeparatedReadingsAreRequired() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(15.0, 8.0, 0))
        assertFalse(detector.observe(15.0, 8.0, 1_000))
        assertTrue(detector.observe(15.0, 8.0, 12_000))
    }

    @Test fun unusableAccuracyNeverConfirmsArrival() {
        val detector = ArrivalDetector()
        repeat(10) { assertFalse(detector.observe(0.0, 150.0, it * 12_000L)) }
    }

    @Test fun inaccurateFixAtBoundaryStillNeedsTwoReadings() {
        val detector = ArrivalDetector()
        assertFalse(detector.observe(150.0, 80.0, 0))
        assertTrue(detector.observe(150.0, 80.0, 2_000))
    }

    @Test fun invalidOrUnavailableFixInterruptsEvidence() {
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { invalid ->
            val detector = twoReadingDetector()
            assertFalse(detector.observe(10.0, 5.0, 0))
            assertFalse(detector.observe(invalid, 5.0, 12_000))
            assertFalse(detector.observe(10.0, 5.0, 24_000))
            assertTrue(detector.observe(10.0, 5.0, 36_000))
        }
    }

    @Test fun unreliableAccuracyInterruptsEvidence() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        assertFalse(detector.observe(10.0, Double.NaN, 12_000))
        assertFalse(detector.observe(10.0, 5.0, 24_000))
    }

    @Test fun movingAwayRequiresNewEvidence() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        assertFalse(detector.observe(500.0, 5.0, 12_000))
        assertFalse(detector.observe(10.0, 5.0, 24_000))
        assertTrue(detector.observe(10.0, 5.0, 36_000))
    }

    @Test fun returningAfterLongPauseDoesNotUseOldFix() {
        val detector = twoReadingDetector()
        assertFalse(detector.observe(10.0, 5.0, 0))
        assertFalse(detector.observe(10.0, 5.0, 60_000))
        assertTrue(detector.observe(10.0, 5.0, 72_000))
    }

    @Test fun decliningArrivalNeverRepeatsDuringSameJourneyEvenAfterLeavingArea() {
        val detector = ArrivalDetector()
        assertTrue(detector.observe(10.0, 5.0, 0))
        assertFalse(detector.observe(10.0, 5.0, 12_000))
        assertFalse(detector.observe(200.0, 5.0, 24_000))
        repeat(10) { assertFalse(detector.observe(10.0, 5.0, 36_000L + it * 12_000)) }
    }

    @Test fun savedNotificationSuppressesPromptAfterRecreation() {
        val detector = ArrivalDetector(alreadyNotified = true)
        repeat(3) { assertFalse(detector.observe(0.0, 5.0, it * 12_000L)) }
    }

    @Test fun policyCanBeConfiguredCentrally() {
        val detector = ArrivalDetector(ArrivalPolicy(radiusMeters = 20.0, exitRadiusMeters = 40.0,
            requiredReadings = 3, allowSingleReading = false))
        assertFalse(detector.observe(5.0, 5.0, 0))
        assertFalse(detector.observe(5.0, 5.0, 12_000))
        assertTrue(detector.observe(5.0, 5.0, 24_000))
    }

    @Test fun unavailableOrInvalidPositionNeverInventsArrival() {
        val detector = ArrivalDetector()
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { distance ->
            assertFalse(detector.observe(distance, 10.0, 100_000))
        }
        listOf(Double.NaN, Double.POSITIVE_INFINITY, -1.0).forEach { accuracy ->
            assertFalse(detector.observe(50.0, accuracy, 102_000))
        }
        assertFalse(detector.observe(50.0, 10.0, -1, 104_000))
        assertFalse(detector.observe(50.0, 10.0, 0, 60_001))
        assertFalse(detector.observe(50.0, 10.0, 110_000, 104_000))
        assertFalse(detector.notified)
        assertTrue(detector.observe(50.0, 80.0, 104_000))
    }
}
