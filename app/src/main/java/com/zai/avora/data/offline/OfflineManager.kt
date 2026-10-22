package com.zai.avora.data.offline

import android.content.Context
import com.zai.avora.data.model.Track
import com.zai.avora.data.remote.HttpClientProvider
import com.zai.avora.data.remote.MusicRepository
import com.zai.avora.data.settings.AudioQuality
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit

data class OfflineTrack(
    val track: Track,
    val audioFile: String,
    val artFile: String?,
    val mimeType: String?,
    val sizeBytes: Long,
    val downloadedAt: Long
)

class OfflineManager private constructor(context: Context) {

    private val appContext = context.applicationContext
    private val prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val audioDir = File(appContext.filesDir, "offline_audio").apply { mkdirs() }
    private val artDir = File(appContext.filesDir, "offline_art").apply { mkdirs() }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val gate = Semaphore(2)
    private val jobs = ConcurrentHashMap<String, Job>()
    private val repository = MusicRepository()

    private val _items = MutableStateFlow<List<OfflineTrack>>(emptyList())
    val items: StateFlow<List<OfflineTrack>> = _items.asStateFlow()

    private val _ids = MutableStateFlow<Set<String>>(emptySet())
    val ids: StateFlow<Set<String>> = _ids.asStateFlow()

    private val _progress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val progress: StateFlow<Map<String, Float>> = _progress.asStateFlow()

    private val _events = MutableSharedFlow<String>(extraBufferCapacity = 8)
    val events: SharedFlow<String> = _events.asSharedFlow()

    init {
        audioDir.listFiles()?.filter { it.name.endsWith(".part") }?.forEach { it.delete() }
        load()
    }

    fun isDownloaded(trackId: String): Boolean = trackId in _ids.value

    fun localAudio(trackId: String): File? {
        val item = _items.value.firstOrNull { it.track.id == trackId } ?: return null
        val f = File(audioDir, item.audioFile)
        return if (f.exists() && f.length() > 0) f else null
    }

    fun mimeOf(trackId: String): String? = _items.value.firstOrNull { it.track.id == trackId }?.mimeType

    fun localArtUri(trackId: String): String? {
        val name = _items.value.firstOrNull { it.track.id == trackId }?.artFile ?: return null
        val f = File(artDir, name)
        return if (f.exists()) "file://${f.absolutePath}" else null
    }

    fun artBytes(trackId: String): ByteArray? {
        val name = _items.value.firstOrNull { it.track.id == trackId }?.artFile ?: return null
        val f = File(artDir, name)
        return try { if (f.exists()) f.readBytes() else null } catch (_: Exception) { null }
    }

    fun totalBytes(): Long = _items.value.sumOf { it.sizeBytes }

    fun download(track: Track, quality: AudioQuality) {
        if (isDownloaded(track.id) || jobs.containsKey(track.id)) return
        setProgress(track.id, 0f)
        val job = scope.launch(start = kotlinx.coroutines.CoroutineStart.LAZY) {
            try {
                gate.withPermit { doDownload(track, quality) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _events.tryEmit("Gagal mengunduh \"${track.title}\"")
            } finally {
                jobs.remove(track.id)
                clearProgress(track.id)
            }
        }
        jobs[track.id] = job
        job.start()
    }

    fun downloadAll(tracks: List<Track>, quality: AudioQuality) {
        val todo = tracks.filter { !isDownloaded(it.id) && !jobs.containsKey(it.id) }
        todo.forEach { download(it, quality) }
    }

    fun cancel(trackId: String) {
        jobs.remove(trackId)?.cancel()
        clearProgress(trackId)
        File(audioDir, "${safe(trackId)}.part").delete()
    }

    fun remove(trackId: String) {
        cancel(trackId)
        val item = _items.value.firstOrNull { it.track.id == trackId } ?: return
        File(audioDir, item.audioFile).delete()
        item.artFile?.let { File(artDir, it).delete() }
        publish(_items.value.filter { it.track.id != trackId })
    }

    fun removeAll() {
        jobs.keys.toList().forEach { cancel(it) }
        audioDir.listFiles()?.forEach { it.delete() }
        artDir.listFiles()?.forEach { it.delete() }
        publish(emptyList())
    }

    private suspend fun doDownload(track: Track, quality: AudioQuality) {
        val base = safe(track.id)
        val part = File(audioDir, "$base.part")
        part.delete()

        var stream = repository.getAudioStream(track.url, quality)
            ?: throw IOException("Stream tidak ditemukan")
        var mime = stream.mimeType
        var triedFallback = false

        while (true) {
            try {
                fetchToFile(stream.url, part) { setProgress(track.id, it) }
                break
            } catch (e: HttpStatus) {
                if (!triedFallback && (e.code == 403 || e.code == 410)) {
                    triedFallback = true
                    val fb = repository.getFallbackStreamUrl(track.url) ?: throw e
                    stream = com.zai.avora.data.remote.ResolvedStream(fb, "audio/webm")
                    mime = "audio/webm"
                    part.delete()
                } else throw e
            }
        }

        val isWebm = mime?.contains("webm") == true || stream.url.contains("audio%2Fwebm") || stream.url.contains("audio/webm")
        val audioName = base + if (isWebm) ".webm" else ".m4a"
        val finalFile = File(audioDir, audioName)
        finalFile.delete()
        if (!part.renameTo(finalFile)) {
            part.delete()
            throw IOException("Gagal menyimpan file")
        }

        val artName = saveArt(track, base)
        val entry = OfflineTrack(
            track = track.copy(streamUrl = null, mimeType = null, isFavorite = false),
            audioFile = audioName,
            artFile = artName,
            mimeType = if (isWebm) "audio/webm" else (mime ?: "audio/mp4"),
            sizeBytes = finalFile.length() + (artName?.let { File(artDir, it).length() } ?: 0L),
            downloadedAt = System.currentTimeMillis()
        )
        publish(listOf(entry) + _items.value.filter { it.track.id != track.id })
    }

    private class HttpStatus(val code: Int) : IOException("HTTP $code")

    private suspend fun fetchToFile(url: String, dest: File, onProgress: (Float) -> Unit) {
        val client = HttpClientProvider.client.newBuilder()
            .readTimeout(30, TimeUnit.SECONDS)
            .build()
        var offset = 0L
        var total = -1L
        var finished = false

        FileOutputStream(dest).use { out ->
            val buf = ByteArray(32 * 1024)
            while (!finished) {
                currentCoroutineContext().ensureActive()
                val req = Request.Builder()
                    .url(url)
                    .header("User-Agent", USER_AGENT)
                    .header("Range", "bytes=$offset-${offset + CHUNK - 1}")
                    .build()
                client.newCall(req).execute().use { resp ->
                    val body = resp.body ?: throw IOException("Respon kosong")
                    when (resp.code) {
                        200 -> {
                            if (offset != 0L) throw IOException("Server mengabaikan Range")
                            total = body.contentLength()
                            var written = 0L
                            body.byteStream().use { input ->
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    written += n
                                    if (total > 0) onProgress((written.toFloat() / total).coerceIn(0f, 1f))
                                }
                            }
                            offset = written
                            finished = true
                        }
                        206 -> {
                            total = resp.header("Content-Range")
                                ?.substringAfter('/', "")
                                ?.toLongOrNull() ?: total
                            var written = 0L
                            body.byteStream().use { input ->
                                while (true) {
                                    currentCoroutineContext().ensureActive()
                                    val n = input.read(buf)
                                    if (n < 0) break
                                    out.write(buf, 0, n)
                                    written += n
                                }
                            }
                            offset += written
                            if (total > 0) onProgress((offset.toFloat() / total).coerceIn(0f, 1f))
                            if (written == 0L || (total in 1..offset) || (total <= 0 && written < CHUNK)) {
                                finished = true
                            }
                        }
                        416 -> finished = true
                        else -> throw HttpStatus(resp.code)
                    }
                    Unit
                }
            }
        }
        if (offset <= 0L) throw IOException("File kosong")
    }

