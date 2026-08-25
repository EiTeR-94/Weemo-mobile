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

suspend fun WineAPI.version(): String {
    return try {
        val (body, _) = execute(requestBuilder("api/health").get().build())
        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
        root.get("version")?.asString ?: "?"
    } catch (_: Exception) {
        "?"
    }
}

suspend fun WineAPI.downloadAsset(pathOrURL: String?): ByteArray = withContext(Dispatchers.IO) {
    val p = pathOrURL?.takeIf { it.isNotBlank() }
        ?: throw WineAPI.ApiException("URL asset invalide")
    if (p.startsWith("http://") || p.startsWith("https://")) {
        // external (Vivino labels etc.) — plain GET
        val plain = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .build()
        plain.newCall(Request.Builder().url(p).get().build()).execute().use { resp ->
            if (!resp.isSuccessful) throw WineAPI.ApiException("Fichier externe HTTP ${resp.code}")
            return@withContext resp.body?.bytes() ?: ByteArray(0)
        }
    }
    val candidates = listOfNotNull(
        ServerSettings.resolveAssetURL(p, ServerSettings.LAN_API_BASE),
        ServerSettings.resolveAssetURL(p, baseURL)
    ).distinct()
    var lastErr: Exception? = null
    for (url in candidates) {
        try {
            val b = Request.Builder().url(url)
            applyHeaders(b)
            client.newCall(b.get().build()).execute().use { resp ->
                if (resp.code == 401) throw WineAPI.ApiException("Session expirée", 401)
                if (resp.isSuccessful) {
                    return@withContext resp.body?.bytes() ?: ByteArray(0)
                }
                lastErr = WineAPI.ApiException("Fichier HTTP ${resp.code}")
            }
        } catch (e: Exception) {
            lastErr = e
        }
    }
    throw (lastErr ?: WineAPI.ApiException("Asset introuvable"))
}

suspend fun WineAPI.patchnotes(): PatchnotesResponse {
    val (body, _) = execute(requestBuilder("api/admin/patchnotes").get().build())
    return gson.fromJson(body, PatchnotesResponse::class.java)
}

suspend fun WineAPI.fetchMobileVersions(): MobileVersionsManifest? = withContext(Dispatchers.IO) {
    try {
        val url = ServerSettings.versionsURL
        val req = Request.Builder().url(url).get().build()
        client.newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) return@withContext null
            val body = resp.body?.string().orEmpty()
            gson.fromJson(body, MobileVersionsManifest::class.java)
        }
    } catch (_: Exception) {
        null
    }
}

