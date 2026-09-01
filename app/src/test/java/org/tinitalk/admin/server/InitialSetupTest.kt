package org.tinitalk.admin.server

import org.junit.Assert.assertEquals
import org.junit.Test

class InitialSetupTest {
    @Test
    fun classifiesServerWithoutTiniTalkComponentsAsClean() {
        assertEquals(
            ServerSetupAssessment.CLEAN,
            InitialSetupEvidence().assessment(),
        )
    }

    @Test
    fun classifiesWorkingInstallationAsConfiguredRegardlessOfInstaller() {
        val configured = InitialSetupEvidence(
            doctorReady = true,
            binaryInstalled = true,
            userPresent = true,
            dataDirectoryPresent = true,
            stateDatabasePresent = true,
            tlsPresent = true,
            serviceInstalled = true,
            serviceEnabled = true,
            serviceRunning = true,
        )
        assertEquals(ServerSetupAssessment.CONFIGURED, configured.assessment())
        assertEquals(
            ServerSetupAssessment.PARTIAL,
            configured.copy(serviceRunning = false).assessment(),
        )
        assertEquals(
            ServerSetupAssessment.PARTIAL,
            configured.copy(doctorReady = false).assessment(),
        )
    }

    @Test
    fun derivesCompletedStepsFromCurrentStep() {
        assertEquals(
            listOf(
                InitialSetupStep.SYSTEM_PACKAGES,
                InitialSetupStep.FIREWALL,
                InitialSetupStep.TLS_CERTIFICATE,
            ),
            InitialSetupStep.PREPARE_TINITALK.completedSteps(),
        )
    }
}
