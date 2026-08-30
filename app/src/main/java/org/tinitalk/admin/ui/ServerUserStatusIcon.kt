package org.tinitalk.admin.ui

import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import org.tinitalk.admin.ui.theme.AccessVerifiedGreen

@Composable
fun ServerUserStatusIcon(disabled: Boolean, modifier: Modifier = Modifier) {
    val color = if (disabled) MaterialTheme.colorScheme.error else AccessVerifiedGreen
    Canvas(
        modifier = modifier.semantics {
            contentDescription = if (disabled) "Пользователь заблокирован" else "Пользователь включён"
        },
    ) {
        val strokeWidth = 2.dp.toPx()
        if (disabled) {
            drawArc(
                color = color,
                startAngle = 180f,
                sweepAngle = 180f,
                useCenter = false,
                topLeft = Offset(size.width * 0.3f, size.height * 0.12f),
                size = Size(size.width * 0.4f, size.height * 0.48f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round),
            )
            drawRoundRect(
                color = color,
                topLeft = Offset(size.width * 0.2f, size.height * 0.43f),
                size = Size(size.width * 0.6f, size.height * 0.43f),
                cornerRadius = CornerRadius(size.width * 0.09f),
                style = Stroke(width = strokeWidth),
            )
        } else {
            drawCircle(
                color = color,
                radius = (size.minDimension - strokeWidth) / 2f,
                style = Stroke(width = strokeWidth),
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.27f, size.height * 0.52f),
                end = Offset(size.width * 0.44f, size.height * 0.68f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
            drawLine(
                color = color,
                start = Offset(size.width * 0.44f, size.height * 0.68f),
                end = Offset(size.width * 0.75f, size.height * 0.34f),
                strokeWidth = strokeWidth,
                cap = StrokeCap.Round,
            )
        }
    }
}
