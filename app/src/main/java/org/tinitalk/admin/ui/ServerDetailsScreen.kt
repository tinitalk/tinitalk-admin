package org.tinitalk.admin.ui

import org.tinitalk.admin.i18n.appString

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.SystemClock
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.tinitalk.admin.R
import org.tinitalk.admin.model.ServerRecord
import org.tinitalk.admin.server.InitialSetupStep
import org.tinitalk.admin.ui.theme.AccessVerifiedGreen
import kotlinx.coroutines.delay

@Composable
fun ServerDetailsScreen(
    server: ServerRecord,
    snackbarHostState: SnackbarHostState,
    serverConnectivity: ServerConnectivityUiState,
    initialSetup: InitialSetupUiState,
    binarySelection: TiniTalkBinaryState,
    tinitalkUpdate: TiniTalkUpdateUiState,
    serverOperationStartedAt: Long?,
    serverOperationInProgress: Boolean,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onCheckConnectivity: () -> Unit,
    onDismissConnectivity: () -> Unit,
    onCheckInitialSetup: () -> Unit,
    onRetryChangedHostKey: () -> Unit,
    onOpenUsers: () -> Unit,
    onCheckAndContinueInitialSetup: () -> Unit,
    onContinueInitialSetup: () -> Unit,
    onRetryInitialSetup: () -> Unit,
    onCloseTiniTalkBinary: () -> Unit,
    onChooseTiniTalkBinary: () -> Unit,
    onStartInitialSetup: () -> Unit,
    onOpenTiniTalkUpdate: () -> Unit,
    onCloseTiniTalkUpdate: () -> Unit,
    onChooseTiniTalkUpdateBinary: () -> Unit,
    onStartTiniTalkUpdate: () -> Unit,
    onRetryTiniTalkUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameDialogVisible by rememberSaveable(server.id) { mutableStateOf(false) }
    var deleteDialogVisible by rememberSaveable(server.id) { mutableStateOf(false) }
    var menuExpanded by rememberSaveable(server.id) { mutableStateOf(false) }
    var nameDraft by rememberSaveable(server.id) { mutableStateOf(server.displayName) }
    val renameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    var serverOperationElapsedSeconds by remember(serverOperationStartedAt) {
        mutableLongStateOf(serverOperationStartedAt.elapsedSeconds())
    }
    var setupElapsedSeconds by remember(initialSetup.startedAtEpochMillis) {
        mutableLongStateOf(initialSetup.startedAtEpochMillis.setupElapsedSeconds())
    }
    val setupBusy = initialSetup.mode == InitialSetupUiMode.CHECKING ||
        initialSetup.mode == InitialSetupUiMode.RUNNING
    val hostKeyChanged = initialSetup.mode == InitialSetupUiMode.SSH_HOST_KEY_CHANGED
    val actionsEnabled = !serverConnectivity.inProgress &&
        !serverOperationInProgress &&
        !setupBusy &&
        !hostKeyChanged &&
        tinitalkUpdate.mode !in setOf(
            TiniTalkUpdateUiMode.RUNNING,
            TiniTalkUpdateUiMode.FAILED,
        )

    LaunchedEffect(renameDialogVisible) {
        if (renameDialogVisible) {
            renameFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    LaunchedEffect(serverOperationStartedAt) {
        val startedAt = serverOperationStartedAt ?: return@LaunchedEffect
        while (true) {
            serverOperationElapsedSeconds =
                ((SystemClock.elapsedRealtime() - startedAt) / 1_000).coerceAtLeast(0)
            delay(1_000)
        }
    }

    LaunchedEffect(initialSetup.startedAtEpochMillis) {
        val startedAt = initialSetup.startedAtEpochMillis ?: return@LaunchedEffect
        while (true) {
            setupElapsedSeconds =
                ((System.currentTimeMillis() - startedAt) / 1_000).coerceAtLeast(0)
            delay(1_000)
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            ScreenHeader(
                title = appString(R.string.text_server_113),
                onBack = onBack,
                actions = {
                    Box {
                        IconButton(onClick = { menuExpanded = true }) {
                            MoreVertIcon()
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                        ) {
                            DropdownMenuItem(
                                enabled = actionsEnabled,
                                text = {
                                    Text(
                                        text = appString(R.string.text_remove_from_app_114),
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                onClick = {
                                    menuExpanded = false
                                    deleteDialogVisible = true
                                },
                            )
                        }
                    }
                },
            )
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = server.displayName.ifEmpty { appString(R.string.text_unnamed_115) },
                    color = if (server.displayName.isEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurface
                    },
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                EditIconButton(
                    onClick = {
                        nameDraft = server.displayName
                        renameDialogVisible = true
                    },
                )
            }
            Surface(
                color = MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.padding(18.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ServerProperty(
                            label = appString(R.string.text_address_116),
                            value = server.enteredAddress,
                            onValueClick = {
                                copyPlainText(context, appString(R.string.text_address_116), server.enteredAddress)
                            },
                            modifier = Modifier.weight(1f),
                        )
                        if (!hostKeyChanged) {
                            ServerCheckIconButton(
                                inProgress = serverConnectivity.inProgress,
                                enabled = actionsEnabled,
                                onClick = onCheckConnectivity,
                            )
                        }
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ServerProperty(
                            label = appString(R.string.text_ssh_port_11),
                            value = server.sshPort.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        ServerProperty(
                            label = appString(R.string.text_user_117),
                            value = server.sshLogin,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            InitialSetupCard(
                state = initialSetup,
                setupElapsedSeconds = setupElapsedSeconds,
                stepElapsedSeconds = serverOperationElapsedSeconds,
                onCheck = onCheckInitialSetup,
                onRetryChangedHostKey = onRetryChangedHostKey,
                onCheckAndStart = onCheckAndContinueInitialSetup,
                onStart = onContinueInitialSetup,
                onRetry = onRetryInitialSetup,
                actionsEnabled = actionsEnabled,
                expectedFingerprint = server.hostKey.sha256Fingerprint,
            )
            if (initialSetup.mode == InitialSetupUiMode.CONFIGURED) {
                SetupActionButton(
                    label = appString(R.string.text_users_118),
                    enabled = actionsEnabled,
                    iconResource = R.drawable.ic_contacts,
                    onClick = onOpenUsers,
                )
                when (tinitalkUpdate.mode) {
                    TiniTalkUpdateUiMode.RUNNING -> TiniTalkUpdateProgress(
                        elapsedSeconds = serverOperationElapsedSeconds,
                    )
                    TiniTalkUpdateUiMode.FAILED -> TiniTalkUpdateFailure(
                        message = tinitalkUpdate.errorMessage?.resolve()
                            ?: appString(R.string.text_could_not_get_update_status_119),
                        enabled = !serverConnectivity.inProgress &&
                            !serverOperationInProgress &&
                            !setupBusy &&
                            !hostKeyChanged,
                        onRetry = onRetryTiniTalkUpdate,
                    )
                    TiniTalkUpdateUiMode.IDLE,
                    TiniTalkUpdateUiMode.SELECTING,
                    -> SetupActionButton(
                        label = appString(R.string.text_update_tinitalk_120),
                        enabled = actionsEnabled,
                        iconResource = R.drawable.ic_update,
                        onClick = onOpenTiniTalkUpdate,
                    )
                }
            }
        }
    }

    if (binarySelection.visible) {
        AlertDialog(
            onDismissRequest = onCloseTiniTalkBinary,
            title = { Text(appString(R.string.text_initial_setup_121)) },
            text = {
                FileSelectionButton(
                    label = appString(R.string.text_tinitalk_server_binary_122),
                    fileName = binarySelection.binaryName,
                    onClick = onChooseTiniTalkBinary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onStartInitialSetup,
                    enabled = binarySelection.ready,
                ) {
                    Text(appString(R.string.text_start_123))
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseTiniTalkBinary) {
                    Text(appString(R.string.text_cancel_8))
                }
            },
        )
    }

    if (tinitalkUpdate.mode == TiniTalkUpdateUiMode.SELECTING) {
        AlertDialog(
            onDismissRequest = onCloseTiniTalkUpdate,
            title = { Text(appString(R.string.text_update_tinitalk_120)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        appString(R.string.text_the_service_will_stop_briefly_during_the_update_the_curr_124),
                    )
                    FileSelectionButton(
                        label = appString(R.string.text_new_tinitalk_server_binary_125),
                        fileName = tinitalkUpdate.binaryName,
                        onClick = onChooseTiniTalkUpdateBinary,
                    )
                }
            },
            confirmButton = {
                TextButton(
                    onClick = onStartTiniTalkUpdate,
                    enabled = tinitalkUpdate.ready,
                ) {
                    Text(appString(R.string.text_update_126))
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseTiniTalkUpdate) {
                    Text(appString(R.string.text_cancel_8))
                }
            },
        )
    }

    if (renameDialogVisible) {
        AlertDialog(
            onDismissRequest = { renameDialogVisible = false },
            title = { Text(appString(R.string.text_server_name_127)) },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text(appString(R.string.text_name_128)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth().focusRequester(renameFocusRequester),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onRename(nameDraft)
                        renameDialogVisible = false
                    },
                ) {
                    Text(appString(R.string.text_save_129))
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) {
                    Text(appString(R.string.text_cancel_8))
                }
            },
        )
    }

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text(appString(R.string.text_remove_server_130)) },
            text = {
                Text(appString(R.string.text_the_server_will_only_be_removed_from_the_app_nothing_on_131))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogVisible = false
                        onRemove()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text(appString(R.string.text_delete_132))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) {
                    Text(appString(R.string.text_cancel_8))
                }
            },
        )
    }

    if (serverConnectivity.visible) {
        AlertDialog(
            onDismissRequest = onDismissConnectivity,
            title = { Text(appString(R.string.text_server_availability_133)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SshConnectivitySection(serverConnectivity.ssh)
                    TiniTalkApiConnectivitySection(serverConnectivity.api)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissConnectivity) {
                    Text(appString(R.string.text_close_134))
                }
            },
        )
    }

}

