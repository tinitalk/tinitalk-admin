package org.tinitalk.admin.ui

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.os.SystemClock
import android.provider.OpenableColumns
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.tinitalk.admin.data.ServerStore
import org.tinitalk.admin.data.SharedPreferencesServerStore
import org.tinitalk.admin.model.PinnedHostKey
import org.tinitalk.admin.model.ServerRecord
import org.tinitalk.admin.server.DnsValidationException
import org.tinitalk.admin.server.AddressKind
import org.tinitalk.admin.server.EndpointParser
import org.tinitalk.admin.server.EndpointResolver
import org.tinitalk.admin.server.EndpointValidationException
import org.tinitalk.admin.server.RemoteOperationState
import org.tinitalk.admin.server.RemoteOperationUpload
import org.tinitalk.admin.server.RemoteServerOperation
import org.tinitalk.admin.server.RemoteServerOperationRunner
import org.tinitalk.admin.server.ResolvedEndpoint
import org.tinitalk.admin.server.ServerOperationKind
import org.tinitalk.admin.server.TiniTalkStatus
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
    val tinitalkStatusInProgress: Boolean = false,
    val tinitalkStatusResult: TiniTalkStatus? = null,
    val serverOperation: RunningServerOperation? = null,
    val tinitalkFiles: TiniTalkFilesState = TiniTalkFilesState(),
    val notice: String? = null,
)

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
)

