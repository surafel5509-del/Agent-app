package com.nexa.ai.presentation.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.nexa.ai.presentation.theme.NexaGradientEnd
import com.nexa.ai.presentation.theme.NexaGradientStart

/**
 * Nexa mark: a rounded gradient tile with a crisp "N" stroke.
 * Drawn with Canvas so it stays sharp at any density and needs no bitmap asset.
 */
@Composable
fun NexaMark(
    modifier: Modifier = Modifier,
    size: Dp = 56.dp,
    cornerFraction: Float = 0.28f,
) {
    Box(
        modifier = modifier
            .size(size)
            .background(
                brush = Brush.linearGradient(listOf(NexaGradientStart, NexaGradientEnd)),
                shape = RoundedCornerShape(size * cornerFraction),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.fillMaxSize(0.56f)) {
            val strokeWidth = this.size.minDimension * 0.17f
            val inset = strokeWidth / 2f
            val left = inset
            val right = this.size.width - inset
            val top = inset
            val bottom = this.size.height - inset

            val path = Path().apply {
                moveTo(left, bottom)
                lineTo(left, top)
                lineTo(right, bottom)
                lineTo(right, top)
            }
            drawPath(
                path = path,
                color = Color.White,
                style = Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round,
                    join = StrokeJoin.Round,
                ),
            )
        }
    }
}

/** Brand lockup used on the login screen, the drawer header and About. */
@Composable
fun NexaWordmark(
    modifier: Modifier = Modifier,
    markSize: Dp = 44.dp,
    subtitle: String? = null,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        NexaMark(size = markSize)
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "Nexa",
                    style = MaterialTheme.typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    text = " AI",
                    style = MaterialTheme.typography.headlineMedium.copy(
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                    ),
                    color = MaterialTheme.colorScheme.primary,
                )
            }
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}
