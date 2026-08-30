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

@Composable
fun AdminApp(viewModel: AdminViewModel) {
    val state by viewModel.state.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
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

    BackHandler(enabled = state.route == AdminRoute.AddServer) {
        viewModel.closeAddServer()
    }

    when (state.route) {
        AdminRoute.ServerList -> ServerListScreen(
            servers = state.servers,
            snackbarHostState = snackbarHostState,
            onAddServer = viewModel::openAddServer,
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
        )

        AdminRoute.AddServer -> AddServerScreen(
            state = state.addServer,
            onBack = viewModel::closeAddServer,
            onDisplayNameChange = viewModel::updateDisplayName,
            onAddressChange = viewModel::updateAddress,
            onPortChange = viewModel::updateSshPort,
            onLoginChange = viewModel::updateSshLogin,
            onScanFingerprint = viewModel::scanFingerprint,
            onRejectFingerprint = viewModel::rejectFingerprint,
            onConfirmFingerprint = viewModel::confirmFingerprint,
            onAuthenticationChange = viewModel::updateAuthentication,
            onPasswordChange = viewModel::updatePassword,
            onPassphraseChange = viewModel::updatePrivateKeyPassphrase,
            onVerifyPassword = viewModel::verifyWithPassword,
            onChoosePrivateKey = {
                privateKeyPicker.launch(arrayOf("application/octet-stream", "text/plain", "*/*"))
            },
            onRetry = viewModel::retryAddServer,
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
        )
    }
}
