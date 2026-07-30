package com.manimarank.websitemonitor.worker


import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.ForegroundInfo
import androidx.work.WorkerParameters
import com.manimarank.websitemonitor.R
import com.manimarank.websitemonitor.data.repository.WebSiteEntryRepository
import com.manimarank.websitemonitor.ui.home.MainActivity
import com.manimarank.websitemonitor.utils.AlarmSoundPlayer
import com.manimarank.websitemonitor.utils.Constants
import com.manimarank.websitemonitor.utils.Print
import com.manimarank.websitemonitor.utils.Utils
import com.manimarank.websitemonitor.utils.Utils.getStringNotWorking
import com.manimarank.websitemonitor.utils.Utils.joinToStringDescription


class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    CoroutineWorker(appContext, workerParams) {
    private lateinit var repository: WebSiteEntryRepository

    override suspend fun doWork(): Result {
        /*
          Running according to set interval.
          Shows Notifications for failed Websites, when App is in Background.
        */

        if (Utils.appIsVisible()) {
            /*
              We only want to send notifications, when App is in Background.
            */
            return Result.success()
        }

        val applicationContext: Context = applicationContext

        repository = WebSiteEntryRepository(applicationContext)

        Print.log("Fetching Data from Remote hosts...")
        return try {
            val entriesWithFailedConnection = repository.checkWebSiteStatus().filter { Utils.mayNotifyStatusFailure(it.status) }
            if (entriesWithFailedConnection.size == 1) {
                val entryWithFailedConnection = entriesWithFailedConnection.first()
                Utils.showNotification(
                    applicationContext,
                    entryWithFailedConnection.name,
                    applicationContext.getStringNotWorking(entryWithFailedConnection.url)
                )
            } else if (entriesWithFailedConnection.size > 1){
                Utils.showNotification(
                    applicationContext,
                    applicationContext.getString(R.string.several_websites_not_reachable),
                    entriesWithFailedConnection.joinToStringDescription()
                )
            }
            if (entriesWithFailedConnection.isNotEmpty()) {
                // Promote to a foreground service for the duration of playback. Without this the
                // worker would return immediately, the process could be killed, and the long alarm
                // clip would be cut off. As a foreground media-playback service the OS keeps us
                // alive until the sound finishes (or the user/app interrupts it).
                runAsForeground(applicationContext)
                AlarmSoundPlayer.playFailureAlarm(applicationContext)
            }
            Result.success()
        } catch (e: Throwable) {
            e.printStackTrace()
            Print.log("Error fetching data : $e")
            Result.failure()
        }
    }

    private suspend fun runAsForeground(context: Context) {
        try {
            setForeground(createAlarmForegroundInfo(context))
        } catch (e: Exception) {
            // If the OS refuses the foreground promotion (rare), fall back to best-effort playback.
            Print.log("Unable to run alarm as foreground service: $e")
        }
    }

    private fun createAlarmForegroundInfo(context: Context): ForegroundInfo {
        val notificationManager =
            context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                Constants.ALARM_NOTIFICATION_CHANNEL_ID,
                Constants.ALARM_NOTIFICATION_CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                // The alarm clip itself is the audible part; keep the notification silent.
                setSound(null, null)
                enableVibration(false)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val contentIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }
        val pendingIntent = PendingIntent.getActivity(
            context,
            0,
            contentIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification: Notification =
            NotificationCompat.Builder(context, Constants.ALARM_NOTIFICATION_CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_alert)
                .setContentTitle(context.getString(R.string.app_name))
                .setContentText(context.getString(R.string.several_websites_not_reachable))
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setSilent(true)
                .build()

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ForegroundInfo(
                Constants.ALARM_NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
            )
        } else {
            ForegroundInfo(Constants.ALARM_NOTIFICATION_ID, notification)
        }
    }
}
