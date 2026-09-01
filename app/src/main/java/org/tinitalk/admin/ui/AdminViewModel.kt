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
import org.tinitalk.admin.data.StoredServerSetupStatus
import org.tinitalk.admin.data.forRetry
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
import org.tinitalk.admin.server.ServerUser
import org.tinitalk.admin.server.ServerUserAdministrativeAccessException
import org.tinitalk.admin.server.ServerUserAlreadyExistsException
import org.tinitalk.admin.server.ServerUserCommandUnavailableException
import org.tinitalk.admin.server.ServerUserNotFoundException
import org.tinitalk.admin.server.ServerUserStorageException
import org.tinitalk.admin.server.ServerUsersReader
import org.tinitalk.admin.server.TiniTalkHealthChecker
import org.tinitalk.admin.server.TiniTalkHealthHttpException
import org.tinitalk.admin.server.TiniTalkHealthInfo
import org.tinitalk.admin.server.TiniTalkStatusChecker
import org.tinitalk.admin.server.UnexpectedTiniTalkServiceException
import org.tinitalk.admin.server.UnhealthyTiniTalkServiceException
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
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.UUID
import javax.net.ssl.SSLException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
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
    data class ServerUsers(val serverId: String) : AdminRoute
    data class AddServerUser(val serverId: String) : AdminRoute
    data class ServerUserDetails(val serverId: String, val user: ServerUser) : AdminRoute
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
    val serverConnectivity: ServerConnectivityUiState = ServerConnectivityUiState(),
    val serverOperation: RunningServerOperation? = null,
    val initialSetup: InitialSetupUiState = InitialSetupUiState(),
    val serverUsers: ServerUsersUiState = ServerUsersUiState(),
    val addServerUser: AddServerUserState = AddServerUserState(),
    val serverUserDetails: ServerUserDetailsUiState = ServerUserDetailsUiState(),
    val binarySelection: TiniTalkBinaryState = TiniTalkBinaryState(),
    val notice: String? = null,
)

data class ServerUsersUiState(
    val loading: Boolean = false,
    val users: List<ServerUser> = emptyList(),
    val errorMessage: String? = null,
)

data class AddServerUserState(
    val login: String = "",
    val displayName: String = "",
    val submitting: Boolean = false,
    val loginError: String? = null,
    val displayNameError: String? = null,
    val errorMessage: String? = null,
    val token: String? = null,
)

data class ServerUserDetailsUiState(
    val deleting: Boolean = false,
    val rotatingToken: Boolean = false,
    val changingAccess: Boolean = false,
    val renaming: Boolean = false,
    val renameDialogVisible: Boolean = false,
    val renameDraft: String = "",
    val renameErrorMessage: String? = null,
    val errorMessage: String? = null,
    val token: String? = null,
) {
    val busy: Boolean
        get() = deleting || rotatingToken || changingAccess || renaming
}

enum class InitialSetupUiMode {
    UNKNOWN,
    CHECKING,
    CLEAN,
    PARTIAL,
    RUNNING,
    FAILED,
    CONFIGURED,
    SSH_HOST_KEY_CHANGED,
}

data class InitialSetupUiState(
    val mode: InitialSetupUiMode = InitialSetupUiMode.UNKNOWN,
    val startedAtEpochMillis: Long? = null,
    val currentStep: InitialSetupStep? = null,
    val completedSteps: Set<InitialSetupStep> = emptySet(),
    val errorMessage: String? = null,
    val observedFingerprint: String? = null,
    val hostKeyCheckInProgress: Boolean = false,
)

private enum class InitialSetupContinuation {
    NONE,
    CLEAN_ONLY,
    ANY_INCOMPLETE,
}

