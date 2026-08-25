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

suspend fun WineAPI.checkins(
    q: String = "",
    style: String = "",
    minRating: Double = 0.0,
    period: String = "",
    limit: Int = 10,
    offset: Int = 0
): List<CheckinItem> {
    val params = mutableListOf("limit=$limit", "offset=$offset")
    if (q.isNotEmpty()) params += "q=${java.net.URLEncoder.encode(q, "UTF-8")}"
    if (style.isNotEmpty()) params += "wine_color=${java.net.URLEncoder.encode(style, "UTF-8")}"
    if (minRating > 0) params += "min_rating=$minRating"
    if (period.isNotEmpty()) params += "period=${java.net.URLEncoder.encode(period, "UTF-8")}"
    val (body, _) = execute(requestBuilder("api/checkins?${params.joinToString("&")}").get().build())
    try {
        val wrapped = gson.fromJson(body, CheckinsListResponse::class.java)
        if (wrapped?.items != null) return wrapped.items!!
    } catch (_: Exception) {}
    val type = object : TypeToken<List<CheckinItem>>() {}.type
    return gson.fromJson(body, type) ?: emptyList()
}

suspend fun WineAPI.stats(): HistoryStats {
    val (body, _) = execute(requestBuilder("api/stats").get().build())
    return gson.fromJson(body, HistoryStats::class.java)
}

suspend fun WineAPI.coupleStats(): CoupleStats {
    val (body, _) = execute(requestBuilder("api/stats/couple").get().build())
    return gson.fromJson(body, CoupleStats::class.java)
}

suspend fun WineAPI.deleteCheckin(id: Int) {
    execute(requestBuilder("api/checkins/$id").delete().build())
}

suspend fun WineAPI.updateCheckin(
    id: Int,
    rating: Double? = null,
    flavors: List<String>? = null,
    hops: List<String>? = null,
    comment: String? = null,
    hiddenFromPartner: Boolean? = null,
    location: String? = null,
    rebuy: String? = null
) {
    val payload = mutableMapOf<String, Any?>()
    if (rating != null) payload["rating"] = rating
    if (flavors != null) payload["flavors"] = flavors
    if (hops != null) payload["hops"] = hops
    if (comment != null) payload["comment"] = comment
    if (location != null) payload["location"] = location.take(300)
    if (hiddenFromPartner != null) payload["hidden_from_partner"] = hiddenFromPartner
    if (rebuy != null) payload["rebuy"] = rebuy
    val json = gson.toJson(payload)
    val req = requestBuilder("api/checkins/$id")
        .patch(json.toRequestBody(WineAPI.JSON))
        .build()
    execute(req)
}

suspend fun WineAPI.replaceCheckinPhoto(id: Int, jpeg: ByteArray) {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart(
            "photo",
            "photo.jpg",
            jpeg.toRequestBody("image/jpeg".toMediaType())
        )
        .build()
    execute(requestBuilder("api/checkins/$id/photo").post(body).build())
}

suspend fun WineAPI.removeCheckinPhoto(id: Int) {
    execute(requestBuilder("api/checkins/$id/photo").delete().build())
}

/**
 * Gson renvoie un [com.google.gson.JsonNull] (≠ null Kotlin) pour `"key": null`.
 * `.asString` / `.asBoolean` sur JsonNull → UnsupportedOperationException("JsonNull").
 */

