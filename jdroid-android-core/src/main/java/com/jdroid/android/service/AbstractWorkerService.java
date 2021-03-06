package com.jdroid.android.service;

import android.app.IntentService;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;

import com.google.firebase.perf.metrics.Trace;
import com.jdroid.android.application.AbstractApplication;
import com.jdroid.android.firebase.performance.TraceHelper;
import com.jdroid.java.date.DateUtils;
import com.jdroid.java.utils.LoggerUtils;
import com.squareup.leakcanary.LeakCanary;

import org.slf4j.Logger;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public abstract class AbstractWorkerService extends IntentService {

	private static String TAG = AbstractWorkerService.class.getSimpleName();

	public AbstractWorkerService() {
		super(TAG);
	}

	public AbstractWorkerService(String name) {
		super(name);
	}

	@Override
	protected final void onHandleIntent(Intent intent) {
		String tag = getTag(intent);
		Logger logger = LoggerUtils.getLogger(tag);
		if (intent != null) {
			Trace trace = null;
			try {
				if (timingTrackingEnabled()) {
					trace = TraceHelper.INSTANCE.startTrace(tag);
				}
				logger.info("Executing service.");
				long startTime = DateUtils.INSTANCE.nowMillis();
				doExecute(intent);
				long executionTime = DateUtils.INSTANCE.nowMillis() - startTime;
				logger.info("Service finished. Execution time: " + DateUtils.INSTANCE.formatDuration(executionTime));

				if (trace != null) {
					trace.putAttribute("result", "success");
					trace.incrementMetric("successes", 1);
				}
			} catch (Exception e) {
				if (trace != null) {
					trace.putAttribute("result", "failure");
					trace.incrementMetric("failures", 1);
				}
				AbstractApplication.get().getExceptionHandler().logHandledException(e);
			} finally {
				if (trace != null) {
					trace.stop();
				}
			}
		} else {
			logger.warn("Null intent when starting the service: " + getClass().getName());
		}
	}

	protected Boolean timingTrackingEnabled() {
		return true;
	}

	protected String getTag(@Nullable Intent intent) {
		return getClass().getSimpleName();
	}

	protected abstract void doExecute(@NonNull Intent intent);

	@Override
	public void onDestroy() {
		super.onDestroy();

		LeakCanary.installedRefWatcher().watch(this);
	}

	public static void runIntentInService(Context context, Bundle bundle, Class<? extends AbstractWorkerService> serviceClass) {
		Intent intent = new Intent();
		intent.putExtras(bundle);
		runIntentInService(context, intent, serviceClass);
	}

	public static void runIntentInService(Context context, Intent intent, Class<? extends AbstractWorkerService> serviceClass) {
		try {
			context.startService(getServiceIntent(context, intent, serviceClass));
		} catch (Exception e) {
			AbstractApplication.get().getExceptionHandler().logHandledException(e);
		}
	}

	public static Intent getServiceIntent(Context context, Intent intent, Class<? extends AbstractWorkerService> serviceClass) {
		intent.setClass(context, serviceClass);
		return intent;
	}

}
