package org.tinitalk.admin.ui

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
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
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
    serverOperationStartedAt: Long?,
    serverOperationInProgress: Boolean,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onCheckConnectivity: () -> Unit,
    onDismissConnectivity: () -> Unit,
    onCheckInitialSetup: () -> Unit,
    onOpenUsers: () -> Unit,
    onCheckAndContinueInitialSetup: () -> Unit,
    onContinueInitialSetup: () -> Unit,
    onRetryInitialSetup: () -> Unit,
    onCloseTiniTalkBinary: () -> Unit,
    onChooseTiniTalkBinary: () -> Unit,
    onStartInitialSetup: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameDialogVisible by rememberSaveable(server.id) { mutableStateOf(false) }
    var deleteDialogVisible by rememberSaveable(server.id) { mutableStateOf(false) }
    var menuExpanded by rememberSaveable(server.id) { mutableStateOf(false) }
    var nameDraft by rememberSaveable(server.id) { mutableStateOf(server.displayName) }
    val renameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    var serverOperationElapsedSeconds by remember(serverOperationStartedAt) {
        mutableLongStateOf(serverOperationStartedAt.elapsedSeconds())
    }
    var setupElapsedSeconds by remember(initialSetup.startedAtEpochMillis) {
        mutableLongStateOf(initialSetup.startedAtEpochMillis.setupElapsedSeconds())
    }
    val setupBusy = initialSetup.mode == InitialSetupUiMode.CHECKING ||
        initialSetup.mode == InitialSetupUiMode.RUNNING
    val actionsEnabled = !serverConnectivity.inProgress && !serverOperationInProgress && !setupBusy

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
                title = "Сервер",
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
                                text = {
                                    Text(
                                        text = "Удалить из приложения",
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
                    text = server.displayName.ifEmpty { "Без названия" },
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
                            label = "Адрес",
                            value = server.enteredAddress,
                            modifier = Modifier.weight(1f),
                        )
                        ServerCheckIconButton(
                            inProgress = serverConnectivity.inProgress,
                            enabled = actionsEnabled,
                            onClick = onCheckConnectivity,
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(18.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        ServerProperty(
                            label = "SSH-порт",
                            value = server.sshPort.toString(),
                            modifier = Modifier.weight(1f),
                        )
                        ServerProperty(
                            label = "Пользователь",
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
                onCheckAndStart = onCheckAndContinueInitialSetup,
                onStart = onContinueInitialSetup,
                onRetry = onRetryInitialSetup,
                actionsEnabled = actionsEnabled,
            )
            if (initialSetup.mode == InitialSetupUiMode.CONFIGURED) {
                SetupActionButton(
                    label = "Пользователи",
                    enabled = actionsEnabled,
                    onClick = onOpenUsers,
                )
            }
        }
    }

    if (binarySelection.visible) {
        AlertDialog(
            onDismissRequest = onCloseTiniTalkBinary,
            title = { Text("Первичная настройка") },
            text = {
                FileSelectionButton(
                    label = "Бинарник TiniTalk Server",
                    fileName = binarySelection.binaryName,
                    onClick = onChooseTiniTalkBinary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onStartInitialSetup,
                    enabled = binarySelection.ready,
                ) {
                    Text("Начать")
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseTiniTalkBinary) {
                    Text("Отмена")
                }
            },
        )
    }

    if (renameDialogVisible) {
        AlertDialog(
            onDismissRequest = { renameDialogVisible = false },
            title = { Text("Название сервера") },
            text = {
                OutlinedTextField(
                    value = nameDraft,
                    onValueChange = { nameDraft = it },
                    label = { Text("Название") },
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
                    Text("Сохранить")
                }
            },
            dismissButton = {
                TextButton(onClick = { renameDialogVisible = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text("Удалить сервер?") },
            text = {
                Text("Сервер будет удалён только из приложения. На VPS ничего не изменится.")
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
                    Text("Удалить")
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteDialogVisible = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (serverConnectivity.visible) {
        AlertDialog(
            onDismissRequest = onDismissConnectivity,
            title = { Text("Доступность сервера") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    SshConnectivitySection(serverConnectivity.ssh)
                    TiniTalkApiConnectivitySection(serverConnectivity.api)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissConnectivity) {
                    Text("Закрыть")
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
                message = "Проверяем SSH-доступ…",
                checking = true,
            )
            is SshConnectivityStatus.Unavailable -> ConnectivityStatusRow(
                message = status.message,
                available = false,
            )
            is SshConnectivityStatus.Available -> {
                ConnectivityStatusRow(message = "SSH-доступ работает", available = true)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ServerProperty(
                        "Пользователь",
                        status.details.user,
                        modifier = Modifier.weight(1f),
                        highlightValue = true,
                    )
                    ServerProperty(
                        "Сервер",
                        status.details.host,
                        modifier = Modifier.weight(1f),
                        highlightValue = true,
                    )
                }
                ServerProperty("Время работы", status.details.uptime, highlightValue = true)
                ServerProperty(
                    "Операционная система",
                    status.details.operatingSystem,
                    highlightValue = true,
                )
                ServerProperty(
                    "Архитектура",
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
                message = "Проверяем TiniTalk API по HTTPS…",
                checking = true,
            )
            is TiniTalkApiConnectivityStatus.Unavailable -> ConnectivityStatusRow(
                message = status.message,
                available = false,
            )
            is TiniTalkApiConnectivityStatus.Available -> {
                ConnectivityStatusRow(message = "Сервер TiniTalk доступен", available = true)
                Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    ServerProperty(
                        "Версия API",
                        status.details.apiVersion?.toString() ?: "Не указана",
                        modifier = Modifier.weight(1f),
                        highlightValue = true,
                    )
                    ServerProperty(
                        "Коммит",
                        status.details.commit ?: "Не указан",
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
    onCheckAndStart: () -> Unit,
    onStart: () -> Unit,
    onRetry: () -> Unit,
    actionsEnabled: Boolean,
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
                    Text("Сервер не настроен", style = MaterialTheme.typography.titleMedium)
                    SetupActionButton("Запустить настройку", actionsEnabled, onCheckAndStart)
                }

                InitialSetupUiMode.CHECKING -> Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(strokeWidth = 2.dp, modifier = Modifier.size(20.dp))
                    Text("Проверяем сервер…", style = MaterialTheme.typography.titleMedium)
                }

                InitialSetupUiMode.CLEAN -> {
                    Text("Сервер чистый", style = MaterialTheme.typography.titleMedium)
                    SetupActionButton("Запустить первичную настройку", actionsEnabled, onStart)
                }

                InitialSetupUiMode.PARTIAL -> {
                    Text("Настройка не завершена", style = MaterialTheme.typography.titleMedium)
                    InitialSetupStep.entries.forEach { step ->
                        InitialSetupStepRow(
                            step = step,
                            currentStep = null,
                            running = false,
                            completedSteps = state.completedSteps,
                            stepElapsedSeconds = 0,
                        )
                    }
                    SetupActionButton("Продолжить настройку", actionsEnabled, onStart)
                }

                InitialSetupUiMode.RUNNING,
                InitialSetupUiMode.FAILED,
                -> {
                    Text(
                        text = if (state.mode == InitialSetupUiMode.RUNNING) {
                            "Идёт настройка сервера · " +
                                setupElapsedSeconds.asElapsedTime()
                        } else {
                            "Настройка остановлена"
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
                            text = it,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    if (state.mode == InitialSetupUiMode.FAILED) {
                        SetupActionButton("Повторить", actionsEnabled, onRetry)
                    }
                }

                InitialSetupUiMode.CONFIGURED -> {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "Сервер настроен",
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
            .semantics { contentDescription = "Проверить состояние сервера" }
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
                    .semantics { contentDescription = "Проверить доступность сервера" },
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
private fun SetupActionButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
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
                text = fileName ?: "Выбрать файл",
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
    InitialSetupStep.SYSTEM_PACKAGES -> "Системные пакеты"
    InitialSetupStep.FIREWALL -> "Firewall"
    InitialSetupStep.TLS_CERTIFICATE -> "TLS-сертификат"
    InitialSetupStep.PREPARE_TINITALK -> "Подготовка TiniTalk"
    InitialSetupStep.UPLOAD_BINARY -> "Загрузка бинарника"
    InitialSetupStep.START_TINITALK -> "Запуск TiniTalk"
}

@Composable
private fun ServerProperty(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    highlightValue: Boolean = false,
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
        )
    }
}
