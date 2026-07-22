import Foundation
import Security

/// Bearer Vivino session (Keychain) — scan part de l'iPhone, pas du serveur.
enum VivinoTokenStore {
    private static let service = "fr.eiter.plexiwinebis.vivino"
    private static let accountToken = "bearer"
    private static let accountUserId = "user_id"

    static var bearer: String? {
        get { read(account: accountToken) }
        set {
            if let newValue, !newValue.isEmpty {
                write(account: accountToken, value: newValue.trimmingCharacters(in: .whitespacesAndNewlines))
            } else {
                delete(account: accountToken)
            }
        }
    }

    static var userId: String? {
        get { read(account: accountUserId) }
        set {
            if let newValue, !newValue.isEmpty {
                write(account: accountUserId, value: newValue.trimmingCharacters(in: .whitespacesAndNewlines))
            } else {
                delete(account: accountUserId)
            }
        }
    }

    static var isConfigured: Bool {
        !(bearer ?? "").isEmpty
    }

    // MARK: - Keychain

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
