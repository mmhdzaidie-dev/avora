package com.zai.avora.data.cache

import android.content.Context
import android.util.Log
import java.io.File

/**
 * Pembersih cache otomatis. Tidak menyentuh unduhan offline (filesDir).
 *
 * - Saat aplikasi dibuka: hapus penanda "lirik tidak ketemu", lirik kedaluwarsa/rusak,
 *   dan jaga total cache tidak membengkak.
 * - Saat aplikasi berjalan: bersihkan cache lirik berkala (lihat MusicViewModel).
 */
object CacheCleaner {

    private const val TAG = "CacheCleaner"
    private const val LYRICS_DIR = "lyrics_cache_v4"

    private const val EMPTY_MARKER_TTL_MS = 30 * 60 * 1000L
    private const val LYRICS_MAX_AGE_MS = 14L * 24 * 60 * 60 * 1000
    private const val PLAIN_LYRICS_MAX_AGE_MS = 3L * 24 * 60 * 60 * 1000
    private const val MAX_LYRICS_FILES = 400
    private const val MAX_TOTAL_CACHE_BYTES = 250L * 1024 * 1024

    /** Dipanggil sekali di Application.onCreate, SEBELUM OkHttp/Coil membuka cache-nya. */
    fun cleanOnStart(context: Context) {
        try {
            cleanLyrics(context, aggressive = true)
            enforceTotalCap(context.cacheDir)
        } catch (e: Exception) {
            Log.w(TAG, "cleanOnStart: ${e.message}")
        }
    }

    /** Aman dipanggil kapan saja saat aplikasi berjalan (hanya menyentuh cache lirik). */
    fun cleanWhileRunning(context: Context) {
        try {
            cleanLyrics(context, aggressive = false)
        } catch (e: Exception) {
            Log.w(TAG, "cleanWhileRunning: ${e.message}")
        }
    }

    private fun cleanLyrics(context: Context, aggressive: Boolean) {
        val dir = File(context.cacheDir, LYRICS_DIR)
        val files = dir.listFiles() ?: return
        val now = System.currentTimeMillis()

        for (f in files) {
            val age = now - f.lastModified()
            when {
                f.name.endsWith(".empty") -> {
                    if (aggressive || age > EMPTY_MARKER_TTL_MS) f.delete()
                }
                f.name.endsWith(".json") -> {
                    val drop = f.length() == 0L ||
                        age > LYRICS_MAX_AGE_MS ||
                        (age > PLAIN_LYRICS_MAX_AGE_MS && isPlainLyrics(f))
                    if (drop) f.delete()
                }
                else -> f.delete()
            }
        }

        val left = dir.listFiles() ?: return
        if (left.size > MAX_LYRICS_FILES) {
            left.sortedBy { it.lastModified() }
                .take(left.size - MAX_LYRICS_FILES + 50)
                .forEach { it.delete() }
        }
    }

    private fun isPlainLyrics(f: File): Boolean = try {
        f.readText().contains("\"isSynced\":false")
    } catch (_: Exception) {
        true
    }

    private fun enforceTotalCap(cacheRoot: File) {
        val all = cacheRoot.walkTopDown().filter { it.isFile }.toList()
        var total = all.sumOf { it.length() }
        if (total <= MAX_TOTAL_CACHE_BYTES) return
        for (f in all.sortedBy { it.lastModified() }) {
            if (total <= MAX_TOTAL_CACHE_BYTES * 8 / 10) break
            total -= f.length()
            f.delete()
        }
    }
}
