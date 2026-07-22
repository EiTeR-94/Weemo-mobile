package fr.eiter.plexiwine

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.MultipartBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

/**
 * Scan étiquette **direct téléphone → api.vivino.com**.
 * Contrat mitm : POST /v/11.0.0/scans/label + GET vintages/{id}
 */
object VivinoScanClient {
    private const val BASE = "https://api.vivino.com"
    private const val SCAN = "/v/11.0.0/scans/label"
    private const val UA = "Vivino regular/2026.29.0 (Linux; Android 14)"
    private const val MAX_BYTES = 480 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    private fun compress(jpeg: ByteArray): ByteArray {
        var data = ImageUtils.compressJPEG(jpeg, maxDimension = 1600, quality = 82)
        var q = 78
        while (data.size > MAX_BYTES && q > 35) {
            q -= 8
            data = ImageUtils.compressJPEG(jpeg, maxDimension = 1400, quality = q)
        }
        if (data.size > MAX_BYTES) {
            data = ImageUtils.compressJPEG(jpeg, maxDimension = 1100, quality = 45)
        }
        return data
    }

    suspend fun labelScan(ctx: Context, jpeg: ByteArray): LabelScanResult = withContext(Dispatchers.IO) {
        val token = VivinoTokenStore.bearer(ctx)
        if (token.isNullOrEmpty()) {
            return@withContext LabelScanResult(
                ok = false,
                aiAvailable = false,
                aiError = "Bearer Vivino manquant — Admin → coller le token",
                hint = "Configure le Bearer (session app Vivino) dans l’admin."
            )
        }
        val payload = compress(jpeg)
        val uid = VivinoTokenStore.userId(ctx)
        val q = buildString {
            append("app_version=2026.29.0&app_platform=android&app_phone=Android&os_version=14")
            append("&app_caller_origin=default&language=fr&image_type=jpg&label_ocr_source=vision")
            append("&add_user_vintage=false&crop_x=0&crop_y=0&crop_width=1&crop_height=1")
            if (!uid.isNullOrEmpty()) append("&user_id=").append(uid)
        }
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", "image",
                payload.toRequestBody("image/jpeg".toMediaType())
            )
            .build()
        val req = Request.Builder()
            .url("$BASE$SCAN?$q")
            .post(body)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", UA)
            .header("Accept", "*/*")
            .header("Accept-Language", "fr;q=1")
            .build()
        client.newCall(req).execute().use { resp ->
            val code = resp.code
            val text = resp.body?.string().orEmpty()
            if (code == 401 || code == 403) {
                return@withContext LabelScanResult(
                    ok = false, aiAvailable = false,
                    aiError = "Token Vivino refusé (HTTP $code) — reconnecte l’app Vivino",
                    hint = "Bearer expiré ou révoqué."
                )
            }
            if (code == 400) {
                return@withContext LabelScanResult(
                    ok = false, aiAvailable = false,
                    aiError = "Scan Vivino 400 — ${text.take(160)}",
                    hint = "Image trop lourde ou format refusé."
                )
            }
            if (code !in 200..299) {
                return@withContext LabelScanResult(
                    ok = false, aiAvailable = false,
                    aiError = "Scan Vivino HTTP $code",
                    hint = text.take(120)
                )
            }
            val root = JSONObject(text)
            val matchStatus = root.optString("match_status", "")
            val vintageId = if (root.isNull("vintage_id")) null else root.optInt("vintage_id")
            if (matchStatus.equals("Matched", true) && vintageId != null && vintageId > 0) {
                return@withContext fetchVintage(token, vintageId)
            }
            return@withContext LabelScanResult(
                ok = true,
                aiAvailable = false,
                hint = "Vision Vivino : pas de match — cherche ou saisie manuelle."
            )
        }
    }

    private fun fetchVintage(token: String, vintageId: Int): LabelScanResult {
        val url = "$BASE/v/9.1.1/vintages/$vintageId"
        fun get(auth: Boolean): JSONObject? {
            val b = Request.Builder().url(url).get().header("User-Agent", UA).header("Accept", "application/json")
            if (auth) b.header("Authorization", "Bearer $token")
            return try {
                client.newCall(b.build()).execute().use { r ->
                    if (r.code !in 200..299) return null
                    JSONObject(r.body?.string().orEmpty())
                }
            } catch (_: Exception) {
                null
            }
        }
        val root = get(true) ?: get(false)
            ?: return LabelScanResult(
                ok = true, aiAvailable = true,
                hint = "Matched vintage $vintageId (détail indisponible)",
                wineName = "Vintage #$vintageId"
            )
        val wine = root.optJSONObject("wine") ?: JSONObject()
        val winery = wine.optJSONObject("winery") ?: JSONObject()
        val region = wine.optJSONObject("region") ?: JSONObject()
        val name = wine.optString("name").ifBlank { root.optString("name") }
        val producer = winery.optString("name").ifBlank { null }
        val regionName = region.optString("name").ifBlank { null }
        val wineId = wine.optInt("id", 0)
        val year = root.optString("year").toIntOrNull()
        val typeId = if (wine.isNull("type_id")) null else wine.optInt("type_id")
        val color = when (typeId) {
            1 -> "rouge"
            2 -> "blanc"
            3 -> "effervescent"
            4 -> "rose"
            7 -> "fortifie"
            24 -> "orange"
            else -> null
        }
        val stats = root.optJSONObject("statistics")
        val rating = stats?.optDouble("ratings_average")?.takeIf { !it.isNaN() }
        var photo: String? = null
        root.optJSONObject("image")?.let { img ->
            val loc = img.optString("location")
            if (loc.isNotBlank()) {
                photo = if (loc.startsWith("//")) "https:$loc" else loc
            } else {
                img.optJSONObject("variations")?.optString("medium")?.let { m ->
                    if (m.isNotBlank()) photo = if (m.startsWith("//")) "https:$m" else m
                }
            }
        }
        val hit = VivinoHit(
            bid = wineId,
            wineName = name.ifBlank { "Vin" },
            producer = producer,
            styleFr = color,
            photoURL = photo,
            vintage = year,
            region = regionName,
            vivinoRating = rating,
            vivinoURL = if (wineId > 0) "https://www.vivino.com/wines/$wineId" else null
        )
        return LabelScanResult(
            ok = true,
            aiAvailable = true,
            hint = "Vision Vivino (scan téléphone) — ${listOfNotNull(producer, name).joinToString(" · ")}",
            wineName = name.ifBlank { null },
            producer = producer,
            wineColor = color,
            vintage = year,
            region = regionName,
            candidates = listOf(hit),
            vivinoQuery = listOfNotNull(producer, name).joinToString(" ")
        )
    }
}
