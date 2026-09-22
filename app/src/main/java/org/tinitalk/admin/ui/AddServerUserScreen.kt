package org.tinitalk.admin.ui

import org.tinitalk.admin.i18n.appString

import org.tinitalk.admin.R

import android.annotation.SuppressLint
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.AndroidClipboard
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.nativeClipboardManager
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import org.tinitalk.admin.server.ServerUserCredential

@Composable
fun AddServerUserScreen(
    state: AddServerUserState,
    serverAddress: String,
    onBack: () -> Unit,
    onLoginChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onTokenCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formEnabled = !state.submitting && state.credential == null
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = appString(R.string.text_add_user_22),
            onBack = onBack,
            backEnabled = formEnabled,
        )
        OutlinedTextField(
            value = state.login,
            onValueChange = onLoginChange,
            enabled = formEnabled,
            label = { Text(appString(R.string.text_username_23)) },
            isError = state.loginError != null,
            supportingText = state.loginError?.let { error -> { Text(error.resolve()) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            enabled = formEnabled,
            label = { Text(appString(R.string.text_name_24)) },
            isError = state.displayNameError != null,
            supportingText = state.displayNameError?.let { error -> { Text(error.resolve()) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
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
        Button(
            onClick = onSubmit,
            enabled = formEnabled,
            shape = MaterialTheme.shapes.large,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            if (state.submitting) {
                CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.onPrimary,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(24.dp),
                )
            } else {
                Text(appString(R.string.text_add_1))
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    state.credential?.let { credential ->
        ServerUserTokenDialog(
            title = appString(R.string.text_user_added_25),
            login = state.login,
            serverAddress = serverAddress,
            credential = credential,
            onTokenCopied = onTokenCopied,
        )
    }
}

@Composable
fun ServerUserTokenDialog(
    title: String,
    login: String,
    serverAddress: String,
    credential: ServerUserCredential,
    onTokenCopied: () -> Unit,
) {
    val context = LocalContext.current
    val clipboard = LocalClipboard.current
    val accessText = remember(login, serverAddress, credential) {
        "${login.trim()}@${serverAddress.trim()}\n${credential.value}"
    }
    // Compose checks this public Android-specific type before opening the selection menu.
    @SuppressLint("VisibleForTests")
    val tokenClipboard = remember(context, clipboard) {
        object : AndroidClipboard {
            override val clipboardManager = clipboard.nativeClipboardManager

            override suspend fun getClipEntry(): ClipEntry? = clipboard.getClipEntry()

            override suspend fun setClipEntry(clipEntry: ClipEntry?) {
                if (clipEntry == null) {
                    clipboard.setClipEntry(null)
                } else {
                    SensitiveClipboard.copy(context, clipEntry.clipData)
                }
            }
        }
    }
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    if (credential.temporary) {
                        appString(R.string.text_this_is_a_temporary_password_the_user_will_set_their_own_26)
                    } else {
                        appString(R.string.text_save_the_password_now_you_cannot_view_it_again_after_clo_27)
                    },
                )
                // Protect selection-menu, keyboard and accessibility copies from this field only.
                CompositionLocalProvider(LocalClipboard provides tokenClipboard) {
                    OutlinedTextField(
                        value = accessText,
                        onValueChange = {},
                        readOnly = true,
                        minLines = 2,
                        maxLines = 3,
                        textStyle = MaterialTheme.typography.bodyMedium.copy(
                            fontFamily = FontFamily.Monospace,
                        ),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    SensitiveClipboard.copyToken(context, accessText)
                    onTokenCopied()
                },
            ) {
                Text(appString(R.string.text_copy_28))
            }
        },
    )
}
