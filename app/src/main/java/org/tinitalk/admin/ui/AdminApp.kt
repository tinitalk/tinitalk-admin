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
            is AdminRoute.ServerUsers -> viewModel.closeServerUsers()
            is AdminRoute.AddServerUser -> viewModel.closeAddServerUser()
            is AdminRoute.ServerUserDetails -> viewModel.closeServerUser()
            AdminRoute.ServerList -> Unit
        }
    }

    val serverDetailsRoute = when (val route = state.route) {
        is AdminRoute.ServerDetails -> route
        is AdminRoute.ServerUsers -> AdminRoute.ServerDetails(route.serverId)
        is AdminRoute.AddServerUser -> AdminRoute.ServerDetails(route.serverId)
        is AdminRoute.ServerUserDetails -> AdminRoute.ServerDetails(route.serverId)
        else -> null
    }
    val serverUsersRoute = when (val route = state.route) {
        is AdminRoute.ServerUsers -> route
        is AdminRoute.AddServerUser -> AdminRoute.ServerUsers(route.serverId)
        is AdminRoute.ServerUserDetails -> AdminRoute.ServerUsers(route.serverId)
        else -> null
    }
    val serverUserDetailsRoute = state.route as? AdminRoute.ServerUserDetails

    Box(modifier = Modifier.fillMaxSize()) {
        ServerListScreen(
            servers = state.servers,
            snackbarHostState = snackbarHostState,
            onAddServer = viewModel::openAddServer,
            onOpenServer = viewModel::openServer,
            modifier = Modifier.fillMaxSize().systemBarsPadding(),
        )

        AnimatedContent(
            targetState = serverDetailsRoute,
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
                                serverConnectivity = state.serverConnectivity,
                                initialSetup = state.initialSetup,
                                binarySelection = state.binarySelection,
                                serverOperationStartedAt = runningOperation?.startedAt
                                    ?.takeIf { runningOperation.serverId == server.id },
                                serverOperationInProgress = runningOperation != null,
                                onBack = viewModel::closeServer,
                                onRename = { viewModel.renameServer(server.id, it) },
                                onRemove = { viewModel.removeServer(server.id) },
                                onCheckConnectivity = {
                                    viewModel.checkServerConnectivity(server.id)
                                },
                                onDismissConnectivity = viewModel::dismissServerConnectivity,
                                onCheckInitialSetup = {
                                    viewModel.checkInitialSetup(server.id)
                                },
                                onOpenUsers = {
                                    viewModel.openServerUsers(server.id)
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
                                onCloseTiniTalkBinary = viewModel::closeTiniTalkBinary,
                                onChooseTiniTalkBinary = {
                                    tinitalkBinaryPicker.launch(
                                        arrayOf("application/octet-stream", "*/*"),
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

        AnimatedContent(
            targetState = serverUsersRoute,
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
            label = "serverUsersRoute",
            modifier = Modifier.fillMaxSize(),
        ) { route ->
            if (route == null) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                ServerUsersScreen(
                    state = state.serverUsers,
                    onBack = viewModel::closeServerUsers,
                    onRetry = viewModel::retryServerUsers,
                    onAddUser = viewModel::openAddServerUser,
                    onOpenUser = viewModel::openServerUser,
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                )
            }
        }

        AnimatedContent(
            targetState = serverUserDetailsRoute,
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
            label = "serverUserDetailsRoute",
            modifier = Modifier.fillMaxSize(),
        ) { route ->
            if (route == null) {
                Box(modifier = Modifier.fillMaxSize())
            } else {
                val currentUser = state.serverUsers.users
                    .firstOrNull { it.login == route.user.login }
                    ?: route.user
                ServerUserDetailsScreen(
                    user = currentUser,
                    state = state.serverUserDetails,
                    onBack = viewModel::closeServerUser,
                    onRotateToken = viewModel::rotateServerUserToken,
                    onTokenCopied = viewModel::serverUserRotatedTokenCopied,
                    onChangeAccess = viewModel::changeServerUserAccess,
                    onOpenRename = viewModel::openServerUserRename,
                    onCloseRename = viewModel::closeServerUserRename,
                    onRenameDraftChange = viewModel::updateServerUserRenameDraft,
                    onRename = viewModel::renameServerUser,
                    onDelete = viewModel::deleteServerUser,
                    modifier = Modifier.fillMaxSize().systemBarsPadding(),
                )
            }
        }

        if (state.route is AdminRoute.AddServerUser) {
            AddServerUserScreen(
                state = state.addServerUser,
                onBack = viewModel::closeAddServerUser,
                onLoginChange = viewModel::updateServerUserLogin,
                onDisplayNameChange = viewModel::updateServerUserDisplayName,
                onSubmit = viewModel::submitServerUser,
                onTokenCopied = viewModel::serverUserTokenCopied,
                modifier = Modifier.fillMaxSize().systemBarsPadding(),
            )
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
