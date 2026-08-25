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

suspend fun WineAPI.sendFeedback(
    message: String,
    category: String = "general",
    appVersion: String = "",
): Pair<Boolean, String?> {
    return try {
        val payload = mutableMapOf<String, Any>(
            "message" to message,
            "category" to category,
            "client_info" to "native-android",
            "page_path" to "native/android",
        )
        if (appVersion.isNotBlank()) payload["app_version"] = appVersion
        val json = gson.toJson(payload)
        val (body, code) = execute(
            requestBuilder("api/feedback").post(json.toRequestBody(WineAPI.JSON)).build()
        )
        if (code in 200..299) {
            true to null
        } else {
            val err = try {
                @Suppress("UNCHECKED_CAST")
                (gson.fromJson(body, Map::class.java) as? Map<String, Any>)
                    ?.get("detail")?.toString()
            } catch (_: Exception) {
                null
            }
            false to (err ?: "Erreur $code")
        }
    } catch (e: Exception) {
        false to (e.message ?: "Réseau indisponible")
    }
}

suspend fun WineAPI.adminFeedbackList(
    limit: Int = 80,
    unreadOnly: Boolean = false,
    status: String? = null,
): AdminFeedbackListResponse = withContext(Dispatchers.IO) {
    var path = "api/admin/feedback?limit=${limit.coerceIn(1, 200)}"
    if (unreadOnly) path += "&unread=1"
    if (!status.isNullOrBlank()) path += "&status=${java.net.URLEncoder.encode(status, "UTF-8")}"
    val (body, code) = execute(requestBuilder(path).get().build())
    if (code !in 200..299) throw WineAPI.ApiException("Feedback admin indisponible", code)
    gson.fromJson(body, AdminFeedbackListResponse::class.java)
        ?: AdminFeedbackListResponse()
}

suspend fun WineAPI.adminFeedbackStats(): AdminFeedbackStats? = try {
    adminFeedbackList(limit = 1).stats
} catch (_: Exception) {
    null
}

suspend fun WineAPI.adminFeedbackMarkRead(id: Int, read: Boolean = true) {
    val json = gson.toJson(mapOf("read" to read))
    val (_, code) = execute(
        requestBuilder("api/admin/feedback/$id/read").post(json.toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Marquage lu impossible", code)
}

suspend fun WineAPI.adminFeedbackReadAll() {
    val (_, code) = execute(
        requestBuilder("api/admin/feedback/read-all").post("{}".toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Lecture globale impossible", code)
}

suspend fun WineAPI.adminFeedbackResolve(id: Int, status: String, reply: String) {
    val json = gson.toJson(mapOf("status" to status, "reply" to reply))
    val (_, code) = execute(
        requestBuilder("api/admin/feedback/$id/resolve").post(json.toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Réponse impossible", code)
}

suspend fun WineAPI.adminFeedbackReopen(id: Int) {
    val (_, code) = execute(
        requestBuilder("api/admin/feedback/$id/reopen").post("{}".toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Réouverture impossible", code)
}

suspend fun WineAPI.adminFeedbackDelete(id: Int) {
    val (_, code) = execute(requestBuilder("api/admin/feedback/$id").delete().build())
    if (code !in 200..299) throw WineAPI.ApiException("Suppression impossible", code)
}

suspend fun WineAPI.feedbackReplies(unseenOnly: Boolean = true): List<AdminFeedbackItem> =
    withContext(Dispatchers.IO) {
        val path = "api/feedback/replies?unseen=${if (unseenOnly) "1" else "0"}&limit=20"
        val (body, code) = execute(requestBuilder(path).get().build())
        if (code !in 200..299) return@withContext emptyList()
        gson.fromJson(body, FeedbackRepliesResponse::class.java)?.items.orEmpty()
    }

suspend fun WineAPI.markFeedbackRepliesSeen(ids: List<Int>) {
    try {
        val json = gson.toJson(mapOf("ids" to ids))
        execute(
            requestBuilder("api/feedback/replies/seen").post(json.toRequestBody(WineAPI.JSON)).build()
        )
    } catch (_: Exception) {
    }
}

// ── RPG admin enrichi ───────────────────────────────────────────────────

