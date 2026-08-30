package org.tinitalk.admin.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
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
import androidx.compose.ui.unit.IntOffset

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

    Box(modifier = Modifier.fillMaxSize()) {
        ServerListScreen(
            servers = state.servers,
            snackbarHostState = snackbarHostState,
            onAddServer = viewModel::openAddServer,
            onOpenServer = viewModel::openServer,
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
        )

        AnimatedContent(
            targetState = state.route as? AdminRoute.ServerDetails,
            transitionSpec = {
                val animationSpec = tween<IntOffset>(
                    durationMillis = 400,
                    easing = FastOutSlowInEasing,
                )
                if (targetState != null) {
                    slideInHorizontally(animationSpec) { fullWidth -> fullWidth }
                        .togetherWith(ExitTransition.None)
                } else {
                    EnterTransition.None.togetherWith(
                        slideOutHorizontally(animationSpec) { fullWidth -> fullWidth },
                    )
                }
            },
            label = "serverDetailsRoute",
            modifier = Modifier.fillMaxSize(),
        ) { route ->
            if (route == null) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                state.servers
                    .firstOrNull { it.id == route.serverId }
                    ?.let { server ->
                        val runningOperation = state.serverOperation
                        ServerDetailsScreen(
                                server = server,
                                snackbarHostState = snackbarHostState,
                                sshCheckInProgress = state.sshCheckInProgress,
                                sshCheckResult = state.sshCheckResult,
                                initialSetup = state.initialSetup,
                                tinitalkFiles = state.tinitalkFiles,
                                serverOperationStartedAt = runningOperation?.startedAt
                                    ?.takeIf { runningOperation.serverId == server.id },
                                serverOperationInProgress = runningOperation != null,
                                onBack = viewModel::closeServer,
                                onRename = { viewModel.renameServer(server.id, it) },
                                onRemove = { viewModel.removeServer(server.id) },
                                onCheckSsh = { viewModel.checkServerSsh(server.id) },
                                onDismissSshCheckResult = viewModel::dismissSshCheckResult,
                                onCheckInitialSetup = {
                                    viewModel.checkInitialSetup(server.id)
                                },
                                onCheckAndContinueInitialSetup = {
                                    viewModel.checkAndContinueInitialSetup(server.id)
                                },
                                onContinueInitialSetup = {
                                    viewModel.continueInitialSetup(server.id)
                                },
                                onRetryInitialSetup = {
                                    viewModel.retryInitialSetup(server.id)
                                },
                                onCloseTiniTalkFiles = viewModel::closeTiniTalkFiles,
                                onChooseTiniTalkBinary = {
                                    tinitalkBinaryPicker.launch(
                                        arrayOf("application/octet-stream", "*/*"),
                                    )
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
                                onStartInitialSetup = {
                                    viewModel.startInitialSetup(server.id)
                                },
                                modifier = Modifier.fillMaxSize().systemBarsPadding(),
                        )
                    }
            }
        }

        if (state.route == AdminRoute.AddServer) {
            AddServerScreen(
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
                    privateKeyPicker.launch(
                        arrayOf("application/octet-stream", "text/plain", "*/*"),
                    )
                },
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
            )
        }
    }
}
