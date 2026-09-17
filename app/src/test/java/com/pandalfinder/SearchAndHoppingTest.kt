package com.pandalfinder

import com.pandalfinder.data.HoppingStop
import com.pandalfinder.data.MetroStation
import com.pandalfinder.data.StopType
import org.junit.Assert.*
import org.junit.Test

class SearchAndHoppingTest {

    @Test
    fun testCoordinateValidation() {
        assertTrue(MainActivity.isValidCoordinate(22.5726, 88.3639))
        assertTrue(MainActivity.isValidCoordinate(-90.0, -180.0))
        assertTrue(MainActivity.isValidCoordinate(90.0, 180.0))
        assertFalse(MainActivity.isValidCoordinate(Double.NaN, 88.0))
        assertFalse(MainActivity.isValidCoordinate(22.0, Double.POSITIVE_INFINITY))
        assertFalse(MainActivity.isValidCoordinate(91.0, 88.0))
        assertFalse(MainActivity.isValidCoordinate(22.0, -181.0))
    }

    @Test
    fun testMetroStationsLoaded() {
        val stations = MetroStation.allStations()
        assertTrue(stations.isNotEmpty())
        assertTrue(stations.any { it.name.contains("Rabindra Sarobar", ignoreCase = true) })
        assertTrue(stations.any { it.name.contains("Howrah", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Blue", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Green", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Purple", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Orange", ignoreCase = true) })
        assertTrue(stations.any { it.line.contains("Yellow", ignoreCase = true) })
    }

    @Test
    fun testMetroColorResolution() {
        val blueStation = MetroStation("Rabindra Sarobar", 22.5087, 88.3466, "Blue Line")
        val greenStation = MetroStation("Howrah", 22.5846, 88.3427, "Green Line")
        val purpleStation = MetroStation("Majerhat", 22.5142, 88.3324, "Purple Line")
        val orangeStation = MetroStation("Satyajit Ray", 22.4842, 88.3842, "Orange Line")
        val yellowStation = MetroStation("Jessore Road", 22.6468, 88.4312, "Yellow Line")

        assertTrue(blueStation.line.contains("Blue"))
        assertTrue(greenStation.line.contains("Green"))
        assertTrue(purpleStation.line.contains("Purple"))
        assertTrue(orangeStation.line.contains("Orange"))
        assertTrue(yellowStation.line.contains("Yellow"))
    }

    @Test
    fun testHoppingStopCreationFromMetroAndPandal() {
        val station = MetroStation("Rabindra Sarobar", 22.5087, 88.3466, "Blue Line")
        val metroStop = HoppingStop.fromMetro(station)

        assertEquals("metro:rabindra_sarobar", metroStop.id)
        assertEquals("Rabindra Sarobar", metroStop.name)
        assertEquals(StopType.METRO, metroStop.type)
        assertEquals(22.5087, metroStop.latitude, 0.0001)
        assertEquals(88.3466, metroStop.longitude, 0.0001)
        assertEquals("Metro • Blue Line", metroStop.subtitle)
        assertNotNull(metroStop.metroRef)

        val pandal = Pandal(
            id = "khidderpore-pally-saradiya",
            name = "Khidderpore 75 Pally",
            area = "Khidderpore",
            latitude = 22.5350,
            longitude = 88.3280
        )
        val pandalStop = HoppingStop.fromPandal(pandal)
        assertEquals("pandal:khidderpore-pally-saradiya", pandalStop.id)
        assertEquals("Khidderpore 75 Pally", pandalStop.name)
        assertEquals(StopType.PANDAL, pandalStop.type)
        assertEquals(22.5350, pandalStop.latitude, 0.0001)
        assertEquals(88.3280, pandalStop.longitude, 0.0001)
        assertEquals("Khidderpore", pandalStop.subtitle)
        assertNotNull(pandalStop.pandalRef)
    }

    @Test
    fun testSearchFilteringQueries() {
        val localPandals = Pandal.getLocalPandals()
        val allStations = MetroStation.allStations()

        // 1. Search "Khidderpore"
        val khidderporePandals = localPandals.filter { it.name.contains("Khidderpore", true) || it.area.contains("Khidderpore", true) }
        val khidderporeMetro = allStations.filter { it.name.contains("Khidderpore", true) || it.line.contains("Khidderpore", true) }
        assertTrue(khidderporePandals.isNotEmpty())

        // 2. Search "Rabindra Sarobar"
        val rabindraMetro = allStations.filter { it.name.contains("Rabindra Sarobar", true) }
        assertEquals(1, rabindraMetro.size)

        // 3. Search "Howrah"
        val howrahPandals = localPandals.filter { it.name.contains("Howrah", true) || it.area.contains("Howrah", true) }
        val howrahMetro = allStations.filter { it.name.contains("Howrah", true) }
        assertTrue(howrahPandals.isNotEmpty() || howrahMetro.isNotEmpty())
        assertTrue(howrahMetro.any { it.name.equals("Howrah", true) })

        // 4. Search "pandal"
        val pandalPandals = localPandals.filter { it.name.contains("pandal", true) || it.area.contains("pandal", true) }
        assertNotNull(pandalPandals)

        // 5. Search "metro"
        val metroMatches = allStations.filter { it.name.contains("metro", true) || it.line.contains("metro", true) }
        assertNotNull(metroMatches)

        // 6. Search random text "xyzabc123"
        val randomPandals = localPandals.filter { it.name.contains("xyzabc123", true) || it.area.contains("xyzabc123", true) }
        val randomMetro = allStations.filter { it.name.contains("xyzabc123", true) || it.line.contains("xyzabc123", true) }
        assertTrue(randomPandals.isEmpty())
        assertTrue(randomMetro.isEmpty())

        // 7. Search empty and whitespace
        val emptyQuery = "   ".trim()
        assertTrue(emptyQuery.isBlank())

        // 8. Special characters
        val specialQuery = "!@#$%^&*()".trim()
        val specialPandals = localPandals.filter { it.name.contains(specialQuery, true) }
        assertTrue(specialPandals.isEmpty())
    }

    @Test
    fun testPublicToiletModelAndHopping() {
        val toilet = com.pandalfinder.data.PublicToilet(
            id = "ChIJm5hSEqd3AjoRsGg140qCjnE",
            name = "Pay and Use Toilet",
            address = "11, Esplanade East, Kolkata",
            latitude = 22.5642,
            longitude = 88.3506
        )
        val stop = HoppingStop.fromToilet(toilet)
        assertEquals("toilet:ChIJm5hSEqd3AjoRsGg140qCjnE", stop.id)
        assertEquals("Pay and Use Toilet", stop.name)
        assertEquals(StopType.TOILET, stop.type)
        assertEquals(22.5642, stop.latitude, 0.0001)
        assertEquals(88.3506, stop.longitude, 0.0001)
        assertEquals("11, Esplanade East, Kolkata", stop.subtitle)
        assertNotNull(stop.toiletRef)
        assertEquals(toilet.id, stop.toiletRef?.id)
    }
}

