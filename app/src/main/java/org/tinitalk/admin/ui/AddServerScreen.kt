package org.tinitalk.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp

@Composable
fun AddServerScreen(
    state: AddServerState,
    onBack: () -> Unit,
    onDisplayNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onLoginChange: (String) -> Unit,
    onScanFingerprint: () -> Unit,
    onRejectFingerprint: () -> Unit,
    onConfirmFingerprint: () -> Unit,
    onAuthenticationChange: (AuthenticationMethod) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onChoosePrivateKey: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val formEnabled = state.phase == AddServerPhase.Form
    val operationInProgress = state.phase == AddServerPhase.ScanningFingerprint ||
        state.phase == AddServerPhase.CheckingAccess
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = "Добавить сервер",
            onBack = onBack,
            backEnabled = !operationInProgress,
        )
        EndpointFields(
            state = state,
            enabled = formEnabled,
            onDisplayNameChange = onDisplayNameChange,
            onAddressChange = onAddressChange,
            onPortChange = onPortChange,
            onLoginChange = onLoginChange,
        )
        CredentialsForm(
            state = state,
            enabled = formEnabled,
            onAuthenticationChange = onAuthenticationChange,
            onPasswordChange = onPasswordChange,
            onPassphraseChange = onPassphraseChange,
            onChoosePrivateKey = onChoosePrivateKey,
        )

        state.errorMessage?.let { ErrorPanel(it) }

        when (state.phase) {
            AddServerPhase.Form -> Button(
                onClick = onScanFingerprint,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Добавить")
            }

            AddServerPhase.ScanningFingerprint -> ProgressRow("Получаем SSH fingerprint…")
            is AddServerPhase.ConfirmFingerprint -> Unit
            AddServerPhase.CheckingAccess -> ProgressRow("Настраиваем безопасный SSH-доступ…")
        }
        Spacer(Modifier.height(12.dp))
    }

    (state.phase as? AddServerPhase.ConfirmFingerprint)?.let { phase ->
        AlertDialog(
            onDismissRequest = onRejectFingerprint,
            title = { Text("Подтвердите SSH fingerprint") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Сверьте fingerprint с данными вашего VPS-провайдера.")
                    Text("Алгоритм", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(phase.key.algorithm, fontWeight = FontWeight.SemiBold)
                    Text("SHA-256", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        text = phase.key.sha256Fingerprint,
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.bodySmall,
                    )
                    state.frozenIpv4?.let {
                        Text("IP: $it", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = onConfirmFingerprint) { Text("Подтвердить") }
            },
            dismissButton = {
                TextButton(onClick = onRejectFingerprint) { Text("Отмена") }
            },
        )
    }
}

@Composable
private fun EndpointFields(
    state: AddServerState,
    enabled: Boolean,
    onDisplayNameChange: (String) -> Unit,
    onAddressChange: (String) -> Unit,
    onPortChange: (String) -> Unit,
    onLoginChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = state.displayName,
        onValueChange = onDisplayNameChange,
        enabled = enabled,
        label = { Text("Название (необязательно)") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.address,
        onValueChange = onAddressChange,
        enabled = enabled,
        label = { Text("IPv4-адрес или домен") },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.sshPort,
            onValueChange = onPortChange,
            enabled = enabled,
            label = { Text("SSH-порт") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.sshLogin,
            onValueChange = onLoginChange,
            enabled = enabled,
            label = { Text("SSH-логин") },
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
    }
}

@Composable
private fun CredentialsForm(
    state: AddServerState,
    enabled: Boolean,
    onAuthenticationChange: (AuthenticationMethod) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onChoosePrivateKey: () -> Unit,
) {
    Text(
        text = "Способ входа",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthenticationButton(
            text = "Пароль",
            selected = state.authentication == AuthenticationMethod.PASSWORD,
            enabled = enabled,
            onClick = { onAuthenticationChange(AuthenticationMethod.PASSWORD) },
            modifier = Modifier.weight(1f),
        )
        AuthenticationButton(
            text = "Private key",
            selected = state.authentication == AuthenticationMethod.PRIVATE_KEY,
            enabled = enabled,
            onClick = { onAuthenticationChange(AuthenticationMethod.PRIVATE_KEY) },
            modifier = Modifier.weight(1f),
        )
    }
    if (state.authentication == AuthenticationMethod.PASSWORD) {
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            enabled = enabled,
            label = { Text("SSH-пароль") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
    } else {
        OutlinedTextField(
            value = state.privateKeyPassphrase,
            onValueChange = onPassphraseChange,
            enabled = enabled,
            label = { Text("Passphrase ключа (если есть)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        OutlinedButton(
            onClick = onChoosePrivateKey,
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(if (state.privateKeySelected) "Выбрать другой private key" else "Выбрать private key")
        }
        if (state.privateKeySelected) {
            Text(
                text = "Private key выбран",
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = "Файл ключа используется один раз и не сохраняется приложением.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun AuthenticationButton(
    text: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outline
            },
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            contentColor = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
        ),
        modifier = modifier,
    ) {
        Text(text)
    }
}

@Composable
private fun ProgressRow(text: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().padding(vertical = 12.dp),
    ) {
        CircularProgressIndicator()
        Text(text, modifier = Modifier.padding(top = 10.dp))
    }
}

@Composable
private fun ErrorPanel(message: String) {
    Surface(
        color = MaterialTheme.colorScheme.errorContainer,
        contentColor = MaterialTheme.colorScheme.onErrorContainer,
        shape = MaterialTheme.shapes.large,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(message, modifier = Modifier.padding(14.dp))
    }
}
