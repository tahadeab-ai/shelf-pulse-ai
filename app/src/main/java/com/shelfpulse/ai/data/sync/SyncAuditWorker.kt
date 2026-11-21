package com.shelfpulse.ai.data.sync

import android.content.Context
import androidx.work.*
import com.shelfpulse.ai.ShelfPulseApplication
import com.shelfpulse.ai.data.db.ShelfPulseDatabase
import com.shelfpulse.ai.data.repository.InventoryRepository
import java.util.concurrent.TimeUnit

class SyncAuditWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val syncPrefs = SyncPreferences(applicationContext)
        val config = syncPrefs.getSyncConfig()

        if (!config.enabled || config.endpointUrl.isBlank()) {
            return Result.success()
        }

        val app = applicationContext as? ShelfPulseApplication
        val repository = app?.repository ?: run {
            val db = ShelfPulseDatabase.getInstance(applicationContext)
            InventoryRepository(db.inventoryDao(), db.productCatalogDao())
        }

        val connector = HttpSyncConnector()
        val pendingSessions = repository.getPendingSyncSessions()

        var hasFailure = false

        for (session in pendingSessions) {
            val result = connector.pushSession(session, config)
            if (result.isSuccess) {
                repository.updateSyncStatus(session.id, "SYNCED", System.currentTimeMillis())
            } else {
                repository.updateSyncStatus(session.id, "FAILED", null)
                hasFailure = true
            }
        }

        return if (hasFailure) {
            Result.retry()
        } else {
            Result.success()
        }
    }

    companion object {
        const val WORK_NAME_ONE_TIME = "shelfpulse_sync_onetime"
        const val WORK_NAME_PERIODIC = "shelfpulse_sync_periodic"

        fun enqueueOneTimeSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val syncWorkRequest = OneTimeWorkRequestBuilder<SyncAuditWorker>()
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 15, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NAME_ONE_TIME,
                ExistingWorkPolicy.REPLACE,
                syncWorkRequest
            )
        }

        fun enqueuePeriodicSync(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()

            val periodicRequest = PeriodicWorkRequestBuilder<SyncAuditWorker>(15, TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME_PERIODIC,
                ExistingPeriodicWorkPolicy.KEEP,
                periodicRequest
            )
        }
    }
}
