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

suspend fun WineAPI.lookup(barcode: String): LookupResponse {
    // Weeno n'a pas de lookup EAN OFF — recherche texte Vivino
    val q = java.net.URLEncoder.encode(barcode, "UTF-8")
    val (body, _) = execute(requestBuilder("api/search?q=$q&limit=5").get().build())
    try {
        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
        val items = root.getAsJsonArray("items")
        if (items != null && items.size() > 0) {
            val c0 = items[0].asJsonObject
            return LookupResponse(
                ok = true,
                barcode = barcode,
                wineName = c0.get("name")?.asString ?: c0.get("wine_name")?.asString,
                producer = c0.get("producer")?.asString ?: c0.get("winery")?.asString,
                style = c0.get("type")?.asString ?: c0.get("wine_color")?.asString,
                vivinoId = c0.get("id")?.asInt ?: c0.get("vivino_id")?.asInt,
                photoURL = c0.get("image")?.asString ?: c0.get("photo_url")?.asString,
                source = "vivino-search"
            )
        }
    } catch (_: Exception) {}
    return LookupResponse(ok = false, barcode = barcode, error = "Aucun résultat")
}

suspend fun WineAPI.styles(): List<StyleOption> {
    // Weeno: /api/config → colors: [{id, label}]
    return try {
        val (body, code) = execute(requestBuilder("api/config").get().build())
        if (code == 401) return emptyList()
        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
        val colors = root.getAsJsonArray("colors") ?: return emptyList()
        colors.mapNotNull { el ->
            val o = el.asJsonObject
            val id = o.get("id")?.asString ?: return@mapNotNull null
            val label = o.get("label")?.asString ?: id
            StyleOption(value = id, label = label)
        }
    } catch (_: Exception) {
        emptyList()
    }
}

suspend fun WineAPI.vivinoFetch(
    bid: Int,
    barcode: String = "",
    wineName: String = "",
    producer: String = "",
    vintage: Int? = null
): LookupResponse {
    // Weeno: GET /api/vivino/{wine_id} → { fields, suggested_flavors }
    if (bid <= 0) {
        return LookupResponse(ok = false, error = "vivino_id invalide", wineName = wineName, producer = producer)
    }
    var path = "api/vivino/$bid"
    if (vintage != null && vintage > 0) path += "?vintage=$vintage"
    val (body, code) = execute(requestBuilder(path).get().build())
    if (code >= 400) {
        return LookupResponse(ok = false, error = "Enrichissement Vivino KO", wineName = wineName, producer = producer, vivinoId = bid)
    }
    return try {
        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
        val o = root.getAsJsonObject("fields") ?: root
        val sug = root.getAsJsonArray("suggested_flavors")?.mapNotNull {
            try { it.asString } catch (_: Exception) { null }
        }
        LookupResponse(
            ok = true,
            wineName = o.get("wine_name")?.asString ?: wineName.ifBlank { null },
            producer = o.get("producer")?.asString ?: producer.ifBlank { null },
            style = o.get("wine_color")?.asString,
            styleFr = o.get("wine_color")?.asString,
            abv = o.get("abv")?.let { runCatching { it.asDouble }.getOrNull() },
            vivinoId = bid,
            photoURL = o.get("photo_url")?.asString,
            source = "vivino-enrich",
            barcode = barcode.ifBlank { null },
            summary = listOfNotNull(o.get("region")?.asString, o.get("country")?.asString).joinToString(" · ")
        ).also {
            // vintage/region via product mapping in wizard from fields
        }
    } catch (_: Exception) {
        LookupResponse(ok = false, error = "decode", vivinoId = bid)
    }
}

suspend fun WineAPI.visionStatus(probe: Boolean = false): WineAPI.VisionStatus = withContext(Dispatchers.IO) {
    try {
        val (body, code) = if (probe) {
            execute(requestBuilder("api/admin/vision-probe").post(ByteArray(0).toRequestBody()).build())
        } else {
            execute(requestBuilder("api/health").get().build())
        }
        if (code !in 200..299) return@withContext WineAPI.VisionStatus(false, 0, emptyList())
        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
        val v = root.getAsJsonObject("vision") ?: root
        val detail = mutableListOf<WineAPI.VisionKeyDetail>()
        v.getAsJsonArray("gemini_keys_detail")?.forEach { el ->
            val o = el.asJsonObject
            detail.add(
                WineAPI.VisionKeyDetail(
                    index = o.get("index")?.asInt ?: 0,
                    lastStatus = o.get("last_status")?.asString ?: "unknown",
                    rateLimited = o.get("rate_limited")?.asBoolean ?: false,
                    lastError = o.get("last_error")?.asString
                )
            )
        }
        WineAPI.VisionStatus(
            available = v.get("available")?.asBoolean ?: (detail.isNotEmpty()),
            keys = v.get("gemini_keys")?.asInt ?: detail.size,
            detail = detail
        )
    } catch (_: Exception) {
        WineAPI.VisionStatus(false, 0, emptyList())
    }
}

