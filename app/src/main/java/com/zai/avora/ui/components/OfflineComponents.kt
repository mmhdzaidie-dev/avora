package com.zai.avora.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CloudOff
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zai.avora.data.model.Track
import com.zai.avora.data.offline.OfflineManager
import com.zai.avora.ui.theme.AvoraAppTheme
import com.zai.avora.ui.theme.DownloadGreen
import com.zai.avora.ui.theme.Typography
import com.zai.avora.viewmodel.MusicViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

fun formatBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes / (1024.0 * 1024.0)
    return if (mb >= 1024) String.format("%.2f GB", mb / 1024.0) else String.format("%.1f MB", mb)
}

@Composable
fun rememberTrackDownload(trackId: String): Pair<Boolean, Float?> {
    val manager = remember { OfflineManager.getInstance() }
    val downloaded by remember(trackId) {
        manager.ids.map { trackId in it }.distinctUntilChanged()
    }.collectAsState(initial = manager.isDownloaded(trackId))
    val progress by remember(trackId) {
        manager.progress.map { it[trackId] }.distinctUntilChanged()
    }.collectAsState(initial = manager.progress.value[trackId])
    return downloaded to progress
}

@Composable
fun DownloadBadge(trackId: String, modifier: Modifier = Modifier, size: Dp = 14.dp) {
    val colors = AvoraAppTheme.colors
    val (downloaded, progress) = rememberTrackDownload(trackId)
    when {
        downloaded -> Box(
            modifier = modifier
                .size(size)
                .clip(CircleShape)
                .background(DownloadGreen),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = "Diunduh",
                tint = Color.Black,
                modifier = Modifier.size(size * 0.7f)
            )
        }
        progress != null -> CircularProgressIndicator(
            progress = { progress.coerceAtLeast(0.06f) },
            color = colors.accent,
            trackColor = colors.border,
            strokeWidth = 1.5.dp,
            modifier = modifier.size(size)
        )
    }
}

