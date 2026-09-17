package com.pandalfinder.data

import android.content.Context
import android.location.Location
import com.pandalfinder.Pandal
import java.util.UUID

data class EligibilityResult(
    val allowed: Boolean,
    val reason: String,
    val deviceId: String
)

/**
 * Local proximity & visit validation.
 * Verifies whether the user is within 200m of the pandal or has a recorded visit within the last 45 minutes.
 * Note: Production anti-spoof enforcement would also verify coordinates/signatures on a server or Cloud Function.
 */
class ContributionEligibility(context: Context) {
    private val preferences = context.getSharedPreferences("pandal_contributions", Context.MODE_PRIVATE)
    
    val deviceId: String
        get() = preferences.getString("device_id", null) ?: UUID.randomUUID().toString().also {
            preferences.edit().putString("device_id", it).apply()
        }

    fun evaluate(pandal: Pandal, latestLocation: Location?): EligibilityResult {
        val nearby = latestLocation?.let { distanceTo(it, pandal) <= CONTRIBUTION_RADIUS_METERS } == true
        if (nearby) {
            // Record visit timestamp when physically near
            preferences.edit().putLong("visit_${pandal.id}", System.currentTimeMillis()).apply()
            return EligibilityResult(true, "You are near this pandal.", deviceId)
        }

        val visitedAt = preferences.getLong("visit_${pandal.id}", 0L)
        val isRecentVisitor = (System.currentTimeMillis() - visitedAt) <= RECENT_VISIT_MILLIS && visitedAt > 0L

        return if (isRecentVisitor) {
            EligibilityResult(true, "Recent visit verified on this device.", deviceId)
        } else {
            EligibilityResult(false, "Move closer to the pandal or mark a recent visit to update.", deviceId)
        }
    }

    fun markVisit(pandalId: String) {
        preferences.edit().putLong("visit_$pandalId", System.currentTimeMillis()).apply()
    }

    fun distanceTo(location: Location, pandal: Pandal): Float =
        Location("").apply {
            latitude = pandal.latitude
            longitude = pandal.longitude
        }.let(location::distanceTo)

    companion object {
        const val CONTRIBUTION_RADIUS_METERS = 200f
        const val RECENT_VISIT_MILLIS = 45 * 60 * 1000L // 45 minutes
    }
}