suspend fun WineAPI.createCheckin(
    barcode: String,
    wineName: String,
    producer: String,
    style: String,
    abv: String,
    summary: String,
    rating: Double,
    flavors: List<String>,
    hops: List<String>,
    comment: String,
    vivinoId: String,
    force: Boolean,
    photoJPEG: ByteArray? = null,
    location: String = "",
    vintage: Int? = null,
    region: String = "",
    country: String = "",
    rebuy: String? = null
): CreateCheckinResult = withContext(Dispatchers.IO) {
    var photoPath: String? = null
    if (photoJPEG != null && photoJPEG.isNotEmpty()) {
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "file",
                "photo.jpg",
                photoJPEG.toRequestBody("image/jpeg".toMediaType())
            )
            .build()
        val (upBody, upCode) = execute(requestBuilder("api/photo").post(body).build())
        if (upCode in 200..299) {
            try {
                val o = com.google.gson.JsonParser.parseString(upBody).asJsonObject
                photoPath = o.get("photo_path")?.asString
            } catch (_: Exception) {}
        }
    }
    val payload = mutableMapOf<String, Any?>(
        "wine_name" to wineName,
        "producer" to producer,
        "wine_color" to style.ifBlank { "autre" },
        "abv" to abv.toDoubleOrNull(),
        "rating" to rating,
        "flavors" to flavors,
        "comment" to comment.take(500),
        "location" to location.trim().take(300),
        "barcode" to barcode,
        "force" to force,
        "photo_path" to photoPath
    )
    if (vivinoId.isNotBlank()) {
        payload["vivino_id"] = vivinoId.toIntOrNull() ?: vivinoId
    }
    if (vintage != null && vintage > 0) payload["vintage"] = vintage
    if (region.isNotBlank()) payload["region"] = region.trim()
    if (country.isNotBlank()) payload["country"] = country.trim()
    if (rebuy != null) payload["rebuy"] = rebuy
    val json = gson.toJson(payload)
    val req = requestBuilder("api/checkins").post(json.toRequestBody(WineAPI.JSON)).build()
    val (body, code) = execute(req)
    // 409 duplicate
    if (code == 409) {
        return@withContext try {
            gson.fromJson(body, CreateCheckinResult::class.java)
        } catch (_: Exception) {
            CreateCheckinResult(ok = false, duplicate = true, error = body)
        } ?: CreateCheckinResult(ok = false, duplicate = true)
    }
    val decoded = try {
        // create returns full checkin row
        val o = com.google.gson.JsonParser.parseString(body).asJsonObject
        if (o.has("id")) {
            CreateCheckinResult(ok = true, id = o.get("id").asInt)
        } else {
            gson.fromJson(body, CreateCheckinResult::class.java)
        }
    } catch (_: Exception) {
        null
    } ?: throw WineAPI.ApiException("Réponse création illisible")
    if (decoded.ok != true && decoded.id == null) {
        throw WineAPI.ApiException(decoded.error ?: "Échec création")
    }
    decoded
}

/** Multipart convenience used by older wizard path */

suspend fun WineAPI.createCheckinMultipart(
    wineName: String,
    producer: String,
    style: String,
    rating: Double,
    comment: String?,
    photoFile: java.io.File? = null,
    barcode: String = "",
    vivinoId: Int? = null,
    flavors: List<String> = emptyList(),
    hops: List<String> = emptyList(),
    force: Boolean = false,
    location: String = "",
    rebuy: String? = null
): Int {
    val bytes = photoFile?.takeIf { it.exists() }?.readBytes()
    val result = createCheckin(
        barcode = barcode,
        wineName = wineName,
        producer = producer,
        style = style,
        abv = "",
        summary = "",
        rating = rating,
        flavors = flavors,
        hops = hops,
        comment = comment.orEmpty(),
        vivinoId = vivinoId?.toString().orEmpty(),
        force = force,
        photoJPEG = bytes,
        location = location,
        rebuy = rebuy
    )
    if (result.duplicate == true) {
        throw WineAPI.ApiException(
            "duplicate|${result.previousCheckin?.wineName.orEmpty()}|${result.previousCheckin?.rating ?: 0}|${result.previousCheckin?.createdAt.orEmpty()}",
            409
        )
    }
    return result.id ?: 0
}

/**
 * Download internal asset with auth cookies. Tries LAN first then current base.
 * External http(s) URLs use plain client without cookie injection issues.
 */

