package com.pandalfinder.data

import android.content.Context
import com.pandalfinder.Pandal
import com.pandalfinder.PandalRepository
import org.json.JSONArray
import java.util.Collections

class HoppingRepository(context: Context, private val pandalRepository: PandalRepository) {
    private val preferences = context.getSharedPreferences("pandal_hopping_plan", Context.MODE_PRIVATE)
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

    fun getPlanIds(): List<String> {
        val raw = preferences.getString("saved_stops", "[]") ?: "[]"
        return runCatching {
            val jsonArray = JSONArray(raw)
            (0 until jsonArray.length()).map { jsonArray.getString(it) }
        }.getOrDefault(emptyList())
    }

    fun getPlan(): List<Pandal> {
        val allMap = pandalRepository.all().associateBy { it.id }
        return getPlanIds().mapNotNull { id -> allMap[id] }
    }

    fun isInPlan(pandalId: String): Boolean {
        return getPlanIds().contains(pandalId)
    }

    fun add(pandal: Pandal): Boolean {
        val current = getPlanIds().toMutableList()
        if (current.contains(pandal.id)) return false
        current.add(pandal.id)
        savePlanIds(current)
        notifyChanged()
        return true
    }

    fun remove(pandalId: String): Boolean {
        val current = getPlanIds().toMutableList()
        val removed = current.remove(pandalId)
        if (removed) {
            savePlanIds(current)
            notifyChanged()
        }
        return removed
    }

    fun move(fromPosition: Int, toPosition: Int) {
        val current = getPlanIds().toMutableList()
        if (fromPosition in current.indices && toPosition in current.indices) {
            Collections.swap(current, fromPosition, toPosition)
            savePlanIds(current)
            notifyChanged()
        }
    }

    fun clear() {
        savePlanIds(emptyList())
        notifyChanged()
    }

    private fun savePlanIds(ids: List<String>) {
        val jsonArray = JSONArray()
        ids.forEach { jsonArray.put(it) }
        preferences.edit().putString("saved_stops", jsonArray.toString()).apply()
    }
}
