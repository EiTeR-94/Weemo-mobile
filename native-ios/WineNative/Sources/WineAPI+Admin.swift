import Foundation

extension WineAPI {
    func adminUsers() async throws -> [AdminUser] {
        let (data, http, _) = try await request(path: "/api/admin/users", method: "GET", body: nil)
        if http.statusCode == 401 || http.statusCode == 403 {
            if http.statusCode == 401 { NotificationCenter.default.post(name: .beerAuthExpired, object: nil) }
            throw WineAPIError.unauthorized
        }
        // Backend renvoie tous les comptes, invités inclus (même logique que les
        // invités qui apparaissent aussi dans /api/admin/users pour les stats) —
        // l'onglet Comptes doit filtrer les invités (parité webapp admin.js:
        // `!username.startsWith("invite_")`), ils ont leur propre onglet Invités.
        func withoutInvites(_ list: [AdminUser]) -> [AdminUser] {
            list.filter { !$0.username.lowercased().hasPrefix("invite_") }
        }
        // Backend renvoie un array (parité Beer). Ancien format {users:[…]} encore accepté.
        if let list = try? JSONDecoder().decode([AdminUser].self, from: data) {
            return withoutInvites(list)
        }
        if let wrapped = try? JSONDecoder().decode(AdminUsersWrap.self, from: data) {
            return withoutInvites(wrapped.users)
        }
        return []
    }
    func adminCreateUser(username: String, password: String, isAdmin: Bool) async throws {
        let body = try JSONSerialization.data(withJSONObject: [
            "username": username,
            "password": password,
            "is_admin": isAdmin,
        ] as [String: Any])
        let (data, http, _) = try await request(path: "/api/admin/users", method: "POST", body: body, contentType: "application/json")
        if http.statusCode >= 400 {
            let msg = Self.extractAPIError(data) ?? "Création impossible"
            throw WineAPIError.server(msg)
        }
    }
    func adminDeleteUser(_ username: String) async throws {
        let (data, http, _) = try await request(path: "/api/admin/users/\(username)", method: "DELETE", body: nil)
        if http.statusCode >= 400 {
            let err = (try? JSONDecoder().decode(OKResponse.self, from: data))?.error
            throw WineAPIError.server(err ?? "Suppression impossible")
        }
    }
    func adminSetAdmin(_ username: String, isAdmin: Bool) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["is_admin": isAdmin])
        let (_, http, _) = try await request(
            path: "/api/admin/users/\(username)",
            method: "PATCH",
            body: body,
            contentType: "application/json"
        )
        if http.statusCode >= 400 { throw WineAPIError.server("Mise à jour impossible") }
    }
    func adminCleanupPhotos() async throws -> String {
        let (data, http, _) = try await request(path: "/api/admin/photos/cleanup", method: "POST", body: Data(), contentType: "application/json")
        if http.statusCode >= 400 { throw WineAPIError.server("Nettoyage impossible") }
        struct R: Decodable { let removed: Int?; let message: String? }
        let r = try? JSONDecoder().decode(R.self, from: data)
        return r?.message ?? "\(r?.removed ?? 0) photo(s) supprimée(s)"
    }
    func adminReferentials() async throws -> ReferentialsResponse {
        let (data, http, _) = try await request(path: "/api/admin/referentials", method: "GET", body: nil)
        if http.statusCode == 401 || http.statusCode == 403 {
            if http.statusCode == 401 { NotificationCenter.default.post(name: .beerAuthExpired, object: nil) }
            throw WineAPIError.unauthorized
        }
        guard let decoded = try? JSONDecoder().decode(ReferentialsResponse.self, from: data) else {
            throw WineAPIError.decode
        }
        return decoded
    }
    func adminAddFlavor(_ name: String) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["name": name, "kind": "arome"])
        let (_, http, _) = try await request(
            path: "/api/admin/referentials/flavors",
            method: "POST",
            body: body,
            contentType: "application/json"
        )
        if http.statusCode >= 400 { throw WineAPIError.server("Arôme non ajouté") }
    }
    func adminDeleteFlavor(id: Int) async throws {
        let (_, http, _) = try await request(
            path: "/api/admin/referentials/flavors/\(id)",
            method: "DELETE",
            body: nil
        )
        if http.statusCode >= 400 { throw WineAPIError.server("Suppression impossible") }
    }
    func adminAddRegion(_ name: String) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["name": name])
        let (_, http, _) = try await request(
            path: "/api/admin/referentials/regions",
            method: "POST",
            body: body,
            contentType: "application/json"
        )
        if http.statusCode >= 400 { throw WineAPIError.server("Région non ajoutée") }
    }
    func adminDeleteRegion(id: Int) async throws {
        let (_, http, _) = try await request(
            path: "/api/admin/referentials/regions/\(id)",
            method: "DELETE",
            body: nil
        )
        if http.statusCode >= 400 { throw WineAPIError.server("Suppression impossible") }
    }
    func adminSetPassword(_ username: String, password: String) async throws {
        let body = try JSONSerialization.data(withJSONObject: ["password": password])
        let (_, http, _) = try await request(
            path: "/api/admin/users/\(username)",
            method: "PATCH",
            body: body,
            contentType: "application/json"
        )
        if http.statusCode >= 400 { throw WineAPIError.server("Mot de passe non mis à jour") }
    }
}
