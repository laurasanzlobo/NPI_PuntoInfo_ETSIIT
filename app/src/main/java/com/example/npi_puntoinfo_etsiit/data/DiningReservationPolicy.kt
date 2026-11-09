package com.example.npi_puntoinfo_etsiit.data

import java.time.LocalDate

object DiningReservationPolicy {
    const val PRICE_LABEL = "4,00 €"

    fun hasService(date: LocalDate): Boolean = date.dayOfWeek.value in 1..5

    fun canReserve(menuDate: LocalDate, today: LocalDate): Boolean =
        menuDate.isAfter(today) && menuDate in menuDates(today)

    /** Hoy seguido de los cuatro días laborables siguientes, saltando sábados y domingos. */
    fun menuDates(today: LocalDate): List<LocalDate> = listOf(today) +
        generateSequence(today.plusDays(1)) { it.plusDays(1) }
        .filter(::hasService)
        .take(4)
        .toList()
}
