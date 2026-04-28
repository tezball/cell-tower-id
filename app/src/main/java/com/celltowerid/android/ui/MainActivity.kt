package com.celltowerid.android.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.celltowerid.android.R
import com.celltowerid.android.data.AppDatabase
import com.celltowerid.android.databinding.ActivityMainBinding
import com.celltowerid.android.export.RetentionCleanupWorker
import com.celltowerid.android.repository.AnomalyRepository
import com.celltowerid.android.repository.MeasurementRepository
import com.celltowerid.android.repository.TowerCacheRepository
import com.celltowerid.android.service.CollectionService
import com.celltowerid.android.ui.viewmodel.CollectionViewModel
import com.celltowerid.android.util.Preferences
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    companion object {
        // Pure permission policy. Extracted so cross-OS-version expectations can
        // be unit-tested without spinning up the full activity.
        internal fun requiredPermissionsForSdk(sdkInt: Int): List<String> {
            val perms = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
                perms.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            return perms
        }

        // Background location is a separate post-foreground request only on Android Q+.
        internal fun shouldRequestBackgroundLocation(
            sdkInt: Int,
            backgroundLocationGranted: Boolean
        ): Boolean = sdkInt >= Build.VERSION_CODES.Q && !backgroundLocationGranted
    }

    private lateinit var binding: ActivityMainBinding
    private val collectionViewModel: CollectionViewModel by viewModels()
    private var isCollecting = false

    private val requiredPermissions: List<String> by lazy {
        requiredPermissionsForSdk(Build.VERSION.SDK_INT)
    }

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val locationGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
        if (locationGranted) {
            requestBackgroundLocationIfNeeded()
        } else {
            Snackbar.make(
                binding.root,
                R.string.permission_location_required,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    private val backgroundLocationLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Snackbar.make(
                binding.root,
                R.string.background_location_rationale,
                Snackbar.LENGTH_LONG
            ).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!Preferences(this).onboardingComplete) {
            startActivity(Intent(this, OnboardingActivity::class.java))
            finish()
            return
        }

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        setSupportActionBar(binding.toolbar)

        val navHostFragment = supportFragmentManager
            .findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        binding.bottomNav.setupWithNavController(navController)

        binding.fabCollect.setOnClickListener { toggleCollection() }

        collectionViewModel.isCollecting.observe(this) { collecting ->
            isCollecting = collecting
            binding.fabCollect.setImageResource(
                if (collecting) android.R.drawable.ic_media_pause
                else android.R.drawable.ic_media_play
            )
            binding.fabCollect.contentDescription = getString(
                if (collecting) R.string.stop_collection
                else R.string.start_collection
            )
        }

        requestPermissions()

        lifecycleScope.launch {
            val db = AppDatabase.getInstance(applicationContext)
            val towerRepo = TowerCacheRepository(db.towerCacheDao())
            val anomalyRepo = AnomalyRepository(db.anomalyDao())
            val measurementRepo = MeasurementRepository(db.measurementDao())

            anomalyRepo.deleteDuplicateCellAnomalies()

            // One-shot cleanup: prior versions shipped a seeded OpenCelliD
            // tower list and an UNKNOWN_TOWER anomaly type. Both are gone now;
            // purge the legacy data so stale rows don't surface in the UI.
            anomalyRepo.deleteByType("UNKNOWN_TOWER")
            towerRepo.deleteBySource("opencellid")

            val days = Preferences(this@MainActivity).retentionDays
            if (days > 0) {
                val cutoffMs = System.currentTimeMillis() - days * 86_400_000L
                measurementRepo.deleteOlderThan(cutoffMs)
                anomalyRepo.deleteOlderThan(cutoffMs)
            }
        }

        RetentionCleanupWorker.enqueue(applicationContext)
    }

    private fun requestPermissions() {
        val needed = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) {
            permissionLauncher.launch(needed.toTypedArray())
        } else {
            requestBackgroundLocationIfNeeded()
        }
    }

    private fun requestBackgroundLocationIfNeeded() {
        val granted = ContextCompat.checkSelfPermission(
            this, Manifest.permission.ACCESS_BACKGROUND_LOCATION
        ) == PackageManager.PERMISSION_GRANTED
        if (shouldRequestBackgroundLocation(Build.VERSION.SDK_INT, granted)) {
            MaterialAlertDialogBuilder(this)
                .setTitle(R.string.background_location_title)
                .setMessage(R.string.background_location_disclosure)
                .setPositiveButton(R.string.background_location_allow) { _, _ ->
                    backgroundLocationLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                }
                .setNegativeButton(R.string.background_location_not_now) { _, _ -> }
                .setCancelable(false)
                .show()
        }
    }

    private fun toggleCollection() {
        if (isCollecting) {
            stopCollection()
        } else {
            startCollection()
        }
    }

    private fun startCollection() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions()
            return
        }

        launchCollectionService()
    }

    private fun launchCollectionService() {
        val interval = collectionViewModel.collectionInterval.value ?: CollectionService.DEFAULT_INTERVAL_MS
        val intent = CollectionService.startIntent(this, interval)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
        collectionViewModel.setCollecting(true)
        promptBatteryOptimizationIfNeeded()
    }

    private fun promptBatteryOptimizationIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val prefs = Preferences(this)
        if (prefs.batteryOptPromptShown) return

        val pm = getSystemService(POWER_SERVICE) as PowerManager
        if (pm.isIgnoringBatteryOptimizations(packageName)) {
            // Already exempted -- record so we never re-prompt.
            prefs.batteryOptPromptShown = true
            return
        }

        prefs.batteryOptPromptShown = true
        MaterialAlertDialogBuilder(this)
            .setTitle(R.string.battery_opt_title)
            .setMessage(R.string.battery_opt_message)
            .setPositiveButton(R.string.battery_opt_open_settings) { _, _ ->
                openBatteryOptimizationSettings()
            }
            .setNegativeButton(R.string.battery_opt_skip) { _, _ -> }
            .show()
    }

    private fun openBatteryOptimizationSettings() {
        // Prefer the app-detail page (lets the user toggle just our app); fall
        // back to the general settings list if the per-app screen isn't routable.
        val appDetail = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
        try {
            startActivity(appDetail)
        } catch (_: Exception) {
            try {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            } catch (_: Exception) {
                // Some OEMs lock these intents down -- nothing we can do.
            }
        }
    }

    private fun stopCollection() {
        startService(CollectionService.stopIntent(this))
        collectionViewModel.setCollecting(false)
    }
}
