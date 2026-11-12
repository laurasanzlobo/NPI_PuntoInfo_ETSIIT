package com.example.npi_puntoinfo_etsiit

import android.Manifest
import android.content.Context
import android.content.ContextWrapper
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.StateRestorationTester
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.npi_puntoinfo_etsiit.sensors.DeviceAuthenticationResult
import com.example.npi_puntoinfo_etsiit.sensors.DeviceAuthenticator
import com.example.npi_puntoinfo_etsiit.ui.screens.DiningScreen
import com.example.npi_puntoinfo_etsiit.data.DiningReservationPolicy
import org.junit.After
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate
import java.util.UUID

/** Autenticación inyectada: no utiliza biometría ni modifica las reservas del usuario. */
@RunWith(AndroidJUnit4::class)
class DiningPaymentTest {
    @get:Rule val compose = createComposeRule()
    private val device = FakeAuthenticator()
    private val testId = UUID.randomUUID().toString()
    private val testPreferences = mutableListOf<SharedPreferences>()
    private val initialDate = LocalDate.of(2026, 11, 9)
    private var testDate by mutableStateOf(initialDate)
    private val menuDate = initialDate.plusDays(1)
    private lateinit var orders: SharedPreferences

    private class FakeAuthenticator : DeviceAuthenticator {
        val callbacks = mutableListOf<(DeviceAuthenticationResult) -> Unit>()
        override fun authenticate(onResult: (DeviceAuthenticationResult) -> Unit) { callbacks += onResult }
        override fun cancel() = Unit
        fun respond(result: DeviceAuthenticationResult) { callbacks.last()(result) }
    }

    @After fun clearTestPreferences() {
        testPreferences.forEach { it.edit().clear().commit() }
    }

    private fun showDining(): StateRestorationTester {
        val restoration = StateRestorationTester(compose)
        restoration.setContent {
            val activity = LocalContext.current
            val context = remember(activity) {
                object : ContextWrapper(activity) {
                    override fun getApplicationContext(): Context = this
                    // Evitar diálogos de permiso de ubicación; Fused puede devolver «no disponible».
                    override fun checkPermission(permission: String, pid: Int, uid: Int): Int =
                        if (permission in listOf(Manifest.permission.ACCESS_FINE_LOCATION,
                                Manifest.permission.ACCESS_COARSE_LOCATION)) PackageManager.PERMISSION_GRANTED
                        else super.checkPermission(permission, pid, uid)

                    override fun getSharedPreferences(name: String, mode: Int): SharedPreferences =
                        super.getSharedPreferences("${name}_payment_test_$testId", mode).also {
                            if (it !in testPreferences) testPreferences += it
                            if (name == "npi_demo_dining_orders") orders = it
                        }
                }
            }
            CompositionLocalProvider(LocalContext provides context) {
                MaterialTheme { DiningScreen(paymentAuthenticator = device, currentDate = { testDate }) }
            }
        }
        return restoration
    }

