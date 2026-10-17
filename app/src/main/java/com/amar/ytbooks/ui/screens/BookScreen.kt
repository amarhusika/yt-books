package com.amar.ytbooks.ui.screens

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Replay
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.amar.ytbooks.data.Playlist
import com.amar.ytbooks.data.Video
import com.amar.ytbooks.ui.PlayerUi
import com.amar.ytbooks.ui.components.ConfirmRemoveDialog
import com.amar.ytbooks.ui.components.ProgressLine
import com.amar.ytbooks.ui.components.SectionLabel
import com.amar.ytbooks.ui.components.VideoRow
import com.amar.ytbooks.util.formatDurationShort

class BookActions(
    val onBack: () -> Unit,
    val onPlayVideo: (Video) -> Unit,
    val onResume: (Playlist) -> Unit,
    val onTogglePlay: () -> Unit,
    val onSeekBy: (Long) -> Unit,
    val onOpenPlayer: () -> Unit,
    val onRefresh: (Playlist) -> Unit,
    val onOpenInYouTube: (String) -> Unit,
    val onRemoveBook: (Playlist) -> Unit,
    val onResetBook: (Playlist) -> Unit,
    val onMarkFinished: (Video, Boolean) -> Unit,
    val onResetVideo: (Video) -> Unit,
)

@Composable
fun BookScreen(
    playlist: Playlist,
    nowVideo: Video?,
    nowPlaylist: Playlist?,
    ui: PlayerUi,
    importing: Boolean,
    actions: BookActions,
) {
    val cs = MaterialTheme.colorScheme
    var menu by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf(false) }
    val isCurrentBook = nowPlaylist?.key == playlist.key
    val playing = isCurrentBook && ui.playWhenReady && !ui.ended
    val resumeVideo = if (isCurrentBook && nowVideo != null) nowVideo else playlist.resumeVideo()

    Scaffold(
        containerColor = cs.background,
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
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.TopCenter) {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 820.dp),
                contentPadding = PaddingValues(
                    top = padding.calculateTopPadding(),
                    bottom = padding.calculateBottomPadding() + 24.dp
                ),
            ) {
                item(key = "bar") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = actions.onBack) {
                            Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
                        }
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = { confirmRemove = true }) {
                            Icon(Icons.Rounded.DeleteOutline, "Remove from library")
                        }
                        Box {
                            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Options") }
                            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                                if (!playlist.isSingle) {
                                    DropdownMenuItem(
                                        text = { Text("Check for new videos") },
                                        leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                                        onClick = {
                                            menu = false
                                            actions.onRefresh(playlist)
                                        }
                                    )
                                }
                                DropdownMenuItem(
                                    text = { Text("Open in YouTube") },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                    onClick = {
                                        menu = false
                                        actions.onOpenInYouTube(playlist.url)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Start book over") },
                                    leadingIcon = { Icon(Icons.Rounded.Replay, null) },
                                    onClick = {
                                        menu = false
                                        actions.onResetBook(playlist)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Remove from library") },
                                    leadingIcon = { Icon(Icons.Rounded.DeleteOutline, null) },
                                    onClick = {
                                        menu = false
                                        confirmRemove = true
                                    }
                                )
                            }
                        }
                    }
                }

                item(key = "head") {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 8.dp)
                    ) {
                        BookCover(playlist, Modifier.fillMaxWidth(), playing = playing, compact = true)
                        Spacer(Modifier.height(18.dp))
                        SectionLabel(if (playlist.isSingle) "YouTube video" else "YouTube playlist")
                        Spacer(Modifier.height(4.dp))
                        Text(
                            playlist.title,
                            style = MaterialTheme.typography.headlineSmall,
                            maxLines = 3,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            listOf(playlist.channel, bookMeta(playlist)).filter { it.isNotBlank() }.joinToString(" · "),
                            style = MaterialTheme.typography.bodyMedium,
                            color = cs.onSurfaceVariant
                        )
                        if (playlist.started) {
                            Spacer(Modifier.height(10.dp))
                            ProgressLine(playlist.progress, height = 3.dp)
                        }
                        if (importing) {
                            Spacer(Modifier.height(10.dp))
                            LinearProgressIndicator(
                                modifier = Modifier.fillMaxWidth(),
                                color = cs.onSurface,
                                trackColor = cs.outlineVariant
                            )
                        }
                    }
                }

                item(key = "play") {
                    Surface(
                        onClick = {
                            when {
                                isCurrentBook -> actions.onTogglePlay()
                                else -> actions.onResume(playlist)
                            }
                        },
                        shape = RoundedCornerShape(20.dp),
                        color = cs.primary,
                        contentColor = cs.onPrimary,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp, vertical = 12.dp)
                    ) {
                        Row(
                            Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, null)
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(
                                    when {
                                        playing -> "Pause"
                                        playlist.finished -> "Play again"
                                        playlist.started -> "Continue"
                                        else -> "Play"
                                    },
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (resumeVideo != null && playlist.videoCount > 1) {
                                    Text(
                                        "${playlist.indexOf(resumeVideo.id) + 1}/${playlist.videoCount} · ${resumeVideo.title}",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = cs.onPrimary.copy(alpha = 0.7f),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                            if (playlist.durationMs > 0 && !playlist.finished) {
                                Text(
                                    "${formatDurationShort(playlist.durationMs - playlist.positionMs)} left",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = cs.onPrimary.copy(alpha = 0.7f)
                                )
                            }
                        }
                    }
                }

                item(key = "label") {
                    SectionLabel(
                        if (playlist.videoCount == 1) "1 video" else "${playlist.videoCount} videos",
                        Modifier.padding(start = 20.dp, top = 12.dp, bottom = 6.dp)
                    )
                }

                itemsIndexed(playlist.videos, key = { _, v -> v.id }) { i, v ->
                    var itemMenu by remember { mutableStateOf(false) }
                    VideoRow(
                        index = i + 1,
                        video = v,
                        isCurrent = nowVideo?.id == v.id,
                        isPlaying = nowVideo?.id == v.id && ui.isPlaying,
                        onClick = {
                            if (nowVideo?.id == v.id) actions.onOpenPlayer() else actions.onPlayVideo(v)
                        },
                        trailing = {
                            Box {
                                IconButton(onClick = { itemMenu = true }) {
                                    Icon(Icons.Rounded.MoreVert, "Video options", tint = cs.onSurfaceVariant)
                                }
                                DropdownMenu(expanded = itemMenu, onDismissRequest = { itemMenu = false }) {
                                    DropdownMenuItem(
                                        text = { Text(if (v.finished) "Mark as unwatched" else "Mark as watched") },
                                        leadingIcon = { Icon(Icons.Rounded.CheckCircle, null) },
                                        onClick = {
                                            itemMenu = false
                                            actions.onMarkFinished(v, !v.finished)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Start from the beginning") },
                                        leadingIcon = { Icon(Icons.Rounded.Replay, null) },
                                        onClick = {
                                            itemMenu = false
                                            actions.onResetVideo(v)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text("Open in YouTube") },
                                        leadingIcon = { Icon(Icons.AutoMirrored.Rounded.OpenInNew, null) },
                                        onClick = {
                                            itemMenu = false
                                            actions.onOpenInYouTube(v.watchUrl + "&t=${v.positionMs / 1000}s")
                                        }
                                    )
                                }
                            }
                        }
                    )
                }
            }
        }
    }

    if (confirmRemove) {
        ConfirmRemoveDialog(
            title = playlist.title,
            onConfirm = {
                confirmRemove = false
                actions.onRemoveBook(playlist)
            },
            onDismiss = { confirmRemove = false }
        )
    }
}
