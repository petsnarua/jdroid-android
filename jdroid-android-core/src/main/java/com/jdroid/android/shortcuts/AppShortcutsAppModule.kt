package com.jdroid.android.shortcuts

import com.jdroid.android.application.AbstractAppModule
import com.jdroid.android.application.AbstractApplication
import com.jdroid.android.shortcuts.analytics.AppShortcutsAnalyticsSender
import com.jdroid.android.shortcuts.analytics.AppShortcutsAnalyticsTracker
import com.jdroid.android.shortcuts.analytics.FirebaseAppShortcutsAnalyticsTracker
import com.jdroid.java.analytics.AnalyticsSender
import com.jdroid.java.analytics.AnalyticsTracker

class AppShortcutsAppModule : AbstractAppModule() {

    companion object {

        val MODULE_NAME: String = AppShortcutsAppModule::class.java.name

        fun get(): AppShortcutsAppModule {
            return AbstractApplication.get().getAppModule(MODULE_NAME) as AppShortcutsAppModule
        }
    }

    @Suppress("UNCHECKED_CAST")
    override fun createModuleAnalyticsSender(analyticsTrackers: List<AnalyticsTracker>): AnalyticsSender<out AnalyticsTracker> {
        return AppShortcutsAnalyticsSender(analyticsTrackers as List<AppShortcutsAnalyticsTracker>)
    }

    override fun createModuleAnalyticsTrackers(): List<AnalyticsTracker> {
        return listOf(FirebaseAppShortcutsAnalyticsTracker())
    }

    override fun getModuleAnalyticsSender(): AppShortcutsAnalyticsSender {
        return super.getModuleAnalyticsSender() as AppShortcutsAnalyticsSender
    }
}
