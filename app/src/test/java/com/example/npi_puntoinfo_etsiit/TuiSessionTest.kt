package com.example.npi_puntoinfo_etsiit

import com.example.npi_puntoinfo_etsiit.ocr.TuiOcrScanner
import org.junit.Assert.*
import org.junit.Test

class TuiSessionTest {

    @Test
    fun testParseTuiText_extractsRealCardFields() {
        val sampleOcrText = """
            UNIVERSIDAD DE GRANADA
            TARJETA UNIVERSITARIA INTELIGENTE
            ESTUDIANTE
            LAURA SANZ LOBO
            75941238
            9400 2847 1930 5182
            CADUCIDAD: 10/28
        """.trimIndent()

        val parsed = TuiOcrScanner.parseTuiText(sampleOcrText)

        assertEquals("Laura Sanz Lobo", parsed.name)
        assertEquals("75941238", parsed.dni)
        assertEquals("9400284719305182", parsed.cardNumber)
        assertEquals("ESTUDIANTE", parsed.colectivo)
    }

    @Test
    fun testParseTuiText_withHyphenatedCardNumber() {
        val sampleText = """
            UNIVERSIDAD DE GRANADA
            ESTUDIANTE
            CARLOS GARCIA MORENO
            84729103
            1234-5678-9012-3456
        """.trimIndent()

        val parsed = TuiOcrScanner.parseTuiText(sampleText)

        assertEquals("Carlos Garcia Moreno", parsed.name)
        assertEquals("84729103", parsed.dni)
        assertEquals("1234567890123456", parsed.cardNumber)
    }

    @Test
    fun testGetDemoTui_returnsValidCredentials() {
        val demo = TuiOcrScanner.getDemoTui()

        assertTrue(demo.name.isNotBlank())
        assertEquals(8, demo.dni.length)
        assertEquals(16, demo.cardNumber.length)
        assertEquals("ESTUDIANTE", demo.colectivo)
    }
}
