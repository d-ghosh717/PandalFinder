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

data class RoutesDiagnosticState(
    var lastHttpStatus: Int = 0,
    var lastErrorMessage: String = "",
    var lastOrigin: String = "",
    var lastDestination: String = "",
    var lastMode: String = "",
    var lastRequestTime: Long = 0L,
    var apiKeyConfigured: Boolean = false
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

    val diagnosticState = RoutesDiagnosticState()

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
        val apiKey = BuildConfig.GOOGLE_ROUTES_API_KEY
        diagnosticState.apiKeyConfigured = apiKey.isNotBlank()

        if (apiKey.isBlank()) {
            val errorMsg = "GOOGLE_ROUTES_API_KEY is blank. Set ROUTES_API_KEY in secrets.properties and rebuild."
            Log.w(TAG, "Routes API request: skipped ($errorMsg)")
            diagnosticState.lastErrorMessage = errorMsg
            diagnosticState.lastHttpStatus = 0
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
            Log.d(TAG, "─── Routes API Request Batch ───")
            Log.d(TAG, "origin = (${origin.latitude}, ${origin.longitude})")
            Log.d(TAG, "destination = ($destLat, $destLng)")

            val walkResult = requestSingleCoordinates(origin.latitude, origin.longitude, destLat, destLng, RouteMode.WALK)
            val bikeResult = requestSingleCoordinates(origin.latitude, origin.longitude, destLat, destLng, RouteMode.TWO_WHEELER)
            val driveResult = requestSingleCoordinates(origin.latitude, origin.longitude, destLat, destLng, RouteMode.DRIVE)

            val results = PandalRoutes(
                walk = walkResult,
                twoWheeler = bikeResult,
                drive = driveResult
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

        val apiKey = BuildConfig.GOOGLE_ROUTES_API_KEY
        if (apiKey.isBlank()) {
            Log.w(TAG, "Routes API request: skipped because GOOGLE_ROUTES_API_KEY is blank")
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
            val result = requestHopping(origin, stops) ?: computeHoppingLegByLeg(origin, stops)
            result?.let { hoppingCache[cacheKey] = now to it }
            main.post { onResult(result) }
        }
    }

    private fun computeHoppingLegByLeg(origin: Location, stops: List<HoppingStop>): HoppingRoute? {
        Log.d(TAG, "Attempting leg-by-leg route calculation for ${stops.size} stops")
        var totalDist = 0
        var totalSec = 0L
        val legDistances = mutableListOf<Int>()

        var prevLat = origin.latitude
        var prevLng = origin.longitude

        for (stop in stops) {
            val leg = requestSingleCoordinates(prevLat, prevLng, stop.latitude, stop.longitude, RouteMode.DRIVE)
                ?: requestSingleCoordinates(prevLat, prevLng, stop.latitude, stop.longitude, RouteMode.TWO_WHEELER)
                ?: requestSingleCoordinates(prevLat, prevLng, stop.latitude, stop.longitude, RouteMode.WALK)

            if (leg != null) {
                totalDist += leg.distanceMeters
                val sec = leg.duration.removeSuffix("s").toLongOrNull() ?: 0L
                totalSec += sec
                legDistances.add(leg.distanceMeters)
            } else {
                Log.w(TAG, "Leg failed between ($prevLat, $prevLng) and (${stop.latitude}, ${stop.longitude})")
                return null
            }
            prevLat = stop.latitude
            prevLng = stop.longitude
        }

        return HoppingRoute(
            totalDistanceMeters = totalDist,
            totalDurationFormatted = formatDurationString("${totalSec}s"),
            legDistances = legDistances
        )
    }

    private fun requestSingleCoordinates(
        origLat: Double,
        origLng: Double,
        destLat: Double,
        destLng: Double,
        mode: RouteMode
    ): RouteResult? = runCatching {
        diagnosticState.lastOrigin = "$origLat,$origLng"
        diagnosticState.lastDestination = "$destLat,$destLng"
        diagnosticState.lastMode = mode.apiValue
        diagnosticState.lastRequestTime = System.currentTimeMillis()

        Log.d(TAG, "Routes API request:")
        Log.d(TAG, "  origin = $origLat, $origLng")
        Log.d(TAG, "  destination = $destLat, $destLng")
        Log.d(TAG, "  travelMode = ${mode.apiValue}")

        val body = JSONObject().apply {
            put("origin", waypoint(origLat, origLng))
            put("destination", waypoint(destLat, destLng))
            put("travelMode", mode.apiValue)
            put("computeAlternativeRoutes", false)
            put("languageCode", "en-IN")
            put("units", "METRIC")
        }

        val result = executeRoutesPost(body)
        if (result != null) {
            val distance = result.optInt("distanceMeters", 0)
            val duration = result.optString("duration", "")
            if (distance > 0) {
                diagnosticState.lastErrorMessage = "OK"
                RouteResult(distance, duration)
            } else {
                diagnosticState.lastErrorMessage = "No route found in response"
                null
            }
        } else {
            null
        }
    }.getOrElse { error ->
        Log.e(TAG, "Routes API request exception for mode ${mode.apiValue}", error)
        diagnosticState.lastErrorMessage = error.localizedMessage ?: "Unknown error"
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

        val routeJson = executeRoutesPost(body, fieldMask = "routes.distanceMeters,routes.duration,routes.legs.distanceMeters,routes.legs.duration")
            ?: return null

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

    private fun executeRoutesPost(body: JSONObject, fieldMask: String = "routes.distanceMeters,routes.duration"): JSONObject? {
        val apiKey = BuildConfig.GOOGLE_ROUTES_API_KEY
        val connection = (URL(COMPUTE_ROUTES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey)
            setRequestProperty("X-Goog-FieldMask", fieldMask)
            // Android app restriction support for REST API
            setRequestProperty("X-Android-Package", PACKAGE_NAME)
            setRequestProperty("X-Android-Cert", CERT_SHA1)
        }

        connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        diagnosticState.lastHttpStatus = status
        Log.d(TAG, "Routes API response:")
        Log.d(TAG, "  HTTP status = $status")

        if (status !in 200..299) {
            Log.e(TAG, "  error body = $responseBody")
            diagnosticState.lastErrorMessage = parseGoogleErrorMessage(status, responseBody)
            return null
        }

        val json = JSONObject(responseBody)
        val route = json.optJSONArray("routes")?.optJSONObject(0)
        if (route == null) {
            Log.w(TAG, "  response body has no routes: $responseBody")
            diagnosticState.lastErrorMessage = "No routes found for given coordinates"
        }
        return route
    }

    private fun parseGoogleErrorMessage(status: Int, responseBody: String): String {
        return try {
            val json = JSONObject(responseBody)
            val err = json.optJSONObject("error")
            val code = err?.optInt("code", status) ?: status
            val msg = err?.optString("message", "Unknown error") ?: "Unknown error"
            val errStatus = err?.optString("status", "") ?: ""
            "HTTP $code ($errStatus): $msg"
        } catch (e: Exception) {
            "HTTP $status: $responseBody"
        }
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

    companion object {
        const val TAG = "GoogleRoutes"
        const val COMPUTE_ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val CACHE_MILLIS = 2 * 60 * 1000L
        const val PACKAGE_NAME = "com.pandalfinder"
        // Signing Certificate SHA-1
        const val CERT_SHA1 = "4F4C1806D054E172BF09FB0760599707D9BCFB5E"
    }
}
