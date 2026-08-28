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

suspend fun WineAPI.adminInvites(): List<InviteItem> = withContext(Dispatchers.IO) {
    val (body, code) = execute(requestBuilder("api/invites").get().build())
    if (code !in 200..299) return@withContext emptyList()
    val type = object : TypeToken<List<InviteItem>>() {}.type
    gson.fromJson<List<InviteItem>>(body, type) ?: emptyList()
}

suspend fun WineAPI.adminExtendInvite(id: Int, validity: String) {
    val json = gson.toJson(mapOf("validity" to validity))
    val (_, code) = execute(
        requestBuilder("api/invites/$id/extend").post(json.toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Prolongation impossible", code)
}

suspend fun WineAPI.adminReissueInvite(id: Int): String? {
    val (body, code) = execute(
        requestBuilder("api/invites/$id/reissue").post("{}".toRequestBody(WineAPI.JSON)).build()
    )
    if (code !in 200..299) throw WineAPI.ApiException("Réémission impossible", code)
    return gson.fromJson(body, CreateInviteResponse::class.java)?.url
}

suspend fun WineAPI.adminRevokeInvite(id: Int) {
    val (_, code) = execute(requestBuilder("api/invites/$id").delete().build())
    if (code !in 200..299) throw WineAPI.ApiException("Révocation impossible", code)
}

