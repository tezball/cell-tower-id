package com.terrycollins.celltowerid.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.setupWithNavController
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.terrycollins.celltowerid.R
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.databinding.ActivityMainBinding
import com.terrycollins.celltowerid.export.RetentionCleanupWorker
import com.terrycollins.celltowerid.repository.AnomalyRepository
import com.terrycollins.celltowerid.repository.MeasurementRepository
import com.terrycollins.celltowerid.repository.TowerCacheRepository
import com.terrycollins.celltowerid.service.CollectionService
import com.terrycollins.celltowerid.ui.viewmodel.CollectionViewModel
import com.terrycollins.celltowerid.util.OpenCellIdImporter
import com.terrycollins.celltowerid.util.Preferences
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private val collectionViewModel: CollectionViewModel by viewModels()
    private var isCollecting = false

    private val requiredPermissions = mutableListOf(
        Manifest.permission.ACCESS_FINE_LOCATION,
        Manifest.permission.ACCESS_COARSE_LOCATION
    ).apply {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            add(Manifest.permission.POST_NOTIFICATIONS)
        }
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
            OpenCellIdImporter.importIfEmpty(applicationContext, towerRepo)

            val anomalyRepo = AnomalyRepository(db.anomalyDao())
            val measurementRepo = MeasurementRepository(db.measurementDao())

            anomalyRepo.deleteDuplicateCellAnomalies()

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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_BACKGROUND_LOCATION)
            != PackageManager.PERMISSION_GRANTED
        ) {
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
    }

    private fun stopCollection() {
        startService(CollectionService.stopIntent(this))
        collectionViewModel.setCollecting(false)
    }
}
