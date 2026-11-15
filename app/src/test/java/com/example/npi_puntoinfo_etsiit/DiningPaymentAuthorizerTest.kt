package com.example.npi_puntoinfo_etsiit

import com.example.npi_puntoinfo_etsiit.data.DiningPaymentAuthorizer
import com.example.npi_puntoinfo_etsiit.sensors.DeviceAuthenticationResult
import com.example.npi_puntoinfo_etsiit.sensors.DeviceAuthenticator
import org.junit.Assert.*
import org.junit.Test

class DiningPaymentAuthorizerTest {
    private class FakeAuthenticator : DeviceAuthenticator {
        val callbacks = mutableListOf<(DeviceAuthenticationResult) -> Unit>()
        var cancellations = 0
        override fun authenticate(onResult: (DeviceAuthenticationResult) -> Unit) { callbacks += onResult }
        override fun cancel() { cancellations++ }
    }

    @Test fun openingConfirmationDoesNotAuthorizeUntilTheDeviceSucceeds() {
        val device = FakeAuthenticator()
        val payment = DiningPaymentAuthorizer(device)
        var reserved = false
        payment.confirm { reserved = it == DeviceAuthenticationResult.Authenticated }
        assertFalse(reserved)
        assertEquals(1, device.callbacks.size)
        device.callbacks.single()(DeviceAuthenticationResult.Authenticated)
        assertTrue(reserved)
    }

    @Test fun cancellationAndErrorsNeverAuthorizeAReservation() {
        listOf(DeviceAuthenticationResult.Cancelled,
            DeviceAuthenticationResult.Error("Sin bloqueo configurado")).forEach { result ->
            val device = FakeAuthenticator()
            val payment = DiningPaymentAuthorizer(device)
            var reserved = false
            var received: DeviceAuthenticationResult? = null
            payment.confirm {
                reserved = it == DeviceAuthenticationResult.Authenticated
                received = it
            }
            device.callbacks.single()(result)
            assertFalse(reserved)
            assertEquals(result, received)
        }
    }

    @Test fun leavingCheckoutIgnoresALateSuccessfulAuthentication() {
        val device = FakeAuthenticator()
        val payment = DiningPaymentAuthorizer(device)
        var completions = 0
        payment.confirm { completions++ }
        payment.cancel()
        device.callbacks.single()(DeviceAuthenticationResult.Authenticated)
        assertEquals(0, completions)
        assertEquals(1, device.cancellations)
    }

    @Test fun duplicateResponsesCannotConfirmTwice() {
        val device = FakeAuthenticator()
        val payment = DiningPaymentAuthorizer(device)
        var completions = 0
        payment.confirm { completions++ }
        device.callbacks.single()(DeviceAuthenticationResult.Authenticated)
        device.callbacks.single()(DeviceAuthenticationResult.Authenticated)
        assertEquals(1, completions)
    }

    @Test fun retryDoesNotAcceptAResponseFromThePreviousAttempt() {
        val device = FakeAuthenticator()
        val payment = DiningPaymentAuthorizer(device)
        val results = mutableListOf<DeviceAuthenticationResult>()
        payment.confirm(results::add)
        device.callbacks[0](DeviceAuthenticationResult.Cancelled)
        payment.confirm(results::add)
        device.callbacks[0](DeviceAuthenticationResult.Authenticated)
        assertEquals(listOf(DeviceAuthenticationResult.Cancelled), results)
        device.callbacks[1](DeviceAuthenticationResult.Authenticated)
        assertEquals(listOf(DeviceAuthenticationResult.Cancelled, DeviceAuthenticationResult.Authenticated), results)
    }
}
