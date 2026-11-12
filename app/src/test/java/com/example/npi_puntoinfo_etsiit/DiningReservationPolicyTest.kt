package com.example.npi_puntoinfo_etsiit

import com.example.npi_puntoinfo_etsiit.data.DiningReservationPolicy
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDate

class DiningReservationPolicyTest {
    private val monday = LocalDate.of(2026, 11, 9)

    @Test fun firstPageIsTodayFollowedByFourWorkingDays() {
        (0L..6L).forEach { offset ->
            val today = monday.plusDays(offset)
            val dates = DiningReservationPolicy.menuDates(today)
            assertEquals(5, dates.size)
            assertEquals(today, dates.first())
            val expected = generateSequence(today.plusDays(1)) { it.plusDays(1) }
                .filter { it.dayOfWeek.value in 1..5 }.take(4).toList()
            assertEquals(expected, dates.drop(1))
            assertEquals(dates.sorted().distinct(), dates)
        }
        assertEquals(listOf(monday.plusDays(4), monday.plusDays(7), monday.plusDays(8),
            monday.plusDays(9), monday.plusDays(10)), DiningReservationPolicy.menuDates(monday.plusDays(4)))
    }

    @Test fun allFourFutureMenusAreReservableButTodayAndOtherDatesAreNot() {
        (0L..6L).forEach { day ->
            val today = monday.plusDays(day)
            val dates = DiningReservationPolicy.menuDates(today)
            assertFalse(DiningReservationPolicy.canReserve(today.minusDays(1), today))
            assertFalse(DiningReservationPolicy.canReserve(today, today))
            dates.drop(1).forEach { assertTrue(DiningReservationPolicy.canReserve(it, today)) }
            assertFalse(DiningReservationPolicy.canReserve(dates.last().plusDays(1), today))
        }
    }

    @Test fun reservationWindowClosesAtMidnightBeforeTheMenuDay() {
        val menu = monday.plusDays(1)
        assertTrue(DiningReservationPolicy.canReserve(menu, monday))
        assertFalse(DiningReservationPolicy.canReserve(menu, menu))
        assertFalse(DiningReservationPolicy.canReserve(menu, menu.plusDays(1)))
    }

    @Test fun monthAndYearBoundariesKeepFutureWorkingDaysReservable() {
        val yearEnd = LocalDate.of(2025, 12, 31)
        assertTrue(DiningReservationPolicy.canReserve(LocalDate.of(2026, 1, 1), yearEnd))
        assertTrue(DiningReservationPolicy.canReserve(LocalDate.of(2026, 1, 2), yearEnd))
        assertFalse(DiningReservationPolicy.canReserve(LocalDate.of(2026, 1, 7), yearEnd))
        assertTrue(DiningReservationPolicy.canReserve(LocalDate.of(2024, 2, 29), LocalDate.of(2024, 2, 28)))
    }

    @Test fun weekendsHaveNoServiceButFollowingMondayCanBeReserved() {
        val friday = monday.plusDays(4)
        assertFalse(DiningReservationPolicy.hasService(friday.plusDays(1)))
        assertFalse(DiningReservationPolicy.hasService(friday.plusDays(2)))
        assertFalse(DiningReservationPolicy.canReserve(friday.plusDays(1), friday))
        assertTrue(DiningReservationPolicy.canReserve(friday.plusDays(3), friday))
        assertTrue(DiningReservationPolicy.canReserve(friday.plusDays(3), friday.plusDays(1)))
        assertTrue(DiningReservationPolicy.canReserve(friday.plusDays(3), friday.plusDays(2)))
    }

    @Test fun displayedPriceIsFourEuros() {
        assertEquals("4,00 €", DiningReservationPolicy.PRICE_LABEL)
    }
}
