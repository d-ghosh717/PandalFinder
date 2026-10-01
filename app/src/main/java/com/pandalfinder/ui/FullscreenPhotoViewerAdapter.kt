package com.pandalfinder.ui

import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.pandalfinder.R
import com.pandalfinder.data.PandalPhoto
import java.net.URL
import java.util.concurrent.Executors

class FullscreenPhotoViewerAdapter(
    private val photos: List<PandalPhoto>,
    private val onSingleTap: () -> Unit
) : RecyclerView.Adapter<FullscreenPhotoViewerAdapter.FullscreenViewHolder>() {

    private val executor = Executors.newFixedThreadPool(2)
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FullscreenViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_fullscreen_photo, parent, false)
        return FullscreenViewHolder(view)
    }

    override fun onBindViewHolder(holder: FullscreenViewHolder, position: Int) {
        holder.bind(photos[position])
    }

    override fun getItemCount(): Int = photos.size

    inner class FullscreenViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val zoomableImage: ZoomableImageView = itemView.findViewById(R.id.zoomablePhotoImage)
        private val progress: ProgressBar = itemView.findViewById(R.id.fullscreenPhotoProgress)

        fun bind(photo: PandalPhoto) {
            zoomableImage.onSingleTapListener = onSingleTap
            zoomableImage.resetZoom(animate = false)

            val cached = ImageCache[photo.downloadUrl]
            if (cached != null) {
                zoomableImage.setImageBitmap(cached)
                progress.visibility = View.GONE
                return
            }

            zoomableImage.setImageDrawable(null)
            progress.visibility = View.VISIBLE

            executor.execute {
                val bitmap = runCatching {
                    val input = URL(photo.downloadUrl).openStream()
                    val bmp = BitmapFactory.decodeStream(input)
                    input.close()
                    bmp
                }.getOrNull()

                mainHandler.post {
                    if (bitmap != null) {
                        ImageCache[photo.downloadUrl] = bitmap
                        zoomableImage.setImageBitmap(bitmap)
                    }
                    progress.visibility = View.GONE
                }
            }
        }
    }
}
