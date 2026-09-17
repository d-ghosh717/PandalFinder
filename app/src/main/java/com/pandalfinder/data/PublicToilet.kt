package com.pandalfinder.data

/**
 * Public restroom / toilet obtained from Google Places API (New).
 */
data class PublicToilet(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double
)
