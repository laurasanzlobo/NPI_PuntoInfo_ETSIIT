package com.example.npi_puntoinfo_etsiit.sensors

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SingleTiltActionDetectorTest {

    @Test
    fun `tilting phone towards user past vertical and returning triggers confirmation`() {
        val detector = SingleTiltActionDetector()

        // Postura inicial de lectura (~50 grados: y=7.5, z=6.3)
        assertFalse(detector.onAccelerometer(0f, 7.5f, 6.3f, 1000L))
        assertFalse(detector.onAccelerometer(0f, 7.5f, 6.3f, 1050L))

        // Inclinar la parte superior hacia ti hasta vertical / pasar vertical (y=9.8, z=-0.4)
        assertFalse(detector.onAccelerometer(0f, 9.7f, 0.2f, 1150L))
        assertFalse(detector.onAccelerometer(0f, 9.8f, -0.5f, 1250L))

        // Volver a la postura de lectura normal -> dispara el gesto «Sí»
        assertFalse(detector.onAccelerometer(0f, 8.6f, 2.2f, 1320L))
        assertTrue(detector.onAccelerometer(0f, 7.4f, 6.4f, 1400L))
    }

    @Test
    fun `small reading wobble without reaching vertical does not trigger`() {
        val detector = SingleTiltActionDetector()

        assertFalse(detector.onAccelerometer(0f, 7.5f, 6.3f, 1000L))
        assertFalse(detector.onAccelerometer(0f, 8.5f, 4.9f, 1150L))
        assertFalse(detector.onAccelerometer(0f, 7.4f, 6.4f, 1300L))
    }

    @Test
    fun `holding vertical longer than timeout cancels confirmation`() {
        val detector = SingleTiltActionDetector()

        assertFalse(detector.onAccelerometer(0f, 7.5f, 6.3f, 1000L))
        assertFalse(detector.onAccelerometer(0f, 9.8f, -0.4f, 1200L))
        // Mantener vertical > 1800 ms antes de regresar
        assertFalse(detector.onAccelerometer(0f, 9.8f, -0.4f, 3200L))
        assertFalse(detector.onAccelerometer(0f, 7.4f, 6.4f, 3350L))
    }
}
