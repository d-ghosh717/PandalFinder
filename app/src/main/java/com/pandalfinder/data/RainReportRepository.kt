package com.pandalfinder.data

import android.location.Location
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.pandalfinder.Pandal
import java.util.UUID

class RainReportRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "RainReportRepository"
        const val COLLECTION_RAIN_REPORTS = "rainReports"
        const val MAX_DISTANCE_METERS = 500f
        const val RAIN_REPORT_VALIDITY_MINUTES = 90L

        /**
         * Pure distance calculation helper to test 500m proximity enforcement.
         */
        fun isWithinProximity(userLat: Double, userLng: Double, pandalLat: Double, pandalLng: Double): Boolean {
            val dist = RouteOptimizer.distanceBetween(userLat, userLng, pandalLat, pandalLng)
            return dist <= MAX_DISTANCE_METERS
        }

        /**
         * Checks if a report timestamp is within the validity window (90 minutes).
         */
        fun isReportValid(timestamp: Long, currentTimeMillis: Long = System.currentTimeMillis()): Boolean {
            val ageMillis = currentTimeMillis - timestamp
            val ageMinutes = ageMillis / (1000 * 60)
            return ageMinutes in 0..RAIN_REPORT_VALIDITY_MINUTES
        }
    }

    /**
     * Submits a real-time rain report if user is authenticated and within 500 meters of the pandal.
     */
    fun submitRainReport(
        pandal: Pandal,
        userLocation: Location?,
        onResult: (Boolean, String?) -> Unit
    ) {
        val user = auth.currentUser
        if (user == null) {
            onResult(false, "User not authenticated")
            return
        }

        if (userLocation == null) {
            onResult(false, "Location is required to report current rain. Please enable Location.")
            return
        }

        val distance = RouteOptimizer.distanceBetween(
            userLocation.latitude,
            userLocation.longitude,
            pandal.latitude,
            pandal.longitude
        )

        if (distance > MAX_DISTANCE_METERS) {
            onResult(false, "Move within 500 m of this Pandal to report current rain.")
            return
        }

        val reportId = UUID.randomUUID().toString()
        val data = hashMapOf(
            "reportId" to reportId,
            "pandalId" to pandal.id.ifEmpty { pandal.name },
            "reportedBy" to user.uid,
            "timestamp" to System.currentTimeMillis(),
            "status" to "active"
        )

        firestore.collection(COLLECTION_RAIN_REPORTS).document(reportId)
            .set(data)
            .addOnSuccessListener {
                Log.d(TAG, "Rain report saved for ${pandal.name} (distance: ${distance.toInt()}m)")
                onResult(true, null)
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Failed to submit rain report: ${e.message}", e)
                onResult(false, e.localizedMessage ?: "Failed to submit rain report")
            }
    }

    /**
     * Listens in real time for active rain reports for a given pandal (within the 90 minute window).
     */
    fun listenToRainReports(
        pandalId: String,
        onUpdate: (recentReportsCount: Int, latestMinutesAgo: Long?) -> Unit
    ): ListenerRegistration {
        return firestore.collection(COLLECTION_RAIN_REPORTS)
            .whereEqualTo("pandalId", pandalId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to rain reports for $pandalId: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    onUpdate(0, null)
                    return@addSnapshotListener
                }

                val now = System.currentTimeMillis()
                val validReports = snapshot.documents.mapNotNull { doc ->
                    val ts = doc.getLong("timestamp") ?: return@mapNotNull null
                    val status = doc.getString("status") ?: "active"
                    if (status == "active" && isReportValid(ts, now)) {
                        ts
                    } else null
                }

                if (validReports.isEmpty()) {
                    onUpdate(0, null)
                } else {
                    val latestTs = validReports.maxOrNull() ?: now
                    val minutesAgo = ((now - latestTs) / (1000 * 60)).coerceAtLeast(0)
                    onUpdate(validReports.size, minutesAgo)
                }
            }
    }
}
