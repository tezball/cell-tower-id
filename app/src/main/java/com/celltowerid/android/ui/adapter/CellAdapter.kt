package com.celltowerid.android.ui.adapter

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.celltowerid.android.R
import com.celltowerid.android.databinding.ItemCellBinding
import com.celltowerid.android.domain.model.CellMeasurement
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.ui.TowerDetailActivity
import com.celltowerid.android.util.CellIdParser
import com.celltowerid.android.util.PinIdentity
import com.celltowerid.android.util.SignalClassifier
import com.celltowerid.android.util.UsCarriers

class CellAdapter(
    private val onTogglePin: ((CellMeasurement) -> Unit)? = null
) : RecyclerView.Adapter<CellAdapter.CellViewHolder>() {

    private var cells: List<CellMeasurement> = emptyList()
    private var pinnedKeys: Set<String> = emptySet()

    fun submitList(newCells: List<CellMeasurement>) {
        val diff = DiffUtil.calculateDiff(CellDiffCallback(cells, newCells))
        cells = newCells
        diff.dispatchUpdatesTo(this)
    }

    fun setPinnedKeys(keys: Set<String>) {
        if (pinnedKeys == keys) return
        pinnedKeys = keys
        notifyDataSetChanged()
    }

    private fun cellKey(cell: CellMeasurement): String =
        PinIdentity.keyOf(cell) ?: "${cell.radio}-?-${System.identityHashCode(cell)}"

    private class CellDiffCallback(
        private val old: List<CellMeasurement>,
        private val new: List<CellMeasurement>
    ) : DiffUtil.Callback() {
        override fun getOldListSize() = old.size
        override fun getNewListSize() = new.size
        override fun areItemsTheSame(oldPos: Int, newPos: Int): Boolean {
            val o = old[oldPos]; val n = new[newPos]
            return o.radio == n.radio && o.cid == n.cid && o.mcc == n.mcc && o.mnc == n.mnc && o.tacLac == n.tacLac
        }
        override fun areContentsTheSame(oldPos: Int, newPos: Int) = old[oldPos] == new[newPos]
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellViewHolder {
        val binding = ItemCellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CellViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
        val cell = cells[position]
        holder.bind(cell, pinnedKeys.contains(cellKey(cell)), onTogglePin)
    }

    override fun getItemCount(): Int = cells.size

    class CellViewHolder(private val binding: ItemCellBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(
            cell: CellMeasurement,
            isPinned: Boolean,
            onTogglePin: ((CellMeasurement) -> Unit)?
        ) {
            val quality = SignalClassifier.classify(cell)

            binding.signalIndicator.setBackgroundColor(Color.parseColor(quality.colorHex))
            binding.signalIndicator.contentDescription = quality.label
            binding.textRadioType.text = cell.radio.name
            binding.textServing.visibility = if (cell.isRegistered) View.VISIBLE else View.GONE

            val carrier = if (cell.mcc != null && cell.mnc != null) {
                UsCarriers.getCarrierName(cell.mcc, cell.mnc) ?: "${cell.mcc}/${cell.mnc}"
            } else {
                ""
            }
            binding.textCarrier.text = carrier

            val cellIdParts = mutableListOf<String>()
            cell.tacLac?.let { cellIdParts.add("TAC: $it") }
            cell.cid?.let { cid ->
                cellIdParts.add("CID: $cid")
                if (cell.radio == RadioType.LTE) {
                    val (enb, _) = CellIdParser.parseEutranCid(cid)
                    cellIdParts.add("eNB: $enb")
                }
            }
            cell.pciPsc?.let { cellIdParts.add("PCI: $it") }
            binding.textCellId.text = cellIdParts.joinToString(" | ")

            val signalText = when {
                cell.rsrp != null -> "${cell.rsrp} dBm"
                cell.rssi != null -> "${cell.rssi} dBm"
                else -> "N/A"
            }
            binding.textSignal.text = signalText
            binding.textSignal.setTextColor(Color.parseColor(quality.colorHex))

            val details = mutableListOf<String>()
            cell.earfcnArfcn?.let { details.add("EARFCN: $it") }
            cell.band?.let { details.add("Band: $it") }
            cell.rsrq?.let { details.add("RSRQ: ${it}dB") }
            cell.sinr?.let { details.add("SINR: ${it}dB") }
            binding.textDetails.text = details.joinToString(" | ")

            val serving = if (cell.isRegistered) "Serving" else "Neighbor"
            binding.root.contentDescription =
                "${cell.radio.name} cell, $serving, ${quality.label}, $signalText, tap for details"

            binding.btnPin.setImageResource(
                if (isPinned) R.drawable.ic_pin_filled else R.drawable.ic_pin_outlined
            )
            binding.btnPin.contentDescription = binding.root.context.getString(
                if (isPinned) R.string.unpin_tower_cd else R.string.pin_tower_cd
            )
            if (onTogglePin != null) {
                binding.btnPin.visibility = View.VISIBLE
                binding.btnPin.setOnClickListener { onTogglePin(cell) }
            } else {
                binding.btnPin.visibility = View.GONE
                binding.btnPin.setOnClickListener(null)
            }

            // Click to open tower detail
            binding.root.setOnClickListener {
                val context = it.context
                val intent = Intent(context, TowerDetailActivity::class.java).apply {
                    putExtra(TowerDetailActivity.EXTRA_RADIO, cell.radio.name)
                    putExtra(TowerDetailActivity.EXTRA_LATITUDE, cell.latitude)
                    putExtra(TowerDetailActivity.EXTRA_LONGITUDE, cell.longitude)
                    putExtra(TowerDetailActivity.EXTRA_TIMESTAMP, cell.timestamp)
                    putExtra(TowerDetailActivity.EXTRA_IS_REGISTERED, cell.isRegistered)
                    cell.mcc?.let { v -> putExtra(TowerDetailActivity.EXTRA_MCC, v) }
                    cell.mnc?.let { v -> putExtra(TowerDetailActivity.EXTRA_MNC, v) }
                    cell.tacLac?.let { v -> putExtra(TowerDetailActivity.EXTRA_TAC_LAC, v) }
                    cell.cid?.let { v -> putExtra(TowerDetailActivity.EXTRA_CID, v) }
                    cell.pciPsc?.let { v -> putExtra(TowerDetailActivity.EXTRA_PCI, v) }
                    cell.earfcnArfcn?.let { v -> putExtra(TowerDetailActivity.EXTRA_EARFCN, v) }
                    cell.band?.let { v -> putExtra(TowerDetailActivity.EXTRA_BAND, v) }
                    cell.bandwidth?.let { v -> putExtra(TowerDetailActivity.EXTRA_BANDWIDTH, v) }
                    cell.rsrp?.let { v -> putExtra(TowerDetailActivity.EXTRA_RSRP, v) }
                    cell.rsrq?.let { v -> putExtra(TowerDetailActivity.EXTRA_RSRQ, v) }
                    cell.rssi?.let { v -> putExtra(TowerDetailActivity.EXTRA_RSSI, v) }
                    cell.sinr?.let { v -> putExtra(TowerDetailActivity.EXTRA_SINR, v) }
                    cell.cqi?.let { v -> putExtra(TowerDetailActivity.EXTRA_CQI, v) }
                    cell.timingAdvance?.let { v -> putExtra(TowerDetailActivity.EXTRA_TA, v) }
                    cell.signalLevel?.let { v -> putExtra(TowerDetailActivity.EXTRA_SIGNAL_LEVEL, v) }
                    cell.operatorName?.let { v -> putExtra(TowerDetailActivity.EXTRA_OPERATOR, v) }
                    cell.gpsAccuracy?.let { v -> putExtra(TowerDetailActivity.EXTRA_GPS_ACCURACY, v) }
                }
                context.startActivity(intent)
            }
        }
    }
}