@Composable
fun DownloadCircleButton(
    allDownloaded: Boolean,
    inProgress: Boolean,
    fraction: Float,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AvoraAppTheme.colors
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(CircleShape)
            .background(if (allDownloaded) DownloadGreen else colors.card)
            .border(1.5.dp, if (allDownloaded) DownloadGreen else colors.border, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        if (inProgress) {
            CircularProgressIndicator(
                progress = { fraction.coerceAtLeast(0.06f) },
                color = colors.accent,
                trackColor = colors.border,
                strokeWidth = 2.dp,
                modifier = Modifier.size(28.dp)
            )
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Batalkan",
                tint = colors.textPrimary,
                modifier = Modifier.size(12.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.ArrowDownward,
                contentDescription = if (allDownloaded) "Sudah diunduh" else "Unduh",
                tint = if (allDownloaded) Color.Black else colors.textPrimary,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun DownloadAllBar(
    tracks: List<Track>,
    viewModel: MusicViewModel,
    modifier: Modifier = Modifier,
    title: String = "Unduh untuk offline"
) {
    if (tracks.isEmpty()) return
    val colors = AvoraAppTheme.colors
    val ids by viewModel.downloadedIds.collectAsState()
    val progress by viewModel.downloadProgress.collectAsState()
    var confirmRemove by remember { mutableStateOf(false) }

    val downloadedCount = tracks.count { it.id in ids }
    val active = tracks.filter { progress.containsKey(it.id) }
    val allDone = downloadedCount == tracks.size
    val fraction = if (active.isEmpty()) 0f else {
        val done = downloadedCount.toFloat()
        val partial = active.sumOf { (progress[it.id] ?: 0f).toDouble() }.toFloat()
        ((done + partial) / tracks.size).coerceIn(0f, 1f)
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(colors.card)
            .clickable {
                when {
                    active.isNotEmpty() -> active.forEach { viewModel.cancelDownload(it.id) }
                    allDone -> confirmRemove = true
                    else -> viewModel.downloadTracks(tracks)
                }
            }
            .padding(horizontal = 14.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DownloadCircleButton(
            allDownloaded = allDone,
            inProgress = active.isNotEmpty(),
            fraction = fraction,
            onClick = {
                when {
                    active.isNotEmpty() -> active.forEach { viewModel.cancelDownload(it.id) }
                    allDone -> confirmRemove = true
                    else -> viewModel.downloadTracks(tracks)
                }
            }
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = Typography.titleMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Text(
                text = when {
                    active.isNotEmpty() -> "Mengunduh ${active.size} lagu…  ketuk untuk batal"
                    allDone -> "Semua ${tracks.size} lagu sudah diunduh"
                    downloadedCount > 0 -> "$downloadedCount dari ${tracks.size} lagu diunduh"
                    else -> "${tracks.size} lagu • tersimpan di dalam aplikasi"
                },
                style = Typography.bodySmall,
                color = colors.textMuted
            )
        }
    }

    if (confirmRemove) {
        AlertDialog(
            onDismissRequest = { confirmRemove = false },
            title = { Text("Hapus unduhan?", style = Typography.titleLarge, color = colors.textPrimary) },
            text = {
                Text(
                    "${tracks.size} lagu akan dihapus dari penyimpanan offline. Lagunya tetap ada di daftar.",
                    style = Typography.bodyMedium,
                    color = colors.textMuted
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.removeDownloads(tracks)
                        confirmRemove = false
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background)
                ) { Text("Hapus") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRemove = false }) { Text("Batal", color = colors.textMuted) }
            },
            containerColor = colors.background
        )
    }
}

@Composable
fun DownloadTrackRow(track: Track, viewModel: MusicViewModel, modifier: Modifier = Modifier) {
    val colors = AvoraAppTheme.colors
    val (downloaded, progress) = rememberTrackDownload(track.id)
    val downloading = progress != null

    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(colors.card)
            .border(1.dp, colors.border, RoundedCornerShape(12.dp))
            .clickable {
                when {
                    downloading -> viewModel.cancelDownload(track.id)
                    downloaded -> viewModel.removeDownload(track.id)
                    else -> viewModel.downloadTrack(track)
                }
            }
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        DownloadCircleButton(
            allDownloaded = downloaded,
            inProgress = downloading,
            fraction = progress ?: 0f,
            onClick = {
                when {
                    downloading -> viewModel.cancelDownload(track.id)
                    downloaded -> viewModel.removeDownload(track.id)
                    else -> viewModel.downloadTrack(track)
                }
            },
            modifier = Modifier.size(36.dp)
        )
        Spacer(modifier = Modifier.width(14.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = when {
                    downloading -> "Mengunduh… ${((progress ?: 0f) * 100).toInt()}%"
                    downloaded -> "Hapus dari unduhan"
                    else -> "Unduh untuk offline"
                },
                style = Typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.textPrimary
            )
            Text(
                text = if (downloaded) "Lagu tersimpan di dalam Avora" else "Disimpan di dalam aplikasi, tidak muncul di galeri",
                style = Typography.bodySmall,
                color = colors.textMuted
            )
        }
    }
}

@Composable
fun OfflinePlaceholder(
    title: String,
    onOpenLibrary: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AvoraAppTheme.colors
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(colors.background)
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(84.dp)
                    .clip(CircleShape)
                    .background(colors.card)
                    .border(1.dp, colors.border, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.CloudOff,
                    contentDescription = null,
                    tint = colors.textPrimary,
                    modifier = Modifier.size(38.dp)
                )
            }
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "Kamu sedang offline",
                style = Typography.headlineMedium.copy(fontWeight = FontWeight.Bold),
                color = colors.textPrimary
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "$title butuh internet. Sekarang hanya lagu yang sudah diunduh (tanda hijau) yang bisa diputar, buka lewat playlist. Halaman ini kembali sendiri begitu internet tersambung.",
                style = Typography.bodyMedium,
                color = colors.textMuted,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = onOpenLibrary,
                shape = RoundedCornerShape(24.dp),
                colors = ButtonDefaults.buttonColors(containerColor = colors.accent, contentColor = colors.background),
                modifier = Modifier.fillMaxWidth()
            ) { Text("Buka playlist", fontWeight = FontWeight.Bold) }
        }
    }
}

@Composable
fun AvoraMessageHost(viewModel: MusicViewModel, modifier: Modifier = Modifier) {
    val colors = AvoraAppTheme.colors
    var current by remember { mutableStateOf<String?>(null) }
    var visible by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.messages.collectLatest { msg ->
            current = msg
            visible = true
            val shown = msg
            delay(2400)
            if (current == shown) visible = false
        }
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically { it / 2 },
        exit = fadeOut() + slideOutVertically { it / 2 },
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(horizontal = 24.dp)
                .clip(RoundedCornerShape(22.dp))
                .background(colors.textPrimary)
                .padding(horizontal = 18.dp, vertical = 11.dp)
        ) {
            Text(
                text = current ?: "",
                style = Typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
                color = colors.background,
                textAlign = TextAlign.Center
            )
        }
    }
}
