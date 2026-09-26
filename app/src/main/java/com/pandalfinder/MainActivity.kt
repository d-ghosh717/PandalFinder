package com.pandalfinder

import android.Manifest
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.*
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.*
import android.view.inputmethod.InputMethodManager
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
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView

class MainActivity : AppCompatActivity() {

    // ── Map Views ──
    private lateinit var mapContainer: View
    private lateinit var mapView: MapView
    private lateinit var searchInput: TextInputEditText
    private lateinit var results: RecyclerView
    private lateinit var searchEmptyView: TextView
    private lateinit var statusCard: View
    private lateinit var dismissStatusCard: View
    private lateinit var statusTitle: TextView
    private lateinit var statusMessage: TextView
    private lateinit var allowButton: MaterialButton
    private lateinit var pandalsFilterButton: MaterialButton
    private lateinit var metroFilterButton: MaterialButton
    private lateinit var toiletsFilterButton: MaterialButton
    private lateinit var zonesFilterButton: MaterialButton
    private lateinit var btnNearbyFloating: View
    private lateinit var crowdLegendPill: View
    private lateinit var crowdLegendText: TextView
    private lateinit var offlineBanner: View
    private lateinit var festivalAlertCard: View
    private lateinit var festivalAlertText: TextView
    private lateinit var dismissFestivalAlert: ImageButton

    // ── Compact Floating Metro Card Views ──
    private lateinit var metroCard: MaterialCardView
    private lateinit var metroCardIconContainer: FrameLayout
    private lateinit var metroCardIcon: ImageView
    private lateinit var metroCardName: TextView
    private lateinit var metroCardDistance: TextView
    private lateinit var dismissMetroCard: ImageButton
    private lateinit var metroAddToHoppingCard: MaterialCardView
    private lateinit var metroHoppingActionIcon: ImageView
    private lateinit var metroNavigateButton: MaterialButton

    // ── Compact Floating Toilet Card Views ──
    private lateinit var toiletCard: MaterialCardView
    private lateinit var toiletCardIconContainer: FrameLayout
    private lateinit var toiletCardIcon: ImageView
    private lateinit var toiletCardName: TextView
    private lateinit var toiletCardDistance: TextView
    private lateinit var dismissToiletCard: ImageButton
    private lateinit var toiletAddToHoppingCard: MaterialCardView
    private lateinit var toiletHoppingActionIcon: ImageView
    private lateinit var toiletNavigateButton: MaterialButton

    // ── Hopping Views ──
    private lateinit var hoppingContainer: View
    private lateinit var hoppingEmptyView: View
    private lateinit var hoppingRecyclerView: RecyclerView
    private lateinit var hoppingSummaryCard: View
    private lateinit var hoppingTotalDistance: TextView
    private lateinit var hoppingEstimatedTime: TextView
    private lateinit var hoppingStopsCount: TextView
    private lateinit var hoppingContextSummaryText: TextView
    private lateinit var btnOptimizeRoute: MaterialButton
    private lateinit var btnTonightsPlan: MaterialButton
    private lateinit var emptyStateTonightsPlanButton: MaterialButton
    private lateinit var startHoppingButton: MaterialButton
    private lateinit var hoppingAdapter: HoppingAdapter
    private lateinit var itemTouchHelper: ItemTouchHelper

    // ── Floating Navigation Pill Views (Map, Hopping, My Pandal) ──
    private lateinit var navMapPill: LinearLayout
    private lateinit var navMapIcon: ImageView
    private lateinit var navMapText: TextView
    private lateinit var navHoppingPill: LinearLayout
    private lateinit var navHoppingIcon: ImageView
    private lateinit var navHoppingText: TextView
    private lateinit var navHoppingBadge: TextView
    private lateinit var navMyPandalPill: LinearLayout
    private lateinit var navMyPandalIcon: ImageView
    private lateinit var navMyPandalText: TextView

    // ── Repositories ──
    private lateinit var pandals: PandalRepository
    private lateinit var weather: WeatherRepository
    private lateinit var weatherReports: WeatherReportRepository
    private lateinit var crowd: CrowdRepository
    private lateinit var metro: MetroRepository
    private lateinit var places: GooglePlacesRepository
    private lateinit var routes: GoogleRoutesRepository
    private lateinit var hopping: HoppingRepository
    private lateinit var eligibility: ContributionEligibility
    private lateinit var favorites: FavoritesRepository
    private lateinit var passport: PassportRepository
    private lateinit var zones: FestivalZoneRepository
    private lateinit var alerts: FestivalAlertRepository
    private lateinit var networkMonitor: NetworkMonitor
    private lateinit var photos: PhotoRepository

    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
    private var pendingUploadPandalId: String? = null
    private var pendingUploadLayout: View? = null
    private var pendingUploadProgressBar: ProgressBar? = null
    private var pendingUploadProgressText: TextView? = null