@Composable
private fun SshConnectivitySection(status: SshConnectivityStatus) {
    ConnectivitySection {
        when (status) {
            SshConnectivityStatus.Checking -> ConnectivityStatusRow(
                message = appString(R.string.text_checking_ssh_access_135),
                checking = true,
            )
            is SshConnectivityStatus.Unavailable -> ConnectivityStatusRow(
                message = status.message.resolve(),
                available = false,
            )
            is SshConnectivityStatus.Available -> {
                ConnectivityStatusRow(message = appString(R.string.text_ssh_access_is_working_136), available = true)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ServerProperty(
                        appString(R.string.text_user_117),
                        status.details.user,
                        modifier = Modifier.weight(1f),
                        highlightValue = true,
                    )
                    ServerProperty(
                        appString(R.string.text_server_113),
                        status.details.host,
                        modifier = Modifier.weight(1f),
                        highlightValue = true,
                    )
                }
                ServerProperty(appString(R.string.text_uptime_137), status.details.uptime, highlightValue = true)
                ServerProperty(
                    appString(R.string.text_operating_system_138),
                    status.details.operatingSystem,
                    highlightValue = true,
                )
                ServerProperty(
                    appString(R.string.text_architecture_139),
                    status.details.architecture,
                    highlightValue = true,
                )
            }
        }
    }
}

