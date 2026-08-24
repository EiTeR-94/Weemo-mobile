import Foundation

extension WineAPI {
    func login(username: String, password: String) async throws -> LoginResponse {
        enableInviteMode(false)
        InviteSessionStore.clear()
        setBaseURL(ServerSettings.lanApiBaseString)
        _ = await discoverWorkingEndpoint()
        if let cookies = HTTPCookieStorage.shared.cookies {
            cookies.forEach { HTTPCookieStorage.shared.deleteCookie($0) }
        }
        let body = try JSONEncoder().encode(["username": username, "password": password])
        var req = URLRequest(url: absURL("api/login"))
        req.httpMethod = "POST"
        req.setValue("application/json", forHTTPHeaderField: "Content-Type")
        req.setValue(Self.nativeClientValue, forHTTPHeaderField: Self.nativeClientHeader)
        req.setValue(Self.userAgentOwner, forHTTPHeaderField: "User-Agent")
        req.httpBody = body
        let (data, code, http, responseURL) = try await execute(req, allowUnauthorizedBody: true)
        if code == 403 {
            throw WineAPIError.server("Accès refusé — Wi‑Fi maison ou VPN Plexi requis pour les comptes principaux")
        }
        guard let decoded = try? JSONDecoder().decode(LoginResponse.self, from: data) else {
            throw WineAPIError.server("Réponse login invalide (HTTP \(code))")
        }
        if code == 401 || code >= 400 || decoded.ok == false {
            throw WineAPIError.server(decoded.error ?? "Identifiants incorrects")
        }
        if let setCookie = http.value(forHTTPHeaderField: "Set-Cookie"), !setCookie.isEmpty {
            let cookies = HTTPCookie.cookies(withResponseHeaderFields: ["Set-Cookie": setCookie], for: responseURL)
            for c in cookies { HTTPCookieStorage.shared.setCookie(c) }
        }
        if beerSessionCookieString() == nil {
            throw WineAPIError.server("Login OK mais cookie session absent. Réessaie.")
        }
        return decoded
    }
    /// Activation invité WAN — miroir Android `joinInvite` :
    /// candidates FQDN puis IPv4, transport IPv4+SNI unique, pas de cookies owner.
    /// `email` : saisi par l'invité (pré-enregistré côté admin), aucun indice côté UI.
    func joinInvite(inviteLink: String, email: String) async throws -> NativeJoinResponse {
        guard let token = InviteSessionStore.parseInviteToken(inviteLink) else {
            throw WineAPIError.server("Lien d'invitation invalide")
        }
        let emailClean = email.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !emailClean.isEmpty, emailClean.contains("@") else {
            throw WineAPIError.server("Email requis")
        }
        let deviceId = InviteSessionStore.deviceId
        if let cookies = HTTPCookieStorage.shared.cookies {
            cookies.forEach { HTTPCookieStorage.shared.deleteCookie($0) }
        }
        WineSessionStore.clear()

        let body = try JSONEncoder().encode([
            "token": token,
            "device_id": deviceId,
            "email": emailClean,
        ])
        var lastError: Error?

        // Weeno prod vs Weeno Quest alpha : base déduite du lien
        let candidates = ServerSettings.basesFromInviteLink(inviteLink)
        for candidate in candidates {
            do {
                setBaseURL(candidate)
                enableInviteMode(true)
                // URL join = base + api/native/join (beer ou beer-alpha)
                var req = URLRequest(url: absURL("api/native/join"))
                req.httpMethod = "POST"
                req.setValue("application/json", forHTTPHeaderField: "Content-Type")
                req.setValue(Self.nativeClientValue, forHTTPHeaderField: Self.nativeClientHeader)
                req.setValue(Self.userAgentInvite, forHTTPHeaderField: "User-Agent")
                req.setValue(deviceId, forHTTPHeaderField: "X-Wine-Device")
                req.httpBody = body

                let (data, code, _, _) = try await execute(req, allowUnauthorizedBody: true)
                guard let decoded = try? JSONDecoder().decode(NativeJoinResponse.self, from: data) else {
                    throw WineAPIError.server("Réponse join invalide (HTTP \(code))")
                }
                if code == 429 {
                    throw WineAPIError.server("Trop de tentatives — réessaie dans une minute")
                }
                if code == 403, decoded.error == "wrong_device" {
                    throw WineAPIError.server("Cette invitation est déjà liée à un autre téléphone")
                }
                if code >= 400 || !decoded.ok || (decoded.accessToken ?? "").isEmpty {
                    let msg: String
                    switch decoded.error {
                    case "invalid": msg = "Invitation invalide ou expirée"
                    case "invalid_device": msg = "Identifiant appareil invalide"
                    case "disabled": msg = "Invitations natives désactivées"
                    case "email_required": msg = "Email requis"
                    case "wrong_email": msg = "Email incorrect"
                    case "rate_limit": msg = "Trop de tentatives — réessaie dans une minute"
                    default: msg = decoded.error ?? "Activation impossible (HTTP \(code))"
                    }
                    // Erreurs métier : pas de retry sur autre endpoint
                    throw WineAPIError.server(msg)
                }
                InviteSessionStore.save(
                    accessToken: decoded.accessToken!,
                    user: decoded.user ?? "invite",
                    label: decoded.label,
                    expiresAt: decoded.expiresAt,
                    deviceId: decoded.deviceId ?? deviceId,
                    apiBase: candidate
                )
                enableInviteMode(true)
                setBaseURL(candidate)
                return decoded
            } catch let e as WineAPIError {
                lastError = e
                // 400/403/429 métier : stop (comme Android)
                let msg = e.errorDescription ?? ""
                if msg.contains("invalide") || msg.contains("liée") || msg.contains("Trop")
                    || msg.contains("désactiv") || msg.contains("appareil") {
                    throw e
                }
                // réseau : essayer candidat suivant
            } catch {
                lastError = error
            }
        }
        if let lastError { throw lastError }
        throw WineAPIError.server("Serveur injoignable en 4G/5G — réessaie")
    }
    func clearAllAuth() { clearSession() }

