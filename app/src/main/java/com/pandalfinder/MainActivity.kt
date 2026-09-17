package com.pandalfinder

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.*
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.widget.doAfterTextChanged
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.TextInputEditText
import com.pandalfinder.data.*
import com.pandalfinder.ui.HoppingAdapter
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.IconFactory
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MainActivity : AppCompatActivity() {

    enum class MapFilter { PANDALS, METRO, TOILETS }

    // ── Map Views ──
    private lateinit var mapContainer: View
    private lateinit var mapView: MapView
    private lateinit var searchInput: TextInputEditText
    private lateinit var results: RecyclerView
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var allowButton: MaterialButton
    private lateinit var pandalsFilterButton: MaterialButton
    private lateinit var metroFilterButton: MaterialButton
    private lateinit var toiletsFilterButton: MaterialButton

    // ── Hopping Views ──
    private lateinit var hoppingContainer: View
    private lateinit var hoppingEmptyView: View
    private lateinit var hoppingRecyclerView: RecyclerView
    private lateinit var hoppingSummaryCard: View
    private lateinit var hoppingTotalDistance: TextView
    private lateinit var hoppingEstimatedTime: TextView
    private lateinit var hoppingStopsCount: TextView
    private lateinit var startHoppingButton: MaterialButton
    private lateinit var hoppingAdapter: HoppingAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    // ── Floating Navigation Pill Views ──
    private lateinit var navMapPill: LinearLayout
    private lateinit var navMapIcon: ImageView
    private lateinit var navMapText: TextView
    private lateinit var navHoppingPill: LinearLayout
    private lateinit var navHoppingIcon: ImageView
    private lateinit var navHoppingText: TextView
    private lateinit var navHoppingBadge: TextView

    // ── Repositories ──
    private lateinit var pandals: PandalRepository
    private lateinit var weather: WeatherRepository
    private lateinit var weatherReports: WeatherReportRepository
    private lateinit var crowd: CrowdRepository
    private lateinit var metro: MetroRepository
    private lateinit var routes: GoogleRoutesRepository
    private lateinit var hopping: HoppingRepository
    private lateinit var eligibility: ContributionEligibility

    // ── State ──
    private var map: MapLibreMap? = null
    private var location: Location? = null
    private var shownPandals = emptyList<Pandal>()
    private var shownStations = emptyList<MetroStation>()
    private var currentFilter = MapFilter.PANDALS
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var currentDetailPandal: Pandal? = null
    private var currentDetailSheetView: View? = null

    // ────────────────────────────────────────────────────────
    //  Lifecycle
    // ────────────────────────────────────────────────────────

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)

        // Initialize Firebase
        if (com.google.firebase.FirebaseApp.getApps(this).isEmpty()) {
            try {
                com.google.firebase.FirebaseApp.initializeApp(
                    this,
                    com.google.firebase.FirebaseOptions.Builder()
                        .setApiKey("AIzaSyBFfA06Yr7eIyXMCaEE8RWPvcSFRnau8Pw")
                        .setApplicationId("1:333025667530:web:0d3f2ca9275298fb694733")
                        .setDatabaseUrl("https://pandalquest-default-rtdb.firebaseio.com")
                        .setProjectId("pandalquest")
                        .setStorageBucket("pandalquest.firebasestorage.app")
                        .build()
                )
                Log.d(TAG, "Firebase initialized successfully")
            } catch (e: Exception) {
                Log.e(TAG, "Firebase initialization failed", e)
            }
        }

        MapLibre.getInstance(this)
        setContentView(R.layout.activity_main)

        // Initialize Data Repositories
        pandals = PandalRepository(this)
        weather = WeatherRepository()
        weatherReports = WeatherReportRepository()
        crowd = CrowdRepository()
        metro = MetroRepository()
        routes = GoogleRoutesRepository()
        hopping = HoppingRepository(this, pandals)
        eligibility = ContributionEligibility(this)

        initViews(state)
        setupHoppingTab()
        setupFloatingNav()
        showLocationExplanation()
    }

    private fun initViews(state: Bundle?) {
        mapContainer = findViewById(R.id.mapContainer)
        mapView = findViewById(R.id.mapView)
        searchInput = findViewById(R.id.searchInput)
        results = findViewById(R.id.searchResults)
        statusTitle = findViewById(R.id.statusTitle)
        statusMessage = findViewById(R.id.statusMessage)
        allowButton = findViewById(R.id.allowLocationButton)

        pandalsFilterButton = findViewById(R.id.pandalsFilter)
        metroFilterButton = findViewById(R.id.nearbyFilter)
        toiletsFilterButton = findViewById(R.id.toiletsFilter)

        hoppingContainer = findViewById(R.id.hoppingContainer)
        hoppingEmptyView = findViewById(R.id.hoppingEmptyView)
        hoppingRecyclerView = findViewById(R.id.hoppingRecyclerView)
        hoppingSummaryCard = findViewById(R.id.hoppingSummaryCard)
        hoppingTotalDistance = findViewById(R.id.hoppingTotalDistance)
        hoppingEstimatedTime = findViewById(R.id.hoppingEstimatedTime)
        hoppingStopsCount = findViewById(R.id.hoppingStopsCount)
        startHoppingButton = findViewById(R.id.startHoppingButton)

        navMapPill = findViewById(R.id.navMapPill)
        navMapIcon = findViewById(R.id.navMapIcon)
        navMapText = findViewById(R.id.navMapText)
        navHoppingPill = findViewById(R.id.navHoppingPill)
        navHoppingIcon = findViewById(R.id.navHoppingIcon)
        navHoppingText = findViewById(R.id.navHoppingText)
        navHoppingBadge = findViewById(R.id.navHoppingBadge)

        results.layoutManager = LinearLayoutManager(this)
        allowButton.setOnClickListener { requestLocationPermission() }

        setupFilterPills()

        mapView.onCreate(state)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.setOnMarkerClickListener { marker ->
                val clickedPandal = shownPandals.firstOrNull {
                    it.longitude == marker.position.longitude && it.latitude == marker.position.latitude
                }
                if (clickedPandal != null) {
                    showDetail(clickedPandal)
                    return@setOnMarkerClickListener true
                }

                val clickedStation = shownStations.firstOrNull {
                    it.longitude == marker.position.longitude && it.latitude == marker.position.latitude
                }
                if (clickedStation != null) {
                    Toast.makeText(this, "🚇 ${clickedStation.name} (${clickedStation.line})", Toast.LENGTH_SHORT).show()
                    return@setOnMarkerClickListener true
                }
                false
            }
            readyMap.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                applyCurrentFilter()
            }
        }

        searchInput.doAfterTextChanged { text ->
            val query = text?.toString().orEmpty()
            if (query.isBlank()) {
                results.visibility = View.GONE
            } else {
                val matching = pandals.searchAll(query, location).take(8)
                results.visibility = if (matching.isEmpty()) View.GONE else View.VISIBLE
                results.adapter = SearchResultAdapter(matching) { result ->
                    searchInput.setText("")
                    results.visibility = View.GONE
                    when (result) {
                        is SearchResult.PandalResult -> {
                            focusPandal(result.pandal)
                            showDetail(result.pandal)
                        }
                        is SearchResult.MetroStationResult -> {
                            focusMetro(result.station)
                            Toast.makeText(this, "🚇 ${result.station.name} (${result.station.line})", Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }
        }
    }

    private fun setupFilterPills() {
        pandalsFilterButton.setOnClickListener {
            setFilter(MapFilter.PANDALS)
        }
        metroFilterButton.setOnClickListener {
            setFilter(MapFilter.METRO)
        }
        toiletsFilterButton.setOnClickListener {
            Toast.makeText(this, "Public toilet discovery requires Places API", Toast.LENGTH_SHORT).show()
        }
    }

    private fun setFilter(filter: MapFilter) {
        currentFilter = filter
        updateFilterPillStyles()
        applyCurrentFilter()
    }

    private fun updateFilterPillStyles() {
        val primaryBg = ContextCompat.getColorStateList(this, R.color.primary)
        val surfaceBg = ContextCompat.getColorStateList(this, R.color.surface_floating)
        val onPrimaryText = ContextCompat.getColor(this, R.color.on_primary)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)

        if (currentFilter == MapFilter.PANDALS) {
            pandalsFilterButton.backgroundTintList = primaryBg
            pandalsFilterButton.setTextColor(onPrimaryText)
            pandalsFilterButton.strokeWidth = 0

            metroFilterButton.backgroundTintList = surfaceBg
            metroFilterButton.setTextColor(textPrimary)
            metroFilterButton.strokeWidth = dp(1)
        } else if (currentFilter == MapFilter.METRO) {
            metroFilterButton.backgroundTintList = primaryBg
            metroFilterButton.setTextColor(onPrimaryText)
            metroFilterButton.strokeWidth = 0

            pandalsFilterButton.backgroundTintList = surfaceBg
            pandalsFilterButton.setTextColor(textPrimary)
            pandalsFilterButton.strokeWidth = dp(1)
        }
    }

    private fun applyCurrentFilter() {
        when (currentFilter) {
            MapFilter.PANDALS -> {
                val list = location?.let { pandals.nearby(it) } ?: pandals.all()
                renderPandals(list)
            }
            MapFilter.METRO -> {
                renderMetro(metro.all())
            }
            MapFilter.TOILETS -> {
                // Not supported without places API
            }
        }
    }

    private fun setupHoppingTab() {
        hoppingRecyclerView.layoutManager = LinearLayoutManager(this)
        hoppingAdapter = HoppingAdapter(
            items = mutableListOf(),
            onRemove = { pandal ->
                hopping.remove(pandal.id)
                Toast.makeText(this, "Removed ${pandal.name} from Hopping", Toast.LENGTH_SHORT).show()
            },
            onPandalClick = { pandal ->
                showDetail(pandal)
            },
            onStartDrag = { viewHolder ->
                itemTouchHelper.startDrag(viewHolder)
            },
            onItemMoved = { from, to ->
                hopping.move(from, to)
            }
        )
        hoppingRecyclerView.adapter = hoppingAdapter

        val touchCallback = object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN,
            0
        ) {
            override fun onMove(
                rv: RecyclerView,
                src: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean {
                hoppingAdapter.onItemMove(src.adapterPosition, target.adapterPosition)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}
            override fun isLongPressDragEnabled(): Boolean = false
        }
        itemTouchHelper = ItemTouchHelper(touchCallback)
        itemTouchHelper.attachToRecyclerView(hoppingRecyclerView)

        hopping.addListener {
            updateHoppingUI()
            updateHoppingBadge()
        }

        startHoppingButton.setOnClickListener {
            val plan = hopping.getPlan()
            if (plan.isNotEmpty()) {
                NavigationLauncher.startHopping(this, location, plan)
            } else {
                Toast.makeText(this, "Add pandals to your hopping plan first", Toast.LENGTH_SHORT).show()
            }
        }

        updateHoppingUI()
        updateHoppingBadge()
    }

    private fun setupFloatingNav() {
        navMapPill.setOnClickListener {
            selectTab(isMap = true)
        }
        navHoppingPill.setOnClickListener {
            selectTab(isMap = false)
        }
    }

    private fun selectTab(isMap: Boolean) {
        if (isMap) {
            mapContainer.visibility = View.VISIBLE
            hoppingContainer.visibility = View.GONE

            navMapPill.setBackgroundResource(R.drawable.bg_nav_pill_active)
            navMapIcon.setColorFilter(ContextCompat.getColor(this, R.color.on_primary))
            navMapText.setTextColor(ContextCompat.getColor(this, R.color.on_primary))

            navHoppingPill.setBackgroundColor(Color.TRANSPARENT)
            navHoppingIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_text))
            navHoppingText.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive_text))
        } else {
            mapContainer.visibility = View.GONE
            hoppingContainer.visibility = View.VISIBLE

            navHoppingPill.setBackgroundResource(R.drawable.bg_nav_pill_active)
            navHoppingIcon.setColorFilter(ContextCompat.getColor(this, R.color.on_primary))
            navHoppingText.setTextColor(ContextCompat.getColor(this, R.color.on_primary))

            navMapPill.setBackgroundColor(Color.TRANSPARENT)
            navMapIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_text))
            navMapText.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive_text))

            updateHoppingUI()
        }
    }

    private fun updateHoppingBadge() {
        val count = hopping.getPlanIds().size
        if (count > 0) {
            navHoppingBadge.visibility = View.VISIBLE
            navHoppingBadge.text = count.toString()
        } else {
            navHoppingBadge.visibility = View.GONE
        }
    }

    private fun updateHoppingUI() {
        val plan = hopping.getPlan()
        if (plan.isEmpty()) {
            hoppingEmptyView.visibility = View.VISIBLE
            hoppingRecyclerView.visibility = View.GONE
            hoppingSummaryCard.visibility = View.GONE
        } else {
            hoppingEmptyView.visibility = View.GONE
            hoppingRecyclerView.visibility = View.VISIBLE
            hoppingSummaryCard.visibility = View.VISIBLE

            hoppingStopsCount.text = "${plan.size} ${if (plan.size == 1) "stop" else "stops"}"
            hoppingTotalDistance.text = "Calculating route…"
            hoppingEstimatedTime.text = "…"

            location?.let { origin ->
                routes.computeHoppingRoute(origin, plan) { routeResult ->
                    if (routeResult != null) {
                        hoppingTotalDistance.text = routeDistanceText(routeResult.totalDistanceMeters)
                        hoppingEstimatedTime.text = routeResult.totalDurationFormatted
                        hoppingAdapter.updateData(plan, routeResult.legDistances)
                    } else {
                        hoppingTotalDistance.text = "Route unavailable"
                        hoppingEstimatedTime.text = "—"
                        hoppingAdapter.updateData(plan, emptyList())
                    }
                }
            } ?: run {
                hoppingTotalDistance.text = "Location needed"
                hoppingEstimatedTime.text = "—"
                hoppingAdapter.updateData(plan, emptyList())
            }
        }
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
            renderPandals(pandals.all())
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
                    if (currentFilter == MapFilter.PANDALS) {
                        val nearby = pandals.nearby(found)
                        renderPandals(nearby)
                        statusTitle.text = getString(R.string.location_found, nearby.size)
                        statusMessage.text = getString(R.string.location_tap)
                    }

                    if (map?.cameraPosition?.zoom ?: 0.0 < 10.0) {
                        map?.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(found.latitude, found.longitude))
                            .zoom(12.5)
                            .build()
                    }

                    pandals.all().forEach { p ->
                        if (eligibility.distanceTo(found, p) <= ContributionEligibility.CONTRIBUTION_RADIUS_METERS) {
                            eligibility.markVisit(p.id)
                        }
                    }

                    if (hoppingContainer.visibility == View.VISIBLE) {
                        updateHoppingUI()
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

    private fun renderPandals(items: List<Pandal>) {
        shownPandals = items
        shownStations = emptyList()
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

    private fun renderMetro(stations: List<MetroStation>) {
        shownPandals = emptyList()
        shownStations = stations
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
            val icon = icons.fromBitmap(metroMarkerBitmap())
            stations.forEach {
                readyMap.addMarker(
                    MarkerOptions()
                        .position(LatLng(it.latitude, it.longitude))
                        .icon(icon)
                )
            }
        }
    }

    private fun focusPandal(pandal: Pandal) {
        renderPandals(listOf(location?.let { pandal.withDistanceFrom(it) } ?: pandal))
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(pandal.latitude, pandal.longitude))
            .zoom(15.5)
            .build()
    }

    private fun focusMetro(station: MetroStation) {
        renderMetro(listOf(station))
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(station.latitude, station.longitude))
            .zoom(15.5)
            .build()
    }

    private fun pandalMarkerBitmap(): Bitmap {
        val w = dp(36)
        val h = dp(46)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = w / 2f
            val circleR = w / 2f - dp(2)

            // Primary Sindoor Red pin
            paint.color = Color.rgb(229, 43, 0)
            drawCircle(cx, cx, circleR, paint)

            val path = Path().apply {
                moveTo(cx - dp(10), cx + dp(4))
                lineTo(cx, h.toFloat() - dp(2))
                lineTo(cx + dp(10), cx + dp(4))
                close()
            }
            drawPath(path, paint)

            // Gold accent ring
            paint.color = Color.rgb(251, 194, 34)
            drawCircle(cx, cx, circleR * 0.65f, paint)

            // Cream center dot
            paint.color = Color.rgb(255, 245, 227)
            drawCircle(cx, cx, circleR * 0.35f, paint)
        }
        return bitmap
    }

    private fun metroMarkerBitmap(): Bitmap {
        val w = dp(34)
        val h = dp(44)
        val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = w / 2f
            val circleR = w / 2f - dp(2)

            // Emerald Green metro pin
            paint.color = Color.rgb(69, 167, 1)
            drawCircle(cx, cx, circleR, paint)

            val path = Path().apply {
                moveTo(cx - dp(9), cx + dp(4))
                lineTo(cx, h.toFloat() - dp(2))
                lineTo(cx + dp(9), cx + dp(4))
                close()
            }
            drawPath(path, paint)

            // White center circle
            paint.color = Color.WHITE
            drawCircle(cx, cx, circleR * 0.55f, paint)

            // Green center dot
            paint.color = Color.rgb(69, 167, 1)
            drawCircle(cx, cx, circleR * 0.25f, paint)
        }
        return bitmap
    }

    private fun userMarkerBitmap(): Bitmap {
        val size = dp(28)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = size / 2f
            // Outer white ring
            paint.color = Color.WHITE
            drawCircle(cx, cx, cx - dp(1), paint)
            // Vivid blue dot with gold tinge
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
        view.findViewById<TextView>(R.id.detailArea).text = item.area
        val mainDistance = view.findViewById<TextView>(R.id.detailDistance)
        mainDistance.text = "Finding road route…"

        view.findViewById<View>(R.id.closeSheet).setOnClickListener {
            sheet.dismiss()
        }

        val walkDistance = view.findViewById<TextView>(R.id.timeWalk)
        val bikeDistance = view.findViewById<TextView>(R.id.timeBike)
        val carDistance = view.findViewById<TextView>(R.id.timeCar)
        walkDistance.text = "…"
        bikeDistance.text = "…"
        carDistance.text = "…"

        location?.let { liveOrigin ->
            routes.routesFrom(liveOrigin, item) { routeData ->
                if (currentDetailPandal?.id != item.id || currentDetailSheetView !== view) return@routesFrom
                if (routeData == null) {
                    mainDistance.text = getString(R.string.road_distance_unavailable)
                    walkDistance.text = "—"
                    bikeDistance.text = "—"
                    carDistance.text = "—"
                } else {
                    mainDistance.text = routeData.drive?.let { routeDistanceText(it.distanceMeters) }
                        ?: getString(R.string.road_distance_unavailable)
                    walkDistance.text = routeData.walk?.let { routeDistanceText(it.distanceMeters) } ?: "—"
                    bikeDistance.text = routeData.twoWheeler?.let { routeDistanceText(it.distanceMeters) } ?: "—"
                    carDistance.text = routeData.drive?.let { routeDistanceText(it.distanceMeters) } ?: "—"
                }
            }
        } ?: run {
            mainDistance.text = getString(R.string.road_distance_unavailable)
            walkDistance.text = "—"
            bikeDistance.text = "—"
            carDistance.text = "—"
        }

        // ── Weather (Community priority + Baseline Open-Meteo) ──
        val weatherStatus = view.findViewById<TextView>(R.id.weatherStatus)
        val weatherUpdated = view.findViewById<TextView>(R.id.weatherUpdated)
        val weatherCardIcon = view.findViewById<ImageView>(R.id.weatherCardIcon)
        weatherStatus.text = getString(R.string.weather_checking)
        weatherUpdated.text = ""

        fun loadWeather() {
            weatherReports.latest(item) { communityReport, _ ->
                if (communityReport != null) {
                    weatherStatus.text = "${communityReport.condition.emoji} ${communityReport.condition.label}"
                    weatherUpdated.text = "Community report · Updated ${ago(communityReport.timestamp)}"
                    when (communityReport.condition) {
                        CommunityWeather.NO_RAIN -> weatherCardIcon.setImageResource(R.drawable.ic_weather_sun)
                        CommunityWeather.DRIZZLE -> weatherCardIcon.setImageResource(R.drawable.ic_weather_drizzle)
                        CommunityWeather.RAINING -> weatherCardIcon.setImageResource(R.drawable.ic_weather_rain)
                        CommunityWeather.HEAVY_RAIN -> weatherCardIcon.setImageResource(R.drawable.ic_weather_thunder)
                    }
                } else {
                    weather.currentFor(item) { result ->
                        if (result != null) {
                            weatherCardIcon.setImageResource(R.drawable.ic_weather)
                            weatherStatus.text = "${result.emoji} ${result.label}"
                            weatherUpdated.text = "Auto weather · Updated ${ago(result.fetchedAt)}"
                        } else {
                            weatherCardIcon.setImageResource(R.drawable.ic_weather)
                            weatherStatus.text = getString(R.string.weather_no_data)
                            weatherUpdated.text = getString(R.string.weather_unavailable)
                        }
                    }
                }
            }
        }
        loadWeather()
        view.findViewById<MaterialButton>(R.id.weatherRefreshButton).setOnClickListener {
            weatherDialog(item, ::loadWeather)
        }

        // ── Crowd (from Firestore) ──
        val crowdStatus = view.findViewById<TextView>(R.id.crowdStatus)
        val crowdLabel = view.findViewById<TextView>(R.id.crowdLabel)
        val crowdUpdated = view.findViewById<TextView>(R.id.crowdUpdated)
        crowdStatus.text = getString(R.string.crowd_loading)
        crowdLabel.text = ""
        crowdUpdated.text = ""

        fun refreshCrowd() = crowd.load(item) { result, _ ->
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
                crowdStatus.text = "No reports yet"
                crowdLabel.text = ""
                crowdUpdated.text = "Be the first to report crowd level"
            }
        }
        refreshCrowd()

        // ── Metro ──
        val metroName = view.findViewById<TextView>(R.id.metroName)
        val metroResult = metro.nearestTo(item)
        val mDistStr = distanceShort(metroResult.distanceMeters)
        metroName.text = "${metroResult.station.name} · $mDistStr"

        // ── Compact Secondary Hopping Toggle Card ──
        val addToHoppingCard = view.findViewById<MaterialCardView>(R.id.addToHoppingCard)
        val hoppingActionIcon = view.findViewById<ImageView>(R.id.hoppingActionIcon)

        fun updateHoppingButtonState() {
            val inPlan = hopping.isInPlan(item.id)
            if (inPlan) {
                hoppingActionIcon.setImageResource(R.drawable.ic_check)
                hoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
                addToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
                addToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
            } else {
                hoppingActionIcon.setImageResource(R.drawable.ic_add)
                hoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                addToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                addToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.outline)
            }
        }
        updateHoppingButtonState()

        addToHoppingCard.setOnClickListener {
            val inPlan = hopping.isInPlan(item.id)
            if (inPlan) {
                hopping.remove(item.id)
                Toast.makeText(this, "Removed from Hopping", Toast.LENGTH_SHORT).show()
            } else {
                hopping.add(item)
                Toast.makeText(this, "Added to Hopping", Toast.LENGTH_SHORT).show()
            }
            updateHoppingButtonState()
        }

        // ── Dominant Primary Navigation Button ──
        view.findViewById<MaterialButton>(R.id.navigateButton).setOnClickListener {
            NavigationLauncher.open(this, item)
        }
        view.findViewById<MaterialButton>(R.id.updateCrowdButton).setOnClickListener {
            crowdDialog(item, ::refreshCrowd)
        }

        sheet.setOnShowListener {
            val behavior = BottomSheetBehavior.from(sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)!!)
            behavior.peekHeight = dp(600)
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Weather contribution bottom sheet
    // ────────────────────────────────────────────────────────

    private fun weatherDialog(item: Pandal, onRefreshed: () -> Unit) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_weather_report, null)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.weatherPandalName).text = item.name
        view.findViewById<View>(R.id.closeWeatherSheet).setOnClickListener { sheet.dismiss() }

        val banner = view.findViewById<View>(R.id.weatherEligibilityBanner)
        val bannerText = view.findViewById<TextView>(R.id.weatherEligibilityText)
        val submit = view.findViewById<MaterialButton>(R.id.submitWeather)
        val progress = view.findViewById<ProgressBar>(R.id.weatherProgress)

        val optNoRain = view.findViewById<LinearLayout>(R.id.optNoRain)
        val optDrizzle = view.findViewById<LinearLayout>(R.id.optDrizzle)
        val optRaining = view.findViewById<LinearLayout>(R.id.optRaining)
        val optHeavyRain = view.findViewById<LinearLayout>(R.id.optHeavyRain)

        val eligibilityResult = eligibility.evaluate(item, location)
        if (!eligibilityResult.allowed) {
            banner.visibility = View.VISIBLE
            bannerText.text = eligibilityResult.reason
        } else {
            banner.visibility = View.GONE
        }

        var selectedCondition: CommunityWeather? = null
        val optionsMap = mapOf(
            optNoRain to CommunityWeather.NO_RAIN,
            optDrizzle to CommunityWeather.DRIZZLE,
            optRaining to CommunityWeather.RAINING,
            optHeavyRain to CommunityWeather.HEAVY_RAIN
        )

        fun selectOption(selectedView: View, condition: CommunityWeather) {
            selectedCondition = condition
            optionsMap.keys.forEach { v ->
                if (v == selectedView) {
                    v.setBackgroundResource(R.drawable.bg_option_selected)
                } else {
                    v.setBackgroundResource(R.drawable.bg_option_unselected)
                }
            }
            if (eligibilityResult.allowed) {
                submit.isEnabled = true
            }
        }

        optNoRain.setOnClickListener { selectOption(optNoRain, CommunityWeather.NO_RAIN) }
        optDrizzle.setOnClickListener { selectOption(optDrizzle, CommunityWeather.DRIZZLE) }
        optRaining.setOnClickListener { selectOption(optRaining, CommunityWeather.RAINING) }
        optHeavyRain.setOnClickListener { selectOption(optHeavyRain, CommunityWeather.HEAVY_RAIN) }

        submit.setOnClickListener {
            val chosen = selectedCondition ?: return@setOnClickListener
            submit.isEnabled = false
            progress.visibility = View.VISIBLE

            weatherReports.submit(item, chosen, eligibilityResult.deviceId, location) { error ->
                progress.visibility = View.GONE
                if (error == null) {
                    Toast.makeText(this, "Weather update submitted. Thank you for contributing!", Toast.LENGTH_SHORT).show()
                    onRefreshed()
                    sheet.dismiss()
                } else {
                    Log.e(TAG, "Failed to submit weather report", error)
                    Toast.makeText(this, "Failed to submit: ${error.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    submit.isEnabled = true
                }
            }
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Crowd contribution bottom sheet
    // ────────────────────────────────────────────────────────

    private fun crowdDialog(item: Pandal, onRefreshed: () -> Unit) {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_crowd_report, null)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.crowdPandalName).text = item.name
        view.findViewById<View>(R.id.closeCrowdSheet).setOnClickListener { sheet.dismiss() }

        val banner = view.findViewById<View>(R.id.crowdEligibilityBanner)
        val bannerText = view.findViewById<TextView>(R.id.crowdEligibilityText)
        val grid = view.findViewById<GridLayout>(R.id.crowdGrid)
        val submit = view.findViewById<MaterialButton>(R.id.submitCrowd)
        val progress = view.findViewById<ProgressBar>(R.id.crowdProgress)
        val feedback = view.findViewById<LinearLayout>(R.id.crowdFeedback)
        val levelDisplay = view.findViewById<TextView>(R.id.crowdLevelDisplay)
        val labelText = view.findViewById<TextView>(R.id.crowdLabelText)
        val descText = view.findViewById<TextView>(R.id.crowdDescriptionText)

        val eligibilityResult = eligibility.evaluate(item, location)
        if (!eligibilityResult.allowed) {
            banner.visibility = View.VISIBLE
            bannerText.text = eligibilityResult.reason
        } else {
            banner.visibility = View.GONE
        }

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
                    feedback.visibility = View.VISIBLE

                    for (i in 0 until grid.childCount) {
                        val child = grid.getChildAt(i) as MaterialButton
                        child.isChecked = (i + 1 == level)
                    }

                    levelDisplay.text = "$level / 10"
                    labelText.text = crowdLabelText(level)
                    descText.text = crowdDescription(level)

                    if (eligibilityResult.allowed) {
                        submit.isEnabled = true
                    }
                }
            }
            grid.addView(button)
        }

        submit.setOnClickListener {
            val chosen = selection ?: return@setOnClickListener
            submit.isEnabled = false
            progress.visibility = View.VISIBLE

            crowd.submit(item, chosen, eligibilityResult.deviceId, location) { error ->
                progress.visibility = View.GONE
                if (error == null) {
                    Toast.makeText(this, "Crowd update submitted. Thank you for contributing!", Toast.LENGTH_SHORT).show()
                    onRefreshed()
                    sheet.dismiss()
                } else {
                    Log.e(TAG, "Failed to submit crowd report", error)
                    Toast.makeText(this, "Failed to submit: ${error.localizedMessage ?: "Unknown error"}", Toast.LENGTH_LONG).show()
                    submit.isEnabled = true
                }
            }
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Helpers
    // ────────────────────────────────────────────────────────

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun ago(time: Long): String {
        val minutes = ((System.currentTimeMillis() - time) / 60_000).coerceAtLeast(0)
        return if (minutes < 1) "just now" else "$minutes min ago"
    }

    companion object {
        private const val TAG = "PandalFinderMain"
        private const val REQUEST_LOCATION = 1001

        fun distanceText(meters: Float): String = when {
            meters < 50 -> "< 50 m away"
            meters < 1000 -> "${(meters / 50).toInt() * 50} m away"
            else -> "%.1f km away".format(meters / 1000)
        }

        fun distanceShort(meters: Float): String = when {
            meters < 50 -> "< 50 m"
            meters < 1000 -> "${(meters / 50).toInt() * 50} m"
            else -> "%.1f km".format(meters / 1000)
        }

        fun routeDistanceText(meters: Int): String = when {
            meters < 1_000 -> "${(meters / 50) * 50} m"
            else -> "%.1f km".format(meters / 1_000.0)
        }

        fun crowdLabelText(level: Int): String = when (level) {
            1, 2 -> "Empty"
            3, 4 -> "Light"
            5, 6 -> "Moderate"
            7, 8 -> "Very busy"
            9 -> "Extremely busy"
            10 -> "Packed"
            else -> ""
        }

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

private class SearchResultAdapter(
    private val items: List<SearchResult>,
    private val selected: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val icon: ImageView = v.findViewById(R.id.resultIcon)
        val name: TextView = v.findViewById(R.id.resultName)
        val area: TextView = v.findViewById(R.id.resultArea)
        val distance: TextView = v.findViewById(R.id.resultDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        when (item) {
            is SearchResult.PandalResult -> {
                holder.icon.setImageResource(R.drawable.ic_temple)
                holder.icon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.primary))
                holder.name.text = item.pandal.name
                holder.area.text = item.pandal.area
                holder.distance.text = item.pandal.distanceMeters.takeIf { it > 0 }?.let {
                    MainActivity.distanceShort(it)
                } ?: ""
            }
            is SearchResult.MetroStationResult -> {
                holder.icon.setImageResource(R.drawable.ic_metro)
                holder.icon.setColorFilter(ContextCompat.getColor(holder.itemView.context, R.color.metro_icon))
                holder.name.text = item.station.name
                holder.area.text = "Metro Station (${item.station.line})"
                holder.distance.text = item.distanceMeters.takeIf { it > 0 }?.let {
                    MainActivity.distanceShort(it)
                } ?: ""
            }
        }
        holder.itemView.setOnClickListener { selected(item) }
    }

    override fun getItemCount(): Int = items.size
}
