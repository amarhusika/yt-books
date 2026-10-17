package com.amar.ytbooks.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.amar.ytbooks.ui.components.BookOptionsSheet
import com.amar.ytbooks.ui.components.ConfirmRemoveDialog
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.PlaylistPlay
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Replay10
import androidx.compose.material.icons.rounded.SmartDisplay
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amar.ytbooks.data.Playlist
import com.amar.ytbooks.data.Video
import com.amar.ytbooks.ui.PlayerUi
import com.amar.ytbooks.ui.components.EqualizerBars
import com.amar.ytbooks.ui.components.ProgressLine
import com.amar.ytbooks.ui.components.SectionLabel
import com.amar.ytbooks.ui.components.VideoTile
import com.amar.ytbooks.util.formatDurationShort
import com.amar.ytbooks.util.formatTime
import com.amar.ytbooks.util.rememberThumb
import kotlin.math.roundToInt

class LibraryActions(
    val onOpenBook: (Playlist) -> Unit,
    val onResume: (Playlist) -> Unit,
    val onTogglePlay: () -> Unit,
    val onSeekBy: (Long) -> Unit,
    val onOpenPlayer: () -> Unit,
    val onAdd: () -> Unit,
    val onRefresh: (Playlist) -> Unit,
    val onReset: (Playlist) -> Unit,
    val onRemove: (Playlist) -> Unit,
)

/** Short description of a book: "24 videos · 6 h 20 min · 35%". */
fun bookMeta(p: Playlist): String = listOfNotNull(
    if (p.isSingle) "Video" else if (p.videoCount == 1) "1 video" else "${p.videoCount} videos",
    if (p.durationMs > 0) formatDurationShort(p.durationMs) else null,
    when {
        p.finished -> "Finished"
        p.started && p.durationMs > 0 -> "${(p.progress * 100).roundToInt()}%"
        else -> null
    },
).joinToString(" · ")

@Composable
fun LibraryScreen(
    playlists: List<Playlist>,
    nowVideo: Video?,
    nowPlaylist: Playlist?,
    ui: PlayerUi,
    importing: Boolean,
    actions: LibraryActions,
) {
    val cs = MaterialTheme.colorScheme
    var optionsFor by remember { mutableStateOf<Playlist?>(null) }
    var confirmFor by remember { mutableStateOf<Playlist?>(null) }

    val resume = remember(playlists) {
        playlists.filter { it.started && !it.finished }.maxByOrNull { it.lastPlayedAt }
    }
    val videoCount = remember(playlists) { playlists.sumOf { it.videoCount } }

    Scaffold(
        containerColor = cs.background,
        floatingActionButton = {
            if (playlists.isNotEmpty()) {
                ExtendedFloatingActionButton(
                    onClick = actions.onAdd,
                    icon = { Icon(Icons.Rounded.Add, null) },
                    text = { Text("Add") },
                    containerColor = cs.primary,
                    contentColor = cs.onPrimary,
                    shape = RoundedCornerShape(20.dp),
                )
            }
        },
        bottomBar = {
            if (nowVideo != null) {
                MiniPlayer(
                    video = nowVideo,
                    playlist = nowPlaylist,
                    ui = ui,
                    onTogglePlay = actions.onTogglePlay,
                    onBack10 = { actions.onSeekBy(-10_000) },
                    onOpen = actions.onOpenPlayer
                )
            }
        }
    ) { padding ->
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 156.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 96.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }, key = "header") {
                Column {
                    SectionLabel("Library")
                    Text("YT Books", style = MaterialTheme.typography.headlineLarge)
                    if (playlists.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            (if (playlists.size == 1) "1 book" else "${playlists.size} books") +
                                "  ·  " + (if (videoCount == 1) "1 video" else "$videoCount videos"),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                    }
                    AnimatedVisibility(visible = importing) {
                        Column(Modifier.padding(top = 12.dp)) {
                            LinearProgressIndicator(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(CircleShape),
                                color = cs.onSurface,
                                trackColor = cs.outlineVariant
                            )
                            Spacer(Modifier.height(6.dp))
                            Text("Reading playlist from YouTube…", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                        }
                    }
                }
            }

            if (playlists.isEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "empty") {
                    EmptyLibrary(actions.onAdd)
                }
                return@LazyVerticalGrid
            }

            if (resume != null) {
                item(span = { GridItemSpan(maxLineSpan) }, key = "resume") {
                    ContinueCard(
                        playlist = resume,
                        nowVideo = nowVideo,
                        ui = ui,
                        onClick = {
                            if (nowPlaylist?.key == resume.key) actions.onOpenPlayer() else actions.onResume(resume)
                        }
                    )
                }
            }

            item(span = { GridItemSpan(maxLineSpan) }, key = "label") {
                SectionLabel("All books", Modifier.padding(top = 4.dp))
            }

            items(playlists, key = { it.key }) { p ->
                val isCurrent = nowPlaylist?.key == p.key
                BookCard(
                    playlist = p,
                    isCurrent = isCurrent,
                    playing = isCurrent && ui.playWhenReady && !ui.ended,
                    onOpen = { actions.onOpenBook(p) },
                    onLongPress = { optionsFor = p },
                    onPlay = { if (isCurrent) actions.onTogglePlay() else actions.onResume(p) },
                )
            }
        }
    }

    optionsFor?.let { p ->
        BookOptionsSheet(
            playlist = p,
            onRefresh = if (p.isSingle) null else ({
                optionsFor = null
                actions.onRefresh(p)
            }),
            onReset = {
                optionsFor = null
                actions.onReset(p)
            },
            onRemove = {
                optionsFor = null
                confirmFor = p
            },
            onDismiss = { optionsFor = null }
        )
    }
    confirmFor?.let { p ->
        ConfirmRemoveDialog(
            title = p.title,
            onConfirm = {
                confirmFor = null
                actions.onRemove(p)
            },
            onDismiss = { confirmFor = null }
        )
    }
}

