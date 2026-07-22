import Foundation
import UIKit

/// Scan étiquette **direct iPhone → api.vivino.com** (Bearer Keychain).
/// Contrat observé mitm : POST /v/11.0.0/scans/label + GET /v/9.1.1/vintages/{id}
enum VivinoScanClient {
    private static let base = "https://api.vivino.com"
    private static let scanPath = "/v/11.0.0/scans/label"
    private static let ua = "Vivino regular/2026.29.0 (iPhone; iOS 18.0; Scale/3.00)"
    private static let maxBytes = 480 * 1024

    static func compressForVivino(_ jpeg: Data) -> Data {
        // Sous 0.5 Mo (limite API)
        var data = WineImageUtils.compressJPEG(jpeg, maxDimension: 1600, quality: 0.82)
        var q: CGFloat = 0.78
        while data.count > maxBytes, q > 0.35 {
            q -= 0.08
            data = WineImageUtils.compressJPEG(jpeg, maxDimension: 1400, quality: q)
        }
        if data.count > maxBytes {
            data = WineImageUtils.compressJPEG(jpeg, maxDimension: 1100, quality: 0.45)
        }
        return data
    }

    static func labelScan(jpeg: Data) async throws -> LabelScanResult {
        guard let token = VivinoTokenStore.bearer, !token.isEmpty else {
            return LabelScanResult(
                ok: false,
                aiAvailable: false,
                aiError: "Bearer Vivino manquant — Admin → Outils → coller le token",
                hint: "Configure le Bearer (session app Vivino) dans l’admin.",
                wineName: nil, producer: nil, wineColor: nil, vintage: nil, abv: nil, region: nil,
                candidates: [], vivinoQuery: nil, labelPhotoPath: nil
            )
        }
        let payload = compressForVivino(jpeg)
        var comps = URLComponents(string: base + scanPath)!
        var items: [URLQueryItem] = [
            .init(name: "app_version", value: "2026.29.0"),
            .init(name: "app_platform", value: "iphone"),
            .init(name: "app_phone", value: "iPhone18,3"),
            .init(name: "os_version", value: "18.0"),
            .init(name: "app_caller_origin", value: "default"),
            .init(name: "language", value: "fr"),
            .init(name: "image_type", value: "jpg"),
            .init(name: "label_ocr_source", value: "vision"),
            .init(name: "add_user_vintage", value: "false"),
            .init(name: "crop_x", value: "0"),
            .init(name: "crop_y", value: "0"),
            .init(name: "crop_width", value: "1"),
            .init(name: "crop_height", value: "1"),
        ]
        if let uid = VivinoTokenStore.userId, !uid.isEmpty {
            items.append(.init(name: "user_id", value: uid))
        }
        comps.queryItems = items
        guard let url = comps.url else { throw WineAPIError.decode }

        let boundary = "Boundary+\(UUID().uuidString.replacingOccurrences(of: "-", with: ""))"
        var body = Data()
        body.append("--\(boundary)\r\n".data(using: .utf8)!)
        body.append("Content-Disposition: form-data; name=\"image\"; filename=\"image\"\r\n".data(using: .utf8)!)
        body.append("Content-Type: image/jpeg\r\n\r\n".data(using: .utf8)!)
        body.append(payload)
        body.append("\r\n--\(boundary)--\r\n".data(using: .utf8)!)

        var req = URLRequest(url: url)
        req.httpMethod = "POST"
        req.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue(ua, forHTTPHeaderField: "User-Agent")
        req.setValue("*/*", forHTTPHeaderField: "Accept")
        req.setValue("fr;q=1", forHTTPHeaderField: "Accept-Language")
        req.httpBody = body
        req.timeoutInterval = 45

        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        if code == 401 || code == 403 {
            return LabelScanResult(
                ok: false, aiAvailable: false,
                aiError: "Token Vivino refusé (HTTP \(code)) — reconnecte l’app Vivino et mets à jour le Bearer",
                hint: "Bearer expiré ou révoqué.",
                wineName: nil, producer: nil, wineColor: nil, vintage: nil, abv: nil, region: nil,
                candidates: [], vivinoQuery: nil, labelPhotoPath: nil
            )
        }
        if code == 400 {
            let snip = String(data: data, encoding: .utf8)?.prefix(160) ?? ""
            return LabelScanResult(
                ok: false, aiAvailable: false,
                aiError: "Scan Vivino 400 — \(snip)",
                hint: "Image trop lourde ou format refusé.",
                wineName: nil, producer: nil, wineColor: nil, vintage: nil, abv: nil, region: nil,
                candidates: [], vivinoQuery: nil, labelPhotoPath: nil
            )
        }
        guard code >= 200, code < 300,
              let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any]
        else {
            return LabelScanResult(
                ok: false, aiAvailable: false,
                aiError: "Réponse scan illisible (HTTP \(code))",
                hint: nil,
                wineName: nil, producer: nil, wineColor: nil, vintage: nil, abv: nil, region: nil,
                candidates: [], vivinoQuery: nil, labelPhotoPath: nil
            )
        }

