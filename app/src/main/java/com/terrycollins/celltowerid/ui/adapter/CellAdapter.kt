package com.terrycollins.celltowerid.ui.adapter

import android.content.Intent
import android.graphics.Color
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.terrycollins.celltowerid.databinding.ItemCellBinding
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.ui.TowerDetailActivity
import com.terrycollins.celltowerid.util.CellIdParser
import com.terrycollins.celltowerid.util.SignalClassifier
import com.terrycollins.celltowerid.util.UsCarriers

class CellAdapter : RecyclerView.Adapter<CellAdapter.CellViewHolder>() {

    private var cells: List<CellMeasurement> = emptyList()

    fun submitList(newCells: List<CellMeasurement>) {
        cells = newCells
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CellViewHolder {
        val binding = ItemCellBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return CellViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CellViewHolder, position: Int) {
        holder.bind(cells[position])
    }

    override fun getItemCount(): Int = cells.size

    class CellViewHolder(private val binding: ItemCellBinding) : RecyclerView.ViewHolder(binding.root) {

        fun bind(cell: CellMeasurement) {
            val quality = SignalClassifier.classify(cell)

            binding.signalIndicator.setBackgroundColor(Color.parseColor(quality.colorHex))
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
