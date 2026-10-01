package com.pandalfinder.ui

import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.pandalfinder.R

enum class UploadStatus {
    PENDING,
    UPLOADING,
    SUCCESS,
    FAILED
}

data class SelectedPhotoItem(
    val uri: Uri,
    var status: UploadStatus = UploadStatus.PENDING,
    var errorMsg: String? = null
)

class SelectedPhotosAdapter(
    private val items: MutableList<SelectedPhotoItem>,
    private val maxLimit: Int = 10,
    private val onRemove: (Int) -> Unit,
    private val onAddMore: () -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        private const val TYPE_PHOTO = 1
        private const val TYPE_ADD_MORE = 2
    }

    private var isUploading = false

    fun setUploading(uploading: Boolean) {
        isUploading = uploading
        notifyDataSetChanged()
    }

    fun getItems(): List<SelectedPhotoItem> = items

    fun updateItemStatus(index: Int, status: UploadStatus, error: String? = null) {
        if (index in items.indices) {
            items[index].status = status
            items[index].errorMsg = error
            notifyItemChanged(index)
        }
    }

    override fun getItemViewType(position: Int): Int {
        return if (position < items.size) TYPE_PHOTO else TYPE_ADD_MORE
    }

    override fun getItemCount(): Int {
        return if (items.size < maxLimit && !isUploading) {
            items.size + 1
        } else {
            items.size
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return if (viewType == TYPE_PHOTO) {
            val view = inflater.inflate(R.layout.item_selected_photo_thumb, parent, false)
            PhotoViewHolder(view)
        } else {
            val view = inflater.inflate(R.layout.item_add_more_photo_thumb, parent, false)
            AddMoreViewHolder(view)
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        if (holder is PhotoViewHolder) {
            holder.bind(items[position], position)
        } else if (holder is AddMoreViewHolder) {
            holder.bind()
        }
    }

    inner class PhotoViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val image: ImageView = itemView.findViewById(R.id.selectedThumbImage)
        private val progressOverlay: FrameLayout = itemView.findViewById(R.id.selectedThumbProgressOverlay)
        private val successOverlay: FrameLayout = itemView.findViewById(R.id.selectedThumbSuccessOverlay)
        private val errorOverlay: FrameLayout = itemView.findViewById(R.id.selectedThumbErrorOverlay)
        private val removeBtn: FrameLayout = itemView.findViewById(R.id.removeThumbBtn)

        fun bind(item: SelectedPhotoItem, position: Int) {
            image.setImageURI(item.uri)

            progressOverlay.visibility = if (item.status == UploadStatus.UPLOADING) View.VISIBLE else View.GONE
            successOverlay.visibility = if (item.status == UploadStatus.SUCCESS) View.VISIBLE else View.GONE
            errorOverlay.visibility = if (item.status == UploadStatus.FAILED) View.VISIBLE else View.GONE

            removeBtn.visibility = if (isUploading || item.status == UploadStatus.SUCCESS) View.GONE else View.VISIBLE
            removeBtn.setOnClickListener {
                if (!isUploading) {
                    onRemove(position)
                }
            }
        }
    }

    inner class AddMoreViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val addCard: View = itemView.findViewById(R.id.addMoreCard)

        fun bind() {
            addCard.setOnClickListener {
                if (!isUploading) {
                    onAddMore()
                }
            }
        }
    }
}
