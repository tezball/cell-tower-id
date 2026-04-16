package com.terrycollins.celltowerid.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.databinding.FragmentSettingsBinding
import com.terrycollins.celltowerid.export.ExportFormat
import com.terrycollins.celltowerid.export.ExportWorker
import com.terrycollins.celltowerid.ui.DebugLogActivity
import com.terrycollins.celltowerid.R
import com.terrycollins.celltowerid.ui.LicensesActivity
import com.terrycollins.celltowerid.ui.viewmodel.CollectionViewModel
import com.terrycollins.celltowerid.util.Preferences
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val collectionViewModel: CollectionViewModel by activityViewModels()

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Collection interval slider
        binding.sliderInterval.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val intervalMs = value.toLong() * 1000
                collectionViewModel.setInterval(intervalMs)
                binding.textIntervalValue.text = "${value.toInt()} seconds"
            }
        }

        // Retention slider
        val prefs = Preferences(requireContext())
        binding.sliderRetention.value = prefs.retentionDays.toFloat().coerceIn(0f, 365f)
        binding.textRetentionValue.text = formatRetention(prefs.retentionDays)
        binding.sliderRetention.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val days = value.toInt()
                prefs.retentionDays = days
                binding.textRetentionValue.text = formatRetention(days)
            }
        }

        binding.btnOpenLicenses.setOnClickListener {
            startActivity(Intent(requireContext(), LicensesActivity::class.java))
        }
        binding.btnOpenDebugLog.setOnClickListener {
            startActivity(Intent(requireContext(), DebugLogActivity::class.java))
        }
        binding.btnPrivacyPolicy.setOnClickListener {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.privacy_policy_url))))
        }

        // Export buttons
        binding.btnExportCsv.setOnClickListener { startExport(ExportFormat.CSV) }
        binding.btnExportGeojson.setOnClickListener { startExport(ExportFormat.GEOJSON) }
        binding.btnExportKml.setOnClickListener { startExport(ExportFormat.KML) }

        // Load stats
        loadStats()

        // Observe
        collectionViewModel.totalMeasurements.observe(viewLifecycleOwner) { count ->
            binding.textTotalMeasurements.text = "Total Measurements: $count"
        }

        collectionViewModel.sessions.observe(viewLifecycleOwner) { sessions ->
            binding.textTotalSessions.text = "Total Sessions: ${sessions.size}"
        }
    }

    private fun formatRetention(days: Int): String =
        if (days <= 0) "Off" else "$days days"

    private fun loadStats() {
        collectionViewModel.loadStats()
        // Also load tower cache count
        lifecycleScope.launch {
            val db = AppDatabase.getInstance(requireContext())
            val count = withContext(Dispatchers.IO) { db.towerCacheDao().getCount() }
            binding.textTowerCache.text = "Cached Towers: $count"
        }
    }

    private fun startExport(format: ExportFormat) {
        val request = ExportWorker.buildRequest(format)
        WorkManager.getInstance(requireContext())
            .enqueue(request)

        Snackbar.make(
            binding.root,
            "Exporting as ${format.name}...",
            Snackbar.LENGTH_SHORT
        ).show()

        // Observe the work result
        WorkManager.getInstance(requireContext())
            .getWorkInfoByIdLiveData(request.id)
            .observe(viewLifecycleOwner) { workInfo ->
                if (workInfo?.state == WorkInfo.State.SUCCEEDED) {
                    val outputPath = workInfo.outputData.getString(ExportWorker.KEY_OUTPUT_URI)
                    Snackbar.make(
                        binding.root,
                        "Exported to: ${outputPath?.substringAfterLast("/")}",
                        Snackbar.LENGTH_LONG
                    ).setAction("Share") {
                        shareFile(outputPath, format)
                    }.show()
                }
            }
    }

    private fun shareFile(path: String?, format: ExportFormat) {
        path ?: return
        val file = File(path)
        if (!file.exists()) return

        val uri = FileProvider.getUriForFile(
            requireContext(),
            "${requireContext().packageName}.fileprovider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = format.mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(shareIntent, "Share ${format.name} export"))
    }

    override fun onResume() {
        super.onResume()
        loadStats()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
