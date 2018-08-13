package com.jdroid.android.firebase.fcm;

import android.content.Context;
import android.support.v4.util.Pair;

import com.jdroid.android.debug.DebugSettingsHelper;
import com.jdroid.android.debug.info.DebugInfoAppender;
import com.jdroid.android.debug.info.DebugInfoHelper;
import com.jdroid.android.lifecycle.ApplicationLifecycleCallback;
import com.jdroid.java.collections.Lists;
import com.jdroid.java.concurrent.ExecutorUtils;

import java.util.List;
import java.util.Map;

public abstract class AbstractFcmDebugAppLifecycleCallback extends ApplicationLifecycleCallback {
	
	@Override
	public void onProviderInit(Context context) {
		DebugSettingsHelper.addPreferencesAppender(new FcmDebugPrefsAppender(getFcmMessagesMap()));
	}
	
	@Override
	public void onCreate(Context context) {
		DebugInfoHelper.addDebugInfoAppender(new DebugInfoAppender() {
			@Override
			public List<Pair<String, Object>> getDebugInfoProperties() {
				List<Pair<String, Object>> properties = Lists.newArrayList();
				properties.add(new Pair<>("Instance ID", InstanceIdHelper.getInstanceId()));
				return properties;
			}
		});
		// Load properties on a worker thread
		ExecutorUtils.execute(new Runnable() {
			@Override
			public void run() {
				InstanceIdHelper.getInstanceId();
			}
		});
	}
	
	protected abstract Map<FcmMessage, Map<String, String>> getFcmMessagesMap();
	
}
