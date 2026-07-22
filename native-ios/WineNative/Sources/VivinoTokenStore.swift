import Foundation
import Security

/// Bearer Vivino session (Keychain, ThisDeviceOnly) — scan part de l'iPhone, pas du serveur.
/// Jamais loggé. Préfixe "Bearer " stripé à l'écriture.
enum VivinoTokenStore {
    private static let service = "fr.eiter.plexiwinebis.vivino"
    private static let accountToken = "bearer"
    private static let accountUserId = "user_id"

    static var bearer: String? {
        get { read(account: accountToken) }
        set {
            let cleaned = Self.normalizeBearer(newValue)
            if let cleaned {
                write(account: accountToken, value: cleaned)
            } else {
                delete(account: accountToken)
            }
        }
    }

    static var userId: String? {
        get { read(account: accountUserId) }
        set {
            let t = newValue?.trimmingCharacters(in: .whitespacesAndNewlines)
            if let t, !t.isEmpty {
                write(account: accountUserId, value: t)
            } else {
                delete(account: accountUserId)
            }
        }
    }

    static var isConfigured: Bool {
        !(bearer ?? "").isEmpty
    }

    /// Strip "Bearer " + whitespace.
    private static func normalizeBearer(_ value: String?) -> String? {
        guard var t = value?.trimmingCharacters(in: .whitespacesAndNewlines), !t.isEmpty else {
            return nil
        }
        if t.lowercased().hasPrefix("bearer ") {
            t = String(t.dropFirst(7)).trimmingCharacters(in: .whitespacesAndNewlines)
        }
        return t.isEmpty ? nil : t
    }

    // MARK: - Keychain (chiffré OS, non extrait hors appareil)

    private static func write(account: String, value: String) {
        let data = Data(value.utf8)
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
        var add = query
        add[kSecValueData as String] = data
        // AfterFirstUnlock + ThisDeviceOnly : pas de backup iCloud/iTunes du secret
        add[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        SecItemAdd(add as CFDictionary, nil)
    }

    private static func read(account: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var out: AnyObject?
        let st = SecItemCopyMatching(query as CFDictionary, &out)
        guard st == errSecSuccess, let data = out as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }

    private static func delete(account: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
        ]
        SecItemDelete(query as CFDictionary)
    }
}
