import Foundation

extension WineAPI {
    /// Weeno : pas de lookup EAN OFF — recherche texte Vivino (`/api/search`).
    func lookup(barcode: String) async throws -> LookupResponse {
        return try await searchWines(query: barcode)
    }
    func searchWines(query: String) async throws -> LookupResponse {
        var components = URLComponents(url: try url("/api/search"), resolvingAgainstBaseURL: true)!
        components.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "limit", value: "5"),
        ]
        var req = URLRequest(url: components.url!)
        let (data, http, _) = try await performTransport(req)
        try throwIfUnauthorized(http.statusCode)
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw WineAPIError.decode
        }
        let items = root["items"] as? [[String: Any]] ?? []
        guard let first = items.first else {
            return LookupResponse(
                ok: false, error: "Aucun résultat", barcode: query,
                wineName: nil, producer: nil, style: nil, styleFr: nil,
                abv: nil, summary: nil, vivinoBid: nil, source: "vivino-search", photoURL: nil
            )
        }
        let name = (first["name"] as? String) ?? (first["wine_name"] as? String)
        let producer = (first["producer"] as? String) ?? (first["winery"] as? String)
        let color = (first["type"] as? String) ?? (first["wine_color"] as? String)
        let vid = first["id"] as? Int ?? first["vivino_id"] as? Int
        let photo = (first["image"] as? String) ?? (first["photo_url"] as? String)
        return LookupResponse(
            ok: true, error: nil, barcode: query,
            wineName: name, producer: producer, style: color, styleFr: color,
            abv: first["abv"] as? Double, summary: [producer, name].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " — "),
            vivinoBid: vid, source: "vivino-search", photoURL: photo
        )
    }
    func styles() async throws -> [StyleOption] {
        // Weeno: couleurs dans /api/config.colors [{id,label}]
        let (data, http, _) = try await request(path: "/api/config", method: "GET", body: nil)
        if http.statusCode == 401 { return [] }
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else { return [] }
        let colors = root["colors"] as? [[String: Any]] ?? []
        return colors.compactMap { c in
            let id = (c["id"] as? String) ?? ""
            let label = (c["label"] as? String) ?? id
            guard !id.isEmpty else { return nil }
            return StyleOption(value: id, label: label)
        }
    }
    func saveProduct(barcode: String, wineName: String, producer: String, style: String) async throws -> LookupResponse {
        let payload: [String: Any] = [
            "barcode": barcode,
            "wine_name": wineName,
            "producer": producer,
            "style": style,
        ]
        let json = try JSONSerialization.data(withJSONObject: payload)
        let (data, http, _) = try await request(path: "/api/products/save", method: "POST", body: json, contentType: "application/json")
        try throwIfUnauthorized(http.statusCode)
        guard let decoded = try? JSONDecoder().decode(LookupResponse.self, from: data) else {
            throw WineAPIError.decode
        }
        if http.statusCode >= 400 || decoded.ok == false {
            throw WineAPIError.server(decoded.error ?? "Sauvegarde impossible")
        }
        return decoded
    }
    func linkProduct(bid: Int, barcode: String, wineName: String, producer: String) async throws -> LookupResponse {
        let payload: [String: Any] = [
            "vivino_bid": bid,
            "barcode": barcode,
            "wine_name": wineName,
            "producer": producer,
        ]
        let json = try JSONSerialization.data(withJSONObject: payload)
        let (data, http, _) = try await request(path: "/api/products/link", method: "POST", body: json, contentType: "application/json")
        try throwIfUnauthorized(http.statusCode)
        guard let decoded = try? JSONDecoder().decode(LookupResponse.self, from: data) else {
            throw WineAPIError.decode
        }
        if http.statusCode >= 400 || decoded.ok == false {
            throw WineAPIError.server(decoded.error ?? "Liaison impossible")
        }
        return decoded
    }
    func decodeBarcode(jpeg: Data) async throws -> DecodeBarcodeResponse {
        let boundary = "WeenoScan-\(UUID().uuidString)"
        var req = URLRequest(url: try url("/api/decode-barcode"))
        req.httpMethod = "POST"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.httpBody = makeMultipart(
            boundary: boundary,
            fields: [:],
            file: ("image", "scan.jpg", "image/jpeg", jpeg)
        )
        let (data, http, _) = try await performTransport(req)
        try throwIfUnauthorized(http.statusCode)
        guard let decoded = try? JSONDecoder().decode(DecodeBarcodeResponse.self, from: data) else {
            throw WineAPIError.decode
        }
        return decoded
    }
    /// POST /api/label-scan — backend serveur configurable (Vivino-vision ou Gemini failover) + candidats Vivino.
    func labelScan(jpeg: Data) async throws -> LabelScanResult {
        let boundary = "WeenoScan-\(UUID().uuidString)"
        var req = URLRequest(url: try url("/api/label-scan"))
        req.httpMethod = "POST"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.httpBody = makeMultipart(
            boundary: boundary,
            fields: [:],
            file: ("file", "label.jpg", "image/jpeg", jpeg)
        )
        let (data, http, _) = try await performTransport(req)
        try throwIfUnauthorized(http.statusCode)
        if http.statusCode >= 400 {
            let snippet = String(data: data, encoding: .utf8)?.trimmingCharacters(in: .whitespacesAndNewlines).prefix(160)
            return LabelScanResult(
                ok: false,
                aiAvailable: false,
                aiError: "Scan KO (\(http.statusCode))" + (snippet.map { " — \($0)" } ?? ""),
                hint: "Réessaie ou saisis / cherche sur Vivino.",
                wineName: nil, producer: nil, wineColor: nil, vintage: nil, abv: nil, region: nil,
                candidates: [], vivinoQuery: nil, labelPhotoPath: nil
            )
        }
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw WineAPIError.decode
        }
        let ai = root["ai"] as? [String: Any]
        let fields = (ai?["fields"] as? [String: Any])
            ?? (root["fields"] as? [String: Any])
            ?? [:]
        // Cast robuste : JSONSerialization donne souvent [Any] d’NSDictionary, pas [[String: Any]]
        let rawCands: [Any] = (root["candidates"] as? [Any]) ?? []
        let cands = rawCands.compactMap { el -> VivinoHit? in
            guard let dict = el as? [String: Any] else { return nil }
            return Self.mapVivinoItem(dict)
        }
        let hint = Self.jsonString(root["hint"])
            ?? Self.jsonString(root["ai_hint"])
            ?? Self.jsonString(ai?["message"])
        let errCode = Self.jsonString(ai?["error"])
        let aiError = hint ?? Self.jsonString(root["ai_error"]) ?? errCode
        return LabelScanResult(
            ok: Self.jsonBool(root["ok"], default: true),
            aiAvailable: Self.jsonBool(ai?["available"]) || Self.jsonBool(root["ai_available"]),
            aiError: aiError,
            hint: hint,
            wineName: Self.jsonString(fields["wine_name"]) ?? Self.jsonString(root["wine_name"]),
            producer: Self.jsonString(fields["producer"]) ?? Self.jsonString(root["producer"]),
            wineColor: Self.jsonString(fields["wine_color"])
                ?? Self.jsonString(fields["color"])
                ?? Self.jsonString(root["wine_color"]),
            vintage: Self.jsonInt(fields["vintage"]) ?? Self.jsonInt(root["vintage"]),
            abv: Self.jsonDouble(fields["abv"]) ?? Self.jsonDouble(root["abv"]),
            region: Self.jsonString(fields["region"]) ?? Self.jsonString(root["region"]),
            candidates: cands,
            vivinoQuery: Self.jsonString(root["vivino_query"]),
            labelPhotoPath: Self.jsonString(root["label_photo_path"])
        )
    }
    /// Compat: scan → LookupResponse (1er candidat / champs IA).
    func scanPhoto(jpeg: Data) async throws -> LookupResponse {
        let scan = try await labelScan(jpeg: jpeg)
        let c0 = scan.candidates.first
        return LookupResponse(
            ok: scan.ok,
            error: scan.aiError,
            barcode: nil,
            wineName: scan.wineName ?? c0?.wineName,
            producer: scan.producer ?? c0?.producer,
            style: scan.wineColor ?? c0?.styleFr,
            styleFr: scan.wineColor ?? c0?.styleFr,
            abv: scan.abv,
            summary: [scan.producer, scan.wineName].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " — "),
            vivinoBid: c0?.bid,
            source: "label-scan",
            photoURL: c0?.photoURL
        )
    }
    /// POST /api/label-memory/lookup — mémoire serveur partagée étiquette → vin, avant labelScan.
    func labelMemoryLookup(d: String, a: String) async throws -> LabelMemoryMatch? {
        let body = try JSONSerialization.data(withJSONObject: ["d": d, "a": a])
        let (data, http, _) = try await request(path: "/api/label-memory/lookup", method: "POST", body: body, contentType: "application/json")
        try throwIfUnauthorized(http.statusCode)
        guard http.statusCode < 400,
              let decoded = try? JSONDecoder().decode(LabelMemoryLookupResponse.self, from: data)
        else { return nil }
        return decoded.match
    }
    /// POST /api/label-memory/remember — enrichit la mémoire partagée après validation d'un vin (best-effort).
    func labelMemoryRemember(d: String, a: String, wine: WineProduct) async {
        var winePayload: [String: Any] = [
            "wine_name": wine.wineName,
            "producer": wine.producer,
            "wine_color": wine.styleFr ?? wine.style
        ]
        if let vintage = wine.vintage { winePayload["vintage"] = vintage }
        if let region = wine.region { winePayload["region"] = region }
        if let country = wine.country { winePayload["country"] = country }
        if let abv = wine.abv { winePayload["abv"] = abv }
        if let grapes = wine.grapes { winePayload["grapes"] = grapes }
        if let vivinoBid = wine.vivinoBid { winePayload["vivino_id"] = vivinoBid }
        if let photoURL = wine.photoURL { winePayload["photo_url"] = photoURL }
        let payload: [String: Any] = ["d": d, "a": a, "wine": winePayload]
        guard let body = try? JSONSerialization.data(withJSONObject: payload) else { return }
        _ = try? await request(path: "/api/label-memory/remember", method: "POST", body: body, contentType: "application/json")
    }
    /// POST /api/label-memory/hit — renforce le score d'un match confirmé (best-effort).
    func labelMemoryHit(id: Int) async {
        guard let body = try? JSONSerialization.data(withJSONObject: ["id": id]) else { return }
        _ = try? await request(path: "/api/label-memory/hit", method: "POST", body: body, contentType: "application/json")
    }
    /// POST /api/label-memory/reject — signale un mauvais match (best-effort).
    func labelMemoryReject(id: Int, d: String? = nil, a: String? = nil) async {
        var payload: [String: Any] = ["id": id]
        if let d { payload["d"] = d }
        if let a { payload["a"] = a }
        guard let body = try? JSONSerialization.data(withJSONObject: payload) else { return }
        _ = try? await request(path: "/api/label-memory/reject", method: "POST", body: body, contentType: "application/json")
    }
    /// Liste arômes presets + custom (GET /api/config).
    func configFlavors() async throws -> [String] {
        let (data, http, _) = try await request(path: "/api/config", method: "GET", body: nil)
        try throwIfUnauthorized(http.statusCode)
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
              let arr = root["flavors"] as? [String] else { return [] }
        return arr
    }
    /// Statut scan étiquette / clés (admin). `probe=true` teste réellement les clés.
    func visionStatus(probe: Bool = false) async throws -> VisionStatus {
        let path = probe ? "/api/admin/vision-probe" : "/api/health"
        let method = probe ? "POST" : "GET"
        let (data, http, _) = try await request(path: path, method: method, body: probe ? Data() : nil)
        try throwIfUnauthorized(http.statusCode)
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            return VisionStatus(available: false, keys: 0, detail: [])
        }
        // probe renvoie vision à la racine ; health sous "vision"
        let v = (root["vision"] as? [String: Any]) ?? root
        let detailArr = v["gemini_keys_detail"] as? [[String: Any]] ?? []
        let detail = detailArr.compactMap { d -> VisionKeyDetail? in
            guard let idx = d["index"] as? Int ?? (d["index"] as? NSNumber)?.intValue else { return nil }
            return VisionKeyDetail(
                index: idx,
                lastStatus: d["last_status"] as? String ?? "unknown",
                rateLimited: d["rate_limited"] as? Bool ?? false,
                lastError: d["last_error"] as? String
            )
        }
        return VisionStatus(
            available: v["available"] as? Bool ?? !detail.isEmpty,
            keys: v["gemini_keys"] as? Int ?? detail.count,
            detail: detail
        )
    }
    /// GET /api/vivino/{id} — enrichissement (fields + suggested_flavors).
    func vivinoFetch(
        bid: Int,
        barcode: String = "",
        wineName: String = "",
        producer: String = "",
        vintage: Int? = nil
    ) async throws -> LookupResponse {
        guard bid > 0 else {
            return LookupResponse(
                ok: false, error: "vivino_id invalide", barcode: barcode.ifEmptyNil,
                wineName: wineName.ifEmptyNil, producer: producer.ifEmptyNil,
                style: nil, styleFr: nil, abv: nil, summary: nil, vivinoBid: nil,
                source: nil, photoURL: nil
            )
        }
        var path = "/api/vivino/\(bid)"
        if let v = vintage, v > 0 { path += "?vintage=\(v)" }
        let (data, http, _) = try await request(path: path, method: "GET", body: nil)
        try throwIfUnauthorized(http.statusCode)
        guard let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any] else {
            throw WineAPIError.decode
        }
        let f = root["fields"] as? [String: Any] ?? root
        let suggested = root["suggested_flavors"] as? [String]
        return LookupResponse(
            ok: true,
            error: nil,
            barcode: barcode.ifEmptyNil,
            wineName: (f["wine_name"] as? String) ?? wineName.ifEmptyNil,
            producer: (f["producer"] as? String) ?? producer.ifEmptyNil,
            style: f["wine_color"] as? String,
            styleFr: f["wine_color"] as? String,
            abv: Self.jsonDouble(f["abv"]),
            summary: [f["region"] as? String, f["country"] as? String].compactMap { $0 }.joined(separator: " · "),
            vivinoBid: bid,
            source: "vivino-enrich",
            photoURL: f["photo_url"] as? String,
            vintage: Self.jsonInt(f["vintage"]) ?? vintage,
            region: f["region"] as? String,
            country: f["country"] as? String,
            suggestedFlavors: suggested
        )
    }
    func flavors(style: String, description: String = "") async throws -> FlavorsResponse {
        // Weeno n'a pas /api/flavors beer — tags depuis /api/config
        let tags = try await configFlavors()
        return FlavorsResponse(
            flavors: tags,
            suggestedFlavors: [],
            hops: nil,
            suggestedHops: nil,
            showFlavorsBlock: true,
            showHopsBlock: false
        )
    }
}
