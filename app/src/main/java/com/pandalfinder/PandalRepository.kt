package com.pandalfinder

import android.content.Context
import android.location.Location
import java.util.Locale

/** Single source of local pandal data. Keeping the UI behind this class makes a JSON
 * catalogue or a seasonal download a drop-in replacement later. */
class PandalRepository(@Suppress("UNUSED_PARAMETER") context: Context) {
    private val pandals: List<Pandal> = Pandal.getLocalPandals()
        .map { pandal ->
            pandal.copy(
                id = pandal.name.lowercase(Locale.US)
                    .replace(Regex("[^a-z0-9]+"), "-")
                    .trim('-') + "-${"%.5f".format(Locale.US, pandal.latitude)}"
                , area = pandal.area.takeUnless { it == "Kolkata" } ?: areaFor(pandal)
            )
        }
        .distinctBy { "${it.name.lowercase(Locale.US)}:${it.latitude}:${it.longitude}" }

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

    /** Broad locality labels derived only from the supplied coordinate catalogue. */
    private fun areaFor(pandal: Pandal): String = when {
        pandal.longitude < 88.345 -> "West Kolkata / Howrah"
        pandal.longitude > 88.400 -> "Salt Lake & East Kolkata"
        pandal.latitude >= 22.585 -> "North Kolkata"
        pandal.latitude >= 22.545 -> "Central Kolkata"
        else -> "South Kolkata"
    }
}
