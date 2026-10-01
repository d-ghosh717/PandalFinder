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

class AdminPhotosAdapter(
    private var photos: List<PandalPhoto>,
    private val pandalMap: Map<String, Pandal>,
    private val onPhotoClick: (PandalPhoto) -> Unit,
    private val onDeleteClick: (PandalPhoto) -> Unit
) : RecyclerView.Adapter<AdminPhotosAdapter.ViewHolder>() {

    private val executor = Executors.newFixedThreadPool(3)
    private val mainHandler = Handler(Looper.getMainLooper())

    fun updatePhotos(newPhotos: List<PandalPhoto>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_photo, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val photo = photos[position]
        val pandal = pandalMap[photo.pandalId] ?: pandalMap["pandal:${photo.pandalId}"]
        holder.pandalName.text = pandal?.name ?: "Pandal: ${photo.pandalId}"
        holder.uploaderText.text = "Uploader: ${photo.uploadedBy.take(8)}... · Status: ${photo.status}"
        
        val timeStr = if (photo.createdAt > 0) {
            val mins = ((System.currentTimeMillis() - photo.createdAt) / 60_000).coerceAtLeast(0)
            if (mins < 60) "$mins min ago" else "${mins / 60}h ago"
        } else "Unknown time"
        holder.timeText.text = "Uploaded $timeStr"

        val cached = ImageCache[photo.downloadUrl]
        if (cached != null) {
            holder.thumb.setImageBitmap(cached)
        } else {
            holder.thumb.setImageResource(R.drawable.ic_camera)
            if (photo.downloadUrl.isNotBlank()) {
                executor.execute {
                    val bitmap = runCatching {
                        val stream = URL(photo.downloadUrl).openStream()
                        val decoded = BitmapFactory.decodeStream(stream)
                        stream.close()
                        decoded
                    }.getOrNull()

                    mainHandler.post {
                        if (holder.bindingAdapterPosition == position && bitmap != null) {
                            ImageCache[photo.downloadUrl] = bitmap
                            holder.thumb.setImageBitmap(bitmap)
                        }
                    }
                }
            }
        }

        holder.itemView.setOnClickListener { onPhotoClick(photo) }
        holder.deleteBtn.setOnClickListener { onDeleteClick(photo) }
    }

    override fun getItemCount(): Int = photos.size

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val thumb: ImageView = v.findViewById(R.id.adminPhotoThumb)
        val pandalName: TextView = v.findViewById(R.id.adminPhotoPandalName)
        val uploaderText: TextView = v.findViewById(R.id.adminPhotoUploaderText)
        val timeText: TextView = v.findViewById(R.id.adminPhotoTimeText)
        val deleteBtn: MaterialButton = v.findViewById(R.id.adminPhotoDeleteBtn)
    }
}
