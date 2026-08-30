package org.tinitalk.admin.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalAutofillManager
import org.tinitalk.admin.server.ServerOperationKind

@Composable
fun AdminApp(viewModel: AdminViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    val autofillManager = LocalAutofillManager.current
    val closeAddServer = {
        autofillManager?.cancel()
        viewModel.closeAddServer()
    }
    val confirmFingerprint = {
        autofillManager?.cancel()
        viewModel.confirmFingerprint()
    }
    val privateKeyPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = viewModel::privateKeySelected,
    )
    val tinitalkBinaryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = viewModel::tinitalkBinarySelected,
    )
    val firebaseAndroidConfigPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = viewModel::firebaseAndroidConfigSelected,
    )
    val firebaseServiceAccountPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = viewModel::firebaseServiceAccountSelected,
    )

    LaunchedEffect(state.notice) {
        state.notice?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.clearNotice()
        }
    }

    BackHandler(enabled = state.route != AdminRoute.ServerList) {
        when (state.route) {
            AdminRoute.AddServer -> closeAddServer()
            is AdminRoute.ServerDetails -> viewModel.closeServer()
            AdminRoute.ServerList -> Unit
        }
    }

    when (val route = state.route) {
        AdminRoute.ServerList -> ServerListScreen(
            servers = state.servers,
            snackbarHostState = snackbarHostState,
            onAddServer = viewModel::openAddServer,
            onOpenServer = viewModel::openServer,
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
        )

        AdminRoute.AddServer -> AddServerScreen(
            state = state.addServer,
            onBack = closeAddServer,
            onDisplayNameChange = viewModel::updateDisplayName,
            onAddressChange = viewModel::updateAddress,
            onPortChange = viewModel::updateSshPort,
            onLoginChange = viewModel::updateSshLogin,
            onScanFingerprint = viewModel::scanFingerprint,
            onRejectFingerprint = viewModel::rejectFingerprint,
            onConfirmFingerprint = confirmFingerprint,
            onAuthenticationChange = viewModel::updateAuthentication,
            onPasswordChange = viewModel::updatePassword,
            onPassphraseChange = viewModel::updatePrivateKeyPassphrase,
            onChoosePrivateKey = {
                privateKeyPicker.launch(arrayOf("application/octet-stream", "text/plain", "*/*"))
            },
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
        )

        is AdminRoute.ServerDetails -> state.servers
            .firstOrNull { it.id == route.serverId }
            ?.let { server ->
                val runningOperation = state.serverOperation
                ServerDetailsScreen(
                    server = server,
                    snackbarHostState = snackbarHostState,
                    sshCheckInProgress = state.sshCheckInProgress,
                    sshCheckResult = state.sshCheckResult,
                    tinitalkStatusInProgress = state.tinitalkStatusInProgress,
                    tinitalkStatusResult = state.tinitalkStatusResult,
                    systemPackagesInstallInProgress =
                        runningOperation?.serverId == server.id &&
                            runningOperation.kind == ServerOperationKind.INSTALL_SYSTEM_PACKAGES,
                    firewallConfigureInProgress =
                        runningOperation?.serverId == server.id &&
                            runningOperation.kind == ServerOperationKind.CONFIGURE_FIREWALL,
                    tlsCertificateInProgress =
                        runningOperation?.serverId == server.id &&
                            runningOperation.kind == ServerOperationKind.OBTAIN_TLS_CERTIFICATE,
                    tinitalkPrepareInProgress =
                        runningOperation?.serverId == server.id &&
                            runningOperation.kind == ServerOperationKind.PREPARE_TINITALK,
                    tinitalkFilesInstallInProgress =
                        runningOperation?.serverId == server.id &&
                            runningOperation.kind == ServerOperationKind.INSTALL_TINITALK_FILES,
                    tinitalkStartInProgress =
                        runningOperation?.serverId == server.id &&
                            runningOperation.kind == ServerOperationKind.START_TINITALK,
                    tinitalkFiles = state.tinitalkFiles,
                    serverOperationStartedAt = runningOperation?.startedAt
                        ?.takeIf { runningOperation.serverId == server.id },
                    serverOperationInProgress = runningOperation != null,
                    onBack = viewModel::closeServer,
                    onRename = { viewModel.renameServer(server.id, it) },
                    onRemove = { viewModel.removeServer(server.id) },
                    onCheckSsh = { viewModel.checkServerSsh(server.id) },
                    onDismissSshCheckResult = viewModel::dismissSshCheckResult,
                    onCheckTiniTalkStatus = { viewModel.checkTiniTalkStatus(server.id) },
                    onDismissTiniTalkStatus = viewModel::dismissTiniTalkStatus,
                    onInstallSystemPackages = { viewModel.installSystemPackages(server.id) },
                    onConfigureFirewall = { viewModel.configureFirewall(server.id) },
                    onObtainTlsCertificate = { viewModel.obtainTlsCertificate(server.id) },
                    onPrepareTiniTalk = { viewModel.prepareTiniTalk(server.id) },
                    onOpenTiniTalkFiles = viewModel::openTiniTalkFiles,
                    onCloseTiniTalkFiles = viewModel::closeTiniTalkFiles,
                    onChooseTiniTalkBinary = {
                        tinitalkBinaryPicker.launch(arrayOf("application/octet-stream", "*/*"))
                    },
                    onChooseFirebaseAndroidConfig = {
                        firebaseAndroidConfigPicker.launch(
                            arrayOf("application/json", "text/plain", "*/*"),
                        )
                    },
                    onChooseFirebaseServiceAccount = {
                        firebaseServiceAccountPicker.launch(
                            arrayOf("application/json", "text/plain", "*/*"),
                        )
                    },
                    onInstallTiniTalkFiles = { viewModel.installTiniTalkFiles(server.id) },
                    onStartTiniTalk = { viewModel.startTiniTalk(server.id) },
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                )
            }
    }
}
