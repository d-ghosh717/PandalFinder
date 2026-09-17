package com.pandalfinder.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pandalfinder.MainActivity
import com.pandalfinder.Pandal
import com.pandalfinder.R

class HoppingAdapter(
    private var items: MutableList<Pandal>,
    private var legDistances: List<Int> = emptyList(),
    private val onRemove: (Pandal) -> Unit,
    private val onPandalClick: (Pandal) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit,
    private val onItemMoved: (Int, Int) -> Unit
) : RecyclerView.Adapter<HoppingAdapter.ViewHolder>() {

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val stopIndex: TextView = v.findViewById(R.id.stopIndex)
        val stopName: TextView = v.findViewById(R.id.stopName)
        val stopArea: TextView = v.findViewById(R.id.stopArea)
        val legDistanceContainer: View = v.findViewById(R.id.legDistanceContainer)
        val legDistance: TextView = v.findViewById(R.id.legDistance)
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
        holder.stopIndex.text = (position + 1).toString()
        holder.stopName.text = item.name
        holder.stopArea.text = item.area

        // Display leg distance if calculated
        if (position in legDistances.indices && legDistances[position] > 0) {
            val distText = MainActivity.routeDistanceText(legDistances[position])
            holder.legDistanceContainer.visibility = View.VISIBLE
            holder.legDistance.text = if (position == 0) "↓ $distText from your location" else "↓ $distText from Stop $position"
        } else {
            holder.legDistanceContainer.visibility = View.GONE
        }

        holder.removeButton.setOnClickListener {
            onRemove(item)
        }

        holder.itemView.setOnClickListener {
            onPandalClick(item)
        }

        holder.dragHandle.setOnTouchListener { _, event ->
            if (event.actionMasked == MotionEvent.ACTION_DOWN) {
                onStartDrag(holder)
            }
            false
        }
    }

    override fun getItemCount(): Int = items.size

    fun updateData(newItems: List<Pandal>, newLegDistances: List<Int>) {
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
