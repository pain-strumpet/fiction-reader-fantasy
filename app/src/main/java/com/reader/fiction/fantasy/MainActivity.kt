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
import com.revenuecat.purchases.EntitlementInfo
import com.revenuecat.purchases.Offerings
import com.revenuecat.purchases.Purchases
import com.revenuecat.purchases.PurchasesConfiguration
import com.revenuecat.purchases.kotlin.*
import com.revenuecat.purchases.models.StoreProduct
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        LaunchedEffect(Unit) {
            try {
                val customerInfo = Purchases.sharedInstance.getCustomerInfo()
                val entitlement: EntitlementInfo? = customerInfo.entitlements.all[Config.ENTITLEMENT_ID]
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
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                } else {
                    SubscriptionScreen(
                        subscribed = isSubscribed,
                        onSubscribe = {
                            lifecycleOwner.lifecycleScope.launch {
                                val success = purchaseSubscription(context)
                                if (success) {
                                    val info = Purchases.sharedInstance.getCustomerInfo()
                                    val entitlement = info.entitlements.all[Config.ENTITLEMENT_ID]
                                    isSubscribed = entitlement?.isActive == true
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

    private suspend fun purchaseSubscription(context: Context): Boolean {
        return try {
            val offerings: Offerings = Purchases.sharedInstance.getOfferings()
            val product: StoreProduct = offerings.current?.availablePackages?.firstOrNull()?.product
                ?: throw IllegalStateException("No StoreProduct available")

            val activity = context.findActivity() ?: throw IllegalStateException("No Activity found")

            val result = Purchases.sharedInstance.purchaseWith(
                activity = activity,
                storeProduct = product
            )

            if (result.userCancelled) {
                Log.i(TAG, "User cancelled the purchase.")
                false
            } else {
                Log.d(TAG, "Purchase successful. Entitlements: ${result.customerInfo.entitlements.active.keys}")
                true
            }
        } catch (e: Exception) {
            Log.e(TAG, "Purchase failed", e)
            false
        }
    }

    private tailrec fun Context.findActivity(): ComponentActivity? = when (this) {
        is ComponentActivity -> this
        is ContextWrapper -> baseContext.findActivity()
        else -> null
    }

    @Composable
    fun SubscriptionScreen(
        subscribed: Boolean,
        onSubscribe: () -> Unit,
        onUnsubscribe: () -> Unit
    ) {
        var targetColor by remember { mutableStateOf(Color(0xFF6200EE)) }
        val animatedColor by animateColorAsState(targetValue = targetColor, label = "buttonColor")

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = if (subscribed) "Subscribed to Fantasy Fiction Reader" else "Fantasy Fiction Reader",
                style = MaterialTheme.typography.headlineMedium,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = {
                    targetColor = if (subscribed) Color(0xFFD32F2F) else Color(0xFF388E3C)
                    if (subscribed) onUnsubscribe() else onSubscribe()
                },
                colors = ButtonDefaults.buttonColors(containerColor = animatedColor)
            ) {
                Text(text = if (subscribed) "Unsubscribe" else "Subscribe for $2.99/month")
            }
        }
    }
}