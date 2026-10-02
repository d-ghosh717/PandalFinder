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

enum class RouteMode(val apiValue: String, val profile: RouteProfile) {
    WALK("WALK", RouteProfile.WALKING),
    TWO_WHEELER("TWO_WHEELER", RouteProfile.CYCLING),
    DRIVE("DRIVE", RouteProfile.DRIVING)
}

data class RoutesDiagnosticState(
    var lastHttpStatus: Int = 0,
    var lastErrorMessage: String = "",
    var lastOrigin: String = "",
    var lastDestination: String = "",
    var lastMode: String = "",
    var lastRequestTime: Long = 0L,
    var apiKeyConfigured: Boolean = false,
    var orsApiKeyConfigured: Boolean = false,
    var lastProviderUsed: String = "NONE",
    var orsLastHttpStatus: Int = 0,
    var orsLastErrorMessage: String = ""
)

/**
 * Computes road-route distances using a robust multi-provider pipeline:
 * Primary: Google Routes API (ComputeRoutes REST)
 * Fallback: OpenRouteService / HeiGIT API
 * 
 * Results are cached in-memory with short TTL to optimize API usage.
 */
class GoogleRoutesRepository(
    private val orsProvider: OpenRouteServiceProvider = OpenRouteServiceProvider()
) {
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
        val googleKey = BuildConfig.GOOGLE_ROUTES_API_KEY
        val orsKey = BuildConfig.ORS_API_KEY
        diagnosticState.apiKeyConfigured = googleKey.isNotBlank()
        diagnosticState.orsApiKeyConfigured = orsKey.isNotBlank() && orsKey != "YOUR_KEY_HERE"

        val cacheKey = "$destinationId:${"%.4f".format(origin.latitude)}:${"%.4f".format(origin.longitude)}"
        val now = System.currentTimeMillis()
        singleCache[cacheKey]?.takeIf { now - it.first < CACHE_MILLIS }?.let {
            onResult(it.second)
            return
        }

        executor.execute {
            Log.d(TAG, "─── Route Request Pipeline ───")
            Log.d(TAG, "Origin = (${origin.latitude}, ${origin.longitude})")
            Log.d(TAG, "Destination = ($destLat, $destLng)")

            val walkResult = resolveSingleRoute(origin.latitude, origin.longitude, destLat, destLng, RouteProfile.WALKING)
            val bikeResult = resolveSingleRoute(origin.latitude, origin.longitude, destLat, destLng, RouteProfile.CYCLING)
            val driveResult = resolveSingleRoute(origin.latitude, origin.longitude, destLat, destLng, RouteProfile.DRIVING)

            val activeProvider = when {
                driveResult?.provider == RoutingProviderName.GOOGLE || walkResult?.provider == RoutingProviderName.GOOGLE -> RoutingProviderName.GOOGLE
                driveResult?.provider == RoutingProviderName.OPENROUTESERVICE || walkResult?.provider == RoutingProviderName.OPENROUTESERVICE -> RoutingProviderName.OPENROUTESERVICE
                else -> RoutingProviderName.UNAVAILABLE
            }

            diagnosticState.lastProviderUsed = activeProvider.name
            val results = PandalRoutes(
                walk = walkResult,
                twoWheeler = bikeResult,
                drive = driveResult,
                activeProvider = activeProvider
            )

            val resolved = results.takeIf { it.walk != null || it.twoWheeler != null || it.drive != null }
            resolved?.let { singleCache[cacheKey] = now to it }
            main.post { onResult(resolved) }
        }
    }

    private val singleModeCache = mutableMapOf<String, Pair<Long, RouteResult>>()

    /**
     * Attempts Google Routes first. If Google fails for any reason, falls back to OpenRouteService.
     */
    fun resolveSingleRoute(
        origLat: Double,
        origLng: Double,
        destLat: Double,
        destLng: Double,
        profile: RouteProfile
    ): RouteResult? {
        diagnosticState.lastOrigin = "$origLat,$origLng"
        diagnosticState.lastDestination = "$destLat,$destLng"
        diagnosticState.lastMode = profile.name
        diagnosticState.lastRequestTime = System.currentTimeMillis()

        val modeCacheKey = "${"%.4f".format(origLat)},${"%.4f".format(origLng)}->${"%.4f".format(destLat)},${"%.4f".format(destLng)}:${profile.name}"
        val now = System.currentTimeMillis()
        singleModeCache[modeCacheKey]?.takeIf { now - it.first < CACHE_MILLIS }?.let {
            return it.second
        }

        // 1. Attempt Primary: Google Routes
        val googleResult = requestGoogleSingleCoordinates(origLat, origLng, destLat, destLng, profile)
        if (googleResult != null) {
            Log.d(TAG, "Routing provider: GOOGLE for $profile (${googleResult.distanceMeters}m)")
            singleModeCache[modeCacheKey] = now to googleResult
            return googleResult
        }

        // 2. Fallback to OpenRouteService
        Log.w(TAG, "Google Routes unavailable: ${diagnosticState.lastErrorMessage}. Falling back to OpenRouteService.")
        val orsResult = orsProvider.getRoute(origLat, origLng, destLat, destLng, profile)
        diagnosticState.orsLastHttpStatus = orsProvider.lastHttpStatus
        diagnosticState.orsLastErrorMessage = orsProvider.lastErrorMessage

        if (orsResult != null) {
            Log.d(TAG, "Routing provider: OPENROUTESERVICE for $profile (${orsResult.distanceMeters}m)")
            singleModeCache[modeCacheKey] = now to orsResult
            return orsResult
        }

        // 3. Both failed
        Log.e(TAG, "Google Routes unavailable. OpenRouteService unavailable. Route unavailable.")
        return null
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

        val stopIds = stops.joinToString(",") { it.id }
        val cacheKey = "${"%.4f".format(origin.latitude)}:${"%.4f".format(origin.longitude)}->$stopIds"
        val now = System.currentTimeMillis()
        hoppingCache[cacheKey]?.takeIf { now - it.first < CACHE_MILLIS }?.let {
            onResult(it.second)
            return
        }

        executor.execute {
            // Attempt Google full route or fall back to leg-by-leg (which uses Google -> ORS)
            val result = requestGoogleHopping(origin, stops) ?: computeHoppingLegByLeg(origin, stops)
            result?.let { hoppingCache[cacheKey] = now to it }
            main.post { onResult(result) }
        }
    }

    private fun computeHoppingLegByLeg(origin: Location, stops: List<HoppingStop>): HoppingRoute? {
        Log.d(TAG, "Computing hopping route leg-by-leg via Google -> ORS fallback for ${stops.size} stops")
        var totalDist = 0
        var totalSec = 0L
        val legDistances = mutableListOf<Int>()
        var primaryProvider = RoutingProviderName.UNAVAILABLE

        var prevLat = origin.latitude
        var prevLng = origin.longitude

        for (stop in stops) {
            val leg = resolveSingleRoute(prevLat, prevLng, stop.latitude, stop.longitude, RouteProfile.DRIVING)
                ?: resolveSingleRoute(prevLat, prevLng, stop.latitude, stop.longitude, RouteProfile.CYCLING)
                ?: resolveSingleRoute(prevLat, prevLng, stop.latitude, stop.longitude, RouteProfile.WALKING)

            if (leg != null) {
                totalDist += leg.distanceMeters
                val sec = if (leg.durationSeconds > 0) leg.durationSeconds else (leg.duration.removeSuffix("s").toLongOrNull() ?: 0L)
                totalSec += sec
                legDistances.add(leg.distanceMeters)
                if (primaryProvider == RoutingProviderName.UNAVAILABLE) {
                    primaryProvider = leg.provider
                }
            } else {
                Log.w(TAG, "Leg failed between ($prevLat, $prevLng) and (${stop.latitude}, ${stop.longitude}) across all providers")
                return null
            }
            prevLat = stop.latitude
            prevLng = stop.longitude
        }

        return HoppingRoute(
            totalDistanceMeters = totalDist,
            totalDurationFormatted = OpenRouteServiceProvider.formatDuration(totalSec),
            legDistances = legDistances,
            provider = primaryProvider
        )
    }

    private fun requestGoogleSingleCoordinates(
        origLat: Double,
        origLng: Double,
        destLat: Double,
        destLng: Double,
        profile: RouteProfile
    ): RouteResult? = runCatching {
        val apiKey = BuildConfig.GOOGLE_ROUTES_API_KEY
        if (apiKey.isBlank()) {
            diagnosticState.lastErrorMessage = "Google Routes API key is blank"
            diagnosticState.lastHttpStatus = 0
            return null
        }

        val body = JSONObject().apply {
            put("origin", waypoint(origLat, origLng))
            put("destination", waypoint(destLat, destLng))
            put("travelMode", profile.googleMode)
            put("computeAlternativeRoutes", false)
            put("languageCode", "en-IN")
            put("units", "METRIC")
        }

        val result = executeGoogleRoutesPost(body)
        if (result != null) {
            val distance = result.optInt("distanceMeters", 0)
            val duration = result.optString("duration", "")
            val durSec = duration.removeSuffix("s").toLongOrNull() ?: 0L
            if (distance > 0) {
                diagnosticState.lastErrorMessage = "OK"
                RouteResult(
                    distanceMeters = distance,
                    duration = duration,
                    durationSeconds = durSec,
                    provider = RoutingProviderName.GOOGLE,
                    success = true
                )
            } else {
                diagnosticState.lastErrorMessage = "No route found in Google response"
                null
            }
        } else {
            null
        }
    }.getOrElse { error ->
        Log.e(TAG, "Google Routes request exception for profile ${profile.name}", error)
        diagnosticState.lastErrorMessage = error.localizedMessage ?: "Unknown Google API error"
        null
    }

    private fun requestGoogleHopping(origin: Location, stops: List<HoppingStop>): HoppingRoute? = runCatching {
        val apiKey = BuildConfig.GOOGLE_ROUTES_API_KEY
        if (apiKey.isBlank()) return null

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

        val routeJson = executeGoogleRoutesPost(body, fieldMask = "routes.distanceMeters,routes.duration,routes.legs.distanceMeters,routes.legs.duration")
            ?: return null

        val totalDistance = routeJson.optInt("distanceMeters", 0)
        val totalDurationRaw = routeJson.optString("duration", "")
        val durSec = totalDurationRaw.removeSuffix("s").toLongOrNull() ?: 0L
        val formattedDuration = OpenRouteServiceProvider.formatDuration(durSec)

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
            legDistances = legDistances,
            provider = RoutingProviderName.GOOGLE
        )
    }.getOrElse { error ->
        Log.e(TAG, "Google Hopping route request failed", error)
        null
    }

    private fun executeGoogleRoutesPost(body: JSONObject, fieldMask: String = "routes.distanceMeters,routes.duration"): JSONObject? {
        val apiKey = BuildConfig.GOOGLE_ROUTES_API_KEY
        val connection = (URL(COMPUTE_ROUTES_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey)
            setRequestProperty("X-Goog-FieldMask", fieldMask)
            setRequestProperty("X-Android-Package", PACKAGE_NAME)
            setRequestProperty("X-Android-Cert", CERT_SHA1)
        }

        connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        diagnosticState.lastHttpStatus = status
        Log.d(TAG, "Google Routes API HTTP status = $status")

        if (status !in 200..299) {
            Log.e(TAG, "Google Routes error body = $responseBody")
            diagnosticState.lastErrorMessage = parseGoogleErrorMessage(status, responseBody)
            return null
        }

        val json = JSONObject(responseBody)
        val route = json.optJSONArray("routes")?.optJSONObject(0)
        if (route == null) {
            Log.w(TAG, "Google Routes response body has no routes: $responseBody")
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

    private fun waypoint(latitude: Double, longitude: Double) = JSONObject().apply {
        put("location", JSONObject().apply {
            put("latLng", JSONObject().apply { put("latitude", latitude); put("longitude", longitude) })
        })
    }

    companion object {
        const val TAG = "RoutingRepository"
        const val COMPUTE_ROUTES_URL = "https://routes.googleapis.com/directions/v2:computeRoutes"
        const val CACHE_MILLIS = 5 * 60 * 1000L
        const val PACKAGE_NAME = "com.pandalfinder"
        const val CERT_SHA1 = "4F4C1806D054E172BF09FB0760599707D9BCFB5E"

        fun makeSingleModeCacheKey(
            originLat: Double,
            originLng: Double,
            destLat: Double,
            destLng: Double,
            mode: String
        ): String {
            return "${"%.4f".format(originLat)},${"%.4f".format(originLng)}->${"%.4f".format(destLat)},${"%.4f".format(destLng)}:$mode"
        }
    }
}