@Composable
private fun TiniTalkApiConnectivitySection(status: TiniTalkApiConnectivityStatus) {
    ConnectivitySection {
        when (status) {
            TiniTalkApiConnectivityStatus.Checking -> ConnectivityStatusRow(
                message = appString(R.string.text_checking_the_tinitalk_api_over_https_140),
                checking = true,
            )
            is TiniTalkApiConnectivityStatus.Unavailable -> ConnectivityStatusRow(
                message = status.message.resolve(),
                available = false,
            )
            is TiniTalkApiConnectivityStatus.Available -> {
                ConnectivityStatusRow(message = appString(R.string.text_the_tinitalk_server_is_available_141), available = true)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ServerProperty(
                        appString(R.string.text_api_version_142),
                        status.details.apiVersion?.toString() ?: appString(R.string.text_not_specified_143),
                        modifier = Modifier.weight(1f),
                        highlightValue = true,
                    )
                    ServerProperty(
                        appString(R.string.text_commit_144),
                        status.details.commit ?: appString(R.string.text_not_specified_145),
                        modifier = Modifier.weight(1f),
                        highlightValue = true,
                    )
                }
            }
        }
    }
}

@Composable
private fun ConnectivitySection(content: @Composable ColumnScope.() -> Unit) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = Modifier.padding(14.dp),
            content = content,
        )
    }
}

