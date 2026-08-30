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
                ServerDetailsScreen(
                    server = server,
                    snackbarHostState = snackbarHostState,
                    sshCheckInProgress = state.sshCheckInProgress,
                    sshCheckResult = state.sshCheckResult,
                    onBack = viewModel::closeServer,
                    onRename = { viewModel.renameServer(server.id, it) },
                    onRemove = { viewModel.removeServer(server.id) },
                    onCheckSsh = { viewModel.checkServerSsh(server.id) },
                    onDismissSshCheckResult = viewModel::dismissSshCheckResult,
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                )
            }
    }
}
