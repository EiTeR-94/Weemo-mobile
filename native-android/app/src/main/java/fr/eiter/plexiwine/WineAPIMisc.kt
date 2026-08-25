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

suspend fun WineAPI.searchVivino(query: String): VivinoSearchResponse {
    // Weeno: GET /api/search?q= → Algolia Vivino (serveur)
    val q = java.net.URLEncoder.encode(query, "UTF-8")
    val (body, _) = execute(requestBuilder("api/search?q=$q&limit=5").get().build())
    return try {
        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
        val items = root.getAsJsonArray("items")
        val hits = mutableListOf<VivinoHit>()
        if (items != null) {
            for (el in items) {
                mapVivinoItem(el.asJsonObject)?.let { hits.add(it) }
            }
        }
        VivinoSearchResponse(ok = true, results = hits)
    } catch (_: Exception) {
        VivinoSearchResponse(ok = false, error = "decode")
    }
}

/** Backward-compatible producer+name search used by wizard */

suspend fun WineAPI.searchVivino(producer: String, name: String): VivinoSearchResponse {
    val q = listOf(producer, name).filter { it.isNotBlank() }.joinToString(" ").trim()
    return if (q.isBlank()) VivinoSearchResponse(ok = false, error = "Requête vide")
    else searchVivino(q)
}

