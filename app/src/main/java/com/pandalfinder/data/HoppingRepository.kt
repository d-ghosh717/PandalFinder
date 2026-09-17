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

    fun getPlan(): List<HoppingStop> {
        val pandalMap = pandalRepository.all().associateBy { it.id }
        val metroMap = MetroStation.allStations().associateBy { it.name.lowercase().replace(" ", "_") }
        val toiletMap = getSavedToilets()

        return getPlanIds().mapNotNull { rawId ->
            when {
                rawId.startsWith("pandal:") -> {
                    val pId = rawId.removePrefix("pandal:")
                    pandalMap[pId]?.let { HoppingStop.fromPandal(it) }
                }
                rawId.startsWith("metro:") -> {
                    val mKey = rawId.removePrefix("metro:")
                    metroMap[mKey]?.let { HoppingStop.fromMetro(it) }
                }
                rawId.startsWith("toilet:") -> {
                    val tId = rawId.removePrefix("toilet:")
                    toiletMap[tId]?.let { HoppingStop.fromToilet(it) }
                }
                else -> {
                    // Legacy IDs (bare pandal ID)
                    pandalMap[rawId]?.let { HoppingStop.fromPandal(it) }
                }
            }
        }
    }

    fun isPandalInPlan(pandalId: String): Boolean {
        val ids = getPlanIds()
        return ids.contains("pandal:$pandalId") || ids.contains(pandalId)
    }

    fun isMetroInPlan(station: MetroStation): Boolean {
        val key = "metro:${station.name.lowercase().replace(" ", "_")}"
        return getPlanIds().contains(key)
    }

    fun isToiletInPlan(toilet: PublicToilet): Boolean {
        val key = "toilet:${toilet.id}"
        return getPlanIds().contains(key)
    }

    fun isInPlan(id: String): Boolean {
        val ids = getPlanIds()
        return ids.contains(id) || ids.contains("pandal:$id") || ids.contains("metro:$id") || ids.contains("toilet:$id")
    }

    fun add(pandal: Pandal): Boolean {
        return addPandal(pandal)
    }

    fun addPandal(pandal: Pandal): Boolean {
        val id = "pandal:${pandal.id}"
        val current = getPlanIds().toMutableList()
        if (current.contains(id) || current.contains(pandal.id)) return false
        current.add(id)
        savePlanIds(current)
        notifyChanged()
        return true
    }

    fun addMetro(station: MetroStation): Boolean {
        val id = "metro:${station.name.lowercase().replace(" ", "_")}"
        val current = getPlanIds().toMutableList()
        if (current.contains(id)) return false
        current.add(id)
        savePlanIds(current)
        notifyChanged()
        return true
    }

    fun addToilet(toilet: PublicToilet): Boolean {
        val id = "toilet:${toilet.id}"
        val current = getPlanIds().toMutableList()
        if (current.contains(id)) return false
        saveToiletMetadata(toilet)
        current.add(id)
        savePlanIds(current)
        notifyChanged()
        return true
    }

    fun remove(stopId: String): Boolean {
        val current = getPlanIds().toMutableList()
        val removed = current.remove(stopId) || 
                      current.remove("pandal:$stopId") || 
                      current.remove("metro:$stopId") ||
                      current.remove("toilet:$stopId") ||
                      current.removeAll { it.endsWith(":$stopId") }
        if (removed) {
            savePlanIds(current)
            notifyChanged()
        }
        return removed
    }

    fun removePandal(pandalId: String): Boolean {
        val current = getPlanIds().toMutableList()
        val removed = current.remove("pandal:$pandalId") || current.remove(pandalId)
        if (removed) {
            savePlanIds(current)
            notifyChanged()
        }
        return removed
    }

    fun removeMetro(station: MetroStation): Boolean {
        val key = "metro:${station.name.lowercase().replace(" ", "_")}"
        val current = getPlanIds().toMutableList()
        val removed = current.remove(key)
        if (removed) {
            savePlanIds(current)
            notifyChanged()
        }
        return removed
    }

    fun removeToilet(toilet: PublicToilet): Boolean {
        val key = "toilet:${toilet.id}"
        val current = getPlanIds().toMutableList()
        val removed = current.remove(key)
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

    private fun saveToiletMetadata(toilet: PublicToilet) {
        val currentToilets = getSavedToilets().toMutableMap()
        currentToilets[toilet.id] = toilet
        val json = JSONArray()
        currentToilets.values.forEach { t ->
            val obj = org.json.JSONObject().apply {
                put("id", t.id)
                put("name", t.name)
                put("address", t.address)
                put("lat", t.latitude)
                put("lng", t.longitude)
            }
            json.put(obj)
        }
        preferences.edit().putString("saved_toilets_data", json.toString()).apply()
    }

    private fun getSavedToilets(): Map<String, PublicToilet> {
        val raw = preferences.getString("saved_toilets_data", "[]") ?: "[]"
        return runCatching {
            val jsonArray = JSONArray(raw)
            val map = mutableMapOf<String, PublicToilet>()
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.optJSONObject(i) ?: continue
                val id = obj.optString("id")
                val name = obj.optString("name")
                val address = obj.optString("address")
                val lat = obj.optDouble("lat", Double.NaN)
                val lng = obj.optDouble("lng", Double.NaN)
                if (id.isNotBlank() && !lat.isNaN() && !lng.isNaN()) {
                    map[id] = PublicToilet(id, name, address, lat, lng)
                }
            }
            map
        }.getOrDefault(emptyMap())
    }
}
