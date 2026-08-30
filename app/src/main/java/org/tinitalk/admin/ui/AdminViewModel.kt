package org.tinitalk.admin.ui

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import org.tinitalk.admin.data.ServerStore
import org.tinitalk.admin.data.SharedPreferencesServerStore
import org.tinitalk.admin.model.PinnedHostKey
import org.tinitalk.admin.model.ServerRecord
import org.tinitalk.admin.server.DnsValidationException
import org.tinitalk.admin.server.EndpointParser
import org.tinitalk.admin.server.EndpointResolver
import org.tinitalk.admin.server.EndpointValidationException
import org.tinitalk.admin.server.ResolvedEndpoint
import org.tinitalk.admin.ssh.ImportedKeyReader
import org.tinitalk.admin.ssh.ImportedSshIdentityException
import org.tinitalk.admin.ssh.SshAccessChecker
import org.tinitalk.admin.ssh.SshCredential
import org.tinitalk.admin.ssh.SshFailure
import org.tinitalk.admin.ssh.SshjAccessChecker
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface AdminRoute {
    data object ServerList : AdminRoute
    data object AddServer : AdminRoute
}

sealed interface AddServerPhase {
    data object EndpointForm : AddServerPhase
    data object ScanningFingerprint : AddServerPhase
    data class ConfirmFingerprint(val key: PinnedHostKey) : AddServerPhase
    data object Credentials : AddServerPhase
    data object CheckingAccess : AddServerPhase
    data class Failed(val message: String) : AddServerPhase
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
    val phase: AddServerPhase = AddServerPhase.EndpointForm,
    val errorMessage: String? = null,
)

data class AdminUiState(
    val route: AdminRoute = AdminRoute.ServerList,
    val servers: List<ServerRecord> = emptyList(),
    val addServer: AddServerState = AddServerState(),
    val notice: String? = null,
)