    private fun openCheckout() {
        compose.onNodeWithTag("dining-menu-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("dining-reserve-$menuDate").performScrollTo().performClick()
        compose.onNodeWithTag("dining-payment-screen").assertIsDisplayed()
        compose.onNodeWithText("Pasarela de pago").assertIsDisplayed()
        compose.onNodeWithText(DiningReservationPolicy.PRICE_LABEL).assertIsDisplayed()
    }

    private fun authenticate() {
        compose.onNodeWithTag("dining-confirm-payment").performScrollTo().performClick()
    }

    @Test fun successfulAuthenticationMarksOnlyTheChosenDayAndMenuAsReservedAndSurvivesRecreation() {
        val restoration = showDining()
        compose.onNodeWithText("Vegetariano").performClick()
        openCheckout()
        authenticate()
        compose.runOnIdle {
            assertNull(orders.getString("ordered_date_$menuDate", null))
            device.respond(DeviceAuthenticationResult.Authenticated)
        }
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNodeWithTag("dining-menu-$menuDate").assert(hasStateDescription("Reservado"))
        compose.onNodeWithTag("dining-reserve-$menuDate").assertIsNotEnabled()
        compose.runOnIdle { assertEquals("Vegetariano", orders.getString("ordered_date_$menuDate", null)) }
        restoration.emulateSavedInstanceStateRestore()
        compose.onNodeWithTag("dining-menu-$initialDate").assertIsDisplayed()
        compose.onNodeWithTag("dining-menu-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("dining-menu-$menuDate").assert(hasStateDescription("Reservado"))
        compose.onNodeWithTag("dining-menu-pager").performTouchInput { swipeLeft() }
        val nextDate = menuDate.plusDays(1)
        compose.onNodeWithTag("dining-menu-$nextDate").assert(hasStateDescription("Sin reservar"))
    }

    @Test fun cancellingDeviceAuthenticationLeavesTheReservationUnchangedAndAllowsRetry() {
        showDining()
        openCheckout()
        authenticate()
        compose.runOnIdle { device.respond(DeviceAuthenticationResult.Cancelled) }
        compose.onNodeWithTag("dining-payment-error").assertTextContains("No se ha reservado", substring = true)
        compose.onNodeWithTag("dining-confirm-payment").assertIsEnabled()
        compose.runOnIdle { assertNull(orders.getString("ordered_date_$menuDate", null)) }
        authenticate()
        compose.runOnIdle { device.respond(DeviceAuthenticationResult.Authenticated) }
        compose.onNodeWithText("Aceptar").performClick()
        compose.onNodeWithTag("dining-menu-$menuDate").assert(hasStateDescription("Reservado"))
    }

    @Test fun noDeviceLockDoesNotCompleteTheSimulatedPayment() {
        showDining()
        openCheckout()
        authenticate()
        compose.runOnIdle { device.respond(DeviceAuthenticationResult.Error("Configura un bloqueo en el móvil.")) }
        compose.onNodeWithTag("dining-payment-error").assertTextEquals("Configura un bloqueo en el móvil.")
        compose.runOnIdle { assertNull(orders.getString("ordered_date_$menuDate", null)) }
    }

    @Test fun cancellingCheckoutIgnoresALateDeviceSuccess() {
        showDining()
        openCheckout()
        authenticate()
        compose.onNodeWithText("Cancelar y volver").performScrollTo().performClick()
        compose.runOnIdle { device.respond(DeviceAuthenticationResult.Authenticated) }
        compose.onNodeWithTag("dining-payment-screen").assertDoesNotExist()
        compose.onNodeWithTag("dining-menu-$menuDate").assert(hasStateDescription("Sin reservar"))
        compose.runOnIdle { assertNull(orders.getString("ordered_date_$menuDate", null)) }
    }

    @Test fun firstMenuIsTodayAndAllFourFollowingWorkingDaysEnableReservation() {
        showDining()
        compose.onNodeWithTag("dining-current-day").assertTextContains("Hoy", substring = true)
        compose.onNodeWithTag("dining-menu-$initialDate").assertIsDisplayed()
        compose.onNodeWithTag("dining-reserve-$initialDate").assertDoesNotExist()
        (1L..4L).forEach { offset ->
            compose.onNodeWithTag("dining-menu-pager").performTouchInput { swipeLeft() }
            compose.onNodeWithTag("dining-reserve-${initialDate.plusDays(offset)}").assertIsEnabled()
        }
        compose.runOnIdle { assertTrue(device.callbacks.isEmpty()) }
    }

    @Test fun authenticationCompletingAfterMidnightCannotBookTheSameDay() {
        showDining()
        openCheckout()
        authenticate()
        compose.runOnIdle {
            testDate = menuDate
            device.respond(DeviceAuthenticationResult.Authenticated)
        }
        compose.onNodeWithTag("dining-payment-screen").assertDoesNotExist()
        compose.onNodeWithTag("dining-menu-$menuDate").assert(hasStateDescription("Sin reservar"))
        compose.runOnIdle { assertNull(orders.getString("ordered_date_$menuDate", null)) }
    }

    @Test fun fridayIsFollowedByReservableMondayWithoutWeekendPages() {
        val friday = initialDate.plusDays(4)
        testDate = friday
        showDining()
        compose.onNodeWithTag("dining-menu-$friday").assertIsDisplayed()
        compose.onNodeWithTag("dining-menu-pager").performTouchInput { swipeLeft() }
        compose.onNodeWithTag("dining-menu-${friday.plusDays(3)}").assertIsDisplayed()
        compose.onNodeWithTag("dining-reserve-${friday.plusDays(3)}").assertIsEnabled()
    }
}
