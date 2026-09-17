package com.pandalfinder.data

import android.location.Location
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
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

    fun latest(pandal: Pandal, onResult: (WeatherReport?, Exception?) -> Unit) {
        val cutoff = System.currentTimeMillis() - TTL_MILLIS
        firestore.collection(COLLECTION)
            .whereEqualTo("pandalId", pandal.id)
            .get()
            .addOnSuccessListener { snap ->
                val reports = snap.documents.mapNotNull { doc ->
                    val raw = doc.getString("weatherCondition")
                    val condition = CommunityWeather.values().firstOrNull { it.storedValue == raw }
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time
                    if (condition != null && timestamp != null && timestamp >= cutoff) {
                        WeatherReport(condition, timestamp)
                    } else null
                }
                val mostRecent = reports.maxByOrNull { it.timestamp }
                onResult(mostRecent, null)
            }
            .addOnFailureListener { error ->
                Log.e(TAG, "Weather report read failed for pandal ${pandal.id}", error)
                onResult(null, error)
            }
    }

    fun submit(
        pandal: Pandal,
        condition: CommunityWeather,
        deviceId: String,
        location: Location?,
        onComplete: (Exception?) -> Unit
    ) {
        val report = hashMapOf<String, Any>(
            "pandalId" to pandal.id,
            "weatherCondition" to condition.storedValue,
            "deviceId" to deviceId,
            "timestamp" to FieldValue.serverTimestamp()
        )
        location?.let {
            report["latitude"] = it.latitude
            report["longitude"] = it.longitude
        }

        Log.d(TAG, "Submitting weather report for ${pandal.name} (${pandal.id}): ${condition.storedValue}")
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
