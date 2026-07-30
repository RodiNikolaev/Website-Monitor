package com.manimarank.websitemonitor.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import com.manimarank.websitemonitor.R

/**
 * Plays the bundled failure alert audio ([R.raw.failure_alert]) once when a website check fails.
 *
 * The clip is played with [AudioAttributes.USAGE_ALARM] so it is audible even while other
 * media is silent, and it is released automatically once it finishes. Any previous playback is
 * stopped before a new one starts, so overlapping check cycles never stack players.
 */
object AlarmSoundPlayer {

    private var mediaPlayer: MediaPlayer? = null

    /**
     * Plays the failure alert once, from start to finish. Safe to call from a background worker.
     */
    @Synchronized
    fun playFailureAlarm(context: Context) {
        stop()

        try {
            val afd = context.applicationContext.resources.openRawResourceFd(R.raw.failure_alert)
                ?: return
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
}
