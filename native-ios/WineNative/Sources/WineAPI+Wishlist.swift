import Foundation

extension WineAPI {
    func wishlist() async throws -> [WishlistItem] {
        let (data, http, _) = try await request(path: "/api/wishlist", method: "GET", body: nil)
        try throwIfUnauthorized(http.statusCode)
        if let arr = try? JSONDecoder().decode([WishlistItem].self, from: data) { return arr }
        if let root = try? JSONSerialization.jsonObject(with: data) as? [String: Any],
           let items = root["items"] as? [[String: Any]],
           let raw = try? JSONSerialization.data(withJSONObject: items) {
            return (try? JSONDecoder().decode([WishlistItem].self, from: raw)) ?? []
        }
        return []
    }
    func addWishlist(wineName: String, producer: String, style: String = "Unknown", barcode: String = "") async throws {
        let payload: [String: Any] = [
            "wine_name": wineName,
            "producer": producer,
            "wine_color": style,
            "barcode": barcode,
        ]
        let body = try JSONSerialization.data(withJSONObject: payload)
        let (data, http, _) = try await request(path: "/api/wishlist", method: "POST", body: body, contentType: "application/json")
        try throwIfUnauthorized(http.statusCode)
        if http.statusCode >= 400 {
            let err = (try? JSONDecoder().decode(OKResponse.self, from: data))?.error
            throw WineAPIError.server(err ?? "Échec wishlist")
        }
    }
    func deleteWishlist(id: Int) async throws {
        let (_, http, _) = try await request(path: "/api/wishlist/\(id)", method: "DELETE", body: nil)
        try throwIfUnauthorized(http.statusCode)
        if http.statusCode >= 400 { throw WineAPIError.server("Suppression impossible") }
    }
}
