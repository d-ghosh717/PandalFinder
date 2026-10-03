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
import java.util.Locale
import java.util.concurrent.Executors

/**
 * Repository for querying public toilets/restrooms using Google Places API (New).
 *
 * Uses the Nearby Search endpoint:
 *   POST https://places.googleapis.com/v1/places:searchNearby
 * With fallback to Text Search:
 *   POST https://places.googleapis.com/v1/places:searchText
 *
 * Emits structured Logcat diagnostics under the tag [DIAGNOSTIC_TAG].
 */
class GooglePlacesRepository {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    
    // In-memory cache for 5 minutes
    private var cachedLocation: Pair<Double, Double>? = null
    private var cachedTimestamp: Long = 0
    var cachedToilets: List<PublicToilet> = emptyList()
        private set

    fun fetchNearbyToilets(
        origin: Location,
        radiusMeters: Double = DEFAULT_SEARCH_RADIUS_METERS,
        onResult: (List<PublicToilet>?, String?) -> Unit
    ) {
        val apiKey = BuildConfig.GOOGLE_ROUTES_API_KEY
        if (apiKey.isBlank()) {
            val errorMsg = "Toilets unavailable: Places API configuration required (API Key is blank)"
            Log.e(DIAGNOSTIC_TAG, "Places request failed: API key is blank in BuildConfig")
            onResult(null, errorMsg)
            return
        }

        val lat = origin.latitude
        val lng = origin.longitude
        val now = System.currentTimeMillis()

        Log.d(DIAGNOSTIC_TAG, "Current GPS: latitude=$lat, longitude=$lng")
        Log.d(DIAGNOSTIC_TAG, "Places request starting for radius=${radiusMeters}m")

        // Check 5-minute cache and displacement threshold (< 200m)
        val lastLoc = cachedLocation
        if (lastLoc != null && (now - cachedTimestamp) < CACHE_MILLIS) {
            val dist = FloatArray(1)
            Location.distanceBetween(lat, lng, lastLoc.first, lastLoc.second, dist)
            if (dist[0] < CACHE_DISPLACEMENT_THRESHOLD_METERS && cachedToilets.isNotEmpty()) {
                Log.d(DIAGNOSTIC_TAG, "Returning ${cachedToilets.size} cached toilets (distance moved: ${dist[0]}m < 200m)")
                onResult(cachedToilets, null)
                return
            }
        }

        executor.execute {
            val (toilets, error) = executePlacesSearch(lat, lng, radiusMeters, apiKey)
            if (toilets != null) {
                cachedLocation = Pair(lat, lng)
                cachedTimestamp = now
                cachedToilets = toilets
            }
            main.post {
                onResult(toilets, error)
            }
        }
    }

