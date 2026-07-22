package fr.eiter.plexiwine

import android.content.Context
import android.os.Build
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
 *
 * Compression unique ici (limite API ~0.5 Mo). Ne pas pré-compresser en amont.
 * Bearer jamais loggé.
 */
object VivinoScanClient {
    private const val BASE = "https://api.vivino.com"
    private const val SCAN = "/v/11.0.0/scans/label"
    private const val APP_VERSION = "2026.29.0"
    /** Limite soft sous le plafond Vivino IMAGE_TOO_LARGE (0.5 Mo). */
    private const val MAX_BYTES = 480 * 1024

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .build()

    /** UA proche app Vivino, version OS + modèle réels. */
    private fun userAgent(): String {
        val ver = Build.VERSION.RELEASE?.ifBlank { null } ?: "14"
        val model = Build.MODEL?.replace(Regex("\\s+"), "")?.ifBlank { null } ?: "Android"
        return "Vivino regular/$APP_VERSION (Linux; Android $ver; $model)"
    }

    private fun queryParams(uid: String?): String {
        val ver = Build.VERSION.RELEASE?.ifBlank { null } ?: "14"
        val model = Build.MODEL?.replace(Regex("\\s+"), "")?.ifBlank { null } ?: "Android"
        return buildString {
            append("app_version=$APP_VERSION")
            append("&app_platform=android")
            append("&app_phone=").append(model)
            append("&os_version=").append(ver)
            append("&app_caller_origin=default&language=fr&image_type=jpg&label_ocr_source=vision")
            append("&add_user_vintage=false&crop_x=0&crop_y=0&crop_width=1&crop_height=1")
            if (!uid.isNullOrEmpty()) append("&user_id=").append(uid)
        }
    }

    /**
     * Compression unique pour Vivino : une seule passe JPEG sous [MAX_BYTES].
     * Si l'entrée est déjà sous la limite, on ne ré-encode pas (évite double lossy).
     */
    private fun compressForVivino(jpeg: ByteArray): ByteArray {
        if (jpeg.size in 1..MAX_BYTES) return jpeg
        var data = ImageUtils.compressJPEG(jpeg, maxDimension = 1600, quality = 82)
        if (data.size <= MAX_BYTES) return data
        var q = 74
        while (data.size > MAX_BYTES && q > 35) {
            data = ImageUtils.compressJPEG(jpeg, maxDimension = 1400, quality = q)
            q -= 8
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
                aiError = "Bearer Vivino manquant",
                hint = "Admin → coller le Bearer (session app Vivino). Le scan part du téléphone, pas du serveur."
            )
        }
        val payload = compressForVivino(jpeg)
        val uid = VivinoTokenStore.userId(ctx)
        val ua = userAgent()
        val body = MultipartBody.Builder().setType(MultipartBody.FORM)
            .addFormDataPart(
                "image", "image",
                payload.toRequestBody("image/jpeg".toMediaType())
            )
            .build()
        val req = Request.Builder()
            .url("$BASE$SCAN?${queryParams(uid)}")
            .post(body)
            .header("Authorization", "Bearer $token")
            .header("User-Agent", ua)
            .header("Accept", "*/*")
            .header("Accept-Language", "fr;q=1")
            .build()
        try {
            client.newCall(req).execute().use { resp ->
                val code = resp.code
                val text = resp.body?.string().orEmpty()
                if (code == 401 || code == 403) {
                    return@withContext LabelScanResult(
                        ok = false, aiAvailable = false,
                        aiError = "Token Vivino refusé (HTTP $code)",
                        hint = "Reconnecte l’app Vivino, capture un nouveau Bearer, puis Admin → Enregistrer."
                    )
                }
                if (code == 400) {
                    val snip = text.take(120)
                    val tooLarge = snip.contains("IMAGE_TOO_LARGE", ignoreCase = true)
                        || snip.contains("too large", ignoreCase = true)
                    return@withContext LabelScanResult(
                        ok = false, aiAvailable = false,
                        aiError = if (tooLarge) "Image encore trop lourde pour Vivino" else "Scan Vivino 400",
                        hint = if (tooLarge) "Recadre plus près ou photo manuelle moins nette."
                        else "Format refusé — réessaie ou saisie manuelle."
                    )
                }
                if (code !in 200..299) {
                    return@withContext LabelScanResult(
                        ok = false, aiAvailable = false,
                        aiError = "Scan Vivino HTTP $code",
                        hint = "Réseau ou API Vivino — réessaie dans un instant."
                    )
                }
                val root = JSONObject(text)
                val matchStatus = root.optString("match_status", "")
                val vintageId = if (root.isNull("vintage_id")) null else root.optInt("vintage_id")
                if (matchStatus.equals("Matched", true) && vintageId != null && vintageId > 0) {
                    return@withContext fetchVintage(token, vintageId, ua)
                }
                return@withContext LabelScanResult(
                    ok = true,
                    aiAvailable = false,
                    hint = "Vision Vivino : pas de match — cherche le vin ou saisie manuelle."
                )
            }
        } catch (e: Exception) {
            return@withContext LabelScanResult(
                ok = false, aiAvailable = false,
                aiError = "Réseau scan Vivino",
                hint = e.message?.take(80) ?: "Vérifie la connexion (Wi‑Fi / données)."
            )
        }
    }

    private fun fetchVintage(token: String, vintageId: Int, ua: String): LabelScanResult {
        val url = "$BASE/v/9.1.1/vintages/$vintageId"
        fun get(auth: Boolean): JSONObject? {
            val b = Request.Builder().url(url).get().header("User-Agent", ua).header("Accept", "application/json")
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
