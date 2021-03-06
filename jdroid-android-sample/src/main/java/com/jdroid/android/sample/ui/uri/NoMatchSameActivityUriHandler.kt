package com.jdroid.android.sample.ui.uri

import android.app.Activity
import android.content.Intent
import android.net.Uri

import com.jdroid.android.uri.AbstractUriHandler

class NoMatchSameActivityUriHandler : AbstractUriHandler<NoMatchSameActivity>() {
    override fun matches(uri: Uri): Boolean {
        return false
    }

    override fun createDefaultIntent(activity: Activity, uri: Uri): Intent? {
        return Intent(activity, NoMatchSameActivity::class.java)
    }
}
