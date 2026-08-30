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
    onVerifyPassword: () -> Unit,
    onChoosePrivateKey: () -> Unit,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val endpointEditable = state.phase == AddServerPhase.EndpointForm
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        TextButton(
            onClick = onBack,
            enabled = state.phase != AddServerPhase.CheckingAccess,
        ) {
            Text("← Назад")
        }
        Text(
            text = "Добавить сервер",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        if (state.phase is AddServerPhase.EndpointForm ||
            state.phase is AddServerPhase.ScanningFingerprint ||
            state.phase is AddServerPhase.ConfirmFingerprint
        ) {
            EndpointFields(
                state = state,
                enabled = endpointEditable,
                onDisplayNameChange = onDisplayNameChange,
                onAddressChange = onAddressChange,
                onPortChange = onPortChange,
                onLoginChange = onLoginChange,
            )
        } else {
            EndpointSummary(state)
        }

        state.errorMessage?.let { ErrorPanel(it) }

        when (val phase = state.phase) {
            AddServerPhase.EndpointForm -> Button(
                onClick = onScanFingerprint,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text("Получить SSH fingerprint")
            }

            AddServerPhase.ScanningFingerprint -> ProgressRow("Получаем SSH fingerprint…")
            is AddServerPhase.ConfirmFingerprint -> Unit
            AddServerPhase.Credentials -> CredentialsForm(
                state = state,
                onAuthenticationChange = onAuthenticationChange,
                onPasswordChange = onPasswordChange,
                onPassphraseChange = onPassphraseChange,
                onVerifyPassword = onVerifyPassword,
                onChoosePrivateKey = onChoosePrivateKey,
            )

            AddServerPhase.CheckingAccess -> ProgressRow("Проверяем SSH-доступ…")
            is AddServerPhase.Failed -> {
                ErrorPanel(phase.message)
                OutlinedButton(
                    onClick = onRetry,
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Попробовать снова")
                }
            }
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
private fun EndpointSummary(state: AddServerState) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.large,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(
            verticalArrangement = Arrangement.spacedBy(4.dp),
            modifier = Modifier.padding(16.dp),
        ) {
            Text(state.displayName, style = MaterialTheme.typography.titleMedium)
            Text(
                "${state.sshLogin}@${state.address}:${state.sshPort}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            state.frozenIpv4?.let {
                Text("IP: $it", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun CredentialsForm(
    state: AddServerState,
    onAuthenticationChange: (AuthenticationMethod) -> Unit,
    onPasswordChange: (String) -> Unit,
    onPassphraseChange: (String) -> Unit,
    onVerifyPassword: () -> Unit,
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
            onClick = { onAuthenticationChange(AuthenticationMethod.PASSWORD) },
            modifier = Modifier.weight(1f),
        )
        AuthenticationButton(
            text = "Private key",
            selected = state.authentication == AuthenticationMethod.PRIVATE_KEY,
            onClick = { onAuthenticationChange(AuthenticationMethod.PRIVATE_KEY) },
            modifier = Modifier.weight(1f),
        )
    }
    if (state.authentication == AuthenticationMethod.PASSWORD) {
        OutlinedTextField(
            value = state.password,
            onValueChange = onPasswordChange,
            label = { Text("SSH-пароль") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onVerifyPassword,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Проверить и добавить")
        }
    } else {
        OutlinedTextField(
            value = state.privateKeyPassphrase,
            onValueChange = onPassphraseChange,
            label = { Text("Passphrase ключа (если есть)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth(),
        )
        Button(
            onClick = onChoosePrivateKey,
            modifier = Modifier.fillMaxWidth().height(52.dp),
        ) {
            Text("Выбрать private key и проверить")
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
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) { Text(text) }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) { Text(text) }
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
