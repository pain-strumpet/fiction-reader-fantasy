package com.reader.fiction.fantasy

import android.content.Context
import android.content.ContextWrapper
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.firestore.firestore
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {

    companion object {
        private const val TAG = "MainActivity"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Initialize Firebase manually with options
        val options = FirebaseOptions.Builder()
            .setProjectId("fantasy-fiction-reader-f892f")
            .setApplicationId("1:186826928823:android:9ec2262802da4ecbcf9332")
            .setApiKey("AIzaSyAfH3vi4PIYB4iVzQofHg8iKDRolippvgs")
            .build()

        FirebaseApp.initializeApp(this, options)

        val config =
            PurchasesConfiguration.Builder(applicationContext, Config.REVENUECAT_PUBLIC_API_KEY)
                .build()
        Purchases.configure(config)
        Purchases.sharedInstance.invalidateCustomerInfoCache() // Ensure clean state after reinstall
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
        val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current

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
                            lifecycleOwner.lifecycleScope.launch {
                                val success = purchaseSubscription(context)
                                if (success) {
                                    try {
                                        val info = fetchCustomerInfo()
                                        val entitlement =
                                            info.entitlements.all[Config.ENTITLEMENT_ID]
                                        isSubscribed = entitlement?.isActive == true
                                    } catch (e: Exception) {
                                        Log.e(
                                            TAG,
                                            "Error refreshing customer info after purchase",
                                            e
                                        )
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

    private suspend fun fetchCustomerInfo(): CustomerInfo = suspendCancellableCoroutine { cont ->
        // fallback logic if CacheFetchPolicy is not available
        Purchases.sharedInstance.getCustomerInfoWith(
            onError = { error ->
                cont.resumeWithException(Exception(error.message))
            },
            onSuccess = { customerInfo ->
                cont.resume(customerInfo)
            }
        )
    }

    private suspend fun purchaseSubscription(context: Context): Boolean {
        return try {
            val offerings = fetchOfferings()
            val product: StoreProduct = offerings.current
                ?.availablePackages
                ?.firstOrNull()
                ?.product
                ?: throw IllegalStateException("No StoreProduct available")

            val activity = context.findActivity()
                ?: throw IllegalStateException("No Activity found")

            suspendCancellableCoroutine { cont ->
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
        val context = LocalContext.current
        var targetColor by remember { mutableStateOf(Color(0xFF6200EE)) }
        val animatedColor by animateColorAsState(targetValue = targetColor)

        // Add navigation state
        var selectedStory by remember { mutableStateOf<Map<String, Any>?>(null) }

        // Show story reader if a story is selected
        if (selectedStory != null) {
            StoryReaderScreen(
                story = selectedStory!!,
                onBack = { selectedStory = null }
            )
            return
        }

        val db = Firebase.firestore
        val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
            .format(java.util.Date())

        // Query for today's stories
        val stories = remember { mutableStateOf<List<Map<String, Any>>>(emptyList()) }

        LaunchedEffect(Unit) {
            db.collection("stories")
                .whereEqualTo("publishDate", today)
                .get()
                .addOnSuccessListener { documents ->
                    val sortedStories = documents.map { it.data }
                        .sortedBy { (it["dayIndex"] as? Long)?.toInt() ?: 0 }
                    stories.value = sortedStories
                }
        }

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

            // Story list - make it scrollable
            Box(
                modifier = Modifier
                    .weight(1f) // Takes available space
                    .fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.verticalScroll(rememberScrollState())
                ) {
                    stories.value.forEach { story ->
                        val index = (story["dayIndex"] as? Long)?.toInt() ?: 0
                        val title = story["title"] as? String ?: "Untitled"

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            onClick = {
                                when (index) {
                                    0 -> {
                                        // Free story - show it immediately
                                        selectedStory = story
                                    }
                                    in 1..3 -> {
                                        // Ad-gated story
                                        Toast.makeText(
                                            context,
                                            "Ad would show here, then story opens",
                                            Toast.LENGTH_SHORT
                                        ).show()
                                        // For now, just show the story after toast
                                        selectedStory = story
                                    }
                                    4 -> {
                                        if (subscribed) {
                                            // Premium story - subscriber can read
                                            selectedStory = story
                                        } else {
                                            // Show subscribe prompt
                                            Toast.makeText(
                                                context,
                                                "Subscribe to read premium stories!",
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "${index + 1}. $title",
                                    modifier = Modifier.weight(1f)
                                )
                                Text(
                                    text = when {
                                        index == 0 -> "FREE"
                                        index in 1..3 -> "🎬 AD"
                                        index == 4 && subscribed -> "PREMIUM"
                                        else -> "🔒 PRO"
                                    },
                                    color = when {
                                        index == 0 -> Color.Green
                                        subscribed -> Color.Blue
                                        else -> Color.Gray
                                    }
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

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

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    val db = Firebase.firestore
                    val today = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.US)
                        .format(java.util.Date())

                    // Create 5 stories for today
                    for (i in 0..4) {
                        val story = hashMapOf(
                            "title" to when(i) {
                                0 -> "The Dragon's Dawn (Free)"
                                1 -> "Wizard's Quest"
                                2 -> "Knights of Tomorrow"
                                3 -> "Magic Kingdom"
                                else -> "Premium Epic"
                            },
                            "content" to when(i) {
                                0 -> """
The dragon stirred in the early morning mist, its scales catching the first rays of sunlight. 

For a thousand years, it had slumbered beneath the mountain, waiting for this moment. The prophecy spoke of a dawn when the world would need its ancient wisdom once more.

As the creature unfurled its massive wings, villagers in the valley below gasped in awe. This was not the fearsome beast of legend, but a majestic guardian, eyes filled with timeless knowledge.

"Fear not," the dragon's voice rumbled like distant thunder, "I have awakened not to destroy, but to guide you through the darkness that approaches."

And so began the most extraordinary chapter in the kingdom's history...
                                """.trimIndent()
                                1 -> "The wizard's tower stood at the edge of reality, where magic met the mundane world..."
                                2 -> "In the year 2157, the last knights of Earth prepared for their greatest battle..."
                                3 -> "The magic kingdom appeared only once every hundred years, and today was that day..."
                                else -> "This premium story contains the secrets of the universe itself..."
                            },
                            "publishDate" to today,
                            "dayIndex" to i.toLong()
                        )

                        db.collection("stories")
                            .add(story)
                            .addOnSuccessListener {
                                if (i == 4) {
                                    Toast.makeText(
                                        context,
                                        "Added 5 stories for today!",
                                        Toast.LENGTH_SHORT
                                    ).show()
                                }
                            }
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Text("Add 5 Daily Stories")
            }

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    // Force refresh the query
                    db.collection("stories")
                        .get()
                        .addOnSuccessListener { documents ->
                            Toast.makeText(
                                context,
                                "Found ${documents.size()} total stories",
                                Toast.LENGTH_SHORT
                            ).show()

                            // Also check today's stories
                            db.collection("stories")
                                .whereEqualTo("publishDate", today)
                                .get()
                                .addOnSuccessListener { todayDocs ->
                                    Toast.makeText(
                                        context,
                                        "Found ${todayDocs.size()} stories for today ($today)",
                                        Toast.LENGTH_LONG
                                    ).show()
                                }
                        }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.Red
                )
            ) {
                Text("Debug: Check Stories")
            }
        }
    }

    @Composable
    fun StoryReaderScreen(
        story: Map<String, Any>,
        onBack: () -> Unit
    ) {
        val title = story["title"] as? String ?: "Untitled"
        val content = story["content"] as? String ?: "No content available."

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            // Back button
            Button(
                onClick = onBack,
                modifier = Modifier.padding(bottom = 16.dp)
            ) {
                Text("← Back to Stories")
            }

            // Title
            Text(
                text = title,
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            // Scrollable content
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
            ) {
                Text(
                    text = content,
                    style = MaterialTheme.typography.bodyLarge,
                    lineHeight = 28.sp
                )
            }
        }
    }
}