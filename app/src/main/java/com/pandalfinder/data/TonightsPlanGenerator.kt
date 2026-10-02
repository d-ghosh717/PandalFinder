package com.pandalfinder.data

import android.location.Location
import com.pandalfinder.Pandal
import com.pandalfinder.PandalRepository
import kotlin.math.roundToInt

data class PlanLeg(
    val fromStopName: String,
    val toStopName: String,
    val isMetroBeneficial: Boolean,
    val directMinutes: Int,
    val metroMinutes: Int,
    val timeSavedMinutes: Int,
    val fromStation: MetroStation? = null,
    val toStation: MetroStation? = null,
    val transferDescription: String? = null
)

data class TonightsPlan(
    val title: String,
    val description: String,
    val stops: List<HoppingStop>,
    val legs: List<PlanLeg> = emptyList(),
    val totalDistanceMeters: Int,
    val estimatedDurationMinutes: Int,
    val pandalCount: Int = 0,
    val metroSavingsTotalMinutes: Int = 0
)

object TonightsPlanGenerator {

    /**
     * Minimum time advantage in minutes required to recommend Metro transit
     * instead of direct road/walking travel between two stops.
     */
    const val METRO_MIN_TIME_SAVING_MINUTES = 5

    /** Maximum acceptable walking distance to or from a metro station (meters). */
    const val MAX_WALK_TO_METRO_METERS = 1400.0

    /** Average walking speed in meters per minute (~4.5 km/h). */
    private const val WALKING_METERS_PER_MIN = 75.0

    /** Average road speed with festival traffic in meters per minute (~15-18 km/h). */
    private const val DRIVING_METERS_PER_MIN = 250.0

    /**
     * Generates a recommended 3-pandal puja night plan based on GPS location,
     * nearby pandals, crowd levels (if available), and transit evaluations.
     * 
     * Selected pandals remain the true itinerary stops. Metro stations are evaluated
     * as transport transfer legs and NEVER inserted as standalone hopping stops.
     */
    fun generate(
        origin: Location?,
        pandalsRepo: PandalRepository,
        metroRepo: MetroRepository,
        cachedToilets: List<PublicToilet> = emptyList(),
        crowdMap: Map<String, Int> = emptyMap()
    ): TonightsPlan {
        val userLoc = origin ?: Location("").apply {
            latitude = 22.5726
            longitude = 88.3639
        }

        // 1. Select 3 pandals by proximity and crowd friendliness
        val sortedPandals = pandalsRepo.all().map { p ->
            val d = RouteOptimizer.distanceBetween(userLoc.latitude, userLoc.longitude, p.latitude, p.longitude)
            val crowd = crowdMap[p.id] ?: 5
            Triple(p, d, crowd)
        }.sortedWith(compareBy({ it.second }, { it.third }))

        val selectedPandals = sortedPandals.take(3).map { it.first }

        // Selected pandals are the actual itinerary stops
        val stops = mutableListOf<HoppingStop>()
        selectedPandals.forEach { pandal ->
            stops.add(HoppingStop.fromPandal(pandal))
        }

        // Add nearest verified public toilet if available at the end of circuit
        if (cachedToilets.isNotEmpty() && stops.isNotEmpty()) {
            val lastPandal = selectedPandals.last()
            val nearestToilet = cachedToilets.minByOrNull { t ->
                RouteOptimizer.distanceBetween(lastPandal.latitude, lastPandal.longitude, t.latitude, t.longitude)
            }
            if (nearestToilet != null) {
                stops.add(HoppingStop.fromToilet(nearestToilet))
            }
        }

        // 2. Evaluate consecutive legs for Metro transport advantages
        val allStations = metroRepo.all()
        val legs = mutableListOf<PlanLeg>()
        var totalMetroSavings = 0
        var totalTravelMinutes = 0
        var totalDist = 0

        var prevLat = userLoc.latitude
        var prevLng = userLoc.longitude
        var prevName = "Current Location"

        for (i in stops.indices) {
            val currentStop = stops[i]
            val legDist = RouteOptimizer.distanceBetween(prevLat, prevLng, currentStop.latitude, currentStop.longitude).toInt()
            totalDist += legDist

            val legDecision = evaluateLeg(
                fromName = prevName,
                fromLat = prevLat,
                fromLng = prevLng,
                toName = currentStop.name,
                toLat = currentStop.latitude,
                toLng = currentStop.longitude,
                stations = allStations
            )

            legs.add(legDecision)

            if (legDecision.isMetroBeneficial) {
                totalTravelMinutes += legDecision.metroMinutes
                totalMetroSavings += legDecision.timeSavedMinutes
            } else {
                totalTravelMinutes += legDecision.directMinutes
            }

            prevLat = currentStop.latitude
            prevLng = currentStop.longitude
            prevName = currentStop.name
        }

        val visitingMinutes = selectedPandals.size * 35
        val totalMinutes = totalTravelMinutes + visitingMinutes
        val areaName = selectedPandals.firstOrNull()?.area ?: "Kolkata"

        val metroCount = legs.count { it.isMetroBeneficial }
        val desc = when {
            metroCount > 0 -> "Curated trail with ${selectedPandals.size} major pandals + $metroCount rapid Metro connection${if (metroCount > 1) "s" else ""}."
            else -> "Curated walking trail with ${selectedPandals.size} major pandals in $areaName."
        }

        return TonightsPlan(
            title = "Tonight's $areaName Circuit",
            description = desc,
            stops = stops,
            legs = legs,
            totalDistanceMeters = totalDist,
            estimatedDurationMinutes = totalMinutes,
            pandalCount = selectedPandals.size,
            metroSavingsTotalMinutes = totalMetroSavings
        )
    }

