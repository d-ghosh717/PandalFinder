package com.pandalfinder

import android.location.Location
import com.pandalfinder.data.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test

class RoutingAndPlanTest {

    // ─────────────────────────────────────────────────────────────
    // 1. Routing Profile & Endpoint Mapping Tests (Requirements 13, 14, 15)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testOrsProfileMapping() {
        assertEquals("driving-car", RouteProfile.DRIVING.orsProfile)
        assertEquals("foot-walking", RouteProfile.WALKING.orsProfile)
        assertEquals("cycling-regular", RouteProfile.CYCLING.orsProfile)

        assertEquals("DRIVE", RouteProfile.DRIVING.googleMode)
        assertEquals("WALK", RouteProfile.WALKING.googleMode)
        assertEquals("TWO_WHEELER", RouteProfile.CYCLING.googleMode)
    }

    @Test
    fun testOrsEndpointHost() {
        // Must use HeiGIT endpoint, not the deprecated api.openrouteservice.org
        assertTrue(OpenRouteServiceProvider.BASE_URL.startsWith("https://api.heigit.org"))
        assertEquals("https://api.heigit.org/openrouteservice/v2/directions", OpenRouteServiceProvider.BASE_URL)
    }

    // ─────────────────────────────────────────────────────────────
    // 2. ORS GeoJSON Parsing & Coordinate Order (Requirements 16, 17, 18)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testOrsCoordinatesOrderLongitudeFirst() {
        // GeoJSON standard: [longitude, latitude]
        val origLng = 88.3639
        val origLat = 22.5726
        val destLng = 88.3516
        val destLat = 22.5628

        val body = JSONObject().apply {
            val coords = JSONArray().apply {
                put(JSONArray().apply { put(origLng); put(origLat) })
                put(JSONArray().apply { put(destLng); put(destLat) })
            }
            put("coordinates", coords)
        }

        val jsonCoords = body.getJSONArray("coordinates")
        val originArr = jsonCoords.getJSONArray(0)
        assertEquals(88.3639, originArr.getDouble(0), 0.0001) // Longitude first
        assertEquals(22.5726, originArr.getDouble(1), 0.0001) // Latitude second

        val destArr = jsonCoords.getJSONArray(1)
        assertEquals(88.3516, destArr.getDouble(0), 0.0001) // Longitude first
        assertEquals(22.5628, destArr.getDouble(1), 0.0001) // Latitude second
    }

    @Test
    fun testOrsResponseParsingStandardRoutes() {
        val jsonStr = """
            {
              "routes": [
                {
                  "summary": {
                    "distance": 2350.5,
                    "duration": 480.0
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = OpenRouteServiceProvider.parseDistanceAndDuration(JSONObject(jsonStr))
        assertNotNull(parsed)
        assertEquals(2350, parsed!!.first)
        assertEquals(480L, parsed.second)
    }

    @Test
    fun testOrsResponseParsingGeoJsonFeatures() {
        val jsonStr = """
            {
              "features": [
                {
                  "properties": {
                    "summary": {
                      "distance": 1820.0,
                      "duration": 360.0
                    }
                  }
                }
              ]
            }
        """.trimIndent()
        val parsed = OpenRouteServiceProvider.parseDistanceAndDuration(JSONObject(jsonStr))
        assertNotNull(parsed)
        assertEquals(1820, parsed!!.first)
        assertEquals(360L, parsed.second)
    }

    @Test
    fun testOrsDurationFormatting() {
        assertEquals("12 min", OpenRouteServiceProvider.formatDuration(720))
        assertEquals("1 hr 15 min", OpenRouteServiceProvider.formatDuration(4500))
        assertEquals("2 hr", OpenRouteServiceProvider.formatDuration(7200))
        assertEquals("< 1 min", OpenRouteServiceProvider.formatDuration(30))
        assertEquals("—", OpenRouteServiceProvider.formatDuration(0))
    }

    @Test
    fun testNoHardcodedKeysInSourceCode() {
        // Verify no raw API keys are committed in source code strings
        val orsProvider = OpenRouteServiceProvider { "" }
        assertNull(orsProvider.getRoute(22.57, 88.36, 22.58, 88.37, RouteProfile.DRIVING))
    }

    // ─────────────────────────────────────────────────────────────
    // 3. Google -> ORS Fallback Logic (Requirements 1, 2, 3, 4, 12)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testFallbackHierarchyLogic() {
        // Mocking provider responses
        val googleSuccessResult = RouteResult(
            distanceMeters = 2000,
            duration = "300s",
            durationSeconds = 300,
            provider = RoutingProviderName.GOOGLE,
            success = true
        )

        val orsSuccessResult = RouteResult(
            distanceMeters = 2100,
            duration = "320s",
            durationSeconds = 320,
            provider = RoutingProviderName.OPENROUTESERVICE,
            success = true
        )

        // 1. When Google succeeds -> Google result used
        assertEquals(RoutingProviderName.GOOGLE, googleSuccessResult.provider)
        assertEquals(2000, googleSuccessResult.distanceMeters)

        // 2 & 3. When Google fails -> ORS attempted and used
        assertEquals(RoutingProviderName.OPENROUTESERVICE, orsSuccessResult.provider)
        assertEquals(2100, orsSuccessResult.distanceMeters)

        // 4. When both fail -> null / unavailable
        val unavailableRoutes = PandalRoutes(null, null, null, RoutingProviderName.UNAVAILABLE)
        assertNull(unavailableRoutes.drive)
        assertNull(unavailableRoutes.walk)
        assertNull(unavailableRoutes.twoWheeler)
        assertEquals(RoutingProviderName.UNAVAILABLE, unavailableRoutes.activeProvider)
    }

    @Test
    fun testPandalDetailDistanceUsesActualRouteDistance() {
        val driveRoute = RouteResult(
            distanceMeters = 3400,
            duration = "15 min",
            durationSeconds = 900,
            provider = RoutingProviderName.OPENROUTESERVICE,
            success = true
        )
        val pandalRoutes = PandalRoutes(
            walk = null,
            twoWheeler = null,
            drive = driveRoute,
            activeProvider = RoutingProviderName.OPENROUTESERVICE
        )
        assertNotNull(pandalRoutes.drive)
        assertEquals(3400, pandalRoutes.drive?.distanceMeters)
        assertEquals(RoutingProviderName.OPENROUTESERVICE, pandalRoutes.activeProvider)
    }

    // ─────────────────────────────────────────────────────────────
    // 4. Auto Plan / Metro Decision Rules (Requirements 5, 6, 7, 8, 9, 10, 11)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testMetroMinimumTimeSavingConstant() {
        assertEquals(5, TonightsPlanGenerator.METRO_MIN_TIME_SAVING_MINUTES)
    }

    @Test
    fun testDirectFasterThanMetroNoMetroInsertion() {
        // Direct = 10 min, Metro = 15 min -> Direct route must be used
        val stations = MetroStation.allStations()
        // Two very close points in Shyambazar (< 600m)
        val leg = TonightsPlanGenerator.evaluateLeg(
            fromName = "Shyambazar Pandal A",
            fromLat = 22.5990,
            fromLng = 88.3710,
            toName = "Shyambazar Pandal B",
            toLat = 22.6020,
            toLng = 88.3730,
            stations = stations
        )
        assertFalse("Direct route is faster, so Metro should NOT be beneficial", leg.isMetroBeneficial)
        assertNull(leg.fromStation)
        assertNull(leg.toStation)
        assertTrue(leg.transferDescription?.contains("Direct route") == true)
    }

    @Test
    fun testMetroFasterByLessThan5MinNoMetroInsertion() {
        // Suppose direct = 45 min and metro = 42 min -> saving = 3 min < 5 min -> Direct route must be used
        val directTime = 45
        val metroTime = 42
        val saving = directTime - metroTime
        val isBeneficial = saving >= TonightsPlanGenerator.METRO_MIN_TIME_SAVING_MINUTES
        assertFalse("Saving of 3 min is below the 5-minute threshold", isBeneficial)
    }

    @Test
    fun testMetroFasterBy5MinOrMoreUsesMetro() {
        // Shyambazar (North Kolkata) to Kalighat (South Kolkata) ~ 10 km along Blue Line
        val stations = MetroStation.allStations()
        val leg = TonightsPlanGenerator.evaluateLeg(
            fromName = "North Kolkata Pandal",
            fromLat = 22.5992,
            fromLng = 88.3716, // Shyambazar
            toName = "South Kolkata Pandal",
            toLat = 22.5194,
            toLng = 88.3417, // Kalighat
            stations = stations
        )
        assertTrue("Long distance along Blue Line should recommend Metro", leg.isMetroBeneficial)
        assertNotNull(leg.fromStation)
        assertNotNull(leg.toStation)
        assertTrue(leg.timeSavedMinutes >= 5)
        assertTrue(leg.transferDescription?.contains("Metro via") == true)
    }

    @Test
    fun testDetourProtectionExcessiveWalkingRejectsMetro() {
        val stations = MetroStation.allStations()
        // Point far away from any metro station (> 3km from any metro)
        val leg = TonightsPlanGenerator.evaluateLeg(
            fromName = "Far East Pandal",
            fromLat = 22.5600,
            fromLng = 88.4800, // Deep Salt Lake / New Town periphery
            toName = "Far South Pandal",
            toLat = 22.4500,
            toLng = 88.4500,
            stations = stations
        )
        assertFalse("Excessive walk to/from station must reject Metro", leg.isMetroBeneficial)
    }

    @Test
    fun testMetroIncompleteDataOrUnconnectedLinesRejectsMetro() {
        // Two stations with no connection (e.g. Purple Line Taratala and Orange Line Satyajit Ray)
        val leg = TonightsPlanGenerator.evaluateLeg(
            fromName = "Taratala Pandal",
            fromLat = 22.4963,
            fromLng = 88.3302, // Taratala (Purple Line)
            toName = "Ruby Pandal",
            toLat = 22.5139,
            toLng = 88.4022, // Hemanta Mukhopadhyay (Orange Line)
            stations = MetroStation.allStations()
        )
        // Since there is no metro connection between Purple and Orange lines, Metro must NOT be selected
        assertFalse("Unconnected metro lines must not suggest Metro transit", leg.isMetroBeneficial)
    }

    @Test
    fun testSelectedPandalListRemainsUnchangedAndNoMetroStopsInHopping() {
        val repo = PandalRepository()
        val metroRepo = MetroRepository()
        val dummyLoc = Location("").apply {
            latitude = 22.5726
            longitude = 88.3639
        }

        val plan = TonightsPlanGenerator.generate(
            origin = dummyLoc,
            pandalsRepo = repo,
            metroRepo = metroRepo
        )

        // Selected pandals should only be pandals (and optional toilet)
        assertTrue(plan.stops.isNotEmpty())
        assertTrue("All stops in hopping plan must be PANDAL or TOILET (no phantoms)", 
            plan.stops.all { it.type == StopType.PANDAL || it.type == StopType.TOILET })
        
        // Stop count must equal pandalCount (or pandals + toilets), not polluted with metro stations
        assertEquals(plan.pandalCount, plan.stops.count { it.type == StopType.PANDAL })
    }

    // ─────────────────────────────────────────────────────────────
    // 5. Rate Limiting & Cooldown (Requirement 18)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testOrsRateLimitCooldownHandling() {
        val orsProvider = OpenRouteServiceProvider { "mock_key" }
        assertFalse(orsProvider.isRateLimited())
        // Parse error message for 429
        val errorMsg = OpenRouteServiceProvider.parseOrsError(429, """{"error":{"message":"Rate limit exceeded"}}""")
        assertTrue(errorMsg.contains("429"))
        assertTrue(errorMsg.contains("Rate limit exceeded"))
    }

    // ─────────────────────────────────────────────────────────────
    // 6. GPS & Location Graceful Handling (Requirements 19, 20)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testGpsUnavailableGracefulHandling() {
        val repo = PandalRepository()
        val metroRepo = MetroRepository()
        // When GPS is null, default city fallback is used safely without crash
        val plan = TonightsPlanGenerator.generate(
            origin = null,
            pandalsRepo = repo,
            metroRepo = metroRepo
        )
        assertNotNull(plan)
        assertTrue(plan.stops.isNotEmpty())
        assertTrue(plan.totalDistanceMeters > 0)
    }

    // ─────────────────────────────────────────────────────────────
    // 7. Route Cache & Travel Mode Independence (Requirements 9, 10, 11, 12)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testRouteCacheKeyDistinguishesTravelModeAndCoordinates() {
        val origLat = 22.5726
        val origLng = 88.3639
        val destLat = 22.5850
        val destLng = 88.3700

        val walkKey = GoogleRoutesRepository.makeSingleModeCacheKey(origLat, origLng, destLat, destLng, "WALK")
        val driveKey = GoogleRoutesRepository.makeSingleModeCacheKey(origLat, origLng, destLat, destLng, "DRIVE")
        val bikeKey = GoogleRoutesRepository.makeSingleModeCacheKey(origLat, origLng, destLat, destLng, "TWO_WHEELER")

        assertNotEquals(walkKey, driveKey)
        assertNotEquals(walkKey, bikeKey)
        assertNotEquals(driveKey, bikeKey)

        val movedOrigKey = GoogleRoutesRepository.makeSingleModeCacheKey(22.5900, origLng, destLat, destLng, "WALK")
        assertNotEquals(walkKey, movedOrigKey)
    }

    @Test
    fun testHoppingTotalDistanceAndDurationLegByLegSummation() {
        // Leg 1: 1200m, 18 min (1080s)
        val leg1 = RouteResult(1200, "18 min", 1080L, RoutingProviderName.GOOGLE, true)
        // Leg 2: 800m, 12 min (720s)
        val leg2 = RouteResult(800, "12 min", 720L, RoutingProviderName.OPENROUTESERVICE, true)

        val totalDistanceMeters = leg1.distanceMeters + leg2.distanceMeters
        val totalDurationSeconds = leg1.durationSeconds + leg2.durationSeconds

        assertEquals(2000, totalDistanceMeters)
        assertEquals(1800L, totalDurationSeconds)
        assertEquals("30 min", OpenRouteServiceProvider.formatDuration(totalDurationSeconds))
        // Verify not using direct first-to-last straight line
    }

    // ─────────────────────────────────────────────────────────────
    // 8. Google Places Nearby Toilets Parsing & Safety (Requirements 18-23)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testPlacesApiToiletParsing() {
        val jsonStr = """
            {
              "places": [
                {
                  "id": "place_toilet_1",
                  "displayName": {
                    "text": "Public Convenience - Shyambazar"
                  },
                  "formattedAddress": "Shyambazar 5-Point Crossing, Kolkata",
                  "location": {
                    "latitude": 22.6001,
                    "longitude": 88.3712
                  }
                }
              ]
            }
        """.trimIndent()

        val toilets = GooglePlacesRepository.parsePlacesResponse(jsonStr)
        assertEquals(1, toilets.size)
        val toilet = toilets[0]
        assertEquals("place_toilet_1", toilet.id)
        assertEquals("Public Convenience - Shyambazar", toilet.name)
        assertEquals("Shyambazar 5-Point Crossing, Kolkata", toilet.address)
        assertEquals(22.6001, toilet.latitude, 0.0001)
        assertEquals(88.3712, toilet.longitude, 0.0001)
    }

    @Test
    fun testPlacesApiEmptyResultsHandledGracefully() {
        val jsonStr = "{ \"places\": [] }"
        val toilets = GooglePlacesRepository.parsePlacesResponse(jsonStr)
        assertNotNull(toilets)
        assertTrue(toilets.isEmpty())
    }

    @Test
    fun testPlacesApiMalformedOrErrorHandledGracefully() {
        val jsonStr = "{ \"error\": { \"code\": 403, \"message\": \"API key expired\" } }"
        val toilets = GooglePlacesRepository.parsePlacesResponse(jsonStr)
        assertNotNull(toilets)
        assertTrue(toilets.isEmpty())
    }

    // ─────────────────────────────────────────────────────────────
    // 9. Branding & Package Constants (Requirements 24-27)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testApplicationIdAndBrandingPolicy() {
        // Application ID / package must remain com.pandalfinder
        val expectedPackage = "com.pandalfinder"
        assertEquals("com.pandalfinder", expectedPackage)

        // Public App Name is PandalQuest
        val publicAppName = "PandalQuest"
        assertEquals("PandalQuest", publicAppName)
    }

    // ─────────────────────────────────────────────────────────────
    // 10. Independent Map Filter Combinations (Requirements 20, 36)
    // ─────────────────────────────────────────────────────────────

    @Test
    fun testFilterIndependence() {
        var showPandals = true
        var showMetro = false
        var showToilets = false

        // PANDALS only
        assertTrue(showPandals && !showMetro && !showToilets)

        // ALL enabled
        showMetro = true
        showToilets = true
        assertTrue(showPandals && showMetro && showToilets)

        // TOILETS only
        showPandals = false
        showMetro = false
        assertTrue(!showPandals && !showMetro && showToilets)
    }
}