@Composable
private fun ConnectivityStatusRow(
    message: String,
    checking: Boolean = false,
    available: Boolean = false,
) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        if (checking) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
        } else {
            val color = if (available) AccessVerifiedGreen else MaterialTheme.colorScheme.error
            val iconForeground = MaterialTheme.colorScheme.surface
            Canvas(modifier = Modifier.size(20.dp)) {
                drawCircle(color = color)
                if (available) {
                    val stroke = Stroke(
                        width = 2.dp.toPx(),
                        cap = StrokeCap.Round,
                        join = StrokeJoin.Round,
                    )
                    val check = Path().apply {
                        moveTo(size.width * 0.25f, size.height * 0.52f)
                        lineTo(size.width * 0.43f, size.height * 0.69f)
                        lineTo(size.width * 0.76f, size.height * 0.34f)
                    }
                    drawPath(check, color = iconForeground, style = stroke)
                } else {
                    val strokeWidth = 2.dp.toPx()
                    drawLine(
                        color = iconForeground,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.32f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.68f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                    drawLine(
                        color = iconForeground,
                        start = androidx.compose.ui.geometry.Offset(size.width * 0.68f, size.height * 0.32f),
                        end = androidx.compose.ui.geometry.Offset(size.width * 0.32f, size.height * 0.68f),
                        strokeWidth = strokeWidth,
                        cap = StrokeCap.Round,
                    )
                }
            }
        }
        Text(
            text = message,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun InitialSetupCard(
    state: InitialSetupUiState,
    setupElapsedSeconds: Long,
    stepElapsedSeconds: Long,
    onCheck: () -> Unit,
    onRetryChangedHostKey: () -> Unit,
    onCheckAndStart: () -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    actionsEnabled: Boolean,
    expectedFingerprint: String,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(18.dp),
        ) {
            when (state.mode) {
                InitialSetupUiMode.UNKNOWN -> {
                    Text(appString(R.string.text_server_not_configured_146), style = MaterialTheme.typography.titleMedium)
                    SetupActionButton(appString(R.string.text_start_setup_147), actionsEnabled, onCheckAndStart)
                }

                InitialSetupUiMode.CHECKING -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text(appString(R.string.text_checking_server_148), style = MaterialTheme.typography.titleMedium)
                }

                InitialSetupUiMode.CLEAN -> {
                    Text(appString(R.string.text_server_is_clean_149), style = MaterialTheme.typography.titleMedium)
                    SetupActionButton(appString(R.string.text_start_initial_setup_150), actionsEnabled, onStart)
                }

                InitialSetupUiMode.PARTIAL -> {
                    Text(appString(R.string.text_setup_incomplete_151), style = MaterialTheme.typography.titleMedium)
                    InitialSetupStep.entries.forEach { step ->
                        InitialSetupStepRow(
                            step = step,
                            currentStep = null,
                            running = false,
                            completedSteps = state.completedSteps,
                            stepElapsedSeconds = 0,
                        )
                    }
                    SetupActionButton(appString(R.string.text_continue_setup_152), actionsEnabled, onStart)
                }

                InitialSetupUiMode.RUNNING,
                InitialSetupUiMode.FAILED,
                -> {
                    Text(
                        text = if (state.mode == InitialSetupUiMode.RUNNING) {
                            appString(R.string.text_setting_up_server_153, setupElapsedSeconds.asElapsedTime())
                        } else {
                            appString(R.string.text_setup_stopped_154)
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    InitialSetupStep.entries.forEach { step ->
                        InitialSetupStepRow(
                            step = step,
                            currentStep = state.currentStep,
                            running = state.mode == InitialSetupUiMode.RUNNING,
                            completedSteps = state.completedSteps,
                            stepElapsedSeconds = stepElapsedSeconds,
                        )
                    }
                    state.errorMessage?.let {
                        Text(
                            text = it.resolve(),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.mode == InitialSetupUiMode.FAILED) {
                        SetupActionButton(appString(R.string.text_retry_155), actionsEnabled, onRetry)
                    }
                }

                InitialSetupUiMode.CONFIGURED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            appString(R.string.text_server_configured_156),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.weight(1f),
                        )
                        RefreshStatusIconButton(
                            enabled = actionsEnabled,
                            onClick = onCheck,
                        )
                    }
                }

                InitialSetupUiMode.SSH_HOST_KEY_CHANGED -> {
                    Text(
                        appString(R.string.text_ssh_fingerprint_changed_157),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    ServerProperty(appString(R.string.text_saved_fingerprint_158), expectedFingerprint)
                    ServerProperty(
                        appString(R.string.text_new_fingerprint_159),
                        state.observedFingerprint ?: appString(R.string.text_unknown_160),
                    )
                    if (state.hostKeyCheckInProgress) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            CircularProgressIndicator(
                                strokeWidth = 2.dp,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(appString(R.string.text_checking_ssh_fingerprint_161))
                        }
                    }
                    SetupActionButton(
                        appString(R.string.text_check_again_162),
                        !state.hostKeyCheckInProgress,
                        onRetryChangedHostKey,
                    )
                }
            }
        }
    }
}

