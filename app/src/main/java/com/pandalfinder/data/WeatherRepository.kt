package com.pandalfinder.data

import android.os.Handler
import android.os.Looper
import com.pandalfinder.Pandal
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

data class WeatherStatus(
    val label: String,
    val emoji: String,
    val detail: String,
    val fetchedAt: Long
)

/** Lightweight, 15-minute in-memory cache. Weather is fetched only for an opened sheet. */
class WeatherRepository {
    private val cache = mutableMapOf<String, WeatherStatus>()
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())

    fun currentFor(pandal: Pandal, onResult: (WeatherStatus?) -> Unit) {
        val key = "%.3f,%.3f".format(pandal.latitude, pandal.longitude)
        val now = System.currentTimeMillis()
        cache[key]?.takeIf { now - it.fetchedAt < CACHE_MILLIS }?.let {
            onResult(it)
            return
        }
        executor.execute {
            val result = runCatching {
                val endpoint = "https://api.open-meteo.com/v1/forecast?latitude=${pandal.latitude}" +
                    "&longitude=${pandal.longitude}&current=weather_code,precipitation"
                val connection = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 6_000
                    readTimeout = 6_000
                }
                connection.inputStream.bufferedReader().use { reader ->
                    val current = JSONObject(reader.readText()).getJSONObject("current")
                    val code = current.optInt("weather_code", -1)
                    val precipitation = current.optDouble("precipitation", 0.0)
                    WeatherStatus(
                        weatherLabel(code, precipitation),
                        weatherEmoji(code, precipitation),
                        "Live weather from Open-Meteo",
                        now
                    )
                }
            }.getOrNull()
            result?.let { cache[key] = it }
            main.post { onResult(result) }
        }
    }

    private fun weatherLabel(code: Int, precipitation: Double): String = when {
        code in setOf(95, 96, 99) -> "Heavy rain"
        precipitation >= 2.5 || code in 65..82 -> "Raining"
        precipitation > 0.0 || code in 51..63 -> "Drizzle"
        else -> "No rain"
    }

    private fun weatherEmoji(code: Int, precipitation: Double): String = when {
        code in setOf(95, 96, 99) -> "⛈"
        precipitation >= 2.5 || code in 65..82 -> "🌧"
        precipitation > 0.0 || code in 51..63 -> "🌦"
        else -> "☀"
    }

    private companion object { const val CACHE_MILLIS = 15 * 60 * 1000L }
}
