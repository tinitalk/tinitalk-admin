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
    fun classifiesServerAsConfiguredOnlyWhenEverySetupStepIsReady() {
        val configured = InitialSetupEvidence(
            systemPackagesReady = true,
            firewallReady = true,
            fail2banReady = true,
            tlsCertificateReady = true,
            tinitalkPrepared = true,
            binaryUploaded = true,
            tinitalkStarted = true,
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
            configured.copy(fail2banReady = false).assessment(),
        )
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
                InitialSetupStep.FAIL2BAN,
                InitialSetupStep.TLS_CERTIFICATE,
            ),
            InitialSetupStep.PREPARE_TINITALK.completedSteps(),
        )
    }
}