        let matchStatus = (root["match_status"] as? String) ?? ""
        let vintageId = WineAPI.jsonInt(root["vintage_id"])
        if matchStatus.lowercased() == "matched", let vid = vintageId, vid > 0 {
            if let detail = try? await fetchVintage(vid, token: token) {
                return detail
            }
            return LabelScanResult(
                ok: true, aiAvailable: true, aiError: nil,
                hint: "Vision Vivino Matched (vintage \(vid))",
                wineName: "Vintage #\(vid)", producer: nil, wineColor: nil,
                vintage: nil, abv: nil, region: nil,
                candidates: [], vivinoQuery: nil, labelPhotoPath: nil
            )
        }
        return LabelScanResult(
            ok: true, aiAvailable: false,
            aiError: nil,
            hint: "Vision Vivino : pas de match — cherche sur Vivino ou saisie manuelle.",
            wineName: nil, producer: nil, wineColor: nil, vintage: nil, abv: nil, region: nil,
            candidates: [], vivinoQuery: nil, labelPhotoPath: nil
        )
    }

    private static func fetchVintage(_ id: Int, token: String) async throws -> LabelScanResult {
        let url = URL(string: "\(base)/v/9.1.1/vintages/\(id)")!
        var req = URLRequest(url: url)
        req.setValue("Bearer \(token)", forHTTPHeaderField: "Authorization")
        req.setValue(ua, forHTTPHeaderField: "User-Agent")
        req.setValue("application/json", forHTTPHeaderField: "Accept")
        let (data, resp) = try await URLSession.shared.data(for: req)
        let code = (resp as? HTTPURLResponse)?.statusCode ?? 0
        // souvent public sans auth
        var payload = data
        if code >= 400 {
            var req2 = URLRequest(url: url)
            req2.setValue(ua, forHTTPHeaderField: "User-Agent")
            let (d2, _) = try await URLSession.shared.data(for: req2)
            payload = d2
        }
        guard let root = try? JSONSerialization.jsonObject(with: payload) as? [String: Any] else {
            throw WineAPIError.decode
        }
        let wine = root["wine"] as? [String: Any] ?? [:]
        let winery = wine["winery"] as? [String: Any] ?? [:]
        let region = wine["region"] as? [String: Any] ?? [:]
        let name = (wine["name"] as? String) ?? (root["name"] as? String)
        let producer = winery["name"] as? String
        let regionName = region["name"] as? String
        let wineId = WineAPI.jsonInt(wine["id"]) ?? 0
        let year = WineAPI.jsonInt(root["year"])
        let typeId = WineAPI.jsonInt(wine["type_id"])
        let color: String? = {
            switch typeId {
            case 1: return "rouge"
            case 2: return "blanc"
            case 3: return "effervescent"
            case 4: return "rose"
            case 7: return "fortifie"
            case 24: return "orange"
            default: return nil
            }
        }()
        let stats = root["statistics"] as? [String: Any]
        let rating = WineAPI.jsonDouble(stats?["ratings_average"])
        var photo: String?
        if let img = root["image"] as? [String: Any] {
            if let loc = img["location"] as? String {
                photo = loc.hasPrefix("//") ? "https:" + loc : loc
            } else if let vars = img["variations"] as? [String: Any],
                      let m = vars["medium"] as? String {
                photo = m.hasPrefix("//") ? "https:" + m : m
            }
        }
        let hit = VivinoHit(
            bid: wineId,
            wineName: name ?? "Vin",
            producer: producer,
            styleFr: color,
            photoURL: photo,
            vintage: year,
            country: nil,
            region: regionName,
            vivinoRating: rating,
            vivinoURL: wineId > 0 ? "https://www.vivino.com/wines/\(wineId)" : nil
        )
        return LabelScanResult(
            ok: true,
            aiAvailable: true,
            aiError: nil,
            hint: "Vision Vivino (scan iPhone) — \(matchHint(name, producer))",
            wineName: name,
            producer: producer,
            wineColor: color,
            vintage: year,
            abv: nil,
            region: regionName,
            candidates: [hit],
            vivinoQuery: [producer, name].compactMap { $0 }.joined(separator: " "),
            labelPhotoPath: nil
        )
    }

    private static func matchHint(_ name: String?, _ producer: String?) -> String {
        [producer, name].compactMap { $0 }.filter { !$0.isEmpty }.joined(separator: " · ")
    }
}
