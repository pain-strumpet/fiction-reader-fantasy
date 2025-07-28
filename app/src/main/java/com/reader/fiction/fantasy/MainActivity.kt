package com.reader.fiction.fantasy

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.revenuecat.purchases.CustomerInfo
import com.revenuecat.purchases.EntitlementInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.PurchaseParams
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.getCustomerInfoWith
import com.revenuecat.purchases.getOfferingsWith
import com.revenuecat.purchases.models.StoreProduct
import com.revenuecat.purchases.purchaseWith
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Initialize RevenueCat SDK
        val config = PurchasesConfiguration.Builder(applicationContext, Config.REVENUECAT_PUBLIC_API_KEY).build()
        Purchases.configure(config)
        Log.d(TAG, "RevenueCat configured")

        setContent {
            SubscriptionApp()
        }
    }

    @Composable
    fun SubscriptionApp() {
        var isSubscribed by remember { mutableStateOf(false) }
        var loading by remember { mutableStateOf(true) }
        val context = LocalContext.current
        val lifecycleOwner = LocalLifecycleOwner.current

        // Check subscription status when the composable enters composition
        LaunchedEffect(Unit) {
            try {
                val customerInfo = fetchCustomerInfo()
                val entitlement: EntitlementInfo? =
                    customerInfo.entitlements.all[Config.ENTITLEMENT_ID]
                isSubscribed = entitlement?.isActive == true
            } catch (e: Exception) {
                Log.e(TAG, "Failed to fetch customer info", e)
            } finally {
                loading = false
            }
        }

        MaterialTheme {
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                if (loading) {
                    Box(
                        Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                } else {
                    SubscriptionScreen(
                        subscribed = isSubscribed,
                        onSubscribe = {
                            // Launch purchase from lifecycle scope
                            lifecycleOwner.lifecycleScope.launch {
                                val success = purchaseSubscription(context)
                                if (success) {
                                    try {
                                        val info = fetchCustomerInfo()
                                        val entitlement = info.entitlements.all[Config.ENTITLEMENT_ID]
                                        isSubscribed = entitlement?.isActive == true
                                    } catch (e: Exception) {
                                        Log.e(TAG, "Error refreshing customer info after purchase", e)
                                    }
                                }
                            }
                        },
                        onUnsubscribe = {
                            Toast.makeText(
                                context,
                                "Manage your subscription in the Google Play Store.",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                    )
                }
            }
        }
    }

    /**
     * Wraps RevenueCat's getOfferingsWith callback into a suspend function.
     */
    private suspend fun fetchOfferings(): Offerings = suspendCancellableCoroutine { cont ->
        Purchases.sharedInstance.getOfferingsWith(
            onError = { error ->
                cont.resumeWithException(Exception(error.message))
            },
            onSuccess = { offerings ->
                cont.resume(offerings)
            }
        )
    }

    /**
     * Wraps RevenueCat's getCustomerInfoWith callback into a suspend function.
     */
    private suspend fun fetchCustomerInfo(): CustomerInfo = suspendCancellableCoroutine { cont ->
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error ->
                cont.resumeWithException(Exception(error.message))
            },
            onSuccess = { customerInfo ->
                cont.resume(customerInfo)
            }
        )
    }

    /**
     * Purchases the first available subscription product.
     *
     * Uses getOfferingsWith to retrieve products and purchaseWith to execute
     * the purchase. When purchaseWith succeeds, it returns updated CustomerInfo
     * via callback. This implementation resumes the coroutine with the
     * entitlement's active state.
     */
    private suspend fun purchaseSubscription(context: Context): Boolean {
        return try {
            // Fetch the current offering and select the first available product
            val offerings = fetchOfferings()
            val product: StoreProduct = offerings.current
                ?.availablePackages
                ?.firstOrNull()
                ?.product
                ?: throw IllegalStateException("No StoreProduct available")

            val activity = context.findActivity()
                ?: throw IllegalStateException("No Activity found")

            // Wrap purchaseWith callback into a suspend coroutine
            suspendCancellableCoroutine<Boolean> { cont ->
                val params = PurchaseParams.Builder(activity, product).build()
                Purchases.sharedInstance.purchaseWith(
                    purchaseParams = params,
                    onError = { error, userCancelled ->
                        if (userCancelled) {
                            Log.i(TAG, "User cancelled the purchase.")
                        } else {
                            Log.e(TAG, "Error purchasing: ${error.message}")
                        }
                        cont.resume(false)
                    },
                    onSuccess = { _, customerInfo ->
                        // Check entitlement status from the returned CustomerInfo
                        val entitlement = customerInfo.entitlements.all[Config.ENTITLEMENT_ID]
                        cont.resume(entitlement?.isActive == true)
                    }
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Purchase failed", e)
            false
        }
    }

    /**
     * Traverses the context chain to find an Activity for purchases.
     */
    private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    /**
     * Displays the subscription state with a button that either subscribes
     * or unsubscribes the user. The button color animates between green
     * and red when toggled.
     */
    @Composable
    fun SubscriptionScreen(
        subscribed: Boolean,
        onSubscribe: () -> Unit,
        onUnsubscribe: () -> Unit
    ) {
        var targetColor by remember { mutableStateOf(Color(0xFF6200EE)) }
        // Do not pass deprecated label parameter
        val animatedColor by animateColorAsState(targetValue = targetColor)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (subscribed) {
                    "Subscribed to Fantasy Fiction Reader"
                } else {
                    "Fantasy Fiction Reader"
                },
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    targetColor = if (subscribed) {
                        Color(0xFFD32F2F)
                    } else {
                        Color(0xFF388E3C)
                    }
                    if (subscribed) {
                        onUnsubscribe()
                    } else {
                        onSubscribe()
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = animatedColor)
            ) {
                Text(
                    text = if (subscribed) {
                        "Unsubscribe"
                    } else {
                        "Subscribe for $2.99/month"
                    }
                )
            }
        }
    }
}
