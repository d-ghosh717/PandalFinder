package com.pandalfinder.data

import android.content.Context
import android.content.SharedPreferences
import android.location.Location
import com.pandalfinder.Pandal
import com.pandalfinder.PandalRepository
import org.json.JSONArray
import org.json.JSONObject

data class VisitedPandalRecord(
    val pandalId: String,
    val pandalName: String,
    val area: String,
    val visitedAtTimestamp: Long
)

/**
 * Local Pandal Passport tracker for recorded physical visits.
 * Persists visited pandals, areas explored, and progress on-device.
 */
class PassportRepository(context: Context) {
    private val preferences: SharedPreferences =
        context.getSharedPreferences("pandal_passport", Context.MODE_PRIVATE)
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

    fun isVisited(pandalId: String): Boolean {
        val cleanId = pandalId.removePrefix("pandal:")
        val records = getVisitedRecords()
        if (records.any { it.pandalId == cleanId || it.pandalId == pandalId }) return true
        val slug = cleanId.substringBeforeLast('-')
        return slug.isNotBlank() && records.any {
            val rId = it.pandalId.removePrefix("pandal:")
            rId == cleanId || rId.startsWith("$slug-") || rId == slug
        }
    }

    fun markVisited(pandal: Pandal): Boolean {
        val records = getVisitedRecords().toMutableList()
        val cleanId = pandal.id.removePrefix("pandal:")
        if (isVisited(pandal.id)) return false

        records.add(
            VisitedPandalRecord(
                pandalId = cleanId,
                pandalName = pandal.name,
                area = pandal.area,
                visitedAtTimestamp = System.currentTimeMillis()
            )
        )
        saveRecords(records)
        notifyChanged()
        return true
    }

    fun removeVisited(pandalId: String): Boolean {
        val cleanId = pandalId.removePrefix("pandal:")
        val slug = cleanId.substringBeforeLast('-')
        val records = getVisitedRecords().toMutableList()
        val removed = records.removeAll {
            val rId = it.pandalId.removePrefix("pandal:")
            rId == cleanId || it.pandalId == pandalId || (slug.isNotBlank() && rId.startsWith("$slug-")) || rId == slug
        }
        if (removed) {
            saveRecords(records)
            notifyChanged()
        }
        return removed
    }

    fun getVisitedCount(): Int = getVisitedRecords().size

    fun getVisitedRecords(): List<VisitedPandalRecord> {
        val raw = preferences.getString("visited_records", "[]") ?: "[]"
        return runCatching {
            val array = JSONArray(raw)
            (0 until array.length()).mapNotNull { i ->
                val obj = array.optJSONObject(i) ?: return@mapNotNull null
                val id = obj.optString("id")
                val name = obj.optString("name")
                val area = obj.optString("area")
                val time = obj.optLong("time", 0L)
                if (id.isNotBlank()) VisitedPandalRecord(id, name, area, time) else null
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Summarizes visited counts broken down by area (e.g. South Kolkata: 12, North Kolkata: 8).
     */
    fun getAreaBreakdown(): Map<String, Int> {
        val map = mutableMapOf<String, Int>()
        getVisitedRecords().forEach { record ->
            val area = record.area.takeIf { it.isNotBlank() } ?: "Kolkata"
            map[area] = (map[area] ?: 0) + 1
        }
        return map
    }

    /**
     * Checks if user is physically within proximity (<200m) of an unvisited pandal.
     */
    fun findUnvisitedNearbyPandal(userLocation: Location, pandals: List<Pandal>): Pandal? {
        val visitedIds = getVisitedRecords().map { it.pandalId }.toSet()
        return pandals.firstOrNull { p ->
            !visitedIds.contains(p.id) && userLocation.distanceTo(Location("").apply {
                latitude = p.latitude
                longitude = p.longitude
            }) <= PROXIMITY_VISIT_RADIUS_METERS
        }
    }

    private fun saveRecords(records: List<VisitedPandalRecord>) {
        val array = JSONArray()
        records.forEach { r ->
            val obj = JSONObject().apply {
                put("id", r.pandalId)
                put("name", r.pandalName)
                put("area", r.area)
                put("time", r.visitedAtTimestamp)
            }
            array.put(obj)
        }
        preferences.edit().putString("visited_records", array.toString()).apply()
    }

    companion object {
        const val PROXIMITY_VISIT_RADIUS_METERS = 200f
    }
}
