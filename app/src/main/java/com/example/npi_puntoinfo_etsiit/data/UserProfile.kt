package com.example.npi_puntoinfo_etsiit.data

/**
 * Representa el perfil verificado del estudiante universitario mediante TUI física y NFC.
 *
 * En la TUI física de la UGR figura únicamente:
 * - 16 dígitos de tarjeta
 * - Nombre completo
 * - DNI (8 dígitos sin letra)
 * - Colectivo ("ESTUDIANTE")
 *
 * El grado, curso, grupo y asignaturas matriculadas (con sus subgrupos específicos)
 * se configuran en la app tras la verificación de identidad y son modificables en cualquier momento.
 */
data class UserProfile(
    val name: String = "",
    val dni: String = "",
    val tuiCardNumber: String = "",
    val nfcUid: String = "",
    val colectivo: String = "ESTUDIANTE",
    val degreeId: String = "gii",
    val degreeName: String = "Grado en Ingeniería Informática",
    val primaryCourse: Int = 3,
    val primaryGroup: String = "A",
    val cuatrimestre: Int = 1,
    // Mapeo de asignaturas matriculadas: Acrónimo -> Subgrupo de prácticas asignado
    // Ejemplo: {"NPI": "A1", "VC": "A2", "ED": "B1"}
    // Si el valor es "", significa que solo asiste a Teoría
    val matricula: Map<String, String> = emptyMap(),
    val isLoggedIn: Boolean = false
)

/**
 * Representa una asignatura disponible en el catálogo de la ETSIIT
 * con sus posibles subgrupos de prácticas disponibles.
 */
data class SubjectOption(
    val acronym: String,
    val name: String,
    val curso: Int,
    val degreeId: String,
    val subgruposDisponibles: List<String> = emptyList()
)
