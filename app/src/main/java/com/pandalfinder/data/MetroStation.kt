package com.pandalfinder.data

/**
 * Kolkata Metro station with geographic coordinates.
 * Dataset covers operational stations on the Blue Line (Line 1: Dakshineswar–Kavi Subhash)
 * and Green Line (Line 2: Howrah Maidan–Sector V), plus the partial Purple Line (Line 3).
 */
data class MetroStation(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val line: String
) {
    companion object {
        fun allStations(): List<MetroStation> = listOf(
            // ── Blue Line (Line 1): Dakshineswar → Kavi Subhash ──
            MetroStation("Dakshineswar", 22.6553, 88.3576, "Blue Line"),
            MetroStation("Baranagar", 22.6394, 88.3755, "Blue Line"),
            MetroStation("Noapara", 22.6296, 88.3773, "Blue Line"),
            MetroStation("Dum Dum", 22.6222, 88.4062, "Blue Line"),
            MetroStation("Belgachia", 22.6097, 88.3840, "Blue Line"),
            MetroStation("Shyambazar", 22.5992, 88.3716, "Blue Line"),
            MetroStation("Shobhabazar Sutanuti", 22.5937, 88.3652, "Blue Line"),
            MetroStation("Girish Park", 22.5857, 88.3646, "Blue Line"),
            MetroStation("Mahatma Gandhi Road", 22.5791, 88.3610, "Blue Line"),
            MetroStation("Central", 22.5721, 88.3590, "Blue Line"),
            MetroStation("Chandni Chowk", 22.5666, 88.3549, "Blue Line"),
            MetroStation("Esplanade", 22.5628, 88.3516, "Blue Line"),
            MetroStation("Park Street", 22.5545, 88.3515, "Blue Line"),
            MetroStation("Maidan", 22.5474, 88.3477, "Blue Line"),
            MetroStation("Rabindra Sadan", 22.5393, 88.3449, "Blue Line"),
            MetroStation("Netaji Bhavan", 22.5334, 88.3440, "Blue Line"),
            MetroStation("Jatin Das Park", 22.5259, 88.3417, "Blue Line"),
            MetroStation("Kalighat", 22.5194, 88.3417, "Blue Line"),
            MetroStation("Rabindra Sarobar", 22.5087, 88.3466, "Blue Line"),
            MetroStation("Masterda Surya Sen", 22.5014, 88.3501, "Blue Line"),
            MetroStation("Netaji", 22.4940, 88.3561, "Blue Line"),
            MetroStation("Kavi Subhash", 22.4805, 88.3638, "Blue Line"),

            // ── Green Line (Line 2): Howrah Maidan → Sector V ──
            MetroStation("Howrah Maidan", 22.5844, 88.3383, "Green Line"),
            MetroStation("Howrah", 22.5846, 88.3427, "Green Line"),
            MetroStation("Mahakaran", 22.5721, 88.3484, "Green Line"),
            MetroStation("Sealdah", 22.5691, 88.3713, "Green Line"),
            MetroStation("Phool Bagan", 22.5715, 88.3848, "Green Line"),
            MetroStation("Bengal Chemical", 22.5726, 88.3945, "Green Line"),
            MetroStation("Salt Lake Stadium", 22.5732, 88.4048, "Green Line"),
            MetroStation("Karunamoyee", 22.5738, 88.4143, "Green Line"),
            MetroStation("Central Park", 22.5762, 88.4227, "Green Line"),
            MetroStation("City Centre", 22.5770, 88.4310, "Green Line"),
            MetroStation("Sector V", 22.5770, 88.4416, "Green Line"),

            // ── Purple Line (Line 3): Joka → Taratala (operational segment) ──
            MetroStation("Joka", 22.4539, 88.3137, "Purple Line"),
            MetroStation("Thakurpukur", 22.4620, 88.3175, "Purple Line"),
            MetroStation("Sakher Bazar", 22.4685, 88.3204, "Purple Line"),
            MetroStation("Behala Chowrasta", 22.4760, 88.3245, "Purple Line"),
            MetroStation("Behala Bazar", 22.4830, 88.3265, "Purple Line"),
            MetroStation("Taratala", 22.4963, 88.3302, "Purple Line"),
            MetroStation("Majerhat", 22.5142, 88.3324, "Purple Line")
        )
    }
}
