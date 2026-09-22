package org.tinitalk.admin

import android.app.Application
import android.content.res.Configuration
import org.tinitalk.admin.i18n.AppLanguage

class AdminApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppLanguage.initialize(this)
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        AppLanguage.refresh()
    }
}
