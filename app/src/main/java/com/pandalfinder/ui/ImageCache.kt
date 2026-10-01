package com.pandalfinder.ui

import android.graphics.Bitmap
import android.util.LruCache

/**
 * Thread-safe LRU Bitmap Cache to prevent OOM errors and large bitmap allocation spikes.
 * Allocated 1/8th of available device memory.
 */
object ImageCache {
    private val maxMemory = (Runtime.getRuntime().maxMemory() / 1024).toInt()
    private val cacheSize = (maxMemory / 8).coerceAtLeast(1024)

    private val cache = object : LruCache<String, Bitmap>(cacheSize) {
        override fun sizeOf(key: String, bitmap: Bitmap): Int {
            return (bitmap.byteCount / 1024).coerceAtLeast(1)
        }
    }

    operator fun get(key: String?): Bitmap? {
        if (key.isNullOrBlank()) return null
        synchronized(cache) {
            return cache.get(key)
        }
    }

    operator fun set(key: String?, bitmap: Bitmap?) {
        if (key.isNullOrBlank() || bitmap == null) return
        synchronized(cache) {
            cache.put(key, bitmap)
        }
    }

    fun remove(key: String?) {
        if (key.isNullOrBlank()) return
        synchronized(cache) {
            cache.remove(key)
        }
    }

    fun clear() {
        synchronized(cache) {
            cache.evictAll()
        }
    }
}
