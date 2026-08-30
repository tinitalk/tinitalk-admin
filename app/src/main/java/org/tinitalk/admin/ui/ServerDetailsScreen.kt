package org.tinitalk.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.tinitalk.admin.model.ServerRecord

@Composable
fun ServerDetailsScreen(
    server: ServerRecord,
    snackbarHostState: SnackbarHostState,
    sshCheckInProgress: Boolean,
    sshCheckResult: SshCheckResult?,
    onBack: () -> Unit,
    onRename: (String) -> Unit,
    onRemove: () -> Unit,
    onCheckSsh: () -> Unit,
    onDismissSshCheckResult: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var renameDialogVisible by rememberSaveable(server.id) { mutableStateOf(false) }
    var deleteDialogVisible by rememberSaveable(server.id) { mutableStateOf(false) }
    var menuExpanded by rememberSaveable(server.id) { mutableStateOf(false) }
    var nameDraft by rememberSaveable(server.id) { mutableStateOf(server.displayName) }
    val renameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(renameDialogVisible) {
        if (renameDialogVisible) {
            renameFocusRequester.requestFocus()
            keyboardController?.show()
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
                    ServerProperty("Адрес", server.enteredAddress)
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
            OutlinedButton(
                onClick = onCheckSsh,
                enabled = !sshCheckInProgress,
                shape = RoundedCornerShape(16.dp),
                contentPadding = PaddingValues(vertical = 14.dp),
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                if (sshCheckInProgress) {
                    CircularProgressIndicator(
                        strokeWidth = 2.dp,
                        modifier = Modifier.size(20.dp),
                    )
                    Box(Modifier.width(10.dp))
                }
                Text(if (sshCheckInProgress) "Проверяем SSH-доступ…" else "Проверить SSH")
            }
        }
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

    sshCheckResult?.let { result ->
        AlertDialog(
            onDismissRequest = onDismissSshCheckResult,
            title = { Text("SSH-доступ работает") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    ServerProperty("Пользователь", result.user, highlightValue = true)
                    ServerProperty("Сервер", result.host, highlightValue = true)
                    ServerProperty("Время работы", result.uptime, highlightValue = true)
                }
            },
            confirmButton = {
                TextButton(onClick = onDismissSshCheckResult) {
                    Text("Закрыть")
                }
            },
        )
    }
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
