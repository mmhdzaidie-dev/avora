package com.zai.avora.ui.components

import android.content.Intent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.ui.draw.blur
import com.zai.avora.data.lyrics.TranslationLanguages
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.zai.avora.R
import com.zai.avora.data.lyrics.LyricsData
import com.zai.avora.data.model.Track
import com.zai.avora.ui.theme.AvoraAppTheme
import com.zai.avora.ui.theme.Typography
import com.zai.avora.viewmodel.MusicViewModel

@Composable
fun FullScreenLyricsView(
    track: Track,
    lyricsData: LyricsData?,
    isLoadingLyrics: Boolean,
    viewModel: MusicViewModel,
    isPlaying: Boolean,
    isLoading: Boolean,
    onPlayPauseClick: () -> Unit,
    onDismiss: () -> Unit,
    translationEnabled: Boolean,
    translationLang: String,
    onTranslationClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val colors = AvoraAppTheme.colors
    val context = LocalContext.current
    val currentPositionMs by viewModel.playbackPosition.collectAsState()
    val showTranslation = translationEnabled
    val listState = rememberLazyListState()

    val activeIndex by remember(lyricsData) {
        derivedStateOf { lyricsData?.getCurrentLineIndex(currentPositionMs) ?: -1 }
    }

    LaunchedEffect(activeIndex) {
        if (activeIndex >= 0) {
            listState.animateScrollToItem((activeIndex - 1).coerceAtLeast(0))
        }
    }

    Box(modifier = modifier.background(colors.background)) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(track.highResThumbnailUrl)
                .crossfade(true)
                .build(),
            contentDescription = null,
            modifier = Modifier
                .fillMaxSize()
                .then(if (canBlurBackdrop) Modifier.blur(70.dp) else Modifier),
            contentScale = ContentScale.Crop
        )
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = if (canBlurBackdrop) listOf(
                            colors.background.copy(alpha = 0.55f),
                            colors.background.copy(alpha = 0.78f),
                            colors.background.copy(alpha = 0.95f)
                        ) else listOf(
                            colors.background.copy(alpha = 0.82f),
                            colors.background.copy(alpha = 0.92f),
                            colors.background
                        )
                    )
                )
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                GlassSurface(
                    shape = CircleShape,
                    elevation = 6.dp,
                    modifier = Modifier
                        .size(40.dp)
                        .align(Alignment.CenterStart)
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.fillMaxSize()) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Close lyrics",
                            tint = colors.textPrimary,
                            modifier = Modifier.size(26.dp)
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 56.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = track.title,
                        style = Typography.titleMedium.copy(fontSize = 15.sp, fontWeight = FontWeight.Bold),
                        color = colors.textPrimary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = track.artist,
                        style = Typography.bodyMedium.copy(fontSize = 13.sp),
                        color = colors.textSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when {
                    isLoadingLyrics -> CircularProgressIndicator(
                        color = colors.textPrimary,
                        modifier = Modifier.size(32.dp).align(Alignment.Center),
                        strokeWidth = 2.dp
                    )

                    lyricsData == null || lyricsData.lines.isEmpty() -> Text(
                        text = "No synchronized lyrics available",
                        style = Typography.bodyMedium,
                        color = colors.textMuted,
                        modifier = Modifier.align(Alignment.Center)
                    )

                    else -> LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 32.dp),
                        verticalArrangement = Arrangement.spacedBy(22.dp)
                    ) {
                        itemsIndexed(
                            items = lyricsData.lines,
                            key = { index, line -> "${line.timeMs}_$index" }
                        ) { index, line ->
                            val isActive = index == activeIndex
                            val textColor by animateColorAsState(
                                targetValue = if (isActive) colors.textPrimary else colors.textPrimary.copy(alpha = 0.42f),
                                animationSpec = tween(durationMillis = 200),
                                label = "fullLyricColor"
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { if (line.timeMs >= 0) viewModel.seekTo(line.timeMs) }
                            ) {
                                Text(
                                    text = line.text,
                                    style = Typography.headlineMedium,
                                    fontSize = 26.sp,
                                    lineHeight = 33.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = textColor,
                                    textAlign = TextAlign.Start
                                )
                                if (showTranslation && line.translation != null) {
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = line.translation!!,
                                        style = Typography.bodyMedium,
                                        fontSize = 15.sp,
                                        lineHeight = 21.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = textColor.copy(alpha = if (isActive) 0.85f else 0.35f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassSurface(shape = RoundedCornerShape(50), elevation = 6.dp) {
                    Row(
                        modifier = Modifier
                            .clickable { onTranslationClick() }
                            .padding(horizontal = 16.dp, vertical = 11.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Translate,
                            contentDescription = "Translation language",
                            tint = if (showTranslation) colors.textPrimary else colors.textPrimary.copy(alpha = 0.45f),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = if (showTranslation) TranslationLanguages.shortLabel(translationLang) else "OFF",
                            style = Typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = colors.textPrimary
                        )
                    }
                }
                GlassSurface(shape = CircleShape, elevation = 6.dp, modifier = Modifier.size(44.dp)) {
                  IconButton(
                    modifier = Modifier.fillMaxSize(),
                    onClick = {
                        try {
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                putExtra(Intent.EXTRA_TEXT, "Dengarkan ${track.title} oleh ${track.artist}: ${track.url}")
                                type = "text/plain"
                            }
                            context.startActivity(Intent.createChooser(sendIntent, "Bagikan via"))
                        } catch (_: Exception) {
                        }
                    }
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Share,
                        contentDescription = "Share",
                        tint = colors.textPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                  }
                }
            }

            PlayerScrubber(
                viewModel = viewModel,
                track = track,
                modifier = Modifier.padding(horizontal = 24.dp)
            )

            Spacer(modifier = Modifier.height(8.dp))

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(colors.textPrimary),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            color = colors.background,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(32.dp)
                        )
                    } else {
                        IconButton(onClick = onPlayPauseClick, modifier = Modifier.size(72.dp)) {
                            Icon(
                                painter = painterResource(if (isPlaying) R.drawable.ic_avora_pause else R.drawable.ic_avora_play),
                                contentDescription = if (isPlaying) "Pause" else "Play",
                                tint = colors.background,
                                modifier = Modifier.size(34.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
