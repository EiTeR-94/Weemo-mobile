package fr.eiter.plexiwine

import android.content.Context
import android.content.SharedPreferences

/** Bearer Vivino session — scan part de l'APK, pas du serveur. */
object VivinoTokenStore {
    private const val PREFS = "weenobis_vivino"
    private const val KEY_TOKEN = "bearer"
    private const val KEY_USER = "user_id"

    private fun prefs(ctx: Context): SharedPreferences =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun bearer(ctx: Context): String? =
        prefs(ctx).getString(KEY_TOKEN, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setBearer(ctx: Context, value: String?) {
        val p = prefs(ctx).edit()
        val t = value?.trim()?.removePrefix("Bearer ")?.removePrefix("bearer ")?.trim()
        if (t.isNullOrEmpty()) p.remove(KEY_TOKEN) else p.putString(KEY_TOKEN, t)
        p.apply()
    }

    fun userId(ctx: Context): String? =
        prefs(ctx).getString(KEY_USER, null)?.trim()?.takeIf { it.isNotEmpty() }

    fun setUserId(ctx: Context, value: String?) {
        val p = prefs(ctx).edit()
        val t = value?.trim()
        if (t.isNullOrEmpty()) p.remove(KEY_USER) else p.putString(KEY_USER, t)
        p.apply()
    }

    fun isConfigured(ctx: Context): Boolean = !bearer(ctx).isNullOrEmpty()
}
