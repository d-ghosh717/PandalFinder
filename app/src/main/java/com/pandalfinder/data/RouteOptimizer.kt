package com.pandalfinder.data

import android.location.Location

data class RouteOptimizationResult(
    val optimizedStops: List<HoppingStop>,
    val originalDistanceMeters: Int,
    val optimizedDistanceMeters: Int,
    val savedDistanceMeters: Int,
    val wasReordered: Boolean
)

/**
 * Smart Route Optimizer for Hopping itineraries.
 * Computes the optimal sequence of stops starting from the user's current GPS location
 * using Nearest Neighbor + 2-Opt TSP heuristic.
 */
object RouteOptimizer {

    fun optimize(
        origin: Location?,
        stops: List<HoppingStop>
    ): RouteOptimizationResult {
        if (stops.size <= 2) {
            val totalDist = computeSequenceDistance(origin, stops)
            return RouteOptimizationResult(
                optimizedStops = stops,
                originalDistanceMeters = totalDist,
                optimizedDistanceMeters = totalDist,
                savedDistanceMeters = 0,
                wasReordered = false
            )
        }

        val originalDistance = computeSequenceDistance(origin, stops)

        // 1. Nearest Neighbor construction starting from origin
        val unvisited = stops.toMutableList()
        val sequence = mutableListOf<HoppingStop>()
        var currentLat = origin?.latitude ?: stops.first().latitude
        var currentLng = origin?.longitude ?: stops.first().longitude

        if (origin == null) {
            sequence.add(unvisited.removeAt(0))
        }

        while (unvisited.isNotEmpty()) {
            val nearestIndex = unvisited.indices.minByOrNull { i ->
                distanceBetween(currentLat, currentLng, unvisited[i].latitude, unvisited[i].longitude)
            } ?: 0
            val nearest = unvisited.removeAt(nearestIndex)
            sequence.add(nearest)
            currentLat = nearest.latitude
            currentLng = nearest.longitude
        }

        // 2. 2-Opt local search improvement
        var improved = true
        var bestSequence = sequence.toList()
        var bestDist = computeSequenceDistance(origin, bestSequence)

        var iterations = 0
        while (improved && iterations < 50) {
            improved = false
            iterations++
            for (i in 0 until bestSequence.size - 1) {
                for (k in i + 1 until bestSequence.size) {
                    val candidate = twoOptSwap(bestSequence, i, k)
                    val candidateDist = computeSequenceDistance(origin, candidate)
                    if (candidateDist < bestDist) {
                        bestSequence = candidate
                        bestDist = candidateDist
                        improved = true
                        break
                    }
                }
                if (improved) break
            }
        }

        val saved = (originalDistance - bestDist).coerceAtLeast(0)
        val wasReordered = bestSequence != stops && saved > 50

        return RouteOptimizationResult(
            optimizedStops = if (wasReordered) bestSequence else stops,
            originalDistanceMeters = originalDistance,
            optimizedDistanceMeters = if (wasReordered) bestDist else originalDistance,
            savedDistanceMeters = if (wasReordered) saved else 0,
            wasReordered = wasReordered
        )
    }

    private fun twoOptSwap(route: List<HoppingStop>, i: Int, k: Int): List<HoppingStop> {
        val result = mutableListOf<HoppingStop>()
        for (c in 0 until i) result.add(route[c])
        for (c in k downTo i) result.add(route[c])
        for (c in k + 1 until route.size) result.add(route[c])
        return result
    }

    private fun computeSequenceDistance(origin: Location?, stops: List<HoppingStop>): Int {
        if (stops.isEmpty()) return 0
        var total = 0.0
        var prevLat = origin?.latitude ?: stops.first().latitude
        var prevLng = origin?.longitude ?: stops.first().longitude

        val startIdx = if (origin == null) 1 else 0
        for (i in startIdx until stops.size) {
            val stop = stops[i]
            total += distanceBetween(prevLat, prevLng, stop.latitude, stop.longitude)
            prevLat = stop.latitude
            prevLng = stop.longitude
        }
        return total.toInt()
    }

    fun distanceBetween(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        if (lat1 == lat2 && lng1 == lng2) return 0f
        return try {
            val results = FloatArray(1)
            Location.distanceBetween(lat1, lng1, lat2, lng2, results)
            if (results[0] > 0f) {
                results[0]
            } else {
                computeGeodesicMeters(lat1, lng1, lat2, lng2)
            }
        } catch (e: Throwable) {
            computeGeodesicMeters(lat1, lng1, lat2, lng2)
        }
    }

    private fun computeGeodesicMeters(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
        val r = 6371000.0 // Earth radius in meters
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaPhi = Math.toRadians(lat2 - lat1)
        val deltaLambda = Math.toRadians(lng2 - lng1)

        val a = Math.sin(deltaPhi / 2) * Math.sin(deltaPhi / 2) +
                Math.cos(phi1) * Math.cos(phi2) *
                Math.sin(deltaLambda / 2) * Math.sin(deltaLambda / 2)
        val c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a))
        return (r * c).toFloat()
    }
}
