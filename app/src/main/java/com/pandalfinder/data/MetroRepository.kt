package com.pandalfinder.data

import android.location.Location
import com.pandalfinder.Pandal

/**
 * Result of a nearest-metro calculation.
 * Distance is always from the pandal coordinates, NOT from the user's location.
 */
data class MetroResult(
    val station: MetroStation,
    val distanceMeters: Float
)

/**
 * Calculates the nearest Kolkata Metro station to a given pandal.
 * Uses straight-line geographic distance via [Location.distanceTo].
 */
class MetroRepository {
    private val stations = MetroStation.allStations()

    /**
     * Returns the nearest metro station to the given [pandal].
     * Calculation uses pandal coordinates, not user location.
     */
    fun nearestTo(pandal: Pandal): MetroResult {
        val pandalLocation = Location("").apply {
            latitude = pandal.latitude
            longitude = pandal.longitude
        }

        var nearest: MetroStation = stations.first()
        var shortestDistance = Float.MAX_VALUE

        for (station in stations) {
            val stationLocation = Location("").apply {
                latitude = station.latitude
                longitude = station.longitude
            }
            val distance = pandalLocation.distanceTo(stationLocation)
            if (distance < shortestDistance) {
                shortestDistance = distance
                nearest = station
            }
        }

        return MetroResult(nearest, shortestDistance)
    }
}
