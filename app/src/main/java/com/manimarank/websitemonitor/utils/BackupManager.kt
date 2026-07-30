package com.manimarank.websitemonitor.utils

import android.content.Context
import com.manimarank.websitemonitor.data.db.DbHelper
import com.manimarank.websitemonitor.data.db.WebSiteEntry
import org.json.JSONArray
import org.json.JSONObject

/**
 * Exports and imports the user's data (monitored sites + settings) as a single JSON document,
 * so it can be saved to a file and restored after uninstalling / reinstalling the app.
 *
 * The JSON is written and read through the Storage Access Framework by the caller; this object
 * only handles serialization and the database/preferences round-trip.
 *
 * Format:
 * ```
 * {
 *   "version": 1,
 *   "settings": { "monitoring_interval": 15, "notify_only_server_issues": false, ... },
 *   "sites": [ { "name": "...", "url": "...", "isPaused": false, "itemPosition": 0 }, ... ]
 * }
 * ```
 */
object BackupManager {

    private const val BACKUP_VERSION = 1

    private const val KEY_VERSION = "version"
    private const val KEY_SETTINGS = "settings"
    private const val KEY_SITES = "sites"

    // Only user-facing settings are backed up. Runtime flags (scheduled, default-data-added,
    // auto-start-shown) are intentionally left out so a fresh install initialises them itself.
    private val INT_SETTINGS = listOf(Constants.MONITORING_INTERVAL)
    private val BOOL_SETTINGS = listOf(
        Constants.NOTIFY_ONLY_SERVER_ISSUES,
        Constants.IS_DARK_MODE_ENABLED
    )

    /** Serializes all monitored sites and settings into a pretty-printed JSON string. */
    suspend fun exportToJson(context: Context): String {
        val dao = DbHelper.getInstance(context)?.webSiteEntryDao()
        val sites = dao?.getAllWebSiteEntryDirectList().orEmpty()
        val prefs = SharedPrefsManager.customPrefs

        val settingsJson = JSONObject()
        INT_SETTINGS.forEach { key ->
            if (prefs.contains(key)) settingsJson.put(key, prefs.getInt(key, Constants.DEFAULT_INTERVAL_MIN))
        }
        BOOL_SETTINGS.forEach { key ->
            if (prefs.contains(key)) settingsJson.put(key, prefs.getBoolean(key, false))
        }

        val sitesJson = JSONArray()
        sites.forEach { entry ->
            val obj = JSONObject().apply {
                put("id", entry.id ?: JSONObject.NULL)
                put("name", entry.name)
                put("url", entry.url)
                put("isPaused", entry.isPaused)
                put("itemPosition", entry.itemPosition ?: JSONObject.NULL)
            }
            sitesJson.put(obj)
        }

        return JSONObject().apply {
            put(KEY_VERSION, BACKUP_VERSION)
            put(KEY_SETTINGS, settingsJson)
            put(KEY_SITES, sitesJson)
        }.toString(2)
    }

    /**
     * Restores sites and settings from a previously exported JSON string, replacing the current
     * data. Returns the number of sites imported. Throws if the JSON is not a valid backup.
     */
    suspend fun importFromJson(context: Context, json: String): Int {
        val root = JSONObject(json)

        // Restore settings.
        val settingsJson = root.optJSONObject(KEY_SETTINGS)
        if (settingsJson != null) {
            val editor = SharedPrefsManager.customPrefs.edit()
            INT_SETTINGS.forEach { key ->
                if (settingsJson.has(key)) editor.putInt(key, settingsJson.getInt(key))
            }
            BOOL_SETTINGS.forEach { key ->
                if (settingsJson.has(key)) editor.putBoolean(key, settingsJson.getBoolean(key))
            }
            editor.apply()
        }

        // Restore sites (replace everything).
        val sitesJson = root.optJSONArray(KEY_SITES) ?: JSONArray()
        val entries = ArrayList<WebSiteEntry>(sitesJson.length())
        for (i in 0 until sitesJson.length()) {
            val obj = sitesJson.getJSONObject(i)
            val name = obj.optString("name")
            val url = obj.optString("url")
            if (url.isBlank()) continue
            entries.add(
                WebSiteEntry(
                    id = if (obj.isNull("id")) null else obj.getLong("id"),
                    name = name,
                    url = url,
                    isPaused = obj.optBoolean("isPaused", false),
                    itemPosition = if (obj.isNull("itemPosition")) null else obj.optInt("itemPosition")
                )
            )
        }

        val dao = DbHelper.getInstance(context)?.webSiteEntryDao()
        dao?.clearAll()
        if (entries.isNotEmpty()) dao?.insertAll(entries)

        return entries.size
    }
}
