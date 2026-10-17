package com.amar.ytbooks.ui.components

import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.FastForward
import androidx.compose.material.icons.rounded.FastRewind
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.amar.ytbooks.util.rememberThumb
import com.amar.ytbooks.util.formatTime

// ---------------------------------------------------------------- tiles

/** Square (or any size) YouTube thumbnail with an equalizer overlay while playing. */
@Composable
fun VideoTile(
    videoId: String?,
    size: Dp,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(size * 0.26f)
    val thumb = rememberThumb(videoId)
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(cs.surfaceContainerHighest),
        contentAlignment = Alignment.Center
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            Icon(
                Icons.Rounded.SmartDisplay, null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier.size(size * 0.42f)
            )
        }
        if (playing) {
            Box(
                Modifier
                    .matchParentSize()
                    .background(Color.Black.copy(alpha = 0.5f)),
                contentAlignment = Alignment.Center
            ) {
                EqualizerBars(color = Color.White, modifier = Modifier.size(size * 0.38f))
            }
        }
    }
}

@Composable
fun EqualizerBars(color: Color, modifier: Modifier = Modifier) {
    val t = rememberInfiniteTransition(label = "eq")
    val a by t.animateFloat(0.25f, 1f, infiniteRepeatable(tween(420), RepeatMode.Reverse), label = "a")
    val b by t.animateFloat(0.9f, 0.3f, infiniteRepeatable(tween(560), RepeatMode.Reverse), label = "b")
    val c by t.animateFloat(0.4f, 0.95f, infiniteRepeatable(tween(360), RepeatMode.Reverse), label = "c")
    Canvas(modifier) {
        val bw = size.width / 5f
        listOf(a, b, c).forEachIndexed { i, h ->
            val bh = size.height * h
            drawRoundRect(
                color = color,
                topLeft = Offset(bw * (i * 2f), size.height - bh),
                size = Size(bw, bh),
                cornerRadius = CornerRadius(bw / 2f, bw / 2f)
            )
        }
    }
}

// ---------------------------------------------------------------- progress

@Composable
fun ProgressLine(
    progress: Float,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface,
    track: Color = MaterialTheme.colorScheme.outlineVariant,
    height: Dp = 4.dp,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(height)
            .clip(CircleShape)
            .background(track)
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .fillMaxWidth(progress.coerceIn(0f, 1f))
                .clip(CircleShape)
                .background(color)
        )
    }
}

@Composable
fun SeekBar(
    positionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onBackground,
    track: Color = MaterialTheme.colorScheme.outlineVariant,
    labelColor: Color = MaterialTheme.colorScheme.onSurfaceVariant,
    showLabels: Boolean = true,
) {
    var dragging by remember { mutableStateOf<Float?>(null) }
    val fraction = dragging ?: if (durationMs > 0) positionMs.toFloat() / durationMs else 0f
    val shownPos = dragging?.let { (it * durationMs).toLong() } ?: positionMs
    Column(modifier) {
        Slider(
            value = fraction.coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let { onSeek((it * durationMs).toLong()) }
                dragging = null
            },
            enabled = durationMs > 0,
            colors = SliderDefaults.colors(
                thumbColor = color,
                activeTrackColor = color,
                inactiveTrackColor = track,
                disabledThumbColor = track,
                disabledActiveTrackColor = track,
                disabledInactiveTrackColor = track,
            ),
        )
        if (showLabels) {
            Row(Modifier.fillMaxWidth()) {
                Text(
                    formatTime(shownPos),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "−" + formatTime((durationMs - shownPos).coerceAtLeast(0L)),
                    style = MaterialTheme.typography.labelMedium,
                    color = labelColor
                )
            }
        }
    }
}

// ---------------------------------------------------------------- buttons

@Composable
fun PlayButton(
    playing: Boolean,
    buffering: Boolean,
    size: Dp,
    onClick: () -> Unit,
    container: Color = MaterialTheme.colorScheme.onBackground,
    content: Color = MaterialTheme.colorScheme.background,
) {
    val scale by animateFloatAsState(if (playing) 1f else 0.94f, label = "playScale")
    Box(
        Modifier
            .size(size)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(CircleShape)
            .background(container)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Crossfade(targetState = playing, label = "pp") { p ->
            Icon(
                if (p) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                contentDescription = if (p) "Pause" else "Play",
                tint = content,
                modifier = Modifier.size(size * 0.46f)
            )
        }
        if (buffering) {
            CircularProgressIndicator(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(3.dp),
                color = content.copy(alpha = 0.45f),
                strokeWidth = 2.dp
            )
        }
    }
}

@Composable
fun RoundIconButton(
    icon: ImageVector,
    contentDescription: String?,
    size: Dp,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    background: Color = Color.Transparent,
    borderColor: Color? = null,
    iconScale: Float = 0.5f,
) {
    Box(
        modifier
            .size(size)
            .clip(CircleShape)
            .background(background)
            .then(if (borderColor != null) Modifier.border(1.dp, borderColor, CircleShape) else Modifier)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription, tint = tint, modifier = Modifier.size(size * iconScale))
    }
}

/** "1 min" forward/back button. */
@Composable
fun SkipMinuteButton(
    forward: Boolean,
    size: Dp,
    onClick: () -> Unit,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    borderColor: Color = MaterialTheme.colorScheme.outlineVariant,
) {
    Box(
        Modifier
            .size(size)
            .clip(CircleShape)
            .border(1.dp, borderColor, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(
                if (forward) Icons.Rounded.FastForward else Icons.Rounded.FastRewind,
                contentDescription = if (forward) "Forward 1 minute" else "Back 1 minute",
                tint = tint,
                modifier = Modifier.size(size * 0.36f)
            )
            Text(
                "1 min",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = tint,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    selected: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = CircleShape,
        color = if (selected) cs.primary else cs.surfaceContainerHigh,
        contentColor = if (selected) cs.onPrimary else cs.onSurface,
        border = if (selected) null else BorderStroke(1.dp, cs.outlineVariant),
    ) {
        Row(
            Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            if (icon != null) {
                Icon(icon, null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
            }
            Text(text, style = MaterialTheme.typography.labelLarge)
        }
    }
}

@Composable
fun Tag(text: String, modifier: Modifier = Modifier) {
    val cs = MaterialTheme.colorScheme
    Box(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, cs.outline, RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelSmall,
            color = cs.onSurfaceVariant
        )
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(
        text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
