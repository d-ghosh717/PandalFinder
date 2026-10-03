package com.pandalfinder.data

import android.content.Context
import android.content.SharedPreferences
import com.pandalfinder.Pandal
import com.pandalfinder.PandalRepository
import org.json.JSONArray

/**
 * Local repository for user's saved/favorite pandals.
 * Persisted on-device via SharedPreferences.
 */
class FavoritesRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("pandal_favorites", Context.MODE_PRIVATE)
    private val listeners = mutableListOf<() -> Unit>()

    fun addListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: () -> Unit) {
        listeners.remove(listener)
    }

    private fun notifyChanged() {
        listeners.forEach { it.invoke() }
    }

    fun getFavoriteIds(): Set<String> {
        val raw = preferences.getString("saved_favorite_ids", "[]") ?: "[]"
        return runCatching {
            val jsonArray = JSONArray(raw)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }.toSet()
        }.getOrDefault(emptySet())
    }

    fun isFavorite(pandalId: String): Boolean {
        val cleanId = pandalId.removePrefix("pandal:")
        val ids = getFavoriteIds()
        if (ids.contains(cleanId) || ids.contains(pandalId)) return true
        val slug = cleanId.substringBeforeLast('-')
        return slug.isNotBlank() && ids.any {
            val s = it.removePrefix("pandal:")
            s == cleanId || s.startsWith("$slug-") || s == slug
        }
    }

    fun toggleFavorite(pandalId: String): Boolean {
        val cleanId = pandalId.removePrefix("pandal:")
        val current = getFavoriteIds().toMutableSet()
        val willBeSaved = !isFavorite(pandalId)
        if (willBeSaved) {
            current.add(cleanId)
        } else {
            val slug = cleanId.substringBeforeLast('-')
            current.removeAll {
                val s = it.removePrefix("pandal:")
                s == cleanId || (slug.isNotBlank() && s.startsWith("$slug-")) || s == slug
            }
        }
        saveIds(current)
        notifyChanged()
        return willBeSaved
    }

    fun setFavorite(pandalId: String, isFavorite: Boolean) {
        val cleanId = pandalId.removePrefix("pandal:")
        val current = getFavoriteIds().toMutableSet()
        if (isFavorite) {
            current.add(cleanId)
        } else {
            val slug = cleanId.substringBeforeLast('-')
            current.removeAll {
                val s = it.removePrefix("pandal:")
                s == cleanId || (slug.isNotBlank() && s.startsWith("$slug-")) || s == slug
            }
        }
        saveIds(current)
        notifyChanged()
    }

    fun getFavoritePandals(pandalRepository: PandalRepository): List<Pandal> {
        val ids = getFavoriteIds()
        val allPandals = pandalRepository.all()
        return allPandals.filter { p ->
            val slug = p.id.substringBeforeLast('-')
            ids.contains(p.id) || ids.contains("pandal:${p.id}") || ids.any { savedId ->
                val s = savedId.removePrefix("pandal:")
                s == p.id || (slug.isNotBlank() && s.startsWith("$slug-")) || s == slug
            }
        }
    }

    private fun saveIds(ids: Set<String>) {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        preferences.edit().putString("saved_favorite_ids", array.toString()).apply()
    }
}
