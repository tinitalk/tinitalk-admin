package org.tinitalk.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.tinitalk.admin.R
import org.tinitalk.admin.server.ServerUser

@Composable
fun ServerUserDetailsScreen(
    user: ServerUser,
    serverAddress: String,
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
    var menuExpanded by rememberSaveable(user.login) { mutableStateOf(false) }
    val renameFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val context = LocalContext.current
    val actionsEnabled = !state.busy && state.token == null

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
                backEnabled = actionsEnabled,
                actions = {
                    Box {
                        IconButton(
                            onClick = { menuExpanded = true },
                            enabled = actionsEnabled,
                        ) {
                            MoreVertIcon()
                        }
                        DropdownMenu(
                            expanded = menuExpanded,
                            onDismissRequest = { menuExpanded = false },
                            modifier = Modifier.widthIn(min = 260.dp),
                        ) {
                            DropdownMenuItem(
                                text = {
                                    UserMenuItemText("Переименовать")
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_edit),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                enabled = actionsEnabled,
                                modifier = Modifier.heightIn(min = 58.dp),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                                onClick = {
                                    menuExpanded = false
                                    onOpenRename()
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    UserMenuItemText("Сменить токен")
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_key),
                                        contentDescription = null,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                enabled = actionsEnabled,
                                modifier = Modifier.heightIn(min = 58.dp),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                                onClick = {
                                    menuExpanded = false
                                    rotateTokenDialogVisible = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    UserMenuItemText(
                                        if (user.disabled) "Разблокировать" else "Заблокировать",
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(
                                            if (user.disabled) R.drawable.ic_lock_open else R.drawable.ic_lock,
                                        ),
                                        contentDescription = null,
                                        tint = if (user.disabled) {
                                            MaterialTheme.colorScheme.primary
                                        } else {
                                            MaterialTheme.colorScheme.error
                                        },
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                enabled = actionsEnabled,
                                modifier = Modifier.heightIn(min = 58.dp),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                                onClick = {
                                    menuExpanded = false
                                    accessDialogVisible = true
                                },
                            )
                            DropdownMenuItem(
                                text = {
                                    UserMenuItemText(
                                        text = "Удалить",
                                        color = MaterialTheme.colorScheme.error,
                                    )
                                },
                                leadingIcon = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete),
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(24.dp),
                                    )
                                },
                                enabled = actionsEnabled,
                                modifier = Modifier.heightIn(min = 58.dp),
                                contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                                onClick = {
                                    menuExpanded = false
                                    deleteDialogVisible = true
                                },
                            )
                        }
                    }
                },
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
                    UserProperty(
                        label = "Логин",
                        value = user.login,
                        onValueClick = {
                            copyPlainText(context, "TiniTalk user login", user.login)
                        },
                    )
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
            login = user.login,
            serverAddress = serverAddress,
            token = token,
            onTokenCopied = onTokenCopied,
        )
    }
}

@Composable
private fun UserMenuItemText(
    text: String,
    color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface,
) {
    Text(
        text = text,
        color = color,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun UserProperty(
    label: String,
    value: String,
    onValueClick: (() -> Unit)? = null,
) {
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
            modifier = if (onValueClick == null) {
                Modifier
            } else {
                Modifier.clickable(onClick = onValueClick)
            },
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
