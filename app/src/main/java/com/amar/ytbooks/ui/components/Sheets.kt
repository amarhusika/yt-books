package com.amar.ytbooks.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.layout.heightIn
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import com.amar.ytbooks.data.Playlist
import com.amar.ytbooks.data.Video
import com.amar.ytbooks.data.YouTubeFetcher
import com.amar.ytbooks.util.rememberThumb
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material.icons.rounded.TouchApp
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.amar.ytbooks.util.formatTime
import com.amar.ytbooks.util.snapSpeed
import com.amar.ytbooks.util.speedLabel
import kotlinx.coroutines.delay
import kotlin.math.abs

private val SpeedPresets = listOf(0.75f, 1f, 1.25f, 1.5f, 1.75f, 2f)
private val SleepPresets = listOf(5, 10, 15, 20, 30, 45, 60, 90)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SpeedSheet(speed: Float, rates: List<Float>, onSpeed: (Float) -> Unit, onDismiss: () -> Unit) {
    val presets = rates.filter { it in 0.5f..2f }.ifEmpty { SpeedPresets }
    val maxRate = (rates.maxOrNull() ?: 2f).coerceAtLeast(1f)
    val minRate = (rates.minOrNull() ?: 0.25f).coerceAtMost(1f)
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = cs.outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            SectionLabel("Playback speed")
            Spacer(Modifier.height(12.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                RoundIconButton(
                    Icons.Rounded.Remove, "Slower", 52.dp,
                    onClick = { onSpeed(snapSpeed(speed - 0.05f).coerceIn(minRate, maxRate)) },
                    borderColor = cs.outlineVariant
                )
                Text(
                    speedLabel(speed),
                    style = MaterialTheme.typography.displaySmall,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.weight(1f)
                )
                RoundIconButton(
                    Icons.Rounded.Add, "Faster", 52.dp,
                    onClick = { onSpeed(snapSpeed(speed + 0.05f).coerceIn(minRate, maxRate)) },
                    borderColor = cs.outlineVariant
                )
            }
            Spacer(Modifier.height(12.dp))
            Slider(
                value = speed.coerceIn(minRate, maxRate),
                onValueChange = { onSpeed(snapSpeed(it).coerceIn(minRate, maxRate)) },
                valueRange = minRate..maxRate,
                colors = SliderDefaults.colors(
                    thumbColor = cs.onSurface,
                    activeTrackColor = cs.onSurface,
                    inactiveTrackColor = cs.outlineVariant,
                    activeTickColor = Color.Transparent,
                    inactiveTickColor = Color.Transparent,
                )
            )
            Row(Modifier.fillMaxWidth()) {
                Text(speedLabel(minRate), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
                Spacer(Modifier.weight(1f))
                Text(speedLabel(maxRate), style = MaterialTheme.typography.labelMedium, color = cs.onSurfaceVariant)
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                presets.forEach { p ->
                    Pill(
                        text = speedLabel(p),
                        selected = abs(speed - p) < 0.01f,
                        onClick = { onSpeed(p) }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun SleepSheet(
    endsAt: Long?,
    onStart: (Int) -> Unit,
    onExtend: (Int) -> Unit,
    onCancel: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(endsAt) {
        while (endsAt != null) {
            now = System.currentTimeMillis()
            delay(1_000)
        }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = cs.outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            SectionLabel("Sleep timer")
            Spacer(Modifier.height(12.dp))
            if (endsAt != null) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Bedtime, null, modifier = Modifier.size(28.dp))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            formatTime(endsAt - now),
                            style = MaterialTheme.typography.displaySmall
                        )
                        Text(
                            "until pause",
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Pill("+5 min", onClick = { onExtend(5) }, icon = Icons.Rounded.Add)
                    Pill("+15 min", onClick = { onExtend(15) }, icon = Icons.Rounded.Add)
                    Pill("Turn off", onClick = onCancel, icon = Icons.Rounded.Close, selected = true)
                }
            } else {
                Text(
                    "Playback will fade out gently and pause.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurfaceVariant
                )
                Spacer(Modifier.height(16.dp))
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SleepPresets.forEach { m ->
                        Pill(text = "$m min", onClick = { onStart(m) })
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddSheet(importing: Boolean, message: String?, onAdd: (String) -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val clipboard = LocalClipboardManager.current
    var text by remember { mutableStateOf("") }
    // Pre-fill with a YouTube link from the clipboard
    LaunchedEffect(Unit) {
        val clip = runCatching { clipboard.getText()?.text }.getOrNull().orEmpty()
        if (text.isEmpty() && YouTubeFetcher.parse(clip) != null && clip.contains("youtu")) text = clip.trim()
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = cs.outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
                .imePadding()
        ) {
            SectionLabel("Add to library")
            Spacer(Modifier.height(6.dp))
            Text("YouTube playlist", style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(
                "Paste a playlist link – every playlist becomes a book. A single video link works too.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(18.dp))
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://youtube.com/playlist?list=…") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                leadingIcon = { Icon(Icons.Rounded.Link, null) },
                trailingIcon = {
                    if (text.isNotEmpty()) {
                        IconButton(onClick = { text = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                    } else {
                        IconButton(onClick = {
                            text = runCatching { clipboard.getText()?.text }.getOrNull().orEmpty().trim()
                        }) { Icon(Icons.Rounded.ContentPaste, "Paste") }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { if (text.isNotBlank() && !importing) onAdd(text) }),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = cs.onSurface,
                    unfocusedBorderColor = cs.outline,
                    cursorColor = cs.onSurface,
                    focusedLeadingIconColor = cs.onSurface,
                ),
            )
            Spacer(Modifier.height(16.dp))
            Surface(
                onClick = { if (text.isNotBlank() && !importing) onAdd(text) },
                enabled = text.isNotBlank() && !importing,
                shape = RoundedCornerShape(18.dp),
                color = if (text.isNotBlank()) cs.primary else cs.surfaceContainerHighest,
                contentColor = if (text.isNotBlank()) cs.onPrimary else cs.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    Modifier.padding(vertical = 16.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (importing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = LocalContentColor.current
                        )
                        Spacer(Modifier.width(12.dp))
                        Text("Reading playlist…", style = MaterialTheme.typography.titleMedium)
                    } else {
                        Icon(Icons.Rounded.Add, null)
                        Spacer(Modifier.width(10.dp))
                        Text("Add playlist", style = MaterialTheme.typography.titleMedium)
                    }
                }
            }
            if (message != null && !importing) {
                Spacer(Modifier.height(12.dp))
                Text(
                    message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = cs.onSurface,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            Spacer(Modifier.height(14.dp))
            Text(
                "Tip: in the YouTube app tap Share → YT Books.",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
        }
    }
}

/** List of all videos in the current playlist – tap one to play it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideosSheet(
    playlist: Playlist,
    currentId: String?,
    isPlaying: Boolean,
    onPick: (Video) -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val startIndex = playlist.indexOf(currentId).coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (startIndex - 2).coerceAtLeast(0))
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = cs.outline) },
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding()) {
            Column(Modifier.padding(horizontal = 24.dp)) {
                SectionLabel("${playlist.videoCount} videos")
                Text(
                    playlist.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Spacer(Modifier.height(12.dp))
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxWidth().heightIn(max = 560.dp)
            ) {
                itemsIndexed(playlist.videos, key = { _, v -> v.id }) { i, v ->
                    VideoRow(
                        index = i + 1,
                        video = v,
                        isCurrent = v.id == currentId,
                        isPlaying = isPlaying && v.id == currentId,
                        onClick = { onPick(v) },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
fun VideoRow(
    index: Int,
    video: Video,
    isCurrent: Boolean,
    isPlaying: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    trailing: (@Composable () -> Unit)? = null,
) {
    val cs = MaterialTheme.colorScheme
    Row(
        modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(if (isCurrent) cs.surfaceContainerHigh else Color.Transparent)
            .padding(start = 20.dp, end = if (trailing != null) 4.dp else 20.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 16:9 thumbnail with the index
        Box(
            Modifier
                .width(96.dp)
                .height(54.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(cs.surfaceContainerHighest)
        ) {
            val thumb = rememberThumb(video.videoId)
            if (thumb != null) {
                Image(
                    bitmap = thumb,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            if (isPlaying) {
                Box(
                    Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.5f)),
                    contentAlignment = Alignment.Center
                ) {
                    EqualizerBars(color = Color.White, modifier = Modifier.size(18.dp))
                }
            } else {
                Box(
                    Modifier
                        .align(Alignment.TopStart)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(if (isCurrent) cs.onSurface else Color.Black.copy(alpha = 0.62f))
                        .padding(horizontal = 6.dp, vertical = 1.dp)
                ) {
                    Text(
                        "$index",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isCurrent) cs.surface else Color.White
                    )
                }
            }
            if (video.durationMs > 0) {
                Box(
                    Modifier
                        .align(Alignment.BottomEnd)
                        .padding(5.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(Color.Black.copy(alpha = 0.72f))
                        .padding(horizontal = 5.dp, vertical = 1.dp)
                ) {
                    Text(formatTime(video.durationMs), fontSize = 10.sp, fontWeight = FontWeight.SemiBold, color = Color.White)
                }
            }
            if (video.inProgress) {
                ProgressLine(
                    video.progress,
                    modifier = Modifier.align(Alignment.BottomStart),
                    color = Color.White,
                    track = Color.White.copy(alpha = 0.25f),
                    height = 3.dp
                )
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                video.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isCurrent) FontWeight.SemiBold else FontWeight.Normal,
                color = if (video.finished && !isCurrent) cs.onSurfaceVariant else cs.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            val meta = listOfNotNull(
                video.channel.takeIf { it.isNotBlank() },
                when {
                    video.finished -> "Watched"
                    video.inProgress && video.durationMs > 0 -> "${(video.progress * 100).toInt()}%"
                    else -> null
                },
            ).joinToString(" · ")
            if (meta.isNotEmpty()) {
                Text(
                    meta,
                    style = MaterialTheme.typography.bodySmall,
                    color = cs.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        if (video.finished) {
            Spacer(Modifier.width(10.dp))
            Icon(Icons.Rounded.CheckCircle, "Watched", tint = cs.onSurfaceVariant, modifier = Modifier.size(18.dp))
        }
        if (trailing != null) trailing()
    }
}

// ---------------------------------------------------------------- video quality

/** YouTube quality names → labels. "default" = YouTube decides (Auto). */
val QualityOptions = listOf(
    "default" to "Auto",
    "hd1080" to "1080p",
    "hd720" to "720p",
    "large" to "480p",
    "medium" to "360p",
    "small" to "240p",
    "tiny" to "144p",
)

fun qualityLabel(q: String): String = when (q) {
    "", "default", "auto", "unknown" -> "Auto"
    "highres" -> "4K"
    "hd2160" -> "2160p"
    "hd1440" -> "1440p"
    else -> QualityOptions.firstOrNull { it.first == q }?.second ?: q
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QualitySheet(
    selected: String,
    actual: String,
    onSelect: (String) -> Unit,
    onYouTubeMenu: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = cs.outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 24.dp)
                .navigationBarsPadding()
        ) {
            SectionLabel("Video quality")
            Spacer(Modifier.height(6.dp))
            Text(
                if (actual.isNotBlank()) "Playing now: ${qualityLabel(actual)}" else "Auto lets YouTube pick by your connection.",
                style = MaterialTheme.typography.bodyMedium,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(16.dp))
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                QualityOptions.forEach { (key, label) ->
                    Pill(text = label, selected = selected == key, onClick = { onSelect(key) })
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                "YouTube treats this as a wish. If the picture doesn't change, use YouTube's own menu (⚙).",
                style = MaterialTheme.typography.bodySmall,
                color = cs.onSurfaceVariant
            )
            Spacer(Modifier.height(10.dp))
            Pill("Open YouTube's ⚙ menu", onClick = onYouTubeMenu, icon = Icons.Rounded.TouchApp)
        }
    }
}

// ---------------------------------------------------------------- remove

@Composable
fun ConfirmRemoveDialog(title: String, onConfirm: () -> Unit, onDismiss: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = { Icon(Icons.Rounded.DeleteOutline, null) },
        title = { Text("Remove from library?") },
        text = {
            Text(
                "“$title” and its saved progress will be removed from YT Books. The playlist on YouTube is not touched.",
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onConfirm) { Text("Remove", color = cs.error) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel", color = cs.onSurface) }
        },
        containerColor = cs.surface,
    )
}

/** Long-press on a book in the library. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BookOptionsSheet(
    playlist: Playlist,
    onRefresh: (() -> Unit)?,
    onReset: () -> Unit,
    onRemove: () -> Unit,
    onDismiss: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = cs.surface,
        dragHandle = { BottomSheetDefaults.DragHandle(color = cs.outline) },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
                .navigationBarsPadding()
        ) {
            Text(
                playlist.title,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(Modifier.height(8.dp))
            if (onRefresh != null) SheetAction(Icons.Rounded.Refresh, "Check for new videos", onClick = onRefresh)
            SheetAction(Icons.Rounded.Replay, "Start book over", onClick = onReset)
            SheetAction(Icons.Rounded.DeleteOutline, "Remove from library", tint = cs.error, onClick = onRemove)
        }
    }
}

@Composable
private fun SheetAction(icon: ImageVector, text: String, tint: Color = Color.Unspecified, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val c = if (tint == Color.Unspecified) cs.onSurface else tint
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, null, tint = c)
        Spacer(Modifier.width(16.dp))
        Text(text, style = MaterialTheme.typography.bodyLarge, color = c)
    }
}
