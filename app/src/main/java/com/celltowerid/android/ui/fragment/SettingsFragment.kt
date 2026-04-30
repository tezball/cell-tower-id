package com.celltowerid.android.ui.fragment

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.documentfile.provider.DocumentFile
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.work.WorkInfo
import androidx.work.WorkManager
import com.celltowerid.android.BuildConfig
import com.celltowerid.android.data.AppDatabase
import com.celltowerid.android.databinding.FragmentSettingsBinding
import com.celltowerid.android.export.ExportFormat
import com.celltowerid.android.export.ExportWorker
import com.celltowerid.android.export.ImportLimits
import com.celltowerid.android.export.ImportWorker
import com.celltowerid.android.ui.DebugLogActivity
import com.celltowerid.android.R
import com.celltowerid.android.service.CollectionService
import com.celltowerid.android.ui.LicensesActivity
import com.celltowerid.android.ui.viewmodel.CollectionViewModel
import com.celltowerid.android.util.Preferences
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

private const val EXPORT_SUCCESS_SNACKBAR_MS = 7_500

class SettingsFragment : Fragment() {

    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!

    private val collectionViewModel: CollectionViewModel by activityViewModels()

    // SAF picker for the backup file. We accept any MIME type because the
    // picker on some devices won't filter beyond `*/*` for these formats; the
    // importer validates the contents strictly regardless of what the user
    // selects.
    private val pickBackup = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) onBackupPicked(uri)
    }

    // SAF tree picker for choosing the durable backup folder. The chosen URI
    // is held by the system across reboots once we take a persistable
    // permission grant on it.
    private val pickBackupLocation = registerForActivityResult(
        ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) onBackupLocationPicked(uri)
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val prefs = Preferences(requireContext())

        // Collection interval slider
        val currentSec = (prefs.scanIntervalMs.takeIf { it > 0 } ?: CollectionService.DEFAULT_INTERVAL_MS) / 1000L
        binding.sliderInterval.value = currentSec.toFloat().coerceIn(1f, 120f)
        binding.textIntervalValue.text = "$currentSec seconds"
        binding.sliderInterval.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val intervalMs = value.toLong() * 1000
                collectionViewModel.setInterval(intervalMs)
                binding.textIntervalValue.text = "${value.toInt()} seconds"
            }
        }

        // Retention slider
        binding.sliderRetention.value = prefs.retentionDays.toFloat().coerceIn(0f, 365f)
        binding.textRetentionValue.text = formatRetention(prefs.retentionDays)
        binding.sliderRetention.addOnChangeListener { _, value, fromUser ->
            if (fromUser) {
                val days = value.toInt()
                prefs.retentionDays = days
                binding.textRetentionValue.text = formatRetention(days)
            }
        }

        // Power saver toggle
        binding.switchPowerSaver.isChecked = prefs.powerSaverEnabled
        binding.switchPowerSaver.setOnCheckedChangeListener { _, checked ->
            prefs.powerSaverEnabled = checked
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

        // Backup location
        binding.btnBackupLocationChoose.setOnClickListener { pickBackupLocation.launch(null) }
        binding.btnBackupLocationClear.setOnClickListener { clearBackupLocation() }
        renderBackupLocationRow()

        // Export buttons
        binding.btnExportCsv.setOnClickListener { startExport(ExportFormat.CSV) }
        binding.btnExportGeojson.setOnClickListener { startExport(ExportFormat.GEOJSON) }
        binding.btnExportKml.setOnClickListener { startExport(ExportFormat.KML) }

        binding.btnImportBackup.setOnClickListener {
            pickBackup.launch(arrayOf("*/*"))
        }

        binding.textAppVersion.text = getString(R.string.app_name) + " v" + BuildConfig.VERSION_NAME

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

    private fun onBackupLocationPicked(uri: Uri) {
        val resolver = requireContext().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION

        // Release the previous URI so we don't leak slots toward Android's
        // ~128-per-app persistable-permission cap.
        val prefs = Preferences(requireContext())
        prefs.backupLocationUri?.let { previous ->
            try {
                resolver.releasePersistableUriPermission(Uri.parse(previous), flags)
            } catch (_: SecurityException) {
                // Already released or never held — nothing to do.
            }
        }

        try {
            resolver.takePersistableUriPermission(uri, flags)
        } catch (_: SecurityException) {
            Snackbar.make(
                binding.root,
                R.string.export_success_backup_revoked,
                Snackbar.LENGTH_LONG
            ).show()
            return
        }

        prefs.backupLocationUri = uri.toString()
        renderBackupLocationRow()
    }

    private fun clearBackupLocation() {
        val prefs = Preferences(requireContext())
        val resolver = requireContext().contentResolver
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        prefs.backupLocationUri?.let { previous ->
            try {
                resolver.releasePersistableUriPermission(Uri.parse(previous), flags)
            } catch (_: SecurityException) {
                // Permission may have already been revoked — ignore.
            }
        }
        prefs.backupLocationUri = null
        renderBackupLocationRow()
    }

    private fun renderBackupLocationRow() {
        val uriString = Preferences(requireContext()).backupLocationUri
        if (uriString.isNullOrBlank()) {
            binding.textBackupLocationSubtitle.setText(R.string.backup_location_subtitle_unset)
            binding.btnBackupLocationChoose.setText(R.string.backup_location_choose)
            binding.btnBackupLocationClear.visibility = View.GONE
        } else {
            val displayName = backupFolderDisplayName(uriString)
            binding.textBackupLocationSubtitle.text =
                getString(R.string.backup_location_subtitle_set, displayName)
            binding.btnBackupLocationChoose.setText(R.string.backup_location_change)
            binding.btnBackupLocationClear.visibility = View.VISIBLE
        }
    }

    private fun backupFolderDisplayName(uriString: String): String {
        val uri = Uri.parse(uriString)
        // DocumentFile.fromTreeUri(...).name is the user-friendly name on most
        // devices, but a few OEMs return null for the root of a tree. Fall back
        // to the last path segment, which at least carries the folder ID.
        val resolved = runCatching {
            DocumentFile.fromTreeUri(requireContext(), uri)?.name
        }.getOrNull()
        return resolved ?: uri.lastPathSegment ?: uriString
    }

    private fun startExport(format: ExportFormat) {
        val request = ExportWorker.buildRequest(requireContext(), format)
        WorkManager.getInstance(requireContext())
            .enqueue(request)

        Snackbar.make(
            binding.root,
            "Exporting as ${format.name}...",
            Snackbar.LENGTH_SHORT
        ).show()

        binding.progressExport.visibility = android.view.View.VISIBLE
        binding.progressExport.isIndeterminate = false
        binding.progressExport.progress = 0

        // Observe the work result
        WorkManager.getInstance(requireContext())
            .getWorkInfoByIdLiveData(request.id)
            .observe(viewLifecycleOwner) { workInfo ->
                when (workInfo?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = workInfo.progress.getInt(ExportWorker.KEY_PROGRESS, 0)
                        binding.progressExport.setProgressCompat(progress, true)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        binding.progressExport.visibility = android.view.View.GONE
                        val outputPath = workInfo.outputData.getString(ExportWorker.KEY_OUTPUT_URI)
                        val backupStatus = workInfo.outputData.getString(ExportWorker.KEY_BACKUP_STATUS)
                        val backupName = workInfo.outputData.getString(ExportWorker.KEY_BACKUP_NAME)
                        showExportSuccessSnackbar(outputPath, format, backupStatus, backupName)
                    }
                    WorkInfo.State.FAILED -> {
                        binding.progressExport.visibility = android.view.View.GONE
                        Snackbar.make(
                            binding.root,
                            "Export failed. Please try again.",
                            Snackbar.LENGTH_LONG
                        ).show()
                    }
                    else -> { /* pending/cancelled — no action */ }
                }
            }
    }

    private fun showExportSuccessSnackbar(
        outputPath: String?,
        format: ExportFormat,
        backupStatus: String?,
        backupName: String?
    ) {
        val fileName = outputPath?.substringAfterLast("/").orEmpty()
        val message = when (backupStatus) {
            ExportWorker.BACKUP_STATUS_SUCCESS -> {
                val savedTo = backupName ?: backupFolderDisplayName(
                    Preferences(requireContext()).backupLocationUri.orEmpty()
                )
                getString(R.string.export_success_with_backup, fileName, savedTo)
            }
            ExportWorker.BACKUP_STATUS_PERMISSION_REVOKED -> {
                // Forget the dead URI so the next export does not retry it.
                clearBackupLocation()
                getString(R.string.export_success_backup_revoked)
            }
            ExportWorker.BACKUP_STATUS_IO_ERROR ->
                getString(R.string.export_success_backup_io_error)
            else /* NOT_CONFIGURED or null */ ->
                getString(R.string.export_success, fileName)
        }

        Snackbar.make(binding.root, message, EXPORT_SUCCESS_SNACKBAR_MS)
            .setAction(R.string.export_share) { shareFile(outputPath, format) }
            .show()
    }

    private fun onBackupPicked(uri: Uri) {
        binding.progressExport.visibility = View.VISIBLE
        binding.progressExport.isIndeterminate = true

        lifecycleScope.launch {
            val staged = withContext(Dispatchers.IO) { stageBackupFile(uri) }
            if (staged == null) {
                binding.progressExport.visibility = View.GONE
                Snackbar.make(
                    binding.root,
                    "Couldn't read selected file. Try a CSV, GeoJSON, or KML file.",
                    Snackbar.LENGTH_LONG
                ).show()
                return@launch
            }
            startImport(staged)
        }
    }

    /**
     * Copy the picker's content:// stream into our cache so the worker has a
     * stable file path. We enforce the file-size cap during the copy rather
     * than relying on whatever the URI reports.
     */
    private fun stageBackupFile(uri: Uri): File? {
        val resolver = requireContext().contentResolver
        val displayName = queryDisplayName(uri) ?: "backup.bin"
        val safeName = displayName.replace(Regex("[/\\\\]"), "_")
        val importDir = File(requireContext().cacheDir, "imports").apply { mkdirs() }
        val target = File(importDir, "${System.currentTimeMillis()}_$safeName")
        return try {
            resolver.openInputStream(uri)?.use { input ->
                target.outputStream().use { output ->
                    var copied = 0L
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val n = input.read(buffer)
                        if (n == -1) break
                        copied += n
                        if (copied > ImportLimits.MAX_FILE_BYTES) {
                            target.delete()
                            return null
                        }
                        output.write(buffer, 0, n)
                    }
                }
            } ?: return null
            target
        } catch (_: Exception) {
            target.delete()
            null
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        val resolver = requireContext().contentResolver
        return resolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)
            ?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
    }

    private fun startImport(file: File) {
        val request = ImportWorker.buildRequest(file.absolutePath)
        WorkManager.getInstance(requireContext()).enqueue(request)

        binding.progressExport.visibility = View.VISIBLE
        binding.progressExport.isIndeterminate = false
        binding.progressExport.progress = 0

        WorkManager.getInstance(requireContext())
            .getWorkInfoByIdLiveData(request.id)
            .observe(viewLifecycleOwner) { info ->
                when (info?.state) {
                    WorkInfo.State.RUNNING -> {
                        val progress = info.progress.getInt(ImportWorker.KEY_PROGRESS, 0)
                        binding.progressExport.setProgressCompat(progress, true)
                    }
                    WorkInfo.State.SUCCEEDED -> {
                        binding.progressExport.visibility = View.GONE
                        file.delete()
                        val count = info.outputData.getInt(ImportWorker.KEY_IMPORTED_COUNT, 0)
                        val format = info.outputData.getString(ImportWorker.KEY_FORMAT) ?: "?"
                        Snackbar.make(
                            binding.root,
                            "Imported $count measurements from $format",
                            Snackbar.LENGTH_LONG
                        ).show()
                        loadStats()
                    }
                    WorkInfo.State.FAILED -> {
                        binding.progressExport.visibility = View.GONE
                        file.delete()
                        val message = info.outputData.getString(ImportWorker.KEY_ERROR_MESSAGE)
                            ?: "Import failed"
                        Snackbar.make(binding.root, message, Snackbar.LENGTH_LONG).show()
                    }
                    else -> { /* pending/cancelled — no action */ }
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
        if (_binding != null) renderBackupLocationRow()
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }
}
