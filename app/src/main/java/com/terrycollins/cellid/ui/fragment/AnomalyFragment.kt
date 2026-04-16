package com.terrycollins.cellid.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.terrycollins.cellid.databinding.FragmentAnomalyBinding
import com.terrycollins.cellid.domain.model.AnomalySeverity
import com.terrycollins.cellid.ui.adapter.AnomalyAdapter
import com.terrycollins.cellid.ui.viewmodel.AnomalyViewModel
import com.google.android.material.snackbar.Snackbar

class AnomalyFragment : Fragment() {

    private var _binding: FragmentAnomalyBinding? = null
    private val binding get() = _binding!!

    private val viewModel: AnomalyViewModel by viewModels()
    private lateinit var adapter: AnomalyAdapter
    private var showingAll = false

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentAnomalyBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = AnomalyAdapter()
        binding.recyclerAnomalies.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerAnomalies.adapter = adapter

        val swipeCallback = object : ItemTouchHelper.SimpleCallback(
            0,
            ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT
        ) {
            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val pos = viewHolder.bindingAdapterPosition
                if (pos == RecyclerView.NO_POSITION) return
                val removed = adapter.removeAt(pos)
                viewModel.dismiss(removed.id)
                Snackbar.make(binding.root, "Alert dismissed", Snackbar.LENGTH_LONG)
                    .setAction("Undo") { viewModel.undismiss(removed.id) }
                    .show()
            }
        }
        ItemTouchHelper(swipeCallback).attachToRecyclerView(binding.recyclerAnomalies)

        viewModel.anomalies.observe(viewLifecycleOwner) { anomalies ->
            adapter.submitList(anomalies)
            binding.textEmpty.visibility = if (anomalies.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerAnomalies.visibility = if (anomalies.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.undismissedCount.observe(viewLifecycleOwner) { count ->
            binding.textAlertCount.text = if (showingAll) "All Alerts" else "$count Active Alerts"
        }

        binding.btnDismissAll.setOnClickListener {
            viewModel.dismissAll()
        }

        binding.btnShowAll.setOnClickListener {
            showingAll = !showingAll
            if (showingAll) {
                viewModel.loadAllAnomalies()
                binding.btnShowAll.text = "Active Only"
            } else {
                viewModel.loadAnomalies()
                binding.btnShowAll.text = "Show All"
            }
        }

        // Initialise chips from ViewModel defaults (HIGH on, MEDIUM/LOW off)
        binding.chipSeverityHigh.isChecked = viewModel.isSeverityEnabled(AnomalySeverity.HIGH)
        binding.chipSeverityMedium.isChecked = viewModel.isSeverityEnabled(AnomalySeverity.MEDIUM)
        binding.chipSeverityLow.isChecked = viewModel.isSeverityEnabled(AnomalySeverity.LOW)

        binding.chipSeverityHigh.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSeverityEnabled(AnomalySeverity.HIGH, isChecked)
        }
        binding.chipSeverityMedium.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSeverityEnabled(AnomalySeverity.MEDIUM, isChecked)
        }
        binding.chipSeverityLow.setOnCheckedChangeListener { _, isChecked ->
            viewModel.setSeverityEnabled(AnomalySeverity.LOW, isChecked)
        }

        viewModel.loadAnomalies()
    }

    override fun onResume() {
        super.onResume()
        if (showingAll) viewModel.loadAllAnomalies() else viewModel.loadAnomalies()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
