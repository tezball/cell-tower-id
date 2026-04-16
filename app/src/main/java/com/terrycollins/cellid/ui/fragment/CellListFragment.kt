package com.terrycollins.cellid.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import com.terrycollins.cellid.R
import com.terrycollins.cellid.databinding.FragmentCellListBinding
import com.terrycollins.cellid.domain.model.RadioType
import com.terrycollins.cellid.ui.adapter.CellAdapter
import com.terrycollins.cellid.ui.viewmodel.CellListViewModel

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

        adapter = CellAdapter()
        binding.recyclerCells.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerCells.adapter = adapter

        setupFilters()

        viewModel.currentCells.observe(viewLifecycleOwner) { cells ->
            adapter.submitList(cells)
            binding.textEmpty.visibility = if (cells.isEmpty()) View.VISIBLE else View.GONE
            binding.recyclerCells.visibility = if (cells.isEmpty()) View.GONE else View.VISIBLE
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
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
