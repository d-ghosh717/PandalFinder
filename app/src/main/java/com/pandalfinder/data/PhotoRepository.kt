package com.pandalfinder.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.util.Log
import androidx.exifinterface.media.ExifInterface
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import com.google.firebase.storage.FirebaseStorage
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.UUID

data class PandalPhoto(
    val id: String = "",
    val pandalId: String = "",
    val storagePath: String = "",
    val downloadUrl: String = "",
    val uploadedBy: String = "",
    val createdAt: Long = 0L,
    val status: String = "active"
)

class PhotoRepository {
    private val firestore get() = FirebaseFirestore.getInstance()
    private val storage get() = FirebaseStorage.getInstance()
    private val auth get() = FirebaseAuth.getInstance()

    fun currentUserId(onReady: (String) -> Unit) {
        val current = auth.currentUser
        if (current != null) {
            onReady(current.uid)
        } else {
            auth.signInAnonymously()
                .addOnSuccessListener { result ->
                    val uid = result.user?.uid ?: "anonymous_${UUID.randomUUID()}"
                    onReady(uid)
                }
                .addOnFailureListener {
                    // Fallback to local stable installation ID
                    onReady("device_user_${UUID.randomUUID().toString().take(8)}")
                }
        }
    }

    /**
     * Listens in real-time to active photos for a given pandal.
     * Ordered by newest first.
     */
    fun listenToPhotos(pandalId: String, onUpdate: (List<PandalPhoto>) -> Unit): ListenerRegistration? {
        val cleanId = pandalId.removePrefix("pandal:")
        return runCatching {
            firestore.collection(COLLECTION)
                .whereEqualTo("pandalId", cleanId)
                .whereEqualTo("status", "active")
                .addSnapshotListener { snapshot, error ->
                    if (error != null) {
                        Log.e(TAG, "Photos listener failed for pandal $cleanId: ${error.message}")
                        onUpdate(emptyList())
                        return@addSnapshotListener
                    }
                    if (snapshot != null) {
                        val photos = snapshot.documents.mapNotNull { doc ->
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
                        onUpdate(photos)
                    }
                }
        }.getOrNull()
    }

    /**
     * Compresses, uploads to Firebase Storage, and writes metadata to Firestore.
     */
    fun uploadPhoto(
        context: Context,
        pandalId: String,
        imageUri: Uri,
        onProgress: (Int) -> Unit,
        onComplete: (Result<PandalPhoto>) -> Unit
    ) {
        val cleanId = pandalId.removePrefix("pandal:")
        currentUserId { uid ->
            val compressedBytes = compressImage(context, imageUri)
            if (compressedBytes == null) {
                onComplete(Result.failure(IllegalArgumentException("Failed to decode or compress selected image")))
                return@currentUserId
            }

            val photoId = UUID.randomUUID().toString()
            val storagePath = "pandalPhotos/$cleanId/$photoId.jpg"
            val photoRef = storage.reference.child(storagePath)

            val uploadTask = photoRef.putBytes(compressedBytes)
            uploadTask.addOnProgressListener { taskSnapshot ->
                val total = taskSnapshot.totalByteCount
                if (total > 0) {
                    val progress = ((taskSnapshot.bytesTransferred.toDouble() / total) * 100).toInt()
                    onProgress(progress.coerceIn(0, 99))
                }
            }.addOnSuccessListener {
                photoRef.downloadUrl.addOnSuccessListener { uri ->
                    val downloadUrl = uri.toString()
                    val timestamp = System.currentTimeMillis()
                    val photo = PandalPhoto(
                        id = photoId,
                        pandalId = cleanId,
                        storagePath = storagePath,
                        downloadUrl = downloadUrl,
                        uploadedBy = uid,
                        createdAt = timestamp,
                        status = "active"
                    )

                    val docData = hashMapOf<String, Any>(
                        "id" to photo.id,
                        "pandalId" to photo.pandalId,
                        "storagePath" to photo.storagePath,
                        "downloadUrl" to photo.downloadUrl,
                        "uploadedBy" to photo.uploadedBy,
                        "createdAt" to photo.createdAt,
                        "status" to photo.status
                    )

                    firestore.collection(COLLECTION)
                        .document(photoId)
                        .set(docData)
                        .addOnSuccessListener {
                            onProgress(100)
                            onComplete(Result.success(photo))
                        }
                        .addOnFailureListener { err ->
                            Log.e(TAG, "Firestore metadata write failed: ${err.message}", err)
                            onComplete(Result.failure(err))
                        }
                }.addOnFailureListener { err ->
                    Log.e(TAG, "Failed to get download URL: ${err.message}", err)
                    onComplete(Result.failure(err))
                }
            }.addOnFailureListener { err ->
                Log.e(TAG, "Storage upload failed: ${err.message}", err)
                onComplete(Result.failure(err))
            }
        }
    }

    /**
     * Deletes photo from storage and marks metadata as deleted.
     */
    fun deletePhoto(photo: PandalPhoto, onComplete: (Exception?) -> Unit) {
        if (photo.storagePath.isNotBlank()) {
            storage.reference.child(photo.storagePath).delete().addOnCompleteListener {
                // Also update firestore doc
                firestore.collection(COLLECTION).document(photo.id)
                    .update("status", "deleted")
                    .addOnSuccessListener { onComplete(null) }
                    .addOnFailureListener { err -> onComplete(err) }
            }
        } else {
            firestore.collection(COLLECTION).document(photo.id)
                .update("status", "deleted")
                .addOnSuccessListener { onComplete(null) }
                .addOnFailureListener { err -> onComplete(err) }
        }
    }

    private fun compressImage(context: Context, uri: Uri): ByteArray? {
        return runCatching {
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            val original = BitmapFactory.decodeStream(inputStream) ?: return null
            inputStream?.close()

            // Handle orientation
            val exifStream = context.contentResolver.openInputStream(uri)
            val orientation = exifStream?.let {
                val exif = ExifInterface(it)
                val ori = exif.getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)
                it.close()
                ori
            } ?: ExifInterface.ORIENTATION_NORMAL

            val matrix = Matrix()
            when (orientation) {
                ExifInterface.ORIENTATION_ROTATE_90 -> matrix.postRotate(90f)
                ExifInterface.ORIENTATION_ROTATE_180 -> matrix.postRotate(180f)
                ExifInterface.ORIENTATION_ROTATE_270 -> matrix.postRotate(270f)
            }

            // Downscale to max 1600px width/height while preserving aspect ratio
            val maxDimension = 1600
            val width = original.width
            val height = original.height
            val scale = if (width > maxDimension || height > maxDimension) {
                val maxSide = Math.max(width, height)
                maxDimension.toFloat() / maxSide.toFloat()
            } else 1.0f

            matrix.postScale(scale, scale)

            val transformed = Bitmap.createBitmap(original, 0, 0, width, height, matrix, true)
            val outputStream = ByteArrayOutputStream()
            transformed.compress(Bitmap.CompressFormat.JPEG, 82, outputStream)
            val bytes = outputStream.toByteArray()
            outputStream.close()
            bytes
        }.getOrNull()
    }

    companion object {
        const val TAG = "PhotoRepository"
        const val COLLECTION = "pandalPhotos"
    }
}
