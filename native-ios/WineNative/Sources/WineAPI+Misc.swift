import Foundation

extension WineAPI {
    func vivinoSearch(query: String) async throws -> VivinoSearchResponse {
        // Weeno: GET /api/search?q= → { query, items, source } (Algolia Vivino côté serveur)
        var components = URLComponents(url: try url("/api/search"), resolvingAgainstBaseURL: true)!
        components.queryItems = [
            URLQueryItem(name: "q", value: query),
            URLQueryItem(name: "limit", value: "5"),
        ]
        var req = URLRequest(url: components.url!)
        return try await NetworkManager.shared.withRetry {
            let (data, http, _) = try await self.performTransport(req)
            try self.throwIfUnauthorized(http.statusCode)
            if let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
               let items = root["items"] as? [[String: Any]] {
                let hits = items.compactMap { Self.mapVivinoItem($0) }
                return VivinoSearchResponse(ok: true, error: nil, results: hits)
            }
            throw WineAPIError.decode
        }
    }
}
