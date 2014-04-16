package com.jdroid.android.analytics;

import android.app.Activity;
import com.jdroid.android.inappbilling.Product;
import com.jdroid.android.social.AccountType;
import com.jdroid.android.social.SocialAction;
import com.jdroid.java.exception.ConnectionException;

/**
 * 
 * @author Maxi Rosson
 */
public interface AnalyticsTracker {
	
	public Boolean isEnabled();
	
	public void onActivityStart(Activity activity, AppLoadingSource appLoadingSource, Object data);
	
	public void onActivityStop(Activity activity);
	
	public void trackConnectionException(ConnectionException connectionException);
	
	public void trackHandledException(Throwable throwable);
	
	public void trackUriHandled(Boolean handled, String validUri, String invalidUri);
	
	public void trackInAppBillingPurchase(Product product);
	
	public void trackSocialInteraction(AccountType accountType, SocialAction socialAction, String socialTarget);
	
}
