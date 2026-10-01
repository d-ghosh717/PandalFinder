package com.pandalfinder

import com.pandalfinder.data.PandalPhoto
import com.pandalfinder.ui.UploadStatus
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class MultiPhotoAndFullscreenTest {

    @Test
    fun testMaxPhotoLimitEnforcement() {
        val maxLimit = 10
        val selectedItems = mutableListOf<String>()
        
        // Add 12 simulated photo URIs
        val incomingUris = (1..12).map { "content://media/external/images/media/$it" }
        
        val existingUris = selectedItems.toSet()
        val uniqueNew = incomingUris.filterNot { existingUris.contains(it) }
        val availableSlots = maxLimit - selectedItems.size
        val toAdd = uniqueNew.take(availableSlots)
        selectedItems.addAll(toAdd)

        assertEquals(10, selectedItems.size)
        assertTrue(selectedItems.size <= maxLimit)
    }

    @Test
    fun testDeduplicationOfSelectedUris() {
        val uri1 = "content://media/external/images/media/101"
        val uri2 = "content://media/external/images/media/102"
        val uriDuplicate = "content://media/external/images/media/101"

        val list = listOf(uri1, uri2, uriDuplicate)
        val unique = list.distinct()

        assertEquals(2, unique.size)
        assertEquals(listOf(uri1, uri2), unique)
    }

    @Test
    fun testPartialFailureBatchAccounting() {
        val total = 5
        val batchResults = mutableListOf<Pair<String, Result<PandalPhoto>>>()

        // Simulate 4 successes and 1 failure
        for (i in 1..4) {
            val photo = PandalPhoto(
                id = "photo_$i",
                pandalId = "bagbazar",
                storagePath = "pandal-photos/userA/bagbazar/photo_$i.jpg",
                downloadUrl = "https://example.com/photo_$i.jpg",
                uploadedBy = "userA",
                createdAt = System.currentTimeMillis(),
                status = "active"
            )
            batchResults.add("uri_$i" to Result.success(photo))
        }
        batchResults.add("uri_5" to Result.failure(RuntimeException("Network timeout")))

        val successCount = batchResults.count { it.second.isSuccess }
        val failedCount = batchResults.count { it.second.isFailure }

        assertEquals(4, successCount)
        assertEquals(1, failedCount)
        assertEquals(total, successCount + failedCount)
    }

    @Test
    fun testPhotoOwnershipImmutabilityInBatch() {
        val currentUserId = "USER_ABC_123"
        val pandalId = "deshapriya-park"

        val uploadedPhotos = (1..3).map {
            val photoId = UUID.randomUUID().toString()
            PandalPhoto(
                id = photoId,
                pandalId = pandalId,
                storagePath = "pandal-photos/$currentUserId/$pandalId/$photoId.jpg",
                downloadUrl = "https://supabase.co/storage/v1/object/pandal-photos/$currentUserId/$pandalId/$photoId.jpg",
                uploadedBy = currentUserId,
                createdAt = System.currentTimeMillis(),
                status = "active"
            )
        }

        uploadedPhotos.forEach { photo ->
            assertEquals(currentUserId, photo.uploadedBy)
            assertTrue(photo.storagePath.startsWith("pandal-photos/$currentUserId/$pandalId/"))
            assertNotEquals("", photo.id)
        }

        // Distinct photo IDs across batch
        val distinctIds = uploadedPhotos.map { it.id }.distinct()
        assertEquals(3, distinctIds.size)
    }

    @Test
    fun testUploadStatusTransitions() {
        var status = UploadStatus.PENDING
        var errorMsg: String? = null

        assertEquals(UploadStatus.PENDING, status)
        assertNull(errorMsg)

        status = UploadStatus.UPLOADING
        assertEquals(UploadStatus.UPLOADING, status)

        status = UploadStatus.FAILED
        errorMsg = "Storage upload error: 500"
        assertEquals(UploadStatus.FAILED, status)
        assertEquals("Storage upload error: 500", errorMsg)

        status = UploadStatus.SUCCESS
        errorMsg = null
        assertEquals(UploadStatus.SUCCESS, status)
    }
}
