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

suspend fun WineAPI.wishlist(): List<WishlistItem> {
    val (body, _) = execute(requestBuilder("api/wishlist").get().build())
    try {
        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
        val items = root.getAsJsonArray("items")
        if (items != null) {
            val type = object : TypeToken<List<WishlistItem>>() {}.type
            return gson.fromJson(items, type) ?: emptyList()
        }
    } catch (_: Exception) {}
    val type = object : TypeToken<List<WishlistItem>>() {}.type
    return gson.fromJson(body, type) ?: emptyList()
}

suspend fun WineAPI.addWishlist(wineName: String, producer: String, style: String = "Unknown", barcode: String = "") {
    val json = gson.toJson(
        mapOf(
            "wine_name" to wineName,
            "producer" to producer,
            "wine_color" to style,
            "barcode" to barcode
        )
    )
    execute(requestBuilder("api/wishlist").post(json.toRequestBody(WineAPI.JSON)).build())
}

suspend fun WineAPI.deleteWishlist(id: Int) {
    execute(requestBuilder("api/wishlist/$id").delete().build())
}

