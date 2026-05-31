package com.pandalfinder

import android.app.AlertDialog
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView

class PandalAdapter(private val pandals: List<Pandal>) : 
    RecyclerView.Adapter<PandalAdapter.PandalViewHolder>() {

    class PandalViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvPandalName: TextView = itemView.findViewById(R.id.tvPandalName)
        val tvDistance: TextView = itemView.findViewById(R.id.tvDistance)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PandalViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_pandal, parent, false)
        return PandalViewHolder(view)
    }

    override fun onBindViewHolder(holder: PandalViewHolder, position: Int) {
        val pandal = pandals[position]
        holder.tvPandalName.text = pandal.name
        holder.tvDistance.text = String.format("%.2f km", pandal.distance)
        
        // Show transport mode selection dialog
        holder.itemView.setOnClickListener {
            showTransportModeDialog(it, pandal)
        }
    }

    override fun getItemCount(): Int = pandals.size

    private fun showTransportModeDialog(view: View, pandal: Pandal) {
        val modes = arrayOf(
            "🚗 Car",
            "🏍️ Motorbike",
            "🚶 Walk"
        )
        
        AlertDialog.Builder(view.context)
            .setTitle("Choose Navigation Mode")
            .setItems(modes) { _, which ->
                val mode = when(which) {
                    0 -> "d"  // driving (car)
                    1 -> "l"  // two-wheeler (motorbike)
                    2 -> "w"  // walking
                    else -> "d"
                }
                openGoogleMapsNavigation(view, pandal, mode)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun openGoogleMapsNavigation(view: View, pandal: Pandal, mode: String) {
        try {
            // Open Google Maps with selected transport mode
            val uri = Uri.parse("google.navigation:q=${pandal.latitude},${pandal.longitude}&mode=$mode")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
            }
            view.context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // Google Maps is not installed
            Toast.makeText(
                view.context,
                "Google Maps is not installed",
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}
