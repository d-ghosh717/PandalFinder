package com.pandalfinder

import android.Manifest
import android.content.pm.PackageManager
import android.location.Location
import android.os.Bundle
import android.widget.Button
import com.google.android.material.textfield.TextInputEditText
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource

class MainActivity : AppCompatActivity() {

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var recyclerView: RecyclerView
    private lateinit var btnFindPandals: Button
    private lateinit var btnSearchArea: Button
    private lateinit var etSearchArea: TextInputEditText
    private val allPandals = Pandal.getSamplePandals()

    companion object {
        private const val LOCATION_PERMISSION_REQUEST_CODE = 1001
        // Removed distance filter - show ALL pandals sorted by distance
        
        // Kolkata area mapping for reference only (not used for distance calculation)
        // Distance is ALWAYS calculated from user's GPS location
        private val KOLKATA_AREAS = mapOf(
            // North Kolkata
            "kumartuli" to Pair(22.5958, 88.3639),
            "bagbazar" to Pair(22.5897, 88.3565),
            "shobhabazar" to Pair(22.5880, 88.3606),
            "hatibagan" to Pair(22.5834, 88.3626),
            "shyambazar" to Pair(22.5817, 88.3714),
            
            // Central Kolkata  
            "park street" to Pair(22.5552, 88.3516),
            "college street" to Pair(22.5745, 88.3642),
            "college square" to Pair(22.5726, 88.3639),
            "esplanade" to Pair(22.5697, 88.3501),
            "dharmatala" to Pair(22.5697, 88.3492),
            "park circus" to Pair(22.5513, 88.3693),
            "sealdah" to Pair(22.5689, 88.3692),
            
            // South Kolkata
            "ballygunge" to Pair(22.5326, 88.3639),
            "gariahat" to Pair(22.5179, 88.3642),
            "deshapriya park" to Pair(22.5229, 88.3639),
            "ekdalia" to Pair(22.5189, 88.3639),
            "rashbehari" to Pair(22.5156, 88.3625),
            "lake gardens" to Pair(22.5181, 88.3512),
            "jadavpur" to Pair(22.4987, 88.3728),
            "garia" to Pair(22.4656, 88.3823),
            "behala" to Pair(22.4898, 88.3145),
            "barisha" to Pair(22.4756, 88.3234),
            
            // Salt Lake & East
            "salt lake" to Pair(22.5778, 88.4167),
            "bidhannagar" to Pair(22.5778, 88.4167),
            "lake town" to Pair(22.5736, 88.4076),
            "new town" to Pair(22.6089, 88.4638),
            "rajarhat" to Pair(22.6089, 88.4638),
            "baguiati" to Pair(22.6425, 88.4368),
            
            // Howrah
            "howrah" to Pair(22.5833, 88.3412),
            "shibpur" to Pair(22.5672, 88.3356),
            
            // Other major areas
            "dum dum" to Pair(22.6289, 88.4233),
            "barracpore" to Pair(22.7645, 88.3782),
            "tollygunge" to Pair(22.4678, 88.3523),
            "alipore" to Pair(22.5312, 88.3289)
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize location client
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        // Initialize views
        btnFindPandals = findViewById(R.id.btnFindPandals)
        btnSearchArea = findViewById(R.id.btnSearchArea)
        etSearchArea = findViewById(R.id.etSearchArea)
        recyclerView = findViewById(R.id.recyclerViewPandals)
        recyclerView.layoutManager = LinearLayoutManager(this)

        // Set button click listeners
        btnFindPandals.setOnClickListener {
            if (checkLocationPermission()) {
                findNearbyPandals()
            } else {
                requestLocationPermission()
            }
        }
        
        btnSearchArea.setOnClickListener {
            searchByArea()
        }
    }

    private fun checkLocationPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermission() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            ),
            LOCATION_PERMISSION_REQUEST_CODE
        )
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                findNearbyPandals()
            } else {
                Toast.makeText(
                    this,
                    getString(R.string.permission_denied),
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    private fun findNearbyPandals() {
        if (!checkLocationPermission()) {
            return
        }

        try {
            // Get current location
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { location: Location? ->
                if (location != null) {
                    processNearbyPandals(location)
                } else {
                    Toast.makeText(
                        this,
                        getString(R.string.location_unavailable),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }.addOnFailureListener {
                Toast.makeText(
                    this,
                    getString(R.string.location_unavailable),
                    Toast.LENGTH_SHORT
                ).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                getString(R.string.permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }

    private fun searchByArea() {
        try {
            val searchQuery = etSearchArea.text?.toString()?.trim()?.lowercase() ?: ""
            
            if (searchQuery.isEmpty()) {
                Toast.makeText(this, "Please enter an area or pandal name", Toast.LENGTH_SHORT).show()
                return
            }
            
            // Find matching area KEY in the map (exact match first, then partial)
            val matchedAreaKey: String? = when {
                KOLKATA_AREAS.containsKey(searchQuery) -> searchQuery
                else -> KOLKATA_AREAS.keys.find { it.contains(searchQuery) || searchQuery.contains(it) }
            }
            
            if (matchedAreaKey != null) {
                // Found area - get its coordinates and show nearby pandals
                val areaCoords = KOLKATA_AREAS[matchedAreaKey]!! // Safe: key confirmed above
                val areaLocation = Location("").apply {
                    latitude = areaCoords.first
                    longitude = areaCoords.second
                }
                showPandalsNearLocationWithGPSDistance(areaLocation, matchedAreaKey)
            } else {
                // Not an area - try to find pandal by name
                val matchedPandal = allPandals.find { 
                    it.name.lowercase().contains(searchQuery) 
                }
                
                if (matchedPandal != null) {
                    val pandalLocation = Location("").apply {
                        latitude = matchedPandal.latitude
                        longitude = matchedPandal.longitude
                    }
                    showPandalsNearLocationWithGPSDistance(pandalLocation, matchedPandal.name)
                } else {
                    Toast.makeText(
                        this,
                        "No area or pandal found for \"$searchQuery\"",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        } catch (e: Exception) {
            val errMsg = e.message ?: e.javaClass.simpleName
            Toast.makeText(this, "Error searching: $errMsg", Toast.LENGTH_LONG).show()
            e.printStackTrace()
        }
    }
    
    private fun showPandalsNearLocationWithGPSDistance(searchLocation: Location, searchName: String) {
        if (!checkLocationPermission()) {
            requestLocationPermission()
            return
        }

        try {
            val cancellationTokenSource = CancellationTokenSource()
            fusedLocationClient.getCurrentLocation(
                Priority.PRIORITY_HIGH_ACCURACY,
                cancellationTokenSource.token
            ).addOnSuccessListener { userLocation: Location? ->
                try {
                    if (userLocation != null) {
                        // Step 1: Calculate AERIAL distance from searched location (for filtering only)
                        // Use aerial distance for filtering so 10km radius works correctly
                        val FILTER_RADIUS_KM = 10.0f  // 10km aerial = ~14km driving
                        val nearbySearchedArea = allPandals.filter { pandal ->
                            val pandalLoc = Location("").apply {
                                latitude = pandal.latitude
                                longitude = pandal.longitude
                            }
                            val aerialKm = searchLocation.distanceTo(pandalLoc) / 1000f
                            aerialKm <= FILTER_RADIUS_KM
                        }

                        // Step 2: Calculate DRIVING distance from USER's GPS (for display)
                        nearbySearchedArea.forEach { pandal ->
                            pandal.calculateDistanceFrom(userLocation)
                        }

                        // Step 3: Sort by driving distance from user
                        val sortedByUserDistance = nearbySearchedArea.sortedBy { it.distance }

                        // Step 4: Prioritize exact pandal name match (if searching by name)
                        val searchedPandal = sortedByUserDistance.find {
                            it.name.lowercase().contains(searchName.lowercase())
                        }

                        val finalList = if (searchedPandal != null) {
                            listOf(searchedPandal) + sortedByUserDistance.filter { it != searchedPandal }
                        } else {
                            sortedByUserDistance
                        }

                        // Update RecyclerView
                        recyclerView.adapter = PandalAdapter(finalList)

                        val msg = if (finalList.isEmpty()) {
                            "No pandals found near \"$searchName\" within ${FILTER_RADIUS_KM.toInt()}km"
                        } else if (searchedPandal != null) {
                            "Found \"${searchedPandal.name}\" + ${finalList.size - 1} nearby pandals"
                        } else {
                            "Showing ${finalList.size} pandals near \"$searchName\""
                        }
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

                    } else {
                        Toast.makeText(this, "Could not get your GPS location. Please try again.", Toast.LENGTH_LONG).show()
                    }
                } catch (inner: Exception) {
                    Toast.makeText(this, "Error displaying results: ${inner.javaClass.simpleName}", Toast.LENGTH_LONG).show()
                    inner.printStackTrace()
                }
            }.addOnFailureListener { ex ->
                Toast.makeText(this, "GPS failed: ${ex.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
            }
        } catch (e: SecurityException) {
            Toast.makeText(
                this,
                getString(R.string.permission_denied),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
    
    private fun processNearbyPandals(userLocation: Location) {
        // Calculate distance for all pandals from user's GPS location
        allPandals.forEach { pandal ->
            pandal.calculateDistanceFrom(userLocation)
        }

        // Show ALL pandals sorted by distance from GPS
        val sortedPandals = allPandals.sortedBy { it.distance }

        // Update RecyclerView with ALL pandals
        recyclerView.adapter = PandalAdapter(sortedPandals)
        Toast.makeText(
            this,
            "Showing ${sortedPandals.size} pandals sorted by distance from your location",
            Toast.LENGTH_SHORT
        ).show()
    }
}
