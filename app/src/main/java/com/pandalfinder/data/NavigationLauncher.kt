package com.pandalfinder.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.location.Location
import android.net.Uri
import com.pandalfinder.Pandal

object NavigationLauncher {
    /** Open Google Maps navigation to a single pandal. */
    fun open(context: Context, pandal: Pandal) {
        val url = "https://www.google.com/maps/dir/?api=1&destination=${pandal.latitude},${pandal.longitude}&travelmode=walking"
        launchMapsUrl(context, url)
    }

    /** Open Google Maps navigation to a metro station. */
    fun openMetroRoute(context: Context, station: MetroStation) {
        val url = "https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}&travelmode=walking"
        launchMapsUrl(context, url)
    }

    /**
     * Open Google Maps multi-stop directions for the Hopping itinerary.
     * Preserves the sequence of stops: Origin -> Stop 1 -> Stop 2 -> ... -> Last Stop
     */
    fun startHopping(context: Context, origin: Location?, stops: List<Pandal>) {
        if (stops.isEmpty()) return

        val lastStop = stops.last()
        val intermediates = if (stops.size > 1) stops.subList(0, stops.size - 1) else emptyList()

        val builder = StringBuilder("https://www.google.com/maps/dir/?api=1")
        origin?.let {
            builder.append("&origin=${it.latitude},${it.longitude}")
        }
        builder.append("&destination=${lastStop.latitude},${lastStop.longitude}")

        if (intermediates.isNotEmpty()) {
            val waypointsStr = intermediates.joinToString("%7C") { "${it.latitude},${it.longitude}" }
            builder.append("&waypoints=$waypointsStr")
        }
        builder.append("&travelmode=driving")

        launchMapsUrl(context, builder.toString())
    }

    private fun launchMapsUrl(context: Context, url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.apps.maps")
        try {
            context.startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        }
    }
}
