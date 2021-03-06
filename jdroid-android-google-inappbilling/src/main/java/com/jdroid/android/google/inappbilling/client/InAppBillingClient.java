package com.jdroid.android.google.inappbilling.client;

import android.app.Activity;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.ConsumeResponseListener;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;
import com.jdroid.android.application.AbstractApplication;
import com.jdroid.android.google.inappbilling.InAppBillingAppModule;
import com.jdroid.android.utils.LocalizationUtils;
import com.jdroid.java.exception.ErrorCodeException;
import com.jdroid.java.exception.UnexpectedException;
import com.jdroid.java.utils.LoggerUtils;
import com.jdroid.java.utils.StringUtils;

import org.json.JSONException;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Provides convenience methods for in-app billing. You can create one instance of this class for your application and
 * use it to process in-app billing operations. It provides asynchronous (non-blocking)
 * methods for many common in-app billing operations, as well as automatic signature verification.
 * <p>
 * After instantiating, you must perform setup in order to start using the object. To perform setup, call the
 * {@link #startSetup} method and provide a listener; that listener will be notified when setup is complete, after which
 * (and not before) you may call other methods.
 * <p>
 * After setup is complete, you will typically want to request an inventory of owned items and subscriptions. See
 * {@link #queryPurchases(ItemType)} and related methods.
 * 
 * A note about threading: When using this object from a background thread, you may call the blocking versions of
 * methods; when using from a UI thread, call only the asynchronous versions and handle the results via callbacks. Also,
 * notice that you can only call one asynchronous operation at a time; attempting to start a second asynchronous
 * operation while the first one has not yet completed will result in an exception being thrown.
 */
public class InAppBillingClient implements PurchasesUpdatedListener {
	
	private final static Logger LOGGER = LoggerUtils.getLogger(InAppBillingClient.class);
	
	private BillingClient billingClient;
	
	// True if billing service is connected now.
	private boolean isServiceConnected;
	
	private Integer billingClientResponseCode;
	
	private Set<String> tokensToBeConsumed;
	
	// Are subscriptions supported?
	private Boolean subscriptionsSupported;
	
	// Public key for verifying signature, in base64 encoding
	private String signatureBase64;
	
	// TODO Maybe we should have a register/unregister logic like usecases. What happen if the listener is invoked when the app is on background?
	private InAppBillingClientListener listener;
	
	private Inventory inventory = new Inventory();
	
	/**
	 * Creates an instance. After creation, it will not yet be ready to use. You must perform setup by calling
	 * {@link #startSetup} and wait for setup to complete. This constructor does not block and is safe to call from a UI
	 * thread.
	 */
	public InAppBillingClient() {
		signatureBase64 = InAppBillingAppModule.get().getInAppBillingContext().getGooglePlayPublicKey();
	}
	
	public void setListener(InAppBillingClientListener listener) {
		this.listener = listener;
	}

	/**
	 * Starts the setup process. This will start up the setup process asynchronously. You will be notified through the
	 * listener when the setup process is complete.
	 */
	@MainThread
	public void startSetup() {
		try {
			LOGGER.debug("Starting in-app billing setup.");

			billingClient = BillingClient.newBuilder(AbstractApplication.get()).setListener(this).build();
			
			Runnable runnable = new Runnable() {
				@Override
				public void run() {
					LOGGER.debug("In-app billing setup successful.");
					
					for (ProductType productType : InAppBillingAppModule.get().getInAppBillingContext().getManagedProductTypes()) {
						inventory.addProduct(new Product(productType));
					}

					if (isSubscriptionsSupported()) {
						for (ProductType productType : InAppBillingAppModule.get().getInAppBillingContext().getSubscriptionsProductTypes()) {
							inventory.addProduct(new Product(productType));
						}
					}
					
					if (listener != null) {
						listener.onSetupFinished();
					}
				}
			};

			if (isServiceConnected) {
				runnable.run();
			} else {
				startServiceConnection(runnable);
			}
			
		} catch (Exception e) {
			AbstractApplication.get().getExceptionHandler().logHandledException(e);
			if (listener != null) {
				listener.onSetupFailed(new UnexpectedException(e));
			}
		}
	}
	
	private void startServiceConnection(Runnable executeOnSuccess) {
		billingClient.startConnection(new BillingClientStateListener() {
			@Override
			public void onBillingSetupFinished(@BillingClient.BillingResponse int billingResponseCode) {
				
				if (billingResponseCode == BillingClient.BillingResponse.OK) {
					isServiceConnected = true;
					if (executeOnSuccess != null) {
						executeOnSuccess.run();
					}
				} else {
					LOGGER.debug("Start connection failed. Response code: " + billingResponseCode);
					// TODO Is this possible ???
				}
				billingClientResponseCode = billingResponseCode;
			}
			
			@Override
			public void onBillingServiceDisconnected() {
				isServiceConnected = false;
			}
		});
	}
	
	private void executeServiceRequest(Runnable runnable) {
		if (isServiceConnected) {
			runnable.run();
		} else {
			// If billing service was disconnected, we try to reconnect 1 time.
			// TODO (feel free to introduce your retry policy here).
			// For example, the Play Billing Library client may lose its connection if the Play Store service is updating in the
			// background.
			startServiceConnection(runnable);
		}
	}

	/**
	 * Returns the value Billing client response code or null if the client connection response was not received yet.
	 */
	public int getBillingClientResponseCode() {
		return billingClientResponseCode;
	}
	
	public void onDestroy() {
		LOGGER.debug("Destroying billing client.");
		if (billingClient != null && billingClient.isReady()) {
			billingClient.endConnection();
			billingClient = null;
		}
	}
	
	@MainThread
	public void queryPurchases(@NonNull ItemType itemType) {
		executeServiceRequest(new Runnable() {
			@Override
			public void run() {
				
				if (ItemType.SUBSCRIPTION.equals(itemType) && !isSubscriptionsSupported()) {
					LOGGER.debug("Query product details failed. Subscriptions not supported.");
					if (listener != null) {
						listener.onPurchaseFailed(InAppBillingErrorCode.SUBSCRIPTIONS_NOT_AVAILABLE.newErrorCodeException());
					}
					return;
				}
				
				LOGGER.debug("Querying owned items. Item type: " + itemType);
				try {
					
					Purchase.PurchasesResult purchasesResult = billingClient.queryPurchases(itemType.getType());
					InAppBillingErrorCode inAppBillingErrorCode = InAppBillingErrorCode.findByErrorResponseCode(purchasesResult.getResponseCode());
					if (inAppBillingErrorCode == null) {
						for (Purchase purchase : purchasesResult.getPurchasesList()) {
							String purchaseJson = purchase.getOriginalJson();
							String signature = purchase.getSignature();
							String productId = purchase.getSku();
							
							Product product = inventory.getProduct(productId);
							
							// TODO Call to onPurchasesUpdated ?. See duplicated logic
							
							if (product != null) {
								try {
									LOGGER.debug("Setting purchase to product: " + productId + ". " + purchaseJson);
									product.setPurchase(signatureBase64, purchaseJson, signature, InAppBillingAppModule.get().getDeveloperPayloadVerificationStrategy());
								} catch (ErrorCodeException e) {
									AbstractApplication.get().getExceptionHandler().logHandledException(e);
								}
							} else {
								AbstractApplication.get().getExceptionHandler().logWarningException(
										"The purchased product [" + productId + "] is not supported by the app, so it is ignored");
							}
						}
						
						for (Product each : inventory.getProductsWaitingToConsume()) {
							consume(each);
						}
						
						InAppBillingAppModule.get().getInAppBillingContext().setPurchasedProductTypes(inventory);
						if (listener != null) {
							listener.onQueryPurchasesFinished(inventory);
						}
					} else {
						ErrorCodeException errorCodeException = inAppBillingErrorCode.newErrorCodeException("getPurchases() failed querying " + itemType);
						AbstractApplication.get().getExceptionHandler().logHandledException(errorCodeException);
						if (listener != null) {
							listener.onQueryPurchasesFailed(errorCodeException);
						}
					}
				} catch (JSONException e) {
					AbstractApplication.get().getExceptionHandler().logHandledException(e);
					if (listener != null) {
						listener.onQueryPurchasesFailed(InAppBillingErrorCode.BAD_PURCHASE_DATA.newErrorCodeException(e));
					}
				}
			}
		});
	}

	/**
	 * Handle a callback that purchases were updated from the Billing library
	 */
	@Override
	// TODO This is executed for each client connected. Should we have only one client connected?
	// TODO Call queryPurchases() at least twice in your code:
	// - Every time your app launches so that you can restore any purchases that a user has made since the app last stopped.
	// - In your onResume() method because a user can make a purchase when your app is in the background (for example, redeeming a promo code in Play Store app).
	// Calling queryPurchases() on startup and resume guarantees that your app finds out about all purchases and redemptions
	// the user may have made while the app wasn't running. Furthermore, if a user makes a purchase while the app is running
	// and your app misses it for any reason, your app still finds out about the purchase the next time the activity resumes and calls queryPurchases().
	// The simplest approach is to call queryPurchases() in your activity's onResume() method, since that callback fires
	// when the activity is created, as well as when the activity is unpaused.
	public void onPurchasesUpdated(@BillingClient.BillingResponse int resultCode, @Nullable List<Purchase> purchases) {
		InAppBillingErrorCode inAppBillingErrorCode = InAppBillingErrorCode.findByErrorResponseCode(resultCode);
		if (inAppBillingErrorCode == null) {
			if (purchases != null) {
				for (Purchase purchase : purchases) {
					String productId = purchase.getSku();
					Product product = inventory.getProduct(productId);
					try {
						if (product != null) {
							product.setPurchase(signatureBase64, purchase.getOriginalJson(), purchase.getSignature(), InAppBillingAppModule.get().getDeveloperPayloadVerificationStrategy());
							InAppBillingAppModule.get().getInAppBillingContext().addPurchasedProductType(product.getProductType());
							
							// TODO Add logic here to see if we are replacing the same purchase. If yes, dont track
							InAppBillingAppModule.get().getModuleAnalyticsSender().trackInAppBillingPurchase(product);
							
							if (listener != null) {
								listener.onPurchaseFinished(product);
							}
							if (product.isWaitingToConsume()) {
								consume(product);
							} else if (listener != null) {
								listener.onProvideProduct(product);
							}
						} else {
							AbstractApplication.get().getExceptionHandler().logWarningException(
									"The purchased product [" + productId + "] is not supported by the app, so it is ignored");
						}
					} catch (ErrorCodeException e) {
						AbstractApplication.get().getExceptionHandler().logHandledException(e);
						if (listener != null) {
							listener.onPurchaseFailed(e);
						}
					} catch (JSONException e) {
						AbstractApplication.get().getExceptionHandler().logHandledException(e);
						if (listener != null) {
							listener.onPurchaseFailed(InAppBillingErrorCode.BAD_PURCHASE_DATA.newErrorCodeException(e));
						}
					}
				}
			}
		} else if (inAppBillingErrorCode.equals(InAppBillingErrorCode.USER_CANCELED)) {
			LOGGER.warn("User cancelled the purchase flow.");
			if (listener != null) {
				listener.onPurchaseFailed(InAppBillingErrorCode.USER_CANCELED.newErrorCodeException("User cancelled the purchase flow."));
			}
		} else {
			AbstractApplication.get().getExceptionHandler().logHandledException(inAppBillingErrorCode.newErrorCodeException());
			if (listener != null) {
				listener.onPurchaseFailed(inAppBillingErrorCode.newErrorCodeException("Purchase failed."));
			}
			
		}
	}
	
	@MainThread
	public void queryProductsDetails(ItemType itemType) {
		executeServiceRequest(new Runnable() {
			@Override
			public void run() {
				
				if (itemType == ItemType.SUBSCRIPTION && !isSubscriptionsSupported()) {
					LOGGER.debug("Query product details failed. Subscriptions not supported.");
					if (listener != null) {
						listener.onPurchaseFailed(InAppBillingErrorCode.SUBSCRIPTIONS_NOT_AVAILABLE.newErrorCodeException());
					}
					return;
				}
				
				LOGGER.debug("Querying products details, item type: " + itemType);
				List<String> productsIdsToQuery = inventory.getAllProductIds();
				
				if (!productsIdsToQuery.isEmpty()) {
					
					SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
					params.setSkusList(productsIdsToQuery).setType(itemType.getType());
					billingClient.querySkuDetailsAsync(params.build(), new SkuDetailsResponseListener() {
						@Override
						public void onSkuDetailsResponse(int responseCode, List<SkuDetails> skuDetailsList) {
							InAppBillingErrorCode inAppBillingErrorCode = InAppBillingErrorCode.findByErrorResponseCode(responseCode);
							if (inAppBillingErrorCode == null) {
								for (SkuDetails skuDetails : skuDetailsList) {
									Product product = inventory.getProduct(skuDetails.getSku());
									ProductType productType = product.getProductType();
									product.setTitle(productType.getTitleId() != null ? LocalizationUtils.INSTANCE.getString(productType.getTitleId()) : skuDetails.getTitle());
									product.setDescription(productType.getDescriptionId() != null ? LocalizationUtils.INSTANCE.getString(productType.getDescriptionId()) : skuDetails.getDescription());
									product.setCurrencyCode(skuDetails.getPriceCurrencyCode());
									product.setFormattedPrice(skuDetails.getPrice());
									product.setPrice((double)skuDetails.getPriceAmountMicros() / 1000000);
									LOGGER.debug("Added product details to inventory: " + product);
								}
								
								if (listener != null) {
									listener.onQueryProductDetailsFinished(inventory);
								}
								
							} else {
								ErrorCodeException errorCodeException = inAppBillingErrorCode.newErrorCodeException("Failed querying " + itemType);
								AbstractApplication.get().getExceptionHandler().logHandledException(errorCodeException);
								if (listener != null) {
									listener.onQueryProductDetailsFailed(errorCodeException);
								}
							}
						}
					});
				}
			}
		});
	}

	@MainThread
	public void launchInAppPurchaseFlow(Activity activity, Product product) {
		launchPurchaseFlow(activity, product, ItemType.MANAGED, null);
	}

	@MainThread
	public void launchSubscriptionPurchaseFlow(Activity activity, Product product, String oldProductId) {
		launchPurchaseFlow(activity, product, ItemType.SUBSCRIPTION, oldProductId);
	}

	/**
	 * Initiate the UI flow for an in-app purchase. Call this method to initiate an in-app purchase, which will involve
	 * bringing up the Google Play screen. The calling activity will be paused while the user interacts with Google
	 * Play
	 * This method MUST be called from the UI thread of the Activity.
	 *
	 * @param activity The calling activity.
	 * @param product The product to purchase.
	 * @param itemType indicates if it's a product or a subscription (ITEM_TYPE_INAPP or ITEM_TYPE_SUBS)
	 * @param oldProductId The SKU which the new SKU is replacing or null if there is none
	 */
	private void launchPurchaseFlow(Activity activity, Product product, ItemType itemType, String oldProductId) {
		executeServiceRequest(new Runnable() {
			@Override
			public void run() {
				if (itemType.equals(ItemType.SUBSCRIPTION) && !isSubscriptionsSupported()) {
					LOGGER.debug("Failed in-app purchase flow for product id " + product.getId() + ", item type: " + itemType + ". Subscriptions not supported.");
					if (listener != null) {
						listener.onPurchaseFailed(InAppBillingErrorCode.SUBSCRIPTIONS_NOT_AVAILABLE.newErrorCodeException());
					}
				} else {
					LOGGER.debug("Launching in-app purchase flow for product id " + product.getId() + ", item type: " + itemType);
					String productIdToBuy = InAppBillingAppModule.get().getInAppBillingContext().isStaticResponsesEnabled() ?
							product.getProductType().getTestProductId() : product.getId();
					BillingFlowParams purchaseParams = BillingFlowParams.newBuilder()
							.setSku(productIdToBuy)
							.setType(itemType.getType())
							.setOldSku(oldProductId)
							.build();
					int responseCode = billingClient.launchBillingFlow(activity, purchaseParams);
					InAppBillingErrorCode inAppBillingErrorCode = InAppBillingErrorCode.findByErrorResponseCode(responseCode);
					if (inAppBillingErrorCode != null) {
						if (listener != null) {
							AbstractApplication.get().getExceptionHandler().logHandledException(inAppBillingErrorCode.newErrorCodeException());
							listener.onPurchaseFailed(inAppBillingErrorCode.newErrorCodeException());
						}
					}
				}
			}
		});
	}

	/**
	 * Consumes a given in-app product. Consuming can only be done on an item that's owned, and as a result of
	 * consumption, the user will no longer own it.
	 * 
	 * @param product The {@link Product} that represents the item to consume.
	 */
	@MainThread
	public void consume(final Product product) {
		if (product.getProductType().getItemType().equals(ItemType.MANAGED)) {
			String token = product.getPurchase().getToken();
			String productId = product.getId();
			if (StringUtils.isBlank(token)) {
				ErrorCodeException errorCodeException = InAppBillingErrorCode.MISSING_TOKEN.newErrorCodeException("Can't consume " + product.getId() + ". No token.");
				AbstractApplication.get().getExceptionHandler().logHandledException(errorCodeException);
				if (listener != null) {
					listener.onConsumeFailed(errorCodeException);
				}
			}

			// If we've already scheduled to consume this token - no action is needed (this could happen
			// if you received the token when querying purchases inside onReceive() and later from
			// onActivityResult()
			if (tokensToBeConsumed == null) {
				tokensToBeConsumed = new HashSet<>();
			} else if (tokensToBeConsumed.contains(token)) {
				LOGGER.debug("Token was already scheduled to be consumed - skipping...");
				return;
			}
			tokensToBeConsumed.add(token);
			
			LOGGER.debug("Consuming productId: " + productId + ", token: " + token);
			
			// Generating Consume Response listener
			ConsumeResponseListener onConsumeListener = new ConsumeResponseListener() {
				@Override
				public void onConsumeResponse(@BillingClient.BillingResponse int responseCode, String purchaseToken) {
					InAppBillingErrorCode inAppBillingErrorCode = InAppBillingErrorCode.findByErrorResponseCode(responseCode);
					if (inAppBillingErrorCode == null) {
						LOGGER.debug("Successfully consumed productId: " + productId);
						if (listener != null) {
							listener.onConsumeFinished(product);
							listener.onProvideProduct(product);
						}
					} else {
						AbstractApplication.get().getExceptionHandler().logHandledException(inAppBillingErrorCode.newErrorCodeException());
						if (listener != null) {
							listener.onConsumeFailed(inAppBillingErrorCode.newErrorCodeException("Consume failed."));
						}
					}
				}
			};
			
			// Creating a runnable from the request to use it inside our connection retry policy below
			executeServiceRequest(new Runnable() {
				@Override
				public void run() {
					billingClient.consumeAsync(token, onConsumeListener);
				}
			});
		} else {
			ErrorCodeException errorCodeException = InAppBillingErrorCode.INVALID_CONSUMPTION.newErrorCodeException("Items of type '"
					+ product.getProductType().getItemType() + "' can't be consumed.");
			AbstractApplication.get().getExceptionHandler().logHandledException(errorCodeException);
			if (listener != null) {
				listener.onConsumeFailed(errorCodeException);
			}
		}
	}

	/**
	 * @return whether subscriptions are supported.
	 */
	public boolean isSubscriptionsSupported() {
		if (subscriptionsSupported == null) {
			int responseCode = billingClient.isFeatureSupported(BillingClient.FeatureType.SUBSCRIPTIONS);
			InAppBillingErrorCode inAppBillingErrorCode = InAppBillingErrorCode.findByErrorResponseCode(responseCode);
			if (inAppBillingErrorCode == null) {
				LOGGER.debug("In-app billing subscriptions supported");
				subscriptionsSupported = true;
			} else {
				LOGGER.warn("Subscriptions NOT AVAILABLE. InAppBillingErrorCode: " + inAppBillingErrorCode);
				subscriptionsSupported = false;
			}
		}
		return subscriptionsSupported;
	}
}
