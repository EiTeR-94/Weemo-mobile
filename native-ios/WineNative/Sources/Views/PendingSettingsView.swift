import SwiftUI

// Files d'attente offline + réglages/diagnostic — utilisées depuis MainView
// (Pending) et AdminSheetView (Settings).

// MARK: - Pending (2)

struct PendingSheetView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        NavigationView {
            List {
                Section("Créations en attente") {
                    if app.pendingItems.isEmpty {
                        Text("Aucune dégustation en attente.")
                            .foregroundStyle(Theme.muted)
                    } else {
                        ForEach(app.pendingItems) { pending in
                            VStack(alignment: .leading, spacing: 4) {
                                Text(pending.wineName)
                                    .font(.headline)
                                Text("\(pending.producer) · \(pending.style) · ★\(String(format: "%.1f", pending.rating))")
                                    .font(.subheadline)
                                    .foregroundStyle(Theme.muted)
                                if !pending.comment.isEmpty {
                                    Text(pending.comment)
                                        .font(.caption)
                                }
                                Text(pending.createdAt.formatted(date: .abbreviated, time: .omitted))
                                    .font(.caption2)
                                    .foregroundStyle(Theme.muted)
                            }
                            .swipeActions {
                                Button(role: .destructive) {
                                    app.removePending(id: pending.id)
                                } label: {
                                    Label("Supprimer", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
                Section("Suppressions en attente") {
                    if app.pendingDeletes.isEmpty {
                        Text("Aucune suppression en attente.")
                            .foregroundStyle(Theme.muted)
                    } else {
                        ForEach(app.pendingDeletes, id: \.self) { delId in
                            HStack {
                                Text("Suppression #\(delId)")
                                Spacer()
                                Text("en file")
                                    .font(.caption)
                                    .foregroundStyle(Theme.muted)
                            }
                            .swipeActions {
                                Button(role: .destructive) {
                                    app.removePendingDelete(id: delId)
                                } label: {
                                    Label("Annuler", systemImage: "trash")
                                }
                            }
                        }
                    }
                }
            }
            .navigationTitle("En attente (\(app.pendingCount))")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fermer") { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Synchroniser") {
                        Task {
                            await app.syncPending()
                            dismiss()
                        }
                    }
                    .disabled(app.pendingCount == 0)
                }
            }
        }
    }
}

// MARK: - Settings + Diagnostics (5)

struct SettingsSheetView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var diagnosticResult: String = ""
    @State private var isTesting = false

    var body: some View {
        NavigationView {
            Form {
                Section("Connexion") {
                    HStack {
                        Text("Endpoint actif")
                        Spacer()
                        Text(app.api.activeEndpoint.isEmpty ? "—" : app.api.activeEndpoint)
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                            .lineLimit(1)
                    }
                    HStack {
                        Text("Statut réseau")
                        Spacer()
                        Text(app.networkStatus.label)
                            .foregroundStyle(networkColor)
                    }
                    Button {
                        Task {
                            isTesting = true
                            diagnosticResult = await app.testServer()
                            isTesting = false
                        }
                    } label: {
                        HStack {
                            Text("Tester les endpoints")
                            if isTesting { ProgressView().scaleEffect(0.7) }
                        }
                    }
                    .disabled(isTesting)

                    if !diagnosticResult.isEmpty {
                        Text(diagnosticResult)
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                    }
                }

                Section("Cache & Offline") {
                    HStack {
                        Text("Éléments en attente")
                        Spacer()
                        Text("\(app.pendingCount)")
                    }
                    Button("Vider le cache offline") {
                        app.cache.clearAll()
                        app.cache.prune()
                        diagnosticResult = "Cache vidé + élagué."
                    }
                }

                Section("Sécurité") {
                    Text("Pinning activé pour le domaine (SPKI hash vérifié)")
                        .font(.caption)
                    Text("Politique domaine pour IPs LAN 192.168.x")
                        .font(.caption)
                        .foregroundStyle(Theme.muted)
                }

                Section("Diagnostic") {
                    Button("Rafraîchir tout (history + gallery + stats)") {
                        Task {
                            await app.bootstrap()
                            diagnosticResult = "Rafraîchi."
                        }
                    }
                    Text("Version serveur: \(app.serverVersion.isEmpty ? "inconnue" : app.serverVersion)")
                }

                Section("Application (Theme 2)") {
                    let marketing = Bundle.main.infoDictionary?["CFBundleShortVersionString"] as? String ?? "?"
                    let build = Bundle.main.infoDictionary?["CFBundleVersion"] as? String ?? "?"
                    HStack {
                        Text("Version")
                        Spacer()
                        Text("\(marketing) (\(build))")
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                    }
                    Text("Build exposé pour debug (corr. audit)")
                        .font(.caption2)
                        .foregroundStyle(Theme.muted)
                }
            }
            .navigationTitle("Paramètres & Diagnostic")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Fermer") { dismiss() }
                }
            }
        }
    }

    private var networkColor: Color {
        switch app.networkStatus {
        case .online: return Theme.ok
        case .serverUnreachable: return Theme.accent
        case .offline: return Theme.error
        }
    }
}

extension WineOfflineCache {
    func clearAll() {
        let fm = FileManager.default
        let base = FileManager.default.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("offline-cache", isDirectory: true)
        if let files = try? fm.contentsOfDirectory(at: dir, includingPropertiesForKeys: nil) {
            for f in files {
                try? fm.removeItem(at: f)
            }
        }
    }
}
