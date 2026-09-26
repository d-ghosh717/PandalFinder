package com.pandalfinder.data

import android.location.Location
import com.pandalfinder.Pandal
import com.pandalfinder.PandalRepository

data class TonightsPlan(
    val title: String,
    val description: String,
    val stops: List<HoppingStop>,
    val totalDistanceMeters: Int,
    val estimatedDurationMinutes: Int
)

object TonightsPlanGenerator {

    /**
     * Generates a recommended 3-5 stop puja night plan based on GPS location,
     * nearby pandals, crowd levels (if available), and transit connections.
     */
    fun generate(
        origin: Location?,
        pandalsRepo: PandalRepository,
        metroRepo: MetroRepository,
        cachedToilets: List<PublicToilet>,
        crowdMap: Map<String, Int> = emptyMap()
    ): TonightsPlan {
        val userLoc = origin ?: Location("").apply {
            latitude = 22.5726
            longitude = 88.3639
        }

        // 1. Sort pandals by proximity and crowd friendliness
        val sortedPandals = pandalsRepo.all().map { p ->
            val d = userLoc.distanceTo(Location("").apply {
                latitude = p.latitude
                longitude = p.longitude
            })
            val crowd = crowdMap[p.id] ?: 5
            Triple(p, d, crowd)
        }.sortedWith(compareBy({ it.second }, { it.third }))

        val selectedPandals = sortedPandals.take(3).map { it.first }

        val stops = mutableListOf<HoppingStop>()
        if (selectedPandals.isNotEmpty()) {
            stops.add(HoppingStop.fromPandal(selectedPandals[0]))
        }
        if (selectedPandals.size > 1) {
            stops.add(HoppingStop.fromPandal(selectedPandals[1]))
        }

        // Add closest Metro station near the middle pandal
        if (selectedPandals.isNotEmpty()) {
            val midPandal = selectedPandals.getOrElse(1) { selectedPandals[0] }
            val nearestMetro = metroRepo.nearestTo(midPandal).station
            stops.add(HoppingStop.fromMetro(nearestMetro))
        }

        if (selectedPandals.size > 2) {
            stops.add(HoppingStop.fromPandal(selectedPandals[2]))
        }

        // Add nearest verified public toilet if available
        if (cachedToilets.isNotEmpty() && stops.isNotEmpty()) {
            val lastPandal = selectedPandals.last()
            val nearestToilet = cachedToilets.minByOrNull { t ->
                RouteOptimizer.distanceBetween(lastPandal.latitude, lastPandal.longitude, t.latitude, t.longitude)
            }
            if (nearestToilet != null) {
                stops.add(HoppingStop.fromToilet(nearestToilet))
            }
        }

        // Compute total distance
        var totalDist = 0
        var prevLat = userLoc.latitude
        var prevLng = userLoc.longitude
        stops.forEach { s ->
            totalDist += RouteOptimizer.distanceBetween(prevLat, prevLng, s.latitude, s.longitude).toInt()
            prevLat = s.latitude
            prevLng = s.longitude
        }

        // Estimate ~45 min per pandal + walking/transit time (4.5 km/h walking ~ 13.3 m/min)
        val travelMinutes = (totalDist / 65).coerceAtLeast(10)
        val visitingMinutes = selectedPandals.size * 40
        val totalMinutes = travelMinutes + visitingMinutes

        val areaName = selectedPandals.firstOrNull()?.area ?: "Kolkata"

        return TonightsPlan(
            title = "Tonight's $areaName Circuit",
            description = "A curated trail with ${selectedPandals.size} major pandals, metro connection & rest stop.",
            stops = stops,
            totalDistanceMeters = totalDist,
            estimatedDurationMinutes = totalMinutes
        )
    }
}
