package com.manimarank.websitemonitor.utils

object Constants {
    const val INTENT_OBJECT = "intent_object"
    const val INTENT_CREATE_ENTRY = 1
    const val INTENT_UPDATE_ENTRY = 2
    const val DEFAULT_INTERVAL_MIN = 15

    const val IS_SCHEDULED: String = "is_scheduled"
    const val TAG_GLOBAL: String = "Website Monitor ##--> "
    const val TAG_WORK_MANAGER: String = "WebSiteMonitorWorkManager"

    const val NOTIFICATION_CHANNEL_ID = "WEB_SITE_MONITOR_CHANNEL_ID"
    const val NOTIFICATION_CHANNEL_NAME = "Web Site Monitor"
    const val NOTIFICATION_CHANNEL_DESCRIPTION = "Notification channel for monitoring web sites. In case of any site failed then showing notification."

    // Low-importance channel for the ongoing notification shown while the failure alarm plays.
    // The audible part is the alarm clip itself, so this channel is intentionally silent.
    const val ALARM_NOTIFICATION_CHANNEL_ID = "WEB_SITE_MONITOR_ALARM_CHANNEL_ID"
    const val ALARM_NOTIFICATION_CHANNEL_NAME = "Website monitor alerts"
    const val ALARM_NOTIFICATION_ID = 424242

    const val IS_ADDED_DEFAULT_DATA: String = "is_added_default_data"
    const val MONITORING_INTERVAL: String = "monitoring_interval"
    const val IS_AUTO_START_SHOWN : String = "is_auto_start_shown"
    const val NOTIFY_ONLY_SERVER_ISSUES : String = "notify_only_server_issues"
    const val IS_DARK_MODE_ENABLED : String = "is_dark_mode_enabled"
}