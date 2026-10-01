package com.pandalfinder.data

import android.location.Location
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pandalfinder.Pandal

enum class CommunityWeather(val storedValue: String, val label: String, val emoji: String) {
    NO_RAIN("NO_RAIN", "No rain", "☀️"),
    DRIZZLE("DRIZZLE", "Drizzle", "🌦️"),
    RAINING("RAINING", "Raining", "🌧️"),
    HEAVY_RAIN("HEAVY_RAIN", "Heavy rain", "⛈️")
}

data class WeatherReport(val condition: CommunityWeather, val timestamp: Long)

class WeatherReportRepository {
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun listenToWeather(pandalId: String, onUpdate: (WeatherReport?) -> Unit): ListenerRegistration? {
        val cleanId = pandalId.removePrefix("pandal:")
        val cutoff = System.currentTimeMillis() - TTL_MILLIS
        return runCatching {
            firestore.collection(COLLECTION)
                .whereEqualTo("pandalId", cleanId)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        Log.e(TAG, "Weather listener failed for $cleanId: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (snap != null) {
                        val reports = snap.documents.mapNotNull { doc ->
                            val raw = doc.getString("condition") ?: doc.getString("weatherCondition")
                            val condition = CommunityWeather.values().firstOrNull { it.storedValue == raw }
                            val timestamp = (doc.getTimestamp("createdAt") ?: doc.getTimestamp("timestamp"))?.toDate()?.time
                            val status = doc.getString("status") ?: "active"
                            if (condition != null && timestamp != null && timestamp >= cutoff && status == "active") {
                                WeatherReport(condition, timestamp)
                            } else null
                        }
                        val mostRecent = reports.maxByOrNull { it.timestamp }
                        onUpdate(mostRecent)
                    }
                }
        }.getOrNull()
    }

    fun latest(pandal: Pandal, onResult: (WeatherReport?, Exception?) -> Unit) {
        val cleanId = pandal.id.removePrefix("pandal:")
        val cutoff = System.currentTimeMillis() - TTL_MILLIS
        firestore.collection(COLLECTION)
            .whereEqualTo("pandalId", cleanId)
            .get()
            .addOnSuccessListener { snap ->
                val reports = snap.documents.mapNotNull { doc ->
                    val raw = doc.getString("condition") ?: doc.getString("weatherCondition")
                    val condition = CommunityWeather.values().firstOrNull { it.storedValue == raw }
                    val timestamp = (doc.getTimestamp("createdAt") ?: doc.getTimestamp("timestamp"))?.toDate()?.time
                    val status = doc.getString("status") ?: "active"
                    if (condition != null && timestamp != null && timestamp >= cutoff && status == "active") {
                        WeatherReport(condition, timestamp)
                    } else null
                }
                val mostRecent = reports.maxByOrNull { it.timestamp }
                onResult(mostRecent, null)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Weather report read failed for pandal ${pandal.id}: ${error.message}", error)
                onResult(null, error)
            }
    }

    fun submit(
        pandal: Pandal,
        condition: CommunityWeather,
        userId: String,
        deviceId: String,
        location: Location?,
        onComplete: (Exception?) -> Unit
    ) {
        val cleanId = pandal.id.removePrefix("pandal:")
        val report = hashMapOf<String, Any>(
            "pandalId" to cleanId,
            "condition" to condition.storedValue,
            "weatherCondition" to condition.storedValue,
            "userId" to userId,
            "deviceId" to deviceId,
            "status" to "active",
            "createdAt" to FieldValue.serverTimestamp(),
            "timestamp" to FieldValue.serverTimestamp()
        )
        location?.let {
            report["latitude"] = it.latitude
            report["longitude"] = it.longitude
        }

        Log.d(TAG, "Submitting weather report for ${pandal.name} ($cleanId): ${condition.storedValue} by $userId")
        firestore.collection(COLLECTION)
            .add(report)
            .addOnSuccessListener { docRef ->
                Log.d(TAG, "Weather report saved successfully with id ${docRef.id}")
                onComplete(null)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Weather report write failed: ${error.message}", error)
                onComplete(error)
            }
    }

    companion object {
        const val TAG = "WeatherReports"
        const val COLLECTION = "weatherReports"
        const val TTL_MILLIS = 30 * 60 * 1000L // 30 minutes
    }
}
