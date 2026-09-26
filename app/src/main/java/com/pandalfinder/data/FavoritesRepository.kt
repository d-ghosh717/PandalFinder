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
        return ids.contains(cleanId) || ids.contains(pandalId)
    }

    fun toggleFavorite(pandalId: String): Boolean {
        val cleanId = pandalId.removePrefix("pandal:")
        val current = getFavoriteIds().toMutableSet()
        val willBeSaved = !current.contains(cleanId)
        if (willBeSaved) {
            current.add(cleanId)
        } else {
            current.remove(cleanId)
            current.remove("pandal:$cleanId")
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
            current.remove(cleanId)
            current.remove("pandal:$cleanId")
        }
        saveIds(current)
        notifyChanged()
    }

    fun getFavoritePandals(pandalRepository: PandalRepository): List<Pandal> {
        val ids = getFavoriteIds()
        val allPandals = pandalRepository.all()
        return allPandals.filter { p -> ids.contains(p.id) || ids.contains("pandal:${p.id}") }
    }

    private fun saveIds(ids: Set<String>) {
        val array = JSONArray()
        ids.forEach { array.put(it) }
        preferences.edit().putString("saved_favorite_ids", array.toString()).apply()
    }
}
