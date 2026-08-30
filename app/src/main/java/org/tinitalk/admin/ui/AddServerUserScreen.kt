package org.tinitalk.admin.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
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
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

@Composable
fun AddServerUserScreen(
    state: AddServerUserState,
    onBack: () -> Unit,
    onLoginChange: (String) -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onSubmit: () -> Unit,
    onTokenCopied: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formEnabled = !state.submitting && state.token == null
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = "Добавить пользователя",
            onBack = onBack,
            backEnabled = formEnabled,
        )
        OutlinedTextField(
            value = state.login,
            onValueChange = onLoginChange,
            enabled = formEnabled,
            label = { Text("Логин") },
            isError = state.loginError != null,
            supportingText = state.loginError?.let { error -> { Text(error) } },
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedTextField(
            value = state.displayName,
            onValueChange = onDisplayNameChange,
            enabled = formEnabled,
            label = { Text("Имя") },
            isError = state.displayNameError != null,
            supportingText = state.displayNameError?.let { error -> { Text(error) } },
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
                    text = message,
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
                Text("Добавить")
            }
        }
        Spacer(Modifier.height(12.dp))
    }

    state.token?.let { token ->
        ServerUserTokenDialog(
            title = "Пользователь добавлен",
            token = token,
            onTokenCopied = onTokenCopied,
        )
    }
}

@Composable
fun ServerUserTokenDialog(
    title: String,
    token: String,
    onTokenCopied: () -> Unit,
) {
    val context = LocalContext.current
    AlertDialog(
        onDismissRequest = {},
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Сохраните токен сейчас. После закрытия посмотреть его снова будет невозможно.",
                )
                OutlinedTextField(
                    value = token,
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
        },
        confirmButton = {
            Button(
                onClick = {
                    copySensitiveText(context, token)
                    onTokenCopied()
                },
            ) {
                Text("Скопировать")
            }
        },
    )
}

private fun copySensitiveText(context: Context, value: String) {
    val clipboard = context.getSystemService(ClipboardManager::class.java)
    val clip = ClipData.newPlainText("TiniTalk token", value)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        clip.description.extras = PersistableBundle().apply {
            putBoolean(android.content.ClipDescription.EXTRA_IS_SENSITIVE, true)
        }
    }
    clipboard.setPrimaryClip(clip)
}
