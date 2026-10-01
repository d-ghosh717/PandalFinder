package com.pandalfinder.ui

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.button.MaterialButton
import com.pandalfinder.Pandal
import com.pandalfinder.R
import com.pandalfinder.data.PandalPhoto
import java.net.URL
import java.util.concurrent.Executors

class MyPhotosAdapter(
    private var photos: List<PandalPhoto>,
    private val pandalMap: Map<String, Pandal>,
    private val onPhotoClick: ((PandalPhoto) -> Unit)? = null,
    private val onReplace: (PandalPhoto) -> Unit,
    private val onDelete: (PandalPhoto) -> Unit
) : RecyclerView.Adapter<MyPhotosAdapter.ViewHolder>() {

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun updatePhotos(newPhotos: List<PandalPhoto>) {
        this.photos = newPhotos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_my_photo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val thumbnail: ImageView = itemView.findViewById(R.id.myPhotoThumbnail)
        private val nameText: TextView = itemView.findViewById(R.id.myPhotoPandalName)
        private val timeText: TextView = itemView.findViewById(R.id.myPhotoUploadTime)
        private val replaceBtn: MaterialButton = itemView.findViewById(R.id.btnReplaceMyPhoto)
        private val deleteBtn: MaterialButton = itemView.findViewById(R.id.btnDeleteMyPhoto)

        fun bind(photo: PandalPhoto) {
            val pandal = pandalMap[photo.pandalId] ?: pandalMap["pandal:${photo.pandalId}"]
            nameText.text = pandal?.name ?: "Pandal Photo"
            timeText.text = if (photo.createdAt > 0) "Uploaded ${formatAgo(photo.createdAt)}" else "Uploaded"

            val cached = ImageCache[photo.downloadUrl]
            if (cached != null) {
                thumbnail.setImageBitmap(cached)
            } else {
                thumbnail.setImageDrawable(null)
                if (photo.downloadUrl.isNotBlank()) {
                    val tag = photo.downloadUrl
                    thumbnail.tag = tag
                    executor.execute {
                        val bmp = runCatching {
                            val stream = URL(photo.downloadUrl).openStream()
                            BitmapFactory.decodeStream(stream)
                        }.getOrNull()

                        mainHandler.post {
                            if (thumbnail.tag == tag && bmp != null) {
                                ImageCache[photo.downloadUrl] = bmp
                                thumbnail.setImageBitmap(bmp)
                            }
                        }
                    }
                }
            }

            thumbnail.setOnClickListener { onPhotoClick?.invoke(photo) }
            itemView.setOnClickListener { onPhotoClick?.invoke(photo) }
            replaceBtn.setOnClickListener { onReplace(photo) }
            deleteBtn.setOnClickListener { onDelete(photo) }
        }

        private fun formatAgo(time: Long): String {
            val diff = (System.currentTimeMillis() - time) / 1000L
            return when {
                diff < 60 -> "just now"
                diff < 3600 -> "${diff / 60}m ago"
                diff < 86400 -> "${diff / 3600}h ago"
                else -> "${diff / 86400}d ago"
            }
        }
    }
}