class AdminViewModel(
    private val contentResolver: ContentResolver,
    private val serverStore: ServerStore,
    private val endpointResolver: EndpointResolver,
    private val sshAccessChecker: SshAccessChecker,
    private val importedKeyReader: ImportedKeyReader,
    private val identityStore: ManagedSshIdentityStore,
    private val managedAccessBootstrapper: ManagedAccessBootstrapper,
    private val tinitalkStatusChecker: TiniTalkStatusChecker,
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
    private var tinitalkStatusJob: Job? = null
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
        tinitalkStatusJob?.cancel()
        tinitalkStatusJob = null
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerDetails(serverId),
                sshCheckInProgress = false,
                sshCheckResult = null,
                tinitalkStatusInProgress = false,
                tinitalkStatusResult = null,
                notice = null,
            )
        }
        discoverServerOperation(serverId)
    }

    fun closeServer() {
        sshCheckJob?.cancel()
        sshCheckJob = null
        tinitalkStatusJob?.cancel()
        tinitalkStatusJob = null
        clearSelectedTiniTalkFiles()
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerList,
                sshCheckInProgress = false,
                sshCheckResult = null,
                tinitalkStatusInProgress = false,
                tinitalkStatusResult = null,
                tinitalkFiles = TiniTalkFilesState(),
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

    fun checkTiniTalkStatus(serverId: String) {
        if (tinitalkStatusJob?.isActive == true) return
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        mutableState.update {
            it.copy(
                tinitalkStatusInProgress = true,
                tinitalkStatusResult = null,
                notice = null,
            )
        }
        tinitalkStatusJob = viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.IO) { runTiniTalkStatusCheck(server) }
                if (mutableState.value.route == AdminRoute.ServerDetails(serverId)) {
                    mutableState.update {
                        it.copy(tinitalkStatusInProgress = false, tinitalkStatusResult = result)
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (mutableState.value.route == AdminRoute.ServerDetails(serverId)) {
                    mutableState.update {
                        it.copy(
                            tinitalkStatusInProgress = false,
                            notice = tinitalkStatusErrorMessage(error),
                        )
                    }
                }
            } finally {
                tinitalkStatusJob = null
            }
        }
    }

    fun dismissTiniTalkStatus() {
        mutableState.update { it.copy(tinitalkStatusResult = null) }
    }

    fun installSystemPackages(serverId: String) {
        startServerOperation(
            serverId = serverId,
            kind = ServerOperationKind.INSTALL_SYSTEM_PACKAGES,
            arguments = emptyList(),
        )
    }

    fun configureFirewall(serverId: String) {
        startServerOperation(
            serverId = serverId,
            kind = ServerOperationKind.CONFIGURE_FIREWALL,
            arguments = listOfNotNull(
                mutableState.value.servers.firstOrNull { it.id == serverId }?.sshPort?.toString(),
            ),
        )
    }

    fun obtainTlsCertificate(serverId: String) {
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        val addressType = if (server.enteredAddress == server.frozenIpv4) "ip" else "domain"
        startServerOperation(
            serverId = serverId,
            kind = ServerOperationKind.OBTAIN_TLS_CERTIFICATE,
            arguments = listOf(addressType, server.enteredAddress),
        )
    }

    fun prepareTiniTalk(serverId: String) {
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        startServerOperation(
            serverId = serverId,
            kind = ServerOperationKind.PREPARE_TINITALK,
            arguments = listOf(server.enteredAddress),
        )
    }

    fun openTiniTalkFiles() {
        if (mutableState.value.serverOperation != null) return
        clearSelectedTiniTalkFiles()
        mutableState.update { it.copy(tinitalkFiles = TiniTalkFilesState(visible = true)) }
    }

    fun closeTiniTalkFiles() {
        clearSelectedTiniTalkFiles()
        mutableState.update { it.copy(tinitalkFiles = TiniTalkFilesState()) }
    }

    fun tinitalkBinarySelected(uri: Uri?) {
        if (uri == null || !mutableState.value.tinitalkFiles.visible) return
        selectedTiniTalkBinaryUri = uri
        mutableState.update {
            it.copy(tinitalkFiles = it.tinitalkFiles.copy(binaryName = displayName(uri)))
        }
    }

    fun firebaseAndroidConfigSelected(uri: Uri?) {
        if (uri == null || !mutableState.value.tinitalkFiles.visible) return
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
        selectedFirebaseServiceAccountUri = uri
        mutableState.update {
            it.copy(
                tinitalkFiles = it.tinitalkFiles.copy(
                    firebaseServiceAccountName = displayName(uri),
                ),
            )
        }
    }

    fun installTiniTalkFiles(serverId: String) {
        val binary = selectedTiniTalkBinaryUri ?: return
        val androidConfig = selectedFirebaseAndroidConfigUri ?: return
        val serviceAccount = selectedFirebaseServiceAccountUri ?: return
        val uploads = listOf(
            LocalOperationUpload(binary, "tinitalk"),
            LocalOperationUpload(androidConfig, "google-services.json"),
            LocalOperationUpload(serviceAccount, "firebase-service-account.json"),
        )
        clearSelectedTiniTalkFiles()
        mutableState.update { it.copy(tinitalkFiles = TiniTalkFilesState()) }
        startServerOperation(
            serverId = serverId,
            kind = ServerOperationKind.INSTALL_TINITALK_FILES,
            arguments = emptyList(),
            localUploads = uploads,
        )
    }

    fun startTiniTalk(serverId: String) {
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        startServerOperation(
            serverId = serverId,
            kind = ServerOperationKind.START_TINITALK,
            arguments = listOf(server.enteredAddress, server.frozenIpv4),
        )
    }

    private fun startServerOperation(
        serverId: String,
        kind: ServerOperationKind,
        arguments: List<String>,
        localUploads: List<LocalOperationUpload> = emptyList(),
    ) {
        if (serverOperationJob?.isActive == true) return
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        mutableState.update {
            it.copy(
                serverOperation = RunningServerOperation(
                    serverId = serverId,
                    kind = kind,
                    startedAt = SystemClock.elapsedRealtime(),
                ),
                notice = null,
            )
        }
        serverOperationJob = viewModelScope.launch {
            var uploads = emptyList<RemoteOperationUpload>()
            try {
                uploads = withContext(Dispatchers.IO) {
                    localUploads.map { upload ->
                        val bytes = contentResolver.openInputStream(upload.uri)?.use { it.readBytes() }
                            ?: error("Failed to open selected file")
                        check(bytes.isNotEmpty()) { "Selected file is empty" }
                        RemoteOperationUpload(upload.remoteName, bytes)
                    }
                }
                val connection = connectToServer(server)
                try {
                    val remote = remoteOperationRunner.start(
                        connection = connection,
                        login = server.sshLogin,
                        kind = kind,
                        arguments = arguments,
                        uploads = uploads,
                    )
                    monitorServerOperation(connection, server, remote)
                } finally {
                    runCatching { connection.close() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                mutableState.update {
                    it.copy(
                        serverOperation = null,
                        notice = serverOperationErrorMessage(error),
                    )
                }
            } finally {
                uploads.forEach { it.bytes.fill(0) }
                serverOperationJob = null
            }
        }
    }

    private fun displayName(uri: Uri): String = runCatching {
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }.getOrNull()?.takeIf(String::isNotBlank) ?: "Выбранный файл"

    private fun clearSelectedTiniTalkFiles() {
        selectedTiniTalkBinaryUri = null
        selectedFirebaseAndroidConfigUri = null
        selectedFirebaseServiceAccountUri = null
    }

    private fun discoverServerOperation(serverId: String) {
        if (serverOperationJob?.isActive == true || mutableState.value.serverOperation != null) return
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        serverOperationJob = viewModelScope.launch {
            try {
                val connection = connectToServer(server)
                try {
                    remoteOperationRunner.find(connection, server.sshLogin)?.let { remote ->
                        monitorServerOperation(connection, server, remote)
                    }
                } finally {
                    runCatching { connection.close() }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                // Status actions still work if no recoverable operation can be read.
            } finally {
                serverOperationJob = null
            }
        }
    }

    private suspend fun monitorServerOperation(
        connection: SshConnection,
        server: ServerRecord,
        initial: RemoteServerOperation,
    ) {
        var remote = initial
        while (remote.state == RemoteOperationState.RUNNING) {
            mutableState.update {
                it.copy(
                    serverOperation = RunningServerOperation(
                        serverId = server.id,
                        kind = remote.kind,
                        startedAt = SystemClock.elapsedRealtime() - remote.elapsedMillis,
                    ),
                )
            }
            delay(REMOTE_OPERATION_POLL_MILLIS)
            remote = remoteOperationRunner.status(connection, server.sshLogin, remote.kind)
                ?: error("Remote operation disappeared")
        }

        remoteOperationRunner.acknowledge(connection, server.sshLogin, remote.kind)
        mutableState.update {
            it.copy(
                serverOperation = null,
                notice = if (remote.state == RemoteOperationState.SUCCEEDED) {
                    remote.kind.successMessage()
                } else {
                    remote.kind.failureMessage()
                },
            )
        }
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
        val command = try {
            connection.exec(SSH_CHECK_COMMAND)
        } finally {
            runCatching { connection.close() }
        }
        check(command.exitCode == 0) { "SSH check command failed" }
        val lines = command.stdout.lineSequence().map(String::trim).filter(String::isNotEmpty).toList()
        check(lines.size == 3) { "Unexpected SSH check output" }
        return SshCheckResult(user = lines[0], host = lines[1], uptime = lines[2])
    }

    private suspend fun runTiniTalkStatusCheck(server: ServerRecord): TiniTalkStatus {
        val connection = connectToServer(server)
        return try {
            tinitalkStatusChecker.check(connection)
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

    private fun tinitalkStatusErrorMessage(error: Exception): String = when (error) {
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        else -> "Не удалось получить статус TiniTalk"
    }

    private fun serverOperationErrorMessage(error: Exception): String = when (error) {
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "Операция не завершилась вовремя"
        else -> "Не удалось выполнить операцию; она может продолжаться на сервере"
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
            route = AdminRoute.ServerList,
            servers = records,
            notice = "Сервер добавлен",
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

    private data class LocalOperationUpload(
        val uri: Uri,
        val remoteName: String,
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
                        remoteOperationRunner = RemoteServerOperationRunner(applicationContext),
                    ) as T
                }
            }
        }
    }
}

private fun ServerOperationKind.successMessage(): String = when (this) {
    ServerOperationKind.INSTALL_SYSTEM_PACKAGES -> "Системные пакеты установлены"
    ServerOperationKind.CONFIGURE_FIREWALL -> "Firewall настроен"
    ServerOperationKind.OBTAIN_TLS_CERTIFICATE -> "TLS-сертификат получен"
    ServerOperationKind.PREPARE_TINITALK -> "TiniTalk подготовлен"
    ServerOperationKind.INSTALL_TINITALK_FILES -> "Файлы TiniTalk загружены"
    ServerOperationKind.START_TINITALK -> "TiniTalk запущен"
}

private fun ServerOperationKind.failureMessage(): String = when (this) {
    ServerOperationKind.INSTALL_SYSTEM_PACKAGES -> "Не удалось установить системные пакеты"
    ServerOperationKind.CONFIGURE_FIREWALL -> "Не удалось настроить firewall"
    ServerOperationKind.OBTAIN_TLS_CERTIFICATE -> "Не удалось получить TLS-сертификат"
    ServerOperationKind.PREPARE_TINITALK -> "Не удалось подготовить TiniTalk"
    ServerOperationKind.INSTALL_TINITALK_FILES -> "Не удалось загрузить файлы TiniTalk"
    ServerOperationKind.START_TINITALK -> "Не удалось запустить TiniTalk"
}

private class KnownHostKeyChangedException : IllegalStateException()

private class ServerAlreadyAddedException : IllegalStateException()

private class LocalPersistenceException(cause: Throwable) :
    IllegalStateException("Failed to save verified server", cause)

private fun PinnedHostKey.sameKeyAs(other: PinnedHostKey): Boolean =
    algorithm == other.algorithm && sshWireKeyBase64 == other.sshWireKeyBase64
