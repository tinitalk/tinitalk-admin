package org.tinitalk.admin.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.tinitalk.admin.R
import org.tinitalk.admin.model.ServerRecord
import org.tinitalk.admin.model.displayTitle

@Composable
fun ServerListScreen(
    servers: List<ServerRecord>,
    snackbarHostState: SnackbarHostState,
    onAddServer: () -> Unit,
    onOpenServer: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        bottomBar = {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                OutlinedButton(
                    onClick = onAddServer,
                    shape = RoundedCornerShape(16.dp),
                    border = BorderStroke(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.55f),
                    ),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary,
                    ),
                    contentPadding = PaddingValues(vertical = 14.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) {
                    Text("Добавить сервер")
                }
            }
        },
        modifier = modifier,
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            AppHeader()
            if (servers.isEmpty()) {
                EmptyServerList(Modifier.weight(1f).fillMaxWidth())
            } else {
                LazyColumn(
                    contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    items(servers, key = ServerRecord::id) { server ->
                        ServerCard(server, onClick = { onOpenServer(server.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun AppHeader() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.fillMaxWidth().height(68.dp).padding(horizontal = 16.dp),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primary,
            shape = CircleShape,
            modifier = Modifier.size(42.dp),
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    text = "T",
                    color = MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
            }
        }
        Text(
            text = "TiniTalk Admin",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
private fun EmptyServerList(modifier: Modifier = Modifier) {
    Column(
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier.padding(24.dp),
    ) {
        Text(
            text = "Серверов пока нет",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Добавьте VPS и проверьте доступ по SSH",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
        )
    }
}

@Composable
private fun ServerCard(server: ServerRecord, onClick: () -> Unit) {
    Surface(
        onClick = onClick,
        color = MaterialTheme.colorScheme.surface,
        shape = RoundedCornerShape(22.dp),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.78f)),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 16.dp),
        ) {
            ServerRackIcon()
            Column(
                verticalArrangement = Arrangement.spacedBy(5.dp),
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    text = server.displayTitle,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                if (server.displayName.isNotEmpty()) {
                    Text(
                        text = server.enteredAddress,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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

@Composable
private fun ServerRackIcon() {
    val rackColor = MaterialTheme.colorScheme.onPrimaryContainer
    val rackFill = rackColor.copy(alpha = 0.12f)
    val indicatorColor = MaterialTheme.colorScheme.primary

    Surface(
        color = MaterialTheme.colorScheme.primaryContainer,
        shape = RoundedCornerShape(15.dp),
        modifier = Modifier.size(52.dp),
    ) {
        Canvas(Modifier.fillMaxSize().padding(10.dp)) {
            val strokeWidth = 1.5.dp.toPx()
            val gap = 5.dp.toPx()
            val unitHeight = (size.height - gap) / 2f
            val cornerRadius = CornerRadius(4.dp.toPx())

            repeat(2) { index ->
                val top = index * (unitHeight + gap)
                val unitSize = Size(size.width, unitHeight)
                val unitOffset = Offset(0f, top)
                val centerY = top + unitHeight / 2f

                drawRoundRect(
                    color = rackFill,
                    topLeft = unitOffset,
                    size = unitSize,
                    cornerRadius = cornerRadius,
                )
                drawRoundRect(
                    color = rackColor,
                    topLeft = unitOffset,
                    size = unitSize,
                    cornerRadius = cornerRadius,
                    style = Stroke(strokeWidth),
                )
                drawCircle(
                    color = indicatorColor,
                    radius = 2.dp.toPx(),
                    center = Offset(6.dp.toPx(), centerY),
                )
                drawLine(
                    color = rackColor,
                    start = Offset(12.dp.toPx(), centerY),
                    end = Offset(size.width - 5.dp.toPx(), centerY),
                    strokeWidth = strokeWidth,
                    cap = StrokeCap.Round,
                )
            }
        }
    }
}
