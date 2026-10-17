package com.example.npi_puntoinfo_etsiit.data

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/**
 * Representa una clase lectiva del horario oficial de la ETSIIT.
 * Distingue explícitamente entre Teoría (fondo gris) y Prácticas (fondo blanco).
 */
data class ClassItem(
    val time: String,
    val subject: String,
    val acronym: String,
    val room: String,
    val professor: String,
    val isPractice: Boolean,
    val isCurrent: Boolean = false
)

data class DayScheduleInfo(
    val dayName: String,
    val dayLabel: String,
    val classes: List<ClassItem>
)

object ScheduleRepository {

    val mondayClasses = listOf(
        ClassItem(
            time = "09:30 - 11:30",
            subject = "Programación Técnica y Científica",
            acronym = "PTC",
            room = "Aula 1.8 · Planta 1",
            professor = "Ignacio Requena",
            isPractice = false
        ),
        ClassItem(
            time = "11:30 - 13:30",
            subject = "Simulación de Sistemas",
            acronym = "SS",
            room = "Aula 2.7 · Planta 2",
            professor = "Javier Martínez",
            isPractice = true
        )
    )

    val tuesdayClasses = listOf(
        ClassItem(
            time = "09:30 - 11:30",
            subject = "Teoría de la Información y la Codificación",
            acronym = "TIC",
            room = "Aula 2.3 · Planta 2",
            professor = "Manuel Gómez",
            isPractice = true
        ),
        ClassItem(
            time = "11:30 - 13:30",
            subject = "Simulación de Sistemas",
            acronym = "SS",
            room = "Aula 0.1 · Planta Baja",
            professor = "Javier Martínez",
            isPractice = false
        )
    )

    val wednesdayClasses = listOf(
        ClassItem(
            time = "09:30 - 11:30",
            subject = "Teoría de la Información y la Codificación",
            acronym = "TIC",
            room = "Aula 1.1 · Planta 1",
            professor = "Manuel Gómez",
            isPractice = false
        ),
        ClassItem(
            time = "11:30 - 13:30",
            subject = "Programación Técnica y Científica",
            acronym = "PTC",
            room = "Aula 3.5 · Planta 3",
            professor = "Ignacio Requena",
            isPractice = true
        )
    )

    val thursdayClasses = listOf(
        ClassItem(
            time = "08:30 - 10:30",
            subject = "Nuevos Paradigmas de Interacción",
            acronym = "NPI",
            room = "Aula 0.5 · Planta Baja",
            professor = "Roberto Pérez",
            isPractice = false
        ),
        ClassItem(
            time = "10:30 - 12:30",
            subject = "Visión por Computador",
            acronym = "VC",
            room = "Aula 3.8 · Planta 3",
            professor = "Nicolás Pérez",
            isPractice = true
        ),
        ClassItem(
            time = "12:30 - 14:30",
            subject = "Visión por Computador",
            acronym = "VC",
            room = "Aula 0.5 · Planta Baja",
            professor = "Nicolás Pérez",
            isPractice = false
        )
    )

    val fridayClasses = listOf(
        ClassItem(
            time = "08:30 - 10:30",
            subject = "Procesadores de Lenguajes",
            acronym = "PL",
            room = "Aula 2.8 · Planta 2",
            professor = "Juan Carlos Cubero",
            isPractice = true
        ),
        ClassItem(
            time = "10:30 - 12:30",
            subject = "Procesadores de Lenguajes",
            acronym = "PL",
            room = "Aula 0.5 · Planta Baja",
            professor = "Juan Carlos Cubero",
            isPractice = false
        ),
        ClassItem(
            time = "12:30 - 14:30",
            subject = "Nuevos Paradigmas de Interacción",
            acronym = "NPI",
            room = "Aula 2.7 · Planta 2",
            professor = "Roberto Pérez",
            isPractice = true
        )
    )

    fun getClassesForDay(dayOfWeek: DayOfWeek): List<ClassItem> {
        return when (dayOfWeek) {
            DayOfWeek.MONDAY -> mondayClasses
            DayOfWeek.TUESDAY -> tuesdayClasses
            DayOfWeek.WEDNESDAY -> wednesdayClasses
            DayOfWeek.THURSDAY -> thursdayClasses
            DayOfWeek.FRIDAY -> fridayClasses
            DayOfWeek.SATURDAY, DayOfWeek.SUNDAY -> emptyList()
        }
    }

    /**
     * Devuelve la clase en curso o la próxima clase con un margen de 30 minutos antes.
     * Si no hay clase en ese rango (o es fin de semana / fuera de horario), devuelve null.
     */
    fun getCurrentOrUpcomingClass(
        date: LocalDate = LocalDate.now(),
        time: LocalTime = LocalTime.now()
    ): ClassItem? {
        val classes = getClassesForDay(date.dayOfWeek)
        if (classes.isEmpty()) return null

        for (i in classes.indices) {
            val item = classes[i]
            val parts = item.time.split("-").map { it.trim() }
            if (parts.size != 2) continue

            val start = try { LocalTime.parse(parts[0]) } catch (_: Exception) { continue }
            val end = try { LocalTime.parse(parts[1]) } catch (_: Exception) { continue }

            // Margen de 30 minutos antes de que empiece la clase
            val windowStart = start.minusMinutes(30)

            // Fin de la ventana: si hay una clase inmediatamente posterior, la ventana termina
            // 30 minutos antes del inicio de la siguiente clase; de lo contrario, al finalizar esta clase.
            val windowEnd = if (i + 1 < classes.size) {
                val nextParts = classes[i + 1].time.split("-").map { it.trim() }
                val nextStart = try { LocalTime.parse(nextParts[0]) } catch (_: Exception) { null }
                if (nextStart != null) {
                    val nextWindowStart = nextStart.minusMinutes(30)
                    if (nextWindowStart.isBefore(end)) nextWindowStart else end
                } else {
                    end
                }
            } else {
                end
            }

            if (!time.isBefore(windowStart) && time.isBefore(windowEnd)) {
                return item
            }
        }

        return null
    }
}

