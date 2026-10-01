package com.pandalfinder.ui

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.card.MaterialCardView
import com.pandalfinder.MainActivity
import com.pandalfinder.R
import com.pandalfinder.data.HoppingStop
import com.pandalfinder.data.StopType

class HoppingAdapter(
    private var items: MutableList<HoppingStop>,
    private var legDistances: List<Int> = emptyList(),
    private val onRemove: (HoppingStop) -> Unit,
    private val onStopClick: (HoppingStop) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onItemMoved: (Int, Int) -> Unit
) : RecyclerView.Adapter<HoppingAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val routeConnectorLayout: View = v.findViewById(R.id.routeConnectorLayout)
        val connectorLegDistance: TextView = v.findViewById(R.id.connectorLegDistance)
        val stopCard: MaterialCardView = v.findViewById(R.id.stopCard)
        val stopIndex: TextView = v.findViewById(R.id.stopIndex)
        val stopTypeIconContainer: FrameLayout = v.findViewById(R.id.stopTypeIconContainer)
        val stopTypeIcon: ImageView = v.findViewById(R.id.stopTypeIcon)
        val stopName: TextView = v.findViewById(R.id.stopName)
        val stopArea: TextView = v.findViewById(R.id.stopArea)
        val removeButton: ImageButton = v.findViewById(R.id.removeStopButton)
        val dragHandle: ImageView = v.findViewById(R.id.dragHandle)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_hopping_stop, parent, false)
        return ViewHolder(v)
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        val context = holder.itemView.context

        holder.stopIndex.text = (position + 1).toString()
        holder.stopName.text = item.name
        holder.stopArea.text = item.subtitle

        // Route connector between stops
        if (position > 0) {
            holder.routeConnectorLayout.visibility = View.VISIBLE
            if (position in legDistances.indices && legDistances[position] > 0) {
                val distText = MainActivity.routeDistanceText(legDistances[position])
                holder.connectorLegDistance.text = "↓  $distText to Stop ${position + 1}"
            } else {
                holder.connectorLegDistance.text = "↓  To Stop ${position + 1}"
            }
        } else {
            holder.routeConnectorLayout.visibility = View.GONE
        }

        when (item.type) {
            StopType.PANDAL -> {
                holder.stopTypeIcon.setImageResource(R.drawable.ic_pandal_icon)
                holder.stopTypeIcon.setColorFilter(ContextCompat.getColor(context, R.color.primary))
                holder.stopTypeIconContainer.setBackgroundResource(R.drawable.bg_lens_red)
                holder.stopTypeIconContainer.backgroundTintList = null
                holder.stopIndex.setTextColor(ContextCompat.getColor(context, R.color.primary))
                holder.stopIndex.setBackgroundResource(R.drawable.bg_lens_index)
                holder.stopIndex.backgroundTintList = null
            }
            StopType.METRO -> {
                holder.stopTypeIcon.setImageResource(R.drawable.ic_metro_train)
                holder.stopTypeIcon.setColorFilter(ContextCompat.getColor(context, R.color.metro_icon))
                holder.stopTypeIconContainer.setBackgroundResource(R.drawable.bg_lens_metro)
                holder.stopTypeIconContainer.backgroundTintList = null
                holder.stopIndex.setTextColor(ContextCompat.getColor(context, R.color.metro_icon))
                holder.stopIndex.setBackgroundResource(R.drawable.bg_lens_metro)
                holder.stopIndex.backgroundTintList = null
            }
            StopType.TOILET -> {
                holder.stopTypeIcon.setImageResource(R.drawable.ic_restroom)
                holder.stopTypeIcon.setColorFilter(ContextCompat.getColor(context, R.color.toilet_icon))
                holder.stopTypeIconContainer.setBackgroundResource(R.drawable.bg_lens_toilet)
                holder.stopTypeIconContainer.backgroundTintList = null
                holder.stopIndex.setTextColor(ContextCompat.getColor(context, R.color.toilet_icon))
                holder.stopIndex.setBackgroundResource(R.drawable.bg_lens_toilet)
                holder.stopIndex.backgroundTintList = null
            }
        }

        holder.removeButton.setOnClickListener {
            onRemove(item)
        }

        holder.itemView.setOnClickListener {
            onStopClick(item)
        }

        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<HoppingStop>, newLegDistances: List<Int>) {
        items = newItems.toMutableList()
        legDistances = newLegDistances
        notifyDataSetChanged()
    }

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition < toPosition) {
            for (i in fromPosition until toPosition) {
                java.util.Collections.swap(items, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                java.util.Collections.swap(items, i, i - 1)
            }
        }
        notifyItemMoved(fromPosition, toPosition)
        notifyItemRangeChanged(Math.min(fromPosition, toPosition), Math.abs(fromPosition - toPosition) + 1)
        onItemMoved(fromPosition, toPosition)
    }
}
