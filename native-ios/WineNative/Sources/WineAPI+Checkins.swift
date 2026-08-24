import Foundation

extension WineAPI {
    func checkins(
        q: String = "",
        style: String = "",
        minRating: Double = 0,
        period: String = "",
        limit: Int = 10,
        offset: Int = 0
    ) async throws -> [CheckinItem] {
        var components = URLComponents(url: try url("/api/checkins"), resolvingAgainstBaseURL: true)!
        var items = [
            URLQueryItem(name: "limit", value: String(limit)),
            URLQueryItem(name: "offset", value: String(offset)),
        ]
        if !q.isEmpty { items.append(URLQueryItem(name: "q", value: q)) }
        if !style.isEmpty { items.append(URLQueryItem(name: "wine_color", value: style)) }
        if minRating > 0 { items.append(URLQueryItem(name: "min_rating", value: String(minRating))) }
        if !period.isEmpty { items.append(URLQueryItem(name: "period", value: period)) }
        components.queryItems = items
        var req = URLRequest(url: components.url!)
        let (data, http, _) = try await performTransport(req)
        try throwIfUnauthorized(http.statusCode)
        // Weeno: { items: [...], count, limit, offset }
        if let wrapped = try? JSONDecoder().decode(CheckinsListResponse.self, from: data) {
            return wrapped.items ?? []
        }
        if let decoded = try? JSONDecoder().decode([CheckinItem].self, from: data) {
            return decoded
        }
        throw WineAPIError.decode
    }
    func stats() async throws -> HistoryStats {
        let (data, http, _) = try await request(path: "/api/stats", method: "GET", body: nil)
        try throwIfUnauthorized(http.statusCode)
        guard let decoded = try? JSONDecoder().decode(HistoryStats.self, from: data) else {
            throw WineAPIError.decode
        }
        return decoded
    }
    func coupleStats() async throws -> CoupleStats {
        let (data, http, _) = try await request(path: "/api/stats/couple", method: "GET", body: nil)
        try throwIfUnauthorized(http.statusCode)
        guard let decoded = try? JSONDecoder().decode(CoupleStats.self, from: data) else {
            throw WineAPIError.decode
        }
        return decoded
    }
    func deleteCheckin(id: Int) async throws {
        let (_, http, _) = try await request(path: "/api/checkins/\(id)", method: "DELETE", body: nil)
        try throwIfUnauthorized(http.statusCode)
        if http.statusCode >= 400 { throw WineAPIError.server("Suppression impossible") }
    }
    func updateCheckin(
        id: Int,
        rating: Double?,
        flavors: [String]?,
        hops: [String]?,
        comment: String?,
        hiddenFromPartner: Bool?,
        location: String? = nil,
        rebuy: String? = nil
    ) async throws {
        var payload: [String: Any] = [:]
        if let rating { payload["rating"] = rating }
        if let flavors { payload["flavors"] = flavors }
        if let hops { payload["hops"] = hops }
        if let comment { payload["comment"] = comment }
        if let location { payload["location"] = location }
        if let hiddenFromPartner { payload["hidden_from_partner"] = hiddenFromPartner }
        if let rebuy { payload["rebuy"] = rebuy }
        let body = try JSONSerialization.data(withJSONObject: payload)
        let (data, http, _) = try await request(
            path: "/api/checkins/\(id)",
            method: "PATCH",
            body: body,
            contentType: "application/json"
        )
        try throwIfUnauthorized(http.statusCode)
        if http.statusCode >= 400 {
            let err = (try? JSONDecoder().decode(OKResponse.self, from: data))?.error
            throw WineAPIError.server(err ?? "Modification impossible")
        }
    }
    func replaceCheckinPhoto(id: Int, jpeg: Data) async throws {
        let boundary = "WeenoPhoto-\(UUID().uuidString)"
        var req = URLRequest(url: try url("/api/checkins/\(id)/photo"))
        req.httpMethod = "POST"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.httpBody = makeMultipart(
            boundary: boundary,
            fields: [:],
            file: ("photo", "photo.jpg", "image/jpeg", jpeg)
        )
        let (_, http, _) = try await performTransport(req)
        try throwIfUnauthorized(http.statusCode)
        if http.statusCode == 403 { throw WineAPIError.forbidden }
        if http.statusCode >= 400 { throw WineAPIError.server("Photo impossible") }
    }
    func removeCheckinPhoto(id: Int) async throws {
        let (_, http, _) = try await request(path: "/api/checkins/\(id)/photo", method: "DELETE", body: nil)
        try throwIfUnauthorized(http.statusCode)
    }
    func createCheckin(
        barcode: String,
        wineName: String,
        producer: String,
        style: String,
        abv: String,
        summary: String,
        rating: Double,
        flavors: [String],
        hops: [String],
        comment: String,
        vivinoBid: String,
        force: Bool,
        photoJPEG: Data? = nil,
        location: String = "",
        vintage: Int? = nil,
        region: String = "",
        country: String = "",
        rebuy: String? = nil
    ) async throws -> CreateCheckinResult {
        // Weeno: JSON POST /api/checkins + photo optionnelle via /api/photo
        var photoPath: String? = nil
        if let jpeg = photoJPEG, !jpeg.isEmpty {
            let boundary = "WeenoPhoto-\(UUID().uuidString)"
            var up = URLRequest(url: try url("/api/photo"))
            up.httpMethod = "POST"
            up.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
            up.httpBody = makeMultipart(
                boundary: boundary,
                fields: [:],
                file: ("file", "photo.jpg", "image/jpeg", jpeg)
            )
            let (upData, upHttp, _) = try await performTransport(up)
            try throwIfUnauthorized(upHttp.statusCode)
            if (200..<300).contains(upHttp.statusCode),
               let obj = try? JSONSerialization.jsonObject(with: upData) as? [String: Any] {
                photoPath = obj["photo_path"] as? String
            }
        }
        let loc = String(location.trimmingCharacters(in: .whitespacesAndNewlines).prefix(300))
        var payload: [String: Any] = [
            "wine_name": wineName,
            "producer": producer,
            "wine_color": style.isEmpty ? "autre" : style,
            "rating": rating,
            "flavors": flavors,
            "comment": String(comment.prefix(500)),
            "location": loc,
            "barcode": barcode,
            "force": force,
        ]
        if let abvD = Double(abv) { payload["abv"] = abvD }
        if let vid = Int(vivinoBid), vid > 0 { payload["vivino_id"] = vid }
        if let photoPath { payload["photo_path"] = photoPath }
        if let vintage, vintage > 0 { payload["vintage"] = vintage }
        let reg = region.trimmingCharacters(in: .whitespacesAndNewlines)
        if !reg.isEmpty { payload["region"] = reg }
        let ctry = country.trimmingCharacters(in: .whitespacesAndNewlines)
        if !ctry.isEmpty { payload["country"] = ctry }
        if let rebuy { payload["rebuy"] = rebuy }
        let json = try JSONSerialization.data(withJSONObject: payload)
        let (data, http, _) = try await request(
            path: "/api/checkins",
            method: "POST",
            body: json,
            contentType: "application/json"
        )
        try throwIfUnauthorized(http.statusCode)
        if http.statusCode == 403 { throw WineAPIError.forbidden }
        if http.statusCode == 409 {
            return (try? JSONDecoder().decode(CreateCheckinResult.self, from: data))
                ?? CreateCheckinResult(ok: false, id: nil, duplicate: true, error: "Doublon", previousCheckin: nil, rpg: nil)
        }
        // create renvoie la row checkin {id, wine_name, ...}
        if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any], let id = obj["id"] as? Int {
            return CreateCheckinResult(ok: true, id: id, duplicate: false, error: nil, previousCheckin: nil, rpg: nil)
        }
        if let decoded = try? JSONDecoder().decode(CreateCheckinResult.self, from: data) {
            if http.statusCode >= 400 {
                throw WineAPIError.server(decoded.error ?? "Échec enregistrement")
            }
            return decoded
        }
        throw WineAPIError.decode
    }
}
