package com.manimarank.websitemonitor.worker

import android.content.Context
import androidx.work.*
import com.manimarank.websitemonitor.utils.Constants
import com.manimarank.websitemonitor.utils.Constants.DEFAULT_INTERVAL_MIN
import com.manimarank.websitemonitor.utils.Constants.TAG_WORK_MANAGER
import com.manimarank.websitemonitor.utils.SharedPrefsManager
import com.manimarank.websitemonitor.utils.SharedPrefsManager.get
import com.manimarank.websitemonitor.utils.Utils
import java.util.concurrent.TimeUnit

object WorkManagerScheduler {

    fun refreshPeriodicWork(context: Context) {

        //define constraints
        val myConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .build()

        val refreshCpnWork = PeriodicWorkRequest
            .Builder(SyncWorker::class.java, Utils.getMonitorInterval(), TimeUnit.MINUTES)
            .setConstraints(myConstraints)
            .addTag(TAG_WORK_MANAGER)
            .build()


        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            TAG_WORK_MANAGER,
            // UPDATE (WorkManager 2.8+) applies the new interval without cancelling and
            // recreating the request, so a pending cycle isn't dropped. REPLACE is deprecated.
            ExistingPeriodicWorkPolicy.UPDATE, refreshCpnWork
        )

    }
}