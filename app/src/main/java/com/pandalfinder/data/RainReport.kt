package com.pandalfinder.data

/**
 * Real-time community rain observation for a specific Pandal.
 * Stored in Firestore under `rainReports/{reportId}`.
 */
data class RainReport(
    val reportId: String = "",
    val pandalId: String = "",
    val reportedBy: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val status: String = "active"
)
