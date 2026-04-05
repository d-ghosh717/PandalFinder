package com.pandalfinder

import android.location.Location

data class Pandal(
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val theme: String = "Modern", // Heritage, Modern, Eco
    var distance: Float = 0f
) {
    companion object {
        // Comprehensive list of famous Durga Puja pandals in Kolkata
        fun getSamplePandals(): List<Pandal> {
            return listOf(
                // North Kolkata
                Pandal("Paikpara 31 Pally Sadharan Durgotsab Samity", 22.6125036, 88.3792923),
Pandal("Ahiritola Jubak Brinda", 22.5945991, 88.3603309),
Pandal("Amherst Sarbojanin Durgotsab", 22.5946391, 88.3576964),
Pandal("Bagbazar Polli Durga Pujo", 22.6012079, 88.3668234),
Pandal("Beadon Street Sarbojanin Durgotsav", 22.5892707, 88.3676133),
Pandal("Belgachia Sadharon Durgotsav", 22.6077319, 88.3827393),
Pandal("Baghbazar Sarbojanin Durgotsav & Exhibition", 22.6044897, 88.365615),
Pandal("Tarun Sangha Durga Puja", 22.5121958, 88.3468731),
Pandal("Brindabon Matri Mandir", 22.5827172, 88.3729053),
Pandal("Dakshindari Youth Forum Ground", 22.5953004, 88.39385),
Pandal("Belgachia Sadharon Durgotsav", 22.6077319, 88.3827393),
Pandal("Maniktala Chaltabagan Lohapatty", 22.5848905, 88.3721509),
Pandal("Chaltabagan Loha Patty Durgapuja", 22.5844943, 88.3723008),
Pandal("Chorebagan Sarbojanin Durgotsab Samity", 22.5834284, 88.3635965),
Pandal("37 Palli Durga Puja Pandal", 22.5746858, 88.3693988),
Pandal("Shakti Sangha Club'S Durga Puja Ground", 22.5047514, 88.3879686),
Pandal("Darjipara Sarbojanin Durgotsab", 22.5925365, 88.3680032),
Pandal("Bakul Bagan Sarbojanin Durgotsav", 22.5267621, 88.34826),
Pandal("Dum Dum Park Bharat Chakra", 22.6108213, 88.4146001),
Pandal("Beniatola Sarbojonin Durgotsab", 22.5957292, 88.3611888),
Pandal("Dum Dum Park Sarbojanin Durga Puja Samity", 22.6094363, 88.4164052),
Pandal("Burrabazar Sarbojanin Durgotsab", 22.5864033, 88.3559956),
Pandal("Goabagan Sarodatsav Sammilani", 22.5893555, 88.3717),
Pandal("Golaghata Sammelani Club", 22.597339, 88.3991458),
Pandal("Gauri Bari Sarbojanin Durga Puja Pandal", 22.5945086, 88.3791679),
Pandal("The Hari Ghosh Street Sarbojanin Durgotsab Samity", 22.5904259, 88.3681481),
Pandal("Haritaki Bagan Sarbojonin Durgotsab", 22.5864522, 88.3721911),
Pandal("Hatibagan Sarbojonin", 22.5943863, 88.3720004),
Pandal("Hatibagan Nabinpally Durga Puja Committee", 22.5959028, 88.3734176),
Pandal("Hatibagan Nabinpally Durga Puja", 22.5959523, 88.3735085),
Pandal("Jagat Mukherjee Park", 22.5994903, 88.3660388),
Pandal("Nimtala Sarbojanin Durgapuja", 22.5918418, 88.3586946),
Pandal("Kumartuli Park", 22.5989827, 88.3614663),
Pandal("Hatkhola Gosaipara Sarbojonin Durgotsab", 22.5990953, 88.359304),
Pandal("Kashi Bose Lane Durga Puja Committee", 22.5908979, 88.3689174),
Pandal("Karbagan Sarbojanin Durgotsab Committee", 22.5953601, 88.3838115),
Pandal("Mitali Sangha Durga Puja Pandal", 22.579987, 88.3942125),
Pandal("Surir Bagan Sarbojanin Durgapuja", 22.5947637, 88.3873849),
Pandal("Kashi Bose Lane Durga Puja Committee", 22.5908979, 88.3689174),
Pandal("Kumartuli Park", 22.5989827, 88.3614663),
Pandal("Kumortuli Sarbojonin Durgotsab", 22.6011655, 88.362723),
Pandal("Sovabazar Borotola Sarbojanin Durgotsav Samity", 22.5956019, 88.3643333),
Pandal("Mohun Bagan Barwari", 22.5981732, 88.3737984),
Pandal("Baranagar Netaji Colony Sarbojanin Durga Puja Highland", 22.636647, 88.379064),
Pandal("Nimtala Sarbojanin Durgapuja", 22.5918418, 88.3586946),
Pandal("Tangra Gholpara Durga Puja", 22.5531208, 88.390752),
Pandal("North Tridhara Durga Puja", 22.5958155, 88.3745568),
Pandal("Pathuriaghata Pancher Pally Sarbojonin Durgotsab Committee", 22.5886068, 88.3576317),
Pandal("Lalabagan Nabankur Durga Puja Pandal Entrance Gate", 22.5883732, 88.3767567),
Pandal("Sikdar Bagan Sadharan Durgotsov", 22.596485, 88.37199),
Pandal("Shimla Sporting Club Durga Puja", 22.5847918, 88.3667183),
Pandal("Sree Bhumi Sporting Club", 22.5988987, 88.4029328),
Pandal("Netaji Sporting Club Durga Puja", 22.6083271, 88.4003968),
Pandal("Tala Barowari Durgotsob", 22.6067969, 88.3762168),
Pandal("Tala Prattoy Durga Puja Art", 22.6087911, 88.382502),
Pandal("Tala Dakshin Pally Sadharan Durgatsab", 22.6049683, 88.3762031),
Pandal("Tala Ponero (15) Pally Durga Puja", 22.6124763, 88.3847732),
Pandal("Telengabagan Durgotsab", 22.5949468, 88.3853031),
Pandal("Ultadanga Jagarani Sangha", 22.5932927, 88.3849094),
Pandal("Ultadanga Pallyshree Durga Puja", 22.5958298, 88.3844134),
Pandal("Ultadanga Sangrami Club Durga Pujo", 22.5905068, 88.3952877),
Pandal("Dakshindari Youth Forum Ground", 22.5953004, 88.39385),

                
                // Central Kolkata
                Pandal("14 Pally Udayan Sangha", 22.5573999, 88.3665828),
Pandal("Ahiritola Jubak Brinda", 22.5945991, 88.3603309),
Pandal("Adhibasibrinda - Kanai Dhar Lane", 22.572337, 88.365265),
Pandal("Lake Town Adhibasi Brinda", 22.6044508, 88.4039701),
Pandal("Machua Bazar Sarbojanik Durgapuja Samity", 22.5801779, 88.3584613),
Pandal("Muhammad Ali Park", 22.5771888, 88.3607369),
Pandal("Pallir Yubak Brinda", 22.5761009, 88.3669929),
Pandal("Santosh Mitra Square", 22.5660201, 88.3656532),
Pandal("Wellington Nagarik Kalyan Samity", 22.5645217, 88.3583834),
Pandal("College Square Durga Puja", 22.5745279, 88.3644724),
Pandal("Beliaghata 33 No. Palli Basi Brinda", 22.5687867, 88.3914127),

                
                // South Kolkata
                Pandal("25 Pally Club", 22.5391841, 88.3264454),
Pandal("64 Pally Durga Pujo", 22.5205584, 88.3475801),
Pandal("95 Pally Association", 22.508235, 88.3627602),
Pandal("Abasar Sarbojanin Durgotsab Samity", 22.5278699, 88.3489227),
Pandal("Chakraberia Sarbojanin Durgotsab", 22.5336065, 88.3519214),
Pandal("Agradut Udaya Sangha", 22.5338208, 88.3419442),
Pandal("Rajdanga Naba Uday Sangha", 22.5166272, 88.3902285),
Pandal("Babubagan Club", 22.5080277, 88.3685822),
Pandal("Babubagan Durgotsav", 22.5079875, 88.3685745),
Pandal("Badamtala Ashar Sangha Durgotsab", 22.5179668, 88.3437245),
Pandal("Baghajatin B & C Durga Puja Art", 22.484073, 88.3760797),
Pandal("Baghajatin Tarun Sangha", 22.4843947, 88.3786093),
Pandal("Ballygunge Cultural Association Durga Puja", 22.5162122, 88.3558059),
Pandal("Joyrampur Sammilita Sarbojanin Durgotsab Committee, Behala", 22.4892136, 88.3065091),
Pandal("Beniatola Sarbojonin Durgotsab", 22.5957292, 88.3611888),
Pandal("Railpukur United Club", 22.6124668, 88.4274064),
Pandal("Bhowanipur Sarbojanin Durgotsav Durga Puja", 22.5349921, 88.3438382),
Pandal("Bhowanipur 75 Palli", 22.533319, 88.3457086),
Pandal("Bhawanipur Muktadal", 22.5290861, 88.3447273),
Pandal("Bhowanipur Swadhin Sangha Durga Puja", 22.5319324, 88.346402),
Pandal("Subhashpally Sarbojonin Durga Puja Pandal", 22.4597639, 88.3756505),
Pandal("Bosepukur Sitala Mandir", 22.5192181, 88.3848576),
Pandal("Chetla Agrani Club", 22.5163997, 88.3368366),
Pandal("Chetla Agrani Durga Puja Pandal", 22.5163878, 88.337111),
Pandal("Deshapriya Park", 22.5185819, 88.3534607),
Pandal("Shraddhanjali Apartment", 22.5186059, 88.3556078),
Pandal("Ekdalia Evergreen Club", 22.5212187, 88.3665629),
Pandal("Hindusthan Park Sarbojanin Durgotsab Committee", 22.5176792, 88.3620716),
Pandal("Mudiali Club", 22.510194, 88.3463122),
Pandal("Shakti Sangha Club'S Durga Puja Ground", 22.5047514, 88.3879686),
Pandal("Ketopole Sammilani Club", 22.486539, 88.2804117),
Pandal("Tridhara Sammilani", 22.5195286, 88.3554398),
Pandal("Behala Kheyali Sangha Park", 22.4976293, 88.3236705),
Pandal("Khidderpore Pally Saradiya", 22.5432531, 88.3215038),
Pandal("75 Pally Khidderpore", 22.5402695, 88.3205241),
Pandal("Khidirpur 25 Palli Durga Puja", 22.5391961, 88.3264565),
Pandal("Khidderpore Pally Saradiya", 22.5432531, 88.3215038),
Pandal("Shibmandir Sarbojanin Durgotsab Samiti", 22.5109975, 88.3498496),
Pandal("Naktala Udayan Sangha", 22.4742929, 88.3665898),
Pandal("Suruchi Sangha", 22.5089855, 88.3339522),
Pandal("Pally Mangal Samity", 22.5049158, 88.3744596),
Pandal("Barisha Sarbojonin", 22.4798533, 88.3081233),
Pandal("Unnayani Sangha", 22.4828461, 88.3435904),
Pandal("Pragati Sangha", 22.367178, 88.4319216),
Pandal("Santoshpur Avenue South Pallymangal Samity", 22.4921878, 88.3798265),
Pandal("Santoshpur Lake Pally", 22.4917563, 88.3829267),
Pandal("Santoshpur Trikon Park Durgotsab", 22.4931559, 88.3784752),
Pandal("Singhi Park Sarbojanin Durga Puja Committee", 22.5209489, 88.3627281),
Pandal("Udayan Kidderpore", 22.5441606, 88.32142),
Pandal("Surir Bagan Sarbojanin Durgapuja", 22.5947637, 88.3873849),
Pandal("Hindusthan Park Sarbojanin Durgotsab Committee", 22.5176792, 88.3620716),
                
                // Salt Lake & East
                Pandal("Beliaghata 33 Pally", 22.5685686, 88.3911284),
Pandal("Bakul Bagan Sarbojanin Durgotsav", 22.5267621, 88.34826),
Pandal("Singhi Park Sarbojanin Durga Puja Committee", 22.5209489, 88.3627281),
Pandal("Netaji Sporting Club Durga Puja", 22.6083271, 88.4003968),
Pandal("Karbagan Sarbojanin Durgotsab Committee", 22.5953601, 88.3838115),
Pandal("64 Pally Durga Pujo", 22.5205584, 88.3475801),
Pandal("Salt Lake Fd Block Durga Puja Pandal", 22.5834541, 88.4122701),
Pandal("Gd Block Durga Puja", 22.5770251, 88.4142505),
Pandal("Ae (Part-2) Durga Puja", 22.6010701, 88.4134563),
Pandal("Labony Estate Durga Puja", 22.5832131, 88.4067466),
Pandal("Durga Pujo, Fe Block", 22.5799308, 88.4148587),
Pandal("Bj Block Durga Puja", 22.5906892, 88.4265676),
Pandal("Cf Block Residents' Association Durga Puja Pandal", 22.5940823, 88.4180967),
Pandal("Ec Block Durga Puja", 22.5850415, 88.4084791),
Pandal("Aj Block Puja Pandal", 22.5921299, 88.4283486),
Pandal("Ah Block Durga Puja Ground", 22.5966207, 88.4258639),

                
                Pandal("New Town Durga Puja Committee, Bg Block", 22.5822737, 88.458849),
Pandal("66 Pally Durgapuja Pandal", 22.5180613, 88.3427526),
Pandal("Aurobindo Setu Sarbojanin Durgapuja Samiti", 22.5944825, 88.3805509),
Pandal("Rajdanga Naba Uday Sangha", 22.5166272, 88.3902285),
Pandal("Behala Nutan Dal", 22.5002078, 88.320287),
Pandal("Behala Nutan Sangha Puja Pandal", 22.5019877, 88.3193392),
Pandal("Buroshibtala Durga Puja", 22.5041504, 88.330462),
Pandal("Behala Player'S Corner Durga Puja", 22.4858476, 88.313717),
Pandal("Behala Club Sarbojanin Durgotsav Committee", 22.5052597, 88.3140807),
Pandal("Behala Sree Sangha", 22.4979172, 88.3201499),
Pandal("Behala Natun Sangha", 22.5016594, 88.3194304),
Pandal("Barisha Club Puja Ground And Community Hall", 22.4812859, 88.3132412),
Pandal("Sri Sangha Durga Puja Area", 22.498415, 88.3204122),
Pandal("Golf Green Phase Ii Durga Puja Pandal", 22.4948663, 88.3630877),
Pandal("Jadavpur Athletic Club", 22.4889159, 88.3716305),
Pandal("Bijoygarh 6 Pally Sarbojanin Shyama Puja Committee", 22.485933, 88.360159),

                
                // Howrah Side
                Pandal("Howrah Station Area", 22.5833, 88.3412),
                Pandal("Shibpur Mitra Sangha", 22.5672, 88.3356)
            )
        }
    }
    
    // Calculate distance from a given location
    fun calculateDistanceFrom(userLocation: Location) {
        val pandalLocation = Location("").apply {
            latitude = this@Pandal.latitude
            longitude = this@Pandal.longitude
        }
        // Calculate aerial distance and multiply by 1.4 to estimate driving distance
        val aerialDistance = userLocation.distanceTo(pandalLocation) / 1000
        distance = aerialDistance * 1.4f  // Estimate driving distance (aerial × 1.4)
    }
}
