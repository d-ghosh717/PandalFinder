package com.pandalfinder.data

import android.util.Log
import com.google.firebase.firestore.FirebaseFirestore

data class UserContributions(
    val photosCount: Int = 0,
    val crowdReportsCount: Int = 0
) {
    val total: Int get() = photosCount + crowdReportsCount
}

class ContributionsRepository {
    private val firestore: FirebaseFirestore
        get() = FirebaseFirestore.getInstance()

    /**
     * Queries Firestore collections for actual community contributions submitted by the given UID.
     * Only counts Photos and Crowd Reports.
     */
    fun fetchUserContributions(uid: String, onResult: (UserContributions) -> Unit) {
        if (uid.isBlank()) {
            onResult(UserContributions(0, 0))
            return
        }

        var photos = 0
        var crowd = 0
        var completed = 0

        fun checkDone() {
            completed++
            if (completed == 2) {
                onResult(UserContributions(photos, crowd))
            }
        }

        firestore.collection(PhotoRepository.COLLECTION)
            .whereEqualTo("uploadedBy", uid)
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { snap ->
                photos = snap.size()
                checkDone()
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Failed to query user photos: ${err.message}", err)
                checkDone()
            }

        firestore.collection(CrowdRepository.COLLECTION)
            .whereEqualTo("userId", uid)
            .get()
            .addOnSuccessListener { snap ->
                crowd = snap.size()
                checkDone()
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Failed to query user crowd reports: ${err.message}", err)
                checkDone()
            }
    }

    /**
     * Fetches all active photos uploaded specifically by the current user UID (newest first).
     */
    fun fetchUserPhotos(uid: String, onResult: (List<PandalPhoto>) -> Unit) {
        if (uid.isBlank()) {
            onResult(emptyList())
            return
        }

        firestore.collection(PhotoRepository.COLLECTION)
            .whereEqualTo("uploadedBy", uid)
            .whereEqualTo("status", "active")
            .get()
            .addOnSuccessListener { snap ->
                val photos = snap.documents.mapNotNull { doc ->
                    val id = doc.getString("id") ?: doc.id
                    val pId = doc.getString("pandalId") ?: ""
                    val path = doc.getString("storagePath") ?: ""
                    val url = doc.getString("downloadUrl") ?: ""
                    val uploader = doc.getString("uploadedBy") ?: ""
                    val time = doc.getLong("createdAt") ?: 0L
                    val status = doc.getString("status") ?: "active"
                    if (url.isNotBlank() && status == "active") {
                        PandalPhoto(id, pId, path, url, uploader, time, status)
                    } else null
                }.sortedByDescending { it.createdAt }
                onResult(photos)
            }
            .addOnFailureListener { err ->
                Log.e(TAG, "Failed to query user photo list: ${err.message}", err)
                onResult(emptyList())
            }
    }

    companion object {
        private const val TAG = "ContributionsRepo"
    }
}
