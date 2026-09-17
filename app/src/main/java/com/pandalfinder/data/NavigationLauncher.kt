package com.pandalfinder.data

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.pandalfinder.Pandal

object NavigationLauncher {
    /** Open Google Maps navigation to the pandal. */
    fun open(context: Context, pandal: Pandal) {
        val url = "https://www.google.com/maps/dir/?api=1&destination=${pandal.latitude},${pandal.longitude}&travelmode=walking"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.apps.maps")
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    /** Open Google Maps navigation to a metro station. */
    fun openMetroRoute(context: Context, station: MetroStation) {
        val url = "https://www.google.com/maps/dir/?api=1&destination=${station.latitude},${station.longitude}&travelmode=walking"
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).setPackage("com.google.android.apps.maps")
        try { context.startActivity(intent) }
        catch (_: ActivityNotFoundException) { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }
}
