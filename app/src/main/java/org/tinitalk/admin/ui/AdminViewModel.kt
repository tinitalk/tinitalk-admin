package org.tinitalk.admin.ui

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.tinitalk.admin.data.ServerStore
import org.tinitalk.admin.data.ServerSetupStore
import org.tinitalk.admin.data.SharedPreferencesServerStore
import org.tinitalk.admin.data.SharedPreferencesServerSetupStore
import org.tinitalk.admin.data.StoredServerSetup
import org.tinitalk.admin.model.PinnedHostKey
import org.tinitalk.admin.model.ServerRecord
import org.tinitalk.admin.server.DnsValidationException
import org.tinitalk.admin.server.AddressKind
import org.tinitalk.admin.server.EndpointParser
import org.tinitalk.admin.server.EndpointResolver
import org.tinitalk.admin.server.EndpointValidationException
import org.tinitalk.admin.server.InitialSetupEvidence
import org.tinitalk.admin.server.InitialSetupInspector
import org.tinitalk.admin.server.InitialSetupStep
import org.tinitalk.admin.server.RemoteOperationState
import org.tinitalk.admin.server.RemoteOperationUpload
import org.tinitalk.admin.server.RemoteServerOperation
import org.tinitalk.admin.server.RemoteServerOperationRunner
import org.tinitalk.admin.server.ResolvedEndpoint
import org.tinitalk.admin.server.ServerOperationKind
import org.tinitalk.admin.server.ServerSetupAssessment
import org.tinitalk.admin.server.TiniTalkStatusChecker
import org.tinitalk.admin.ssh.ImportedKeyReader
import org.tinitalk.admin.ssh.ImportedSshIdentityException
import org.tinitalk.admin.ssh.AndroidKeystoreSshIdentityStore
import org.tinitalk.admin.ssh.AuthorizedKeyInstaller
import org.tinitalk.admin.ssh.ManagedAccessBootstrapper
import org.tinitalk.admin.ssh.ManagedSshIdentityStore
import org.tinitalk.admin.ssh.MissingAdministrativeAccessException
import org.tinitalk.admin.ssh.SshAccessChecker
import org.tinitalk.admin.ssh.SshConnection
import org.tinitalk.admin.ssh.SshCredential
import org.tinitalk.admin.ssh.SshFailure
import org.tinitalk.admin.ssh.SshjAccessChecker
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AdminRoute {
    data object ServerList : AdminRoute
    data object AddServer : AdminRoute
    data class ServerDetails(val serverId: String) : AdminRoute
}

sealed interface AddServerPhase {
    data object Form : AddServerPhase
    data object ScanningFingerprint : AddServerPhase
    data class ConfirmFingerprint(val key: PinnedHostKey) : AddServerPhase
    data object CheckingAccess : AddServerPhase
}

enum class AuthenticationMethod {
    PASSWORD,
    PRIVATE_KEY,
}

data class AddServerState(
    val displayName: String = "",
    val address: String = "",
    val sshPort: String = "22",
    val sshLogin: String = "root",
    val frozenIpv4: String? = null,
    val authentication: AuthenticationMethod = AuthenticationMethod.PASSWORD,
    val password: String = "",
    val privateKeyPassphrase: String = "",
    val privateKeySelected: Boolean = false,
    val phase: AddServerPhase = AddServerPhase.Form,
    val errorMessage: String? = null,
)

data class AdminUiState(
    val route: AdminRoute = AdminRoute.ServerList,
    val servers: List<ServerRecord> = emptyList(),
    val addServer: AddServerState = AddServerState(),
    val sshCheckInProgress: Boolean = false,
    val sshCheckResult: SshCheckResult? = null,
    val serverOperation: RunningServerOperation? = null,
    val initialSetup: InitialSetupUiState = InitialSetupUiState(),
    val tinitalkFiles: TiniTalkFilesState = TiniTalkFilesState(),
    val notice: String? = null,
)

enum class InitialSetupUiMode {
    UNKNOWN,
    CHECKING,
    CLEAN,
    PARTIAL,
    RUNNING,
    FAILED,
    CONFIGURED,
}

data class InitialSetupUiState(
    val mode: InitialSetupUiMode = InitialSetupUiMode.UNKNOWN,
    val startedAtEpochMillis: Long? = null,
    val currentStep: InitialSetupStep? = null,
    val completedSteps: Set<InitialSetupStep> = emptySet(),
    val errorMessage: String? = null,
)

private enum class InitialSetupContinuation {
    NONE,
    CLEAN_ONLY,
    ANY_INCOMPLETE,
}

data class TiniTalkFilesState(
    val visible: Boolean = false,
    val binaryName: String? = null,
    val firebaseAndroidConfigName: String? = null,
    val firebaseServiceAccountName: String? = null,
) {
    val ready: Boolean
        get() = binaryName != null &&
            firebaseAndroidConfigName != null &&
            firebaseServiceAccountName != null
}

data class RunningServerOperation(
    val serverId: String,
    val kind: ServerOperationKind,
    val startedAt: Long,
)

data class SshCheckResult(
    val user: String,
    val host: String,
    val uptime: String,
    val operatingSystem: String,
    val architecture: String,
)

