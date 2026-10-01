package com.pandalfinder.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.ProgressBar
import androidx.recyclerview.widget.RecyclerView
import com.pandalfinder.R
import com.pandalfinder.data.PandalPhoto
import java.net.URL
import java.util.concurrent.Executors

/**
 * ViewPager2 adapter for the top Pandal Hero image carousel.
 */
class HeroPhotoAdapter(
    private var photos: List<PandalPhoto>,
    private val onPhotoClick: (PandalPhoto) -> Unit
) : RecyclerView.Adapter<HeroPhotoAdapter.HeroViewHolder>() {

    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())

    companion object {
        val sharedMemoryCache = ImageCache
    }

    fun updatePhotos(newPhotos: List<PandalPhoto>) {
        photos = newPhotos
        notifyDataSetChanged()
    }

    fun getPhotos(): List<PandalPhoto> = photos

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): HeroViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_hero_photo, parent, false)
        return HeroViewHolder(view)
    }

    override fun onBindViewHolder(holder: HeroViewHolder, position: Int) {
        val photo = photos[position]
        holder.bind(photo)
    }

    override fun getItemCount(): Int = photos.size

    inner class HeroViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.heroPhotoImage)
        private val progress: ProgressBar = itemView.findViewById(R.id.heroPhotoProgress)

        fun bind(photo: PandalPhoto) {
            itemView.setOnClickListener { onPhotoClick(photo) }

            val cached = sharedMemoryCache[photo.downloadUrl]
            if (cached != null) {
                image.setImageBitmap(cached)
                progress.visibility = View.GONE
                return
            }

            image.setImageDrawable(null)
            progress.visibility = View.VISIBLE

            executor.execute {
                val bitmap = runCatching {
                    val input = URL(photo.downloadUrl).openStream()
                    val bmp = BitmapFactory.decodeStream(input)
                    input.close()
                    bmp
                }.getOrNull()

                main.post {
                    if (bitmap != null) {
                        sharedMemoryCache[photo.downloadUrl] = bitmap
                        image.setImageBitmap(bitmap)
                    }
                    progress.visibility = View.GONE
                }
            }
        }
    }
}
