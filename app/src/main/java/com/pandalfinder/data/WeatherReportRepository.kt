package com.pandalfinder.data

import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pandalfinder.Pandal

enum class CommunityWeather(val storedValue: String, val label: String, val iconRes: Int) {
    NO_RAIN("NO_RAIN", "No rain", com.pandalfinder.R.drawable.ic_weather_sun),
    DRIZZLE("DRIZZLE", "Drizzle", com.pandalfinder.R.drawable.ic_weather_drizzle),
    RAINING("RAINING", "Raining", com.pandalfinder.R.drawable.ic_weather_rain),
    HEAVY_RAIN("HEAVY_RAIN", "Heavy rain", com.pandalfinder.R.drawable.ic_weather_thunder);

    val emoji: String
        get() = when (this) {
            NO_RAIN -> "☀️"
            DRIZZLE -> "🌦️"
            RAINING -> "🌧️"
            HEAVY_RAIN -> "⛈️"
        }
}

data class WeatherReport(val condition: CommunityWeather, val timestamp: Long)

class WeatherReportRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {

    private fun ensureAuth(onReady: (com.google.firebase.auth.FirebaseUser?) -> Unit) {
        val user = auth.currentUser
        if (user != null) {
            onReady(user)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { result -> onReady(result.user) }
            .addOnFailureListener { e ->
                Log.e(DIAGNOSTIC_TAG, "write failed code=AUTH_FAILED: ${e.message}", e)
                onReady(null)
            }
    }

    /**
     * Submits a community weather observation if user is authenticated and within 500 meters of the pandal.
     */
    fun submitWeatherObservation(
        pandal: Pandal,
        condition: CommunityWeather,
        userLocation: Location?,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (userLocation == null) {
            onResult(false, "Location is required to update weather. Please enable Location.")
            return
        }

        val distance = RouteOptimizer.distanceBetween(
            userLocation.latitude,
            userLocation.longitude,
            pandal.latitude,
            pandal.longitude
        )

        if (distance > MAX_DISTANCE_METERS) {
            onResult(false, "Move within 500 m of this Pandal to update the current weather.")
            return
        }

        ensureAuth { user ->
            if (user == null) {
                Log.e(DIAGNOSTIC_TAG, "write failed code=UNAUTHENTICATED")
                onResult(false, "Couldn't update weather. Try again.")
                return@ensureAuth
            }

            val cleanId = pandal.id.removePrefix("pandal:")
            val report = hashMapOf<String, Any>(
                "pandalId" to cleanId,
                "reportedBy" to user.uid,
                "userId" to user.uid,
                "weatherStatus" to condition.storedValue,
                "condition" to condition.storedValue,
                "weatherCondition" to condition.storedValue,
                "status" to "active",
                "createdAt" to FieldValue.serverTimestamp(),
                "timestamp" to System.currentTimeMillis()
            )

            Log.d(DIAGNOSTIC_TAG, "Submitting weather observation for ${pandal.name} ($cleanId): ${condition.storedValue} by ${user.uid} (dist=${distance.toInt()}m)")
            firestore.collection(COLLECTION)
                .add(report)
                .addOnSuccessListener { docRef ->
                    Log.d(DIAGNOSTIC_TAG, "Weather report saved successfully id=${docRef.id}")
                    onResult(true, null)
                }
                .addOnFailureListener { error ->
                    Log.e(DIAGNOSTIC_TAG, "write failed code=${error.message}", error)
                    onResult(false, "Couldn't update weather. Try again.")
                }
        }
    }

    fun listenToWeather(
        pandalId: String,
        onUpdate: (latestReport: WeatherReport?, activeReportsCount: Int) -> Unit
    ): ListenerRegistration? {
        val cleanId = pandalId.removePrefix("pandal:")
        return runCatching {
            firestore.collection(COLLECTION)
                .whereEqualTo("pandalId", cleanId)
                .addSnapshotListener { snap, error ->
                    if (error != null) {
                        Log.e(DIAGNOSTIC_TAG, "Weather listener failed for $cleanId: ${error.message}", error)
                        return@addSnapshotListener
                    }
                    if (snap != null) {
                        val now = System.currentTimeMillis()
                        val validReports = snap.documents.mapNotNull { doc ->
                            val raw = doc.getString("weatherStatus") ?: doc.getString("condition") ?: doc.getString("weatherCondition")
                            val condition = CommunityWeather.values().firstOrNull { it.storedValue == raw }
                            val timestamp = doc.getLong("timestamp")
                                ?: (doc.getTimestamp("createdAt") ?: doc.getTimestamp("timestamp"))?.toDate()?.time
                            val status = doc.getString("status") ?: "active"
                            if (condition != null && timestamp != null && isReportValid(timestamp, now) && status == "active") {
                                WeatherReport(condition, timestamp)
                            } else null
                        }
                        val mostRecent = validReports.maxByOrNull { it.timestamp }
                        onUpdate(mostRecent, validReports.size)
                    }
                }
        }.getOrNull()
    }

    fun latest(pandal: Pandal, onResult: (WeatherReport?, Exception?) -> Unit) {
        val cleanId = pandal.id.removePrefix("pandal:")
        val now = System.currentTimeMillis()
        firestore.collection(COLLECTION)
            .whereEqualTo("pandalId", cleanId)
            .get()
            .addOnSuccessListener { snap ->
                val reports = snap.documents.mapNotNull { doc ->
                    val raw = doc.getString("weatherStatus") ?: doc.getString("condition") ?: doc.getString("weatherCondition")
                    val condition = CommunityWeather.values().firstOrNull { it.storedValue == raw }
                    val timestamp = doc.getLong("timestamp")
                        ?: (doc.getTimestamp("createdAt") ?: doc.getTimestamp("timestamp"))?.toDate()?.time
                    val status = doc.getString("status") ?: "active"
                    if (condition != null && timestamp != null && isReportValid(timestamp, now) && status == "active") {
                        WeatherReport(condition, timestamp)
                    } else null
                }
                val mostRecent = reports.maxByOrNull { it.timestamp }
                onResult(mostRecent, null)
            }
            .addOnFailureListener { error ->
                Log.e(DIAGNOSTIC_TAG, "Weather report read failed for pandal ${pandal.id}: ${error.message}", error)
                onResult(null, error)
            }
    }

    companion object {
        const val TAG = "WeatherReports"
        const val DIAGNOSTIC_TAG = "PandalQuest-Weather"
        const val COLLECTION = "weatherReports"
        const val MAX_DISTANCE_METERS = 500f
        const val WEATHER_REPORT_VALIDITY_MINUTES = 90L
        const val TTL_MILLIS = WEATHER_REPORT_VALIDITY_MINUTES * 60 * 1000L

        fun isWithinProximity(userLat: Double, userLng: Double, pandalLat: Double, pandalLng: Double): Boolean {
            val dist = RouteOptimizer.distanceBetween(userLat, userLng, pandalLat, pandalLng)
            return dist <= MAX_DISTANCE_METERS
        }

        fun isReportValid(timestamp: Long, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
            val ageMillis = currentTimeMillis - timestamp
            val ageMinutes = ageMillis / (1000 * 60)
            return ageMinutes in 0..WEATHER_REPORT_VALIDITY_MINUTES
        }
    }
}