data class TiniTalkBinaryState(
    val visible: Boolean = false,
    val binaryName: String? = null,
) {
    val ready: Boolean
        get() = binaryName != null
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

sealed interface SshConnectivityStatus {
    data object Checking : SshConnectivityStatus
    data class Available(val details: SshCheckResult) : SshConnectivityStatus
    data class Unavailable(val message: String) : SshConnectivityStatus
}

sealed interface TiniTalkApiConnectivityStatus {
    data object Checking : TiniTalkApiConnectivityStatus
    data class Available(val details: TiniTalkHealthInfo) : TiniTalkApiConnectivityStatus
    data class Unavailable(val message: String) : TiniTalkApiConnectivityStatus
}

data class ServerConnectivityUiState(
    val visible: Boolean = false,
    val ssh: SshConnectivityStatus = SshConnectivityStatus.Checking,
    val api: TiniTalkApiConnectivityStatus = TiniTalkApiConnectivityStatus.Checking,
) {
    val inProgress: Boolean
        get() = visible && (
            ssh is SshConnectivityStatus.Checking ||
                api is TiniTalkApiConnectivityStatus.Checking
            )
}

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
    private val serverUsersReader: ServerUsersReader,
    private val tinitalkHealthChecker: TiniTalkHealthChecker,
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
    private var serverConnectivityJob: Job? = null
    private var serverUsersJob: Job? = null
    private var addServerUserJob: Job? = null
    private var deleteServerUserJob: Job? = null
    private var rotateServerUserTokenJob: Job? = null
    private var changeServerUserAccessJob: Job? = null
    private var renameServerUserJob: Job? = null
    private var serverOperationJob: Job? = null
    private var pendingEndpoint: ResolvedEndpoint? = null
    private var pendingHostKey: PinnedHostKey? = null
    private var selectedPrivateKeyUri: Uri? = null
    private var selectedTiniTalkBinaryUri: Uri? = null

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
        serverConnectivityJob?.cancel()
        serverConnectivityJob = null
        val savedSetup = runCatching { serverSetupStore.get(serverId) }.getOrNull()
        val setupState = when {
            savedSetup?.hostKeyChanged == true -> InitialSetupUiState(
                mode = InitialSetupUiMode.SSH_HOST_KEY_CHANGED,
                observedFingerprint = savedSetup.observedFingerprint,
            )
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
                serverConnectivity = ServerConnectivityUiState(),
                initialSetup = setupState,
                notice = null,
            )
        }
        if (savedSetup?.inProgress == true) resumeInitialSetup(serverId)
    }

    fun closeServer() {
        serverConnectivityJob?.cancel()
        serverConnectivityJob = null
        serverUsersJob?.cancel()
        serverUsersJob = null
        addServerUserJob?.cancel()
        addServerUserJob = null
        deleteServerUserJob?.cancel()
        deleteServerUserJob = null
        rotateServerUserTokenJob?.cancel()
        rotateServerUserTokenJob = null
        changeServerUserAccessJob?.cancel()
        changeServerUserAccessJob = null
        renameServerUserJob?.cancel()
        renameServerUserJob = null
        discardSelectedTiniTalkBinary()
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerList,
                serverConnectivity = ServerConnectivityUiState(),
                serverUsers = ServerUsersUiState(),
                addServerUser = AddServerUserState(),
                serverUserDetails = ServerUserDetailsUiState(),
                binarySelection = TiniTalkBinaryState(),
                initialSetup = InitialSetupUiState(),
            )
        }
    }

    fun openServerUsers(serverId: String) {
        val currentState = mutableState.value
        if (
            currentState.route != AdminRoute.ServerDetails(serverId) ||
            currentState.initialSetup.mode != InitialSetupUiMode.CONFIGURED
        ) return
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerUsers(serverId),
                serverUsers = ServerUsersUiState(loading = true),
                notice = null,
            )
        }
        loadServerUsers(serverId)
    }

    fun closeServerUsers() {
        val route = mutableState.value.route as? AdminRoute.ServerUsers ?: return
        serverUsersJob?.cancel()
        serverUsersJob = null
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerDetails(route.serverId),
                serverUsers = ServerUsersUiState(),
            )
        }
    }

    fun retryServerUsers() {
        val route = mutableState.value.route as? AdminRoute.ServerUsers ?: return
        if (serverUsersJob?.isActive == true) return
        mutableState.update {
            it.copy(serverUsers = ServerUsersUiState(loading = true))
        }
        loadServerUsers(route.serverId)
    }

    fun openAddServerUser() {
        val route = mutableState.value.route as? AdminRoute.ServerUsers ?: return
        val users = mutableState.value.serverUsers
        if (users.loading || users.errorMessage != null) return
        mutableState.update {
            it.copy(
                route = AdminRoute.AddServerUser(route.serverId),
                addServerUser = AddServerUserState(),
            )
        }
    }

    fun closeAddServerUser() {
        val route = mutableState.value.route as? AdminRoute.AddServerUser ?: return
        val add = mutableState.value.addServerUser
        if (add.submitting || add.token != null) return
        addServerUserJob?.cancel()
        addServerUserJob = null
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerUsers(route.serverId),
                addServerUser = AddServerUserState(),
            )
        }
    }

    fun updateServerUserLogin(value: String) {
        updateAddServerUser {
            copy(login = value, loginError = null, errorMessage = null)
        }
    }

    fun updateServerUserDisplayName(value: String) {
        updateAddServerUser {
            copy(displayName = value, displayNameError = null, errorMessage = null)
        }
    }

    fun submitServerUser() {
        if (addServerUserJob?.isActive == true) return
        val route = mutableState.value.route as? AdminRoute.AddServerUser ?: return
        val server = mutableState.value.servers.firstOrNull { it.id == route.serverId } ?: return
        val form = mutableState.value.addServerUser
        if (form.token != null) return
        val login = form.login.trim()
        val displayName = form.displayName.trim()
        val loginError = when {
            login.isEmpty() -> "Укажите логин"
            login.length > MAX_SERVER_USER_LOGIN_LENGTH ->
                "Логин должен быть не длиннее $MAX_SERVER_USER_LOGIN_LENGTH символов"
            login == "--data-dir" -> "Этот логин зарезервирован"
            !login.matches(SERVER_USER_LOGIN_PATTERN) ->
                "Используйте латинские буквы, цифры, точку, дефис или подчёркивание"
            else -> null
        }
        val displayNameError = when {
            displayName.isEmpty() -> "Укажите имя"
            displayName.length > MAX_SERVER_USER_DISPLAY_NAME_LENGTH ->
                "Имя должно быть не длиннее $MAX_SERVER_USER_DISPLAY_NAME_LENGTH символов"
            displayName == "--data-dir" -> "Выберите другое имя"
            displayName.any(Char::isISOControl) -> "Имя содержит недопустимые символы"
            else -> null
        }
        if (loginError != null || displayNameError != null) {
            mutableState.update {
                it.copy(
                    addServerUser = form.copy(
                        login = login,
                        displayName = displayName,
                        loginError = loginError,
                        displayNameError = displayNameError,
                        errorMessage = null,
                    ),
                )
            }
            return
        }
        mutableState.update {
            it.copy(
                addServerUser = form.copy(
                    login = login,
                    displayName = displayName,
                    submitting = true,
                    loginError = null,
                    displayNameError = null,
                    errorMessage = null,
                ),
            )
        }
        addServerUserJob = viewModelScope.launch {
            try {
                val added = withContext(Dispatchers.IO) {
                    val connection = connectToServer(server)
                    try {
                        serverUsersReader.add(
                            connection = connection,
                            sshLogin = server.sshLogin,
                            login = login,
                            displayName = displayName,
                        )
                    } finally {
                        runCatching { connection.close() }
                    }
                }
                if (mutableState.value.route == route) {
                    mutableState.update { state ->
                        state.copy(
                            serverUsers = state.serverUsers.copy(
                                users = (state.serverUsers.users + added.user)
                                    .sortedBy { it.login },
                            ),
                            addServerUser = state.addServerUser.copy(
                                submitting = false,
                                token = added.token,
                            ),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (mutableState.value.route == route) {
                    mutableState.update {
                        it.copy(
                            addServerUser = it.addServerUser.copy(
                                submitting = false,
                                errorMessage = serverUserAddErrorMessage(error),
                            ),
                        )
                    }
                }
            } finally {
                addServerUserJob = null
            }
        }
    }

    fun serverUserTokenCopied() {
        val route = mutableState.value.route as? AdminRoute.AddServerUser ?: return
        if (mutableState.value.addServerUser.token == null) return
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerUsers(route.serverId),
                addServerUser = AddServerUserState(),
            )
        }
    }

    fun openServerUser(login: String) {
        val route = mutableState.value.route as? AdminRoute.ServerUsers ?: return
        val user = mutableState.value.serverUsers.users.firstOrNull { it.login == login } ?: return
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerUserDetails(route.serverId, user),
                serverUserDetails = ServerUserDetailsUiState(),
            )
        }
    }

    fun closeServerUser() {
        val route = mutableState.value.route as? AdminRoute.ServerUserDetails ?: return
        val details = mutableState.value.serverUserDetails
        if (details.busy || details.token != null) return
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerUsers(route.serverId),
                serverUserDetails = ServerUserDetailsUiState(),
            )
        }
    }

    fun deleteServerUser() {
        if (
            deleteServerUserJob?.isActive == true ||
            rotateServerUserTokenJob?.isActive == true ||
            changeServerUserAccessJob?.isActive == true ||
            renameServerUserJob?.isActive == true ||
            mutableState.value.serverUserDetails.token != null
        ) return
        val route = mutableState.value.route as? AdminRoute.ServerUserDetails ?: return
        val server = mutableState.value.servers.firstOrNull { it.id == route.serverId } ?: return
        mutableState.update {
            it.copy(serverUserDetails = ServerUserDetailsUiState(deleting = true))
        }
        deleteServerUserJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val connection = connectToServer(server)
                    try {
                        serverUsersReader.delete(
                            connection = connection,
                            sshLogin = server.sshLogin,
                            login = route.user.login,
                        )
                    } finally {
                        runCatching { connection.close() }
                    }
                }
                if (mutableState.value.route == route) {
                    mutableState.update { state ->
                        state.copy(
                            route = AdminRoute.ServerUsers(route.serverId),
                            serverUsers = state.serverUsers.copy(
                                users = state.serverUsers.users.filterNot {
                                    it.login == route.user.login
                                },
                            ),
                            serverUserDetails = ServerUserDetailsUiState(),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (mutableState.value.route == route) {
                    mutableState.update {
                        it.copy(
                            serverUserDetails = ServerUserDetailsUiState(
                                errorMessage = serverUserDeleteErrorMessage(error),
                            ),
                        )
                    }
                }
            } finally {
                deleteServerUserJob = null
            }
        }
    }

    fun rotateServerUserToken() {
        if (
            rotateServerUserTokenJob?.isActive == true ||
            deleteServerUserJob?.isActive == true ||
            changeServerUserAccessJob?.isActive == true ||
            renameServerUserJob?.isActive == true ||
            mutableState.value.serverUserDetails.token != null
        ) return
        val route = mutableState.value.route as? AdminRoute.ServerUserDetails ?: return
        val server = mutableState.value.servers.firstOrNull { it.id == route.serverId } ?: return
        mutableState.update {
            it.copy(serverUserDetails = ServerUserDetailsUiState(rotatingToken = true))
        }
        rotateServerUserTokenJob = viewModelScope.launch {
            try {
                val token = withContext(Dispatchers.IO) {
                    val connection = connectToServer(server)
                    try {
                        serverUsersReader.rotateToken(
                            connection = connection,
                            sshLogin = server.sshLogin,
                            login = route.user.login,
                        )
                    } finally {
                        runCatching { connection.close() }
                    }
                }
                if (mutableState.value.route == route) {
                    mutableState.update {
                        it.copy(
                            serverUserDetails = ServerUserDetailsUiState(token = token),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (mutableState.value.route == route) {
                    mutableState.update {
                        it.copy(
                            serverUserDetails = ServerUserDetailsUiState(
                                errorMessage = serverUserTokenErrorMessage(error),
                            ),
                        )
                    }
                }
            } finally {
                rotateServerUserTokenJob = null
            }
        }
    }

    fun serverUserRotatedTokenCopied() {
        if (
            mutableState.value.route !is AdminRoute.ServerUserDetails ||
            mutableState.value.serverUserDetails.token == null
        ) return
        mutableState.update {
            it.copy(serverUserDetails = ServerUserDetailsUiState())
        }
    }

    fun changeServerUserAccess() {
        if (
            changeServerUserAccessJob?.isActive == true ||
            rotateServerUserTokenJob?.isActive == true ||
            deleteServerUserJob?.isActive == true ||
            renameServerUserJob?.isActive == true ||
            mutableState.value.serverUserDetails.token != null
        ) return
        val route = mutableState.value.route as? AdminRoute.ServerUserDetails ?: return
        val server = mutableState.value.servers.firstOrNull { it.id == route.serverId } ?: return
        val user = mutableState.value.serverUsers.users
            .firstOrNull { it.login == route.user.login } ?: return
        val disabled = !user.disabled
        mutableState.update {
            it.copy(serverUserDetails = ServerUserDetailsUiState(changingAccess = true))
        }
        changeServerUserAccessJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val connection = connectToServer(server)
                    try {
                        serverUsersReader.setDisabled(
                            connection = connection,
                            sshLogin = server.sshLogin,
                            login = user.login,
                            disabled = disabled,
                        )
                    } finally {
                        runCatching { connection.close() }
                    }
                }
                if (mutableState.value.route == route) {
                    mutableState.update { state ->
                        state.copy(
                            serverUsers = state.serverUsers.copy(
                                users = state.serverUsers.users.map {
                                    if (it.login == user.login) it.copy(disabled = disabled) else it
                                },
                            ),
                            serverUserDetails = ServerUserDetailsUiState(),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (mutableState.value.route == route) {
                    mutableState.update {
                        it.copy(
                            serverUserDetails = ServerUserDetailsUiState(
                                errorMessage = serverUserAccessErrorMessage(error, disabled),
                            ),
                        )
                    }
                }
            } finally {
                changeServerUserAccessJob = null
            }
        }
    }

    fun openServerUserRename() {
        val route = mutableState.value.route as? AdminRoute.ServerUserDetails ?: return
        val details = mutableState.value.serverUserDetails
        if (details.busy || details.token != null) return
        val user = mutableState.value.serverUsers.users
            .firstOrNull { it.login == route.user.login } ?: route.user
        mutableState.update {
            it.copy(
                serverUserDetails = details.copy(
                    renameDialogVisible = true,
                    renameDraft = user.displayName,
                    renameErrorMessage = null,
                    errorMessage = null,
                ),
            )
        }
    }

    fun closeServerUserRename() {
        val details = mutableState.value.serverUserDetails
        if (!details.renameDialogVisible || details.renaming) return
        mutableState.update {
            it.copy(
                serverUserDetails = details.copy(
                    renameDialogVisible = false,
                    renameDraft = "",
                    renameErrorMessage = null,
                ),
            )
        }
    }

    fun updateServerUserRenameDraft(value: String) {
        val details = mutableState.value.serverUserDetails
        if (!details.renameDialogVisible || details.renaming) return
        mutableState.update {
            it.copy(
                serverUserDetails = details.copy(
                    renameDraft = value,
                    renameErrorMessage = null,
                ),
            )
        }
    }

    fun renameServerUser() {
        if (
            renameServerUserJob?.isActive == true ||
            rotateServerUserTokenJob?.isActive == true ||
            changeServerUserAccessJob?.isActive == true ||
            deleteServerUserJob?.isActive == true
        ) return
        val route = mutableState.value.route as? AdminRoute.ServerUserDetails ?: return
        val server = mutableState.value.servers.firstOrNull { it.id == route.serverId } ?: return
        val user = mutableState.value.serverUsers.users
            .firstOrNull { it.login == route.user.login } ?: route.user
        val details = mutableState.value.serverUserDetails
        if (!details.renameDialogVisible || details.token != null) return
        val displayName = details.renameDraft.trim()
        val validationError = when {
            displayName.isEmpty() -> "Укажите имя"
            displayName.length > MAX_SERVER_USER_DISPLAY_NAME_LENGTH ->
                "Имя должно быть не длиннее $MAX_SERVER_USER_DISPLAY_NAME_LENGTH символов"
            displayName == "--data-dir" -> "Выберите другое имя"
            displayName.any(Char::isISOControl) -> "Имя содержит недопустимые символы"
            else -> null
        }
        if (validationError != null) {
            mutableState.update {
                it.copy(
                    serverUserDetails = details.copy(
                        renameDraft = displayName,
                        renameErrorMessage = validationError,
                    ),
                )
            }
            return
        }
        if (displayName == user.displayName) {
            mutableState.update { it.copy(serverUserDetails = ServerUserDetailsUiState()) }
            return
        }
        mutableState.update {
            it.copy(
                serverUserDetails = details.copy(
                    renaming = true,
                    renameDraft = displayName,
                    renameErrorMessage = null,
                    errorMessage = null,
                ),
            )
        }
        renameServerUserJob = viewModelScope.launch {
            try {
                withContext(Dispatchers.IO) {
                    val connection = connectToServer(server)
                    try {
                        serverUsersReader.rename(
                            connection = connection,
                            sshLogin = server.sshLogin,
                            login = user.login,
                            displayName = displayName,
                        )
                    } finally {
                        runCatching { connection.close() }
                    }
                }
                if (mutableState.value.route == route) {
                    mutableState.update { state ->
                        state.copy(
                            serverUsers = state.serverUsers.copy(
                                users = state.serverUsers.users.map {
                                    if (it.login == user.login) {
                                        it.copy(displayName = displayName)
                                    } else {
                                        it
                                    }
                                },
                            ),
                            serverUserDetails = ServerUserDetailsUiState(),
                        )
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (mutableState.value.route == route) {
                    mutableState.update {
                        it.copy(
                            serverUserDetails = it.serverUserDetails.copy(
                                renaming = false,
                                renameErrorMessage = serverUserRenameErrorMessage(error),
                            ),
                        )
                    }
                }
            } finally {
                renameServerUserJob = null
            }
        }
    }

    private fun updateAddServerUser(transform: AddServerUserState.() -> AddServerUserState) {
        val add = mutableState.value.addServerUser
        if (mutableState.value.route !is AdminRoute.AddServerUser || add.submitting || add.token != null) {
            return
        }
        mutableState.update { it.copy(addServerUser = add.transform()) }
    }

    private fun serverUserAddErrorMessage(error: Exception): String = when (error) {
        is ServerUserAlreadyExistsException -> "Этот логин уже занят"
        is ServerUserAdministrativeAccessException ->
            "Нет прав для добавления пользователя на сервере"
        is ServerUserCommandUnavailableException -> "Команда TiniTalk не найдена на сервере"
        is ServerUserStorageException -> "Не удалось изменить базу пользователей"
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        else -> "Не удалось добавить пользователя"
    }

    private fun serverUserDeleteErrorMessage(error: Exception): String = when (error) {
        is ServerUserNotFoundException -> "Пользователь уже удалён с сервера"
        is ServerUserAdministrativeAccessException ->
            "Нет прав для удаления пользователя на сервере"
        is ServerUserCommandUnavailableException -> "Команда TiniTalk не найдена на сервере"
        is ServerUserStorageException -> "Не удалось изменить базу пользователей"
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        else -> "Не удалось удалить пользователя"
    }

    private fun serverUserTokenErrorMessage(error: Exception): String = when (error) {
        is ServerUserNotFoundException -> "Пользователь уже удалён с сервера"
        is ServerUserAdministrativeAccessException ->
            "Нет прав для смены токена на сервере"
        is ServerUserCommandUnavailableException -> "Команда TiniTalk не найдена на сервере"
        is ServerUserStorageException -> "Не удалось изменить базу пользователей"
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        else -> "Не удалось сменить токен"
    }

    private fun serverUserAccessErrorMessage(error: Exception, disabling: Boolean): String = when (error) {
        is ServerUserNotFoundException -> "Пользователь уже удалён с сервера"
        is ServerUserAdministrativeAccessException ->
            "Нет прав для изменения статуса пользователя"
        is ServerUserCommandUnavailableException -> "Команда TiniTalk не найдена на сервере"
        is ServerUserStorageException -> "Не удалось изменить базу пользователей"
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        else -> if (disabling) {
            "Не удалось заблокировать пользователя"
        } else {
            "Не удалось разблокировать пользователя"
        }
    }

    private fun serverUserRenameErrorMessage(error: Exception): String = when (error) {
        is ServerUserNotFoundException -> "Пользователь уже удалён с сервера"
        is ServerUserAdministrativeAccessException ->
            "Нет прав для переименования пользователя"
        is ServerUserCommandUnavailableException -> "Команда TiniTalk не найдена на сервере"
        is ServerUserStorageException -> "Не удалось изменить базу пользователей"
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        else -> "Не удалось переименовать пользователя"
    }

    private fun loadServerUsers(serverId: String) {
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        serverUsersJob = viewModelScope.launch {
            try {
                val users = withContext(Dispatchers.IO) {
                    val connection = connectToServer(server)
                    try {
                        serverUsersReader.read(connection, server.sshLogin)
                    } finally {
                        runCatching { connection.close() }
                    }
                }
                if (mutableState.value.route == AdminRoute.ServerUsers(serverId)) {
                    mutableState.update {
                        it.copy(serverUsers = ServerUsersUiState(users = users))
                    }
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                if (mutableState.value.route == AdminRoute.ServerUsers(serverId)) {
                    mutableState.update {
                        it.copy(
                            serverUsers = ServerUsersUiState(
                                errorMessage = "Не удалось загрузить пользователей",
                            ),
                        )
                    }
                }
            } finally {
                serverUsersJob = null
            }
        }
    }

    fun checkServerConnectivity(serverId: String) {
        if (serverConnectivityJob?.isActive == true) return
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        mutableState.update {
            it.copy(
                serverConnectivity = ServerConnectivityUiState(visible = true),
                notice = null,
            )
        }
        val job = viewModelScope.launch {
            coroutineScope {
                launch {
                    val status = try {
                        val result = withContext(Dispatchers.IO) { runSshCheck(server) }
                        SshConnectivityStatus.Available(result)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        SshConnectivityStatus.Unavailable(sshCheckErrorMessage(error))
                    }
                    if (serverConnectivityIsVisible(serverId)) {
                        mutableState.update { state ->
                            state.copy(
                                serverConnectivity = state.serverConnectivity.copy(ssh = status),
                            )
                        }
                    }
                }
                launch {
                    val status = try {
                        val result = tinitalkHealthChecker.check(server.enteredAddress)
                        TiniTalkApiConnectivityStatus.Available(result)
                    } catch (error: CancellationException) {
                        throw error
                    } catch (error: Exception) {
                        TiniTalkApiConnectivityStatus.Unavailable(
                            tinitalkApiCheckErrorMessage(error),
                        )
                    }
                    if (serverConnectivityIsVisible(serverId)) {
                        mutableState.update { state ->
                            state.copy(
                                serverConnectivity = state.serverConnectivity.copy(api = status),
                            )
                        }
                    }
                }
            }
        }
        serverConnectivityJob = job
        job.invokeOnCompletion {
            if (serverConnectivityJob === job) serverConnectivityJob = null
        }
    }

    fun dismissServerConnectivity() {
        serverConnectivityJob?.cancel()
        serverConnectivityJob = null
        mutableState.update {
            it.copy(serverConnectivity = ServerConnectivityUiState())
        }
    }

    private fun serverConnectivityIsVisible(serverId: String): Boolean =
        mutableState.value.route == AdminRoute.ServerDetails(serverId) &&
            mutableState.value.serverConnectivity.visible

    fun checkInitialSetup(serverId: String) {
        inspectInitialSetup(serverId, continuation = InitialSetupContinuation.NONE)
    }

    fun retryChangedHostKey(serverId: String) {
        if (serverOperationJob?.isActive == true) return
        val server = mutableState.value.servers.firstOrNull { it.id == serverId } ?: return
        if (serverSetupStore.get(serverId)?.hostKeyChanged != true) return
        mutableState.update { state ->
            state.copy(
                initialSetup = state.initialSetup.copy(hostKeyCheckInProgress = true),
                notice = null,
            )
        }
        serverOperationJob = viewModelScope.launch {
            var inspectServer = false
            try {
                val observedHostKey = sshAccessChecker.scanHostKey(server.resolvedEndpoint())
                if (server.hostKey.sameKeyAs(observedHostKey)) {
                    serverSetupStore.remove(server.id)
                    mutableState.update {
                        it.copy(initialSetup = InitialSetupUiState())
                    }
                    inspectServer = true
                } else {
                    markHostKeyChanged(server, observedHostKey)
                }
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                mutableState.update { state ->
                    state.copy(
                        initialSetup = state.initialSetup.copy(hostKeyCheckInProgress = false),
                        notice = "Не удалось повторно проверить SSH fingerprint",
                    )
                }
            } finally {
                serverOperationJob = null
                if (inspectServer) checkInitialSetup(serverId)
            }
        }
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
                if (error !is SshFailure.HostKeyChanged) {
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
        if (InitialSetupStep.UPLOAD_BINARY in completedSteps) {
            discardSelectedTiniTalkBinary()
            beginInitialSetup(serverId, completedSteps)
            return
        }
        discardSelectedTiniTalkBinary()
        mutableState.update { it.copy(binarySelection = TiniTalkBinaryState(visible = true)) }
    }

    fun closeTiniTalkBinary() {
        discardSelectedTiniTalkBinary()
        mutableState.update { it.copy(binarySelection = TiniTalkBinaryState()) }
    }

    fun tinitalkBinarySelected(uri: Uri?) {
        if (uri == null || !mutableState.value.binarySelection.visible) return
        if (!preserveReadAccess(uri)) return
        selectedTiniTalkBinaryUri?.takeIf { it != uri }?.let(::releaseReadAccess)
        selectedTiniTalkBinaryUri = uri
        mutableState.update {
            it.copy(binarySelection = it.binarySelection.copy(binaryName = displayName(uri)))
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
        beginInitialSetup(
            serverId = serverId,
            completedSteps = mutableState.value.initialSetup.completedSteps,
            binaryUri = binary,
        )
    }

    private fun beginInitialSetup(
        serverId: String,
        completedSteps: Set<InitialSetupStep>,
        binaryUri: Uri? = null,
    ) {
        val firstIncompleteStep = InitialSetupStep.entries.firstOrNull {
            it !in completedSteps
        } ?: return
        if (
            InitialSetupStep.UPLOAD_BINARY !in completedSteps &&
            binaryUri == null
        ) return
        val setup = StoredServerSetup(
            configured = false,
            startedAtEpochMillis = System.currentTimeMillis(),
            currentStep = firstIncompleteStep,
            completedSteps = completedSteps,
            binaryUri = binaryUri?.toString(),
        )
        serverSetupStore.put(serverId, setup)
        clearSelectedTiniTalkBinary()
        mutableState.update {
            it.copy(
                binarySelection = TiniTalkBinaryState(),
                initialSetup = setup.toUiState(),
            )
        }
        resumeInitialSetup(serverId)
    }

    fun retryInitialSetup(serverId: String) {
        if (mutableState.value.initialSetup.mode != InitialSetupUiMode.FAILED) return
        val setup = serverSetupStore.get(serverId)?.takeIf(StoredServerSetup::inProgress) ?: return
        serverSetupStore.put(serverId, setup.forRetry())
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
                if (error !is SshFailure.HostKeyChanged) {
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
                    if (step == InitialSetupStep.UPLOAD_BINARY) {
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
                releaseSetupBinary(setup)
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
        InitialSetupStep.FAIL2BAN -> listOf(server.sshPort.toString())
        InitialSetupStep.TLS_CERTIFICATE -> listOf(
            if (server.enteredAddress == server.frozenIpv4) "ip" else "domain",
            server.enteredAddress,
        )
        InitialSetupStep.PREPARE_TINITALK -> listOf(server.enteredAddress)
        InitialSetupStep.UPLOAD_BINARY -> emptyList()
        InitialSetupStep.START_TINITALK -> listOf(server.enteredAddress, server.frozenIpv4)
    }

    private suspend fun loadSetupUploads(setup: StoredServerSetup): List<RemoteOperationUpload> =
        withContext(Dispatchers.IO) {
            listOf(
                readSetupUpload(setup.binaryUri, "tinitalk"),
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

    private fun releaseSetupBinary(setup: StoredServerSetup) {
        setup.binaryUri?.let(Uri::parse)?.let(::releaseReadAccess)
    }

    private fun discardSelectedTiniTalkBinary() {
        selectedTiniTalkBinaryUri?.let(::releaseReadAccess)
        clearSelectedTiniTalkBinary()
    }

    private fun releaseReadAccess(uri: Uri) {
        runCatching {
            contentResolver.releasePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }
    }

    private fun clearSelectedTiniTalkBinary() {
        selectedTiniTalkBinaryUri = null
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
                    serverSetupStore.get(serverId)?.let(::releaseSetupBinary)
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
        check(serverSetupStore.get(server.id)?.hostKeyChanged != true) {
            "SSH access is blocked because the host key changed"
        }
        return try {
            sshAccessChecker.connect(
                endpoint = server.resolvedEndpoint(),
                login = server.sshLogin,
                credential = SshCredential.ManagedKey(server.keystoreAlias),
                pinnedHostKey = server.hostKey,
            )
        } catch (error: SshFailure.HostKeyChanged) {
            markHostKeyChanged(server, error.observedHostKey)
            throw error
        }
    }

    private fun ServerRecord.resolvedEndpoint() = ResolvedEndpoint(
        enteredAddress = enteredAddress,
        sshPort = sshPort,
        frozenIpv4 = frozenIpv4,
        addressKind = if (enteredAddress == frozenIpv4) AddressKind.IPV4 else AddressKind.DNS,
    )

    private fun markHostKeyChanged(server: ServerRecord, observedHostKey: PinnedHostKey) {
        serverSetupStore.get(server.id)?.let(::releaseSetupBinary)
        serverSetupStore.put(
            server.id,
            StoredServerSetup(
                configured = false,
                status = StoredServerSetupStatus.SSH_HOST_KEY_CHANGED,
                observedFingerprint = observedHostKey.sha256Fingerprint,
            ),
        )
        mutableState.update { state ->
            if (state.route.serverIdOrNull() != server.id) return@update state
            state.copy(
                route = AdminRoute.ServerDetails(server.id),
                serverOperation = null,
                serverConnectivity = ServerConnectivityUiState(),
                serverUsers = ServerUsersUiState(),
                addServerUser = AddServerUserState(),
                serverUserDetails = ServerUserDetailsUiState(),
                initialSetup = InitialSetupUiState(
                    mode = InitialSetupUiMode.SSH_HOST_KEY_CHANGED,
                    observedFingerprint = observedHostKey.sha256Fingerprint,
                ),
                notice = null,
            )
        }
    }

    private fun sshCheckErrorMessage(error: Exception): String = when (error) {
        is SshFailure.HostKeyChanged -> "SSH fingerprint сервера изменился"
        is SshFailure.AuthenticationFailed -> "Сохранённый SSH-ключ отклонён сервером"
        is SshFailure.Timeout -> "SSH: сервер не ответил вовремя"
        else -> "SSH-доступ недоступен"
    }

    private fun tinitalkApiCheckErrorMessage(error: Exception): String = when (error) {
        is UnexpectedTiniTalkServiceException -> "По этому адресу нет сервера TiniTalk"
        is UnhealthyTiniTalkServiceException -> "Сервер TiniTalk сообщил о недоступности"
        is TiniTalkHealthHttpException -> "HTTPS вернул код ${error.statusCode}"
        is SSLException -> "Не удалось проверить TLS-сертификат сервера"
        is SocketTimeoutException -> "Сервер не ответил по HTTPS вовремя"
        is UnknownHostException -> "Не удалось найти адрес сервера"
        else -> "Сервер TiniTalk недоступен по HTTPS"
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
        private val SERVER_USER_LOGIN_PATTERN = Regex("[A-Za-z0-9._-]+")
        private const val MAX_SERVER_USER_LOGIN_LENGTH = 64
        private const val MAX_SERVER_USER_DISPLAY_NAME_LENGTH = 100
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
                        serverUsersReader = ServerUsersReader(),
                        tinitalkHealthChecker = TiniTalkHealthChecker(),
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
    ServerOperationKind.SETUP_FAIL2BAN -> "Не удалось настроить Fail2ban"
    ServerOperationKind.OBTAIN_TLS_CERTIFICATE -> "Не удалось получить TLS-сертификат"
    ServerOperationKind.PREPARE_TINITALK -> "Не удалось подготовить TiniTalk"
    ServerOperationKind.INSTALL_TINITALK_BINARY -> "Не удалось загрузить бинарник TiniTalk"
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

private fun AdminRoute.serverIdOrNull(): String? = when (this) {
    AdminRoute.ServerList,
    AdminRoute.AddServer,
    -> null
    is AdminRoute.ServerDetails -> serverId
    is AdminRoute.ServerUsers -> serverId
    is AdminRoute.AddServerUser -> serverId
    is AdminRoute.ServerUserDetails -> serverId
}
