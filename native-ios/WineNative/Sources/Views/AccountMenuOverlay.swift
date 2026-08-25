import SwiftUI

// Bannière de mise à jour + résumé de butin + panneau "Mon compte" —
// utilisées uniquement depuis MainView.

// MARK: - Bannière update discrète

struct AppUpdateBanner: View {
    let current: String
    let latest: String
    let portalURL: URL

    var body: some View {
        HStack(spacing: 10) {
            Text("⬆️")
                .font(.system(size: 14))
            VStack(alignment: .leading, spacing: 2) {
                Text("Mise à jour disponible")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Theme.text)
                Text("Tu as \(current) · dernière \(latest)")
                    .font(.system(size: 11))
                    .foregroundStyle(Theme.muted)
            }
            Spacer(minLength: 4)
            Link(destination: portalURL) {
                Text("Mettre à jour")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(Color(red: 0.07, green: 0.07, blue: 0.07))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .background(Theme.accent)
                    .clipShape(Capsule())
            }
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 9)
        .background(Theme.accent.opacity(0.1))
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.accent.opacity(0.35)))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Résumé butin

struct LootSummaryKey: Identifiable {
    let loot: RpgLoot
    var id: String {
        "\(loot.level ?? 0)-\(loot.xp ?? 0)-\(loot.xpGained ?? 0)-\((loot.badgesEarned ?? []).count)"
    }
}

struct LootSummarySheet: View {
    let loot: RpgLoot
    let onClose: () -> Void

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    HStack {
                        Text(loot.levelUp == true ? "🎉 LEVEL UP" : "✨ Butin")
                            .font(.system(size: 11, weight: .heavy))
                            .foregroundStyle(Theme.accent)
                            .tracking(1)
                        Spacer()
                    }
                    Text(loot.levelUp == true
                         ? (loot.phraseLevelUp ?? loot.phrase ?? "Niveau \(loot.level ?? 0) !")
                         : (loot.phrase ?? "Butin de dégustation"))
                        .font(.title3.weight(.bold))
                        .foregroundStyle(Theme.text)

                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible())], spacing: 8) {
                        lootTile("⚡", loot.xpGained.map { "+\($0)" } ?? "0", "XP gagnés")
                        lootTile("🏅", "Nv \(loot.level ?? 1)", loot.title ?? "Niveau")
                        if let toNext = loot.xpToNext {
                            lootTile("📈", "\(toNext)", "encore XP")
                        }
                        if let streak = loot.streakDays, streak > 0 {
                            lootTile("🔥", "\(streak)", "streak")
                        }
                    }

                    if loot.dailySoftCapped == true {
                        let day = loot.dailyXp.map(String.init) ?? "?"
                        let cap = loot.dailySoftCap.map(String.init) ?? "?"
                        VStack(alignment: .leading, spacing: 4) {
                            Text("⛔ Soft-cap journalier · \(day)/\(cap) XP")
                                .font(.subheadline.weight(.bold))
                                .foregroundStyle(Color.yellow.opacity(0.95))
                            Text(
                                loot.softCapMessage
                                    ?? "Plus d’XP aujourd’hui. Reviens demain — check-ins et badges restent ouverts."
                            )
                            .font(.caption)
                            .foregroundStyle(Theme.muted)
                            .fixedSize(horizontal: false, vertical: true)
                        }
                        .padding(10)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .background(Color.yellow.opacity(0.1))
                        .overlay(RoundedRectangle(cornerRadius: 10).stroke(Color.yellow.opacity(0.35)))
                        .clipShape(RoundedRectangle(cornerRadius: 10))
                    }

                    if let badges = loot.badgesEarned, !badges.isEmpty {
                        Text("Badges obtenus")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(Theme.text)
                        ForEach(badges) { b in
                            HStack {
                                Text(b.icon ?? "🏅")
                                Text(b.name ?? "Badge")
                                    .font(.subheadline.weight(.semibold))
                                    .foregroundStyle(Theme.text)
                                Spacer()
                                Text(rarityLabelFr(b.rarity))
                                    .font(.caption.weight(.bold))
                                    .foregroundStyle(Theme.accent)
                            }
                            .padding(10)
                            .background(Theme.card)
                            .clipShape(RoundedRectangle(cornerRadius: 10))
                        }
                    }

                    if let quests = loot.questsCompleted, !quests.isEmpty {
                        Text("Quêtes terminées")
                            .font(.subheadline.weight(.bold))
                            .foregroundStyle(Theme.text)
                        ForEach(quests) { q in
                            Text("📜 \(q.title ?? "Quête") · +\(q.rewardXp ?? 0) XP")
                                .font(.subheadline)
                                .foregroundStyle(Theme.muted)
                        }
                    }

                    Button(action: onClose) {
                        Text("Continuer")
                            .font(.system(size: 15, weight: .bold))
                            .foregroundStyle(Color(red: 0.07, green: 0.07, blue: 0.07))
                            .frame(maxWidth: .infinity)
                            .padding(.vertical, 12)
                            .background(LinearGradient(colors: [Theme.accent, .orange], startPoint: .leading, endPoint: .trailing))
                            .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                    .padding(.top, 6)
                }
                .padding(16)
            }
            .background(Theme.bg)
            .navigationTitle("Weeno")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fermer", action: onClose)
                }
            }
        }
    }

    private func lootTile(_ ico: String, _ v: String, _ l: String) -> some View {
        VStack(spacing: 3) {
            Text(ico)
            Text(v).font(.system(size: 15, weight: .bold)).foregroundStyle(Theme.text)
            Text(l).font(.system(size: 10, weight: .semibold)).foregroundStyle(Theme.muted)
        }
        .frame(maxWidth: .infinity)
        .padding(.vertical, 10)
        .background(Theme.card)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }
}

