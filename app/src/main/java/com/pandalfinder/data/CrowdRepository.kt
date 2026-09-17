package com.pandalfinder.data

import android.location.Location
import android.util.Log
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pandalfinder.Pandal

data class CrowdStatus(val level: Int?, val updatedAt: Long?, val reportCount: Int = 0)

class CrowdRepository {
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun load(pandal: Pandal, onResult: (CrowdStatus?, Exception?) -> Unit) {
        val cutoff = System.currentTimeMillis() - REPORT_TTL_MILLIS
        // Query by pandalId to avoid requiring a custom composite index in Firestore console.
        firestore.collection(COLLECTION)
            .whereEqualTo("pandalId", pandal.id)
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

    private companion object {
        const val TAG = "CrowdReports"
        const val COLLECTION = "crowdReports"
        const val REPORT_TTL_MILLIS = 90 * 60 * 1000L // 90 minutes
    }
}
