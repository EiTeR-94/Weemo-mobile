package fr.eiter.plexiwine

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Dns
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.Inet4Address
import java.net.InetAddress
import java.util.concurrent.TimeUnit

suspend fun WineAPI.rpgMe(): RpgState = withContext(Dispatchers.IO) {
    try {
        val (body, code) = execute(
            requestBuilder("api/rpg/me").get().build(),
            allowUnauthorizedBody = true
        )
        if (code !in 200..299) return@withContext RpgState(enabled = false)
        gson.fromJson(body, RpgState::class.java) ?: RpgState(enabled = false)
    } catch (_: Exception) {
        RpgState(enabled = false)
    }
}

suspend fun WineAPI.rpgSetClass(classKey: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val json = gson.toJson(mapOf("class" to classKey))
        val (body, code) = execute(
            requestBuilder("api/rpg/class").post(json.toRequestBody(WineAPI.JSON)).build()
        )
        code in 200..299 && (gson.fromJson(body, OkResponse::class.java)?.ok == true)
    } catch (_: Exception) {
        false
    }
}

suspend fun WineAPI.rpgIntroSeen(): Boolean = withContext(Dispatchers.IO) {
    try {
        val (_, code) = execute(
            requestBuilder("api/rpg/intro-seen")
                .post("{}".toRequestBody(WineAPI.JSON))
                .build()
        )
        code in 200..299
    } catch (_: Exception) {
        false
    }
}

suspend fun WineAPI.adminRpgPlayers(): List<RpgAdminPlayer> = withContext(Dispatchers.IO) {
    try {
        val (body, code) = execute(requestBuilder("api/admin/rpg/players").get().build())
        if (code !in 200..299) return@withContext emptyList()
        gson.fromJson(body, RpgAdminPlayersResponse::class.java)?.players.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}

/** Liste joueurs + flags RPG (pour les toggles admin). */

suspend fun WineAPI.adminRpgPlayersBundle(): RpgAdminPlayersResponse = withContext(Dispatchers.IO) {
    try {
        val (body, code) = execute(requestBuilder("api/admin/rpg/players").get().build())
        if (code !in 200..299) return@withContext RpgAdminPlayersResponse()
        gson.fromJson(body, RpgAdminPlayersResponse::class.java) ?: RpgAdminPlayersResponse()
    } catch (_: Exception) {
        RpgAdminPlayersResponse()
    }
}

suspend fun WineAPI.adminRpgGetSettings(): RpgAdminFlags? = withContext(Dispatchers.IO) {
    try {
        val (body, code) = execute(requestBuilder("api/admin/rpg/settings").get().build())
        if (code !in 200..299) return@withContext null
        gson.fromJson(body, RpgAdminSettingsResponse::class.java)?.flags
    } catch (_: Exception) {
        null
    }
}

suspend fun WineAPI.adminRpgPatchSettings(payload: Map<String, Any?>): RpgAdminFlags? =
    withContext(Dispatchers.IO) {
        try {
            val json = gson.toJson(payload)
            val (body, code) = execute(
                requestBuilder("api/admin/rpg/settings")
                    .patch(json.toRequestBody(WineAPI.JSON))
                    .build()
            )
            if (code !in 200..299) return@withContext null
            gson.fromJson(body, RpgAdminSettingsResponse::class.java)?.flags
        } catch (_: Exception) {
            null
        }
    }

/**
 * @param allowed true=force ON, false=force OFF, null=auto (défaut allowlist/env)
 */

suspend fun WineAPI.adminRpgSetUserAllowed(username: String, allowed: Boolean?): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val enc = java.net.URLEncoder.encode(username, "UTF-8")
            // null JSON explicite
            val json = if (allowed == null) {
                """{"allowed":null}"""
            } else {
                gson.toJson(mapOf("allowed" to allowed))
            }
            val (_, code) = execute(
                requestBuilder("api/admin/rpg/settings/users/$enc")
                    .put(json.toRequestBody(WineAPI.JSON))
                    .build()
            )
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

suspend fun WineAPI.adminRpgAdjustXp(username: String, delta: Int): Boolean = withContext(Dispatchers.IO) {
    try {
        val json = gson.toJson(mapOf("delta" to delta))
        val enc = java.net.URLEncoder.encode(username, "UTF-8")
        val (_, code) = execute(
            requestBuilder("api/admin/rpg/players/$enc/xp").post(json.toRequestBody(WineAPI.JSON)).build()
        )
        code in 200..299
    } catch (_: Exception) {
        false
    }
}

suspend fun WineAPI.adminRpgResetDaily(username: String): Boolean = withContext(Dispatchers.IO) {
    try {
        val enc = java.net.URLEncoder.encode(username, "UTF-8")
        val (_, code) = execute(
            requestBuilder("api/admin/rpg/players/$enc/reset-daily")
                .post(ByteArray(0).toRequestBody())
                .build()
        )
        code in 200..299
    } catch (_: Exception) {
        false
    }
}

suspend fun WineAPI.adminRpgPatchPlayer(username: String, payload: Map<String, Any?>): Boolean =
    withContext(Dispatchers.IO) {
        try {
            val enc = java.net.URLEncoder.encode(username, "UTF-8")
            val json = gson.toJson(payload)
            val (_, code) = execute(
                requestBuilder("api/admin/rpg/players/$enc")
                    .patch(json.toRequestBody(WineAPI.JSON))
                    .build()
            )
            code in 200..299
        } catch (_: Exception) {
            false
        }
    }

// ── Versions portail ────────────────────────────────────────────────────

