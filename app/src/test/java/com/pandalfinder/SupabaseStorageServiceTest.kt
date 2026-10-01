package com.pandalfinder

import com.pandalfinder.data.supabase.SupabaseStorageService
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SupabaseStorageServiceTest {

    @Test
    fun testPublicUrlConstruction() {
        val service = SupabaseStorageService(
            supabaseUrl = "https://ittdzzuzgkavkeiookin.supabase.co",
            supabasePublishableKey = "sb_publishable_test_key"
        )

        val url = service.getPublicUrl(
            firebaseUid = "uid_12345",
            pandalId = "mudiali_club",
            photoId = "photo_abc"
        )

        assertEquals(
            "https://ittdzzuzgkavkeiookin.supabase.co/storage/v1/object/public/pandal-photos/uid_12345/mudiali_club/photo_abc.jpg",
            url
        )
    }

    @Test
    fun testPublicUrlFromStoragePath() {
        val service = SupabaseStorageService(
            supabaseUrl = "https://ittdzzuzgkavkeiookin.supabase.co",
            supabasePublishableKey = "sb_publishable_test_key"
        )

        val fullPath = "pandal-photos/user_99/ballygunge/xyz.jpg"
        val url = service.getPublicUrlFromPath(fullPath)

        assertEquals(
            "https://ittdzzuzgkavkeiookin.supabase.co/storage/v1/object/public/pandal-photos/user_99/ballygunge/xyz.jpg",
            url
        )
    }

    @Test
    fun testConfigurationStatus() {
        val configuredService = SupabaseStorageService(
            supabaseUrl = "https://ittdzzuzgkavkeiookin.supabase.co",
            supabasePublishableKey = "sb_publishable_3jZnGgxDoeDBmcDtlDlcow_6hBqnqjV"
        )
        assertTrue(configuredService.isConfigured)
    }
}
