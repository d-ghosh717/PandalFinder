package com.pandalfinder.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

data class FestivalAlert(
    val id: String,
    val title: String,
    val message: String,
    val severity: String = "INFO", // "INFO", "WARNING", "ALERT"
    val timestamp: Long = System.currentTimeMillis(),
    val expiresAt: Long? = null
)

class FestivalAlertRepository {
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    fun fetchActiveAlerts(onResult: (List<FestivalAlert>) -> Unit) {
        val now = System.currentTimeMillis()
        firestore.collection(COLLECTION)
            .get()
            .addOnSuccessListener { snapshot ->
                val alerts = snapshot.documents.mapNotNull { doc ->
                    val id = doc.id
                    val title = doc.getString("title") ?: return@mapNotNull null
                    val message = doc.getString("message") ?: return@mapNotNull null
                    val severity = doc.getString("severity") ?: "INFO"
                    val timestamp = doc.getTimestamp("timestamp")?.toDate()?.time ?: now
                    val expiresAt = doc.getTimestamp("expiresAt")?.toDate()?.time

                    if (expiresAt != null && expiresAt < now) {
                        null // Expired
                    } else {
                        FestivalAlert(id, title, message, severity, timestamp, expiresAt)
                    }
                }.sortedByDescending { it.timestamp }

                onResult(alerts)
            }
            .addOnFailureListener { error ->
                Log.d(TAG, "No festival alerts loaded: ${error.message}")
                onResult(emptyList())
            }
    }

    private companion object {
        const val TAG = "FestivalAlerts"
        const val COLLECTION = "festivalAlerts"
    }
}