class AdminViewModel(
    private val serverStore: ServerStore,
    private val endpointResolver: EndpointResolver,
    private val sshAccessChecker: SshAccessChecker,
    private val importedKeyReader: ImportedKeyReader,
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
    private var pendingEndpoint: ResolvedEndpoint? = null
    private var pendingHostKey: PinnedHostKey? = null

    fun openAddServer() {
        invalidateOperation()
        mutableState.update {
            it.copy(
                route = AdminRoute.AddServer,
                addServer = AddServerState(),
                notice = null,
            )
        }
    }

    fun closeAddServer() {
        if (mutableState.value.addServer.phase == AddServerPhase.CheckingAccess) return
        invalidateOperation()
        mutableState.update {
            it.copy(
                route = AdminRoute.ServerList,
                addServer = AddServerState(),
            )
        }
    }

    fun clearNotice() {
        mutableState.update { it.copy(notice = null) }
    }

    fun updateDisplayName(value: String) = updateEndpointForm { copy(displayName = value) }
    fun updateAddress(value: String) = updateEndpointForm { copy(address = value) }
    fun updateSshPort(value: String) = updateEndpointForm { copy(sshPort = value) }
    fun updateSshLogin(value: String) = updateEndpointForm { copy(sshLogin = value) }

    fun updateAuthentication(value: AuthenticationMethod) = updateCredentials {
        if (authentication == value) return@updateCredentials this
        copy(
            authentication = value,
            password = "",
            privateKeyPassphrase = "",
            errorMessage = null,
        )
    }

    fun updatePassword(value: String) = updateCredentials { copy(password = value, errorMessage = null) }

    fun updatePrivateKeyPassphrase(value: String) = updateCredentials {
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

        val id = nextOperation()
        mutableState.update {
            it.copy(
                addServer = form.copy(
                    displayName = validated.displayName,
                    address = validated.address,
                    sshPort = validated.port.toString(),
                    sshLogin = validated.login,
                    frozenIpv4 = null,
                    password = "",
                    privateKeyPassphrase = "",
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
                if (isCurrent(id)) failAdd(error)
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
                    password = "",
                    privateKeyPassphrase = "",
                    phase = AddServerPhase.EndpointForm,
                    errorMessage = null,
                ),
            )
        }
    }

    fun confirmFingerprint() {
        if (pendingEndpoint == null || pendingHostKey == null) return
        val add = mutableState.value.addServer
        if (add.phase !is AddServerPhase.ConfirmFingerprint) return
        mutableState.update {
            it.copy(
                addServer = add.copy(
                    phase = AddServerPhase.Credentials,
                    errorMessage = null,
                ),
            )
        }
    }

    fun verifyWithPassword() {
        val add = mutableState.value.addServer
        if (add.phase != AddServerPhase.Credentials) return
        if (add.password.isEmpty()) {
            mutableState.update {
                it.copy(addServer = add.copy(errorMessage = "Укажите SSH-пароль"))
            }
            return
        }
        val password = add.password.toCharArray()
        verifyAccess(SshCredential.Password(password))
    }

    fun privateKeySelected(uri: Uri?) {
        val add = mutableState.value.addServer
        if (
            add.phase != AddServerPhase.Credentials ||
            add.authentication != AuthenticationMethod.PRIVATE_KEY
        ) return
        if (uri == null) {
            mutableState.update {
                it.copy(addServer = add.copy(privateKeyPassphrase = ""))
            }
            return
        }

        val endpoint = pendingEndpoint ?: return
        val hostKey = pendingHostKey ?: return
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
            try {
                val identity = importedKeyReader.read(uri, passphrase)
                sshAccessChecker.verifyAccess(
                    endpoint = endpoint,
                    login = metadata.sshLogin,
                    credential = SshCredential.ImportedKey(identity),
                    pinnedHostKey = hostKey,
                )
                if (isCurrent(id)) persistSuccessfulCheck(endpoint, hostKey, metadata)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(id)) failAdd(error)
            } finally {
                passphrase.fill('\u0000')
                if (isCurrent(id)) currentJob = null
            }
        }
    }

    fun retryAddServer() {
        invalidateOperation()
        mutableState.update {
            it.copy(
                addServer = it.addServer.copy(
                    frozenIpv4 = null,
                    password = "",
                    privateKeyPassphrase = "",
                    phase = AddServerPhase.EndpointForm,
                    errorMessage = null,
                ),
            )
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
                sshAccessChecker.verifyAccess(
                    endpoint = endpoint,
                    login = metadata.sshLogin,
                    credential = credential,
                    pinnedHostKey = hostKey,
                )
                if (isCurrent(id)) persistSuccessfulCheck(endpoint, hostKey, metadata)
            } catch (error: CancellationException) {
                throw error
            } catch (error: Exception) {
                if (isCurrent(id)) failAdd(error)
            } finally {
                credential.close()
                if (isCurrent(id)) currentJob = null
            }
        }
    }

    private suspend fun persistSuccessfulCheck(
        endpoint: ResolvedEndpoint,
        hostKey: PinnedHostKey,
        metadata: PendingServerMetadata,
    ) {
        val record = ServerRecord(
            id = UUID.randomUUID().toString(),
            displayName = metadata.displayName,
            enteredAddress = endpoint.enteredAddress,
            frozenIpv4 = endpoint.frozenIpv4,
            sshPort = endpoint.sshPort,
            sshLogin = metadata.sshLogin,
            hostKey = hostKey,
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
            notice = "Сервер добавлен. SSH-доступ проверен",
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
            displayName = form.displayName.trim().ifEmpty { parsed.enteredAddress },
        )
    }

    private fun failAdd(error: Exception) {
        pendingEndpoint = null
        pendingHostKey = null
        mutableState.update {
            it.copy(
                addServer = it.addServer.copy(
                    frozenIpv4 = null,
                    password = "",
                    privateKeyPassphrase = "",
                    phase = AddServerPhase.Failed(safeErrorMessage(error)),
                    errorMessage = null,
                ),
            )
        }
    }

    private fun safeErrorMessage(error: Exception): String = when (error) {
        is DnsValidationException -> "Не удалось получить один публичный IPv4 для домена"
        is SshFailure.HostKeyChanged -> "SSH host key изменился. Начните проверку заново"
        is SshFailure.AuthenticationFailed -> "SSH-аутентификация не прошла"
        is SshFailure.Timeout -> "Сервер не ответил вовремя"
        is ImportedSshIdentityException -> "Не удалось прочитать SSH private key"
        is KnownHostKeyChangedException ->
            "SSH host key отличается от сохранённого. Автоматическая замена запрещена"
        is LocalPersistenceException -> "SSH-доступ проверен, но сервер не удалось сохранить"
        else -> "Не удалось проверить SSH-доступ"
    }

    private fun updateEndpointForm(transform: AddServerState.() -> AddServerState) {
        val add = mutableState.value.addServer
        if (add.phase != AddServerPhase.EndpointForm) return
        mutableState.update { it.copy(addServer = add.transform().copy(errorMessage = null)) }
    }

    private fun updateCredentials(transform: AddServerState.() -> AddServerState) {
        val add = mutableState.value.addServer
        if (add.phase != AddServerPhase.Credentials) return
        mutableState.update { it.copy(addServer = add.transform()) }
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
        fun factory(context: Context): ViewModelProvider.Factory {
            val applicationContext = context.applicationContext
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    require(modelClass.isAssignableFrom(AdminViewModel::class.java))
                    return AdminViewModel(
                        serverStore = SharedPreferencesServerStore(applicationContext),
                        endpointResolver = EndpointResolver(),
                        sshAccessChecker = SshjAccessChecker(),
                        importedKeyReader = ImportedKeyReader(applicationContext.contentResolver),
                    ) as T
                }
            }
        }
    }
}

private class KnownHostKeyChangedException : IllegalStateException()

private class LocalPersistenceException(cause: Throwable) :
    IllegalStateException("Failed to save verified server", cause)

private fun PinnedHostKey.sameKeyAs(other: PinnedHostKey): Boolean =
    algorithm == other.algorithm && sshWireKeyBase64 == other.sshWireKeyBase64
