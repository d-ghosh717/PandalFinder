package com.pandalfinder.data

enum class RouteProfile(val orsProfile: String, val googleMode: String) {
    DRIVING("driving-car", "DRIVE"),
    WALKING("foot-walking", "WALK"),
    CYCLING("cycling-regular", "TWO_WHEELER")
}

enum class RoutingProviderName {
    GOOGLE,
    OPENROUTESERVICE,
    UNAVAILABLE
}

data class RouteResult(
    val distanceMeters: Int,
    val duration: String,
    val durationSeconds: Long = 0L,
    val provider: RoutingProviderName = RoutingProviderName.GOOGLE,
    val success: Boolean = true,
    val error: String? = null
)

data class PandalRoutes(
    val walk: RouteResult?,
    val twoWheeler: RouteResult?,
    val drive: RouteResult?,
    val activeProvider: RoutingProviderName = RoutingProviderName.UNAVAILABLE
)

data class HoppingLeg(
    val distanceMeters: Int,
    val duration: String,
    val durationSeconds: Long = 0L,
    val provider: RoutingProviderName = RoutingProviderName.GOOGLE
)

data class HoppingRoute(
    val totalDistanceMeters: Int,
    val totalDurationFormatted: String,
    val legDistances: List<Int>,
    val provider: RoutingProviderName = RoutingProviderName.GOOGLE
)

interface RoutingProvider {
    val providerName: RoutingProviderName
    fun getRoute(
        origLat: Double,
        origLng: Double,
        destLat: Double,
        destLng: Double,
        profile: RouteProfile
    ): RouteResult?
}
