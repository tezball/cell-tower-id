package com.celltowerid.android.export

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequest
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.celltowerid.android.data.AppDatabase
import com.celltowerid.android.repository.AnomalyRepository
import com.celltowerid.android.repository.MeasurementRepository
import com.celltowerid.android.util.Preferences
import java.util.concurrent.TimeUnit

class RetentionCleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val UNIQUE_WORK_NAME = "retention_cleanup"
        private const val DAY_MS = 86_400_000L

        fun buildRequest(): PeriodicWorkRequest =
            PeriodicWorkRequestBuilder<RetentionCleanupWorker>(1, TimeUnit.DAYS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(true)
                        .build()
                )
                .build()

        fun enqueue(context: Context) {
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                buildRequest()
            )
        }

        suspend fun run(
            retentionDays: Int,
            measurementRepo: MeasurementRepository,
            anomalyRepo: AnomalyRepository,
            nowMs: Long
        ) {
            if (retentionDays <= 0) return
            val cutoffMs = nowMs - retentionDays * DAY_MS
            measurementRepo.deleteOlderThan(cutoffMs)
            anomalyRepo.deleteOlderThan(cutoffMs)
        }
    }

    override suspend fun doWork(): Result {
        val retentionDays = Preferences(applicationContext).retentionDays
        val db = AppDatabase.getInstance(applicationContext)
        run(
            retentionDays = retentionDays,
            measurementRepo = MeasurementRepository(db.measurementDao()),
            anomalyRepo = AnomalyRepository(db.anomalyDao()),
            nowMs = System.currentTimeMillis()
        )
        return Result.success()
    }
}