suspend fun WineAPI.configFlavors(): List<String> {
    return try {
        val (body, code) = execute(requestBuilder("api/config").get().build())
        if (code !in 200..299) return emptyList()
        val root = com.google.gson.JsonParser.parseString(body).asJsonObject
        root.getAsJsonArray("flavors")?.mapNotNull {
            try { it.asString } catch (_: Exception) { null }
        }.orEmpty()
    } catch (_: Exception) {
        emptyList()
    }
}

suspend fun WineAPI.flavors(style: String = "", description: String = ""): FlavorsResponse {
    val tags = configFlavors()
    return FlavorsResponse(flavors = tags, suggestedFlavors = emptyList(), showFlavorsBlock = true, showHopsBlock = false)
}

suspend fun WineAPI.flavorsAndHops(): FlavorsResponse = flavors()

suspend fun WineAPI.addHop(name: String) {
    // Weeno n'a pas de houblons — no-op (évite crash UI beer restante)
}

/** POST /api/label-scan — backend serveur configurable (Vivino-vision ou Gemini failover) + candidats Vivino. */

suspend fun WineAPI.labelScan(jpeg: ByteArray): LabelScanResult {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart(
            "file",
            "label.jpg",
            jpeg.toRequestBody("image/jpeg".toMediaType())
        )
        .build()
    val (respBody, code) = execute(requestBuilder("api/label-scan").post(body).build())
    if (code >= 400) {
        // Ne pas laisser Gson planter sur un body HTML/erreur : message clair
        val snippet = respBody.trim().take(160).ifBlank { "HTTP $code" }
        return LabelScanResult(
            ok = false,
            aiAvailable = false,
            aiError = "Scan KO ($code) — $snippet",
            hint = "Réessaie ou saisis / cherche sur Vivino."
        )
    }
    val root = try {
        com.google.gson.JsonParser.parseString(respBody).asJsonObject
    } catch (e: Exception) {
        return LabelScanResult(
            ok = false,
            aiAvailable = false,
            aiError = "Réponse scan illisible",
            hint = e.message
        )
    }
    val ai = root.get("ai")?.takeIf { it.isJsonObject }?.asJsonObject
    val fields = ai?.get("fields")?.takeIf { it.isJsonObject }?.asJsonObject
        ?: root.get("fields")?.takeIf { it.isJsonObject }?.asJsonObject
    val cands = mutableListOf<VivinoHit>()
    root.getAsJsonArray("candidates")?.forEach { el ->
        if (el != null && el.isJsonObject) {
            mapVivinoItem(el.asJsonObject)?.let { cands.add(it) }
        }
    }
    // Messages amicaux serveur (hint / ai.message / ai_error) — pas seulement le code "error"
    val hint = jsonString(root.get("hint"))
        ?: jsonString(root.get("ai_hint"))
        ?: jsonString(ai?.get("message"))
    val errCode = jsonString(ai?.get("error"))
    val aiError = hint
        ?: jsonString(root.get("ai_error"))
        ?: errCode
    return LabelScanResult(
        ok = jsonBool(root.get("ok"), default = true),
        aiAvailable = jsonBool(ai?.get("available"))
            || jsonBool(root.get("ai_available")),
        aiError = aiError,
        hint = hint,
        // champs IA + fallback plat racine (API /api/label-scan)
        wineName = jsonString(fields?.get("wine_name")) ?: jsonString(root.get("wine_name")),
        producer = jsonString(fields?.get("producer")) ?: jsonString(root.get("producer")),
        wineColor = jsonString(fields?.get("wine_color"))
            ?: jsonString(fields?.get("color"))
            ?: jsonString(root.get("wine_color")),
        vintage = jsonInt(fields?.get("vintage")) ?: jsonInt(root.get("vintage")),
        abv = jsonDouble(fields?.get("abv")) ?: jsonDouble(root.get("abv")),
        region = jsonString(fields?.get("region")) ?: jsonString(root.get("region")),
        candidates = cands,
        vivinoQuery = jsonString(root.get("vivino_query")),
        labelPhotoPath = jsonString(root.get("label_photo_path"))
    )
}

/** POST /api/label-memory/lookup — mémoire serveur partagée étiquette → vin, avant labelScan. */

suspend fun WineAPI.labelMemoryLookup(d: String, a: String): LabelMemoryMatch? {
    val json = gson.toJson(mapOf("d" to d, "a" to a))
    val (body, _) = execute(requestBuilder("api/label-memory/lookup").post(json.toRequestBody(WineAPI.JSON)).build())
    return try {
        gson.fromJson(body, LabelMemoryLookupResponse::class.java)?.match
    } catch (_: Exception) {
        null
    }
}