    private val photoPicker = registerForActivityResult(androidx.activity.result.contract.ActivityResultContracts.GetContent()) { uri: android.net.Uri? ->
        val pId = pendingUploadPandalId
        if (uri != null && pId != null) {
            pendingUploadLayout?.visibility = View.VISIBLE
            pendingUploadProgressBar?.progress = 0
            pendingUploadProgressText?.text = "Uploading 0%"

            photos.uploadPhoto(
                context = this,
                pandalId = pId,
                imageUri = uri,
                onProgress = { progress ->
                    mainHandler.post {
                        pendingUploadProgressBar?.progress = progress
                        pendingUploadProgressText?.text = "Uploading $progress%"
                    }
                },
                onComplete = { result ->
                    mainHandler.post {
                        pendingUploadLayout?.visibility = View.GONE
                        result.onSuccess {
                            Toast.makeText(this, "Photo uploaded successfully!", Toast.LENGTH_SHORT).show()
                        }.onFailure { err ->
                            Toast.makeText(this, "Upload failed: ${err.localizedMessage}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            )
        }
    }

    // ── State ──
    private var map: MapLibreMap? = null
    private var location: Location? = null
    private var shownPandals = emptyList<Pandal>()
    private var shownStations = emptyList<MetroStation>()
    private var shownToilets = emptyList<PublicToilet>()
    private var liveCrowdMap = mapOf<String, Int>()
    private var filterShowPandals = true
    private var filterShowMetro = false
    private var filterShowToilets = false
    private var locationCallback: com.google.android.gms.location.LocationCallback? = null
    private var currentDetailPandal: Pandal? = null
    private var currentDetailSheetView: View? = null
    private var currentSelectedMetro: MetroStation? = null
    private var currentSelectedToilet: PublicToilet? = null
    private var selectedMarkerPosition: LatLng? = null
    private val markerBitmapCache = mutableMapOf<String, Bitmap>()

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
        places = GooglePlacesRepository()
        routes = GoogleRoutesRepository()
        hopping = HoppingRepository(this, pandals)
        eligibility = ContributionEligibility(this)
        favorites = FavoritesRepository(this)
        passport = PassportRepository(this)
        zones = FestivalZoneRepository(pandals)
        alerts = FestivalAlertRepository()
        networkMonitor = NetworkMonitor(this)
        photos = PhotoRepository()

        initViews(state)
        setupHoppingTab()
        setupFloatingNav()
        setupNetworkAndAlerts()
        showLocationExplanation()
        loadLiveCrowdHeatmap()
    }

    private fun initViews(state: Bundle?) {
        mapContainer = findViewById(R.id.mapContainer)
        mapView = findViewById(R.id.mapView)
        searchInput = findViewById(R.id.searchInput)
        results = findViewById(R.id.searchResults)
        searchEmptyView = findViewById(R.id.searchEmptyView)
        statusCard = findViewById(R.id.statusCard)
        dismissStatusCard = findViewById(R.id.dismissStatusCard)
        statusTitle = findViewById(R.id.statusTitle)
        statusMessage = findViewById(R.id.statusMessage)
        allowButton = findViewById(R.id.allowLocationButton)
        offlineBanner = findViewById(R.id.offlineBanner)
        festivalAlertCard = findViewById(R.id.festivalAlertCard)
        festivalAlertText = findViewById(R.id.festivalAlertText)
        dismissFestivalAlert = findViewById(R.id.dismissFestivalAlert)

        dismissStatusCard.setOnClickListener {
            statusCard.visibility = View.GONE
        }
        dismissFestivalAlert.setOnClickListener {
            festivalAlertCard.visibility = View.GONE
        }

        pandalsFilterButton = findViewById(R.id.pandalsFilter)
        metroFilterButton = findViewById(R.id.nearbyFilter)
        toiletsFilterButton = findViewById(R.id.toiletsFilter)
        zonesFilterButton = findViewById(R.id.zonesFilter)
        btnNearbyFloating = findViewById(R.id.btnNearbyFloating)
        crowdLegendPill = findViewById(R.id.crowdLegendPill)
        crowdLegendText = findViewById(R.id.crowdLegendText)

        // Metro Card Views
        metroCard = findViewById(R.id.metroCard)
        metroCardIconContainer = findViewById(R.id.metroCardIconContainer)
        metroCardIcon = findViewById(R.id.metroCardIcon)
        metroCardName = findViewById(R.id.metroCardName)
        metroCardDistance = findViewById(R.id.metroCardDistance)
        dismissMetroCard = findViewById(R.id.dismissMetroCard)
        metroAddToHoppingCard = findViewById(R.id.metroAddToHoppingCard)
        metroHoppingActionIcon = findViewById(R.id.metroHoppingActionIcon)
        metroNavigateButton = findViewById(R.id.metroNavigateButton)

        dismissMetroCard.setOnClickListener {
            metroCard.visibility = View.GONE
            currentSelectedMetro = null
        }

        // Toilet Card Views
        toiletCard = findViewById(R.id.toiletCard)
        toiletCardIconContainer = findViewById(R.id.toiletCardIconContainer)
        toiletCardIcon = findViewById(R.id.toiletCardIcon)
        toiletCardName = findViewById(R.id.toiletCardName)
        toiletCardDistance = findViewById(R.id.toiletCardDistance)
        dismissToiletCard = findViewById(R.id.dismissToiletCard)
        toiletAddToHoppingCard = findViewById(R.id.toiletAddToHoppingCard)
        toiletHoppingActionIcon = findViewById(R.id.toiletHoppingActionIcon)
        toiletNavigateButton = findViewById(R.id.toiletNavigateButton)

        dismissToiletCard.setOnClickListener {
            toiletCard.visibility = View.GONE
            currentSelectedToilet = null
        }

        // Hopping Views
        hoppingContainer = findViewById(R.id.hoppingContainer)
        hoppingEmptyView = findViewById(R.id.hoppingEmptyView)
        hoppingRecyclerView = findViewById(R.id.hoppingRecyclerView)
        hoppingSummaryCard = findViewById(R.id.hoppingSummaryCard)
        hoppingTotalDistance = findViewById(R.id.hoppingTotalDistance)
        hoppingEstimatedTime = findViewById(R.id.hoppingEstimatedTime)
        hoppingStopsCount = findViewById(R.id.hoppingStopsCount)
        hoppingContextSummaryText = findViewById(R.id.hoppingContextSummaryText)
        btnOptimizeRoute = findViewById(R.id.btnOptimizeRoute)
        btnTonightsPlan = findViewById(R.id.btnTonightsPlan)
        emptyStateTonightsPlanButton = findViewById(R.id.emptyStateTonightsPlanButton)
        startHoppingButton = findViewById(R.id.startHoppingButton)

        // Floating Bottom Navigation Pills (Map, Hopping, My Pandal)
        navMapPill = findViewById(R.id.navMapPill)
        navMapIcon = findViewById(R.id.navMapIcon)
        navMapText = findViewById(R.id.navMapText)
        navHoppingPill = findViewById(R.id.navHoppingPill)
        navHoppingIcon = findViewById(R.id.navHoppingIcon)
        navHoppingText = findViewById(R.id.navHoppingText)
        navHoppingBadge = findViewById(R.id.navHoppingBadge)
        navMyPandalPill = findViewById(R.id.navMyPandalPill)
        navMyPandalIcon = findViewById(R.id.navMyPandalIcon)
        navMyPandalText = findViewById(R.id.navMyPandalText)

        results.layoutManager = LinearLayoutManager(this)
        allowButton.setOnClickListener { requestLocationPermission() }

        setupFilterPills()
        setupActionButtons()

        mapView.onCreate(state)
        mapView.getMapAsync { readyMap ->
            map = readyMap
            readyMap.setOnMarkerClickListener { marker ->
                val pos = marker.position
                selectedMarkerPosition = pos

                val clickedPandal = shownPandals.firstOrNull {
                    it.latitude == pos.latitude && it.longitude == pos.longitude
                }
                if (clickedPandal != null) {
                    metroCard.visibility = View.GONE
                    toiletCard.visibility = View.GONE
                    currentSelectedMetro = null
                    currentSelectedToilet = null
                    renderMarkers(shownPandals, shownStations, shownToilets)
                    showDetail(clickedPandal)
                    return@setOnMarkerClickListener true
                }

                val clickedStation = shownStations.firstOrNull {
                    it.latitude == pos.latitude && it.longitude == pos.longitude
                }
                if (clickedStation != null) {
                    toiletCard.visibility = View.GONE
                    currentSelectedToilet = null
                    renderMarkers(shownPandals, shownStations, shownToilets)
                    showMetroCard(clickedStation)
                    return@setOnMarkerClickListener true
                }

                val clickedToilet = shownToilets.firstOrNull {
                    it.latitude == pos.latitude && it.longitude == pos.longitude
                }
                if (clickedToilet != null) {
                    metroCard.visibility = View.GONE
                    currentSelectedMetro = null
                    renderMarkers(shownPandals, shownStations, shownToilets)
                    showToiletCard(clickedToilet)
                    return@setOnMarkerClickListener true
                }
                false
            }
            readyMap.setStyle("https://tiles.openfreemap.org/styles/liberty") {
                applyCurrentFilters()
            }
        }

        searchInput.doAfterTextChanged { text ->
            val query = text?.toString()?.trim().orEmpty()
            if (query.isBlank()) {
                results.visibility = View.GONE
                searchEmptyView.visibility = View.GONE
            } else {
                val matching = pandals.searchAll(query, location, places.cachedToilets).take(10)
                if (matching.isEmpty()) {
                    results.visibility = View.GONE
                    searchEmptyView.visibility = View.VISIBLE
                } else {
                    searchEmptyView.visibility = View.GONE
                    results.visibility = View.VISIBLE
                    results.adapter = SearchResultAdapter(matching) { result ->
                        val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
                        imm?.hideSoftInputFromWindow(searchInput.windowToken, 0)

                        searchInput.setText("")
                        searchInput.clearFocus()
                        results.visibility = View.GONE
                        searchEmptyView.visibility = View.GONE

                        if (!isValidCoordinate(result.latitude, result.longitude)) {
                            Log.w(TAG, "Search result has invalid coordinates: ${result.name}")
                            Toast.makeText(this, "Coordinates unavailable for ${result.name}", Toast.LENGTH_SHORT).show()
                            return@SearchResultAdapter
                        }

                        when (result.type) {
                            PlaceType.PANDAL -> {
                                val p = result.pandal ?: pandals.all().firstOrNull { it.id == result.id.removePrefix("pandal:") }
                                if (p != null) {
                                    focusPandal(p)
                                    showDetail(p)
                                }
                            }
                            PlaceType.METRO -> {
                                val s = result.metroStation ?: MetroStation.allStations().firstOrNull {
                                    it.name.equals(result.name, ignoreCase = true)
                                }
                                if (s != null) {
                                    focusMetro(s)
                                    showMetroCard(s)
                                }
                            }
                            PlaceType.TOILET -> {
                                val t = result.toilet ?: shownToilets.firstOrNull { it.id == result.id.removePrefix("toilet:") }
                                if (t != null) {
                                    focusToilet(t)
                                    showToiletCard(t)
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    private fun setupFilterPills() {
        pandalsFilterButton.setOnClickListener {
            filterShowPandals = !filterShowPandals
            updateFilterPillStyles()
            applyCurrentFilters()
        }
        metroFilterButton.setOnClickListener {
            filterShowMetro = !filterShowMetro
            updateFilterPillStyles()
            applyCurrentFilters()
        }
        toiletsFilterButton.setOnClickListener {
            filterShowToilets = !filterShowToilets
            updateFilterPillStyles()
            if (filterShowToilets) {
                fetchAndDisplayToilets()
            } else {
                applyCurrentFilters()
            }
        }

        zonesFilterButton.setOnClickListener {
            showExploreZonesSheet()
        }

        updateFilterPillStyles()
    }

    private fun setupActionButtons() {
        btnNearbyFloating.setOnClickListener {
            showNearbySheet()
        }

        crowdLegendPill.setOnClickListener {
            loadLiveCrowdHeatmap()
            Toast.makeText(this, "Live crowd updated from recent community reports", Toast.LENGTH_SHORT).show()
        }

        btnOptimizeRoute.setOnClickListener {
            showOptimizeRouteDialog()
        }

        btnTonightsPlan.setOnClickListener {
            showTonightsPlanSheet()
        }

        emptyStateTonightsPlanButton.setOnClickListener {
            showTonightsPlanSheet()
        }
    }

    private fun setupNetworkAndAlerts() {
        networkMonitor.addListener { online ->
            if (online) {
                offlineBanner.visibility = View.GONE
                loadLiveCrowdHeatmap()
                loadFestivalAlerts()
            } else {
                offlineBanner.visibility = View.VISIBLE
            }
        }
        loadFestivalAlerts()
    }

    private fun loadFestivalAlerts() {
        alerts.fetchActiveAlerts { alertList ->
            if (alertList.isNotEmpty()) {
                val topAlert = alertList.first()
                festivalAlertCard.visibility = View.VISIBLE
                val agoText = ago(topAlert.timestamp)
                festivalAlertText.text = "${topAlert.title}: ${topAlert.message} • Updated $agoText"
            } else {
                festivalAlertCard.visibility = View.GONE
            }
        }
    }

    private fun loadLiveCrowdHeatmap() {
        crowd.fetchAllRecentCrowds { crowds ->
            liveCrowdMap = crowds
            if (crowds.isNotEmpty()) {
                crowdLegendText.text = "Crowd (${crowds.size} active)"
            } else {
                crowdLegendText.text = "Crowd Live"
            }
            markerBitmapCache.clear()
            if (filterShowPandals) {
                applyCurrentFilters()
            }
        }
    }

    private fun updateFilterPillStyles() {
        val primaryBg = ContextCompat.getColorStateList(this, R.color.primary)
        val surfaceBg = ContextCompat.getColorStateList(this, R.color.surface_floating)
        val onPrimaryText = ContextCompat.getColor(this, R.color.on_primary)
        val textPrimary = ContextCompat.getColor(this, R.color.text_primary)

        if (filterShowPandals) {
            pandalsFilterButton.backgroundTintList = primaryBg
            pandalsFilterButton.setTextColor(onPrimaryText)
            pandalsFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.on_primary)
            pandalsFilterButton.strokeWidth = 0
        } else {
            pandalsFilterButton.backgroundTintList = surfaceBg
            pandalsFilterButton.setTextColor(textPrimary)
            pandalsFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.primary)
            pandalsFilterButton.strokeWidth = dp(1)
        }

        if (filterShowMetro) {
            metroFilterButton.backgroundTintList = primaryBg
            metroFilterButton.setTextColor(onPrimaryText)
            metroFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.on_primary)
            metroFilterButton.strokeWidth = 0
        } else {
            metroFilterButton.backgroundTintList = surfaceBg
            metroFilterButton.setTextColor(textPrimary)
            metroFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.metro_icon)
            metroFilterButton.strokeWidth = dp(1)
        }

        if (filterShowToilets) {
            toiletsFilterButton.backgroundTintList = primaryBg
            toiletsFilterButton.setTextColor(onPrimaryText)
            toiletsFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.on_primary)
            toiletsFilterButton.strokeWidth = 0
        } else {
            toiletsFilterButton.backgroundTintList = surfaceBg
            toiletsFilterButton.setTextColor(textPrimary)
            toiletsFilterButton.iconTint = ContextCompat.getColorStateList(this, R.color.toilet_icon)
            toiletsFilterButton.strokeWidth = dp(1)
        }

        updateFloatingBadges()
    }

    private fun fetchAndDisplayToilets() {
        val loc = location ?: Location("").apply {
            latitude = 22.5726
            longitude = 88.3639
        }
        places.fetchNearbyToilets(loc) { toiletsList, error ->
            if (error != null) {
                Log.e(TAG, "Places API Error: $error")
            }
            if (toiletsList != null) {
                shownToilets = toiletsList
                if (filterShowToilets) {
                    applyCurrentFilters()
                }
            }
        }
    }

    private fun applyCurrentFilters() {
        val pandalList = if (filterShowPandals) {
            location?.let { pandals.nearby(it) } ?: pandals.all()
        } else {
            emptyList()
        }

        val metroList = if (filterShowMetro) {
            MetroStation.allStations()
        } else {
            emptyList()
        }

        val toiletList = if (filterShowToilets) {
            shownToilets
        } else {
            emptyList()
        }

        renderMarkers(pandalList, metroList, toiletList)
    }

    private fun setupHoppingTab() {
        hoppingRecyclerView.layoutManager = LinearLayoutManager(this)
        hoppingAdapter = HoppingAdapter(
            items = mutableListOf(),
            onRemove = { stop ->
                hopping.remove(stop.id)
                Toast.makeText(this, "Removed ${stop.name} from Hopping", Toast.LENGTH_SHORT).show()
            },
            onStopClick = { stop ->
                when (stop.type) {
                    StopType.PANDAL -> {
                        stop.pandalRef?.let { showDetail(it) }
                    }
                    StopType.METRO -> {
                        stop.metroRef?.let { station ->
                            selectTab(isMap = true)
                            focusMetro(station)
                            showMetroCard(station)
                        }
                    }
                    StopType.TOILET -> {
                        stop.toiletRef?.let { toilet ->
                            selectTab(isMap = true)
                            focusToilet(toilet)
                            showToiletCard(toilet)
                        }
                    }
                }
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
            currentSelectedMetro?.let { updateMetroCardHoppingState(it) }
            currentSelectedToilet?.let { updateToiletCardHoppingState(it) }
        }

        startHoppingButton.setOnClickListener {
            val plan = hopping.getPlan()
            if (plan.isNotEmpty()) {
                NavigationLauncher.startHopping(this, location, plan)
            } else {
                Toast.makeText(this, "Add places to your hopping plan first", Toast.LENGTH_SHORT).show()
            }
        }

        findViewById<View>(R.id.hoppingExploreMapButton)?.setOnClickListener {
            selectTab(isMap = true)
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
        navMyPandalPill.setOnClickListener {
            showMyPandalSheet()
        }
        updateFloatingBadges()
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

            navMyPandalPill.setBackgroundColor(Color.TRANSPARENT)
            navMyPandalIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_text))
            navMyPandalText.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive_text))
        } else {
            mapContainer.visibility = View.GONE
            hoppingContainer.visibility = View.VISIBLE

            navHoppingPill.setBackgroundResource(R.drawable.bg_nav_pill_active)
            navHoppingIcon.setColorFilter(ContextCompat.getColor(this, R.color.on_primary))
            navHoppingText.setTextColor(ContextCompat.getColor(this, R.color.on_primary))

            navMapPill.setBackgroundColor(Color.TRANSPARENT)
            navMapIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_text))
            navMapText.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive_text))

            navMyPandalPill.setBackgroundColor(Color.TRANSPARENT)
            navMyPandalIcon.setColorFilter(ContextCompat.getColor(this, R.color.nav_inactive_text))
            navMyPandalText.setTextColor(ContextCompat.getColor(this, R.color.nav_inactive_text))

            updateHoppingUI()
        }
        updateFloatingBadges()
    }

