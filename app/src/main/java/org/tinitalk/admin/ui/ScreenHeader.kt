package org.tinitalk.admin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import org.tinitalk.admin.R

@Composable
fun ScreenHeader(
    title: String,
    onBack: () -> Unit,
    backEnabled: Boolean = true,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        IconButton(onClick = onBack, enabled = backEnabled) {
            BackArrowIcon()
        }
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
    }
}

@Composable
fun EditIconButton(onClick: () -> Unit) {
    IconButton(onClick = onClick) {
        Icon(
            painter = painterResource(R.drawable.ic_edit),
            contentDescription = "Изменить название",
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(22.dp),
        )
    }
}

@Composable
private fun BackArrowIcon() {
    val color = MaterialTheme.colorScheme.onSurface
    Canvas(
        modifier = Modifier
            .size(24.dp)
            .semantics { contentDescription = "Назад" },
    ) {
        val path = Path().apply {
            moveTo(size.width * 0.83f, size.height * 0.5f)
            lineTo(size.width * 0.22f, size.height * 0.5f)
            moveTo(size.width * 0.22f, size.height * 0.5f)
            lineTo(size.width * 0.5f, size.height * 0.22f)
            moveTo(size.width * 0.22f, size.height * 0.5f)
            lineTo(size.width * 0.5f, size.height * 0.78f)
        }
        drawPath(
            path = path,
            color = color,
            style = Stroke(
                width = 2.2.dp.toPx(),
                cap = StrokeCap.Round,
                join = StrokeJoin.Round,
            ),
        )
    }
}
