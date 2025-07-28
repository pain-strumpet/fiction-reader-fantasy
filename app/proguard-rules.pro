# ProGuard rules for the Fiction Reader application.
# This file is intentionally minimal. Uncomment and adjust the rules below
# when ready to configure code shrinking and obfuscation for release builds.

# Keep Jetpack Compose runtime from being removed by the shrinker.
-keep class androidx.compose.** { *; }

# Keep RevenueCat SDK classes that use reflection.
-keep class com.revenuecat.purchases.** { *; }