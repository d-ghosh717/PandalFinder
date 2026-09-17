package com.pandalfinder

import android.content.Context
import android.location.Location
import android.util.Log
import com.pandalfinder.data.MetroStation
import java.util.Locale

sealed class SearchResult {
    abstract val title: String
    abstract val subtitle: String
    abstract val distanceMeters: Float
    abstract val latitude: Double
    abstract val longitude: Double

    data class PandalResult(
        val pandal: Pandal,
        override val distanceMeters: Float = pandal.distanceMeters
    ) : SearchResult() {
        override val title: String get() = pandal.name
        override val subtitle: String get() = pandal.area
        override val latitude: Double get() = pandal.latitude
        override val longitude: Double get() = pandal.longitude
    }

    data class MetroStationResult(
        val station: MetroStation,
        override val distanceMeters: Float = 0f
    ) : SearchResult() {
        override val title: String get() = "${station.name} Metro"
        override val subtitle: String get() = "${station.line} • Station"
        override val latitude: Double get() = station.latitude
        override val longitude: Double get() = station.longitude
    }
}

/** Single source of local pandal data. Keeping the UI behind this class makes a JSON
 * catalogue or a seasonal download a drop-in replacement later. */
class PandalRepository(@Suppress("UNUSED_PARAMETER") context: Context) {
    companion object {
        private const val TAG = "PandalRepository"
        
        /** Sanity bounding box covering Greater Kolkata, Howrah, Salt Lake, and New Town */
        fun isValidKolkataCoordinate(lat: Double, lng: Double): Boolean {
            return lat in 22.35..22.75 && lng in 88.20..88.55
        }
    }

    private val pandals: List<Pandal> = Pandal.getLocalPandals()
        .filter { pandal ->
            val valid = isValidKolkataCoordinate(pandal.latitude, pandal.longitude)
            if (!valid) {
                Log.w(TAG, "Sanity check rejected invalid coordinate for pandal: ${pandal.name} (${pandal.latitude}, ${pandal.longitude})")
            }
            valid
        }
        .map { pandal ->
            pandal.copy(
                id = pandal.name.lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-') + "-${"%.5f".format(Locale.US, pandal.latitude)}"
                , area = pandal.area.takeUnless { it == "Kolkata" } ?: areaFor(pandal)
            )
        }
        .distinctBy { "${it.name.lowercase(Locale.US)}:${it.latitude}:${it.longitude}" }

    private val metroStations: List<MetroStation> = MetroStation.allStations()

    fun all(): List<Pandal> = pandals

    fun nearby(location: Location): List<Pandal> =
        pandals.map { it.withDistanceFrom(location) }.sortedBy { it.distanceMeters }

    fun search(query: String, location: Location?): List<Pandal> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()
        val matches = pandals
            .filter { it.name.contains(normalized, true) || it.area.contains(normalized, true) }
            .map { if (location == null) it else it.withDistanceFrom(location) }
        return if (location == null) matches.sortedBy { it.name } else matches.sortedBy { it.distanceMeters }
    }

    fun searchAll(query: String, location: Location?): List<SearchResult> {
        val normalized = query.trim()
        if (normalized.isBlank()) return emptyList()

        val pandalResults = pandals
            .filter { it.name.contains(normalized, true) || it.area.contains(normalized, true) }
            .map { pandal ->
                val p = if (location != null) pandal.withDistanceFrom(location) else pandal
                SearchResult.PandalResult(p, p.distanceMeters)
            }

        val metroResults = metroStations
            .filter { it.name.contains(normalized, true) || it.line.contains(normalized, true) }
            .map { station ->
                val dist = if (location != null) {
                    val sLoc = Location("").apply {
                        latitude = station.latitude
                        longitude = station.longitude
                    }
                    location.distanceTo(sLoc)
                } else 0f
                SearchResult.MetroStationResult(station, dist)
            }

        val combined = (pandalResults + metroResults)
        return if (location != null) {
            combined.sortedBy { it.distanceMeters }
        } else {
            combined.sortedBy { it.title }
        }
    }

    /** Broad locality labels derived only from the supplied coordinate catalogue. */
    private fun areaFor(pandal: Pandal): String = when {
        pandal.longitude < 88.345 -> "West Kolkata / Howrah"
        pandal.longitude > 88.400 -> "Salt Lake & East Kolkata"
        pandal.latitude >= 22.585 -> "North Kolkata"
        pandal.latitude >= 22.545 -> "Central Kolkata"
        else -> "South Kolkata"
    }
}