/** 16:9 cover: thumbnail of the video you're on (or the first one). */
@Composable
fun BookCover(
    playlist: Playlist,
    modifier: Modifier = Modifier,
    playing: Boolean = false,
    compact: Boolean = false,
) {
    val cs = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(if (compact) 18.dp else 22.dp)
    val thumb = rememberThumb(playlist.coverVideoId)
    Box(
        modifier
            .aspectRatio(16f / 9f)
            .clip(shape)
            .background(cs.surfaceContainerHighest)
            .border(1.dp, cs.outlineVariant, shape)
    ) {
        if (thumb != null) {
            Image(
                bitmap = thumb,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.55f))))
            )
        } else {
            Icon(
                Icons.Rounded.SmartDisplay, null,
                tint = cs.onSurfaceVariant,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(if (compact) 28.dp else 36.dp)
            )
        }
        if (!compact && !playlist.isSingle && !playlist.finished && !playing) {
            Row(
                Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.6f))
                    .padding(horizontal = 6.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    Icons.AutoMirrored.Rounded.PlaylistPlay, null,
                    tint = Color.White,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(3.dp))
                Text("${playlist.videoCount}", style = MaterialTheme.typography.labelSmall, color = Color.White)
            }
        }
        if (playing) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(26.dp)
                    .clip(CircleShape)
                    .background(Color.Black.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                EqualizerBars(color = Color.White, modifier = Modifier.size(13.dp))
            }
        }
        if (playlist.finished && !playing) {
            Icon(
                Icons.Rounded.CheckCircle, "Finished",
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(8.dp)
                    .size(20.dp)
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BookCard(
    playlist: Playlist,
    isCurrent: Boolean,
    playing: Boolean,
    onOpen: () -> Unit,
    onLongPress: () -> Unit,
    onPlay: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    Column(
        Modifier
            .clip(RoundedCornerShape(22.dp))
            .combinedClickable(onClick = onOpen, onLongClick = onLongPress)
    ) {
        Box {
            BookCover(playlist, Modifier.fillMaxWidth(), playing = playing)
            if (playlist.started && !playlist.finished) {
                ProgressLine(
                    playlist.progress,
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(start = 12.dp, end = 56.dp, bottom = 16.dp),
                    color = Color.White,
                    track = Color.White.copy(alpha = 0.3f),
                    height = 3.dp
                )
            }
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .padding(8.dp)
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isCurrent) cs.onSurface else cs.surface)
                    .border(1.dp, cs.outlineVariant, CircleShape)
                    .clickable(onClick = onPlay),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                    if (playing) "Pause" else "Play",
                    tint = if (isCurrent) cs.surface else cs.onSurface,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            playlist.title,
            style = MaterialTheme.typography.titleSmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
        Text(
            bookMeta(playlist),
            style = MaterialTheme.typography.bodySmall,
            color = cs.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
        )
    }
}

@Composable
private fun ContinueCard(playlist: Playlist, nowVideo: Video?, ui: PlayerUi, onClick: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    val video = playlist.resumeVideo()
    val isCurrent = video != null && nowVideo?.id == video.id
    val content = cs.inverseOnSurface
    val playing = isCurrent && ui.playWhenReady && !ui.ended
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(28.dp),
        color = cs.inverseSurface,
        contentColor = content,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Column(Modifier.padding(22.dp)) {
            Text(
                "CONTINUE WHERE YOU LEFT OFF",
                style = MaterialTheme.typography.labelSmall,
                color = content.copy(alpha = 0.6f),
            )
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.title,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (video != null && playlist.videoCount > 1) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "${playlist.indexOf(video.id) + 1}/${playlist.videoCount} · ${video.title}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = content.copy(alpha = 0.65f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
                Spacer(Modifier.width(16.dp))
                Box(
                    Modifier
                        .size(56.dp)
                        .clip(CircleShape)
                        .background(content),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = cs.inverseSurface,
                        modifier = Modifier.size(30.dp)
                    )
                }
            }
            Spacer(Modifier.height(18.dp))
            ProgressLine(playlist.progress, color = content, track = content.copy(alpha = 0.18f))
            Spacer(Modifier.height(8.dp))
            Row {
                Text(
                    if (playlist.durationMs > 0) "${formatDurationShort(playlist.durationMs - playlist.positionMs)} left" else "",
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.6f)
                )
                Spacer(Modifier.weight(1f))
                Text(
                    "${(playlist.progress * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = content.copy(alpha = 0.6f)
                )
            }
        }
    }
}

