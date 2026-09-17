package com.pandalfinder.data

import android.content.Context
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.pandalfinder.Pandal

data class CrowdStatus(
    val level: Int?,
    val updatedAt: Long?,
    val reportCount: Int = 0
)

/** Firestore is optional until this Android app is linked to a Firebase project.
 * Only reports within [REPORT_TTL_MILLIS] are considered current. */
class CrowdRepository(context: Context) {
    private val preferences = context.getSharedPreferences("crowd_reports", Context.MODE_PRIVATE)
    private val firestore: FirebaseFirestore? = runCatching { FirebaseFirestore.getInstance() }.getOrNull()

    fun load(pandal: Pandal, onResult: (CrowdStatus?) -> Unit) {
        val since = Timestamp((System.currentTimeMillis() - REPORT_TTL_MILLIS) / 1000, 0)
        val database = firestore ?: return onResult(null)
        database.collection("reports")
            .whereEqualTo("pandalId", pandal.id)
            .whereGreaterThan("timestamp", since)
            .get()
            .addOnSuccessListener { result ->
                val reports = result.documents.mapNotNull { doc ->
                    val level = doc.getLong("crowdLevel")?.toInt()
                    val time = doc.getTimestamp("timestamp")?.toDate()?.time
                    if (level == null || level !in 1..10 || time == null) null else level to time
                }
                onResult(
                    if (reports.isEmpty()) CrowdStatus(null, null, 0)
                    else CrowdStatus(
                        reports.map { it.first }.average().toInt().coerceIn(1, 10),
                        reports.maxOf { it.second },
                        reports.size
                    )
                )
            }
            .addOnFailureListener { onResult(null) }
    }

    fun submit(pandal: Pandal, level: Int, onComplete: (Boolean) -> Unit) {
        val now = System.currentTimeMillis()
        val lastSubmit = preferences.getLong("last_${pandal.id}", 0L)
        if (now - lastSubmit < SUBMISSION_COOLDOWN_MILLIS || firestore == null) {
            onComplete(false)
            return
        }
        firestore.collection("reports").add(
            mapOf("pandalId" to pandal.id, "crowdLevel" to level, "timestamp" to FieldValue.serverTimestamp())
        ).addOnSuccessListener {
            preferences.edit().putLong("last_${pandal.id}", now).apply()
            onComplete(true)
        }.addOnFailureListener { onComplete(false) }
    }

    companion object {
        const val REPORT_TTL_MILLIS = 90 * 60 * 1000L
        private const val SUBMISSION_COOLDOWN_MILLIS = 5 * 60 * 1000L
    }
}
