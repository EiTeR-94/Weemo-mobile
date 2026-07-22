# WeenoBis — R8 release rules
# Objectif : minify libs tierces sans casser Gson/Compose/ML Kit/CameraX.
# Les modèles app sont conservés (JSON stable). Secrets (Bearer) = EncryptedSharedPreferences.

-keepattributes Signature, *Annotation*, InnerClasses, EnclosingMethod, Exceptions
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile

# --- App models + UI (Gson reflection, Compose) ---
-keep class fr.eiter.plexiwine.** { *; }
-keepclassmembers class fr.eiter.plexiwine.** { *; }

# --- Gson ---
-keep class com.google.gson.** { *; }
-keepclassmembers,allowobfuscation class * {
  @com.google.gson.annotations.SerializedName <fields>;
}
-dontwarn com.google.gson.**

# --- OkHttp / Okio ---
-dontwarn okhttp3.**
-dontwarn okio.**
-keep class okhttp3.internal.publicsuffix.PublicSuffixDatabase { *; }

# --- Coil ---
-dontwarn coil.**

# --- CameraX ---
-keep class androidx.camera.** { *; }
-dontwarn androidx.camera.**

# --- ML Kit (barcode + text) ---
-keep class com.google.mlkit.** { *; }
-keep class com.google.android.gms.internal.mlkit_** { *; }
-dontwarn com.google.mlkit.**
-dontwarn com.google.android.gms.**

# --- EncryptedSharedPreferences / Tink ---
-keep class androidx.security.crypto.** { *; }
-keep class com.google.crypto.tink.** { *; }
-dontwarn com.google.crypto.tink.**

# --- Compose (stable) ---
-dontwarn androidx.compose.**