    private fun updateFloatingBadges() {
        val hoppingCount = hopping.getPlanIds().size
        if (hoppingCount > 0) {
            navHoppingBadge.visibility = View.VISIBLE
            navHoppingBadge.text = hoppingCount.toString()
        } else {
            navHoppingBadge.visibility = View.GONE
        }
    }

    private fun updateHoppingBadge() {
        updateFloatingBadges()
    }

    private fun updateHoppingUI() {
        val plan = hopping.getPlan()
        if (plan.isEmpty()) {
            hoppingEmptyView.visibility = View.VISIBLE
            hoppingRecyclerView.visibility = View.GONE
            hoppingSummaryCard.visibility = View.GONE
            hoppingContextSummaryText.text = "0 stops"
        } else {
            hoppingEmptyView.visibility = View.GONE
            hoppingRecyclerView.visibility = View.VISIBLE
            hoppingSummaryCard.visibility = View.VISIBLE

            val countText = "${plan.size} ${if (plan.size == 1) "stop" else "stops"}"
            hoppingStopsCount.text = countText
            hoppingContextSummaryText.text = "$countText • Calculating route…"
            hoppingTotalDistance.text = "Calculating route…"
            hoppingEstimatedTime.text = "…"

            location?.let { origin ->
                routes.computeHoppingRoute(origin, plan) { routeResult ->
                    if (routeResult != null) {
                        val distStr = routeDistanceText(routeResult.totalDistanceMeters)
                        hoppingTotalDistance.text = distStr
                        hoppingEstimatedTime.text = routeResult.totalDurationFormatted
                        hoppingContextSummaryText.text = "$countText • $distStr"
                        hoppingAdapter.updateData(plan, routeResult.legDistances)
                    } else {
                        hoppingTotalDistance.text = "Route unavailable"
                        hoppingEstimatedTime.text = "—"
                        hoppingContextSummaryText.text = countText
                        hoppingAdapter.updateData(plan, emptyList())
                    }
                }
            } ?: run {
                hoppingTotalDistance.text = "Location needed"
                hoppingEstimatedTime.text = "—"
                hoppingContextSummaryText.text = "$countText • GPS needed"
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
            renderMarkers(pandals.all(), emptyList())
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

                    if (filterShowPandals) {
                        val nearby = pandals.nearby(found)
                        val within3km = nearby.filter { it.distanceMeters <= 3000f }
                        if (within3km.isEmpty()) {
                            statusTitle.text = getString(R.string.location_none_nearby)
                            statusMessage.text = getString(R.string.location_none_nearby_body, pandals.all().size)
                        } else if (within3km.size == 1) {
                            statusTitle.text = getString(R.string.location_found_one)
                            statusMessage.text = getString(R.string.location_tap)
                        } else {
                            statusTitle.text = getString(R.string.location_found, within3km.size)
                            statusMessage.text = getString(R.string.location_tap)
                        }
                        if (metroCard.visibility != View.VISIBLE && toiletCard.visibility != View.VISIBLE) {
                            statusCard.visibility = View.VISIBLE
                        }
                    }

                    applyCurrentFilters()

                    if (map?.cameraPosition?.zoom ?: 0.0 < 10.0 && isValidCoordinate(found.latitude, found.longitude)) {
                        map?.cameraPosition = CameraPosition.Builder()
                            .target(LatLng(found.latitude, found.longitude))
                            .zoom(12.5)
                            .build()
                    }

                    // Check passport proximity
                    val nearbyUnvisited = passport.findUnvisitedNearbyPandal(found, pandals.all())
                    if (nearbyUnvisited != null) {
                        eligibility.markVisit(nearbyUnvisited.id)
                    }

                    if (hoppingContainer.visibility == View.VISIBLE) {
                        updateHoppingUI()
                    }

                    currentSelectedMetro?.let { showMetroCard(it) }
                    currentSelectedToilet?.let { showToiletCard(it) }
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
    //  Map rendering (Custom PandalFinder markers + Crowd)
    // ────────────────────────────────────────────────────────

    private fun renderMarkers(
        pandalList: List<Pandal>,
        stationList: List<MetroStation>,
        toiletList: List<PublicToilet> = emptyList()
    ) {
        shownPandals = pandalList
        shownStations = stationList
        shownToilets = toiletList

        map?.let { readyMap ->
            readyMap.clear()
            val icons = IconFactory.getInstance(this)

            // User Location Marker
            location?.let {
                if (isValidCoordinate(it.latitude, it.longitude)) {
                    readyMap.addMarker(
                        MarkerOptions()
                            .position(LatLng(it.latitude, it.longitude))
                            .icon(icons.fromBitmap(userMarkerBitmap()))
                    )
                }
            }

            // Pandal Markers with Custom Silhouette & Crowd Halo
            if (pandalList.isNotEmpty()) {
                pandalList.forEach { pandal ->
                    if (isValidCoordinate(pandal.latitude, pandal.longitude)) {
                        val isSelected = selectedMarkerPosition?.let {
                            it.latitude == pandal.latitude && it.longitude == pandal.longitude
                        } ?: false
                        val crowdLevel = liveCrowdMap[pandal.id]
                        val iconBmp = pandalMarkerBitmap(isSelected, crowdLevel)
                        readyMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(pandal.latitude, pandal.longitude))
                                .icon(icons.fromBitmap(iconBmp))
                        )
                    }
                }
            }

            // Metro Station Markers
            if (stationList.isNotEmpty()) {
                stationList.forEach { station ->
                    if (isValidCoordinate(station.latitude, station.longitude)) {
                        val isSelected = selectedMarkerPosition?.let {
                            it.latitude == station.latitude && it.longitude == station.longitude
                        } ?: false
                        val iconBmp = metroMarkerBitmap(station, isSelected)
                        readyMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(station.latitude, station.longitude))
                                .icon(icons.fromBitmap(iconBmp))
                        )
                    }
                }
            }

            // Public Toilet Markers
            if (toiletList.isNotEmpty()) {
                toiletList.forEach { toilet ->
                    if (isValidCoordinate(toilet.latitude, toilet.longitude)) {
                        val isSelected = selectedMarkerPosition?.let {
                            it.latitude == toilet.latitude && it.longitude == toilet.longitude
                        } ?: false
                        val iconBmp = toiletMarkerBitmap(isSelected)
                        readyMap.addMarker(
                            MarkerOptions()
                                .position(LatLng(toilet.latitude, toilet.longitude))
                                .icon(icons.fromBitmap(iconBmp))
                        )
                    }
                }
            }
        }
    }

    private fun focusPandal(pandal: Pandal) {
        if (!isValidCoordinate(pandal.latitude, pandal.longitude)) return
        selectedMarkerPosition = LatLng(pandal.latitude, pandal.longitude)
        if (!filterShowPandals) {
            filterShowPandals = true
            updateFilterPillStyles()
        }
        applyCurrentFilters()
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(pandal.latitude, pandal.longitude))
            .zoom(15.5)
            .build()
    }

    private fun focusMetro(station: MetroStation) {
        if (!isValidCoordinate(station.latitude, station.longitude)) return
        selectedMarkerPosition = LatLng(station.latitude, station.longitude)
        if (!filterShowMetro) {
            filterShowMetro = true
            updateFilterPillStyles()
        }
        applyCurrentFilters()
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(station.latitude, station.longitude))
            .zoom(15.5)
            .build()
    }

    private fun focusToilet(toilet: PublicToilet) {
        if (!isValidCoordinate(toilet.latitude, toilet.longitude)) return
        selectedMarkerPosition = LatLng(toilet.latitude, toilet.longitude)
        if (!filterShowToilets) {
            filterShowToilets = true
            updateFilterPillStyles()
        }
        applyCurrentFilters()
        map?.cameraPosition = CameraPosition.Builder()
            .target(LatLng(toilet.latitude, toilet.longitude))
            .zoom(15.5)
            .build()
    }

    // ────────────────────────────────────────────────────────
    //  Marker Bitmaps (Part 1: Custom Vector Silhouettes)
    // ────────────────────────────────────────────────────────

    /**
     * Custom PandalFinder Pandal Marker:
     * Stylized Durga Puja pandal/temple silhouette inside a crisp teardrop map-pin.
     * Primary #E52B00 with gold (#FBC222) and warm cream (#FFF5E3) internal temple architecture.
     * Subtle crowd halo when recent crowd reports exist.
     */
    private fun pandalMarkerBitmap(selected: Boolean = false, crowdLevel: Int? = null): Bitmap {
        val cacheKey = "pandal:${selected}:${crowdLevel ?: 0}"
        return markerBitmapCache.getOrPut(cacheKey) {
            val scale = if (selected) 1.25f else 1.0f
            val w = (dp(38) * scale).toInt()
            val h = (dp(48) * scale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val cx = w / 2f
                val headR = (w / 2f) - dp(3) * scale

                // Crowd indicator halo if recent report exists (Part 8)
                if (crowdLevel != null && crowdLevel in 1..10) {
                    val haloColor = when (crowdLevel) {
                        in 1..3 -> Color.parseColor("#45A701")
                        in 4..6 -> Color.parseColor("#FBC222")
                        in 7..8 -> Color.parseColor("#F97E04")
                        else -> Color.parseColor("#E52B00")
                    }
                    paint.color = haloColor
                    paint.style = Paint.Style.STROKE
                    paint.strokeWidth = dp(3) * scale
                    drawCircle(cx, cx, headR + dp(1.5f), paint)
                    paint.style = Paint.Style.FILL
                }

                if (selected) {
                    // Outer gold glowing halo for selected state
                    paint.color = Color.parseColor("#80FBC222")
                    drawCircle(cx, cx, headR + dp(3), paint)
                }

                // Primary Sindoor Red Pin Body
                paint.color = Color.parseColor("#E52B00")
                drawCircle(cx, cx, headR, paint)

                val pinTip = Path().apply {
                    moveTo(cx - (dp(11) * scale), cx + (dp(4) * scale))
                    lineTo(cx, h.toFloat() - (dp(2) * scale))
                    lineTo(cx + (dp(11) * scale), cx + (dp(4) * scale))
                    close()
                }
                drawPath(pinTip, paint)

                // Gold Accent Inner Ring
                paint.color = Color.parseColor("#FBC222")
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dp(1.5f) * scale
                drawCircle(cx, cx, headR * 0.72f, paint)
                paint.style = Paint.Style.FILL

                // Stylized Durga Puja Temple Silhouette in Cream & Gold
                paint.color = Color.parseColor("#FFF5E3")

                // Temple Dome / Kalash
                val templePath = Path().apply {
                    // Kalash finial top
                    moveTo(cx, cx - (dp(9) * scale))
                    lineTo(cx - (dp(4) * scale), cx - (dp(3) * scale))
                    lineTo(cx + (dp(4) * scale), cx - (dp(3) * scale))
                    close()

                    // Temple Tier
                    moveTo(cx - (dp(6) * scale), cx - (dp(2.5f) * scale))
                    lineTo(cx + (dp(6) * scale), cx - (dp(2.5f) * scale))
                    lineTo(cx + (dp(7) * scale), cx + (dp(1.5f) * scale))
                    lineTo(cx - (dp(7) * scale), cx + (dp(1.5f) * scale))
                    close()

                    // Base Sanctum
                    moveTo(cx - (dp(6.5f) * scale), cx + (dp(2.5f) * scale))
                    lineTo(cx + (dp(6.5f) * scale), cx + (dp(2.5f) * scale))
                    lineTo(cx + (dp(6.5f) * scale), cx + (dp(7) * scale))
                    lineTo(cx - (dp(6.5f) * scale), cx + (dp(7) * scale))
                    close()
                }
                drawPath(templePath, paint)

                // Inner Sanctum Arch (Sindoor Red cut)
                paint.color = Color.parseColor("#E52B00")
                drawRoundRect(
                    RectF(
                        cx - (dp(2.5f) * scale),
                        cx + (dp(3.5f) * scale),
                        cx + (dp(2.5f) * scale),
                        cx + (dp(7) * scale)
                    ),
                    dp(2) * scale,
                    dp(2) * scale,
                    paint
                )
            }
            bitmap
        }
    }

    /**
     * Dedicated Metro Marker:
     * Clean metro train silhouette inside a rounded/circular pin in line/green theme.
     */
    private fun metroMarkerBitmap(station: MetroStation, selected: Boolean = false): Bitmap {
        val cacheKey = "metro:${station.lineColor}:${selected}"
        return markerBitmapCache.getOrPut(cacheKey) {
            val scale = if (selected) 1.25f else 1.0f
            val w = (dp(36) * scale).toInt()
            val h = (dp(46) * scale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val cx = w / 2f
                val headR = (w / 2f) - dp(2.5f) * scale
                val color = station.lineColor

                if (selected) {
                    paint.color = Color.parseColor("#6645A701")
                    drawCircle(cx, cx, headR + dp(3), paint)
                }

                // Line Color Pin Body
                paint.color = color
                drawCircle(cx, cx, headR, paint)

                val pinTip = Path().apply {
                    moveTo(cx - (dp(10) * scale), cx + (dp(4) * scale))
                    lineTo(cx, h.toFloat() - (dp(2) * scale))
                    lineTo(cx + (dp(10) * scale), cx + (dp(4) * scale))
                    close()
                }
                drawPath(pinTip, paint)

                // Clean White Circle Center
                paint.color = Color.WHITE
                drawCircle(cx, cx, headR * 0.65f, paint)

                // Train Silhouette
                paint.color = color
                val trainRect = RectF(
                    cx - (dp(5) * scale),
                    cx - (dp(6) * scale),
                    cx + (dp(5) * scale),
                    cx + (dp(4.5f) * scale)
                )
                drawRoundRect(trainRect, dp(2) * scale, dp(2) * scale, paint)

                // Train Window Cutout
                paint.color = Color.WHITE
                drawRoundRect(
                    RectF(
                        cx - (dp(3.5f) * scale),
                        cx - (dp(4.5f) * scale),
                        cx + (dp(3.5f) * scale),
                        cx - (dp(1) * scale)
                    ),
                    dp(1) * scale,
                    dp(1) * scale,
                    paint
                )

                // Headlights
                drawCircle(cx - (dp(2.5f) * scale), cx + (dp(2) * scale), dp(0.9f) * scale, paint)
                drawCircle(cx + (dp(2.5f) * scale), cx + (dp(2) * scale), dp(0.9f) * scale, paint)
            }
            bitmap
        }
    }

    /**
     * Dedicated Public Toilet / Restroom Marker:
     * Clean restroom silhouette inside a teal (#0D9488) pin.
     */
    private fun toiletMarkerBitmap(selected: Boolean = false): Bitmap {
        val cacheKey = "toilet:${selected}"
        return markerBitmapCache.getOrPut(cacheKey) {
            val scale = if (selected) 1.25f else 1.0f
            val w = (dp(36) * scale).toInt()
            val h = (dp(46) * scale).toInt()
            val bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            Canvas(bitmap).apply {
                val paint = Paint(Paint.ANTI_ALIAS_FLAG)
                val cx = w / 2f
                val headR = (w / 2f) - dp(2.5f) * scale
                val tealColor = Color.parseColor("#0D9488")

                if (selected) {
                    paint.color = Color.parseColor("#660D9488")
                    drawCircle(cx, cx, headR + dp(3), paint)
                }

                // Teal Pin Body
                paint.color = tealColor
                drawCircle(cx, cx, headR, paint)

                val pinTip = Path().apply {
                    moveTo(cx - (dp(10) * scale), cx + (dp(4) * scale))
                    lineTo(cx, h.toFloat() - (dp(2) * scale))
                    lineTo(cx + (dp(10) * scale), cx + (dp(4) * scale))
                    close()
                }
                drawPath(pinTip, paint)

                // Clean White Circle Center
                paint.color = Color.WHITE
                drawCircle(cx, cx, headR * 0.65f, paint)

                // Restroom Figures Silhouette
                paint.color = tealColor

                // Man head & body
                drawCircle(cx - (dp(3) * scale), cx - (dp(4) * scale), dp(1.5f) * scale, paint)
                drawRoundRect(
                    RectF(
                        cx - (dp(4.5f) * scale),
                        cx - (dp(2) * scale),
                        cx - (dp(1.5f) * scale),
                        cx + (dp(4.5f) * scale)
                    ),
                    dp(1) * scale,
                    dp(1) * scale,
                    paint
                )

                // Woman head & body (dress)
                drawCircle(cx + (dp(3) * scale), cx - (dp(4) * scale), dp(1.5f) * scale, paint)
                val dressPath = Path().apply {
                    moveTo(cx + (dp(1.8f) * scale), cx - (dp(2) * scale))
                    lineTo(cx + (dp(4.2f) * scale), cx - (dp(2) * scale))
                    lineTo(cx + (dp(5.2f) * scale), cx + (dp(4.5f) * scale))
                    lineTo(cx + (dp(0.8f) * scale), cx + (dp(4.5f) * scale))
                    close()
                }
                drawPath(dressPath, paint)
            }
            bitmap
        }
    }

    private fun userMarkerBitmap(): Bitmap {
        val size = dp(28)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        Canvas(bitmap).apply {
            val paint = Paint(Paint.ANTI_ALIAS_FLAG)
            val cx = size / 2f
            paint.color = Color.WHITE
            drawCircle(cx, cx, cx - dp(1), paint)
            paint.color = Color.rgb(21, 101, 192)
            drawCircle(cx, cx, cx - dp(4), paint)
        }
        return bitmap
    }

    // ────────────────────────────────────────────────────────
    //  Compact Floating Metro Card
    // ────────────────────────────────────────────────────────

    private fun showMetroCard(station: MetroStation) {
        currentSelectedMetro = station
        currentSelectedToilet = null
        statusCard.visibility = View.GONE
        toiletCard.visibility = View.GONE
        metroCard.visibility = View.VISIBLE

        metroCardIcon.setColorFilter(station.lineColor)
        metroCardIconContainer.backgroundTintList = ColorStateList.valueOf(station.lineBadgeBgColor)
        metroCardName.text = "${station.name} Metro"

        if (location != null && isValidCoordinate(location!!.latitude, location!!.longitude)) {
            val sLoc = Location("").apply {
                latitude = station.latitude
                longitude = station.longitude
            }
            val geodesicDistance = location!!.distanceTo(sLoc)
            val formattedGeodesic = distanceShort(geodesicDistance)
            metroCardDistance.text = "$formattedGeodesic from you • ${station.line}"

            routes.routesToStation(location!!, station) { routesData ->
                if (currentSelectedMetro?.name == station.name && routesData?.drive != null) {
                    val roadDist = routeDistanceText(routesData.drive.distanceMeters)
                    metroCardDistance.text = "$roadDist from you • ${station.line}"
                }
            }
        } else {
            metroCardDistance.text = "${station.line} • Location needed for distance"
        }

        updateMetroCardHoppingState(station)

        metroAddToHoppingCard.setOnClickListener {
            val inPlan = hopping.isMetroInPlan(station)
            if (inPlan) {
                hopping.removeMetro(station)
                Toast.makeText(this, "Removed ${station.name} Metro from Hopping", Toast.LENGTH_SHORT).show()
            } else {
                val added = hopping.addMetro(station)
                if (added) {
                    Toast.makeText(this, "Added ${station.name} Metro to Hopping", Toast.LENGTH_SHORT).show()
                }
            }
            updateMetroCardHoppingState(station)
        }

        metroNavigateButton.setOnClickListener {
            NavigationLauncher.openMetroRoute(this, station)
        }
    }

    private fun updateMetroCardHoppingState(station: MetroStation) {
        val inPlan = hopping.isMetroInPlan(station)
        if (inPlan) {
            metroHoppingActionIcon.setImageResource(R.drawable.ic_check)
            metroHoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
            metroAddToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
            metroAddToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
        } else {
            metroHoppingActionIcon.setImageResource(R.drawable.ic_add)
            metroHoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            metroAddToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            metroAddToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.outline)
        }
    }

    // ────────────────────────────────────────────────────────
    //  Compact Floating Toilet Card
    // ────────────────────────────────────────────────────────

    private fun showToiletCard(toilet: PublicToilet) {
        currentSelectedToilet = toilet
        currentSelectedMetro = null
        statusCard.visibility = View.GONE
        metroCard.visibility = View.GONE
        toiletCard.visibility = View.VISIBLE

        toiletCardName.text = toilet.name

        if (location != null && isValidCoordinate(location!!.latitude, location!!.longitude)) {
            val tLoc = Location("").apply {
                latitude = toilet.latitude
                longitude = toilet.longitude
            }
            val geodesicDistance = location!!.distanceTo(tLoc)
            val formattedGeodesic = distanceShort(geodesicDistance)
            toiletCardDistance.text = "$formattedGeodesic from you • ${toilet.address}"
        } else {
            toiletCardDistance.text = toilet.address
        }

        updateToiletCardHoppingState(toilet)

        toiletAddToHoppingCard.setOnClickListener {
            val inPlan = hopping.isToiletInPlan(toilet)
            if (inPlan) {
                hopping.removeToilet(toilet)
                Toast.makeText(this, "Removed ${toilet.name} from Hopping", Toast.LENGTH_SHORT).show()
            } else {
                val added = hopping.addToilet(toilet)
                if (added) {
                    Toast.makeText(this, "Added ${toilet.name} to Hopping", Toast.LENGTH_SHORT).show()
                }
            }
            updateToiletCardHoppingState(toilet)
        }

        toiletNavigateButton.setOnClickListener {
            val uri = android.net.Uri.parse("geo:0,0?q=${toilet.latitude},${toilet.longitude}(${android.net.Uri.encode(toilet.name)})")
            val intent = android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            } else {
                startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri))
            }
        }
    }

    private fun updateToiletCardHoppingState(toilet: PublicToilet) {
        val inPlan = hopping.isToiletInPlan(toilet)
        if (inPlan) {
            toiletHoppingActionIcon.setImageResource(R.drawable.ic_check)
            toiletHoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
            toiletAddToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
            toiletAddToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
        } else {
            toiletHoppingActionIcon.setImageResource(R.drawable.ic_add)
            toiletHoppingActionIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
            toiletAddToHoppingCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
            toiletAddToHoppingCard.strokeColor = ContextCompat.getColor(this, R.color.outline)
        }
    }

    // ────────────────────────────────────────────────────────
    //  Pandal Detail Bottom Sheet (Enhanced)
    // ────────────────────────────────────────────────────────

    private fun showDetail(pandal: Pandal) {
        val item = location?.let { pandal.withDistanceFrom(it) } ?: pandal
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_pandal_detail, null)
        sheet.setContentView(view)

        currentDetailPandal = pandal
        currentDetailSheetView = view

        // ── Pandal info ──
        view.findViewById<TextView>(R.id.detailName).text = item.name
        view.findViewById<TextView>(R.id.detailArea).text = item.area
        val mainDistance = view.findViewById<TextView>(R.id.detailDistance)
        mainDistance.text = if (location != null) "Finding road route…" else "Acquiring GPS location…"

        view.findViewById<View>(R.id.closeSheet).setOnClickListener {
            sheet.dismiss()
        }

        // ── Save / Favorite Action Button (Part 6) ──
        val favoriteButton = view.findViewById<MaterialCardView>(R.id.favoriteButton)
        val favoriteIcon = view.findViewById<ImageView>(R.id.favoriteIcon)
        val favoriteText = view.findViewById<TextView>(R.id.favoriteText)

        fun updateFavoriteState() {
            val isFav = favorites.isFavorite(item.id)
            if (isFav) {
                favoriteIcon.setImageResource(R.drawable.ic_heart_filled)
                favoriteIcon.setColorFilter(ContextCompat.getColor(this, R.color.primary))
                favoriteText.text = "Saved"
                favoriteButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.hopping_surface))
                favoriteButton.strokeColor = ContextCompat.getColor(this, R.color.hopping_outline)
            } else {
                favoriteIcon.setImageResource(R.drawable.ic_heart_outline)
                favoriteIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                favoriteText.text = "Save"
                favoriteButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                favoriteButton.strokeColor = ContextCompat.getColor(this, R.color.outline)
            }
        }
        updateFavoriteState()

        favoriteButton.setOnClickListener {
            val nowFav = favorites.toggleFavorite(item.id)
            val scaleX = ObjectAnimator.ofFloat(favoriteIcon, "scaleX", 1.0f, 1.35f, 1.0f)
            val scaleY = ObjectAnimator.ofFloat(favoriteIcon, "scaleY", 1.0f, 1.35f, 1.0f)
            AnimatorSet().apply {
                playTogether(scaleX, scaleY)
                duration = 300
                start()
            }
            updateFavoriteState()
            updateFilterPillStyles()
            Toast.makeText(this, if (nowFav) "Saved to your favorites" else "Removed from favorites", Toast.LENGTH_SHORT).show()
        }

        // ── Passport / Visited Action Button (Part 7) ──
        val passportButton = view.findViewById<MaterialCardView>(R.id.passportButton)
        val passportIcon = view.findViewById<ImageView>(R.id.passportIcon)
        val passportText = view.findViewById<TextView>(R.id.passportText)

        fun updatePassportState() {
            val isVis = passport.isVisited(item.id)
            if (isVis) {
                passportIcon.setImageResource(R.drawable.ic_check_circle)
                passportIcon.setColorFilter(ContextCompat.getColor(this, R.color.metro_green))
                passportText.text = "Visited"
                passportButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.metro_surface))
                passportButton.strokeColor = ContextCompat.getColor(this, R.color.metro_outline)
            } else {
                passportIcon.setImageResource(R.drawable.ic_passport)
                passportIcon.setColorFilter(ContextCompat.getColor(this, R.color.text_secondary))
                passportText.text = "Mark Visited"
                passportButton.setCardBackgroundColor(ContextCompat.getColor(this, R.color.surface_variant))
                passportButton.strokeColor = ContextCompat.getColor(this, R.color.outline)
            }
        }
        updatePassportState()

        passportButton.setOnClickListener {
            val isVis = passport.isVisited(item.id)
            if (isVis) {
                passport.removeVisited(item.id)
                Toast.makeText(this, "Visit removed", Toast.LENGTH_SHORT).show()
            } else {
                passport.markVisited(item)
                Toast.makeText(this, "Stamped in your Pandal Passport!", Toast.LENGTH_SHORT).show()
            }
            updatePassportState()
            updateFilterPillStyles()
        }

        // ── Community Photo Gallery (Parts 9-14) ──
        val photoRecycler = view.findViewById<RecyclerView>(R.id.pandalPhotosRecyclerView)
        val photoPlaceholder = view.findViewById<View>(R.id.pandalPhotoPlaceholder)
        val addPhotoBtn = view.findViewById<MaterialButton>(R.id.addPhotoButton)
        val uploadProgressLayout = view.findViewById<LinearLayout>(R.id.photoUploadProgressLayout)
        val uploadProgressBar = view.findViewById<ProgressBar>(R.id.photoUploadProgressBar)
        val uploadProgressText = view.findViewById<TextView>(R.id.photoUploadProgressText)

        val photoAdapter = com.pandalfinder.ui.PhotoGalleryAdapter(emptyList()) { photo ->
            showFullPhotoDialog(photo)
        }
        photoRecycler.layoutManager = LinearLayoutManager(this, LinearLayoutManager.HORIZONTAL, false)
        photoRecycler.adapter = photoAdapter

        val photoListener = photos.listenToPhotos(item.id) { photoList ->
            if (currentDetailPandal?.id == item.id && currentDetailSheetView === view) {
                photoAdapter.updatePhotos(photoList)
                if (photoList.isNotEmpty()) {
                    photoRecycler.visibility = View.VISIBLE
                    photoPlaceholder.visibility = View.GONE
                } else {
                    photoRecycler.visibility = View.GONE
                    photoPlaceholder.visibility = View.VISIBLE
                }
            }
        }

        addPhotoBtn.setOnClickListener {
            pendingUploadPandalId = item.id
            pendingUploadLayout = uploadProgressLayout
            pendingUploadProgressBar = uploadProgressBar
            pendingUploadProgressText = uploadProgressText
            photoPicker.launch("image/*")
        }

        // ── Optional Metadata Section (Part 13: Theme, Established, Known For) ──
        val infoSection = view.findViewById<LinearLayout>(R.id.pandalInfoSection)
        val infoTheme = view.findViewById<TextView>(R.id.infoTheme)
        val infoEst = view.findViewById<TextView>(R.id.infoEstablished)
        val infoKnown = view.findViewById<TextView>(R.id.infoKnownFor)

        var hasAnyInfo = false
        if (!item.theme.isNullOrBlank()) {
            infoTheme.visibility = View.VISIBLE
            infoTheme.text = "Theme: ${item.theme}"
            hasAnyInfo = true
        }
        if (item.establishedYear != null && item.establishedYear > 0) {
            infoEst.visibility = View.VISIBLE
            infoEst.text = "Established: ${item.establishedYear}"
            hasAnyInfo = true
        }
        if (!item.knownFor.isNullOrBlank()) {
            infoKnown.visibility = View.VISIBLE
            infoKnown.text = "Known for: ${item.knownFor}"
            hasAnyInfo = true
        }
        infoSection.visibility = if (hasAnyInfo) View.VISIBLE else View.GONE

        // ── Travel Mode Chips ──
        val walkDistance = view.findViewById<TextView>(R.id.timeWalk)
        val bikeDistance = view.findViewById<TextView>(R.id.timeBike)
        val carDistance = view.findViewById<TextView>(R.id.timeCar)
        walkDistance.text = "…"
        bikeDistance.text = "…"
        carDistance.text = "…"

        calculateDetailRoutes(location, item, view)

        // Diagnostic tap on road distance
        mainDistance.setOnLongClickListener {
            showDiagnosticDialog()
            true
        }
        mainDistance.setOnClickListener {
            if (routes.diagnosticState.lastHttpStatus != 200) {
                showDiagnosticDialog()
            }
        }

        // ── Weather (Authoritative Open-Meteo + Refresh) ──
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
                            weatherUpdated.text = "Live weather · Updated ${ago(result.fetchedAt)}"
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
            loadWeather()
            Toast.makeText(this, "Refreshing weather…", Toast.LENGTH_SHORT).show()
        }

        // ── Crowd (from Firestore realtime snapshot) & Best Time to Visit (Part 9) ──
        val crowdStatus = view.findViewById<TextView>(R.id.crowdStatus)
        val crowdLabel = view.findViewById<TextView>(R.id.crowdLabel)
        val crowdUpdated = view.findViewById<TextView>(R.id.crowdUpdated)
        val bestTimeSummary = view.findViewById<TextView>(R.id.bestTimeSummary)
        crowdStatus.text = getString(R.string.crowd_loading)
        crowdLabel.text = ""
        crowdUpdated.text = ""

        fun refreshCrowd() {
            crowd.load(item) { result, _ ->
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

            crowd.getHistoricalCrowdByHour(item.id) { slots ->
                if (slots != null && slots.isNotEmpty()) {
                    val summaryText = slots.joinToString("  •  ") { "${it.hourLabel}: ${it.levelLabel}" }
                    bestTimeSummary.text = summaryText
                } else {
                    bestTimeSummary.text = "Not enough crowd data yet."
                }
            }
        }
        refreshCrowd()

        val crowdListener = crowd.listenToPandalCrowd(item.id) { status ->
            if (currentDetailPandal?.id == item.id && currentDetailSheetView === view) {
                if (status.level != null) {
                    crowdStatus.text = "${status.level} / 10"
                    crowdLabel.text = crowdLabelText(status.level)
                    val countText = if (status.reportCount == 1) "1 report" else "${status.reportCount} reports"
                    val timeText = status.updatedAt?.let { "Updated ${ago(it)}" } ?: ""
                    crowdUpdated.text = "$countText · $timeText"
                } else {
                    crowdStatus.text = "No reports yet"
                    crowdLabel.text = ""
                    crowdUpdated.text = "Be the first to report crowd level"
                }
            }
        }

        view.findViewById<MaterialButton>(R.id.updateCrowdButton).setOnClickListener {
            showCrowdUpdateSheet(item) { refreshCrowd() }
        }

        // ── Nearest Metro (Part 14) ──
        val metroName = view.findViewById<TextView>(R.id.metroName)
        val metroWalkTime = view.findViewById<TextView>(R.id.metroWalkTime)
        val metroNavigateBtn = view.findViewById<MaterialButton>(R.id.metroNavigateButton)
        val metroResult = metro.nearestTo(item)
        val mDistStr = distanceShort(metroResult.distanceMeters)
        metroName.text = "${metroResult.station.name} (${metroResult.station.line}) · $mDistStr"
        val walkMins = Math.max(1, (metroResult.distanceMeters / 80.0).toInt())
        metroWalkTime.text = "~$walkMins min walk"

        metroNavigateBtn.setOnClickListener {
            NavigationLauncher.openMetroRoute(this, metroResult.station)
        }

        // ── Compact Secondary Hopping Toggle Card ──
        val addToHoppingCard = view.findViewById<MaterialCardView>(R.id.addToHoppingCard)
        val hoppingActionIcon = view.findViewById<ImageView>(R.id.hoppingActionIcon)

        fun updateHoppingButtonState() {
            val inPlan = hopping.isPandalInPlan(item.id)
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
            val inPlan = hopping.isPandalInPlan(item.id)
            if (inPlan) {
                hopping.removePandal(item.id)
                Toast.makeText(this, "Removed from Hopping", Toast.LENGTH_SHORT).show()
            } else {
                hopping.addPandal(item)
                Toast.makeText(this, "Added to Hopping", Toast.LENGTH_SHORT).show()
            }
            updateHoppingButtonState()
        }

        // ── Dominant Primary Navigation Button ──
        view.findViewById<MaterialButton>(R.id.navigateButton).setOnClickListener {
            NavigationLauncher.open(this, item)
        }

        sheet.setOnDismissListener {
            crowdListener?.remove()
            photoListener?.remove()
            currentDetailPandal = null
            currentDetailSheetView = null
            pendingUploadPandalId = null
            pendingUploadLayout = null
            pendingUploadProgressBar = null
            pendingUploadProgressText = null
        }

        sheet.setOnShowListener {
            val behavior = BottomSheetBehavior.from(sheet.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)!!)
            behavior.peekHeight = dp(620)
            behavior.state = BottomSheetBehavior.STATE_COLLAPSED
        }
        sheet.show()
    }

    private fun calculateDetailRoutes(origin: Location?, item: Pandal, view: View) {
        val mainDistance = view.findViewById<TextView>(R.id.detailDistance)
        val walkDistance = view.findViewById<TextView>(R.id.timeWalk)
        val bikeDistance = view.findViewById<TextView>(R.id.timeBike)
        val carDistance = view.findViewById<TextView>(R.id.timeCar)

        if (origin == null) {
            mainDistance.text = "Acquiring GPS location…"
            walkDistance.text = "…"
            bikeDistance.text = "…"
            carDistance.text = "…"
            return
        }

        routes.routesFrom(origin, item) { routeData ->
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
    }

    private fun showDiagnosticDialog() {
        val diag = routes.diagnosticState
        val view = layoutInflater.inflate(R.layout.dialog_routes_diagnostic, null)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.diagApiKeyStatus).text =
            if (diag.apiKeyConfigured) "Google Routes API Key: Configured (${BuildConfig.GOOGLE_ROUTES_API_KEY.take(8)}...)"
            else "Google Routes API Key: NOT CONFIGURED"

        view.findViewById<TextView>(R.id.diagPackageName).text =
            "Package: ${GoogleRoutesRepository.PACKAGE_NAME} (Cert: ${GoogleRoutesRepository.CERT_SHA1.take(8)}...)"

        view.findViewById<TextView>(R.id.diagGpsStatus).text =
            if (location != null) "Current GPS: Acquired (${"%.5f".format(location!!.latitude)}, ${"%.5f".format(location!!.longitude)})"
            else "Current GPS: Acquiring / Not available"

        view.findViewById<TextView>(R.id.diagOriginCoords).text = "Origin: ${diag.lastOrigin.ifBlank { "None" }}"
        view.findViewById<TextView>(R.id.diagDestCoords).text = "Destination: ${diag.lastDestination.ifBlank { "None" }}"
        view.findViewById<TextView>(R.id.diagLastHttp).text = "Last Routes HTTP Status: ${if (diag.lastHttpStatus > 0) "${diag.lastHttpStatus}" else "—"}"
        view.findViewById<TextView>(R.id.diagLastError).text = "Last Error / Result: ${diag.lastErrorMessage.ifBlank { "None" }}"

        view.findViewById<View>(R.id.closeDiagnostic).setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun showCrowdUpdateSheet(pandal: Pandal, onUpdated: () -> Unit) {
        val view = layoutInflater.inflate(R.layout.sheet_crowd_update, null)
        val sheet = BottomSheetDialog(this)
        sheet.setContentView(view)

        view.findViewById<TextView>(R.id.crowdUpdatePandalTitle).text = pandal.name
        val numberText = view.findViewById<TextView>(R.id.crowdSelectedNumber)
        val labelText = view.findViewById<TextView>(R.id.crowdSelectedLabel)
        val descText = view.findViewById<TextView>(R.id.crowdSelectedDesc)
        val submitBtn = view.findViewById<MaterialButton>(R.id.submitCrowdReportBtn)

        var selectedLevel = 5

        fun updateLevelDisplay(level: Int) {
            selectedLevel = level
            numberText.text = "$level / 10"
            val (lbl, colorHex) = crowd.getCrowdLabel(level)
            labelText.text = lbl
            labelText.setTextColor(Color.parseColor(colorHex))
            descText.text = when (level) {
                in 1..2 -> "No crowd, direct entry with zero waiting."
                in 3..4 -> "Light crowd, walking smoothly through pandal."
                in 5..6 -> "Moderate queue, 10–20 minute wait."
                in 7..8 -> "Very busy, packed entry queue (30–45 mins)."
                9 -> "Extremely busy, massive queue moving slowly."
                10 -> "Completely packed, heavy police barricades."
                else -> ""
            }
        }

        updateLevelDisplay(5)

        val buttons = listOf(
            view.findViewById<Button>(R.id.btnLevel1) to 1,
            view.findViewById<Button>(R.id.btnLevel2) to 2,
            view.findViewById<Button>(R.id.btnLevel3) to 3,
            view.findViewById<Button>(R.id.btnLevel4) to 4,
            view.findViewById<Button>(R.id.btnLevel5) to 5,
            view.findViewById<Button>(R.id.btnLevel6) to 6,
            view.findViewById<Button>(R.id.btnLevel7) to 7,
            view.findViewById<Button>(R.id.btnLevel8) to 8,
            view.findViewById<Button>(R.id.btnLevel9) to 9,
            view.findViewById<Button>(R.id.btnLevel10) to 10
        )

        buttons.forEach { (btn, lvl) ->
            btn.setOnClickListener {
                updateLevelDisplay(lvl)
            }
        }

        submitBtn.setOnClickListener {
            submitBtn.isEnabled = false
            submitBtn.text = "Saving..."

            val devId = android.provider.Settings.Secure.getString(contentResolver, android.provider.Settings.Secure.ANDROID_ID) ?: "device_user"
            crowd.submit(pandal, selectedLevel, devId, location) { error ->
                if (error != null) {
                    submitBtn.isEnabled = true
                    submitBtn.text = "Submit Crowd Report"
                    Toast.makeText(this, "Crowd update failed: ${error.localizedMessage}", Toast.LENGTH_LONG).show()
                } else {
                    Toast.makeText(this, "Crowd updated: $selectedLevel/10", Toast.LENGTH_SHORT).show()
                    sheet.dismiss()
                    onUpdated()
                }
            }
        }

        view.findViewById<View>(R.id.closeCrowdUpdate).setOnClickListener { sheet.dismiss() }
        sheet.show()
    }

    private fun showFullPhotoDialog(photo: com.pandalfinder.data.PandalPhoto) {
        val view = layoutInflater.inflate(R.layout.dialog_photo_view, null)
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(view)

        val timeText = view.findViewById<TextView>(R.id.photoUploadedTime)
        val fullImage = view.findViewById<ImageView>(R.id.fullPhotoImage)
        val progress = view.findViewById<ProgressBar>(R.id.fullPhotoProgress)
        val deleteBtn = view.findViewById<MaterialButton>(R.id.deletePhotoButton)
        val closeBtn = view.findViewById<View>(R.id.closePhotoView)

        timeText.text = if (photo.createdAt > 0) "Uploaded ${ago(photo.createdAt)}" else "Community photo"

        java.util.concurrent.Executors.newSingleThreadExecutor().execute {
            val bmp = runCatching {
                val stream = java.net.URL(photo.downloadUrl).openStream()
                val decoded = BitmapFactory.decodeStream(stream)
                stream.close()
                decoded
            }.getOrNull()

            mainHandler.post {
                progress.visibility = View.GONE
                if (bmp != null) {
                    fullImage.setImageBitmap(bmp)
                }
            }
        }

        photos.currentUserId { currentUid ->
            if (photo.uploadedBy.isNotBlank() && photo.uploadedBy == currentUid) {
                deleteBtn.visibility = View.VISIBLE
                deleteBtn.setOnClickListener {
                    deleteBtn.isEnabled = false
                    deleteBtn.text = "Deleting..."
                    photos.deletePhoto(photo) { err ->
                        if (err != null) {
                            Toast.makeText(this, "Failed to delete: ${err.localizedMessage}", Toast.LENGTH_SHORT).show()
                            deleteBtn.isEnabled = true
                            deleteBtn.text = "Delete This Photo"
                        } else {
                            Toast.makeText(this, "Photo deleted", Toast.LENGTH_SHORT).show()
                            dialog.dismiss()
                        }
                    }
                }
            }
        }

        closeBtn.setOnClickListener { dialog.dismiss() }
        dialog.show()
    }

    // ────────────────────────────────────────────────────────
    //  Nearby Pandals Sheet (Part 5)
    // ────────────────────────────────────────────────────────

    private fun showNearbySheet() {
        val userLoc = location
        if (userLoc == null) {
            Toast.makeText(this, "Current GPS location required for Nearby mode", Toast.LENGTH_SHORT).show()
            requestLocationPermission()
            return
        }

        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_nearby_pandals, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeNearbySheet).setOnClickListener { sheet.dismiss() }

        val r1 = view.findViewById<MaterialButton>(R.id.radius1km)
        val r3 = view.findViewById<MaterialButton>(R.id.radius3km)
        val r5 = view.findViewById<MaterialButton>(R.id.radius5km)
        val countLabel = view.findViewById<TextView>(R.id.nearbyCountLabel)
        val rv = view.findViewById<RecyclerView>(R.id.nearbyRecyclerView)
        rv.layoutManager = LinearLayoutManager(this)

        var selectedRadius = 3000f

        fun renderNearbyList() {
            val allNearby = pandals.nearby(userLoc)
            val filtered = allNearby.filter { it.distanceMeters <= selectedRadius }
            countLabel.text = "${filtered.size} pandals within ${"%.0f".format(selectedRadius / 1000)} km of you"
            rv.adapter = NearbyPandalAdapter(filtered) { p ->
                sheet.dismiss()
                focusPandal(p)
                showDetail(p)
            }
        }

        fun updateRadiusButtons(active: MaterialButton) {
            val primaryBg = ContextCompat.getColorStateList(this, R.color.primary)
            val surfaceBg = ContextCompat.getColorStateList(this, R.color.surface_variant)
            listOf(r1, r3, r5).forEach { btn ->
                if (btn == active) {
                    btn.backgroundTintList = primaryBg
                    btn.setTextColor(ContextCompat.getColor(this, R.color.on_primary))
                } else {
                    btn.backgroundTintList = surfaceBg
                    btn.setTextColor(ContextCompat.getColor(this, R.color.text_primary))
                }
            }
            renderNearbyList()
        }

        r1.setOnClickListener { selectedRadius = 1000f; updateRadiusButtons(r1) }
        r3.setOnClickListener { selectedRadius = 3000f; updateRadiusButtons(r3) }
        r5.setOnClickListener { selectedRadius = 5000f; updateRadiusButtons(r5) }

        renderNearbyList()
        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  My Pandal Sheet (Unified Personal Hub: Saved & Passport)
    // ────────────────────────────────────────────────────────

    private fun showMyPandalSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_my_pandal, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeMyPandalSheet).setOnClickListener { sheet.dismiss() }

        val savedCount = favorites.getFavoriteIds().size
        val savedCountView = view.findViewById<TextView>(R.id.myPandalSavedCount)
        savedCountView.text = if (savedCount == 1) "1 saved pandal" else "$savedCount saved pandals"

        val visitedCount = passport.getVisitedCount()
        val totalPandals = pandals.all().size
        val percent = if (totalPandals > 0) ((visitedCount.toFloat() / totalPandals) * 100).toInt() else 0
        val passportCountView = view.findViewById<TextView>(R.id.myPandalPassportCount)
        passportCountView.text = if (visitedCount == 1) "1 pandal visited ($percent%)" else "$visitedCount pandals visited ($percent%)"

        val progressTextView = view.findViewById<TextView>(R.id.myPandalPassportProgressText)
        val areaMap = passport.getAreaBreakdown()
        if (areaMap.isNotEmpty()) {
            progressTextView.text = "${areaMap.size} areas explored • Tap to view passport"
        } else {
            progressTextView.text = "Track Puja progress & collected pandals"
        }

        view.findViewById<View>(R.id.myPandalSavedCard).setOnClickListener {
            sheet.dismiss()
            showSavedFavoritesSheet()
        }

        view.findViewById<View>(R.id.myPandalPassportCard).setOnClickListener {
            sheet.dismiss()
            showPassportSheet()
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Saved / Favorites Sheet (Part 6)
    // ────────────────────────────────────────────────────────

    private fun showSavedFavoritesSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_saved_favorites, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeSavedSheet).setOnClickListener { sheet.dismiss() }

        val countSubtitle = view.findViewById<TextView>(R.id.savedCountSubtitle)
        val emptyText = view.findViewById<TextView>(R.id.savedEmptyText)
        val rv = view.findViewById<RecyclerView>(R.id.savedRecyclerView)
        val btnAddAll = view.findViewById<MaterialButton>(R.id.btnAddAllSavedToHopping)
        rv.layoutManager = LinearLayoutManager(this)

        fun refreshSaved() {
            val savedList = favorites.getFavoritePandals(pandals)
            countSubtitle.text = "${savedList.size} bookmarked pandals"
            if (savedList.isEmpty()) {
                emptyText.visibility = View.VISIBLE
                rv.visibility = View.GONE
                btnAddAll.visibility = View.GONE
            } else {
                emptyText.visibility = View.GONE
                rv.visibility = View.VISIBLE
                btnAddAll.visibility = View.VISIBLE
                rv.adapter = SavedPandalAdapter(
                    savedList,
                    onSelect = { p ->
                        sheet.dismiss()
                        focusPandal(p)
                        showDetail(p)
                    },
                    onRemove = { p ->
                        favorites.toggleFavorite(p.id)
                        updateFilterPillStyles()
                        refreshSaved()
                    }
                )
            }
        }

        btnAddAll.setOnClickListener {
            val savedList = favorites.getFavoritePandals(pandals)
            var addedCount = 0
            savedList.forEach { p ->
                if (hopping.addPandal(p)) addedCount++
            }
            sheet.dismiss()
            selectTab(isMap = false)
            Toast.makeText(this, "Added $addedCount pandals to Hopping Trail", Toast.LENGTH_SHORT).show()
        }

        refreshSaved()
        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Pandal Passport Sheet (Part 7)
    // ────────────────────────────────────────────────────────

    private fun showPassportSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_pandal_passport, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closePassportSheet).setOnClickListener { sheet.dismiss() }

        val visitedCountText = view.findViewById<TextView>(R.id.passportVisitedCountText)
        val percentText = view.findViewById<TextView>(R.id.passportPercentText)
        val progressBar = view.findViewById<ProgressBar>(R.id.passportProgressBar)
        val areasExplored = view.findViewById<TextView>(R.id.passportAreasExploredText)
        val rv = view.findViewById<RecyclerView>(R.id.passportRecyclerView)
        rv.layoutManager = LinearLayoutManager(this)

        val total = pandals.all().size
        val visited = passport.getVisitedCount()
        val percent = if (total > 0) ((visited.toFloat() / total) * 100).toInt() else 0

        visitedCountText.text = "$visited / $total visited"
        percentText.text = "$percent%"
        progressBar.progress = percent

        val areaMap = passport.getAreaBreakdown()
        if (areaMap.isNotEmpty()) {
            areasExplored.text = areaMap.entries.joinToString("  •  ") { "${it.key}: ${it.value}" }
        } else {
            areasExplored.text = "Explore pandals around Kolkata to stamp your passport."
        }

        val visitedRecords = passport.getVisitedRecords()
        rv.adapter = PassportRecordAdapter(visitedRecords) { record ->
            val p = pandals.all().firstOrNull { it.id == record.pandalId }
            if (p != null) {
                sheet.dismiss()
                focusPandal(p)
                showDetail(p)
            }
        }

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Explore Zones Sheet (Part 10)
    // ────────────────────────────────────────────────────────

    private fun showExploreZonesSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_explore_zones, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closeZonesSheet).setOnClickListener { sheet.dismiss() }

        val rv = view.findViewById<RecyclerView>(R.id.zonesRecyclerView)
        rv.layoutManager = LinearLayoutManager(this)

        val zoneList = zones.getZones()
        rv.adapter = FestivalZoneAdapter(
            zoneList,
            onExplore = { zone ->
                sheet.dismiss()
                filterShowPandals = true
                updateFilterPillStyles()
                applyCurrentFilters()
                map?.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(zone.centerLat, zone.centerLng))
                    .zoom(13.5)
                    .build()
                Toast.makeText(this, "Exploring ${zone.name}", Toast.LENGTH_SHORT).show()
            },
            onAddToHopping = { zone ->
                val topPandals = zone.pandals.take(4)
                var added = 0
                topPandals.forEach { if (hopping.addPandal(it)) added++ }
                sheet.dismiss()
                selectTab(isMap = false)
                Toast.makeText(this, "Added $added pandals from ${zone.name} to Trail", Toast.LENGTH_SHORT).show()
            }
        )

        sheet.show()
    }

    // ────────────────────────────────────────────────────────
    //  Smart Route Optimizer Dialog (Part 3)
    // ────────────────────────────────────────────────────────

    private fun showOptimizeRouteDialog() {
        val plan = hopping.getPlan()
        if (plan.size < 2) {
            Toast.makeText(this, "Add at least 2 stops to optimize route", Toast.LENGTH_SHORT).show()
            return
        }

        val dialog = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.dialog_optimize_route, null)
        dialog.setContentView(view)

        view.findViewById<MaterialButton>(R.id.btnCancelOptimize).setOnClickListener {
            dialog.dismiss()
        }

        view.findViewById<MaterialButton>(R.id.btnConfirmOptimize).setOnClickListener {
            dialog.dismiss()
            val result = RouteOptimizer.optimize(location, plan)
            if (result.wasReordered) {
                hopping.clear()
                result.optimizedStops.forEach { s ->
                    when (s.type) {
                        StopType.PANDAL -> s.pandalRef?.let { hopping.addPandal(it) }
                        StopType.METRO -> s.metroRef?.let { hopping.addMetro(it) }
                        StopType.TOILET -> s.toiletRef?.let { hopping.addToilet(it) }
                    }
                }
                val savedKm = "%.1f".format(result.savedDistanceMeters / 1000.0)
                Toast.makeText(this, "Route optimized! Reduced travel by ~$savedKm km", Toast.LENGTH_LONG).show()
            } else {
                Toast.makeText(this, "Your route is already optimal!", Toast.LENGTH_SHORT).show()
            }
        }

        dialog.show()
    }

    // ────────────────────────────────────────────────────────
    //  Tonight's Plan Generator Sheet (Part 11)
    // ────────────────────────────────────────────────────────

    private fun showTonightsPlanSheet() {
        val sheet = BottomSheetDialog(this)
        val view = layoutInflater.inflate(R.layout.sheet_tonights_plan, null)
        sheet.setContentView(view)

        view.findViewById<View>(R.id.closePlanSheet).setOnClickListener { sheet.dismiss() }

        val planTitle = view.findViewById<TextView>(R.id.planTitle)
        val planDesc = view.findViewById<TextView>(R.id.planDescription)
        val statsSummary = view.findViewById<TextView>(R.id.planStatsSummary)
        val rv = view.findViewById<RecyclerView>(R.id.planStopsRecyclerView)
        val btnAddPlan = view.findViewById<MaterialButton>(R.id.btnAddPlanToHopping)
        rv.layoutManager = LinearLayoutManager(this)

        val tonightsPlan = TonightsPlanGenerator.generate(
            location,
            pandals,
            metro,
            places.cachedToilets,
            liveCrowdMap
        )

        planTitle.text = tonightsPlan.title
        planDesc.text = tonightsPlan.description

        val distKm = "%.1f km".format(tonightsPlan.totalDistanceMeters / 1000.0)
        val hours = tonightsPlan.estimatedDurationMinutes / 60
        val mins = tonightsPlan.estimatedDurationMinutes % 60
        val timeStr = if (hours > 0) "~${hours}h ${mins}m" else "~${mins}m"
        statsSummary.text = "${tonightsPlan.stops.size} stops • $distKm • $timeStr"

        rv.adapter = PlanStopsPreviewAdapter(tonightsPlan.stops)

        btnAddPlan.setOnClickListener {
            sheet.dismiss()
            tonightsPlan.stops.forEach { s ->
                when (s.type) {
                    StopType.PANDAL -> s.pandalRef?.let { hopping.addPandal(it) }
                    StopType.METRO -> s.metroRef?.let { hopping.addMetro(it) }
                    StopType.TOILET -> s.toiletRef?.let { hopping.addToilet(it) }
                }
            }
            selectTab(isMap = false)
            Toast.makeText(this, "Tonight's Plan added to your Hopping Trail!", Toast.LENGTH_SHORT).show()
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
                    loadLiveCrowdHeatmap()
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
    private fun dp(value: Float): Float = value * resources.displayMetrics.density

    private fun ago(time: Long): String {
        val minutes = ((System.currentTimeMillis() - time) / 60_000).coerceAtLeast(0)
        return if (minutes < 1) "just now" else "$minutes min ago"
    }

    companion object {
        private const val TAG = "PandalFinderMain"
        private const val REQUEST_LOCATION = 1001

        fun isValidCoordinate(lat: Double, lng: Double): Boolean =
            !lat.isNaN() && !lat.isInfinite() && !lng.isNaN() && !lng.isInfinite() &&
            lat in -90.0..90.0 && lng in -180.0..180.0

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

// ────────────────────────────────────────────────────────────
//  Adapters
// ────────────────────────────────────────────────────────────

private class SearchResultAdapter(
    private val items: List<SearchResult>,
    private val selected: (SearchResult) -> Unit
) : RecyclerView.Adapter<SearchResultAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val iconContainer: FrameLayout = v.findViewById(R.id.resultIconContainer)
        val icon: ImageView = v.findViewById(R.id.resultIcon)
        val name: TextView = v.findViewById(R.id.resultName)
        val area: TextView = v.findViewById(R.id.resultArea)
        val distance: TextView = v.findViewById(R.id.resultDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_search_result, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        when (item.type) {
            PlaceType.PANDAL -> {
                holder.icon.setImageResource(R.drawable.ic_pandal_icon)
                holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.primary))
                holder.iconContainer.backgroundTintList = ContextCompat.getColorStateList(context, R.color.surface_variant)
                holder.name.text = item.name
                holder.area.text = item.subtitle
                holder.distance.text = if (item.distanceMeters > 0) {
                    MainActivity.distanceShort(item.distanceMeters)
                } else ""
            }
            PlaceType.METRO -> {
                holder.icon.setImageResource(R.drawable.ic_metro_train)
                val lineColor = item.metroStation?.lineColor ?: ContextCompat.getColor(context, R.color.metro_icon)
                holder.icon.setColorFilter(lineColor)
                val badgeBg = item.metroStation?.lineBadgeBgColor ?: ContextCompat.getColor(context, R.color.surface_variant)
                holder.iconContainer.backgroundTintList = ColorStateList.valueOf(badgeBg)
                holder.name.text = item.name
                holder.area.text = item.subtitle
                holder.distance.text = if (item.distanceMeters > 0) {
                    MainActivity.distanceShort(item.distanceMeters)
                } else ""
            }
            PlaceType.TOILET -> {
                holder.icon.setImageResource(R.drawable.ic_restroom)
                holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.toilet_icon))
                holder.iconContainer.backgroundTintList = ContextCompat.getColorStateList(context, R.color.toilet_surface)
                holder.name.text = item.name
                holder.area.text = item.subtitle
                holder.distance.text = if (item.distanceMeters > 0) {
                    MainActivity.distanceShort(item.distanceMeters)
                } else ""
            }
        }
        holder.itemView.setOnClickListener { selected(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class NearbyPandalAdapter(
    private val items: List<Pandal>,
    private val onSelect: (Pandal) -> Unit
) : RecyclerView.Adapter<NearbyPandalAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.nearbyName)
        val subtitle: TextView = v.findViewById(R.id.nearbySubtitle)
        val distance: TextView = v.findViewById(R.id.nearbyDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_nearby_pandal, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.subtitle.text = item.area
        holder.distance.text = MainActivity.distanceShort(item.distanceMeters)
        holder.itemView.setOnClickListener { onSelect(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class SavedPandalAdapter(
    private val items: List<Pandal>,
    private val onSelect: (Pandal) -> Unit,
    private val onRemove: (Pandal) -> Unit
) : RecyclerView.Adapter<SavedPandalAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.savedPandalName)
        val area: TextView = v.findViewById(R.id.savedPandalArea)
        val btnRemove: ImageButton = v.findViewById(R.id.btnRemoveSaved)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_saved_pandal, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.area.text = item.area
        holder.itemView.setOnClickListener { onSelect(item) }
        holder.btnRemove.setOnClickListener { onRemove(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class PassportRecordAdapter(
    private val items: List<VisitedPandalRecord>,
    private val onSelect: (VisitedPandalRecord) -> Unit
) : RecyclerView.Adapter<PassportRecordAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.passportPandalName)
        val areaAndTime: TextView = v.findViewById(R.id.passportPandalAreaAndTime)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_passport_visited, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.pandalName
        val timeStr = if (item.visitedAtTimestamp > 0) {
            val min = ((System.currentTimeMillis() - item.visitedAtTimestamp) / 60_000).coerceAtLeast(0)
            if (min < 60) "$min min ago" else "${min / 60}h ago"
        } else "Visited"
        holder.areaAndTime.text = "${item.area} • $timeStr"
        holder.itemView.setOnClickListener { onSelect(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class FestivalZoneAdapter(
    private val items: List<FestivalZone>,
    private val onExplore: (FestivalZone) -> Unit,
    private val onAddToHopping: (FestivalZone) -> Unit
) : RecyclerView.Adapter<FestivalZoneAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val name: TextView = v.findViewById(R.id.zoneName)
        val countBadge: TextView = v.findViewById(R.id.zoneCountBadge)
        val description: TextView = v.findViewById(R.id.zoneDescription)
        val btnExplore: MaterialButton = v.findViewById(R.id.btnExploreZone)
        val btnAdd: MaterialButton = v.findViewById(R.id.btnAddZoneToHopping)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_festival_zone, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        holder.name.text = item.name
        holder.countBadge.text = "${item.count} Pandals"
        holder.description.text = item.description
        holder.btnExplore.setOnClickListener { onExplore(item) }
        holder.btnAdd.setOnClickListener { onAddToHopping(item) }
    }

    override fun getItemCount(): Int = items.size
}

private class PlanStopsPreviewAdapter(
    private val items: List<HoppingStop>
) : RecyclerView.Adapter<PlanStopsPreviewAdapter.Holder>() {

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val index: TextView = v.findViewById(R.id.planStopIndex)
        val icon: ImageView = v.findViewById(R.id.planStopIcon)
        val name: TextView = v.findViewById(R.id.planStopName)
        val subtitle: TextView = v.findViewById(R.id.planStopSubtitle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, type: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_plan_stop, parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context
        holder.index.text = (position + 1).toString()
        holder.name.text = item.name
        holder.subtitle.text = item.subtitle

        when (item.type) {
            StopType.PANDAL -> {
                holder.icon.setImageResource(R.drawable.ic_pandal_icon)
                holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.primary))
            }
            StopType.METRO -> {
                holder.icon.setImageResource(R.drawable.ic_metro_train)
                val color = item.metroRef?.lineColor ?: ContextCompat.getColor(context, R.color.metro_icon)
                holder.icon.setColorFilter(color)
            }
            StopType.TOILET -> {
                holder.icon.setImageResource(R.drawable.ic_restroom)
                holder.icon.setColorFilter(ContextCompat.getColor(context, R.color.toilet_icon))
            }
        }
    }

    override fun getItemCount(): Int = items.size
}
