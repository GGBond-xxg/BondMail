# WorkManager creates workers by class name.
-keep class com.bond.mail.background.** extends androidx.work.ListenableWorker { *; }

# JavaMail discovers protocol providers and content handlers through resource files and reflection.
-keep class com.sun.mail.** { *; }
-keep class javax.mail.** { *; }
-keep class javax.activation.** { *; }
-dontwarn com.sun.mail.**
-dontwarn javax.mail.**
-dontwarn javax.activation.**

# Keep Room database implementation naming stable. Generated DAO implementations remain shrinkable.
-keep class * extends androidx.room.RoomDatabase { *; }

# WebView JavaScript is disabled, but these methods may still be referenced by Android framework
# callbacks after optimization.
-keepclassmembers class * extends android.webkit.WebViewClient {
    public *;
}

# OAuth SDK callbacks and Parcelable results cross activity/process boundaries.
-keep class com.microsoft.identity.client.** { *; }
-keep class com.microsoft.identity.common.** { *; }
-keep class com.google.android.gms.auth.api.identity.** { *; }
-dontwarn com.microsoft.identity.**
-dontwarn com.microsoft.device.**

# Nimbus JOSE exposes optional algorithms backed by Tink and Bouncy Castle. MSAL does not
# exercise these providers for BondMail's public-client OAuth flow, but R8 still sees their
# optional references while shrinking the performance build.
-dontwarn com.google.crypto.tink.**
-dontwarn org.bouncycastle.**
-dontwarn com.nimbusds.jose.crypto.bc.**
