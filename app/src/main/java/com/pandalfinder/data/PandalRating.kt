package com.pandalfinder.data

/**
 * Community rating submitted by a user for a specific Pandal.
 * Stored in Firestore under `pandalRatings/{pandalId}_{userId}`.
 */
data class PandalRating(
    val ratingId: String = "",
    val pandalId: String = "",
    val userId: String = "",
    val rating: Int = 5,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
