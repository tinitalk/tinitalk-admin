package org.tinitalk.admin.ui

import org.tinitalk.admin.i18n.appString

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
    val actionsEnabled = !state.busy && state.credential == null

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
                title = appString(R.string.text_user_117),
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
                                    UserMenuItemText(appString(R.string.text_rename_175))
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
                                    UserMenuItemText(appString(R.string.text_change_password_176))
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
                                        if (user.disabled) appString(R.string.text_unblock_177) else appString(R.string.text_block_178),
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
                                        text = appString(R.string.text_delete_132),
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
                    UserProperty(label = appString(R.string.text_name_24), value = user.displayName)
                    UserProperty(
                        label = appString(R.string.text_username_23),
                        value = user.login,
                        onValueClick = {
                            copyPlainText(context, appString(R.string.text_username_23), user.login)
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
                        text = message.resolve(),
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
            title = { Text(appString(R.string.text_rename_user_179)) },
            text = {
                OutlinedTextField(
                    value = state.renameDraft,
                    onValueChange = onRenameDraftChange,
                    enabled = !state.renaming,
                    label = { Text(appString(R.string.text_name_24)) },
                    isError = state.renameErrorMessage != null,
                    supportingText = state.renameErrorMessage?.let { error ->
                        { Text(error.resolve()) }
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
                    Text(if (state.renaming) appString(R.string.text_saving_180) else appString(R.string.text_save_129))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = onCloseRename,
                    enabled = !state.renaming,
                ) {
                    Text(appString(R.string.text_cancel_8))
                }
            },
        )
    }

    if (deleteDialogVisible) {
        AlertDialog(
            onDismissRequest = { deleteDialogVisible = false },
            title = { Text(appString(R.string.text_delete_user_181)) },
            text = {
                Text(
                    appString(R.string.text_this_action_cannot_be_undone_the_user_s_password_linked_182),
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

    if (rotateTokenDialogVisible) {
        AlertDialog(
            onDismissRequest = { rotateTokenDialogVisible = false },
            title = { Text(appString(R.string.text_change_password_183)) },
            text = {
                Text(
                    appString(R.string.text_the_current_sign_in_credentials_will_stop_working_immedi_184),
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
                    Text(appString(R.string.text_change_185))
                }
            },
            dismissButton = {
                TextButton(onClick = { rotateTokenDialogVisible = false }) {
                    Text(appString(R.string.text_cancel_8))
                }
            },
        )
    }

    if (accessDialogVisible) {
        val blocking = !user.disabled
        AlertDialog(
            onDismissRequest = { accessDialogVisible = false },
            title = {
                Text(if (blocking) appString(R.string.text_block_user_186) else appString(R.string.text_unblock_user_187))
            },
            text = {
                Text(
                    if (blocking) {
                        appString(R.string.text_new_sign_ins_requests_and_connections_will_be_blocked_th_188)
                    } else {
                        appString(R.string.text_the_user_will_be_able_to_sign_in_again_with_the_same_pas_189)
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
                    Text(if (blocking) appString(R.string.text_block_178) else appString(R.string.text_unblock_177))
                }
            },
            dismissButton = {
                TextButton(onClick = { accessDialogVisible = false }) {
                    Text(appString(R.string.text_cancel_8))
                }
            },
        )
    }

    state.credential?.let { credential ->
        ServerUserTokenDialog(
            title = appString(R.string.text_new_password_ready_190),
            login = user.login,
            serverAddress = serverAddress,
            credential = credential,
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
            text = appString(R.string.text_status_191),
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
                text = if (disabled) appString(R.string.text_blocked_192) else appString(R.string.text_enabled_193),
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
