package com.pandalfinder.data

import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pandalfinder.BuildConfig
import com.pandalfinder.Pandal
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

enum class RouteMode(val apiValue: String) {
    WALK("WALK"),
    TWO_WHEELER("TWO_WHEELER"),
    DRIVE("DRIVE")
}

data class RouteResult(val distanceMeters: Int, val duration: String)
data class PandalRoutes(val walk: RouteResult?, val twoWheeler: RouteResult?, val drive: RouteResult?)

data class HoppingLeg(val distanceMeters: Int, val duration: String)
data class HoppingRoute(
    val totalDistanceMeters: Int,
    val totalDurationFormatted: String,
    val legDistances: List<Int> // Distance to reach each stop from previous point
)

/**
 * Computes road-route distances using Google Routes API.
 * Results are cached for short durations to minimize billable API calls.
 */
class GoogleRoutesRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val singleCache = mutableMapOf<String, Pair<Long, PandalRoutes>>()
    private val hoppingCache = mutableMapOf<String, Pair<Long, HoppingRoute>>()

    fun routesFrom(origin: Location, pandal: Pandal, onResult: (PandalRoutes?) -> Unit) {
        routesToCoordinates(origin, "pandal:${pandal.id}", pandal.latitude, pandal.longitude, onResult)
    }

    fun routesToStation(origin: Location, station: MetroStation, onResult: (PandalRoutes?) -> Unit) {
        val id = "metro:${station.name.lowercase().replace(" ", "_")}"
        routesToCoordinates(origin, id, station.latitude, station.longitude, onResult)
    }

    fun routesToCoordinates(
        origin: Location,
        destinationId: String,
        destLat: Double,
        destLng: Double,
        onResult: (PandalRoutes?) -> Unit
    ) {
        if (BuildConfig.GOOGLE_ROUTES_API_KEY.isBlank()) {
            Log.w(TAG, "Routes API key is not configured; route request skipped")
            onResult(null)
            return
        }
        val cacheKey = "$destinationId:${"%.4f".format(origin.latitude)}:${"%.4f".format(origin.longitude)}"
        val now = System.currentTimeMillis()
        singleCache[cacheKey]?.takeIf { now - it.first < CACHE_MILLIS }?.let {
            onResult(it.second)
            return
        }

        executor.execute {
            Log.d(TAG, "Compute Routes origin=${origin.latitude},${origin.longitude}; destination=$destLat,$destLng")
            val results = PandalRoutes(
                walk = requestSingle(origin, destLat, destLng, RouteMode.WALK),
                twoWheeler = requestSingle(origin, destLat, destLng, RouteMode.TWO_WHEELER),
                drive = requestSingle(origin, destLat, destLng, RouteMode.DRIVE)
            )
            val resolved = results.takeIf { it.walk != null || it.twoWheeler != null || it.drive != null }
            resolved?.let { singleCache[cacheKey] = now to it }
            main.post { onResult(resolved) }
        }
    }

    fun computeHoppingRoute(
        origin: Location,
        stops: List<HoppingStop>,
        onResult: (HoppingRoute?) -> Unit
    ) {
        if (stops.isEmpty()) {
            onResult(null)
            return
        }

        if (BuildConfig.GOOGLE_ROUTES_API_KEY.isBlank()) {
            Log.w(TAG, "Routes API key is not configured; hopping route request skipped")
            onResult(null)
            return
        }

        val stopIds = stops.joinToString(",") { it.id }
        val cacheKey = "${"%.4f".format(origin.latitude)}:${"%.4f".format(origin.longitude)}->$stopIds"
        val now = System.currentTimeMillis()
        hoppingCache[cacheKey]?.takeIf { now - it.first < CACHE_MILLIS }?.let {
            onResult(it.second)
            return
        }

        executor.execute {
            val result = requestHopping(origin, stops)
            result?.let { hoppingCache[cacheKey] = now to it }
            main.post { onResult(result) }
        }
    }

    private fun requestSingle(origin: Location, destLat: Double, destLng: Double, mode: RouteMode): RouteResult? = runCatching {
        val body = JSONObject().apply {
            put("origin", waypoint(origin.latitude, origin.longitude))
            put("destination", waypoint(destLat, destLng))
            put("travelMode", mode.apiValue)
            put("computeAlternativeRoutes", false)
            put("languageCode", "en-IN")
            put("units", "METRIC")
        }
        val connection = (URL(COMPUTE_ROUTES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", BuildConfig.GOOGLE_ROUTES_API_KEY)
            setRequestProperty("X-Goog-FieldMask", "routes.distanceMeters,routes.duration")
        }
        connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()
        if (status !in 200..299) {
            Log.e(TAG, "${mode.apiValue} Routes HTTP $status: $responseBody")
            return null
        }
        val route = JSONObject(responseBody).optJSONArray("routes")?.optJSONObject(0)
        val distance = route?.optInt("distanceMeters", 0) ?: 0
        if (distance <= 0) {
            Log.e(TAG, "${mode.apiValue} Routes returned no route: $responseBody")
            null
        } else {
            val duration = route?.optString("duration").orEmpty()
            RouteResult(distance, duration)
        }
    }.getOrElse { error ->
        Log.e(TAG, "${mode.apiValue} Routes request failed", error)
        null
    }

    private fun requestHopping(origin: Location, stops: List<HoppingStop>): HoppingRoute? = runCatching {
        val lastStop = stops.last()
        val intermediateStops = if (stops.size > 1) stops.subList(0, stops.size - 1) else emptyList()

        val body = JSONObject().apply {
            put("origin", waypoint(origin.latitude, origin.longitude))
            put("destination", waypoint(lastStop.latitude, lastStop.longitude))
            if (intermediateStops.isNotEmpty()) {
                val intermediates = JSONArray()
                intermediateStops.forEach { stop ->
                    intermediates.put(waypoint(stop.latitude, stop.longitude))
                }
                put("intermediates", intermediates)
            }
            put("travelMode", "DRIVE")
            put("computeAlternativeRoutes", false)
            put("languageCode", "en-IN")
            put("units", "METRIC")
        }

        val connection = (URL(COMPUTE_ROUTES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", BuildConfig.GOOGLE_ROUTES_API_KEY)
            setRequestProperty("X-Goog-FieldMask", "routes.distanceMeters,routes.duration,routes.legs.distanceMeters,routes.legs.duration")
        }
        connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (status !in 200..299) {
            Log.e(TAG, "Hopping Routes HTTP $status: $responseBody")
            return null
        }

        val routeJson = JSONObject(responseBody).optJSONArray("routes")?.optJSONObject(0) ?: return null
        val totalDistance = routeJson.optInt("distanceMeters", 0)
        val totalDurationRaw = routeJson.optString("duration", "")
        val formattedDuration = formatDurationString(totalDurationRaw)

        val legsArray = routeJson.optJSONArray("legs")
        val legDistances = mutableListOf<Int>()
        if (legsArray != null) {
            for (i in 0 until legsArray.length()) {
                val legObj = legsArray.optJSONObject(i)
                legDistances.add(legObj?.optInt("distanceMeters", 0) ?: 0)
            }
        }

        HoppingRoute(
            totalDistanceMeters = totalDistance,
            totalDurationFormatted = formattedDuration,
            legDistances = legDistances
        )
    }.getOrElse { error ->
        Log.e(TAG, "Hopping route request failed", error)
        null
    }

    private fun formatDurationString(durationStr: String): String {
        val seconds = durationStr.removeSuffix("s").toLongOrNull() ?: return "—"
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        return when {
            hours > 0 && minutes > 0 -> "${hours} hr ${minutes} min"
            hours > 0 -> "${hours} hr"
            minutes > 0 -> "${minutes} min"
            seconds > 0 -> "< 1 min"
            else -> "—"
        }
    }

    private fun waypoint(latitude: Double, longitude: Double) = JSONObject().apply {
        put("location", JSONObject().apply {
            put("latLng", JSONObject().apply { put("latitude", latitude); put("longitude", longitude) })
        })
    }

    private companion object {
        const val TAG = "GoogleRoutes"
        const val COMPUTE_ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val CACHE_MILLIS = 2 * 60 * 1000L
    }
}
