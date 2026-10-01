package com.pandalfinder.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageButton
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pandalfinder.Pandal
import com.pandalfinder.R
import com.pandalfinder.data.AdminCrowdReport

class AdminCrowdAdapter(
    private var reports: List<AdminCrowdReport>,
    private val pandalMap: Map<String, Pandal>,
    private val onDeleteClick: (AdminCrowdReport) -> Unit
) : RecyclerView.Adapter<AdminCrowdAdapter.ViewHolder>() {

    fun updateReports(newReports: List<AdminCrowdReport>) {
        reports = newReports
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_admin_crowd, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val report = reports[position]
        val pandal = pandalMap[report.pandalId] ?: pandalMap["pandal:${report.pandalId}"]
        holder.levelBadge.text = "${report.level}"
        holder.pandalName.text = pandal?.name ?: "Pandal: ${report.pandalId}"
        
        val timeStr = if (report.timestamp > 0) {
            val mins = ((System.currentTimeMillis() - report.timestamp) / 60_000).coerceAtLeast(0)
            if (mins < 60) "$mins min ago" else "${mins / 60}h ago"
        } else "Unknown time"
        
        holder.reporterInfo.text = "User: ${report.userId.take(8)}... · $timeStr · status: ${report.status}"
        holder.deleteBtn.setOnClickListener { onDeleteClick(report) }
    }

    override fun getItemCount(): Int = reports.size

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v) {
        val levelBadge: TextView = v.findViewById(R.id.adminCrowdLevelBadge)
        val pandalName: TextView = v.findViewById(R.id.adminCrowdPandalName)
        val reporterInfo: TextView = v.findViewById(R.id.adminCrowdReporterInfo)
        val deleteBtn: ImageButton = v.findViewById(R.id.adminCrowdDeleteBtn)
    }
}