@Composable
private fun RefreshStatusIconButton(
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val color = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = appString(R.string.text_check_server_status_163) }
            .clickable(enabled = enabled, onClick = onClick),
    ) {
        val stroke = Stroke(
            width = 2.2.dp.toPx(),
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
        )
        val arrows = Path().apply {
            moveTo(size.width * 0.79f, size.height * 0.38f)
            cubicTo(
                size.width * 0.68f,
                size.height * 0.17f,
                size.width * 0.38f,
                size.height * 0.14f,
                size.width * 0.22f,
                size.height * 0.35f,
            )
            moveTo(size.width * 0.22f, size.height * 0.20f)
            lineTo(size.width * 0.22f, size.height * 0.36f)
            lineTo(size.width * 0.38f, size.height * 0.36f)

            moveTo(size.width * 0.21f, size.height * 0.62f)
            cubicTo(
                size.width * 0.32f,
                size.height * 0.83f,
                size.width * 0.62f,
                size.height * 0.86f,
                size.width * 0.78f,
                size.height * 0.65f,
            )
            moveTo(size.width * 0.78f, size.height * 0.80f)
            lineTo(size.width * 0.78f, size.height * 0.64f)
            lineTo(size.width * 0.62f, size.height * 0.64f)
        }
        drawPath(path = arrows, color = color, style = stroke)
    }
}

@Composable
private fun InitialSetupStepRow(
    step: InitialSetupStep,
    currentStep: InitialSetupStep?,
    running: Boolean,
    completedSteps: Set<InitialSetupStep>,
    stepElapsedSeconds: Long,
) {
    val completed = step in completedSteps ||
        (currentStep != null && step.ordinal < currentStep.ordinal)
    val current = step == currentStep
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth(),
    ) {
        when {
            completed -> Text(
                text = "✓",
                color = AccessVerifiedGreen,
                fontWeight = FontWeight.Bold,
            )
            current && running -> CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp),
            )
            current -> Text("!", color = MaterialTheme.colorScheme.error)
            else -> Text("○", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Text(
            text = buildString {
                append(step.displayName())
                if (current && running) {
                    append(" · ").append(stepElapsedSeconds.asElapsedTime())
                }
            },
            color = if (current || completed) {
                MaterialTheme.colorScheme.onSurface
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun ServerCheckIconButton(
    inProgress: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    IconButton(onClick = onClick, enabled = enabled) {
        if (inProgress) {
            CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(22.dp))
        } else {
            val color = if (enabled) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
            }
            Canvas(
                modifier = Modifier
                    .size(24.dp)
                    .semantics { contentDescription = appString(R.string.text_check_server_availability_164) },
            ) {
                val stroke = Stroke(
                    width = 2.dp.toPx(),
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                )
                drawRoundRect(
                    color = color,
                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(3.dp.toPx()),
                    style = stroke,
                )
                val prompt = Path().apply {
                    moveTo(size.width * 0.24f, size.height * 0.32f)
                    lineTo(size.width * 0.42f, size.height * 0.5f)
                    lineTo(size.width * 0.24f, size.height * 0.68f)
                    moveTo(size.width * 0.5f, size.height * 0.68f)
                    lineTo(size.width * 0.74f, size.height * 0.68f)
                }
                drawPath(prompt, color = color, style = stroke)
            }
        }
    }
}

@Composable
private fun SetupActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    iconResource: Int? = null,
) {
    val withIcon = iconResource != null
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        contentPadding = if (withIcon) {
            PaddingValues(horizontal = 20.dp, vertical = 18.dp)
        } else {
            ButtonDefaults.ContentPadding
        },
        modifier = Modifier
            .fillMaxWidth()
            .then(if (withIcon) Modifier.heightIn(min = 72.dp) else Modifier),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(if (withIcon) 14.dp else 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            iconResource?.let { resource ->
                Icon(
                    painter = painterResource(resource),
                    contentDescription = null,
                    modifier = Modifier.size(30.dp),
                )
            }
            if (withIcon) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            } else {
                Text(label)
            }
        }
    }
}

