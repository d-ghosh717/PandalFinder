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
 */
class GooglePlacesRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val cache = mutableMapOf<String, Pair<Long, List<PublicToilet>>>()
    var cachedToilets: List<PublicToilet> = emptyList()
        private set

    fun fetchNearbyToilets(
        origin: Location,
        radiusMeters: Double = 3000.0,
        onResult: (List<PublicToilet>?, String?) -> Unit
    ) {
        if (BuildConfig.GOOGLE_ROUTES_API_KEY.isBlank()) {
            val errorMsg = "Google API Key is not configured."
            Log.w(TAG, errorMsg)
            onResult(null, errorMsg)
            return
        }

        val cacheKey = "${"%.3f".format(origin.latitude)}:${"%.3f".format(origin.longitude)}:$radiusMeters"
        val now = System.currentTimeMillis()
        cache[cacheKey]?.takeIf { now - it.first < CACHE_MILLIS }?.let {
            cachedToilets = it.second
            onResult(it.second, null)
            return
        }

        executor.execute {
            val (toilets, error) = executeNearbySearch(origin.latitude, origin.longitude, radiusMeters)
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
        radiusMeters: Double
    ): Pair<List<PublicToilet>?, String?> = runCatching {
        val requestBody = JSONObject().apply {
            put("includedTypes", JSONArray().apply {
                put("public_bathroom")
            })
            put("maxResultCount", 20)
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

        val connection = (URL(NEARBY_SEARCH_URL).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 10_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", BuildConfig.GOOGLE_ROUTES_API_KEY)
            setRequestProperty("X-Goog-FieldMask", "places.id,places.displayName,places.formattedAddress,places.location")
        }

        connection.outputStream.bufferedWriter().use { it.write(requestBody.toString()) }
        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (status !in 200..299) {
            val errorMsg = try {
                val json = JSONObject(responseBody)
                val errObj = json.optJSONObject("error")
                val msg = errObj?.optString("message") ?: "HTTP $status error"
                val errStatus = errObj?.optString("status") ?: ""
                "Places API Error: $msg ($errStatus)".trim()
            } catch (e: Exception) {
                "Places API HTTP $status: $responseBody"
            }
            Log.e(TAG, errorMsg)
            return Pair(null, errorMsg)
        }

        val placesJson = JSONObject(responseBody).optJSONArray("places")
        val toilets = mutableListOf<PublicToilet>()
        if (placesJson != null) {
            for (i in 0 until placesJson.length()) {
                val p = placesJson.optJSONObject(i) ?: continue
                val id = p.optString("id", "")
                val name = p.optJSONObject("displayName")?.optString("text", "Public Toilet") ?: "Public Toilet"
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
                }
            }
        }

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
