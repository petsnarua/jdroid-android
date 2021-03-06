package com.jdroid.android.google.inappbilling.ui;

import android.os.Bundle;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentActivity;
import androidx.fragment.app.FragmentTransaction;

import com.jdroid.android.activity.AbstractFragmentActivity;
import com.jdroid.android.exception.DefaultExceptionHandler;
import com.jdroid.android.exception.DialogErrorDisplayer;
import com.jdroid.android.fragment.AbstractFragment;
import com.jdroid.android.google.inappbilling.InAppBillingAppModule;
import com.jdroid.android.google.inappbilling.R;
import com.jdroid.android.google.inappbilling.client.InAppBillingClient;
import com.jdroid.android.google.inappbilling.client.InAppBillingClientListener;
import com.jdroid.android.google.inappbilling.client.InAppBillingErrorCode;
import com.jdroid.android.google.inappbilling.client.Inventory;
import com.jdroid.android.google.inappbilling.client.ItemType;
import com.jdroid.android.google.inappbilling.client.Product;
import com.jdroid.java.exception.AbstractException;
import com.jdroid.java.exception.ErrorCodeException;
import com.jdroid.java.utils.LoggerUtils;

import org.slf4j.Logger;

public class InAppBillingHelperFragment extends AbstractFragment implements InAppBillingClientListener {

	private static final String SILENT_MODE = "silentMode";

	private static final Logger LOGGER = LoggerUtils.getLogger(InAppBillingHelperFragment.class);

	private InAppBillingClient inAppBillingClient;
	private Boolean silentMode;

	public static void add(FragmentActivity activity,
			Class<? extends InAppBillingHelperFragment> inAppBillingHelperFragmentClass, Boolean silentMode,
			Fragment targetFragment) {
		
		if (get(activity) == null) {
			AbstractFragmentActivity abstractFragmentActivity = (AbstractFragmentActivity)activity;
			InAppBillingHelperFragment inAppBillingHelperFragment = abstractFragmentActivity.instanceFragment(
				inAppBillingHelperFragmentClass, null);
			inAppBillingHelperFragment.setTargetFragment(targetFragment, 0);

			Bundle args = new Bundle();
			args.putBoolean(SILENT_MODE, silentMode);
			inAppBillingHelperFragment.setArguments(args);

			FragmentTransaction fragmentTransaction = abstractFragmentActivity.getSupportFragmentManager().beginTransaction();
			fragmentTransaction.add(0, inAppBillingHelperFragment, InAppBillingHelperFragment.class.getSimpleName());
			fragmentTransaction.commit();
		}

	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		silentMode = getArgument(SILENT_MODE);
		
		inAppBillingClient = new InAppBillingClient();
		inAppBillingClient.setListener(this);
		inAppBillingClient.startSetup();
		
		// TODO To support promotion codes, your app must call the getPurchases() method whenever the app starts or resumes.
		// The simplest approach is to call getPurchases() in your activity's onResume() method, since that callback fires when the activity is created, as well as when the activity is unpaused.
	}
	
	public InAppBillingListener getInAppBillingListener() {
		return (InAppBillingListener)getTargetFragment();
	}
	
	@Override
	public void onSetupFinished() {
		inAppBillingClient.queryProductsDetails(ItemType.MANAGED);
	}

	@Override
	public void onSetupFailed(AbstractException abstractException) {
		if (!silentMode) {
			abstractException.setDescription(getString(R.string.jdroid_failedToLoadPurchases));
			createErrorDisplayer(abstractException).displayError(getActivity(), abstractException);
		}
	}
	
	@Override
	public void onQueryProductDetailsFinished(Inventory inventory) {
		inAppBillingClient.queryPurchases(ItemType.MANAGED);
		//inAppBillingClient.queryPurchases(ItemType.SUBSCRIPTION, subscriptionsProductTypes);
	}

	@Override
	public void onQueryProductDetailsFailed(ErrorCodeException errorCodeException) {
		if (!silentMode) {
			errorCodeException.setDescription(getString(R.string.jdroid_failedToLoadPurchases));
			createErrorDisplayer(errorCodeException).displayError(getActivity(), errorCodeException);
		}
	}

	@Override
	public void onQueryPurchasesFinished(Inventory inventory) {
		InAppBillingListener inAppBillingListener = getInAppBillingListener();
		if (inAppBillingListener != null) {
			inAppBillingListener.onProductsLoaded(inventory.getAvailableProducts());
		}
	}

	@Override
	public void onQueryPurchasesFailed(ErrorCodeException errorCodeException) {
		if (!silentMode) {
			errorCodeException.setDescription(getString(R.string.jdroid_failedToLoadPurchases));
			createErrorDisplayer(errorCodeException).displayError(getActivity(), errorCodeException);
		}
	}

	public void launchPurchaseFlow(Product product) {
		inAppBillingClient.launchInAppPurchaseFlow(getActivity(), product);
		InAppBillingAppModule.get().getModuleAnalyticsSender().trackInAppBillingPurchaseTry(product);
	}

	@Override
	public void onPurchaseFinished(Product product) {
		InAppBillingListener inAppBillingListener = getInAppBillingListener();
		if (inAppBillingListener != null) {
			inAppBillingListener.onPurchased(product);
		}
	}

	@Override
	public void onPurchaseFailed(ErrorCodeException errorCodeException) {
		if (!silentMode && !DefaultExceptionHandler.matchAnyErrorCode(errorCodeException, InAppBillingErrorCode.USER_CANCELED)) {
			DialogErrorDisplayer.Companion.markAsNotGoBackOnError(errorCodeException);
			createErrorDisplayer(errorCodeException).displayError(getActivity(), errorCodeException);
		}
	}

	@Override
	public void onConsumeFinished(Product product) {
		InAppBillingListener inAppBillingListener = getInAppBillingListener();
		if (inAppBillingListener != null) {
			inAppBillingListener.onConsumed(product);
		}
	}

	@Override
	public void onConsumeFailed(ErrorCodeException errorCodeException) {
		if (!silentMode) {
			createErrorDisplayer(errorCodeException).displayError(getActivity(), errorCodeException);
		}
	}

	@Override
	public void onProvideProduct(Product product) {
		InAppBillingListener inAppBillingListener = getInAppBillingListener();
		if (inAppBillingListener != null) {
			inAppBillingListener.onProvideProduct(product);
		}
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		if (inAppBillingClient != null) {
			inAppBillingClient.onDestroy();
			inAppBillingClient = null;
		}
	}

	public static InAppBillingHelperFragment get(FragmentActivity activity) {
		return ((AbstractFragmentActivity)activity).getFragment(InAppBillingHelperFragment.class);
	}

	public static void removeTarget(FragmentActivity activity) {
		InAppBillingHelperFragment inAppBillingHelperFragment = InAppBillingHelperFragment.get(activity);
		if (inAppBillingHelperFragment != null) {
			inAppBillingHelperFragment.setTargetFragment(null, 0);
		}
	}
}
