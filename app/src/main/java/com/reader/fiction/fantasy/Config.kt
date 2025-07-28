package com.reader.fiction.fantasy

/**
 * Central configuration values for the application.  Placing these in a
 * separate object makes it easy to find and update your app's unique
 * identifiers and API keys.  Edit these constants to match your own
 * RevenueCat and Google Play configuration.
 */
object Config {
    /**
     * Your RevenueCat public API key.  You can find this value in your
     * RevenueCat dashboard under Project Settings -> API Keys.  Without this
     * key RevenueCat cannot communicate with your backend.
     */
    const val REVENUECAT_PUBLIC_API_KEY: String = "REPLACE_WITH_YOUR_PUBLIC_API_KEY"

    /**
     * The entitlement identifier defined in RevenueCat.  This string should
     * match the identifier you choose when creating the entitlement in the
     * RevenueCat dashboard.  The entitlement determines whether your user
     * gains access to subscription features.
     */
    const val ENTITLEMENT_ID: String = "fictionreader_entitlement"

    /**
     * The subscription product identifier you create in Google Play Console.
     * It should follow your own naming conventions (e.g. "fictionreader_sub").
     */
    const val PRODUCT_ID: String = "fictionreader_sub"

    /**
     * The base plan identifier associated with your subscription product in
     * Google Play Console.  When configuring your subscription you create
     * at least one base plan (e.g. "basic-monthly").  Customers actually
     * purchase base plans, not the subscription object itself.
     */
    const val BASE_PLAN_ID: String = "basic-monthly"
}