class AdminViewModel(
    private val contentResolver: ContentResolver,
    private val serverStore: ServerStore,
    private val serverSetupStore: ServerSetupStore,
    private val endpointResolver: EndpointResolver,
    private val sshAccessChecker: SshAccessChecker,
    private val importedKeyReader: ImportedKeyReader,
    private val identityStore: ManagedSshIdentityStore,
    private val managedAccessBootstrapper: ManagedAccessBootstrapper,
    private val tinitalkStatusChecker: TiniTalkStatusChecker,
    private val initialSetupInspector: InitialSetupInspector,
    private val remoteOperationRunner: RemoteServerOperationRunner,
) : ViewModel() {
    private val initialServers = runCatching(serverStore::list)
    private val mutableState = MutableStateFlow(
        AdminUiState(
            servers = initialServers.getOrDefault(emptyList()),
            notice = initialServers.exceptionOrNull()?.let {
                "Не удалось прочитать сохранённый список серверов"
            },
        ),
    )
    val state: StateFlow<AdminUiState> = mutableState.asStateFlow()

    private var operationId = 0L
    private var currentJob: Job? = null
    private var sshCheckJob: Job? = null
    private var serverOperationJob: Job? = null
    private var pendingEndpoint: ResolvedEndpoint? = null
    private var pendingHostKey: PinnedHostKey? = null
    private var selectedPrivateKeyUri: Uri? = null
    private var selectedTiniTalkBinaryUri: Uri? = null
    private var selectedFirebaseAndroidConfigUri: Uri? = null
    private var selectedFirebaseServiceAccountUri: Uri? = null

    fun openAddServer() {
        invalidateOperation()
        selectedPrivateKeyUri = null
        mutableState.update {
            it.copy(
                route = AdminRoute.AddServer,
                addServer = AddServerState(),
                notice = null,
            )
        }
    }

    fun closeAddServer() {
        if (
            mutableState.value.addServer.phase == AddServerPhase.ScanningFingerprint ||
            mutableState.value.addServer.phase == AddServerPhase.CheckingAccess
        ) return
        invalidateOperation()
        selectedPrivateKeyUri = null
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerList,
                addServer = AddServerState(),
            )
        }
    }

    fun openServer(serverId: String) {
        if (mutableState.value.servers.none { it.id == serverId }) return
        sshCheckJob?.cancel()
        sshCheckJob = null
        val savedSetup = runCatching { serverSetupStore.get(serverId) }.getOrNull()
        val setupState = when {
            savedSetup?.configured == true -> InitialSetupUiState(InitialSetupUiMode.CONFIGURED)
            savedSetup?.inProgress == true -> InitialSetupUiState(
                mode = InitialSetupUiMode.RUNNING,
                startedAtEpochMillis = savedSetup.startedAtEpochMillis,
                currentStep = savedSetup.currentStep,
            )
            else -> InitialSetupUiState()
        }
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerDetails(serverId),
                sshCheckInProgress = false,
                sshCheckResult = null,
                initialSetup = setupState,
                notice = null,
            )
        }
        if (savedSetup?.inProgress == true) resumeInitialSetup(serverId)
    }

    fun closeServer() {
        sshCheckJob?.cancel()
        sshCheckJob = null
        discardSelectedTiniTalkFiles()
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerList,
                sshCheckInProgress = false,
                sshCheckResult = null,
                tinitalkFiles = TiniTalkFilesState(),
                initialSetup = InitialSetupUiState(),
            )
        }
    }

    fun checkServerSsh(serverId: String) {
        if (sshCheckJob?.isActive == true) return
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        mutableState.update {
            it.copy(sshCheckInProgress = true, sshCheckResult = null, notice = null)
        }
        sshCheckJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { runSshCheck(server) }
                if (mutableState.value.route == AdminRoute.ServerDetails(serverId)) {
                    mutableState.update {
                        it.copy(sshCheckInProgress = false, sshCheckResult = result)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (mutableState.value.route == AdminRoute.ServerDetails(serverId)) {
                    mutableState.update {
                        it.copy(
                            sshCheckInProgress = false,
                            notice = sshCheckErrorMessage(error),
                        )
                    }
                }
            } finally {
                sshCheckJob = null
            }
        }
    }

    fun dismissSshCheckResult() {
        mutableState.update { it.copy(sshCheckResult = null) }
    }

    fun checkInitialSetup(serverId: String) {
        inspectInitialSetup(serverId, continuation = InitialSetupContinuation.NONE)
    }

    fun checkAndContinueInitialSetup(serverId: String) {
        inspectInitialSetup(serverId, continuation = InitialSetupContinuation.ANY_INCOMPLETE)
    }

    private fun inspectInitialSetup(
        serverId: String,
        continuation: InitialSetupContinuation,
    ) {
        if (serverOperationJob?.isActive == true) return
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        val previousSetup = mutableState.value.initialSetup
        mutableState.update {
            it.copy(initialSetup = InitialSetupUiState(InitialSetupUiMode.CHECKING), notice = null)
        }
        serverOperationJob = viewModelScope.launch {
            var shouldContinue = false
            try {
                val connection = connectToServer(server)
                try {
                    val remote = remoteOperationRunner.find(connection, server.sshLogin)
                    if (remote?.state == RemoteOperationState.RUNNING) {
                        val step = InitialSetupStep.from(remote.kind)
                        val setup = StoredServerSetup(
                            configured = false,
                            startedAtEpochMillis = System.currentTimeMillis() - remote.elapsedMillis,
                            currentStep = step,
                            operationStarted = true,
                            completedSteps = step.completedSteps().toSet(),
                        )
                        serverSetupStore.put(server.id, setup)
                        runInitialSetup(connection, server, setup)
                    } else {
                        val assessment = applySetupAssessment(
                            server,
                            initialSetupInspector.inspect(
                                connection = connection,
                                login = server.sshLogin,
                                serverAddress = server.enteredAddress,
                                sshPort = server.sshPort,
                            ),
                        )
                        shouldContinue = when (continuation) {
                            InitialSetupContinuation.NONE -> false
                            InitialSetupContinuation.CLEAN_ONLY -> {
                                assessment == ServerSetupAssessment.CLEAN
                            }
                            InitialSetupContinuation.ANY_INCOMPLETE -> {
                                assessment != ServerSetupAssessment.CONFIGURED
                            }
                        }
                    }
                } finally {
                    runCatching { connection.close() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        initialSetup = if (previousSetup.mode == InitialSetupUiMode.CONFIGURED) {
                            previousSetup
                        } else {
                            InitialSetupUiState(InitialSetupUiMode.UNKNOWN)
                        },
                        notice = setupErrorMessage(error),
                    )
                }
            } finally {
                serverOperationJob = null
                if (shouldContinue) {
                    continueInitialSetup(serverId)
                }
            }
        }
    }

    fun continueInitialSetup(serverId: String) {
        if (
            mutableState.value.route != AdminRoute.ServerDetails(serverId) ||
            mutableState.value.serverOperation != null ||
            mutableState.value.initialSetup.mode !in setOf(
                InitialSetupUiMode.CLEAN,
                InitialSetupUiMode.PARTIAL,
            )
        ) return
        val completedSteps = mutableState.value.initialSetup.completedSteps
        if (InitialSetupStep.UPLOAD_FILES in completedSteps) {
            discardSelectedTiniTalkFiles()
            beginInitialSetup(serverId, completedSteps)
            return
        }
        discardSelectedTiniTalkFiles()
        mutableState.update { it.copy(tinitalkFiles = TiniTalkFilesState(visible = true)) }
    }

    fun closeTiniTalkFiles() {
        discardSelectedTiniTalkFiles()
        mutableState.update { it.copy(tinitalkFiles = TiniTalkFilesState()) }
    }

    fun tinitalkBinarySelected(uri: Uri?) {
        if (uri == null || !mutableState.value.tinitalkFiles.visible) return
        if (!preserveReadAccess(uri)) return
        selectedTiniTalkBinaryUri?.takeIf { it != uri }?.let(::releaseReadAccess)
        selectedTiniTalkBinaryUri = uri
        mutableState.update {
            it.copy(tinitalkFiles = it.tinitalkFiles.copy(binaryName = displayName(uri)))
        }
    }

    fun firebaseAndroidConfigSelected(uri: Uri?) {
        if (uri == null || !mutableState.value.tinitalkFiles.visible) return
        if (!preserveReadAccess(uri)) return
        selectedFirebaseAndroidConfigUri?.takeIf { it != uri }?.let(::releaseReadAccess)
        selectedFirebaseAndroidConfigUri = uri
        mutableState.update {
            it.copy(
                tinitalkFiles = it.tinitalkFiles.copy(
                    firebaseAndroidConfigName = displayName(uri),
                ),
            )
        }
    }

    fun firebaseServiceAccountSelected(uri: Uri?) {
        if (uri == null || !mutableState.value.tinitalkFiles.visible) return
        if (!preserveReadAccess(uri)) return
        selectedFirebaseServiceAccountUri?.takeIf { it != uri }?.let(::releaseReadAccess)
        selectedFirebaseServiceAccountUri = uri
        mutableState.update {
            it.copy(
                tinitalkFiles = it.tinitalkFiles.copy(
                    firebaseServiceAccountName = displayName(uri),
                ),
            )
        }
    }

    fun startInitialSetup(serverId: String) {
        if (
            mutableState.value.route != AdminRoute.ServerDetails(serverId) ||
            mutableState.value.initialSetup.mode !in setOf(
                InitialSetupUiMode.CLEAN,
                InitialSetupUiMode.PARTIAL,
            ) ||
            serverOperationJob?.isActive == true
        ) return
        val binary = selectedTiniTalkBinaryUri ?: return
        val androidConfig = selectedFirebaseAndroidConfigUri ?: return
        val serviceAccount = selectedFirebaseServiceAccountUri ?: return
        beginInitialSetup(
            serverId = serverId,
            completedSteps = mutableState.value.initialSetup.completedSteps,
            binaryUri = binary,
            firebaseAndroidConfigUri = androidConfig,
            firebaseServiceAccountUri = serviceAccount,
        )
    }

    private fun beginInitialSetup(
        serverId: String,
        completedSteps: Set<InitialSetupStep>,
        binaryUri: Uri? = null,
        firebaseAndroidConfigUri: Uri? = null,
        firebaseServiceAccountUri: Uri? = null,
    ) {
        val firstIncompleteStep = InitialSetupStep.entries.firstOrNull {
            it !in completedSteps
        } ?: return
        if (
            InitialSetupStep.UPLOAD_FILES !in completedSteps &&
            (binaryUri == null ||
                firebaseAndroidConfigUri == null ||
                firebaseServiceAccountUri == null)
        ) return
        val setup = StoredServerSetup(
            configured = false,
            startedAtEpochMillis = System.currentTimeMillis(),
            currentStep = firstIncompleteStep,
            completedSteps = completedSteps,
            binaryUri = binaryUri?.toString(),
            firebaseAndroidConfigUri = firebaseAndroidConfigUri?.toString(),
            firebaseServiceAccountUri = firebaseServiceAccountUri?.toString(),
        )
        serverSetupStore.put(serverId, setup)
        clearSelectedTiniTalkFiles()
        mutableState.update {
            it.copy(
                tinitalkFiles = TiniTalkFilesState(),
                initialSetup = setup.toUiState(),
            )
        }
        resumeInitialSetup(serverId)
    }

    fun retryInitialSetup(serverId: String) {
        if (mutableState.value.initialSetup.mode != InitialSetupUiMode.FAILED) return
        resumeInitialSetup(serverId)
    }

    private fun resumeInitialSetup(serverId: String) {
        if (serverOperationJob?.isActive == true) return
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        val setup = serverSetupStore.get(serverId)?.takeIf(StoredServerSetup::inProgress) ?: return
        mutableState.update { it.copy(initialSetup = setup.toUiState(), notice = null) }
        serverOperationJob = viewModelScope.launch {
            try {
                val connection = connectToServer(server)
                try {
                    runInitialSetup(connection, server, setup)
                } finally {
                    runCatching { connection.close() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                val failedSetup = serverSetupStore.get(serverId)
                    ?.takeIf(StoredServerSetup::inProgress)
                    ?: setup
                mutableState.update {
                    it.copy(
                        serverOperation = null,
                        initialSetup = failedSetup.toUiState(
                            mode = InitialSetupUiMode.FAILED,
                            errorMessage = setupErrorMessage(error),
                        ),
                    )
                }
            } finally {
                serverOperationJob = null
            }
        }
    }

    private suspend fun runInitialSetup(
        connection: SshConnection,
        server: ServerRecord,
        initial: StoredServerSetup,
    ) {
        var setup = initial
        while (true) {
            val step = checkNotNull(setup.currentStep)
            mutableState.update {
                it.copy(
                    serverOperation = null,
                    initialSetup = setup.toUiState(),
                )
            }
            var uploads = emptyList<RemoteOperationUpload>()
            try {
                var remote = if (setup.operationStarted) {
                    remoteOperationRunner.status(connection, server.sshLogin, step.operationKind)
                } else {
                    null
                }
                if (remote == null) {
                    if (step == InitialSetupStep.UPLOAD_FILES) {
                        uploads = loadSetupUploads(setup)
                    }
                    setup = setup.copy(operationStarted = true)
                    serverSetupStore.put(server.id, setup)
                    remote = remoteOperationRunner.start(
                        connection = connection,
                        login = server.sshLogin,
                        kind = step.operationKind,
                        arguments = setupArguments(server, step),
                        uploads = uploads,
                    )
                }
                remote = monitorInitialSetupOperation(connection, server, remote)
                if (remote.state != RemoteOperationState.SUCCEEDED) {
                    mutableState.update {
                        it.copy(
                            serverOperation = null,
                            initialSetup = setup.toUiState(
                                mode = InitialSetupUiMode.FAILED,
                                errorMessage = step.operationKind.failureMessage(),
                            ),
                        )
                    }
                    return
                }
            } finally {
                uploads.forEach { it.bytes.fill(0) }
            }

            val completedSteps = setup.completedStepSet + step
            setup = setup.copy(
                completedSteps = completedSteps,
                operationStarted = false,
            )
            serverSetupStore.put(server.id, setup)
            val next = InitialSetupStep.entries
                .drop(step.ordinal + 1)
                .firstOrNull { it !in completedSteps }
            if (next == null) {
                val assessment = initialSetupInspector.inspect(
                    connection = connection,
                    login = server.sshLogin,
                    serverAddress = server.enteredAddress,
                    sshPort = server.sshPort,
                ).assessment()
                if (assessment != ServerSetupAssessment.CONFIGURED) {
                    mutableState.update {
                        it.copy(
                            serverOperation = null,
                            initialSetup = setup.toUiState(
                                mode = InitialSetupUiMode.FAILED,
                                errorMessage = "Сервис TiniTalk не прошёл итоговую проверку",
                            ),
                        )
                    }
                    return
                }
                releaseSetupFiles(setup)
                serverSetupStore.put(server.id, StoredServerSetup(configured = true))
                mutableState.update {
                    it.copy(
                        serverOperation = null,
                        initialSetup = InitialSetupUiState(InitialSetupUiMode.CONFIGURED),
                    )
                }
                return
            }
            setup = setup.copy(currentStep = next)
            serverSetupStore.put(server.id, setup)
        }
    }

    private suspend fun monitorInitialSetupOperation(
        connection: SshConnection,
        server: ServerRecord,
        initial: RemoteServerOperation,
    ): RemoteServerOperation {
        var remote = initial
        val startedAt = SystemClock.elapsedRealtime() - initial.elapsedMillis
        while (remote.state == RemoteOperationState.RUNNING) {
            mutableState.update {
                it.copy(
                    serverOperation = RunningServerOperation(
                        serverId = server.id,
                        kind = remote.kind,
                        startedAt = startedAt,
                    ),
                )
            }
            delay(REMOTE_OPERATION_POLL_MILLIS)
            remote = remoteOperationRunner.status(connection, server.sshLogin, remote.kind)
                ?: error("Remote operation disappeared")
        }
        remoteOperationRunner.acknowledge(connection, server.sshLogin, remote.kind)
        return remote
    }

    private fun applySetupAssessment(
        server: ServerRecord,
        evidence: InitialSetupEvidence,
    ): ServerSetupAssessment {
        val assessment = evidence.assessment()
        val mode = when (assessment) {
            ServerSetupAssessment.CLEAN -> InitialSetupUiMode.CLEAN
            ServerSetupAssessment.PARTIAL -> InitialSetupUiMode.PARTIAL
            ServerSetupAssessment.CONFIGURED -> InitialSetupUiMode.CONFIGURED
        }
        if (assessment == ServerSetupAssessment.CONFIGURED) {
            serverSetupStore.put(server.id, StoredServerSetup(configured = true))
        } else {
            serverSetupStore.remove(server.id)
        }
        mutableState.update {
            it.copy(
                initialSetup = InitialSetupUiState(
                    mode = mode,
                    completedSteps = evidence.completedSteps(),
                ),
            )
        }
        return assessment
    }

    private fun setupArguments(server: ServerRecord, step: InitialSetupStep): List<String> = when (step) {
        InitialSetupStep.SYSTEM_PACKAGES -> emptyList()
        InitialSetupStep.FIREWALL -> listOf(server.sshPort.toString())
        InitialSetupStep.TLS_CERTIFICATE -> listOf(
            if (server.enteredAddress == server.frozenIpv4) "ip" else "domain",
            server.enteredAddress,
        )
        InitialSetupStep.PREPARE_TINITALK -> listOf(server.enteredAddress)
        InitialSetupStep.UPLOAD_FILES -> emptyList()
        InitialSetupStep.START_TINITALK -> listOf(server.enteredAddress, server.frozenIpv4)
    }

    private suspend fun loadSetupUploads(setup: StoredServerSetup): List<RemoteOperationUpload> =
        withContext(Dispatchers.IO) {
            listOf(
                readSetupUpload(setup.binaryUri, "tinitalk"),
                readSetupUpload(setup.firebaseAndroidConfigUri, "google-services.json"),
                readSetupUpload(
                    setup.firebaseServiceAccountUri,
                    "firebase-service-account.json",
                ),
            )
        }

    private fun readSetupUpload(uriValue: String?, remoteName: String): RemoteOperationUpload {
        val uri = uriValue?.let(Uri::parse) ?: error("Setup file selection is missing")
        val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Failed to open selected setup file")
        check(bytes.isNotEmpty()) { "Selected setup file is empty" }
        return RemoteOperationUpload(remoteName, bytes)
    }

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()?.takeIf(String::isNotBlank) ?: "Выбранный файл"

    private fun preserveReadAccess(uri: Uri): Boolean = runCatching {
        contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }.fold(
        onSuccess = { true },
        onFailure = {
            mutableState.update {
                it.copy(notice = "Не удалось сохранить доступ к выбранному файлу")
            }
            false
        },
    )

    private fun releaseSetupFiles(setup: StoredServerSetup) {
        listOf(
            setup.binaryUri,
            setup.firebaseAndroidConfigUri,
            setup.firebaseServiceAccountUri,
        ).filterNotNull().map(Uri::parse).forEach(::releaseReadAccess)
    }

    private fun discardSelectedTiniTalkFiles() {
        listOf(
            selectedTiniTalkBinaryUri,
            selectedFirebaseAndroidConfigUri,
            selectedFirebaseServiceAccountUri,
        ).filterNotNull().forEach(::releaseReadAccess)
        clearSelectedTiniTalkFiles()
    }

    private fun releaseReadAccess(uri: Uri) {
        runCatching {
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun clearSelectedTiniTalkFiles() {
        selectedTiniTalkBinaryUri = null
        selectedFirebaseAndroidConfigUri = null
        selectedFirebaseServiceAccountUri = null
    }

    fun renameServer(serverId: String, value: String) {
        if (mutableState.value.servers.none { it.id == serverId }) return
        val displayName = value.trim()
        viewModelScope.launch {
            updateSavedServers("Не удалось изменить название сервера") {
                serverStore.rename(serverId, displayName)
            }
        }
    }

    fun removeServer(serverId: String) {
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        viewModelScope.launch {
            val records = try {
                withContext(Dispatchers.IO) {
                    serverSetupStore.get(serverId)?.let(::releaseSetupFiles)
                    serverSetupStore.remove(serverId)
                    identityStore.delete(server.keystoreAlias)
                    serverStore.remove(serverId)
                    serverStore.list()
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { it.copy(notice = "Не удалось удалить сервер из приложения") }
                return@launch
            }
            mutableState.update {
                it.copy(
                    route = AdminRoute.ServerList,
                    servers = records,
                    notice = "Сервер удалён из приложения",
                )
            }
        }
    }

    fun clearNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    private suspend fun runSshCheck(server: ServerRecord): SshCheckResult {
        val connection = connectToServer(server)
        return try {
            val command = connection.exec(SSH_CHECK_COMMAND)
            check(command.exitCode == 0) { "SSH check command failed" }
            val lines = command.stdout.lineSequence()
                .map(String::trim)
                .filter(String::isNotEmpty)
                .toList()
            check(lines.size == 3) { "Unexpected SSH check output" }
            val status = tinitalkStatusChecker.check(connection)
            SshCheckResult(
                user = lines[0],
                host = lines[1],
                uptime = lines[2],
                operatingSystem = status.os,
                architecture = status.architecture,
            )
        } finally {
            runCatching { connection.close() }
        }
    }

    private suspend fun connectToServer(server: ServerRecord): SshConnection {
        val endpoint = ResolvedEndpoint(
            enteredAddress = server.enteredAddress,
            sshPort = server.sshPort,
            frozenIpv4 = server.frozenIpv4,
            addressKind = if (server.enteredAddress == server.frozenIpv4) {
                AddressKind.IPV4
            } else {
                AddressKind.DNS
            },
        )
        return sshAccessChecker.connect(
            endpoint = endpoint,
            login = server.sshLogin,
            credential = SshCredential.ManagedKey(server.keystoreAlias),
            pinnedHostKey = server.hostKey,
        )
    }

    private fun sshCheckErrorMessage(error: Exception): String = when (error) {
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        else -> "Не удалось проверить SSH-доступ"
    }

    private fun setupErrorMessage(error: Exception): String = when (error) {
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        else -> "Не удалось проверить или продолжить настройку сервера"
    }

    fun updateDisplayName(value: String) = updateAddServerForm { copy(displayName = value) }
    fun updateAddress(value: String) = updateAddServerForm { copy(address = value) }
    fun updateSshPort(value: String) = updateAddServerForm { copy(sshPort = value) }
    fun updateSshLogin(value: String) = updateAddServerForm { copy(sshLogin = value) }

    fun updateAuthentication(value: AuthenticationMethod) = updateAddServerForm {
        if (authentication == value) return@updateAddServerForm this
        selectedPrivateKeyUri = null
        copy(
            authentication = value,
            password = "",
            privateKeyPassphrase = "",
            privateKeySelected = false,
            errorMessage = null,
        )
    }

    fun updatePassword(value: String) = updateAddServerForm {
        copy(password = value, errorMessage = null)
    }

    fun updatePrivateKeyPassphrase(value: String) = updateAddServerForm {
        copy(privateKeyPassphrase = value, errorMessage = null)
    }

    fun scanFingerprint() {
        val form = mutableState.value.addServer
        val validated = try {
            validateEndpointForm(form)
        } catch (_: EndpointValidationException) {
            mutableState.update {
                it.copy(addServer = form.copy(errorMessage = "Проверьте адрес сервера и SSH-порт"))
            }
            return
        } catch (_: IllegalArgumentException) {
            mutableState.update {
                it.copy(addServer = form.copy(errorMessage = "Проверьте SSH-порт и логин"))
            }
            return
        }
        if (form.authentication == AuthenticationMethod.PASSWORD && form.password.isEmpty()) {
            mutableState.update {
                it.copy(addServer = form.copy(errorMessage = "Укажите SSH-пароль"))
            }
            return
        }
        if (
            form.authentication == AuthenticationMethod.PRIVATE_KEY &&
            selectedPrivateKeyUri == null
        ) {
            mutableState.update {
                it.copy(addServer = form.copy(errorMessage = "Выберите private key"))
            }
            return
        }

        val id = nextOperation()
        mutableState.update {
            it.copy(
                addServer = form.copy(
                    displayName = validated.displayName,
                    address = validated.address,
                    sshPort = validated.port.toString(),
                    sshLogin = validated.login,
                    frozenIpv4 = null,
                    phase = AddServerPhase.ScanningFingerprint,
                    errorMessage = null,
                ),
            )
        }
        currentJob = viewModelScope.launch {
            try {
                val endpoint = endpointResolver.resolve(validated.address, validated.port)
                val hostKey = sshAccessChecker.scanHostKey(endpoint)
                if (!isCurrent(id)) return@launch
                val savedPins = mutableState.value.servers.filter {
                    it.enteredAddress == endpoint.enteredAddress && it.sshPort == endpoint.sshPort
                }
                if (savedPins.any { !it.hostKey.sameKeyAs(hostKey) }) {
                    throw KnownHostKeyChangedException()
                }
                pendingEndpoint = endpoint
                pendingHostKey = hostKey
                mutableState.update {
                    it.copy(
                        addServer = it.addServer.copy(
                            address = endpoint.enteredAddress,
                            frozenIpv4 = endpoint.frozenIpv4,
                            phase = AddServerPhase.ConfirmFingerprint(hostKey),
                        ),
                    )
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(id)) failAdd(error, clearSecrets = false)
            } finally {
                if (isCurrent(id)) currentJob = null
            }
        }
    }

    fun rejectFingerprint() {
        invalidateOperation()
        mutableState.update {
            it.copy(
                addServer = it.addServer.copy(
                    frozenIpv4 = null,
                    phase = AddServerPhase.Form,
                    errorMessage = null,
                ),
            )
        }
    }

    fun confirmFingerprint() {
        if (pendingEndpoint == null || pendingHostKey == null) return
        val add = mutableState.value.addServer
        if (add.phase !is AddServerPhase.ConfirmFingerprint) return
        when (add.authentication) {
            AuthenticationMethod.PASSWORD -> {
                val password = add.password.toCharArray()
                verifyAccess(SshCredential.Password(password))
            }

            AuthenticationMethod.PRIVATE_KEY -> verifyAccessWithPrivateKey()
        }
    }

    fun privateKeySelected(uri: Uri?) {
        val add = mutableState.value.addServer
        if (
            add.phase != AddServerPhase.Form ||
            add.authentication != AuthenticationMethod.PRIVATE_KEY
        ) return
        if (uri == null) return
        selectedPrivateKeyUri = uri
        mutableState.update {
            it.copy(
                addServer = add.copy(
                    privateKeySelected = true,
                    errorMessage = null,
                ),
            )
        }
    }

    private fun verifyAccessWithPrivateKey() {
        val uri = selectedPrivateKeyUri ?: return
        val endpoint = pendingEndpoint ?: return
        val hostKey = pendingHostKey ?: return
        val add = mutableState.value.addServer
        val metadata = PendingServerMetadata(add.displayName, add.sshLogin)
        val passphrase = add.privateKeyPassphrase.toCharArray()
        val id = nextOperation()
        mutableState.update {
            it.copy(
                addServer = add.copy(
                    password = "",
                    privateKeyPassphrase = "",
                    phase = AddServerPhase.CheckingAccess,
                    errorMessage = null,
                ),
            )
        }
        currentJob = viewModelScope.launch {
            var credential: SshCredential? = null
            try {
                credential = SshCredential.ImportedKey(importedKeyReader.read(uri, passphrase))
                bootstrapAccess(id, endpoint, hostKey, metadata, credential)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(id)) failAdd(error, clearSecrets = true)
            } finally {
                credential?.close()
                passphrase.fill('\u0000')
                if (isCurrent(id)) currentJob = null
            }
        }
    }

    private fun verifyAccess(credential: SshCredential) {
        val endpoint = pendingEndpoint ?: return credential.close()
        val hostKey = pendingHostKey ?: return credential.close()
        val add = mutableState.value.addServer
        val metadata = PendingServerMetadata(add.displayName, add.sshLogin)
        val id = nextOperation()
        mutableState.update {
            it.copy(
                addServer = add.copy(
                    password = "",
                    privateKeyPassphrase = "",
                    phase = AddServerPhase.CheckingAccess,
                    errorMessage = null,
                ),
            )
        }
        currentJob = viewModelScope.launch {
            try {
                bootstrapAccess(id, endpoint, hostKey, metadata, credential)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(id)) failAdd(error, clearSecrets = true)
            } finally {
                credential.close()
                if (isCurrent(id)) currentJob = null
            }
        }
    }

    private suspend fun bootstrapAccess(
        operation: Long,
        endpoint: ResolvedEndpoint,
        hostKey: PinnedHostKey,
        metadata: PendingServerMetadata,
        credential: SshCredential,
    ) {
        if (!isCurrent(operation)) return
        if (mutableState.value.servers.any {
                it.enteredAddress == endpoint.enteredAddress &&
                    it.sshPort == endpoint.sshPort &&
                    it.sshLogin == metadata.sshLogin
            }
        ) {
            throw ServerAlreadyAddedException()
        }
        val serverId = UUID.randomUUID().toString()
        managedAccessBootstrapper.bootstrap(
            serverId = serverId,
            endpoint = endpoint,
            login = metadata.sshLogin,
            initialCredential = credential,
            hostKey = hostKey,
        ) { keystoreAlias ->
            if (!isCurrent(operation)) throw CancellationException("Operation was cancelled")
            persistSuccessfulCheck(serverId, endpoint, hostKey, keystoreAlias, metadata)
        }
    }

    private suspend fun persistSuccessfulCheck(
        serverId: String,
        endpoint: ResolvedEndpoint,
        hostKey: PinnedHostKey,
        keystoreAlias: String,
        metadata: PendingServerMetadata,
    ) {
        val record = ServerRecord(
            id = serverId,
            displayName = metadata.displayName,
            enteredAddress = endpoint.enteredAddress,
            frozenIpv4 = endpoint.frozenIpv4,
            sshPort = endpoint.sshPort,
            sshLogin = metadata.sshLogin,
            hostKey = hostKey,
            keystoreAlias = keystoreAlias,
            verifiedAtEpochMillis = System.currentTimeMillis(),
        )
        val records = try {
            withContext(Dispatchers.IO) {
                serverStore.put(record)
                serverStore.list()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (error: Exception) {
            throw LocalPersistenceException(error)
        }
        finishOperation()
        mutableState.value = AdminUiState(
            route = AdminRoute.ServerDetails(record.id),
            servers = records,
            notice = "Сервер добавлен",
        )
        inspectInitialSetup(
            serverId = record.id,
            continuation = InitialSetupContinuation.CLEAN_ONLY,
        )
    }

    private fun validateEndpointForm(form: AddServerState): ValidatedEndpointForm {
        val port = form.sshPort.toIntOrNull()
            ?: throw IllegalArgumentException("Invalid SSH port")
        val parsed = EndpointParser.parse(form.address, port)
        val login = form.sshLogin.trim()
        if (
            login.isEmpty() ||
            login.length > 128 ||
            login.any(Char::isWhitespace) ||
            login.any(Char::isISOControl)
        ) {
            throw IllegalArgumentException("Invalid SSH login")
        }
        return ValidatedEndpointForm(
            address = parsed.enteredAddress,
            port = parsed.sshPort,
            login = login,
            displayName = form.displayName.trim(),
        )
    }

    private fun failAdd(error: Exception, clearSecrets: Boolean) {
        pendingEndpoint = null
        pendingHostKey = null
        mutableState.update {
            val add = it.addServer
            it.copy(
                addServer = add.copy(
                    frozenIpv4 = null,
                    password = if (clearSecrets) "" else add.password,
                    privateKeyPassphrase = if (clearSecrets) "" else add.privateKeyPassphrase,
                    phase = AddServerPhase.Form,
                    errorMessage = safeErrorMessage(error),
                ),
            )
        }
    }

    private fun safeErrorMessage(error: Exception): String = when (error) {
        is DnsValidationException -> "Не удалось получить один публичный IPv4 для домена"
        is SshFailure.HostKeyChanged -> "SSH host key изменился. Начните проверку заново"
        is SshFailure.AuthenticationFailed -> "SSH-аутентификация не прошла"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        is MissingAdministrativeAccessException -> "Нужен root или sudo без пароля"
        is ImportedSshIdentityException -> "Не удалось прочитать SSH private key"
        is ServerAlreadyAddedException -> "Этот сервер уже добавлен"
        is KnownHostKeyChangedException ->
            "SSH host key отличается от сохранённого. Автоматическая замена запрещена"
        is LocalPersistenceException -> "Не удалось сохранить сервер"
        else -> "Не удалось настроить SSH-доступ"
    }

    private fun updateAddServerForm(transform: AddServerState.() -> AddServerState) {
        val add = mutableState.value.addServer
        if (add.phase != AddServerPhase.Form) return
        mutableState.update { it.copy(addServer = add.transform().copy(errorMessage = null)) }
    }

    private suspend fun updateSavedServers(
        errorMessage: String,
        update: () -> Unit,
    ) {
        val records = try {
            withContext(Dispatchers.IO) {
                update()
                serverStore.list()
            }
        } catch (error: CancellationException) {
            throw error
        } catch (_: Exception) {
            mutableState.update { it.copy(notice = errorMessage) }
            return
        }
        mutableState.update { it.copy(servers = records) }
    }

    private fun nextOperation(): Long {
        currentJob?.cancel()
        operationId += 1
        return operationId
    }

    private fun invalidateOperation() {
        currentJob?.cancel()
        currentJob = null
        operationId += 1
        pendingEndpoint = null
        pendingHostKey = null
    }

    private fun finishOperation() {
        currentJob = null
        operationId += 1
        pendingEndpoint = null
        pendingHostKey = null
        selectedPrivateKeyUri = null
    }

    private fun isCurrent(id: Long): Boolean = id == operationId

    private data class ValidatedEndpointForm(
        val address: String,
        val port: Int,
        val login: String,
        val displayName: String,
    )

    private data class PendingServerMetadata(
        val displayName: String,
        val sshLogin: String,
    )

    companion object {
        private const val SSH_CHECK_COMMAND =
            "LC_ALL=C; export LC_ALL; id -un && hostname && uptime -p"
        private const val REMOTE_OPERATION_POLL_MILLIS = 1_000L

        fun factory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(AdminViewModel::class.java))
                    val identityStore = AndroidKeystoreSshIdentityStore(applicationContext)
                    val sshAccessChecker = SshjAccessChecker(identityStore)
                    return AdminViewModel(
                        contentResolver = applicationContext.contentResolver,
                        serverStore = SharedPreferencesServerStore(applicationContext),
                        serverSetupStore = SharedPreferencesServerSetupStore(applicationContext),
                        endpointResolver = EndpointResolver(),
                        sshAccessChecker = sshAccessChecker,
                        importedKeyReader = ImportedKeyReader(applicationContext.contentResolver),
                        identityStore = identityStore,
                        managedAccessBootstrapper = ManagedAccessBootstrapper(
                            ssh = sshAccessChecker,
                            identityStore = identityStore,
                            installer = AuthorizedKeyInstaller(applicationContext),
                        ),
                        tinitalkStatusChecker = TiniTalkStatusChecker(applicationContext),
                        initialSetupInspector = InitialSetupInspector(applicationContext),
                        remoteOperationRunner = RemoteServerOperationRunner(applicationContext),
                    ) as T
                }
            }
        }
    }
}

