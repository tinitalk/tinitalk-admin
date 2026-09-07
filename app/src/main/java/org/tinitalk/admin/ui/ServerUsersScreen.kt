package org.tinitalk.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.tinitalk.admin.R
import org.tinitalk.admin.server.ServerUser
import org.tinitalk.admin.ui.theme.BrandGold
import java.util.Locale

@Composable
fun ServerUsersScreen(
    state: ServerUsersUiState,
    onBack: () -> Unit,
    onRetry: () -> Unit,
    onAddUser: () -> Unit,
    onOpenUser: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            Box(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                var menuExpanded by remember { mutableStateOf(false) }
                ScreenHeader(
                    title = "Пользователи",
                    onBack = onBack,
                    actions = {
                        if (!state.loading && state.errorMessage == null && state.users.isNotEmpty()) {
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    MoreVertIcon()
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false },
                                    modifier = Modifier.widthIn(min = 260.dp),
                                ) {
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                text = "Добавить пользователя",
                                                style = MaterialTheme.typography.titleMedium,
                                                fontWeight = FontWeight.SemiBold,
                                            )
                                        },
                                        leadingIcon = {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_person_add),
                                                contentDescription = null,
                                                modifier = Modifier.size(24.dp),
                                            )
                                        },
                                        modifier = Modifier.heightIn(min = 58.dp),
                                        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 14.dp),
                                        onClick = {
                                            menuExpanded = false
                                            onAddUser()
                                        },
                                    )
                                }
                            }
                        }
                    },
                )
            }
            when {
                state.loading -> Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    CircularProgressIndicator()
                }

                state.errorMessage != null -> UsersMessage(
                    title = state.errorMessage,
                    actionLabel = "Повторить",
                    onAction = onRetry,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                state.users.isEmpty() -> UsersMessage(
                    title = "Пользователей пока нет",
                    actionLabel = "Добавить пользователя",
                    onAction = onAddUser,
                    modifier = Modifier.weight(1f).fillMaxWidth(),
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(state.users, key = ServerUser::login) { user ->
                        ServerUserRow(user, onClick = { onOpenUser(user.login) })
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersMessage(
    title: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onAction: () -> Unit = {},
) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(24.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        actionLabel?.let { label ->
            OutlinedButton(
                onClick = onAction,
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.padding(top = 20.dp),
            ) {
                Text(label)
            }
        }
    }
}

@Composable
private fun ServerUserRow(user: ServerUser, onClick: () -> Unit) {
    val name = user.displayName.trim().ifEmpty { user.login }
    val avatarColors = listOf(
        Color(0xFF394A67), Color(0xFF514464), Color(0xFF30514D),
        Color(0xFF60443B), Color(0xFF4E5337), Color(0xFF593F4C),
    )
    val colorIndex = Math.floorMod(
        user.login.trim().lowercase(Locale.ROOT).hashCode(),
        avatarColors.size,
    )
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(
            1.dp,
            MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f),
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 82.dp)
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Surface(
                shape = CircleShape,
                color = if (user.disabled) {
                    MaterialTheme.colorScheme.errorContainer
                } else {
                    avatarColors[colorIndex]
                },
                border = BorderStroke(1.dp, BrandGold.copy(alpha = 0.22f)),
                modifier = Modifier.size(52.dp),
            ) {
                Box(contentAlignment = Alignment.Center) {
                    if (user.disabled) {
                        ServerUserStatusIcon(
                            disabled = true,
                            modifier = Modifier.size(26.dp),
                        )
                    } else {
                        Text(
                            text = userInitial(name, user.login),
                            color = Color(0xFFF6E8C0),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = name,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = user.login,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_chevron_right),
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(23.dp),
                )
            }
        }
    }
}

private fun userInitial(displayName: String, login: String): String {
    val value = displayName.trim().ifEmpty { login.trim() }.ifEmpty { "?" }
    val end = value.offsetByCodePoints(0, 1)
    return value.substring(0, end).uppercase(Locale.getDefault())
}
