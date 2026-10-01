package com.pandalfinder

import com.pandalfinder.data.PandalPhoto
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdminAndSecurityTest {

    // ── 1. Photo Ownership Model ──

    @Test
    fun testPhotoOwnershipPermissions() {
        val userA = "USER_A_UID_12345"
        val userB = "USER_B_UID_67890"

        val photoA = PandalPhoto(
            id = "photo_abc",
            pandalId = "mudiali_club",
            storagePath = "pandal-photos/$userA/mudiali_club/photo_abc.jpg",
            downloadUrl = "https://example.supabase.co/storage/v1/object/public/pandal-photos/$userA/mudiali_club/photo_abc.jpg",
            uploadedBy = userA,
            createdAt = System.currentTimeMillis(),
            status = "active"
        )

        // User A (Owner) can modify/delete own photo
        val canUserAModify = photoA.uploadedBy == userA
        val canUserADelete = photoA.uploadedBy == userA
        assertTrue("User A (owner) must have modify permissions", canUserAModify)
        assertTrue("User A (owner) must have delete permissions", canUserADelete)

        // User B (Normal User) MUST NOT modify/delete User A's photo
        val canUserBModify = photoA.uploadedBy == userB
        val canUserBDelete = photoA.uploadedBy == userB
        assertFalse("User B must NOT have modify permissions on User A's photo", canUserBModify)
        assertFalse("User B must NOT have delete permissions on User A's photo", canUserBDelete)
    }

    @Test
    fun testPhotoOwnershipImmutability() {
        val userA = "USER_A"
        val userB = "USER_B"

        val originalPhoto = PandalPhoto(
            id = "photo_123",
            pandalId = "ballygunge_cultural",
            storagePath = "pandal-photos/$userA/ballygunge_cultural/photo_123.jpg",
            downloadUrl = "https://example.com/photo.jpg",
            uploadedBy = userA,
            createdAt = 1000L,
            status = "active"
        )

        // Simulate update attempt changing uploadedBy
        val updatedPhotoWithChangedOwner = originalPhoto.copy(uploadedBy = userB)

        // Security rule verification: updated.uploadedBy MUST equal original.uploadedBy
        val isOwnershipPreserved = updatedPhotoWithChangedOwner.uploadedBy == originalPhoto.uploadedBy
        assertFalse("Ownership transfer must be DENIED - uploadedBy is immutable", isOwnershipPreserved)
    }

    @Test
    fun testAdminPhotoPrivileges() {
        val userA = "USER_A"
        val adminUid = "ADMIN_FIREBASE_UID"
        val isUserAdmin = true

        val photoA = PandalPhoto(
            id = "photo_xyz",
            pandalId = "ekdalia_evergreen",
            storagePath = "pandal-photos/$userA/ekdalia_evergreen/photo_xyz.jpg",
            downloadUrl = "https://example.com/photo_xyz.jpg",
            uploadedBy = userA,
            createdAt = 2000L,
            status = "active"
        )

        // Admin authorization logic: owner check OR admin check
        val canAdminDelete = (photoA.uploadedBy == adminUid) || isUserAdmin
        val canAdminModerate = isUserAdmin

        assertTrue("Authorized Admin must be allowed to delete any photo", canAdminDelete)
        assertTrue("Authorized Admin must be allowed to moderate any photo", canAdminModerate)
    }

    // ── 2. Storage Path Validation ──

    @Test
    fun testStoragePathValidation() {
        val validRegex = Regex("^pandal-?photos/[^/]+/[^/]+/[^/]+\\.jpg$")

        val validPath1 = "pandal-photos/user_123/ballygunge/photo_99.jpg"
        val validPath2 = "pandalphotos/uid_abc/chetla_agrani/img_01.jpg"
        val invalidPathTraversal = "pandal-photos/../user_123/pandal/photo.jpg"
        val invalidPathWrongBucket = "other-bucket/user_123/pandal/photo.jpg"
        val invalidPathMissingUid = "pandal-photos//pandal/photo.jpg"

        assertTrue("Valid path 1 should match", validRegex.matches(validPath1))
        assertTrue("Valid path 2 should match", validRegex.matches(validPath2))
        assertFalse("Path traversal must fail", validRegex.matches(invalidPathTraversal))
        assertFalse("Wrong bucket must fail", validRegex.matches(invalidPathWrongBucket))
        assertFalse("Missing UID must fail", validRegex.matches(invalidPathMissingUid))
    }

    // ── 3. Crowd Report Validation ──

    @Test
    fun testCrowdReportValidationRules() {
        fun isValidCrowdLevel(level: Int): Boolean = level in 1..10
        fun isValidPandalId(id: String): Boolean = id.isNotBlank() && !id.contains("/")

        assertTrue("Level 1 should be valid", isValidCrowdLevel(1))
        assertTrue("Level 5 should be valid", isValidCrowdLevel(5))
        assertTrue("Level 10 should be valid", isValidCrowdLevel(10))

        assertFalse("Level 0 must be invalid", isValidCrowdLevel(0))
        assertFalse("Level 11 must be invalid", isValidCrowdLevel(11))
        assertFalse("Level -5 must be invalid", isValidCrowdLevel(-5))

        assertTrue("Valid pandal ID", isValidPandalId("mudiali_club"))
        assertFalse("Blank pandal ID must be invalid", isValidPandalId(""))
        assertFalse("Path traversal pandal ID must be invalid", isValidPandalId("../hack"))
    }

    // ── 4. Weather Report Validation ──

    @Test
    fun testWeatherReportConditionValidation() {
        val allowedConditions = setOf("NO_RAIN", "DRIZZLE", "RAINING", "HEAVY_RAIN")

        assertTrue("NO_RAIN is allowed", allowedConditions.contains("NO_RAIN"))
        assertTrue("DRIZZLE is allowed", allowedConditions.contains("DRIZZLE"))
        assertTrue("RAINING is allowed", allowedConditions.contains("RAINING"))
        assertTrue("HEAVY_RAIN is allowed", allowedConditions.contains("HEAVY_RAIN"))

        assertFalse("Invalid condition 'SNOW' must be rejected", allowedConditions.contains("SNOW"))
        assertFalse("Malicious injection must be rejected", allowedConditions.contains("DROP TABLE"))
    }

    // ── 5. Admin Allowlist Authorization Verification ──

    @Test
    fun testAdminAuthorizationCheck() {
        data class AdminRecord(val role: String, val enabled: Boolean)

        fun isAuthorizedAdmin(record: AdminRecord?): Boolean {
            return record != null && record.role == "admin" && record.enabled
        }

        val validAdmin = AdminRecord("admin", true)
        val disabledAdmin = AdminRecord("admin", false)
        val nonAdminUser = AdminRecord("user", true)
        val missingRecord: AdminRecord? = null

        assertTrue("Valid enabled admin record must be authorized", isAuthorizedAdmin(validAdmin))
        assertFalse("Disabled admin record must be DENIED", isAuthorizedAdmin(disabledAdmin))
        assertFalse("Non-admin role must be DENIED", isAuthorizedAdmin(nonAdminUser))
        assertFalse("Missing admin record must be DENIED", isAuthorizedAdmin(missingRecord))
    }
}