private fun ServerOperationKind.failureMessage(): String = when (this) {
    ServerOperationKind.INSTALL_SYSTEM_PACKAGES -> "Не удалось установить системные пакеты"
    ServerOperationKind.CONFIGURE_FIREWALL -> "Не удалось настроить firewall"
    ServerOperationKind.OBTAIN_TLS_CERTIFICATE -> "Не удалось получить TLS-сертификат"
    ServerOperationKind.PREPARE_TINITALK -> "Не удалось подготовить TiniTalk"
    ServerOperationKind.INSTALL_TINITALK_FILES -> "Не удалось загрузить файлы TiniTalk"
    ServerOperationKind.START_TINITALK -> "Не удалось запустить TiniTalk"
}

private fun StoredServerSetup.toUiState(
    mode: InitialSetupUiMode = InitialSetupUiMode.RUNNING,
    errorMessage: String? = null,
) = InitialSetupUiState(
    mode = mode,
    startedAtEpochMillis = startedAtEpochMillis,
    currentStep = currentStep,
    completedSteps = completedStepSet,
    errorMessage = errorMessage,
)

private class KnownHostKeyChangedException : IllegalStateException()

private class ServerAlreadyAddedException : IllegalStateException()

private class LocalPersistenceException(cause: Throwable) :
    IllegalStateException("Failed to save verified server", cause)

private fun PinnedHostKey.sameKeyAs(other: PinnedHostKey): Boolean =
    algorithm == other.algorithm && sshWireKeyBase64 == other.sshWireKeyBase64
