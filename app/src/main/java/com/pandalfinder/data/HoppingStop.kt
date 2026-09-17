package com.pandalfinder.data

import com.pandalfinder.Pandal

enum class StopType {
    PANDAL,
    METRO,
    TOILET
}

/**
 * Unified stop item for the Hopping itinerary.
 * Supports Pandals and Metro Stations explicitly (and ready for Toilets).
 */
data class HoppingStop(
    val id: String,
    val name: String,
    val type: StopType,
    val latitude: Double,
    val longitude: Double,
    val subtitle: String,
    val pandalRef: Pandal? = null,
    val metroRef: MetroStation? = null,
    val toiletRef: PublicToilet? = null
) {
    companion object {
        fun fromPandal(pandal: Pandal): HoppingStop = HoppingStop(
            id = "pandal:${pandal.id}",
            name = pandal.name,
            type = StopType.PANDAL,
            latitude = pandal.latitude,
            longitude = pandal.longitude,
            subtitle = pandal.area,
            pandalRef = pandal
        )

        fun fromMetro(station: MetroStation): HoppingStop = HoppingStop(
            id = "metro:${station.name.lowercase().replace(" ", "_")}",
            name = station.name,
            type = StopType.METRO,
            latitude = station.latitude,
            longitude = station.longitude,
            subtitle = "Metro • ${station.line}",
            metroRef = station
        )

        fun fromToilet(toilet: PublicToilet): HoppingStop = HoppingStop(
            id = "toilet:${toilet.id}",
            name = toilet.name,
            type = StopType.TOILET,
            latitude = toilet.latitude,
            longitude = toilet.longitude,
            subtitle = toilet.address,
            toiletRef = toilet
        )
    }
}
