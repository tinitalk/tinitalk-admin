package org.tinitalk.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import org.tinitalk.admin.server.ServerUser

@Composable
fun ServerUserDetailsScreen(
    user: ServerUser,
    state: ServerUserDetailsUiState,
    onBack: () -> Unit,
    onRotateToken: () -> Unit,
    onTokenCopied: () -> Unit,
    onChangeAccess: () -> Unit,
    onOpenRename: () -> Unit,
    onCloseRename: () -> Unit,
    onRenameDraftChange: (String) -> Unit,
    onRename: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var deleteDialogVisible by rememberSaveable(user.login) { mutableStateOf(false) }
    var rotateTokenDialogVisible by rememberSaveable(user.login) { mutableStateOf(false) }
    var accessDialogVisible by rememberSaveable(user.login) { mutableStateOf(false) }
    val renameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current

    LaunchedEffect(state.renameDialogVisible) {
        if (state.renameDialogVisible) {
            renameFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.spacedBy(18.dp),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            ScreenHeader(
                title = "Пользователь",
                onBack = onBack,
                backEnabled = !state.busy && state.token == null,
            )
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
                    UserProperty(label = "Имя", value = user.displayName)
                    UserProperty(label = "Логин", value = user.login)
                    UserStatusProperty(disabled = user.disabled)
                }
            }
            Spacer(Modifier.weight(1f))
            state.errorMessage?.let { message ->
                Surface(
                    color = MaterialTheme.colorScheme.errorContainer,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = message,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(14.dp),
                    )
                }
            }
            OutlinedButton(
                onClick = onOpenRename,
                enabled = !state.busy && state.token == null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text("Переименовать")
            }
            OutlinedButton(
                onClick = { rotateTokenDialogVisible = true },
                enabled = !state.busy && state.token == null,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.rotatingToken) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Text("Меняем…")
                    }
                } else {
                    Text("Сменить токен")
                }
            }
            OutlinedButton(
                onClick = { accessDialogVisible = true },
                enabled = !state.busy && state.token == null,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    if (user.disabled) {
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.72f)
                    } else {
                        MaterialTheme.colorScheme.error.copy(alpha = 0.72f)
                    },
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = if (user.disabled) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    },
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.changingAccess) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = if (user.disabled) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.error
                            },
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(if (user.disabled) "Разблокируем…" else "Блокируем…")
                    }
                } else {
                    Text(if (user.disabled) "Разблокировать" else "Заблокировать")
                }
            }
            OutlinedButton(
                onClick = { deleteDialogVisible = true },
                enabled = !state.busy && state.token == null,
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(
                    1.dp,
                    MaterialTheme.colorScheme.error.copy(alpha = 0.72f),
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.deleting) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.error,
                            strokeWidth = 2.dp,
                            modifier = Modifier.size(20.dp),
                        )
                        Text("Удаляем…")
                    }
                } else {
                    Text("Удалить")
                }
            }
        }
    }

    if (state.renameDialogVisible) {
        AlertDialog(
            onDismissRequest = onCloseRename,
            title = { Text("Переименовать пользователя") },
            text = {
                OutlinedTextField(
                    value = state.renameDraft,
                    onValueChange = onRenameDraftChange,
                    enabled = !state.renaming,
                    label = { Text("Имя") },
                    isError = state.renameErrorMessage != null,
                    supportingText = state.renameErrorMessage?.let { error ->
                        { Text(error) }
                    },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(renameFocusRequester),
                )
            },
            confirmButton = {
                TextButton(
                    onClick = onRename,
                    enabled = !state.renaming,
                ) {
                    Text(if (state.renaming) "Сохраняем…" else "Сохранить")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCloseRename,
                    enabled = !state.renaming,
                ) {
                    Text("Отмена")
                }
            },
        )
    }

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text("Удалить пользователя?") },
            text = {
                Text(
                    "Это действие нельзя отменить. " +
                        "Будут удалены его токен, привязанные устройства, контакты и история звонков.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        deleteDialogVisible = false
                        onDelete()
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

    if (rotateTokenDialogVisible) {
        AlertDialog(
            onDismissRequest = { rotateTokenDialogVisible = false },
            title = { Text("Сменить токен?") },
            text = {
                Text(
                    "Текущий токен сразу перестанет работать. Пользователь потеряет доступ, " +
                        "а привязанные устройства будут удалены. Отменить действие и восстановить " +
                        "старый токен нельзя. Новый токен будет показан только один раз.",
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        rotateTokenDialogVisible = false
                        onRotateToken()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error,
                    ),
                ) {
                    Text("Сменить")
                }
            },
            dismissButton = {
                TextButton(onClick = { rotateTokenDialogVisible = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    if (accessDialogVisible) {
        val blocking = !user.disabled
        AlertDialog(
            onDismissRequest = { accessDialogVisible = false },
            title = {
                Text(if (blocking) "Заблокировать пользователя?" else "Разблокировать пользователя?")
            },
            text = {
                Text(
                    if (blocking) {
                        "Новые авторизации, запросы и подключения пользователя будут запрещены. " +
                            "Токен и привязанные устройства сохранятся. Уже открытое соединение " +
                            "может работать до отключения."
                    } else {
                        "Пользователь снова сможет авторизоваться с прежним токеном. " +
                            "Сохранённые привязки устройств останутся доступны."
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        accessDialogVisible = false
                        onChangeAccess()
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (blocking) {
                            MaterialTheme.colorScheme.error
                        } else {
                            MaterialTheme.colorScheme.primary
                        },
                    ),
                ) {
                    Text(if (blocking) "Заблокировать" else "Разблокировать")
                }
            },
            dismissButton = {
                TextButton(onClick = { accessDialogVisible = false }) {
                    Text("Отмена")
                }
            },
        )
    }

    state.token?.let { token ->
        ServerUserTokenDialog(
            title = "Новый токен готов",
            token = token,
            onTokenCopied = onTokenCopied,
        )
    }
}

@Composable
private fun UserProperty(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun UserStatusProperty(disabled: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "Статус",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ServerUserStatusIcon(
                disabled = disabled,
                modifier = Modifier.size(20.dp),
            )
            Text(
                text = if (disabled) "Заблокирован" else "Включён",
                color = if (disabled) {
                    MaterialTheme.colorScheme.error
                } else {
                    org.tinitalk.admin.ui.theme.AccessVerifiedGreen
                },
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
        }
    }
}