    /**
     * Evaluates whether taking Metro between two coordinates provides a meaningful
     * total journey time advantage over direct road/walking travel.
     */
    fun evaluateLeg(
        fromName: String,
        fromLat: Double,
        fromLng: Double,
        toName: String,
        toLat: Double,
        toLng: Double,
        stations: List<MetroStation>
    ): PlanLeg {
        val directDist = RouteOptimizer.distanceBetween(fromLat, fromLng, toLat, toLng)
        val directTimeMinutes = calculateDirectTimeMinutes(directDist)

        val metroOption = findBestMetroOption(fromLat, fromLng, toLat, toLng, stations, directDist)

        if (metroOption == null) {
            return PlanLeg(
                fromStopName = fromName,
                toStopName = toName,
                isMetroBeneficial = false,
                directMinutes = directTimeMinutes,
                metroMinutes = directTimeMinutes,
                timeSavedMinutes = 0,
                transferDescription = "Direct route (~$directTimeMinutes min)"
            )
        }

        val (fromStation, toStation, metroTimeMinutes) = metroOption
        val timeSaved = directTimeMinutes - metroTimeMinutes

        val isBeneficial = timeSaved >= METRO_MIN_TIME_SAVING_MINUTES

        val desc = if (isBeneficial) {
            "Metro via ${fromStation.name} → ${toStation.name} (Saves ~$timeSaved min)"
        } else {
            "Direct route (~$directTimeMinutes min)"
        }

        return PlanLeg(
            fromStopName = fromName,
            toStopName = toName,
            isMetroBeneficial = isBeneficial,
            directMinutes = directTimeMinutes,
            metroMinutes = metroTimeMinutes,
            timeSavedMinutes = if (isBeneficial) timeSaved else 0,
            fromStation = if (isBeneficial) fromStation else null,
            toStation = if (isBeneficial) toStation else null,
            transferDescription = desc
        )
    }

    private fun calculateDirectTimeMinutes(distanceMeters: Float): Int {
        return if (distanceMeters <= 1200) {
            (distanceMeters / WALKING_METERS_PER_MIN).roundToInt().coerceAtLeast(2)
        } else {
            // Road transit with festive traffic: base traffic buffer 6 min + travel
            val drivingTime = (distanceMeters / DRIVING_METERS_PER_MIN).roundToInt() + 6
            val walkingTime = (distanceMeters / WALKING_METERS_PER_MIN).roundToInt()
            minOf(drivingTime, walkingTime).coerceAtLeast(3)
        }
    }

    private data class MetroCandidate(
        val fromStation: MetroStation,
        val toStation: MetroStation,
        val totalMetroMinutes: Int
    )

