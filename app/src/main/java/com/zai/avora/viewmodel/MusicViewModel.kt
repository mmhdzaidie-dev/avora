package com.zai.avora.viewmodel

import android.app.Application
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import coil.Coil
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.zai.avora.data.model.Track
import com.zai.avora.data.remote.MusicRepository
import com.zai.avora.data.remote.ResolvedStream
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import com.zai.avora.service.PlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import java.io.ByteArrayOutputStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MusicViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = MusicRepository()
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null

    private val _currentTrack = MutableStateFlow<Track?>(null)
    val currentTrack = _currentTrack.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    val isPlaying = _isPlaying.asStateFlow()

    private val _isLoadingTrack = MutableStateFlow(false)
    val isLoadingTrack = _isLoadingTrack.asStateFlow()

    private val _playbackPosition = MutableStateFlow(0L)
    val playbackPosition = _playbackPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    val duration = _duration.asStateFlow()

    private val _isPlayerExpanded = MutableStateFlow(false)
    val isPlayerExpanded = _isPlayerExpanded.asStateFlow()

    private val _homeSections = MutableStateFlow<List<com.zai.avora.data.model.HomeSection>>(emptyList())
    val homeSections = _homeSections.asStateFlow()

    private val _trendingTracks = MutableStateFlow<List<Track>>(emptyList())
    val trendingTracks = _trendingTracks.asStateFlow()

    private val _searchResults = MutableStateFlow<List<Track>>(emptyList())
    val searchResults = _searchResults.asStateFlow()

    private val _suggestions = MutableStateFlow<List<String>>(emptyList())
    val suggestions = _suggestions.asStateFlow()

    private val favPrefs = getApplication<android.app.Application>().getSharedPreferences("avora_favorites", android.content.Context.MODE_PRIVATE)

    private val _favorites = MutableStateFlow<List<Track>>(loadStoredFavorites())
    val favorites = _favorites.asStateFlow()

    private fun loadStoredFavorites(): List<Track> {
        val jsonStr = favPrefs.getString("fav_list", null) ?: return emptyList()
        return try {
            val arr = org.json.JSONArray(jsonStr)
            val list = mutableListOf<Track>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    Track(
                        id = obj.getString("id"),
                        url = obj.getString("url"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        thumbnailUrl = obj.getString("thumbnailUrl"),
                        durationSeconds = obj.optLong("durationSeconds", 0L),
                        isFavorite = true
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveStoredFavorites(list: List<Track>) {
        try {
            val arr = org.json.JSONArray()
            list.forEach { t ->
                val obj = org.json.JSONObject().apply {
                    put("id", t.id)
                    put("url", t.url)
                    put("title", t.title)
                    put("artist", t.artist)
                    put("thumbnailUrl", t.thumbnailUrl)
                    put("durationSeconds", t.durationSeconds)
                }
                arr.put(obj)
            }
            favPrefs.edit().putString("fav_list", arr.toString()).apply()
        } catch (e: Exception) {
            android.util.Log.e("AvoraVM", "Save favorites error: ${e.message}")
        }
    }

    private val _isLoadingTrending = MutableStateFlow(false)
    val isLoadingTrending = _isLoadingTrending.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching = _isSearching.asStateFlow()

    private val _isShuffle = MutableStateFlow(false)
    val isShuffle = _isShuffle.asStateFlow()

    private val _repeatMode = MutableStateFlow(Player.REPEAT_MODE_OFF)
    val repeatMode = _repeatMode.asStateFlow()

    val authState = com.zai.avora.data.auth.AuthManager.getInstance().authState

    val settingsManager = com.zai.avora.data.settings.SettingsManager.getInstance()
    val themeMode = settingsManager.themeMode
    val audioQuality = settingsManager.audioQuality

    val audioOutputManager = com.zai.avora.data.audio.AudioOutputManager.getInstance(getApplication())
    val audioDeviceState = audioOutputManager.deviceState
    fun openAudioOutputSettings() = audioOutputManager.openAudioSettings()

    val tasteManager = com.zai.avora.data.recommendation.TasteManager.getInstance(getApplication())
    val recentlyPlayed = tasteManager.recentlyPlayed
    val onRepeatTracks = tasteManager.onRepeatTracks

    val playlistManager = com.zai.avora.data.playlist.PlaylistManager.getInstance(getApplication())
    val playlists = playlistManager.playlists

    fun createPlaylist(name: String) = playlistManager.createPlaylist(name)
    fun deletePlaylist(id: String) = playlistManager.deletePlaylist(id)
    fun renamePlaylist(id: String, name: String) = playlistManager.renamePlaylist(id, name)
    fun addTrackToPlaylist(id: String, track: Track) = playlistManager.addTrackToPlaylist(id, track)
    fun removeTrackFromPlaylist(id: String, trackId: String) = playlistManager.removeTrackFromPlaylist(id, trackId)
    fun setPlaylistCover(id: String, bitmap: Bitmap): Boolean = playlistManager.setCover(id, bitmap)
    fun removePlaylistCover(id: String) = playlistManager.removeCover(id)

    val offlineManager = com.zai.avora.data.offline.OfflineManager.getInstance()
    val offlineMode = settingsManager.offlineMode
    val downloadedTracks = offlineManager.items
    val downloadedIds = offlineManager.ids
    val downloadProgress = offlineManager.progress

    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val messages = _messages.asSharedFlow()
    fun showMessage(text: String) { _messages.tryEmit(text) }

    init {
        viewModelScope.launch { offlineManager.events.collect { _messages.tryEmit(it) } }
        viewModelScope.launch {
            var first = true
            offlineMode.collect { offline ->
                if (first) {
                    first = false
                    if (offline) showMessage("Tidak ada internet — hanya lagu unduhan yang diputar")
                } else if (offline) {
                    showMessage("Tidak ada internet — mode offline aktif otomatis")
                } else {
                    showMessage("Kembali online")
                    if (_homeSections.value.isEmpty()) loadTrending()
                }
            }
        }
    }

    fun downloadTrack(track: Track) {
        offlineManager.download(track, settingsManager.audioQuality.value)
        showMessage("Mengunduh \"${track.title}\"")
    }

    fun downloadTracks(tracks: List<Track>) {
        val todo = tracks.filter { !offlineManager.isDownloaded(it.id) }
        if (todo.isEmpty()) return
        offlineManager.downloadAll(todo, settingsManager.audioQuality.value)
        showMessage("Mengunduh ${todo.size} lagu")
    }

    fun removeDownload(trackId: String) = offlineManager.remove(trackId)
    fun cancelDownload(trackId: String) = offlineManager.cancel(trackId)
    fun removeDownloads(tracks: List<Track>) = tracks.forEach { offlineManager.remove(it.id) }
    fun removeAllDownloads() {
        offlineManager.removeAll()
        showMessage("Semua unduhan dihapus")
    }

    fun playPlaylist(playlist: com.zai.avora.data.playlist.Playlist, startTrack: Track? = null, shuffle: Boolean = false) {
        if (playlist.tracks.isEmpty()) return
        val list = if (shuffle) playlist.tracks.shuffled() else playlist.tracks
        val initialTrack = startTrack ?: list.first()
        playTrack(initialTrack, list)
    }

    val lyricsRepository = com.zai.avora.data.lyrics.LyricsRepository(getApplication())

    private val _currentLyrics = MutableStateFlow<com.zai.avora.data.lyrics.LyricsData?>(null)
    val currentLyrics = _currentLyrics.asStateFlow()

    private val _isLoadingLyrics = MutableStateFlow(false)
    val isLoadingLyrics = _isLoadingLyrics.asStateFlow()

    private val playlistQueue = java.util.concurrent.CopyOnWriteArrayList<Track>()
    private var currentIndex = -1
    private var progressJob: Job? = null
    private var suggestJob: Job? = null
    private var playJob: Job? = null
    private var lyricsJob: Job? = null
    private var translateJob: Job? = null

    val autoTranslate = settingsManager.autoTranslate
    val translationLang = settingsManager.translationLang

    init {
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(autoTranslate, translationLang) { on, lang -> on to lang }
                .collect { retranslateCurrentLyrics() }
        }
    }

    fun setTranslationLanguage(code: String) {
        settingsManager.setTranslationLang(code)
        settingsManager.setAutoTranslate(true)
    }

    fun setTranslationEnabled(enabled: Boolean) = settingsManager.setAutoTranslate(enabled)

    private fun retranslateCurrentLyrics() {
        val data = _currentLyrics.value ?: return
        val lang = settingsManager.translationLang.value
        if (!settingsManager.autoTranslate.value || data.lines.isEmpty() || data.translationLang == lang) return
        translateJob?.cancel()
        translateJob = viewModelScope.launch(Dispatchers.IO) {
            val translated = lyricsRepository.translateLyrics(data, lang)
            if (_currentLyrics.value?.trackId == data.trackId) {
                _currentLyrics.value = translated
            }
        }
    }
    private var searchJob: Job? = null
    private var pendingTrackToPlay: Track? = null
    private var streamErrorRetries = 0
    private var streamRecoveryTotal = 0
    private var trackStartTime = 0L

    private val mediaActionReceiver = object : android.content.BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            when (intent?.action) {
                PlaybackService.ACTION_NEXT -> skipNext()
                PlaybackService.ACTION_PREV -> skipPrevious()
            }
        }
    }

    init {
        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                delay(30 * 60 * 1000L)
                com.zai.avora.data.cache.CacheCleaner.cleanWhileRunning(getApplication())
            }
        }
        initializeMediaController()
        loadTrending()
        try {
            val filter = android.content.IntentFilter().apply {
                addAction(PlaybackService.ACTION_NEXT)
                addAction(PlaybackService.ACTION_PREV)
            }
            androidx.core.content.ContextCompat.registerReceiver(
                getApplication(),
                mediaActionReceiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED
            )
        } catch (_: Exception) {}
    }

    init {
        viewModelScope.launch {
            com.zai.avora.service.MediaBridge.commands.collect { cmd ->
                when (cmd) {
                    com.zai.avora.service.MediaBridge.Command.TOGGLE_FAVORITE ->
                        _currentTrack.value?.let { toggleFavorite(it) }
                    com.zai.avora.service.MediaBridge.Command.TOGGLE_REPEAT -> toggleRepeat()
                }
            }
        }
        viewModelScope.launch {
            kotlinx.coroutines.flow.combine(_currentTrack, _favorites) { track, favs ->
                track != null && favs.any { it.id == track.id }
            }.collect { com.zai.avora.service.MediaBridge.isFavorite.value = it }
        }
        viewModelScope.launch {
            _repeatMode.collect { com.zai.avora.service.MediaBridge.repeatMode.value = it }
        }
    }

    private fun initializeMediaController() {
        val sessionToken = SessionToken(
            getApplication(),
            ComponentName(getApplication(), PlaybackService::class.java)
        )
        controllerFuture = MediaController.Builder(getApplication(), sessionToken).buildAsync()
        controllerFuture?.addListener({
            try {
                mediaController = controllerFuture?.get()
                pendingTrackToPlay?.let { pending ->
                    pendingTrackToPlay = null
                    playTrack(pending)
                }
                mediaController?.addListener(object : Player.Listener {
                    override fun onIsPlayingChanged(playing: Boolean) {
                        _isPlaying.value = playing
                        if (playing) {
                            _isLoadingTrack.value = false
                            startProgressTracking()
                        } else {
                            stopProgressTracking()
                        }
                    }

                    override fun onPlaybackStateChanged(playbackState: Int) {
                        when (playbackState) {
                            Player.STATE_BUFFERING -> {
                                _isLoadingTrack.value = true
                            }
                            Player.STATE_READY -> {
                                _isLoadingTrack.value = false
                                streamErrorRetries = 0
                                isSeeking = false
                                mediaController?.let { controller ->
                                    val d = controller.duration
                                    if (d > 0) {
                                        _duration.value = d
                                        val currentLrc = _currentLyrics.value
                                        val currentTrk = _currentTrack.value
                                        if (currentTrk != null && (currentLrc == null || !currentLrc.isSynced)) {
                                            val realDurSec = d / 1000L
                                            if (realDurSec > 0 && realDurSec != currentTrk.durationSeconds) {
                                                lyricsJob?.cancel()
                                                lyricsJob = viewModelScope.launch(Dispatchers.IO) {
                                                    val refreshed = lyricsRepository.getLyrics(
                                                        trackId = currentTrk.id,
                                                        title = currentTrk.title,
                                                        artist = currentTrk.artist,
                                                        durationSeconds = realDurSec,
                                                        autoTranslate = settingsManager.autoTranslate.value,
                                                        targetLang = settingsManager.translationLang.value,
                                                        preferSynced = true
                                                    )
                                                    if (refreshed != null && _currentTrack.value?.id == currentTrk.id) {
                                                        val shown = _currentLyrics.value
                                                        if (shown == null || refreshed.isSynced || !shown.isSynced) {
                                                            _currentLyrics.value = refreshed
                                                        }
                                                    }
                                                    _isLoadingLyrics.value = false
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            Player.STATE_ENDED -> {
                                val ctrl = mediaController
                                val endDuration = ctrl?.duration ?: 0L
                                val endPosition = ctrl?.currentPosition ?: 0L
                                if (endDuration > 0 && endPosition < endDuration - 10_000L && recoverStream()) {
                                    return
                                }
                                _isLoadingTrack.value = false
                                _currentTrack.value?.let { tasteManager.recordTrackPlayed(it) }
                                skipNext()
                            }
                            Player.STATE_IDLE -> {
                                _isLoadingTrack.value = false
                            }
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        Log.e("AvoraMusicViewModel", "Player error: ${error.message}", error)
                        if (!recoverStream()) {
                            streamErrorRetries = 0
                            _isLoadingTrack.value = false
                            _isPlaying.value = false
                            skipNext()
                        }
                    }
                })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }, MoreExecutors.directExecutor())
    }

    private fun recoverStream(): Boolean {
        val track = _currentTrack.value ?: return false
        val oldUrl = track.streamUrl ?: return false
        if (oldUrl.startsWith("file")) return false
        if (streamErrorRetries >= 2 || streamRecoveryTotal >= 6) return false
        val attempt = streamErrorRetries
        streamErrorRetries++
        streamRecoveryTotal++
        val resumeMs = (mediaController?.currentPosition ?: 0L).coerceAtLeast(0L)
        _isLoadingTrack.value = true
        viewModelScope.launch(Dispatchers.IO) {
            var newUrl: String? = null
            var newMime: String? = null
            if (attempt == 0) {
                val fresh = repository.getAudioStream(
                    track.url,
                    settingsManager.audioQuality.value,
                    forceRefresh = true
                )
                newUrl = fresh?.url
                newMime = fresh?.mimeType
            }
            if (newUrl == null || newUrl == oldUrl) {
                newUrl = repository.getFallbackStreamUrl(track.url)
                newMime = "audio/webm"
                streamErrorRetries = 2
            }
            val finalUrl = newUrl
            val finalMime = newMime
            if (finalUrl != null && finalUrl != oldUrl) {
                val refreshedTrack = track.copy(streamUrl = finalUrl, mimeType = finalMime)
                withContext(Dispatchers.Main) {
                    _currentTrack.value = refreshedTrack
                    mediaController?.let { ctrl ->
                        val meta = MediaMetadata.Builder()
                            .setTitle(refreshedTrack.mediaTitle)
                            .setArtist(refreshedTrack.mediaArtist)
                            .setArtworkUri(Uri.parse(refreshedTrack.highResThumbnailUrl))
                            .setDisplayTitle(refreshedTrack.mediaTitle)
                            .build()

                        val itemBuilder = MediaItem.Builder()
                            .setMediaId(refreshedTrack.id)
                            .setUri(finalUrl)
                            .setMediaMetadata(meta)
                        if (!finalMime.isNullOrBlank()) itemBuilder.setMimeType(finalMime)

                        ctrl.setMediaItem(itemBuilder.build(), resumeMs)
                        ctrl.prepare()
                        ctrl.play()
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    _isLoadingTrack.value = false
                    _isPlaying.value = false
                    skipNext()
                }
            }
        }
        return true
    }

    private var isSeeking = false
    private var lastSeekTimestamp = 0L

    private fun startProgressTracking() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (true) {
                val now = System.currentTimeMillis()
                if (isSeeking && (now - lastSeekTimestamp < 1200L)) {
                } else {
                    isSeeking = false
                    mediaController?.let { controller ->
                        val pos = controller.currentPosition.coerceAtLeast(0)
                        _playbackPosition.value = pos
                        val realDuration = controller.duration
                        if (realDuration > 0) {
                            _duration.value = realDuration
                        }
                    }
                }
                delay(250)
            }
        }
    }

    private fun stopProgressTracking() {
        progressJob?.cancel()
    }

    fun loadTrending() {
        if (settingsManager.offlineMode.value) return
        viewModelScope.launch {
            _isLoadingTrending.value = true
            val rawShelves = repository.getHomeShelves().toMutableList()
            val onRepeat = tasteManager.onRepeatTracks.value
            if (onRepeat.isNotEmpty()) {
                rawShelves.add(0, com.zai.avora.data.model.HomeSection(
                    title = "Lagi Sering Kamu Putar",
                    subtitle = "Berdasarkan selera musikmu",
                    tracks = onRepeat
                ))
            }
            _homeSections.value = rawShelves
            val tracks = if (rawShelves.isNotEmpty()) {
                rawShelves.flatMap { it.tracks }.distinctBy { it.id }
            } else {
                repository.getTrendingTracks()
            }
            _trendingTracks.value = tracks
            _isLoadingTrending.value = false

            launch(Dispatchers.IO) {
                delay(2000)
                tracks.take(2).forEach {
                    repository.getAudioStreamUrl(it.url, settingsManager.audioQuality.value)
                }
            }
        }
    }

    fun loadSuggestions(query: String) {
        suggestJob?.cancel()
        if (query.isBlank()) {
            _suggestions.value = emptyList()
            return
        }
        suggestJob = viewModelScope.launch {
            delay(200)
            val list = repository.getSuggestions(query)
            _suggestions.value = list
        }
    }

    fun search(query: String) {
        searchJob?.cancel()
        _suggestions.value = emptyList()
        if (query.isBlank()) {
            _searchResults.value = emptyList()
            return
        }
        searchJob = viewModelScope.launch {
            _isSearching.value = true
            val results = repository.searchTracks(query)
            _searchResults.value = results
            _isSearching.value = false

            launch(Dispatchers.IO) {
                delay(1500)
                results.take(2).forEach {
                    repository.getAudioStreamUrl(it.url, settingsManager.audioQuality.value)
                }
            }
        }
    }

    fun playTrackFromSearch(track: Track) {
        playlistQueue.clear()
        playlistQueue.add(track)
        currentIndex = 0
        playTrack(track, playlistQueue)
    }

    fun playTrack(track: Track, requestedQueue: List<Track> = emptyList()) {
        val offline = settingsManager.offlineMode.value
        val localFile = offlineManager.localAudio(track.id)
        if (offline && localFile == null) {
            showMessage("\"${track.title}\" belum diunduh")
            return
        }
        val newQueue = if (offline && requestedQueue.isNotEmpty()) {
            requestedQueue.filter { offlineManager.isDownloaded(it.id) }
        } else requestedQueue

        playJob?.cancel()
        val playGuard = kotlinx.coroutines.CoroutineExceptionHandler { _, e ->
            Log.e("AvoraVM", "playTrack error: ${e.message}", e)
            _isLoadingTrack.value = false
        }
        playJob = viewModelScope.launch(playGuard) {
            _currentTrack.value = track
            _isLoadingTrack.value = true
            _playbackPosition.value = 0L
            _duration.value = if (track.durationSeconds > 0) track.durationSeconds * 1000L else 0L

            if (newQueue.isNotEmpty() && newQueue !== playlistQueue) {
                playlistQueue.clear()
                playlistQueue.addAll(newQueue)
                currentIndex = playlistQueue.indexOfFirst { it.id == track.id }
            } else if (currentIndex == -1 || playlistQueue.getOrNull(currentIndex)?.id != track.id) {
                val idx = playlistQueue.indexOfFirst { it.id == track.id }
                if (idx != -1) {
                    currentIndex = idx
                } else if (playlistQueue.isEmpty()) {
                    playlistQueue.add(track)
                    currentIndex = 0
                }
            }

            if (playlistQueue.size <= 2 && !offline) {
                launch(Dispatchers.IO) {
                    try {
                        val radioTracks = repository.getRadioTracks(track.id)
                        if (radioTracks.isNotEmpty()) {
                            val existingIds = playlistQueue.map { it.id }.toSet()
                            val freshTracks = radioTracks.filter { it.id !in existingIds }
                            playlistQueue.addAll(freshTracks)
                            if (playlistQueue.size > 1) {
                                repository.getAudioStreamUrl(playlistQueue[1].url, settingsManager.audioQuality.value)
                            }
                        }
                    } catch (e: Exception) {
                        Log.d("AvoraVM", "Automix radio fetch: ${e.message}")
                    }
                }
            }

                lyricsJob?.cancel()
                lyricsJob = viewModelScope.launch(Dispatchers.IO) {
                    _isLoadingLyrics.value = true
                    _currentLyrics.value = null
                    try {
                        val data = lyricsRepository.getLyrics(
                            trackId = track.id,
                            title = track.title,
                            artist = track.artist,
                            durationSeconds = track.durationSeconds,
                            autoTranslate = false
                        )
                        _currentLyrics.value = data
                        _isLoadingLyrics.value = false

                        var finalData = data
                        var retry = 0
                        while ((finalData == null || finalData.lines.isEmpty()) && retry < 2 &&
                            !settingsManager.offlineMode.value
                        ) {
                            retry++
                            delay(4_000L)
                            if (_currentTrack.value?.id != track.id) break
                            lyricsRepository.forgetMisses(track.id)
                            val again = lyricsRepository.getLyrics(
                                trackId = track.id,
                                title = track.title,
                                artist = track.artist,
                                durationSeconds = track.durationSeconds,
                                autoTranslate = false
                            )
                            if (again != null && again.lines.isNotEmpty() && _currentTrack.value?.id == track.id) {
                                _currentLyrics.value = again
                                finalData = again
                            }
                        }
                        val lyricsNow = finalData

                        val targetLang = settingsManager.translationLang.value
                        if (lyricsNow != null && lyricsNow.lines.isNotEmpty() && settingsManager.autoTranslate.value &&
                            lyricsNow.translationLang != targetLang
                        ) {
                            val translated = lyricsRepository.translateLyrics(lyricsNow, targetLang)
                            if (_currentTrack.value?.id == track.id) {
                                _currentLyrics.value = translated
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("AvoraVM", "Lyrics load error: ${e.message}")
                        _isLoadingLyrics.value = false
                    }
                }

            streamErrorRetries = 0
            streamRecoveryTotal = 0
            trackStartTime = System.currentTimeMillis()
            val localArt = if (localFile != null) offlineManager.localArtUri(track.id) else null
            val resolvedStream = if (localFile != null) {
                ResolvedStream(Uri.fromFile(localFile).toString(), offlineManager.mimeOf(track.id))
            } else {
                repository.getAudioStream(track.url, settingsManager.audioQuality.value)
            }
            val bestThumbnail = localArt
                ?: if (offline) track.thumbnailUrl else repository.resolveBestThumbnail(track)
            if (resolvedStream != null) {
                val updatedTrack = track.copy(
                    streamUrl = resolvedStream.url,
                    mimeType = resolvedStream.mimeType,
                    thumbnailUrl = bestThumbnail
                )
                _currentTrack.value = updatedTrack

                val controller = mediaController
                if (controller != null) {
                    val mediaMetadataBuilder = MediaMetadata.Builder()
                        .setTitle(updatedTrack.mediaTitle)
                        .setArtist(updatedTrack.mediaArtist)
                        .setArtworkUri(Uri.parse(updatedTrack.highResThumbnailUrl))
                        .setDisplayTitle(updatedTrack.mediaTitle)
                    if (localFile != null) {
                        offlineManager.artBytes(track.id)?.let {
                            mediaMetadataBuilder.setArtworkData(it, MediaMetadata.PICTURE_TYPE_FRONT_COVER)
                        }
                    }
                    val mediaMetadata = mediaMetadataBuilder.build()

                    val mediaItemBuilder = MediaItem.Builder()
                        .setMediaId(updatedTrack.id)
                        .setUri(resolvedStream.url)
                        .setMediaMetadata(mediaMetadata)

                    if (!resolvedStream.mimeType.isNullOrBlank()) {
                        mediaItemBuilder.setMimeType(resolvedStream.mimeType)
                    }

                    controller.setMediaItem(mediaItemBuilder.build())
                    controller.prepare()
                    controller.play()
                } else {
                    pendingTrackToPlay = updatedTrack
                }

                if (!offline && playlistQueue.isNotEmpty() && currentIndex + 1 < playlistQueue.size) {
                    val nextTrack = playlistQueue[currentIndex + 1]
                    launch(Dispatchers.IO) {
                        repository.getAudioStreamUrl(nextTrack.url, settingsManager.audioQuality.value)
                    }
                }
            } else {
                _isLoadingTrack.value = false
                Log.e("AvoraMusicViewModel", "Failed to resolve stream URL for ${track.title}")
                if (playlistQueue.isNotEmpty() && currentIndex + 1 < playlistQueue.size) {
                    skipNext()
                }
            }
        }
    }

    fun togglePlayPause() {
        mediaController?.let { controller ->
            if (controller.isPlaying) {
                controller.pause()
            } else {
                controller.play()
            }
        }
    }

    fun getProgress(): Float {
        val d = if (_duration.value > 0) _duration.value else ((_currentTrack.value?.durationSeconds ?: 0) * 1000L)
        return if (d > 0) (_playbackPosition.value.toFloat() / d.toFloat()).coerceIn(0f, 1f) else 0f
    }

    fun seekTo(positionMs: Long) {
        val target = positionMs.coerceAtLeast(0L)
        _playbackPosition.value = target
        lastSeekTimestamp = System.currentTimeMillis()
        isSeeking = true
        mediaController?.let { controller ->
            controller.seekTo(target)
            if (!controller.isPlaying) {
                controller.play()
            }
        }
    }

    fun getPositionMs(): Long = _playbackPosition.value

    fun skipNext() {
        if (playlistQueue.isEmpty()) return
        val current = _currentTrack.value
        if (current != null && trackStartTime > 0L) {
            val playedSec = (System.currentTimeMillis() - trackStartTime) / 1000L
            if (playedSec in 1..14) {
                tasteManager.recordTrackSkipped(current)
            } else if (playedSec >= 25) {
                tasteManager.recordTrackPlayed(current)
            }
        }
        if (_repeatMode.value == Player.REPEAT_MODE_ONE) {
            _currentTrack.value?.let { playTrack(it) }
            return
        }
        var nextIndex = if (_isShuffle.value) {
            (0 until playlistQueue.size).random()
        } else if (currentIndex + 1 < playlistQueue.size) {
            currentIndex + 1
        } else if (_repeatMode.value == Player.REPEAT_MODE_ALL) {
            0
        } else {
            -1
        }

        if (settingsManager.offlineMode.value && nextIndex in 0 until playlistQueue.size &&
            !offlineManager.isDownloaded(playlistQueue[nextIndex].id)
        ) {
            val downloaded = playlistQueue.indices.filter { offlineManager.isDownloaded(playlistQueue[it].id) }
            nextIndex = downloaded.firstOrNull { it > currentIndex }
                ?: if (_repeatMode.value == Player.REPEAT_MODE_ALL) downloaded.firstOrNull() ?: -1 else -1
        }

        if (nextIndex in 0 until playlistQueue.size) {
            currentIndex = nextIndex
            playTrack(playlistQueue[nextIndex])

            if (!settingsManager.offlineMode.value && playlistQueue.size - nextIndex <= 3 && playlistQueue.isNotEmpty()) {
                val seed = playlistQueue[nextIndex]
                viewModelScope.launch(Dispatchers.IO) {
                    try {
                        val freshRadio = repository.getRadioTracks(seed.id)
                        val existingIds = playlistQueue.map { it.id }.toSet()
                        val toAdd = freshRadio.filter { it.id !in existingIds }
                        playlistQueue.addAll(toAdd)
                    } catch (_: Exception) {}
                }
            }
        } else {
            _isPlaying.value = false
            mediaController?.pause()
        }
    }

    fun skipPrevious() {
        if (playlistQueue.isEmpty()) return
        val current = _currentTrack.value
        if (current != null && trackStartTime > 0L) {
            val playedSec = (System.currentTimeMillis() - trackStartTime) / 1000L
            if (playedSec >= 25) {
                tasteManager.recordTrackPlayed(current)
            }
        }
        var prevIndex = if (currentIndex - 1 >= 0) {
            currentIndex - 1
        } else {
            playlistQueue.size - 1
        }
        if (settingsManager.offlineMode.value && !offlineManager.isDownloaded(playlistQueue[prevIndex].id)) {
            val downloaded = playlistQueue.indices.filter { offlineManager.isDownloaded(playlistQueue[it].id) }
            prevIndex = downloaded.lastOrNull { it < currentIndex } ?: downloaded.lastOrNull() ?: return
        }
        currentIndex = prevIndex
        playTrack(playlistQueue[prevIndex])
    }

    fun toggleShuffle() {
        _isShuffle.value = !_isShuffle.value
    }

    fun toggleRepeat() {
        val next = when (_repeatMode.value) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
        _repeatMode.value = next
        mediaController?.repeatMode = if (next == Player.REPEAT_MODE_ONE) Player.REPEAT_MODE_ONE else Player.REPEAT_MODE_OFF
        if (next == Player.REPEAT_MODE_ONE) {
            _currentTrack.value?.let { tasteManager.recordTrackRepeated(it) }
        }
    }

    fun toggleFavorite(track: Track) {
        val currentFavs = _favorites.value.toMutableList()
        val exists = currentFavs.any { it.id == track.id }
        if (exists) {
            currentFavs.removeAll { it.id == track.id }
        } else {
            currentFavs.add(track.copy(isFavorite = true))
            tasteManager.recordTrackLiked(track)
        }
        _favorites.value = currentFavs
        saveStoredFavorites(currentFavs)

        if (_currentTrack.value?.id == track.id) {
            _currentTrack.value = _currentTrack.value?.copy(isFavorite = !exists)
        }
    }

    fun expandPlayer(expand: Boolean) {
        _isPlayerExpanded.value = expand
    }

    fun syncLikedSongs() {
        viewModelScope.launch {
            _isLoadingTrending.value = true
            val liked = repository.getLikedTracks()
            if (liked.isNotEmpty()) {
                val currentFavs = _favorites.value.toMutableList()
                liked.forEach { track ->
                    if (currentFavs.none { it.id == track.id }) {
                        currentFavs.add(track.copy(isFavorite = true))
                    }
                }
                _favorites.value = currentFavs
                saveStoredFavorites(currentFavs)
            }
            _isLoadingTrending.value = false
        }
    }

    fun logout() {
        com.zai.avora.data.auth.AuthManager.getInstance().logout()
    }

    override fun onCleared() {
        super.onCleared()
        try {
            getApplication<android.app.Application>().unregisterReceiver(mediaActionReceiver)
        } catch (_: Exception) {}
        stopProgressTracking()
        suggestJob?.cancel()
        controllerFuture?.let { MediaController.releaseFuture(it) }
    }
}
