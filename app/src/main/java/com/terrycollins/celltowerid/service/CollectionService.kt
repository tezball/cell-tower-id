package com.terrycollins.celltowerid.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.lifecycleScope
import com.terrycollins.celltowerid.data.AppDatabase
import com.terrycollins.celltowerid.domain.model.CellMeasurement
import com.terrycollins.celltowerid.domain.model.RadioType
import com.terrycollins.celltowerid.repository.AnomalyRepository
import com.terrycollins.celltowerid.repository.MeasurementRepository
import com.terrycollins.celltowerid.repository.SessionRepository
import com.terrycollins.celltowerid.repository.TowerCacheRepository
import com.terrycollins.celltowerid.util.AppLog
import com.terrycollins.celltowerid.util.Preferences
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

class CollectionService : LifecycleService() {

    companion object {
        private const val TAG = "CellTowerID.Collection"
        const val ACTION_START = "com.terrycollins.celltowerid.ACTION_START_COLLECTION"
        const val ACTION_STOP = "com.terrycollins.celltowerid.ACTION_STOP_COLLECTION"
        const val EXTRA_INTERVAL_MS = "interval_ms"
        const val DEFAULT_INTERVAL_MS = 10_000L
        const val NOTIFICATION_ID = 1
        const val CHANNEL_ID = "collection_channel"
        private const val LOW_POWER_INTERVAL_THRESHOLD_MS = 30_000L
        private const val NOTIFICATION_UPDATE_CYCLES = 6

        fun startIntent(context: Context, intervalMs: Long = DEFAULT_INTERVAL_MS): Intent {
            return Intent(context, CollectionService::class.java).apply {
                action = ACTION_START
                putExtra(EXTRA_INTERVAL_MS, intervalMs)
            }
        }

        fun stopIntent(context: Context): Intent {
            return Intent(context, CollectionService::class.java).apply {
                action = ACTION_STOP
            }
        }
    }

    private lateinit var cellInfoProvider: CellInfoProvider
    private lateinit var anomalyDetector: AnomalyDetector
    private lateinit var measurementRepository: MeasurementRepository
    private lateinit var sessionRepository: SessionRepository
    private lateinit var anomalyRepository: AnomalyRepository
    private lateinit var towerCacheRepository: TowerCacheRepository
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var batteryManager: BatteryManager

    private var sessionId: Long = -1
    private var measurementCount = 0
    private var isCollecting = false
    private var collectionJob: Job? = null

    @Volatile
    private var lastLocation: android.location.Location? = null
    private var currentIntervalMs: Long = DEFAULT_INTERVAL_MS
    private var locationUpdatesRegistered = false