    func me() async throws -> MeResponse {
        let (data, http, _) = try await request(path: "/api/me", method: "GET", body: nil)
        // 401 = révoqué / expiré (serveur) — wipe Bearer invité
        if http.statusCode == 401 {
            if isInviteMode { InviteSessionStore.clear() }
            NotificationCenter.default.post(name: .beerAuthExpired, object: nil)
            throw WineAPIError.unauthorized
        }
        try throwIfUnauthorized(http.statusCode)
        if http.statusCode == 403 {
            if isInviteMode {
                InviteSessionStore.clear()
                throw WineAPIError.server("Invitation invalide ou expirée — demande un nouveau lien")
            }
            throw WineAPIError.forbidden
        }
        guard let decoded = try? JSONDecoder().decode(MeResponse.self, from: data) else {
            throw WineAPIError.decode
        }
        // Défense : 200 + user vide alors qu'on était en invite = session morte
        if isInviteMode, (decoded.resolvedUser ?? "").isEmpty {
            InviteSessionStore.clear()
            NotificationCenter.default.post(name: .beerAuthExpired, object: nil)
            throw WineAPIError.unauthorized
        }
        return decoded
    }
    func tutorialSeen() async throws -> Bool {
        let (data, http, _) = try await request(
            path: "/api/tutorial-seen",
            method: "POST",
            body: Data("{}".utf8),
            contentType: "application/json"
        )
        if http.statusCode >= 200 && http.statusCode < 300 {
            if let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
                return (obj["ok"] as? Bool) != false
            }
            return true
        }
        return false
    }
    func logout() async {
        if !isInviteMode {
            _ = try? await request(path: "/api/logout", method: "POST", body: nil)
        }
        clearAllAuth()
    }
}
