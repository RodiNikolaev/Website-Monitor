package com.manimarank.websitemonitor.utils

import android.content.Context
import android.database.ContentObserver
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import com.manimarank.websitemonitor.R

/**
 * Plays the bundled failure alert audio ([R.raw.failure_alert]) once when a website check fails.
 *
 * The clip is played with [AudioAttributes.USAGE_ALARM] so it is audible even while other
 * media is silent, and it is released automatically once it finishes. Any previous playback is
 * stopped before a new one starts, so overlapping check cycles never stack players.
 *
 * Playback is interrupted early when:
 *  - the app is brought to the foreground (see `MyApplication.onStart`), or
 *  - the user presses a volume key (tracked via a [ContentObserver] on the alarm stream volume).
 */
object AlarmSoundPlayer {

    private var mediaPlayer: MediaPlayer? = null

    private var appContext: Context? = null
    private var audioManager: AudioManager? = null
    private var volumeObserver: ContentObserver? = null
    private var lastAlarmVolume: Int = -1

    /**
     * Plays the failure alert once, from start to finish. Safe to call from a background worker.
     */
    @Synchronized
    fun playFailureAlarm(context: Context) {
        stop()

        val ctx = context.applicationContext
        appContext = ctx

        try {
            val afd = ctx.resources.openRawResourceFd(R.raw.failure_alert) ?: return
            mediaPlayer = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                afd.use { setDataSource(it.fileDescriptor, it.startOffset, it.length) }
                isLooping = false
                setOnCompletionListener { stop() }
                setOnErrorListener { _, _, _ ->
                    stop()
                    true
                }
                prepare()
                start()
            }
            registerVolumeObserver(ctx)
        } catch (e: Exception) {
            Print.log("AlarmSoundPlayer failed to play: $e")
            stop()
        }
    }

    /**
     * Stops and releases any active playback. Safe to call multiple times.
     */
    @Synchronized
    fun stop() {
        unregisterVolumeObserver()
        try {
            mediaPlayer?.let { player ->
                if (player.isPlaying) player.stop()
                player.release()
            }
        } catch (e: Exception) {
            Print.log("AlarmSoundPlayer failed to stop: $e")
        } finally {
            mediaPlayer = null
        }
    }

    /**
     * Stops the alarm as soon as any volume key changes the alarm stream volume. The alert plays
     * while the app is backgrounded, so no Activity is available to catch key events directly —
     * observing the system volume works regardless of which component (if any) is in focus.
     */
    private fun registerVolumeObserver(context: Context) {
        val am = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager ?: return
        audioManager = am
        lastAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)

        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                val current = audioManager?.getStreamVolume(AudioManager.STREAM_ALARM) ?: return
                if (current != lastAlarmVolume) {
                    lastAlarmVolume = current
                    stop()
                }
            }
        }
        volumeObserver = observer
        context.contentResolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
    }

    private fun unregisterVolumeObserver() {
        volumeObserver?.let { observer ->
            try {
                appContext?.contentResolver?.unregisterContentObserver(observer)
            } catch (e: Exception) {
                Print.log("AlarmSoundPlayer failed to unregister volume observer: $e")
            }
        }
        volumeObserver = null
        audioManager = null
    }
}
