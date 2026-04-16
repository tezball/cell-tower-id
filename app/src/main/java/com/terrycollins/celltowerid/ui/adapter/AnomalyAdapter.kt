package com.terrycollins.celltowerid.ui.adapter

import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.terrycollins.celltowerid.databinding.ItemAnomalyBinding
import com.terrycollins.celltowerid.domain.model.AnomalyEvent
import com.terrycollins.celltowerid.ui.TowerDetailActivity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnomalyAdapter : RecyclerView.Adapter<AnomalyAdapter.AnomalyViewHolder>() {

    private var anomalies: List<AnomalyEvent> = emptyList()

    fun submitList(newList: List<AnomalyEvent>) {
        anomalies = newList
        notifyDataSetChanged()
    }

    /** Removes the item at [position] from the in-memory list and notifies the
     *  RecyclerView. Returns the removed event so callers can offer an "undo". */
    fun removeAt(position: Int): AnomalyEvent {
        val mutable = anomalies.toMutableList()
        val removed = mutable.removeAt(position)
        anomalies = mutable
        notifyItemRemoved(position)
        return removed
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AnomalyViewHolder {
        val binding = ItemAnomalyBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return AnomalyViewHolder(binding)
    }

    override fun onBindViewHolder(holder: AnomalyViewHolder, position: Int) {
        holder.bind(anomalies[position])
    }

    override fun getItemCount() = anomalies.size

    class AnomalyViewHolder(private val binding: ItemAnomalyBinding) : RecyclerView.ViewHolder(binding.root) {

        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US)

        fun bind(anomaly: AnomalyEvent) {
            binding.root.setOnClickListener {
                val context = it.context
                val intent = Intent(context, TowerDetailActivity::class.java).apply {
                    putExtra(TowerDetailActivity.EXTRA_RADIO, (anomaly.cellRadio?.name ?: "UNKNOWN"))
                    putExtra(TowerDetailActivity.EXTRA_LATITUDE, anomaly.latitude ?: 0.0)
                    putExtra(TowerDetailActivity.EXTRA_LONGITUDE, anomaly.longitude ?: 0.0)
                    putExtra(TowerDetailActivity.EXTRA_TIMESTAMP, anomaly.timestamp)
                    anomaly.cellMcc?.let { v -> putExtra(TowerDetailActivity.EXTRA_MCC, v) }
                    anomaly.cellMnc?.let { v -> putExtra(TowerDetailActivity.EXTRA_MNC, v) }
                    anomaly.cellTacLac?.let { v -> putExtra(TowerDetailActivity.EXTRA_TAC_LAC, v) }
                    anomaly.cellCid?.let { v -> putExtra(TowerDetailActivity.EXTRA_CID, v) }
                    anomaly.cellPci?.let { v -> putExtra(TowerDetailActivity.EXTRA_PCI, v) }
                    anomaly.signalStrength?.let { v -> putExtra(TowerDetailActivity.EXTRA_RSSI, v) }
                }
                context.startActivity(intent)
            }

            // Severity indicator color
            binding.severityIndicator.setBackgroundColor(
                Color.parseColor(anomaly.severity.colorHex)
            )

            // Type
            binding.textAnomalyType.text = anomaly.type.displayName

            // Severity badge
            binding.textSeverity.text = anomaly.severity.displayName
            val bgDrawable = GradientDrawable().apply {
                setColor(Color.parseColor(anomaly.severity.colorHex))
                cornerRadius = 12f
            }
            binding.textSeverity.background = bgDrawable

            // Description
            binding.textDescription.text = anomaly.description

            // Explanation
            val explanation = buildString {
                append(anomaly.type.explanation)
                anomaly.type.drivingNote?.let { note ->
                    append("\n\nWhile driving: ")
                    append(note)
                }
            }
            binding.textExplanation.text = explanation

            // Cell info
            val cellParts = mutableListOf<String>()
            anomaly.cellRadio?.let { cellParts.add(it.name) }
            if (anomaly.cellMcc != null && anomaly.cellMnc != null) {
                cellParts.add("${anomaly.cellMcc}/${anomaly.cellMnc}")
            }
            anomaly.cellCid?.let { cellParts.add("CID: $it") }
            anomaly.signalStrength?.let { cellParts.add("${it}dBm") }
            binding.textCellInfo.text = cellParts.joinToString(" | ")

            // Timestamp
            binding.textTimestamp.text = dateFormat.format(Date(anomaly.timestamp))
        }
    }
}
