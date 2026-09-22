package org.tinitalk.admin.ui

import org.tinitalk.admin.i18n.appString

import android.content.ClipboardManager
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import org.tinitalk.admin.R

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
    val context = LocalContext.current
    val clipboardManager = remember(context) { context.getSystemService(ClipboardManager::class.java) }
    val clipboardText: () -> String? = {
        clipboardManager.primaryClip
            ?.takeIf { it.itemCount > 0 }
            ?.getItemAt(0)
            ?.coerceToText(context)
            ?.toString()
    }
    Column(
        verticalArrangement = Arrangement.spacedBy(12.dp),
        modifier = modifier
            .background(MaterialTheme.colorScheme.background)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        ScreenHeader(
            title = appString(R.string.text_add_server_0),
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
            onPasteAddress = {
                clipboardText()
                    ?.lineSequence()
                    ?.firstOrNull { it.isNotBlank() }
                    ?.trim()
                    ?.let(onAddressChange)
            },
        )
        CredentialsForm(
            state = state,
            enabled = formEnabled,
            onAuthenticationChange = onAuthenticationChange,
            onPasswordChange = onPasswordChange,
            onPassphraseChange = onPassphraseChange,
            onChoosePrivateKey = onChoosePrivateKey,
            onPastePassword = { clipboardText()?.let(onPasswordChange) },
            onPastePassphrase = { clipboardText()?.let(onPassphraseChange) },
        )

        state.errorMessage?.let { ErrorPanel(it.resolve()) }

        when (state.phase) {
            AddServerPhase.Form -> Button(
                onClick = onScanFingerprint,
                shape = MaterialTheme.shapes.large,
                modifier = Modifier.fillMaxWidth().height(52.dp),
            ) {
                Text(appString(R.string.text_add_1))
            }

            AddServerPhase.ScanningFingerprint -> ProgressRow(appString(R.string.text_getting_ssh_fingerprint_2))
            is AddServerPhase.ConfirmFingerprint -> Unit
            AddServerPhase.CheckingAccess -> ProgressRow(appString(R.string.text_setting_up_secure_ssh_access_3))
        }
        Spacer(Modifier.height(12.dp))
    }

    (state.phase as? AddServerPhase.ConfirmFingerprint)?.let { phase ->
        AlertDialog(
            onDismissRequest = onRejectFingerprint,
            title = { Text(appString(R.string.text_confirm_ssh_fingerprint_4)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(appString(R.string.text_compare_the_fingerprint_with_the_information_from_your_v_5))
                    Text(appString(R.string.text_algorithm_6), color = MaterialTheme.colorScheme.onSurfaceVariant)
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
                TextButton(onClick = onConfirmFingerprint) { Text(appString(R.string.text_confirm_7)) }
            },
            dismissButton = {
                TextButton(onClick = onRejectFingerprint) { Text(appString(R.string.text_cancel_8)) }
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
    onPasteAddress: () -> Unit,
) {
    OutlinedTextField(
        value = state.displayName,
        onValueChange = onDisplayNameChange,
        enabled = enabled,
        label = { Text(appString(R.string.text_name_optional_9)) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = state.address,
        onValueChange = onAddressChange,
        enabled = enabled,
        label = { Text(appString(R.string.text_ipv4_address_or_domain_10)) },
        trailingIcon = if (state.address.isEmpty()) {
            { PasteButton(enabled = enabled, onClick = onPasteAddress) }
        } else {
            null
        },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = state.sshPort,
            onValueChange = onPortChange,
            enabled = enabled,
            label = { Text(appString(R.string.text_ssh_port_11)) },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            singleLine = true,
            modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = state.sshLogin,
            onValueChange = onLoginChange,
            enabled = enabled,
            label = { Text(appString(R.string.text_ssh_username_12)) },
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
    onPastePassword: () -> Unit,
    onPastePassphrase: () -> Unit,
) {
    Text(
        text = appString(R.string.text_sign_in_method_13),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AuthenticationButton(
            text = appString(R.string.text_password_14),
            selected = state.authentication == AuthenticationMethod.PASSWORD,
            enabled = enabled,
            onClick = { onAuthenticationChange(AuthenticationMethod.PASSWORD) },
            modifier = Modifier.weight(1f),
        )
        AuthenticationButton(
            text = appString(R.string.private_key),
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
            label = { Text(appString(R.string.text_ssh_password_15)) },
            trailingIcon = if (state.password.isEmpty()) {
                { PasteButton(enabled = enabled, onClick = onPastePassword) }
            } else {
                null
            },
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
            label = { Text(appString(R.string.text_key_passphrase_if_any_16)) },
            trailingIcon = if (state.privateKeyPassphrase.isEmpty()) {
                { PasteButton(enabled = enabled, onClick = onPastePassphrase) }
            } else {
                null
            },
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
            Text(if (state.privateKeySelected) appString(R.string.text_choose_another_private_key_17) else appString(R.string.text_choose_private_key_18))
        }
        if (state.privateKeySelected) {
            Text(
                text = appString(R.string.text_private_key_selected_19),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            text = appString(R.string.text_the_key_file_is_used_once_and_is_not_stored_by_the_app_20),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

@Composable
private fun PasteButton(enabled: Boolean, onClick: () -> Unit) {
    IconButton(onClick = onClick, enabled = enabled) {
        Icon(
            painterResource(R.drawable.ic_paste),
            contentDescription = appString(R.string.text_paste_21),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
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
