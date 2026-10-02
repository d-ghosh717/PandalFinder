package com.pandalfinder.data

import android.util.Log
import com.pandalfinder.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class OpenRouteServiceProvider(
    private val apiKeyProvider: () -> String = { BuildConfig.ORS_API_KEY }
) : RoutingProvider {

    override val providerName: RoutingProviderName = RoutingProviderName.OPENROUTESERVICE

    var lastHttpStatus: Int = 0
        private set
    var lastErrorMessage: String = ""
        private set

    @Volatile
    private var rateLimitCooldownUntil: Long = 0L

    fun isRateLimited(): Boolean {
        return System.currentTimeMillis() < rateLimitCooldownUntil
    }

    override fun getRoute(
        origLat: Double,
        origLng: Double,
        destLat: Double,
        destLng: Double,
        profile: RouteProfile
    ): RouteResult? = runCatching {
        val apiKey = apiKeyProvider().trim()
        if (apiKey.isBlank() || apiKey == "YOUR_KEY_HERE" || apiKey == "YOUR_OPENROUTESERVICE_KEY") {
            lastErrorMessage = "ORS API key not configured in secrets.properties"
            lastHttpStatus = 0
            Log.w(TAG, "OpenRouteService skipped: $lastErrorMessage")
            return null
        }

        if (isRateLimited()) {
            val remainingSec = (rateLimitCooldownUntil - System.currentTimeMillis()) / 1000
            lastErrorMessage = "ORS rate limit in effect (cooldown remaining: ${remainingSec}s)"
            Log.w(TAG, "OpenRouteService rate-limit active, skipping request: $lastErrorMessage")
            return null
        }

        val endpointUrl = "$BASE_URL/${profile.orsProfile}"
        Log.d(TAG, "OpenRouteService Request:")
        Log.d(TAG, "  URL = $endpointUrl")
        Log.d(TAG, "  Coordinates GeoJSON: [[$origLng, $origLat], [$destLng, $destLat]]")

        // Build GeoJSON coordinates array: [longitude, latitude]
        val body = JSONObject().apply {
            val coords = JSONArray().apply {
                put(JSONArray().apply { put(origLng); put(origLat) })
                put(JSONArray().apply { put(destLng); put(destLat) })
            }
            put("coordinates", coords)
        }

        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("Accept", "application/json, application/geo+json")
            val authHeader = if (apiKey.startsWith("Bearer ", ignoreCase = true)) apiKey else "Bearer $apiKey"
            setRequestProperty("Authorization", authHeader)
        }

        connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        val status = connection.responseCode
        lastHttpStatus = status

        // Log rate limit headers if present
        val rlLimit = connection.getHeaderField("X-Ratelimit-Limit")
        val rlRemaining = connection.getHeaderField("X-Ratelimit-Remaining")
        val rlReset = connection.getHeaderField("X-Ratelimit-Reset")
        if (rlLimit != null || rlRemaining != null || rlReset != null) {
            Log.d(TAG, "ORS Rate Limits: Limit=$rlLimit, Remaining=$rlRemaining, Reset=$rlReset")
        }

        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        Log.d(TAG, "OpenRouteService response status: $status")

        if (status == 429) {
            rateLimitCooldownUntil = System.currentTimeMillis() + RATE_LIMIT_COOLDOWN_MS
            lastErrorMessage = "HTTP 429: OpenRouteService rate limit exceeded. Cooling down for 60s."
            Log.e(TAG, lastErrorMessage)
            return null
        }

        if (status !in 200..299) {
            lastErrorMessage = parseOrsError(status, responseBody)
            Log.e(TAG, "OpenRouteService error: $lastErrorMessage")
            return null
        }

        val json = JSONObject(responseBody)
        val (distMeters, durSeconds) = parseDistanceAndDuration(json)
            ?: run {
                lastErrorMessage = "Unable to parse distance/duration from ORS response"
                Log.w(TAG, lastErrorMessage)
                return null
            }

        if (distMeters <= 0) {
            lastErrorMessage = "ORS returned 0 distance"
            return null
        }

        lastErrorMessage = "OK"
        RouteResult(
            distanceMeters = distMeters,
            duration = "${durSeconds}s",
            durationSeconds = durSeconds,
            provider = RoutingProviderName.OPENROUTESERVICE,
            success = true
        )
    }.getOrElse { err ->
        Log.e(TAG, "OpenRouteService execution failed", err)
        lastErrorMessage = err.localizedMessage ?: "Unknown network error"
        null
    }

    companion object {
        const val TAG = "OpenRouteService"
        // Active HeiGIT OpenRouteService endpoint (api.openrouteservice.org is deprecated)
        const val BASE_URL = "https://api.heigit.org/openrouteservice/v2/directions"
        const val RATE_LIMIT_COOLDOWN_MS = 60_000L

        fun parseDistanceAndDuration(json: JSONObject): Pair<Int, Long>? {
            // Check 'routes' array format
            val routes = json.optJSONArray("routes")
            if (routes != null && routes.length() > 0) {
                val routeObj = routes.optJSONObject(0)
                val summary = routeObj?.optJSONObject("summary")
                if (summary != null) {
                    val distance = summary.optDouble("distance", 0.0).toInt()
                    val duration = summary.optDouble("duration", 0.0).toLong()
                    return Pair(distance, duration)
                }
            }

            // Check 'features' array (GeoJSON format)
            val features = json.optJSONArray("features")
            if (features != null && features.length() > 0) {
                val featureObj = features.optJSONObject(0)
                val properties = featureObj?.optJSONObject("properties")
                val summary = properties?.optJSONObject("summary")
                if (summary != null) {
                    val distance = summary.optDouble("distance", 0.0).toInt()
                    val duration = summary.optDouble("duration", 0.0).toLong()
                    return Pair(distance, duration)
                }
            }

            return null
        }

        fun parseOrsError(status: Int, responseBody: String): String {
            return try {
                val json = JSONObject(responseBody)
                val errorObj = json.optJSONObject("error")
                val msg = errorObj?.optString("message") ?: json.optString("message", "")
                if (msg.isNotBlank()) "HTTP $status: $msg"
                else "HTTP $status: $responseBody"
            } catch (e: Exception) {
                "HTTP $status: $responseBody"
            }
        }

        fun formatDuration(seconds: Long): String {
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
    }
}
