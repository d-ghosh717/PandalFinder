package com.pandalfinder.data

import android.location.Location
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pandalfinder.Pandal

data class CrowdStatus(val level: Int?, val updatedAt: Long?, val reportCount: Int = 0)

data class HourlyCrowdSlot(
    val hourLabel: String,
    val level: Int,
    val levelLabel: String,
    val colorHex: String
)

class CrowdRepository {
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun getCrowdLabel(level: Int?): Pair<String, String> {
        return when (level) {
            null -> "No reports yet" to "#8D6E63"
            in 1..2 -> "Empty" to "#45A701"
            in 3..4 -> "Light" to "#7CB342"
            in 5..6 -> "Moderate" to "#FBC222"
            in 7..8 -> "Very busy" to "#F97E04"
            9 -> "Extremely busy" to "#E52B00"
            10 -> "Packed" to "#B71C1C"
            else -> "Moderate" to "#FBC222"
        }
    }

    /**
     * Real-time listener for a pandal's crowd reports so updates propagate automatically.
     */
    fun listenToPandalCrowd(pandalId: String, onUpdate: (CrowdStatus) -> Unit): ListenerRegistration? {
        val cleanId = pandalId.removePrefix("pandal:")
        val cutoff = System.currentTimeMillis() - REPORT_TTL_MILLIS
        return runCatching {
            firestore.collection(COLLECTION)
                .whereEqualTo("pandalId", cleanId)
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Crowd listener error for $cleanId: ${error.message}")
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val validReports = snapshot.documents.mapNotNull { doc ->
                            val level = doc.getLong("crowdLevel")?.toInt()
                            val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time
                            if (level != null && level in 1..10 && timestamp != null && timestamp >= cutoff) {
                                level to timestamp
                            } else null
                        }

                        if (validReports.isEmpty()) {
                            onUpdate(CrowdStatus(null, null, 0))
                        } else {
                            val avgLevel = validReports.map { it.first }.average().toInt().coerceIn(1, 10)
                            val latestTime = validReports.maxOf { it.second }
                            onUpdate(CrowdStatus(avgLevel, latestTime, validReports.size))
                        }
                    }
                }
        }.getOrNull()
    }

    fun load(pandal: Pandal, onResult: (CrowdStatus?, Exception?) -> Unit) {
        val cleanId = pandal.id.removePrefix("pandal:")
        val cutoff = System.currentTimeMillis() - REPORT_TTL_MILLIS
        firestore.collection(COLLECTION)
            .whereEqualTo("pandalId", cleanId)
            .get()
            .addOnSuccessListener { snapshot ->
                val validReports = snapshot.documents.mapNotNull { doc ->
                    val level = doc.getLong("crowdLevel")?.toInt()
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time
                    if (level != null && level in 1..10 && timestamp != null && timestamp >= cutoff) {
                        level to timestamp
                    } else null
                }
                
                if (validReports.isEmpty()) {
                    onResult(CrowdStatus(null, null, 0), null)
                } else {
                    val avgLevel = validReports.map { it.first }.average().toInt().coerceIn(1, 10)
                    val latestTime = validReports.maxOf { it.second }
                    onResult(CrowdStatus(avgLevel, latestTime, validReports.size), null)
                }
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Crowd read failed for pandal ${pandal.id}", error)
                onResult(null, error)
            }
    }

    /**
     * Fetches recent crowd levels for all pandals to generate the live map crowd visualization.
     * Returns a map of pandalId -> average recent crowd level (1-10).
     */
    fun fetchAllRecentCrowds(onResult: (Map<String, Int>) -> Unit) {
        val cutoff = System.currentTimeMillis() - REPORT_TTL_MILLIS
        firestore.collection(COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                val groupMap = mutableMapOf<String, MutableList<Int>>()
                snapshot.documents.forEach { doc ->
                    val pId = doc.getString("pandalId") ?: return@forEach
                    val level = doc.getLong("crowdLevel")?.toInt() ?: return@forEach
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: 0L
                    if (level in 1..10 && timestamp >= cutoff) {
                        groupMap.getOrPut(pId) { mutableListOf() }.add(level)
                    }
                }
                val resultMap = groupMap.mapValues { (_, levels) ->
                    levels.average().toInt().coerceIn(1, 10)
                }
                onResult(resultMap)
            }
            .addOnFailureListener { error ->
                Log.d(TAG, "Crowd heatmap read skipped/failed: ${error.message}")
                onResult(emptyMap())
            }
    }

    /**
     * Analyzes historical crowd reports for a pandal grouped by hour.
     * ONLY returns predictions if at least 3 historical reports exist.
     * Never fabricates fake predictions when insufficient data is available.
     */
    fun getHistoricalCrowdByHour(pandalId: String, onResult: (List<HourlyCrowdSlot>?) -> Unit) {
        val cleanId = pandalId.removePrefix("pandal:")
        firestore.collection(COLLECTION)
            .whereEqualTo("pandalId", cleanId)
            .get()
            .addOnSuccessListener { snapshot ->
                val docs = snapshot.documents
                if (docs.size < MIN_HISTORICAL_REPORTS_THRESHOLD) {
                    onResult(null)
                    return@addOnSuccessListener
                }

                val hourGroups = mutableMapOf<Int, MutableList<Int>>()
                val cal = java.util.Calendar.getInstance()

                docs.forEach { doc ->
                    val level = doc.getLong("crowdLevel")?.toInt() ?: return@forEach
                    val date = doc.getTimestamp("timestamp")?.toDate() ?: return@forEach
                    if (level in 1..10) {
                        cal.time = date
                        val hour = cal.get(java.util.Calendar.HOUR_OF_DAY)
                        hourGroups.getOrPut(hour) { mutableListOf() }.add(level)
                    }
                }

                // Evening hours: 18 (6 PM) to 23 (11 PM)
                val eveningHours = listOf(18, 19, 20, 21, 22, 23)
                val slots = eveningHours.mapNotNull { hour ->
                    val levels = hourGroups[hour]
                    if (levels != null && levels.isNotEmpty()) {
                        val avg = levels.average().toInt().coerceIn(1, 10)
                        val (label, color) = when (avg) {
                            in 1..3 -> "Light" to "#45A701"
                            in 4..6 -> "Moderate" to "#FBC222"
                            in 7..8 -> "Busy" to "#F97E04"
                            else -> "Packed" to "#E52B00"
                        }
                        val hourStr = when (hour) {
                            12 -> "12 PM"
                            in 13..23 -> "${hour - 12} PM"
                            else -> "$hour AM"
                        }
                        HourlyCrowdSlot(hourStr, avg, label, color)
                    } else null
                }

                if (slots.size >= 2) {
                    onResult(slots)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    fun submit(
        pandal: Pandal,
        level: Int,
        deviceId: String,
        location: Location?,
        onComplete: (Exception?) -> Unit
    ) {
        if (level !in 1..10) {
            val error = IllegalArgumentException("Crowd level must be 1–10")
            Log.e(TAG, "Invalid crowd level: $level", error)
            onComplete(error)
            return
        }

        val report = hashMapOf<String, Any>(
            "pandalId" to pandal.id,
            "crowdLevel" to level,
            "deviceId" to deviceId,
            "timestamp" to FieldValue.serverTimestamp()
        )
        location?.let {
            report["latitude"] = it.latitude
            report["longitude"] = it.longitude
        }

        Log.d(TAG, "Submitting crowd report for ${pandal.name} (${pandal.id}): level $level")
        firestore.collection(COLLECTION)
            .add(report)
            .addOnSuccessListener { docRef ->
                Log.d(TAG, "Crowd report saved successfully with id ${docRef.id}")
                onComplete(null)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Crowd report write failed: ${error.message}", error)
                onComplete(error)
            }
    }

    companion object {
        const val TAG = "CrowdReports"
        const val COLLECTION = "crowdReports"
        const val REPORT_TTL_MILLIS = 90 * 60 * 1000L // 90 minutes
        const val MIN_HISTORICAL_REPORTS_THRESHOLD = 3
    }
}
