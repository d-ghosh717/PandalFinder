package com.pandalfinder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pandalfinder.Pandal
import com.pandalfinder.R
import com.pandalfinder.data.AdminWeatherReport

class AdminWeatherAdapter(
    private var reports: List<AdminWeatherReport>,
    private val pandalMap: Map<String, Pandal>,
    private val onDeleteClick: (AdminWeatherReport) -> Unit
) : RecyclerView.Adapter<AdminWeatherAdapter.ViewHolder>() {

    fun updateReports(newReports: List<AdminWeatherReport>) {
        reports = newReports
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_weather, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = reports[position]
        val pandal = pandalMap[report.pandalId] ?: pandalMap["pandal:${report.pandalId}"]
        holder.emojiBadge.text = when (report.condition) {
            "HEAVY_RAIN" -> "⛈"
            "RAINING" -> "🌧"
            "DRIZZLE" -> "🌦"
            else -> "☀"
        }
        holder.pandalName.text = pandal?.name ?: "Pandal: ${report.pandalId}"
        
        val timeStr = if (report.timestamp > 0) {
            val mins = ((System.currentTimeMillis() - report.timestamp) / 60_000).coerceAtLeast(0)
            if (mins < 60) "$mins min ago" else "${mins / 60}h ago"
        } else "Unknown time"
        
        holder.conditionText.text = "${report.condition.replace('_', ' ')} · by ${report.userId.take(8)}... ($timeStr)"
        holder.deleteBtn.setOnClickListener { onDeleteClick(report) }
    }

    override fun getItemCount(): Int = reports.size

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val emojiBadge: TextView = v.findViewById(R.id.adminWeatherEmojiBadge)
        val pandalName: TextView = v.findViewById(R.id.adminWeatherPandalName)
        val conditionText: TextView = v.findViewById(R.id.adminWeatherConditionText)
        val deleteBtn: ImageButton = v.findViewById(R.id.adminWeatherDeleteBtn)
    }
}
