package com.manimarank.websitemonitor

import android.app.Application
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import com.manimarank.websitemonitor.utils.Constants
import com.manimarank.websitemonitor.utils.SharedPrefsManager
import com.manimarank.websitemonitor.utils.Utils

class MyApplication : Application(), DefaultLifecycleObserver {

    object ActivityVisibility {
        var appIsVisible: Boolean = false
        @JvmStatic
        fun resumeApp() { appIsVisible = true }
        @JvmStatic
        fun pauseApp() { appIsVisible = false }
    }

    override fun onCreate() {
        super<Application>.onCreate()
        SharedPrefsManager.init(this)
        Utils.enableDarkMode(SharedPrefsManager.customPrefs.getBoolean(Constants.IS_DARK_MODE_ENABLED, false))
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        ActivityVisibility.resumeApp()
    }

    override fun onStop(owner: LifecycleOwner) {
        ActivityVisibility.pauseApp()
    }
}
