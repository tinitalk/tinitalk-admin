package org.tinitalk.admin.server

enum class ServerSetupAssessment {
    CLEAN,
    PARTIAL,
    CONFIGURED,
}

enum class InitialSetupStep(val operationKind: ServerOperationKind) {
    SYSTEM_PACKAGES(ServerOperationKind.INSTALL_SYSTEM_PACKAGES),
    FIREWALL(ServerOperationKind.CONFIGURE_FIREWALL),
    TLS_CERTIFICATE(ServerOperationKind.OBTAIN_TLS_CERTIFICATE),
    PREPARE_TINITALK(ServerOperationKind.PREPARE_TINITALK),
    UPLOAD_FILES(ServerOperationKind.INSTALL_TINITALK_FILES),
    START_TINITALK(ServerOperationKind.START_TINITALK),
    ;

    fun completedSteps(): List<InitialSetupStep> = entries.takeWhile { it != this }

    fun next(): InitialSetupStep? = entries.getOrNull(ordinal + 1)

    companion object {
        fun from(kind: ServerOperationKind): InitialSetupStep = entries.first {
            it.operationKind == kind
        }
    }
}

data class InitialSetupEvidence(
    val systemPackagesReady: Boolean = false,
    val firewallReady: Boolean = false,
    val tlsCertificateReady: Boolean = false,
    val tinitalkPrepared: Boolean = false,
    val filesUploaded: Boolean = false,
    val tinitalkStarted: Boolean = false,
    val binaryInstalled: Boolean = false,
    val userPresent: Boolean = false,
    val dataDirectoryPresent: Boolean = false,
    val stateDatabasePresent: Boolean = false,
    val tlsPresent: Boolean = false,
    val serviceInstalled: Boolean = false,
    val serviceEnabled: Boolean = false,
    val serviceRunning: Boolean = false,
) {
    fun completedSteps(): Set<InitialSetupStep> = buildSet {
        if (systemPackagesReady) add(InitialSetupStep.SYSTEM_PACKAGES)
        if (firewallReady) add(InitialSetupStep.FIREWALL)
        if (tlsCertificateReady) add(InitialSetupStep.TLS_CERTIFICATE)
        if (tinitalkPrepared) add(InitialSetupStep.PREPARE_TINITALK)
        if (filesUploaded) add(InitialSetupStep.UPLOAD_FILES)
        if (tinitalkStarted) add(InitialSetupStep.START_TINITALK)
    }

    fun assessment(): ServerSetupAssessment = when {
        completedSteps().size == InitialSetupStep.entries.size -> {
            ServerSetupAssessment.CONFIGURED
        }

        !binaryInstalled &&
            !userPresent &&
            !dataDirectoryPresent &&
            !stateDatabasePresent &&
            !tlsPresent &&
            !serviceInstalled &&
            completedSteps().isEmpty() -> ServerSetupAssessment.CLEAN

        else -> ServerSetupAssessment.PARTIAL
    }
}
