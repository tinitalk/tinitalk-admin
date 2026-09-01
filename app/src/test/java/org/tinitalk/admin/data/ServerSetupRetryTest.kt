package org.tinitalk.admin.data

import org.tinitalk.admin.server.InitialSetupStep
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class ServerSetupRetryTest {
    @Test
    fun preparesFailedOperationForFreshStart() {
        val setup = StoredServerSetup(
            configured = false,
            startedAtEpochMillis = 1234,
            currentStep = InitialSetupStep.FIREWALL,
            operationStarted = true,
            completedSteps = setOf(InitialSetupStep.SYSTEM_PACKAGES),
            binaryUri = "content://selected-binary",
        )

        val retry = setup.forRetry()

        assertFalse(retry.operationStarted)
        assertEquals(InitialSetupStep.FIREWALL, retry.currentStep)
        assertEquals(setOf(InitialSetupStep.SYSTEM_PACKAGES), retry.completedSteps)
        assertEquals("content://selected-binary", retry.binaryUri)
    }
}
