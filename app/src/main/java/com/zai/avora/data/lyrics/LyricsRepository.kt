package com.zai.avora.data.lyrics

import android.content.Context
import android.util.Log
import android.util.LruCache
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class LyricsRepository(private val context: Context) {

    private val httpClient = com.zai.avora.data.remote.HttpClientProvider.client

    private val memCache = LruCache<String, LyricsData>(100)
    private val negativeCache = LruCache<String, Long>(200)
    private val NEGATIVE_CACHE_TTL = 30 * 60 * 1000L
    private val diskCacheDir = File(context.cacheDir, "lyrics_cache_v4").apply { mkdirs() }

    suspend fun getLyrics(
        trackId: String,
        title: String,
        artist: String,
        durationSeconds: Long = 0L,
        autoTranslate: Boolean = true,
        targetLang: String = TranslationLanguages.FALLBACK,
        preferSynced: Boolean = false
    ): LyricsData? = withContext(Dispatchers.IO) {
        val negKey = "${trackId}_$durationSeconds"
        var cachedFallback: LyricsData? = null

        synchronized(memCache) {
            memCache.get(trackId)?.let {
                if (it.isSynced || !preferSynced) return@withContext it
                cachedFallback = it
            }
        }

        val diskFile = File(diskCacheDir, "$trackId.json")
        if (diskFile.exists()) {
            try {
                val cached = parseLyricsJson(diskFile.readText())
                if (cached != null) {
                    if (cached.isSynced || !preferSynced) {
                        synchronized(memCache) { memCache.put(trackId, cached) }
                        return@withContext cached
                    }
                    if (cachedFallback == null) cachedFallback = cached
                } else {
                    diskFile.delete()
                }
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Disk cache read error: ${e.message}")
                diskFile.delete()
            }
        }

        synchronized(negativeCache) {
            val emptyAt = negativeCache.get(negKey)
            if (emptyAt != null && System.currentTimeMillis() - emptyAt < NEGATIVE_CACHE_TTL) {
                return@withContext cachedFallback
            }
        }

        val emptyMarker = File(diskCacheDir, "$negKey.empty")
        if (emptyMarker.exists() && System.currentTimeMillis() - emptyMarker.lastModified() < NEGATIVE_CACHE_TTL) {
            synchronized(negativeCache) { negativeCache.put(negKey, emptyMarker.lastModified()) }
            return@withContext cachedFallback
        }

        val cleanTitle = cleanSearchTerm(title)
        val cleanArtist = cleanSearchTerm(artist)

        val failures = java.util.concurrent.atomic.AtomicInteger(0)

        var lyrics = fetchFromLrcLib(trackId, title, artist, cleanTitle, cleanArtist, durationSeconds, failures)

        if (lyrics == null) {
            lyrics = fetchFromYouTubeMusic(trackId, title, artist, durationSeconds, failures)
        }

        if (lyrics != null && autoTranslate && lyrics.lines.isNotEmpty()) {
            val translatedLines = translateLines(lyrics.lines, targetLang)
            if (translatedLines != null) {
                lyrics = lyrics.copy(lines = translatedLines, translationLang = targetLang)
            }
        }

        if (lyrics != null) {
            synchronized(memCache) { memCache.put(trackId, lyrics) }
            try {
                pruneDiskCacheIfNeeded()
                diskFile.writeText(serializeLyricsJson(lyrics))
                if (emptyMarker.exists()) emptyMarker.delete()
            } catch (e: Exception) {
                Log.d("LyricsRepository", "Disk cache save error: ${e.message}")
            }
        } else if (failures.get() == 0 && cachedFallback == null) {
            synchronized(negativeCache) { negativeCache.put(negKey, System.currentTimeMillis()) }
            try {
                emptyMarker.createNewFile()
            } catch (_: Exception) {}
        }

        lyrics ?: cachedFallback
    }

    /** Lupakan catatan "lirik tidak ketemu" untuk lagu ini supaya bisa dicoba ulang. */
    fun forgetMisses(trackId: String) {
        try {
            synchronized(negativeCache) {
                negativeCache.snapshot().keys.filter { it.startsWith("${trackId}_") }
                    .forEach { negativeCache.remove(it) }
            }
            diskCacheDir.listFiles { f -> f.name.startsWith("${trackId}_") && f.name.endsWith(".empty") }
                ?.forEach { it.delete() }
        } catch (_: Exception) {}
    }

    private fun pruneDiskCacheIfNeeded() {
        try {
            val files = diskCacheDir.listFiles() ?: return
            if (files.size > 500) {
                files.sortedBy { it.lastModified() }.take(files.size - 400).forEach { it.delete() }
            }
        } catch (_: Exception) {}
    }

    private fun cleanSearchTerm(term: String): String {
        return term
            .replace(Regex("""[(\[]\s*(?:feat|ft|featuring|with)\b[^)\]]*[)\]]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\((?:official|audio|video|lyric|lyrics|remastered|remaster|hd|4k|hq|visualizer|mv).*?\)""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\[(?:official|audio|video|lyric|lyrics|remastered|remaster|hd|4k|hq|visualizer|mv).*?\]""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""\b(?:feat|ft|featuring)\b\.?\s.*""", RegexOption.IGNORE_CASE), "")
            .replace(Regex("""[(\[]\s*[)\]]"""), "")
            .replace(Regex("""\s*[(\[]\s*$"""), "")
            .replace(Regex("""\s+"""), " ")
            .trim()
    }

    private fun fetchFromLrcLib(
        trackId: String,
        origTitle: String,
        origArtist: String,
        cleanTitle: String,
        cleanArtist: String,
        durationSeconds: Long,
        failures: java.util.concurrent.atomic.AtomicInteger
    ): LyricsData? {
        var plainFallback: LyricsData? = null
        try {
            if (durationSeconds > 0) {
                val exactUrl = "https://lrclib.net/api/get?track_name=${encode(cleanTitle)}&artist_name=${encode(cleanArtist)}&duration=$durationSeconds"
                val exactReq = Request.Builder()
                    .url(exactUrl)
                    .header("User-Agent", "AvoraMusic/2.4.0 (Android)")
                    .build()

                val exactResp = httpClient.newCall(exactReq).execute()
                if (!exactResp.isSuccessful) {
                    if (exactResp.code != 404) failures.incrementAndGet()
                    exactResp.close()
                }
                if (exactResp.isSuccessful) {
                    val json = JSONObject(exactResp.body?.string().orEmpty())
                    val synced = json.optString("syncedLyrics")
                    val plain = json.optString("plainLyrics")

                    if (synced.isNotBlank()) {
                        val lines = LrcParser.parse(synced)
                        if (lines.isNotEmpty()) {
                            return LyricsData(trackId, origTitle, origArtist, lines, isSynced = true)
                        }
                    } else if (plain.isNotBlank()) {
                        val lines = LrcParser.parsePlain(plain, durationSeconds * 1000L)
                        plainFallback = LyricsData(trackId, origTitle, origArtist, lines, isSynced = false)
                    }
                }
            }

            val targetedUrl = "https://lrclib.net/api/search?track_name=${encode(cleanTitle)}&artist_name=${encode(cleanArtist)}"
            val targetedReq = Request.Builder()
                .url(targetedUrl)
                .header("User-Agent", "AvoraMusic/2.4.0 (Android)")
                .build()

            val targetedResp = httpClient.newCall(targetedReq).execute()
            if (!targetedResp.isSuccessful) {
                if (targetedResp.code != 404) failures.incrementAndGet()
                targetedResp.close()
            }
            if (targetedResp.isSuccessful) {
                val arr = JSONArray(targetedResp.body?.string().orEmpty())
                val candidates = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) {
                    candidates.add(arr.getJSONObject(i))
                }

                val syncedCandidates = candidates.filter { it.optString("syncedLyrics").isNotBlank() }
                if (syncedCandidates.isNotEmpty()) {
                    val best = if (durationSeconds > 0) {
                        val closeMatch = syncedCandidates.filter {
                            val dur = it.optDouble("duration", 0.0)
                            kotlin.math.abs(dur - durationSeconds) <= 2.5
                        }.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }

                        closeMatch ?: syncedCandidates.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }
                    } else {
                        syncedCandidates.firstOrNull()
                    }

                    if (best != null) {
                        val lines = LrcParser.parse(best.getString("syncedLyrics"))
                        if (lines.isNotEmpty()) {
                            return LyricsData(trackId, origTitle, origArtist, lines, isSynced = true)
                        }
                    }
                }

                val plainCandidates = candidates.filter { it.optString("plainLyrics").isNotBlank() }
                if (plainCandidates.isNotEmpty()) {
                    val bestPlain = if (durationSeconds > 0) {
                        plainCandidates.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }
                    } else plainCandidates.firstOrNull()

                    if (bestPlain != null) {
                        val lines = LrcParser.parsePlain(bestPlain.getString("plainLyrics"))
                        return LyricsData(trackId, origTitle, origArtist, lines, isSynced = false)
                    }
                }
            }

            val generalUrl = "https://lrclib.net/api/search?q=${encode("$cleanTitle $cleanArtist")}"
            val generalReq = Request.Builder()
                .url(generalUrl)
                .header("User-Agent", "AvoraMusic/2.5.0 (Android)")
                .build()

            val generalResp = httpClient.newCall(generalReq).execute()
            if (!generalResp.isSuccessful) {
                if (generalResp.code != 404) failures.incrementAndGet()
                generalResp.close()
            }
            if (generalResp.isSuccessful) {
                val arr = JSONArray(generalResp.body?.string().orEmpty())
                val candidates = mutableListOf<JSONObject>()
                for (i in 0 until arr.length()) {
                    candidates.add(arr.getJSONObject(i))
                }
                val syncedCandidates = candidates.filter { it.optString("syncedLyrics").isNotBlank() }
                if (syncedCandidates.isNotEmpty()) {
                    val best = if (durationSeconds > 0) {
                        val closeMatch = syncedCandidates.filter {
                            val dur = it.optDouble("duration", 0.0)
                            kotlin.math.abs(dur - durationSeconds) <= 2.5
                        }.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }
                        closeMatch ?: syncedCandidates.minByOrNull { kotlin.math.abs(it.optDouble("duration", 0.0) - durationSeconds) }
                    } else {
                        syncedCandidates.firstOrNull()
                    }

                    if (best != null) {
                        val lines = LrcParser.parse(best.getString("syncedLyrics"))
                        if (lines.isNotEmpty()) {
                            return LyricsData(trackId, origTitle, origArtist, lines, isSynced = true)
                        }
                    }
                }
            }
        } catch (e: Exception) {
            failures.incrementAndGet()
            Log.e("LyricsRepository", "LRCLIB fetch error: ${e.message}")
        }
        return plainFallback
    }

    private fun fetchFromYouTubeMusic(
        trackId: String,
        origTitle: String,
        origArtist: String,
        durationSeconds: Long,
        failures: java.util.concurrent.atomic.AtomicInteger
    ): LyricsData? {
        try {
            val nextPayload = JSONObject().apply {
                put("context", JSONObject().apply {
                    put("client", JSONObject().apply {
                        put("clientName", "WEB_REMIX")
                        put("clientVersion", "1.20240101.01.00")
                        put("hl", "en")
                        put("gl", "US")
                    })
                })
                put("videoId", trackId)
            }

            val nextReq = Request.Builder()
                .url("https://music.youtube.com/youtubei/v1/next")
                .header("Content-Type", "application/json")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                .header("Origin", "https://music.youtube.com")
                .header("Referer", "https://music.youtube.com/")
                .header("X-YouTube-Client-Name", "67")
                .header("X-YouTube-Client-Version", "1.20240101.01.00")
                .post(nextPayload.toString().toRequestBody("application/json".toMediaType()))
                .build()

            val nextResp = httpClient.newCall(nextReq).execute()
            if (!nextResp.isSuccessful) {
                failures.incrementAndGet()
                nextResp.close()
                return null
            }
            val nextJson = JSONObject(nextResp.body?.string().orEmpty())
            val tabs = nextJson.optJSONObject("contents")
                ?.optJSONObject("singleColumnMusicWatchNextResultsRenderer")
                ?.optJSONObject("tabbedRenderer")
                ?.optJSONObject("watchNextTabbedResultsRenderer")
                ?.optJSONArray("tabs") ?: return null

            var browseId: String? = null
            for (i in 0 until tabs.length()) {
                val tab = tabs.optJSONObject(i)?.optJSONObject("tabRenderer")
                val title = tab?.optString("title", "")
                if (title.equals("Lyrics", ignoreCase = true)) {
                    browseId = tab?.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("browseId")
                    break
                }
            }

            if (!browseId.isNullOrBlank()) {
                val browsePayload = JSONObject().apply {
                    put("context", JSONObject().apply {
                        put("client", JSONObject().apply {
                            put("clientName", "WEB_REMIX")
                            put("clientVersion", "1.20240101.01.00")
                            put("hl", "en")
                            put("gl", "US")
                        })
                    })
                    put("browseId", browseId)
                }

                val browseReq = Request.Builder()
                    .url("https://music.youtube.com/youtubei/v1/browse")
                    .header("Content-Type", "application/json")
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                    .header("Origin", "https://music.youtube.com")
                    .header("Referer", "https://music.youtube.com/")
                    .header("X-YouTube-Client-Name", "67")
                    .header("X-YouTube-Client-Version", "1.20240101.01.00")
                    .post(browsePayload.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                val browseResp = httpClient.newCall(browseReq).execute()
                if (!browseResp.isSuccessful) {
                    failures.incrementAndGet()
                    browseResp.close()
                }
                if (browseResp.isSuccessful) {
                    val browseJson = JSONObject(browseResp.body?.string().orEmpty())
                    val contents = browseJson.optJSONObject("contents")
                        ?.optJSONObject("sectionListRenderer")
                        ?.optJSONArray("contents")
                    val shelf = contents?.optJSONObject(0)?.optJSONObject("musicDescriptionShelfRenderer")
                    val runs = shelf?.optJSONObject("description")?.optJSONArray("runs")
                    if (runs != null && runs.length() > 0) {
                        val sb = StringBuilder()
                        for (j in 0 until runs.length()) {
                            sb.append(runs.optJSONObject(j)?.optString("text", ""))
                        }
                        val rawText = sb.toString().trim()
                        if (isValidLyricsText(rawText)) {
                            val lines = LrcParser.parsePlain(rawText)
                            if (isValidLyricsLines(lines)) {
                                return LyricsData(trackId, origTitle, origArtist, lines, isSynced = false)
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            failures.incrementAndGet()
            Log.d("LyricsRepository", "YTM Lyrics fetch error: ${e.message}")
        }
        return null
    }

    private fun isValidLyricsText(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.length < 25) return false
        val lower = trimmed.lowercase()
        if (lower == "null" || lower == "cancel" || lower == "batal") return false
        val lines = lower.lines().map { it.trim() }.filter { it.isNotBlank() }
        if (lines.all { it in listOf("null", "cancel", "batal", "ok", "error") }) return false
        return true
    }

    fun isValidLyricsLines(lines: List<LyricLine>): Boolean {
        if (lines.isEmpty()) return false
        val nonBlank = lines.filter { it.text.isNotBlank() }
        if (nonBlank.isEmpty()) return false
        if (nonBlank.size <= 2) {
            val combined = nonBlank.joinToString(" ") { it.text.lowercase().trim() }
            if (combined.contains("null") || combined.contains("batal") || combined.contains("cancel")) {
                return false
            }
        }
        return true
    }

    suspend fun translateLyrics(
        lyrics: LyricsData,
        targetLang: String = TranslationLanguages.FALLBACK
    ): LyricsData = withContext(Dispatchers.IO) {
        if (lyrics.lines.isEmpty()) return@withContext lyrics
        val translatedLines = translateLines(lyrics.lines, targetLang) ?: return@withContext lyrics
        val result = lyrics.copy(lines = translatedLines, translationLang = targetLang)
        synchronized(memCache) { memCache.put(lyrics.trackId, result) }
        try {
            val diskFile = File(diskCacheDir, "${lyrics.trackId}.json")
            diskFile.writeText(serializeLyricsJson(result))
        } catch (_: Exception) {}
        result
    }

    private fun translateLines(lines: List<LyricLine>, targetLang: String): List<LyricLine>? {
        if (lines.isEmpty()) return lines
        try {
            val textToTranslate = lines.joinToString("\n~~~\n") { it.text }
            val formBody = ("client=gtx&sl=auto&tl=" + encode(targetLang) + "&dt=t&q=" + encode(textToTranslate))
                .toRequestBody("application/x-www-form-urlencoded".toMediaType())

            val req = Request.Builder()
                .url("https://translate.googleapis.com/translate_a/single")
                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64)")
                .post(formBody)
                .build()

            val resp = httpClient.newCall(req).execute()
            if (resp.isSuccessful) {
                val jsonArr = JSONArray(resp.body?.string().orEmpty())
                val segments = jsonArr.optJSONArray(0)
                if (segments != null) {
                    val sb = StringBuilder()
                    for (i in 0 until segments.length()) {
                        val seg = segments.optJSONArray(i)
                        if (seg != null) {
                            sb.append(seg.optString(0))
                        }
                    }
                    val fullTranslated = sb.toString()
                    val translatedBlocks = if (fullTranslated.contains("~~~")) {
                        fullTranslated.split("~~~").map { it.trim() }
                    } else {
                        fullTranslated.lines().map { it.trim() }
                    }

                    return lines.mapIndexed { idx, line ->
                        val trans = translatedBlocks.getOrNull(idx)?.trim()
                        val validTrans = if (!trans.isNullOrBlank() && !trans.equals(line.text.trim(), ignoreCase = true) && !trans.equals("~~~")) trans else null
                        line.copy(translation = validTrans)
                    }
                }
            }
        } catch (e: Exception) {
            Log.d("LyricsRepository", "Translation error: ${e.message}")
        }
        return null
    }

    private fun encode(str: String): String = URLEncoder.encode(str, "UTF-8")

    private fun serializeLyricsJson(data: LyricsData): String {
        val root = JSONObject().apply {
            put("trackId", data.trackId)
            put("title", data.title)
            put("artist", data.artist)
            put("isSynced", data.isSynced)
            data.translationLang?.let { put("translationLang", it) }
            val linesArr = JSONArray()
            data.lines.forEach { line ->
                val lineObj = JSONObject().apply {
                    put("timeMs", line.timeMs)
                    put("text", line.text)
                    if (line.translation != null) put("translation", line.translation)
                }
                linesArr.put(lineObj)
            }
            put("lines", linesArr)
        }
        return root.toString()
    }

    private fun parseLyricsJson(jsonStr: String): LyricsData? {
        return try {
            val root = JSONObject(jsonStr)
            val trackId = root.getString("trackId")
            val title = root.getString("title")
            val artist = root.getString("artist")
            val isSynced = root.optBoolean("isSynced", true)
            val linesArr = root.getJSONArray("lines")
            val lines = mutableListOf<LyricLine>()
            for (i in 0 until linesArr.length()) {
                val obj = linesArr.getJSONObject(i)
                lines.add(
                    LyricLine(
                        timeMs = obj.getLong("timeMs"),
                        text = obj.getString("text"),
                        translation = if (obj.has("translation")) obj.getString("translation") else null
                    )
                )
            }
            val translationLang = if (root.has("translationLang")) root.getString("translationLang")
            else if (lines.any { it.translation != null }) "id" else null
            LyricsData(trackId, title, artist, lines, isSynced, translationLang)
        } catch (e: Exception) {
            null
        }
    }
}