// MARK: - Mon compte (parité PWA)

struct AccountMenuOverlay: View {
    let connectedLabel: String
    let appVersionLine: String
    let needsUpdate: Bool
    let latestIos: String?
    let isCheckingMaj: Bool
    let isInvite: Bool
    let isAdmin: Bool
    let rpgActive: Bool
    let pendingCount: Int
    let portalURL: URL
    let onDismiss: () -> Void
    let onOpen: (WeenoSheet) -> Void
    let onCheckMaj: () -> Void
    let onFeedback: () -> Void
    let onLogout: () -> Void

    var body: some View {
        // ViewThatFits : panneau = hauteur contenu (s’arrête sous Déconnexion).
        // Si trop long pour l’écran → version scrollable plafonnée (~72 %).
        GeometryReader { geo in
            let maxPanelH = min(geo.size.height * 0.72, geo.size.height - 72)
            let maxPanelW = min(320.0, geo.size.width - 60)

            ZStack(alignment: .topTrailing) {
                Color.black.opacity(0.45)
                    .frame(width: geo.size.width, height: geo.size.height)
                    .ignoresSafeArea()
                    .onTapGesture { onDismiss() }

                ViewThatFits(in: .vertical) {
                    // 1) Contenu compact — pas de ScrollView → pas de vide
                    menuPanel(scroll: false, width: maxPanelW)
                    // 2) Trop long → scroll, hauteur max écran
                    menuPanel(scroll: true, width: maxPanelW)
                        .frame(maxHeight: maxPanelH, alignment: .top)
                }
                .padding(.top, 56)
                .padding(.trailing, 12)
            }
        }
        .ignoresSafeArea()
    }

    @ViewBuilder
    private func menuPanel(scroll: Bool, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Connecté")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                    Text(connectedLabel)
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Theme.text)
                    Text(appVersionLine)
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.muted)
                        .padding(.top, 1)
                }
                Spacer(minLength: 8)
                Button(action: onDismiss) {
                    Text("×")
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(Theme.muted)
                        .padding(4)
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 12)
            .padding(.bottom, 8)

            if scroll {
                ScrollView {
                    menuItems
                        .padding(.horizontal, 6)
                        .padding(.bottom, 12)
                }
            } else {
                menuItems
                    .padding(.horizontal, 6)
                    .padding(.bottom, 12)
            }
        }
        .frame(width: width, alignment: .leading)
        // Sans scroll : hauteur = contenu. Avec scroll : laisse le parent plafonner.
        .fixedSize(horizontal: true, vertical: !scroll)
        .background(Theme.card)
        .overlay(RoundedRectangle(cornerRadius: 16).stroke(Theme.border))
        .clipShape(RoundedRectangle(cornerRadius: 16))
    }

    @ViewBuilder
    private var menuItems: some View {
        VStack(alignment: .leading, spacing: 2) {
            section("Journal")
            item("📜 Historique") { onOpen(.history) }
            if !isInvite {
                item("🍷 À boire") { onOpen(.wishlist) }
                item("🎁 Idées cadeaux") { onOpen(.gifts) }
            }
            if rpgActive {
                item("📖 Grimoire") { onOpen(.grimoire) }
            }
            if pendingCount > 0 {
                item("⏳ En attente (\(pendingCount))") { onOpen(.pending) }
            }

            section("Aide")
            item("🎓 Tutoriel") { onOpen(.tutorial) }

            section("Parler à l’admin")
            item("💬 Un retour") { onFeedback() }

            if isAdmin {
                section("Admin")
                item("⚙️ Administration") { onOpen(.admin) }
                // Toujours visible admin : même si Weeno Quest est coupé (pour le rallumer)
                item("⚔ Quest") { onOpen(.rpgAdmin) }
                item("📝 Patch notes") { onOpen(.patchnotes) }
            }

            section("Application")
            item(isCheckingMaj ? "Check MAJ…" : "Check MAJ") { onCheckMaj() }
            if needsUpdate {
                item("⬆️ Installer maj IPA \(latestIos ?? "")") {
                    onDismiss()
                    UIApplication.shared.open(portalURL)
                }
            }

            section("Session")
            item("Déconnexion", danger: true) { onLogout() }
        }
    }

    private func section(_ title: String) -> some View {
        Text(title)
            .font(.system(size: 11, weight: .bold))
            .foregroundStyle(Theme.muted)
            .padding(.horizontal, 10)
            .padding(.top, 10)
            .padding(.bottom, 4)
    }

    private func item(_ title: String, danger: Bool = false, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(danger ? Theme.error : Theme.text)
                .frame(maxWidth: .infinity, alignment: .leading)
                .padding(.horizontal, 10)
                .padding(.vertical, 11)
        }
        .buttonStyle(.plain)
    }
}