    private fun executePlacesSearch(
        lat: Double,
        lng: Double,
        radiusMeters: Double,
        apiKey: String
    ): Pair<List<PublicToilet>?, String?> {
        // 1. Try Places API (New) Nearby Search
        Log.d(DIAGNOSTIC_TAG, "API method being used: Places API (New) searchNearby")
        Log.d(DIAGNOSTIC_TAG, "requested radius: ${radiusMeters}m")
        Log.d(DIAGNOSTIC_TAG, "requested place types: [public_bathroom, public_bath]")

        val nearbyBody = JSONObject().apply {
            put("includedTypes", JSONArray().apply {
                put("public_bathroom")
                put("public_bath")
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

        val (nearbyToilets, nearbyError, httpCode) = postPlacesApi(NEARBY_SEARCH_URL, nearbyBody, apiKey)

        if (nearbyToilets != null && nearbyToilets.isNotEmpty()) {
            val deduped = deduplicateToilets(nearbyToilets)
            Log.d(DIAGNOSTIC_TAG, "Places response count: ${nearbyToilets.size}")
            Log.d(DIAGNOSTIC_TAG, "Toilet candidates: ${deduped.size}")
            Log.d(DIAGNOSTIC_TAG, "parsed toilet count: ${deduped.size}")
            return Pair(deduped, null)
        }

        // If error was a fatal auth/config error, do not attempt fallback
        if (httpCode in listOf(400, 401, 403)) {
            val userMsg = "Toilets unavailable: Places API configuration required"
            Log.e(DIAGNOSTIC_TAG, "Places request failed status=$httpCode: $nearbyError")
            return Pair(null, userMsg)
        }

        // 2. Fallback: Places API (New) Text Search for public toilet keywords
        Log.d(DIAGNOSTIC_TAG, "No results from strict searchNearby. Initiating controlled fallback: Places API (New) searchText")
        Log.d(DIAGNOSTIC_TAG, "requested query: 'public toilet'")

        val textBody = JSONObject().apply {
            put("textQuery", "public toilet")
            put("maxResultCount", 20)
            put("locationBias", JSONObject().apply {
                put("circle", JSONObject().apply {
                    put("center", JSONObject().apply {
                        put("latitude", lat)
                        put("longitude", lng)
                    })
                    put("radius", radiusMeters)
                })
            })
        }

        val (textToilets, textError, textHttpCode) = postPlacesApi(TEXT_SEARCH_URL, textBody, apiKey)

        if (textToilets != null && textToilets.isNotEmpty()) {
            val filtered = filterAndDeduplicateToilets(textToilets)
            Log.d(DIAGNOSTIC_TAG, "Places response count: ${textToilets.size}")
            Log.d(DIAGNOSTIC_TAG, "Toilet candidates: ${filtered.size}")
            Log.d(DIAGNOSTIC_TAG, "parsed toilet count: ${filtered.size}")
            return Pair(filtered, null)
        }

        if (textHttpCode in listOf(400, 401, 403)) {
            val userMsg = "Toilets unavailable: Places API configuration required"
            Log.e(DIAGNOSTIC_TAG, "Places fallback failed status=$textHttpCode: $textError")
            return Pair(null, userMsg)
        }

        Log.d(DIAGNOSTIC_TAG, "Places response count: 0")
        Log.d(DIAGNOSTIC_TAG, "Toilet candidates: 0")
        Log.d(DIAGNOSTIC_TAG, "parsed toilet count: 0")
        return Pair(emptyList(), null)
    }

    private fun postPlacesApi(
        endpointUrl: String,
        body: JSONObject,
        apiKey: String
    ): Triple<List<PublicToilet>?, String?, Int> = try {
        val connection = (URL(endpointUrl).openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 12_000
            readTimeout = 12_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("X-Goog-Api-Key", apiKey)
            setRequestProperty(
                "X-Goog-FieldMask",
                "places.id,places.displayName,places.formattedAddress,places.location,places.types,places.rating"
            )
        }

        connection.outputStream.bufferedWriter().use { it.write(body.toString()) }
        val status = connection.responseCode
        val responseBody = (if (status in 200..299) connection.inputStream else connection.errorStream)
            ?.bufferedReader()?.use { it.readText() }.orEmpty()

        if (status !in 200..299) {
            val errMsg = try {
                val json = JSONObject(responseBody)
                val errObj = json.optJSONObject("error")
                val code = errObj?.optInt("code", status) ?: status
                val msg = errObj?.optString("message", "Unknown error") ?: "Unknown error"
                val errStatus = errObj?.optString("status", "") ?: ""
                "HTTP $code ($errStatus): $msg"
            } catch (e: Exception) {
                "HTTP $status: ${responseBody.take(150)}"
            }
            Log.e(DIAGNOSTIC_TAG, "HTTP/API error code: $status")
            Log.e(DIAGNOSTIC_TAG, "error message: $errMsg")
            Triple(null, errMsg, status)
        } else {
            val toilets = parsePlacesResponse(responseBody)
            Triple(toilets, null, status)
        }
    } catch (e: Exception) {
        val msg = e.localizedMessage ?: "Network error"
        Log.e(DIAGNOSTIC_TAG, "Places API network exception: $msg", e)
        Triple(null, msg, -1)
    }

    companion object {
        const val TAG = "GooglePlacesRepository"
        const val DIAGNOSTIC_TAG = "PandalQuest-Toilets"
        const val NEARBY_SEARCH_URL = "https://places.googleapis.com/v1/places:searchNearby"
        const val TEXT_SEARCH_URL = "https://places.googleapis.com/v1/places:searchText"
        const val DEFAULT_SEARCH_RADIUS_METERS = 1000.0
        const val CACHE_MILLIS = 5 * 60 * 1000L
        const val CACHE_DISPLACEMENT_THRESHOLD_METERS = 200f

        /**
         * Parses Places API (New) JSON payload into a list of [PublicToilet].
         */
        fun parsePlacesResponse(responseBody: String): List<PublicToilet> {
            val toilets = mutableListOf<PublicToilet>()
            val json = try {
                JSONObject(responseBody)
            } catch (e: Exception) {
                return emptyList()
            }
            val placesJson = json.optJSONArray("places") ?: return emptyList()

            for (i in 0 until placesJson.length()) {
                val p = placesJson.optJSONObject(i) ?: continue
                val id = p.optString("id", "")
                val name = p.optJSONObject("displayName")?.optString("text", "Public Toilet")
                    ?: "Public Toilet"
                val address = p.optString("formattedAddress", "Kolkata, West Bengal")
                val locObj = p.optJSONObject("location")
                val pLat = locObj?.optDouble("latitude", Double.NaN) ?: Double.NaN
                val pLng = locObj?.optDouble("longitude", Double.NaN) ?: Double.NaN
                val rating = if (p.has("rating")) p.optDouble("rating") else null

                val typesList = mutableListOf<String>()
                val typesArray = p.optJSONArray("types")
                if (typesArray != null) {
                    for (j in 0 until typesArray.length()) {
                        typesList.add(typesArray.optString(j))
                    }
                }

                if (id.isNotBlank() && !pLat.isNaN() && !pLng.isNaN()) {
                    toilets.add(
                        PublicToilet(
                            id = id,
                            name = name,
                            address = address,
                            latitude = pLat,
                            longitude = pLng,
                            types = typesList,
                            rating = rating
                        )
                    )
                }
            }
            return deduplicateToilets(toilets)
        }

        /**
         * Deduplicates toilets by place ID.
         */
        fun deduplicateToilets(toilets: List<PublicToilet>): List<PublicToilet> {
            return toilets.distinctBy { it.id }
        }

        /**
         * Filters text search candidates to ensure they are authentic public restrooms,
         * avoiding general businesses like restaurants, hotels, or cafes unless explicitly named a toilet/restroom.
         */
        fun filterAndDeduplicateToilets(toilets: List<PublicToilet>): List<PublicToilet> {
            val keywords = listOf(
                "toilet", "bathroom", "restroom", "washroom", "sauchalaya",
                "shouchalaya", "lavatory", "sulabh", "pay and use", "public urinal", "kmc toilet"
            )
            val excludeKeywords = listOf(
                "restaurant", "cafe", "hotel", "resort", "dhaba", "bar", "pub", "supermarket", "salon", "spa"
            )

            return deduplicateToilets(toilets).filter { toilet ->
                val nameLower = toilet.name.lowercase(Locale.US)
                val isExplicitToilet = keywords.any { nameLower.contains(it) } ||
                        toilet.types.any { it == "public_bathroom" || it == "public_bath" }

                val isExcluded = excludeKeywords.any { nameLower.contains(it) } && !nameLower.contains("sulabh")

                isExplicitToilet && !isExcluded
            }
        }
    }
}
