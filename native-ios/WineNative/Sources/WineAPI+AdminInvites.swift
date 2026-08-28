import Foundation

extension WineAPI {
    func adminInvites() async throws -> [InviteItem] {
        let (data, http, _) = try await request(path: "/api/invites", method: "GET", body: nil)
        if http.statusCode == 401 || http.statusCode == 403 {
            if http.statusCode == 401 { NotificationCenter.default.post(name: .beerAuthExpired, object: nil) }
            throw WineAPIError.unauthorized
        }
        return (try? JSONDecoder().decode([InviteItem].self, from: data)) ?? []
    }
    /// Admin : dégustations d'un invité (lecture seule).
    func adminInviteCheckins(inviteId: Int, limit: Int = 30, offset: Int = 0) async throws -> [CheckinItem] {
        let (data, http, _) = try await request(
            path: "/api/invites/\(inviteId)/checkins?limit=\(limit)&offset=\(offset)",
            method: "GET",
            body: nil
        )
        if http.statusCode == 401 || http.statusCode == 403 {
            if http.statusCode == 401 { NotificationCenter.default.post(name: .beerAuthExpired, object: nil) }
            throw WineAPIError.unauthorized
        }
        if http.statusCode == 404 {
            throw WineAPIError.server("Invitation introuvable")
        }
        return (try? JSONDecoder().decode([CheckinItem].self, from: data)) ?? []
    }
    func adminExtendInvite(id: Int, validity: String) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["validity": validity])
        let (data, http, _) = try await request(
            path: "/api/invites/\(id)/extend",
            method: "POST",
            body: body,
            contentType: "application/json"
        )
        if http.statusCode >= 400 {
            let err = (try? JSONDecoder().decode(OKResponse.self, from: data))?.error
            throw WineAPIError.server(err ?? "Prolongation impossible")
        }
    }
    func adminReissueInvite(id: Int) async throws -> String? {
        let (data, http, _) = try await request(path: "/api/invites/\(id)/reissue", method: "POST", body: Data(), contentType: "application/json")
        struct R: Decodable { let ok: Bool?; let url: String?; let error: String? }
        let decoded = try? JSONDecoder().decode(R.self, from: data)
        if http.statusCode >= 400 || decoded?.ok == false {
            throw WineAPIError.server(decoded?.error ?? "Réémission impossible")
        }
        return decoded?.url
    }
    func adminRevokeInvite(id: Int) async throws {
        let (_, http, _) = try await request(path: "/api/invites/\(id)", method: "DELETE", body: nil)
        if http.statusCode >= 400 { throw WineAPIError.server("Révocation impossible") }
    }
}
