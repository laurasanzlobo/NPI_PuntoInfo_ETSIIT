package com.example.npi_puntoinfo_etsiit.outdoor

import com.example.npi_puntoinfo_etsiit.sensors.DeviceCoordinates
import org.junit.Assert.*
import org.junit.Test

class EtsiitPresenceDetectorTest {
    private val reference = EtsiitAuxiliaryRoute.mainBuildingReference
    private fun fix(metersEast: Double, time: Long, accuracy: Float = 5f): DeviceCoordinates {
        val delta = metersEast / (111_195.0802335 * kotlin.math.cos(Math.toRadians(reference.latitude)))
        return DeviceCoordinates(reference.latitude, reference.longitude + delta, accuracy, time)
    }

    @Test fun outsideRequiresThreeIndependentRecentAccurateReadings() {
        val detector = EtsiitPresenceDetector()
        assertEquals(EtsiitPresence.UNKNOWN, detector.observe(fix(200.0, 1_000), 1_000))
        repeat(8) { assertEquals(EtsiitPresence.UNKNOWN, detector.observe(fix(200.0, 1_000), 1_000)) }
        assertEquals(EtsiitPresence.UNKNOWN, detector.observe(fix(200.0, 3_000), 3_000))
        assertEquals(EtsiitPresence.OUTSIDE, detector.observe(fix(200.0, 5_000), 5_000))
    }

    @Test fun accuracyMarginAndHysteresisPreventOscillation() {
        val detector = EtsiitPresenceDetector()
        repeat(3) { detector.observe(fix(200.0, 1_000L + it * 2_000), 1_000L + it * 2_000) }
        for (i in 0..4) assertEquals(EtsiitPresence.OUTSIDE,
            detector.observe(fix(if (i % 2 == 0) 100.0 else 135.0, 7_000L + i * 2_000), 7_000L + i * 2_000))
        repeat(3) { detector.observe(fix(0.0, 17_000L + it * 2_000), 17_000L + it * 2_000) }
        assertEquals(EtsiitPresence.NEAR_BUILDING, detector.presence)
    }

    @Test fun invalidImpreciseOldFutureAndGapsBreakConfirmation() {
        val detector = EtsiitPresenceDetector()
        detector.observe(fix(200.0, 1_000), 1_000)
        detector.observe(fix(200.0, 3_000, 60f), 3_000)
        detector.observe(fix(200.0, 5_000), 5_000)
        assertEquals(EtsiitPresence.UNKNOWN, detector.presence)
        detector.observe(fix(200.0, 7_000).copy(latitude = Double.NaN), 7_000)
        detector.observe(fix(200.0, 9_000), 9_000)
        detector.observe(fix(200.0, 11_000), 30_000)
        detector.observe(fix(200.0, 31_000), 30_000)
        detector.observe(fix(200.0, 33_000).copy(elapsedRealtimeMillis = null), 33_000)
        detector.observe(fix(200.0, 35_000), 35_000)
        assertEquals(EtsiitPresence.UNKNOWN, detector.presence)
        detector.reset()
        assertEquals(EtsiitPresence.UNKNOWN, detector.presence)
    }

    @Test fun foreignLocationIsOutsideButDoesNotMeanArrivalAtAuxiliary() {
        val detector = EtsiitPresenceDetector()
        repeat(3) { detector.observe(fix(2000.0, 1_000L + it * 2_000), 1_000L + it * 2_000) }
        assertEquals(EtsiitPresence.OUTSIDE, detector.presence)
        val tracker = FixedRouteTracker(EtsiitAuxiliaryRoute.route).apply { start() }
        repeat(5) { tracker.observe(fix(2000.0, 10_000L + it * 2_000), 10_000L + it * 2_000) }
        assertFalse(tracker.state.arrived)
        assertTrue(tracker.state.positionConfirmed) // Se ofrece el paso más cercano, con aviso de desvío.
        assertTrue(tracker.state.offRoute)
    }
}
