package com.celltowerid.android.ui.adapter

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celltowerid.android.databinding.ItemAnomalyBinding
import com.celltowerid.android.domain.model.AnomalyEvent
import com.celltowerid.android.ui.AnomalyIntentBuilder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class AnomalyAdapter : RecyclerView.Adapter<AnomalyAdapter.AnomalyViewHolder>() {

    private var anomalies: List<AnomalyEvent> = emptyList()

    fun submitList(newList: List<AnomalyEvent>) {
        val diff = DiffUtil.calculateDiff(AnomalyDiffCallback(anomalies, newList))
        anomalies = newList
        diff.dispatchUpdatesTo(this)
    }

    private class AnomalyDiffCallback(
        private val old: List<AnomalyEvent>,
        private val new: List<AnomalyEvent>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int) = old[oldPos].id == new[newPos].id
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == new[newPos]
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

        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm:ss", Locale.getDefault())

        fun bind(anomaly: AnomalyEvent) {
            binding.root.setOnClickListener {
                val context = it.context
                context.startActivity(AnomalyIntentBuilder.build(context, anomaly))
            }

            // Severity indicator color
            binding.severityIndicator.setBackgroundColor(
                Color.parseColor(anomaly.severity.colorHex)
            )
            binding.severityIndicator.contentDescription = anomaly.severity.displayName

            // Type
            binding.textAnomalyType.text = anomaly.type.displayName

            // Severity badge
            binding.textSeverity.text = anomaly.severity.displayName
            val bgDrawable = GradientDrawable().apply {
                setColor(Color.parseColor(anomaly.severity.colorHex))
                cornerRadius = 12f
            }
            binding.textSeverity.background = bgDrawable

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

            binding.root.contentDescription =
                "${anomaly.type.displayName}, ${anomaly.severity.displayName} severity, tap for details"
        }
    }
}