    private var cyclesSinceNotifUpdate = 0
    private var lastNotifRadio: RadioType? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            try {
                result.lastLocation?.let { lastLocation = it }
            } catch (e: SecurityException) {
                AppLog.e(TAG, "locationCallback: permission revoked", e)
            }
        }
    }

    private val _isRunning = MutableLiveData(false)
    val isRunning: LiveData<Boolean> = _isRunning

    private val _lastMeasurement = MutableLiveData<CellMeasurement?>()
    val lastMeasurement: LiveData<CellMeasurement?> = _lastMeasurement

    private val _measurementCountLive = MutableLiveData(0)
    val measurementCountLive: LiveData<Int> = _measurementCountLive

    override fun onCreate() {
        super.onCreate()
        val db = AppDatabase.getInstance(applicationContext)
        cellInfoProvider = if (isEmulator()) {
            MockCellInfoProvider()
        } else {
            RealCellInfoProvider(applicationContext)
        }
        anomalyDetector = AnomalyDetector(db.towerCacheDao())
        measurementRepository = MeasurementRepository(db.measurementDao())
        sessionRepository = SessionRepository(db.sessionDao())
        anomalyRepository = AnomalyRepository(db.anomalyDao())
        towerCacheRepository = TowerCacheRepository(db.towerCacheDao())
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        batteryManager = getSystemService(Context.BATTERY_SERVICE) as BatteryManager

        // One-shot cleanup of duplicate cell anomalies accumulated before
        // we added persistent dedupe at the repository layer.
        lifecycleScope.launch { anomalyRepository.deleteDuplicateCellAnomalies() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        // A null intent means Android restarted us via START_STICKY after
        // process death. The prior code fell through the when-block silently,
        // which is dangerous: if we had been launched as a foreground service
        // we must either re-enter foreground state or stop promptly. Consult
        // persisted scan state and act accordingly.
        if (intent == null) {
            when (val decision = CollectionRestartPolicy.decide(Preferences(this), DEFAULT_INTERVAL_MS)) {
                is CollectionRestartPolicy.Decision.Resume -> {
                    AppLog.d(TAG, "sticky restart: resuming collection at ${decision.intervalMs}ms")
                    startCollection(decision.intervalMs)
                }
                CollectionRestartPolicy.Decision.Stop -> {
                    AppLog.d(TAG, "sticky restart: no active scan, stopping")
                    stopSelf(startId)
                }
            }
            return START_STICKY
        }

        when (intent.action) {
            ACTION_START -> {
                val interval = intent.getLongExtra(EXTRA_INTERVAL_MS, DEFAULT_INTERVAL_MS)
                startCollection(interval)
            }
            ACTION_STOP -> stopCollection()
        }
        return START_STICKY
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates(intervalMs: Long) {
        if (locationUpdatesRegistered && currentIntervalMs == intervalMs) return
        if (locationUpdatesRegistered) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                AppLog.e(TAG, "failed to remove previous location updates", e)
            }
            locationUpdatesRegistered = false
        }
        currentIntervalMs = intervalMs
        val useLowPower = intervalMs >= LOW_POWER_INTERVAL_THRESHOLD_MS
        val priority = if (useLowPower) Priority.PRIORITY_LOW_POWER else Priority.PRIORITY_BALANCED_POWER_ACCURACY
        val minDistance = if (useLowPower) 25f else 10f
        val request = LocationRequest.Builder(priority, intervalMs)
            .setMinUpdateDistanceMeters(minDistance)
            .setMaxUpdateDelayMillis(intervalMs * 3)
            .build()
        try {
            fusedLocationClient.requestLocationUpdates(
                request,
                locationCallback,
                android.os.Looper.getMainLooper()
            )
            locationUpdatesRegistered = true
        } catch (e: SecurityException) {
            AppLog.e(TAG, "requestLocationUpdates denied", e)
        } catch (e: Exception) {
            AppLog.e(TAG, "requestLocationUpdates failed", e)
        }
    }

    private fun startCollection(intervalMs: Long) {
        if (isCollecting) return
        isCollecting = true
        _isRunning.postValue(true)

        cyclesSinceNotifUpdate = 0
        lastNotifRadio = null

        // startForeground MUST happen early so Android's foreground-service
        // start-in-time contract is satisfied.
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, buildNotification("Starting collection..."))

        // Persist after we've entered foreground so a crash during init still
        // leaves state consistent for the next restart.
        Preferences(this).apply {
            isScanActive = true
            scanIntervalMs = intervalMs
        }

        startLocationUpdates(intervalMs)

        collectionJob = lifecycleScope.launch {
            sessionId = withContext(Dispatchers.IO) {
                sessionRepository.startSession("Collection session")
            }

            while (isActive && isCollecting) {
                collectOnce()
                delay(effectiveIntervalMs(intervalMs))
            }
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun collectOnce() {
        try {
            val location = getLastLocation()
            if (location == null) {
                AppLog.w(TAG, "collectOnce: location is null, skipping")
                return
            }
            AppLog.d(TAG, "collectOnce: location=${location.latitude},${location.longitude} acc=${location.accuracy}")

            val measurements = cellInfoProvider.getCellMeasurements(
                location.latitude,
                location.longitude,
                location.accuracy,
                location.speed.takeIf { location.hasSpeed() }
            )
            AppLog.d(TAG, "collectOnce: got ${measurements.size} measurements")

            if (measurements.isNotEmpty()) {
                withContext(Dispatchers.IO) {
                    measurementRepository.insertMeasurements(measurements, sessionId)
                }
                measurementCount += measurements.size
                _measurementCountLive.postValue(measurementCount)

                val serving = measurements.find { it.isRegistered } ?: measurements.first()
                _lastMeasurement.postValue(serving)
                AppLog.d(TAG, "collectOnce: serving=${serving.radio} rsrp=${serving.rsrp} cid=${serving.cid}")

                withContext(Dispatchers.IO) {
                    for (m in measurements) {
                        val anomalies = anomalyDetector.analyze(m)
                        for (a in anomalies) {
                            anomalyRepository.insertAnomaly(a, sessionId)
                            AppLog.d(TAG, "collectOnce: anomaly=${a.type} severity=${a.severity}")
                        }
                        // Self-learning tower cache. Runs after analyze() so detectors
                        // compare against the *prior* cached PCI/position, not the one
                        // we're about to write.
                        towerCacheRepository.recordObservation(m)
                    }
                }

                cyclesSinceNotifUpdate++
                if (cyclesSinceNotifUpdate >= NOTIFICATION_UPDATE_CYCLES || serving.radio != lastNotifRadio) {
                    updateNotification(
                        "Collecting: $measurementCount measurements | ${serving.radio.name}"
                    )
                    cyclesSinceNotifUpdate = 0
                    lastNotifRadio = serving.radio
                }
            }
        } catch (e: SecurityException) {
            AppLog.e(TAG, "collectOnce: SecurityException, stopping", e)
            stopCollection()
        } catch (e: Exception) {
            AppLog.e(TAG, "collectOnce: unexpected error", e)
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun getLastLocation(): android.location.Location? {
        // Prefer the cached value from the persistent LocationCallback —
        // the callback delivers fresh fixes via the displacement-gated request.
        lastLocation?.let { return it }

        // Bootstrap: if the callback hasn't delivered anything yet (we just
        // started), request a single high-accuracy fix to get going.
        val fresh = suspendCancellableCoroutine<android.location.Location?> { continuation ->
            try {
                fusedLocationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener { continuation.resume(it) }
                    .addOnFailureListener { continuation.resume(null) }
            } catch (e: SecurityException) {
                AppLog.e(TAG, "getCurrentLocation denied", e)
                continuation.resume(null)
            }
        }
        if (fresh != null) lastLocation = fresh
        return fresh
    }

    private fun stopCollection() {
        isCollecting = false
        collectionJob?.cancel()
        _isRunning.postValue(false)

        Preferences(this).apply {
            isScanActive = false
            scanIntervalMs = 0L
        }

        if (locationUpdatesRegistered) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                AppLog.e(TAG, "removeLocationUpdates failed", e)
            }
            locationUpdatesRegistered = false
        }

        lifecycleScope.launch {
            if (sessionId > 0) {
                sessionRepository.endSession(sessionId, measurementCount)
            }
        }

        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        if (locationUpdatesRegistered) {
            try {
                fusedLocationClient.removeLocationUpdates(locationCallback)
            } catch (e: Exception) {
                AppLog.e(TAG, "removeLocationUpdates in onDestroy failed", e)
            }
            locationUpdatesRegistered = false
        }
        super.onDestroy()
    }

    private fun isEmulator(): Boolean {
        return (Build.FINGERPRINT.startsWith("generic")
                || Build.FINGERPRINT.startsWith("unknown")
                || Build.MODEL.contains("Emulator")
                || Build.MODEL.contains("Android SDK built for x86")
                || Build.MANUFACTURER.contains("Genymotion")
                || Build.BRAND.startsWith("generic")
                || Build.DEVICE.startsWith("generic"))
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Cell Collection",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Shows when Cell Tower ID is collecting cell tower data"
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(text: String): Notification {
        val launchIntent = packageManager.getLaunchIntentForPackage(packageName)
        val contentPendingIntent = PendingIntent.getActivity(
            this,
            0,
            launchIntent ?: Intent(),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent(this),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Cell Tower ID Collection")
            .setContentText(text)
            .setSmallIcon(com.terrycollins.celltowerid.R.drawable.ic_notification_tower)
            .setContentIntent(contentPendingIntent)
            .setOngoing(true)
            .addAction(
                android.R.drawable.ic_media_pause,
                "Stop",
                stopPendingIntent
            )
            .build()
    }

    private fun updateNotification(text: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.notify(NOTIFICATION_ID, buildNotification(text))
    }

    private fun effectiveIntervalMs(baseMs: Long): Long {
        return CollectionPowerPolicy.effectiveIntervalMs(
            baseMs = baseMs,
            powerSaverEnabled = Preferences(this).powerSaverEnabled,
            speedMps = lastLocation?.takeIf { it.hasSpeed() }?.speed,
            batteryCapacity = batteryManager.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY),
            isCharging = batteryManager.isCharging
        )
    }
}
