package fr.eiter.plexiwine

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey

/**
 * Bearer Vivino session — **chiffré au repos** (EncryptedSharedPreferences + MasterKey AES256-GCM).
 * Scan part de l'APK, pas du serveur. Jamais loggé en clair.
 */
object VivinoTokenStore {
    private const val TAG = "VivinoTokenStore"
    /** Ancien prefs clair (migration one-shot puis effacement). */
    private const val PREFS_LEGACY = "weenobis_vivino"
    /** Prefs chiffrés (clés + valeurs). */
    private const val PREFS_SECURE = "weenobis_vivino_secure"
    private const val KEY_TOKEN = "bearer"
    private const val KEY_USER = "user_id"

    @Volatile
    private var cached: SharedPreferences? = null

    private fun securePrefs(ctx: Context): SharedPreferences {
        cached?.let { return it }
        synchronized(this) {
            cached?.let { return it }
            val app = ctx.applicationContext
            val masterKey = MasterKey.Builder(app)
                .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
                .build()
            val prefs = EncryptedSharedPreferences.create(
                app,
                PREFS_SECURE,
                masterKey,
                EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
                EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
            )
            migrateFromLegacy(app, prefs)
            cached = prefs
            return prefs
        }
    }

    /** Migre bearer/user_id depuis SharedPreferences clair, puis efface le legacy. */
    private fun migrateFromLegacy(app: Context, secure: SharedPreferences) {
        if (secure.contains(KEY_TOKEN) || secure.contains(KEY_USER)) return
        val legacy = app.getSharedPreferences(PREFS_LEGACY, Context.MODE_PRIVATE)
        val legacyToken = legacy.getString(KEY_TOKEN, null)
        val legacyUser = legacy.getString(KEY_USER, null)
        if (legacyToken.isNullOrBlank() && legacyUser.isNullOrBlank()) return
        try {
            secure.edit().apply {
                if (!legacyToken.isNullOrBlank()) putString(KEY_TOKEN, normalizeBearer(legacyToken))
                if (!legacyUser.isNullOrBlank()) putString(KEY_USER, legacyUser.trim())
                apply()
            }
            // Efface le stockage clair pour éviter le leak
            legacy.edit().clear().apply()
            Log.i(TAG, "migrated vivino token store to encrypted prefs")
        } catch (e: Exception) {
            // Ne log jamais la valeur du token
            Log.w(TAG, "migration failed: ${e.javaClass.simpleName}")
        }
    }

    /** Strip préfixe "Bearer " / whitespace — jamais de log de la valeur. */
    private fun normalizeBearer(value: String?): String? {
        val t = value?.trim()
            ?.removePrefix("Bearer ")
            ?.removePrefix("bearer ")
            ?.removePrefix("BEARER ")
            ?.trim()
        return t?.takeIf { it.isNotEmpty() }
    }

    fun bearer(ctx: Context): String? =
        try {
            securePrefs(ctx).getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "read bearer failed: ${e.javaClass.simpleName}")
            null
        }

    fun setBearer(ctx: Context, value: String?) {
        try {
            val p = securePrefs(ctx).edit()
            val t = normalizeBearer(value)
            if (t == null) p.remove(KEY_TOKEN) else p.putString(KEY_TOKEN, t)
            p.apply()
        } catch (e: Exception) {
            Log.w(TAG, "write bearer failed: ${e.javaClass.simpleName}")
        }
    }

    fun userId(ctx: Context): String? =
        try {
            securePrefs(ctx).getString(KEY_USER, null)?.trim()?.takeIf { it.isNotEmpty() }
        } catch (e: Exception) {
            Log.w(TAG, "read user_id failed: ${e.javaClass.simpleName}")
            null
        }

    fun setUserId(ctx: Context, value: String?) {
        try {
            val p = securePrefs(ctx).edit()
            val t = value?.trim()?.takeIf { it.isNotEmpty() }
            if (t == null) p.remove(KEY_USER) else p.putString(KEY_USER, t)
            p.apply()
        } catch (e: Exception) {
            Log.w(TAG, "write user_id failed: ${e.javaClass.simpleName}")
        }
    }

    fun isConfigured(ctx: Context): Boolean = !bearer(ctx).isNullOrEmpty()
}
