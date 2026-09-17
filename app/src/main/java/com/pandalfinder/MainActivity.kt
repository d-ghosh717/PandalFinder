package com.pandalfinder

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.os.Bundle
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.pandalfinder.data.*
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MainActivity : AppCompatActivity() {

    // ── Views ──
    private lateinit var mapView: MapView
    private lateinit var searchInput: TextInputEditText
    private lateinit var results: RecyclerView
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var allowButton: MaterialButton

    // ── Data ──
    private lateinit var pandals: PandalRepository
    private lateinit var weather: WeatherRepository
    private lateinit var crowd: CrowdRepository
    private lateinit var metro: MetroRepository

    // ── State ──
    private var map: MapLibreMap? = null
    private var location: Location? = null
    private var shown = emptyList<Pandal>()
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var currentDetailPandal: Pandal? = null
    private var currentDetailSheetView: View? = null

    // ────────────────────────────────────────────────────────
    //  Lifecycle
    // ────────────────────────────────────────────────────────

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        
        // Initialize Firebase manually
        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            com.google.firebase.FirebaseApp.initializeApp(this, com.google.firebase.FirebaseOptions.Builder()
                .setApiKey("AIzaSyBFfA06Yr7eIyXMCaEE8RWPvcSFRnau8Pw")
                .setApplicationId("1:333025667530:web:0d3f2ca9275298fb694733")
                .setDatabaseUrl("https://pandalquest-default-rtdb.firebaseio.com")
                .setProjectId("pandalquest")
                .setStorageBucket("pandalquest.firebasestorage.app")
                .build())
        }

        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        pandals = PandalRepository(this)
        weather = WeatherRepository()
        crowd = CrowdRepository(this)
        metro = MetroRepository()

        mapView = findViewById(R.id.mapView)
        searchInput = findViewById(R.id.searchInput)
        results = findViewById(R.id.searchResults)
        statusTitle = findViewById(R.id.statusTitle)
        statusMessage = findViewById(R.id.statusMessage)
        allowButton = findViewById(R.id.allowLocationButton)

        results.layoutManager = LinearLayoutManager(this)
        allowButton.setOnClickListener { requestLocationPermission() }
        findViewById<MaterialButton>(R.id.nearbyFilter).setOnClickListener { requestNearby() }

        mapView.onCreate(state)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.setOnMarkerClickListener { marker ->
                shown.firstOrNull {
                    it.longitude == marker.position.longitude && it.latitude == marker.position.latitude
                }?.let(::showDetail)
                true
            }
            readyMap.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                render(pandals.all())
            }
        }

        searchInput.doAfterTextChanged { text ->
            val matching = pandals.search(text?.toString().orEmpty(), location).take(8)
            results.visibility = if (matching.isEmpty()) View.GONE else View.VISIBLE
            results.adapter = SearchAdapter(matching) { pandal ->
                searchInput.setText("")
                results.visibility = View.GONE
                focus(pandal)
                showDetail(pandal)
            }
        }

        showLocationExplanation()
    }

    override fun onStart() { super.onStart(); mapView.onStart() }
    override fun onResume() { super.onResume(); mapView.onResume() }
    override fun onPause() { mapView.onPause(); super.onPause() }
    override fun onStop() { mapView.onStop(); super.onStop() }
    override fun onDestroy() { mapView.onDestroy(); super.onDestroy() }

    // ────────────────────────────────────────────────────────
    //  Location
    // ────────────────────────────────────────────────────────

    private fun showLocationExplanation() {
        if (hasLocation()) {
            requestNearby()
        } else {
            statusTitle.text = getString(R.string.location_explanation_title)
            statusMessage.text = getString(R.string.location_explanation_body)
            allowButton.visibility = View.VISIBLE
            render(pandals.all())
        }
    }

    private fun requestLocationPermission() = requestPermissions(
        arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION),
        REQUEST_LOCATION
    )

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, grants: IntArray) {
        super.onRequestPermissionsResult(code, permissions, grants)
        if (code == REQUEST_LOCATION) {
            if (hasLocation()) {
                requestNearby()
            } else {
                statusTitle.text = getString(R.string.location_denied_title)
                statusMessage.text = getString(R.string.location_denied_body)
                allowButton.visibility = View.VISIBLE
            }
        }
    }

    private fun requestNearby() {
        if (!hasLocation()) return showLocationExplanation()
        allowButton.visibility = View.GONE
        statusTitle.text = getString(R.string.location_finding)
        statusMessage.text = getString(R.string.location_getting)

        val request = com.google.android.gms.location.LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 10000)
            .setMinUpdateDistanceMeters(10f)
            .build()

        if (locationCallback == null) {
            locationCallback = object : com.google.android.gms.location.LocationCallback() {
                override fun onLocationResult(result: com.google.android.gms.location.LocationResult) {
                    val found = result.lastLocation ?: return
                    location = found
                    val nearby = pandals.nearby(found)
                    render(nearby)
                    statusTitle.text = getString(R.string.location_found, nearby.size)
                    statusMessage.text = getString(R.string.location_tap)
                    
                    if (map?.cameraPosition?.zoom ?: 0.0 < 10.0) {
                        map?.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(found.latitude, found.longitude))
                            .zoom(12.5)
                            .build()
                    }
                    
                    // Update detail sheet if open
                    currentDetailPandal?.let { pandal ->
                        val itemWithDistance = pandal.withDistanceFrom(found)
                        val distStr = itemWithDistance.distanceMeters.takeIf { it > 0 }?.let(::distanceText) ?: getString(R.string.distance_unavailable)
                        currentDetailSheetView?.findViewById<TextView>(R.id.detailAreaDistance)?.text = "${itemWithDistance.area} · $distStr"
                            
                        // Recalculate travel modes
                        currentDetailSheetView?.let { updateTravelEstimates(it, itemWithDistance.distanceMeters) }
                        
                        android.util.Log.d("PandalFinder", "User: ${found.latitude}, ${found.longitude} | Pandal: ${pandal.latitude}, ${pandal.longitude} | Distance: ${itemWithDistance.distanceMeters}m")
                    }
                }
            }
            try {
                LocationServices.getFusedLocationProviderClient(this).requestLocationUpdates(
                    request,
                    locationCallback!!,
                    android.os.Looper.getMainLooper()
                )
            } catch (e: SecurityException) {
                statusTitle.text = getString(R.string.location_unavailable)
                statusMessage.text = getString(R.string.location_enable_gps)
            }
        }
    }

    private fun hasLocation(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
        ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    // ────────────────────────────────────────────────────────
    //  Map rendering
    // ────────────────────────────────────────────────────────

    private fun render(items: List<Pandal>) {
        shown = items
        map?.let { readyMap ->
            readyMap.clear()
            val icons = IconFactory.getInstance(this)
            location?.let {
                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(it.latitude, it.longitude))
                        .icon(icons.fromBitmap(userMarkerBitmap()))
                )
            }
            val icon = icons.fromBitmap(pandalMarkerBitmap())
            items.forEach {
                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(it.latitude, it.longitude))
                        .icon(icon)
                )
            }
        }
    }

    private fun focus(pandal: Pandal) {
        render(listOf(location?.let { pandal.withDistanceFrom(it) } ?: pandal))
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(pandal.latitude, pandal.longitude))
            .zoom(15.5)
            .build()
    }

    // ────────────────────────────────────────────────────────
    //  Custom markers
    // ────────────────────────────────────────────────────────

    /** Clean teardrop pin in vermilion with white accent. */
    private fun pandalMarkerBitmap(): Bitmap {
        val w = dp(36)
        val h = dp(46)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = w / 2f
            val circleR = w / 2f - dp(2)

            // Teardrop body
            paint.color = Color.rgb(198, 40, 40) // primary vermilion
            drawCircle(cx, cx, circleR, paint)

            // Triangle tip
            val path = Path().apply {
                moveTo(cx - dp(10), cx + dp(4))
                lineTo(cx, h.toFloat() - dp(2))
                lineTo(cx + dp(10), cx + dp(4))
                close()
            }
            drawPath(path, paint)

            // White inner ring
            paint.color = Color.WHITE
            drawCircle(cx, cx, circleR * 0.55f, paint)

            // Vermilion center dot
            paint.color = Color.rgb(198, 40, 40)
            drawCircle(cx, cx, circleR * 0.25f, paint)
        }
        return bitmap
    }

    /** Google Maps-style blue dot for user location. */
    private fun userMarkerBitmap(): Bitmap {
        val size = dp(28)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = size / 2f
            // Outer white ring
            paint.color = Color.WHITE
            drawCircle(cx, cx, cx - dp(1), paint)
            // Blue fill
            paint.color = Color.rgb(21, 101, 192)
            drawCircle(cx, cx, cx - dp(4), paint)
        }
        return bitmap
    }

    // ────────────────────────────────────────────────────────
    //  Pandal detail bottom sheet
    // ────────────────────────────────────────────────────────

    private fun showDetail(pandal: Pandal) {
        val item = location?.let { pandal.withDistanceFrom(it) } ?: pandal
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_pandal_detail, null)
        sheet.setContentView(view)

        currentDetailPandal = pandal
        currentDetailSheetView = view
        sheet.setOnDismissListener {
            currentDetailPandal = null
            currentDetailSheetView = null
        }

        // ── Pandal info ──
        view.findViewById<TextView>(R.id.detailName).text = item.name
        val distanceStr = item.distanceMeters.takeIf { it > 0 }?.let(::distanceText) ?: getString(R.string.distance_unavailable)
        view.findViewById<TextView>(R.id.detailAreaDistance).text = "${item.area} · $distanceStr"
        
        view.findViewById<View>(R.id.closeSheet).setOnClickListener {
            sheet.dismiss()
        }
            
        updateTravelEstimates(view, item.distanceMeters)

        // ── Weather (automatic from Open-Meteo) ──
        val weatherEmoji = view.findViewById<TextView>(R.id.weatherEmoji)
        val weatherStatus = view.findViewById<TextView>(R.id.weatherStatus)
        val weatherUpdated = view.findViewById<TextView>(R.id.weatherUpdated)
        weatherStatus.text = getString(R.string.weather_checking)
        weatherEmoji.text = "⏳"
        weatherUpdated.text = ""

        weather.currentFor(item) { result ->
            if (result != null) {
                weatherEmoji.text = result.emoji
                weatherStatus.text = result.label
                weatherUpdated.text = "Updated ${ago(result.fetchedAt)}"
            } else {
                weatherEmoji.text = "—"
                weatherStatus.text = getString(R.string.weather_no_data)
                weatherUpdated.text = getString(R.string.weather_unavailable)
            }
        }

        // ── Crowd (from Firestore) ──
        val crowdStatus = view.findViewById<TextView>(R.id.crowdStatus)
        val crowdLabel = view.findViewById<TextView>(R.id.crowdLabel)
        val crowdUpdated = view.findViewById<TextView>(R.id.crowdUpdated)
        crowdStatus.text = getString(R.string.crowd_loading)
        crowdLabel.text = ""
        crowdUpdated.text = ""

        fun refreshCrowd() = crowd.load(item) { result ->
            if (result?.level != null) {
                crowdStatus.text = "${result.level} / 10"
                crowdLabel.text = crowdLabelText(result.level)
                val countText = if (result.reportCount == 1)
                    getString(R.string.crowd_report_count_one)
                else
                    getString(R.string.crowd_report_count, result.reportCount)
                val timeText = result.updatedAt?.let { "Updated ${ago(it)}" } ?: ""
                crowdUpdated.text = "$countText · $timeText"
            } else {
                crowdStatus.text = getString(R.string.crowd_no_level)
                crowdLabel.text = ""
                crowdUpdated.text = getString(R.string.crowd_no_reports)
            }
        }
        refreshCrowd()

        // ── Metro (calculated from pandal coordinates) ──
        val metroName = view.findViewById<TextView>(R.id.metroName)

        val metroResult = metro.nearestTo(item)
        val mDistStr = distanceShort(metroResult.distanceMeters)
        metroName.text = "${metroResult.station.name} · $mDistStr"

        // ── Actions ──
        view.findViewById<MaterialButton>(R.id.navigateButton).setOnClickListener {
            NavigationLauncher.open(this, item)
        }
        view.findViewById<MaterialButton>(R.id.updateCrowdButton).setOnClickListener {
            crowdDialog(item, ::refreshCrowd)
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Crowd report bottom sheet
    // ────────────────────────────────────────────────────────

    private fun crowdDialog(item: Pandal, refreshed: () -> Unit) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_crowd_report, null)
        sheet.setContentView(view)

        val grid = view.findViewById<GridLayout>(R.id.crowdGrid)
        val submit = view.findViewById<MaterialButton>(R.id.submitCrowd)
        val feedback = view.findViewById<LinearLayout>(R.id.crowdFeedback)
        val levelDisplay = view.findViewById<TextView>(R.id.crowdLevelDisplay)
        val labelText = view.findViewById<TextView>(R.id.crowdLabelText)
        val descText = view.findViewById<TextView>(R.id.crowdDescriptionText)

        var selection: Int? = null

        (1..10).forEach { level ->
            val button = MaterialButton(this).apply {
                text = level.toString()
                isAllCaps = false
                textSize = 18f
                layoutParams = GridLayout.LayoutParams().apply {
                    width = 0
                    height = dp(56)
                    columnSpec = GridLayout.spec(GridLayout.UNDEFINED, 1f)
                    setMargins(dp(3), dp(3), dp(3), dp(3))
                }
                setOnClickListener {
                    selection = level
                    submit.isEnabled = true
                    feedback.visibility = View.VISIBLE

                    // Update all button states
                    for (i in 0 until grid.childCount) {
                        val child = grid.getChildAt(i) as MaterialButton
                        child.isChecked = (i + 1 == level)
                    }

                    // Update feedback
                    levelDisplay.text = "$level / 10"
                    labelText.text = crowdLabelText(level)
                    descText.text = crowdDescription(level)
                }
            }
            grid.addView(button)
        }

        submit.setOnClickListener {
            val chosen = selection ?: return@setOnClickListener
            submit.isEnabled = false
            crowd.submit(item, chosen) { success ->
                if (success) {
                    Toast.makeText(
                        this,
                        "${getString(R.string.crowd_success_title)} ${getString(R.string.crowd_success_body)}",
                        Toast.LENGTH_LONG
                    ).show()
                    refreshed()
                    sheet.dismiss()
                } else {
                    Toast.makeText(this, getString(R.string.crowd_submit_failed), Toast.LENGTH_LONG).show()
                    submit.isEnabled = true
                }
            }
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    private fun updateTravelEstimates(view: View, meters: Float) {
        val walkText = view.findViewById<TextView>(R.id.timeWalk)
        val bikeText = view.findViewById<TextView>(R.id.timeBike)
        val carText = view.findViewById<TextView>(R.id.timeCar)
        if (walkText == null) return

        if (meters <= 0) {
            walkText.text = "—"
            bikeText.text = "—"
            carText.text = "—"
            return
        }

        // Walk: 5 km/h (~83 m/min)
        val walkMin = (meters / 83.3f).toInt().coerceAtLeast(1)
        walkText.text = if (walkMin > 120) ">2h" else "${walkMin}m"

        // Bike: 15 km/h (~250 m/min)
        val bikeMin = (meters / 250f).toInt().coerceAtLeast(1)
        bikeText.text = if (bikeMin > 120) ">2h" else "${bikeMin}m"

        // Car: 25 km/h (~416 m/min) in city
        val carMin = (meters / 416f).toInt().coerceAtLeast(1)
        carText.text = if (carMin > 120) ">2h" else "${carMin}m"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun ago(time: Long): String {
        val minutes = ((System.currentTimeMillis() - time) / 60_000).coerceAtLeast(0)
        return if (minutes < 1) "just now" else "$minutes min ago"
    }

    companion object {
        private const val REQUEST_LOCATION = 1001

        /** Format distance for display. */
        fun distanceText(meters: Float): String = when {
            meters < 50 -> "< 50 m away"
            meters < 1000 -> "${(meters / 50).toInt() * 50} m away"
            else -> "%.1f km away".format(meters / 1000)
        }

        /** Short distance without "away" suffix. */
        fun distanceShort(meters: Float): String = when {
            meters < 50 -> "< 50 m"
            meters < 1000 -> "${(meters / 50).toInt() * 50} m"
            else -> "%.1f km".format(meters / 1000)
        }

        /** Crowd intensity label for 1–10 scale. */
        fun crowdLabelText(level: Int): String = when (level) {
            1, 2 -> "Empty"
            3, 4 -> "Light"
            5, 6 -> "Moderate"
            7, 8 -> "Very busy"
            9 -> "Extremely busy"
            10 -> "Packed"
            else -> ""
        }

        /** Contextual description for each crowd level. */
        fun crowdDescription(level: Int): String = when (level) {
            1 -> "Little to no crowd."
            2 -> "Almost empty, very easy to visit."
            3 -> "Light crowd, comfortable."
            4 -> "Some people around, easy movement."
            5 -> "Comfortable but noticeable crowd."
            6 -> "Moderate crowd, some waiting."
            7 -> "Busy, expect some queues."
            8 -> "Very busy, slow movement in places."
            9 -> "Extremely busy, long queues and slow movement."
            10 -> "Very dense crowd and slow movement."
            else -> ""
        }
    }
}

// ────────────────────────────────────────────────────────
//  Search adapter
// ────────────────────────────────────────────────────────

private class SearchAdapter(
    private val items: List<Pandal>,
    private val selected: (Pandal) -> Unit
) : RecyclerView.Adapter<SearchAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.resultName)
        val area: TextView = v.findViewById(R.id.resultArea)
        val distance: TextView = v.findViewById(R.id.resultDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.area.text = item.area
        holder.distance.text = item.distanceMeters.takeIf { it > 0 }?.let {
            MainActivity.distanceShort(it)
        } ?: ""
        holder.itemView.setOnClickListener { selected(item) }
    }

    override fun getItemCount(): Int = items.size
}
