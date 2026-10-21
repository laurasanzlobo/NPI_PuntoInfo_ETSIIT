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
    fun testParseTuiText_withRealUserTui() {
        val userTuiOcr = """
            ( TaRETA UN
            IVERSITARIA N TELIGENTE - UNIVERSIDAD DE GRANADA 3\
            590144 393364055 8
            JOSE PEDREGOSA GARCIA
            75932717
            Caduca: Final 09/2029 UNIVERSIDAD
            ESTUDIANTE DE GRANADA
        """.trimIndent()

        val parsed = TuiOcrScanner.parseTuiText(userTuiOcr)

        assertEquals("Jose Pedregosa Garcia", parsed.name)
        assertEquals("75932717", parsed.dni)
        assertEquals("5901443933640558", parsed.cardNumber)
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