@Composable
private fun TiniTalkUpdateProgress(elapsedSeconds: Long) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(18.dp),
        ) {
            CircularProgressIndicator(
                strokeWidth = 2.dp,
                modifier = Modifier.size(24.dp),
            )
            Text(
                text = appString(R.string.text_updating_tinitalk_1_s_165, elapsedSeconds.asElapsedTime()),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun TiniTalkUpdateFailure(
    message: String,
    enabled: Boolean,
    onRetry: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(20.dp),
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(18.dp),
        ) {
            Text(
                text = message,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
            )
            SetupActionButton(
                label = appString(R.string.text_check_update_166),
                enabled = enabled,
                onClick = onRetry,
            )
        }
    }
}

@Composable
private fun FileSelectionButton(
    label: String,
    fileName: String?,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(label)
            Text(
                text = fileName ?: appString(R.string.text_choose_file_167),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

private fun Long?.elapsedSeconds(): Long = this?.let {
    ((SystemClock.elapsedRealtime() - it) / 1_000).coerceAtLeast(0)
} ?: 0

private fun Long?.setupElapsedSeconds(): Long = this?.let {
    ((System.currentTimeMillis() - it) / 1_000).coerceAtLeast(0)
} ?: 0

private fun Long.asElapsedTime(): String {
    val minutes = this / 60
    val seconds = this % 60
    return "${minutes.toString().padStart(2, '0')}:${seconds.toString().padStart(2, '0')}"
}

private fun InitialSetupStep.displayName(): String = when (this) {
    InitialSetupStep.SYSTEM_PACKAGES -> appString(R.string.text_system_packages_168)
    InitialSetupStep.FIREWALL -> appString(R.string.firewall)
    InitialSetupStep.FAIL2BAN -> "Fail2ban"
    InitialSetupStep.TLS_CERTIFICATE -> appString(R.string.text_tls_certificate_169)
    InitialSetupStep.PREPARE_TINITALK -> appString(R.string.text_preparing_tinitalk_170)
    InitialSetupStep.UPLOAD_BINARY -> appString(R.string.text_uploading_binary_171)
    InitialSetupStep.START_TINITALK -> appString(R.string.text_starting_tinitalk_172)
}

@Composable
private fun ServerProperty(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlightValue: Boolean = false,
    onValueClick: (() -> Unit)? = null,
) {
    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier,
    ) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            color = MaterialTheme.colorScheme.onSurface,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = if (highlightValue) FontWeight.SemiBold else FontWeight.Medium,
            modifier = if (onValueClick == null) {
                Modifier
            } else {
                Modifier.clickable(onClick = onValueClick)
            },
        )
    }
}

internal fun copyPlainText(context: Context, label: String, text: String) {
    val clipboard = context.applicationContext.getSystemService(ClipboardManager::class.java)
    clipboard.setPrimaryClip(ClipData.newPlainText(label, text))
}
