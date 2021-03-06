package com.jdroid.android.debug.firebase.analytics

import android.content.Context
import androidx.core.util.Pair
import com.jdroid.android.debug.info.DebugInfoAppender
import com.jdroid.android.debug.info.DebugInfoHelper
import com.jdroid.android.firebase.analytics.FirebaseAnalyticsHelper
import com.jdroid.android.lifecycle.ApplicationLifecycleCallback

class FirebaseAnalyticsDebugAppLifecycleCallback : ApplicationLifecycleCallback() {

    override fun onCreate(context: Context) {
        DebugInfoHelper.addDebugInfoAppender(object : DebugInfoAppender {
            override fun getDebugInfoProperties(): List<Pair<String, Any>> {
                val properties = mutableListOf<Pair<String, Any>>()
                properties.add(
                    Pair(
                        "Firebase Analytics Enabled",
                        FirebaseAnalyticsHelper.isFirebaseAnalyticsEnabled()
                    )
                )
                return properties
            }
        })
    }
}
