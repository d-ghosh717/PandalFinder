package com.pandalfinder.data

import android.location.Location
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.pandalfinder.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/**
 * Repository for querying public toilets/restrooms using Google Places API (New).
 *
 * Uses the Nearby Search endpoint:
 *   POST https://places.googleapis.com/v1/places:searchNearby
 *
 * Requires the same API key used by Google Routes (stored in secrets.properties
 * as ROUTES_API_KEY and exposed through BuildConfig.GOOGLE_ROUTES_API_KEY).
 * The key must have Places API (New) enabled in Google Cloud Console.
 */
class GooglePlacesRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = mutableMapOf<String, Pair<Long, List<PublicToilet>>>()
    var cachedToilets: List<PublicToilet> = emptyList()
        private set

    fun fetchNearbyToilets(
        origin: Location,
        radiusMeters: Double = 5000.0,
        onResult: (List<PublicToilet>?, String?) -> Unit
    ) {
        val apiKey = BuildConfig.GOOGLE_ROUTES_API_KEY
        if (apiKey.isBlank()) {
            val errorMsg = "Google API Key is blank in BuildConfig. " +
                "Ensure secrets.properties contains ROUTES_API_KEY=<your-key> and rebuild."
            Log.e(TAG, errorMsg)
            Log.e(TAG, "BuildConfig.GOOGLE_ROUTES_API_KEY = '${apiKey.take(8)}...' (length=${apiKey.length})")
            onResult(null, errorMsg)
            return
        }

        Log.d(TAG, "fetchNearbyToilets: key present (${apiKey.take(8)}…), " +
            "origin=(${origin.latitude}, ${origin.longitude}), radius=$radiusMeters")

        val cacheKey = "${"%.3f".format(origin.latitude)}:${"%.3f".format(origin.longitude)}:$radiusMeters"
        val now = System.currentTimeMillis()
        cache[cacheKey]?.takeIf { now - it.first < CACHE_MILLIS }?.let {
            Log.d(TAG, "Returning ${it.second.size} cached toilets for key=$cacheKey")
            cachedToilets = it.second
            onResult(it.second, null)
            return
        }

        executor.execute {
            val (toilets, error) = executeNearbySearch(origin.latitude, origin.longitude, radiusMeters, apiKey)
            if (toilets != null) {
                cache[cacheKey] = now to toilets
                cachedToilets = toilets
            }
            main.post {
                onResult(toilets, error)
            }
        }
    }

    private fun executeNearbySearch(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        apiKey: String
    ): Pair<List<PublicToilet>?, String?> = runCatching {
        val requestBody = JSONObject().apply {
            put("includedTypes", JSONArray().apply {
                put("public_bathroom")
            })
            put("maxResultCount", 20)
            put("rankPreference", "DISTANCE")
            put("locationRestriction", JSONObject().apply {
                put("circle", JSONObject().apply {
                    put("center", JSONObject().apply {
                        put("latitude", lat)
                        put("longitude", lng)
                    })
                    put("radius", radiusMeters)
                })
            })
        }

        Log.d(TAG, "Places API Request: POST $NEARBY_SEARCH_URL")
        Log.d(TAG, "Request body: ${requestBody.toString(2)}")

        val connection = (URL(NEARBY_SEARCH_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 15_000
            readTimeout = 15_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey)
            setRequestProperty("X-Goog-FieldMask",
                "places.id,places.displayName,places.formattedAddress,places.location,places.types")
        }

        connection.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        Log.d(TAG, "Places API Response: HTTP $status")

        if (status !in 200..299) {
            // Extract detailed Google error information
            val errorMsg = try {
                val json = JSONObject(responseBody)
                val errObj = json.optJSONObject("error")
                val code = errObj?.optInt("code", status) ?: status
                val msg = errObj?.optString("message", "Unknown error") ?: "Unknown error"
                val errStatus = errObj?.optString("status", "") ?: ""
                val details = errObj?.optJSONArray("details")

                Log.e(TAG, "┌─── Places API Error ───")
                Log.e(TAG, "│ HTTP Status:    $code")
                Log.e(TAG, "│ Error Status:   $errStatus")
                Log.e(TAG, "│ Error Message:  $msg")
                if (details != null) {
                    Log.e(TAG, "│ Error Details:  ${details.toString(2)}")
                }
                Log.e(TAG, "└────────────────────────")

                "Places API error: $errStatus — $msg"
            } catch (e: Exception) {
                Log.e(TAG, "Raw error response: $responseBody")
                "Places API HTTP $status: ${responseBody.take(200)}"
            }
            return Pair(null, errorMsg)
        }

        Log.d(TAG, "Places API Success: HTTP $status, body length=${responseBody.length}")

        val placesJson = JSONObject(responseBody).optJSONArray("places")
        val toilets = mutableListOf<PublicToilet>()
        if (placesJson != null) {
            Log.d(TAG, "Found ${placesJson.length()} places in response")
            for (i in 0 until placesJson.length()) {
                val p = placesJson.optJSONObject(i) ?: continue
                val id = p.optString("id", "")
                val name = p.optJSONObject("displayName")?.optString("text", "Public Toilet")
                    ?: "Public Toilet"
                val address = p.optString("formattedAddress", "Kolkata, West Bengal")
                val locObj = p.optJSONObject("location")
                val pLat = locObj?.optDouble("latitude", Double.NaN) ?: Double.NaN
                val pLng = locObj?.optDouble("longitude", Double.NaN) ?: Double.NaN

                if (id.isNotBlank() && !pLat.isNaN() && !pLng.isNaN()) {
                    toilets.add(
                        PublicToilet(
                            id = id,
                            name = name,
                            address = address,
                            latitude = pLat,
                            longitude = pLng
                        )
                    )
                    Log.d(TAG, "  [$i] $name @ ($pLat, $pLng)")
                }
            }
        } else {
            Log.d(TAG, "No 'places' array in response — 0 results")
        }

        Log.d(TAG, "Parsed ${toilets.size} valid toilets")
        Pair(toilets, null)
    }.getOrElse { error ->
        val msg = "Network/API error: ${error.localizedMessage ?: "Unknown error"}"
        Log.e(TAG, "Places nearby search failed", error)
        Pair(null, msg)
    }

    private companion object {
        const val TAG = "GooglePlaces"
        const val NEARBY_SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby"
        const val CACHE_MILLIS = 5 * 60 * 1000L
    }
}
