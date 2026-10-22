package com.zai.avora.service

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CommandButton
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch
import com.zai.avora.MainActivity
import com.zai.avora.R

class PlaybackService : MediaSessionService() {

    private var player: Player? = null
    private var mediaSession: MediaSession? = null
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private val isPlayingState = MutableStateFlow(false)

    private val cmdFavorite = SessionCommand("com.zai.avora.CMD_FAVORITE", Bundle.EMPTY)
    private val cmdRepeat = SessionCommand("com.zai.avora.CMD_REPEAT", Bundle.EMPTY)

    companion object {
        const val CHANNEL_ID = "avora_playback_channel"
        const val NOTIFICATION_ID = 1001
        const val ACTION_NEXT = "com.zai.avora.ACTION_NEXT"
        const val ACTION_PREV = "com.zai.avora.ACTION_PREV"
    }

    @OptIn(UnstableApi::class)
    override fun onCreate() {
        super.onCreate()

        createNotificationChannel()

        val audioAttributes = AudioAttributes.Builder()
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .setUsage(C.USAGE_MEDIA)
            .build()

        val httpDataSourceFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15_000)
            .setReadTimeoutMs(15_000)

        val extractorsFactory = androidx.media3.extractor.DefaultExtractorsFactory()
            .setConstantBitrateSeekingEnabled(true)
            .setMp4ExtractorFlags(androidx.media3.extractor.mp4.Mp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)
            .setFragmentedMp4ExtractorFlags(androidx.media3.extractor.mp4.FragmentedMp4Extractor.FLAG_WORKAROUND_IGNORE_EDIT_LISTS)

        val dataSourceFactory = androidx.media3.datasource.DefaultDataSource.Factory(this, httpDataSourceFactory)
        val mediaSourceFactory = DefaultMediaSourceFactory(dataSourceFactory, extractorsFactory)
            .setLoadErrorHandlingPolicy(androidx.media3.exoplayer.upstream.DefaultLoadErrorHandlingPolicy(8))

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                 15_000,
                 50_000,
                 500,
                 1_500
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val exoPlayer = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(audioAttributes, true)
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
            .build()

        exoPlayer.addListener(object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                isPlayingState.value = playWhenReady
            }

            override fun onPlayerError(error: PlaybackException) {
                Log.e("AvoraPlaybackService", "ExoPlayer Error: ${error.message}", error)
            }
        })

        val forwardingPlayer = object : androidx.media3.common.ForwardingPlayer(exoPlayer) {
            override fun isCommandAvailable(command: Int): Boolean {
                return when (command) {
                    Player.COMMAND_SEEK_TO_NEXT,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                    Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> true
                    else -> super.isCommandAvailable(command)
                }
            }

            override fun getAvailableCommands(): Player.Commands {
                val builder = super.getAvailableCommands().buildUpon()
                    .add(Player.COMMAND_SEEK_TO_NEXT)
                    .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                    .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                if (!playWhenReady) {
                    builder.removeAll(
                        Player.COMMAND_SEEK_TO_NEXT,
                        Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_TO_PREVIOUS,
                        Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                        Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM,
                        Player.COMMAND_SEEK_BACK,
                        Player.COMMAND_SEEK_FORWARD
                    )
                }
                return builder.build()
            }

            override fun seekToNext() {
                sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
            }

            override fun seekToNextMediaItem() {
                sendBroadcast(Intent(ACTION_NEXT).setPackage(packageName))
            }

            override fun seekToPrevious() {
                sendBroadcast(Intent(ACTION_PREV).setPackage(packageName))
            }

            override fun seekToPreviousMediaItem() {
                sendBroadcast(Intent(ACTION_PREV).setPackage(packageName))
            }
        }

        player = forwardingPlayer

        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notificationProvider = DefaultMediaNotificationProvider.Builder(this)
            .setChannelId(CHANNEL_ID)
            .setChannelName(R.string.app_name)
            .setNotificationId(NOTIFICATION_ID)
            .build()
        notificationProvider.setSmallIcon(R.drawable.ic_notification)

        setMediaNotificationProvider(notificationProvider)

        mediaSession = MediaSession.Builder(this, forwardingPlayer)
            .setSessionActivity(pendingIntent)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo
                ): MediaSession.ConnectionResult {
                    val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                        .add(cmdFavorite)
                        .add(cmdRepeat)
                        .build()
                    return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                        .setAvailableSessionCommands(commands)
                        .build()
                }

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    customCommand: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (customCommand.customAction) {
                        cmdFavorite.customAction ->
                            MediaBridge.commands.tryEmit(MediaBridge.Command.TOGGLE_FAVORITE)
                        cmdRepeat.customAction ->
                            MediaBridge.commands.tryEmit(MediaBridge.Command.TOGGLE_REPEAT)
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .build()

        serviceScope.launch {
            combine(MediaBridge.isFavorite, MediaBridge.repeatMode, isPlayingState) { fav, repeat, playing ->
                Triple(fav, repeat, playing)
            }.collect { (fav, repeat, playing) ->
                val layout = if (playing) buildCustomLayout(fav, repeat) else ImmutableList.of()
                mediaSession?.setCustomLayout(layout)
            }
        }
    }

    private fun buildCustomLayout(isFavorite: Boolean, repeatMode: Int): ImmutableList<CommandButton> {
        val favButton = CommandButton.Builder()
            .setDisplayName(if (isFavorite) "Hapus dari favorit" else "Tambahkan ke favorit")
            .setIconResId(if (isFavorite) R.drawable.ic_media_heart_on else R.drawable.ic_media_heart_off)
            .setSessionCommand(cmdFavorite)
            .build()
        val repeatButton = CommandButton.Builder()
            .setDisplayName("Ulangi")
            .setIconResId(
                when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> R.drawable.ic_media_repeat_one
                    Player.REPEAT_MODE_ALL -> R.drawable.ic_media_repeat_all
                    else -> R.drawable.ic_media_repeat_off
                }
            )
            .setSessionCommand(cmdRepeat)
            .build()
        return ImmutableList.of(favButton, repeatButton)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.app_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Avora Media Playback Controls"
                setShowBadge(false)
            }
            val notificationManager = getSystemService(NotificationManager::class.java)
            notificationManager?.createNotificationChannel(channel)
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? {
        return mediaSession
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        val p = mediaSession?.player
        if (p == null || !p.playWhenReady || p.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        serviceScope.cancel()
        mediaSession?.run {
            player.release()
            release()
            mediaSession = null
        }
        player = null
        super.onDestroy()
    }
}