/** POST /api/label-memory/remember — enrichit la mémoire partagée après validation d'un vin (best-effort). */

suspend fun WineAPI.labelMemoryRemember(d: String, a: String, wine: WineProduct) {
    val payload = mapOf(
        "d" to d,
        "a" to a,
        "wine" to mapOf(
            "wine_name" to wine.wineName,
            "producer" to wine.producer,
            "vintage" to wine.vintage,
            "wine_color" to (wine.styleFr ?: wine.style),
            "region" to wine.region,
            "country" to wine.country,
            "abv" to wine.abv,
            "vivino_id" to wine.vivinoId,
            "photo_url" to wine.photoURL
        )
    )
    try {
        execute(requestBuilder("api/label-memory/remember").post(gson.toJson(payload).toRequestBody(WineAPI.JSON)).build())
    } catch (_: Exception) {
        // best-effort — comme le webapp, ne bloque jamais le wizard
    }
}

/** POST /api/label-memory/hit — renforce le score d'un match confirmé (best-effort). */

suspend fun WineAPI.labelMemoryHit(id: Int) {
    try {
        val json = gson.toJson(mapOf("id" to id))
        execute(requestBuilder("api/label-memory/hit").post(json.toRequestBody(WineAPI.JSON)).build())
    } catch (_: Exception) {
        // best-effort
    }
}

/** POST /api/label-memory/reject — signale un mauvais match (best-effort). */

suspend fun WineAPI.labelMemoryReject(id: Int, d: String? = null, a: String? = null) {
    try {
        val payload = mutableMapOf<String, Any?>("id" to id)
        if (d != null) payload["d"] = d
        if (a != null) payload["a"] = a
        execute(requestBuilder("api/label-memory/reject").post(gson.toJson(payload).toRequestBody(WineAPI.JSON)).build())
    } catch (_: Exception) {
        // best-effort
    }
}

suspend fun WineAPI.scanPhoto(jpeg: ByteArray): LookupResponse {
    val scan = labelScan(jpeg)
    val c0 = scan.candidates.firstOrNull()
    return LookupResponse(
        ok = scan.ok,
        error = scan.aiError,
        wineName = scan.wineName ?: c0?.wineName,
        producer = scan.producer ?: c0?.producer,
        style = scan.wineColor ?: c0?.styleFr,
        styleFr = scan.wineColor ?: c0?.styleFr,
        abv = scan.abv,
        summary = listOfNotNull(scan.producer, scan.wineName).joinToString(" — "),
        vivinoId = c0?.bid,
        source = "label-scan",
        photoURL = c0?.photoURL
    )
}

suspend fun WineAPI.decodeBarcode(jpeg: ByteArray): DecodeBarcodeResponse {
    val body = MultipartBody.Builder().setType(MultipartBody.FORM)
        .addFormDataPart(
            "image",
            "scan.jpg",
            jpeg.toRequestBody("image/jpeg".toMediaType())
        )
        .build()
    val (respBody, _) = execute(requestBuilder("api/decode-barcode").post(body).build())
    return gson.fromJson(respBody, DecodeBarcodeResponse::class.java)
}

suspend fun WineAPI.saveProduct(
    barcode: String,
    wineName: String,
    producer: String,
    style: String
): LookupResponse {
    val json = gson.toJson(
        mapOf(
            "barcode" to barcode,
            "wine_name" to wineName,
            "producer" to producer,
            "style" to style
        )
    )
    val (body, code) = execute(
        requestBuilder("api/products/save").post(json.toRequestBody(WineAPI.JSON)).build()
    )
    val decoded = gson.fromJson(body, LookupResponse::class.java)
    if (code >= 400 || decoded.ok == false) {
        throw WineAPI.ApiException(decoded.error ?: "Sauvegarde produit impossible", code)
    }
    return decoded
}

suspend fun WineAPI.linkProduct(
    bid: Int,
    barcode: String,
    wineName: String,
    producer: String
): LookupResponse {
    val json = gson.toJson(
        mapOf(
            "vivino_bid" to bid,
            "barcode" to barcode,
            "wine_name" to wineName,
            "producer" to producer
        )
    )
    val (body, code) = execute(
        requestBuilder("api/products/link").post(json.toRequestBody(WineAPI.JSON)).build()
    )
    val decoded = gson.fromJson(body, LookupResponse::class.java)
    if (code >= 400 || decoded.ok == false) {
        throw WineAPI.ApiException(decoded.error ?: "Liaison impossible", code)
    }
    return decoded
}

// ── Admin comptes / invites / outils (parité iOS) ────────────────────────