@Composable
fun MiniPlayer(
    video: Video,
    playlist: Playlist?,
    ui: PlayerUi,
    onTogglePlay: () -> Unit,
    onBack10: () -> Unit,
    onOpen: () -> Unit,
) {
    val cs = MaterialTheme.colorScheme
    val isThis = ui.mediaId == video.id
    val pos = if (isThis) ui.positionMs else video.positionMs
    val dur = if (isThis && ui.durationMs > 0) ui.durationMs else video.durationMs
    val progress = if (dur > 0) (pos.toFloat() / dur).coerceIn(0f, 1f) else 0f
    val content = cs.inverseOnSurface
    Box(
        Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            color = cs.inverseSurface,
            contentColor = content,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Row(
                    Modifier
                        .clickable(onClick = onOpen)
                        .padding(start = 10.dp, end = 8.dp, top = 10.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    VideoTile(video.videoId, 44.dp, playing = isThis && ui.isPlaying)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            video.title,
                            style = MaterialTheme.typography.titleSmall,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (playlist != null && playlist.videoCount > 1) "${playlist.title} · ${formatTime(pos)}"
                            else "${formatTime(pos)} / ${formatTime(dur)}",
                            style = MaterialTheme.typography.bodySmall,
                            color = content.copy(alpha = 0.6f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = onBack10) {
                        Icon(Icons.Rounded.Replay10, "Back 10 seconds", tint = content)
                    }
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(content)
                            .clickable(onClick = onTogglePlay),
                        contentAlignment = Alignment.Center
                    ) {
                        val playing = isThis && ui.playWhenReady && !ui.ended
                        Icon(
                            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                            if (playing) "Pause" else "Play",
                            tint = cs.inverseSurface
                        )
                    }
                }
                ProgressLine(
                    progress,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 10.dp),
                    color = content,
                    track = content.copy(alpha = 0.15f),
                    height = 3.dp
                )
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onAdd: () -> Unit) {
    val cs = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 72.dp, bottom = 24.dp)
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(cs.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.AutoMirrored.Rounded.PlaylistPlay, null, tint = cs.onSurface, modifier = Modifier.size(56.dp))
        }
        Spacer(Modifier.height(28.dp))
        Text("Your library is empty", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(8.dp))
        Text(
            "Paste a YouTube playlist link.\nEvery playlist becomes a book, and YT Books remembers where you stopped.\nLong-press a book for more options.",
            style = MaterialTheme.typography.bodyMedium,
            color = cs.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(28.dp))
        Button(
            onClick = onAdd,
            colors = ButtonDefaults.buttonColors(containerColor = cs.primary, contentColor = cs.onPrimary),
            shape = RoundedCornerShape(18.dp),
            contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)
        ) {
            Icon(Icons.Rounded.Add, null)
            Spacer(Modifier.width(10.dp))
            Text("Add playlist")
        }
    }
}
