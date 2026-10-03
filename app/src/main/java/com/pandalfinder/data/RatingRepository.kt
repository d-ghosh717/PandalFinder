package com.pandalfinder.data

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.SetOptions
import java.util.Locale

class RatingRepository(
    private val auth: FirebaseAuth = FirebaseAuth.getInstance(),
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    companion object {
        private const val TAG = "RatingRepository"
        const val DIAGNOSTIC_TAG = "PandalQuest-Ratings"
        const val COLLECTION_RATINGS = "pandalRatings"

        /**
         * Generates deterministic document ID: {pandalId}_{userId}
         */
        fun getDocumentId(pandalId: String, userId: String): String {
            val safePandal = pandalId.replace("/", "_")
            return "${safePandal}_${userId}"
        }

        /**
         * Calculates aggregate average rating (rounded to 1 decimal place) and count.
         * Returns null average if count == 0.
         */
        fun calculateAggregate(ratings: List<Int>): Pair<Double?, Int> {
            if (ratings.isEmpty()) return Pair(null, 0)
            val sum = ratings.sum()
            val avg = sum.toDouble() / ratings.size
            val rounded = String.format(Locale.US, "%.1f", avg).toDoubleOrNull() ?: avg
            return Pair(rounded, ratings.size)
        }

        /**
         * Formats average rating for display:
         * 4.0 -> "4 ⭐"
         * 4.3 -> "4.3 ⭐"
         * 5.0 -> "5 ⭐"
         * null / 0 -> "—"
         */
        fun formatRatingDisplay(avg: Double?): String {
            if (avg == null || avg <= 0.0) return "—"
            return if (avg % 1.0 == 0.0) {
                "${avg.toInt()} ⭐"
            } else {
                String.format(Locale.US, "%.1f ⭐", avg)
            }
        }
    }

    private fun ensureAuth(onReady: (com.google.firebase.auth.FirebaseUser?) -> Unit) {
        val user = auth.currentUser
        if (user != null) {
            onReady(user)
            return
        }
        auth.signInAnonymously()
            .addOnSuccessListener { result -> onReady(result.user) }
            .addOnFailureListener { e ->
                Log.e(DIAGNOSTIC_TAG, "write failed code=AUTH_FAILED: ${e.message}", e)
                onReady(null)
            }
    }

    /**
     * Submits or updates the user's rating (1 to 5 stars) for a pandal.
     * Uses deterministic doc ID {pandalId}_{userId} to ensure 1 active rating per user.
     */
    fun submitRating(
        pandalId: String,
        rating: Int,
        onResult: (Boolean, String?) -> Unit
    ) {
        if (rating !in 1..5) {
            onResult(false, "Rating must be between 1 and 5 stars")
            return
        }

        ensureAuth { user ->
            if (user == null) {
                Log.e(DIAGNOSTIC_TAG, "write failed code=UNAUTHENTICATED")
                onResult(false, "Couldn't save your rating. Please try again.")
                return@ensureAuth
            }

            val docId = getDocumentId(pandalId, user.uid)
            val data = hashMapOf(
                "ratingId" to docId,
                "pandalId" to pandalId,
                "userId" to user.uid,
                "rating" to rating,
                "updatedAt" to System.currentTimeMillis()
            )

            val docRef = firestore.collection(COLLECTION_RATINGS).document(docId)
            docRef.get().addOnSuccessListener { snapshot ->
                if (!snapshot.exists()) {
                    data["createdAt"] = System.currentTimeMillis()
                }
                docRef.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(DIAGNOSTIC_TAG, "Rating $rating saved successfully for $pandalId (uid=${user.uid})")
                        onResult(true, null)
                    }
                    .addOnFailureListener { e ->
                        Log.e(DIAGNOSTIC_TAG, "write failed code=${e.message}", e)
                        onResult(false, "Couldn't save your rating. Please try again.")
                    }
            }.addOnFailureListener { e ->
                Log.e(DIAGNOSTIC_TAG, "write failed code=GET_FAILED: ${e.message}", e)
                data["createdAt"] = System.currentTimeMillis()
                docRef.set(data, SetOptions.merge())
                    .addOnSuccessListener {
                        Log.d(DIAGNOSTIC_TAG, "Rating $rating saved successfully for $pandalId (uid=${user.uid})")
                        onResult(true, null)
                    }
                    .addOnFailureListener { err ->
                        Log.e(DIAGNOSTIC_TAG, "write failed code=${err.message}", err)
                        onResult(false, "Couldn't save your rating. Please try again.")
                    }
            }
        }
    }

    /**
     * Fetches current user's rating for the specified pandal.
     */
    fun fetchUserRating(pandalId: String, onResult: (Int?) -> Unit) {
        val user = auth.currentUser ?: return onResult(null)
        val docId = getDocumentId(pandalId, user.uid)
        firestore.collection(COLLECTION_RATINGS).document(docId)
            .get()
            .addOnSuccessListener { doc ->
                if (doc != null && doc.exists()) {
                    val r = doc.getLong("rating")?.toInt()
                    onResult(r)
                } else {
                    onResult(null)
                }
            }
            .addOnFailureListener {
                onResult(null)
            }
    }

    /**
     * Listens to all ratings for a given pandal in real time.
     * Returns the user's rating, average rating, and total rating count.
     */
    fun listenToRatings(
        pandalId: String,
        onUpdate: (userRating: Int?, avg: Double?, count: Int) -> Unit
    ): ListenerRegistration {
        val uid = auth.currentUser?.uid
        return firestore.collection(COLLECTION_RATINGS)
            .whereEqualTo("pandalId", pandalId)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error listening to ratings for $pandalId: ${error.message}")
                    return@addSnapshotListener
                }

                if (snapshot == null || snapshot.isEmpty) {
                    onUpdate(null, null, 0)
                    return@addSnapshotListener
                }

                val allRatings = mutableListOf<Int>()
                var myRating: Int? = null

                for (doc in snapshot.documents) {
                    val r = doc.getLong("rating")?.toInt() ?: continue
                    if (r in 1..5) {
                        allRatings.add(r)
                        if (uid != null && doc.getString("userId") == uid) {
                            myRating = r
                        }
                    }
                }

                val (avg, count) = calculateAggregate(allRatings)
                onUpdate(myRating, avg, count)
            }
    }

    /**
     * Fetches aggregate average rating and total count across a set of pandal IDs.
     */
    fun fetchAggregateRatingForPandals(pandalIds: Set<String>, onResult: (Double?, Int) -> Unit) {
        if (pandalIds.isEmpty()) {
            onResult(null, 0)
            return
        }
        val cleanIds = pandalIds.map { it.removePrefix("pandal:") }.toSet()
        val slugs = cleanIds.map { it.substringBeforeLast('-') }.filter { it.isNotBlank() }.toSet()
        firestore.collection(COLLECTION_RATINGS)
            .get()
            .addOnSuccessListener { snap ->
                val allRatings = mutableListOf<Int>()
                for (doc in snap.documents) {
                    val pId = doc.getString("pandalId")?.removePrefix("pandal:") ?: ""
                    val pSlug = pId.substringBeforeLast('-')
                    if (pId in cleanIds || (pSlug.isNotBlank() && pSlug in slugs)) {
                        val r = doc.getLong("rating")?.toInt() ?: continue
                        if (r in 1..5) {
                            allRatings.add(r)
                        }
                    }
                }
                val (avg, count) = calculateAggregate(allRatings)
                onResult(avg, count)
            }
            .addOnFailureListener {
                onResult(null, 0)
            }
    }
}