    private fun saveArt(track: Track, base: String): String? {
        return try {
            val url = track.highResThumbnailUrl.takeIf { it.startsWith("http") } ?: return null
            val req = Request.Builder().url(url).header("User-Agent", USER_AGENT).build()
            HttpClientProvider.client.newCall(req).execute().use { resp ->
                if (!resp.isSuccessful) return null
                val bytes = resp.body?.bytes() ?: return null
                if (bytes.isEmpty()) return null
                val name = "$base.img"
                File(artDir, name).writeBytes(bytes)
                name
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun load() {
        val raw = prefs.getString(KEY_ITEMS, "[]") ?: "[]"
        val list = mutableListOf<OfflineTrack>()
        try {
            val arr = JSONArray(raw)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val audio = o.optString("audioFile")
                val f = File(audioDir, audio)
                if (audio.isBlank() || !f.exists()) continue
                list.add(
                    OfflineTrack(
                        track = Track(
                            id = o.optString("id"),
                            url = o.optString("url"),
                            title = o.optString("title"),
                            artist = o.optString("artist"),
                            thumbnailUrl = o.optString("thumbnailUrl"),
                            durationSeconds = o.optLong("durationSeconds", 0L)
                        ),
                        audioFile = audio,
                        artFile = o.optString("artFile").takeIf { it.isNotBlank() },
                        mimeType = o.optString("mimeType").takeIf { it.isNotBlank() },
                        sizeBytes = o.optLong("sizeBytes", f.length()),
                        downloadedAt = o.optLong("downloadedAt", 0L)
                    )
                )
            }
        } catch (_: Exception) {
        }
        _items.value = list
        _ids.value = list.mapTo(HashSet()) { it.track.id }
    }

    private fun publish(list: List<OfflineTrack>) {
        _items.value = list
        _ids.value = list.mapTo(HashSet()) { it.track.id }
        val arr = JSONArray()
        list.forEach { it0 ->
            arr.put(JSONObject().apply {
                put("id", it0.track.id)
                put("url", it0.track.url)
                put("title", it0.track.title)
                put("artist", it0.track.artist)
                put("thumbnailUrl", it0.track.thumbnailUrl)
                put("durationSeconds", it0.track.durationSeconds)
                put("audioFile", it0.audioFile)
                put("artFile", it0.artFile ?: "")
                put("mimeType", it0.mimeType ?: "")
                put("sizeBytes", it0.sizeBytes)
                put("downloadedAt", it0.downloadedAt)
            })
        }
        prefs.edit().putString(KEY_ITEMS, arr.toString()).apply()
    }

    private fun setProgress(id: String, p: Float) {
        _progress.value = _progress.value + (id to p)
    }

    private fun clearProgress(id: String) {
        _progress.value = _progress.value - id
    }

    private fun safe(id: String) = id.replace(Regex("[^A-Za-z0-9_-]"), "_")

    companion object {
        private const val PREF_NAME = "avora_offline"
        private const val KEY_ITEMS = "items"
        private const val CHUNK = 2L * 1024 * 1024
        private const val USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:128.0) Gecko/20100101 Firefox/128.0"

        @Volatile
        private var instance: OfflineManager? = null

        fun init(context: Context): OfflineManager =
            instance ?: synchronized(this) {
                instance ?: OfflineManager(context.applicationContext).also { instance = it }
            }

        fun getInstance(): OfflineManager =
            checkNotNull(instance) { "OfflineManager must be initialized before use" }
    }
}