    private fun findBestMetroOption(
        fromLat: Double,
        fromLng: Double,
        toLat: Double,
        toLng: Double,
        stations: List<MetroStation>,
        directDistMeters: Float
    ): MetroCandidate? {
        if (stations.size < 2 || directDistMeters < 1500) {
            // Distance is too short for a multi-leg metro transit detour
            return null
        }

        // Find stations close to origin
        val originCandidates = stations.map { s ->
            s to RouteOptimizer.distanceBetween(fromLat, fromLng, s.latitude, s.longitude)
        }.filter { it.second <= MAX_WALK_TO_METRO_METERS }

        // Find stations close to destination
        val destCandidates = stations.map { s ->
            s to RouteOptimizer.distanceBetween(toLat, toLng, s.latitude, s.longitude)
        }.filter { it.second <= MAX_WALK_TO_METRO_METERS }

        if (originCandidates.isEmpty() || destCandidates.isEmpty()) {
            return null
        }

        var bestOption: MetroCandidate? = null
        var lowestMetroTime = Int.MAX_VALUE

        for ((stA, walkADist) in originCandidates) {
            for ((stB, walkBDist) in destCandidates) {
                // Reject same station
                if (stA.name.equals(stB.name, ignoreCase = true)) continue

                // Detour Protection: Walking to + from metro cannot exceed total direct distance
                if (walkADist + walkBDist >= directDistMeters * 0.9) continue

                // Detour Protection: Station A must not be significantly further from destination than origin is
                val stAToDestDist = RouteOptimizer.distanceBetween(stA.latitude, stA.longitude, toLat, toLng)
                if (stAToDestDist > directDistMeters + 400) continue

                val rideMinutes = calculateMetroRideMinutes(stA, stB, stations) ?: continue

                val walkAMinutes = (walkADist / WALKING_METERS_PER_MIN).roundToInt()
                val walkBMinutes = (walkBDist / WALKING_METERS_PER_MIN).roundToInt()
                val waitAndPlatformMinutes = 4 // Average platform wait + turnstile

                val totalMetroTime = walkAMinutes + rideMinutes + waitAndPlatformMinutes + walkBMinutes

                if (totalMetroTime < lowestMetroTime) {
                    lowestMetroTime = totalMetroTime
                    bestOption = MetroCandidate(stA, stB, totalMetroTime)
                }
            }
        }

        return bestOption
    }

    private fun calculateMetroRideMinutes(
        stA: MetroStation,
        stB: MetroStation,
        allStations: List<MetroStation>
    ): Int? {
        // Case 1: Both stations on the same line
        if (stA.line.equals(stB.line, ignoreCase = true)) {
            val lineStations = allStations.filter { it.line.equals(stA.line, ignoreCase = true) }
            val idxA = lineStations.indexOfFirst { it.name.equals(stA.name, ignoreCase = true) }
            val idxB = lineStations.indexOfFirst { it.name.equals(stB.name, ignoreCase = true) }
            if (idxA == -1 || idxB == -1 || idxA == idxB) return null
            val stopsCount = kotlin.math.abs(idxA - idxB)
            return (stopsCount * 2.2).roundToInt().coerceAtLeast(3)
        }

        // Case 2: Inter-line connection via Esplanade (Blue Line & Green Line interchange)
        val isBlueGreen = (stA.line.contains("Blue") && stB.line.contains("Green")) ||
                          (stA.line.contains("Green") && stB.line.contains("Blue"))

        if (isBlueGreen) {
            val blueStations = allStations.filter { it.line.contains("Blue") }
            val greenStations = allStations.filter { it.line.contains("Green") }

            val blueEspIdx = blueStations.indexOfFirst { it.name.equals("Esplanade", ignoreCase = true) }
            val greenEspIdx = greenStations.indexOfFirst { it.name.equals("Esplanade", ignoreCase = true) || it.name.equals("Mahakaran", ignoreCase = true) }

            val (fromBlue, toGreen) = if (stA.line.contains("Blue")) Pair(stA, stB) else Pair(stB, stA)
            val idxFrom = blueStations.indexOfFirst { it.name.equals(fromBlue.name, ignoreCase = true) }
            val idxTo = greenStations.indexOfFirst { it.name.equals(toGreen.name, ignoreCase = true) }

            if (idxFrom == -1 || idxTo == -1 || blueEspIdx == -1 || greenEspIdx == -1) return null

            val leg1Stops = kotlin.math.abs(idxFrom - blueEspIdx)
            val leg2Stops = kotlin.math.abs(idxTo - greenEspIdx)
            val transferWalkMinutes = 5
            return ((leg1Stops + leg2Stops) * 2.2).roundToInt() + transferWalkMinutes
        }

        // Other unconnected lines in Kolkata metro network currently
        return null
    }
}
