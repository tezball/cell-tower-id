package com.celltowerid.android.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.google.android.material.snackbar.Snackbar
import com.celltowerid.android.R
import com.celltowerid.android.databinding.FragmentCellListBinding
import com.celltowerid.android.domain.model.RadioType
import com.celltowerid.android.ui.adapter.CellAdapter
import com.celltowerid.android.ui.viewmodel.CellListViewModel

class CellListFragment : Fragment() {

    private var _binding: FragmentCellListBinding? = null
    private val binding get() = _binding!!

    private val viewModel: CellListViewModel by viewModels()
    private lateinit var adapter: CellAdapter

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCellListBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        adapter = CellAdapter(onTogglePin = viewModel::togglePin)
        binding.recyclerCells.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCells.adapter = adapter

        setupFilters()

        viewModel.currentCells.observe(viewLifecycleOwner) { cells ->
            binding.progressLoading.visibility = View.GONE
            adapter.submitList(cells)
            binding.textEmpty.visibility = if (cells.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerCells.visibility = if (cells.isEmpty()) View.GONE else View.VISIBLE
        }

        viewModel.pinnedTowerKeys.observe(viewLifecycleOwner) { keys ->
            adapter.setPinnedKeys(keys)
        }

        viewModel.pinSnackbar.observe(viewLifecycleOwner) { event ->
            event.getContentIfNotHandled()?.let { msg ->
                Snackbar.make(binding.root, msg, Snackbar.LENGTH_SHORT).show()
            }
        }

        viewModel.loadRecentCells()
    }

    private fun setupFilters() {
        binding.chipAll.isChecked = true
        binding.chipGroupFilter.setOnCheckedStateChangeListener { _, checkedIds ->
            val filter = when {
                checkedIds.contains(R.id.chip_lte) -> RadioType.LTE
                checkedIds.contains(R.id.chip_nr) -> RadioType.NR
                else -> null
            }
            viewModel.setRadioTypeFilter(filter)
            viewModel.loadRecentCells()
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.loadRecentCells()
        viewModel.startAutoRefresh()
    }

    override fun onPause() {
        viewModel.stopAutoRefresh()
        super.onPause()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
