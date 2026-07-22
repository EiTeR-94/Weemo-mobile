import SwiftUI

// MARK: - Overlay shell (history-head, wishlist-head, admin-head…)

struct WeenoOverlayScreen<Content: View>: View {
    @EnvironmentObject private var app: AppModel

    let title: String
    let onClose: () -> Void
    var trailing: [WeenoHeadAction] = []
    var onRefresh: (() async -> Void)?
    @ViewBuilder let content: () -> Content

    var body: some View {
        ZStack {
            VStack(spacing: 0) {
                WeenoOverlayHead(title: title, onClose: onClose, trailing: trailing)
                ScrollView {
                    content()
                        .padding(.horizontal, 16)
                        .padding(.bottom, 20)
                }
                .scrollDismissesKeyboard(.interactively)
                .dismissKeyboardOnTap()
                .refreshable {
                    if let onRefresh { await onRefresh() }
                }
            }
            .background(Theme.bg)

            ToastOverlay(toast: app.toast, onDismiss: { app.hideToast() })
        }
        .preferredColorScheme(.dark)
    }
}

struct WeenoHeadAction: Identifiable {
    let id = UUID()
    let title: String
    let primary: Bool
    let handler: () -> Void

    static func ghost(_ title: String, handler: @escaping () -> Void) -> WeenoHeadAction {
        WeenoHeadAction(title: title, primary: false, handler: handler)
    }

    static func primary(_ title: String, handler: @escaping () -> Void) -> WeenoHeadAction {
        WeenoHeadAction(title: title, primary: true, handler: handler)
    }
}

struct WeenoOverlayHead: View {
    let title: String
    let onClose: () -> Void
    var trailing: [WeenoHeadAction] = []

    var body: some View {
        HStack(alignment: .center, spacing: 8) {
            Text(title)
                .font(.system(size: Theme.Font.h1, weight: .bold))
                .foregroundStyle(Theme.text)
            Spacer(minLength: 4)
            HStack(spacing: 6) {
                ForEach(trailing) { action in
                    if action.primary {
                        Button(action.title, action: action.handler)
                            .font(.system(size: Theme.Font.ghost, weight: .semibold))
                            .padding(.horizontal, 12)
                            .padding(.vertical, 8)
                            .background(Theme.primaryGradient)
                            .foregroundStyle(Theme.btnPrimaryText)
                            .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.btn))
                    } else {
                        WeenoGhostButton(action.title, action: action.handler)
                    }
                }
                WeenoGhostButton("Fermer", action: onClose)
            }
        }
        .padding(.horizontal, 16)
        .padding(.top, 8)
        .padding(.bottom, 6)
        .background(Theme.bg)
        .overlay(alignment: .bottom) {
            Rectangle().fill(Theme.border).frame(height: 1)
        }
    }
}

// MARK: - Filtres PWA (grille 3 colonnes + recherche)

struct WeenoFilterLabel<Content: View>: View {
    let label: String
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11))
                .foregroundStyle(Theme.muted)
            content()
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }
}

/// Menu déroulant joli (champ formulaire) — une valeur.
struct WeenoFormSelectField: View {
    let label: String
    let value: String
    let options: [(String, String)]
    let onSelect: (String) -> Void
    var placeholder: String = "Choisir…"

    private var display: String {
        options.first(where: { $0.0 == value })?.1 ?? placeholder
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 6) {
            if !label.isEmpty {
                Text(label)
                    .font(.system(size: Theme.Font.field))
                    .foregroundStyle(Theme.muted)
            }
            Menu {
                ForEach(options, id: \.0) { opt in
                    Button {
                        onSelect(opt.0)
                    } label: {
                        HStack {
                            Text(opt.1)
                            if opt.0 == value {
                                Spacer()
                                Image(systemName: "checkmark")
                            }
                        }
                    }
                }
            } label: {
                HStack(spacing: 8) {
                    Text(display)
                        .lineLimit(1)
                        .foregroundStyle(value.isEmpty ? Theme.muted : Theme.text)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 12, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                }
                .font(.system(size: 15))
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.fieldBg)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 0.5))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
        }
    }
}

/// Menu multi-sélection tags prédéfinis — sheet compact téléphone (pas chips).
struct WeenoTagDropdownField: View {
    let label: String
    let tags: [String]
    @Binding var selected: Set<String>
    var maxCount: Int = 8
    var suggested: Set<String> = []

    @State private var open = false
    @State private var filter = ""
    /// Hauteur réduite par défaut (téléphone) ; on peut tirer un peu plus.
    @State private var sheetDetent: PresentationDetent = .height(280)

    private var filtered: [String] {
        let q = filter.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        if q.isEmpty { return tags }
        return tags.filter { $0.lowercased().contains(q) }
    }

    private var summary: String {
        if selected.isEmpty { return "Ajouter un tag prédéfini…" }
        let list = Array(selected).sorted()
        if list.count <= 2 { return list.joined(separator: ", ") }
        return "\(list.count) tags sélectionnés"
    }

    var body: some View {
        VStack(alignment: .leading, spacing: 4) {
            if !label.isEmpty {
                Text(label)
                    .font(.system(size: Theme.Font.field))
                    .foregroundStyle(Theme.muted)
            }
            Button {
                filter = ""
                sheetDetent = .height(280)
                open = true
            } label: {
                HStack(spacing: 8) {
                    Text(summary)
                        .lineLimit(1)
                        .foregroundStyle(selected.isEmpty ? Theme.muted : Theme.text)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.up.chevron.down")
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                }
                .font(.system(size: 14))
                .padding(.horizontal, 10)
                .padding(.vertical, 9)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(Theme.fieldBg)
                .overlay(RoundedRectangle(cornerRadius: 10).stroke(Theme.border, lineWidth: 0.5))
                .clipShape(RoundedRectangle(cornerRadius: 10))
            }
            .buttonStyle(.plain)
            if !selected.isEmpty {
                VStack(alignment: .leading, spacing: 2) {
                    ForEach(Array(selected).sorted(), id: \.self) { tag in
                        HStack(spacing: 6) {
                            Text(tag)
                                .font(.system(size: 12))
                                .foregroundStyle(Theme.accent)
                                .lineLimit(1)
                            Spacer(minLength: 0)
                            Button("×") {
                                selected.remove(tag)
                            }
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(Theme.muted)
                            .buttonStyle(.plain)
                        }
                    }
                }
                .padding(.top, 2)
            }
        }
        .sheet(isPresented: $open) {
            NavigationStack {
                VStack(spacing: 0) {
                    HStack(spacing: 6) {
                        Image(systemName: "magnifyingglass")
                            .font(.system(size: 13))
                            .foregroundStyle(Theme.muted)
                        TextField("Filtrer…", text: $filter)
                            .font(.system(size: 14))
                            .textInputAutocapitalization(.never)
                            .autocorrectionDisabled()
                            .foregroundStyle(Theme.text)
                    }
                    .padding(.horizontal, 10)
                    .padding(.vertical, 8)
                    .background(Theme.fieldBg)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border, lineWidth: 0.5))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .padding(.horizontal, 12)
                    .padding(.top, 8)
                    .padding(.bottom, 4)

                    Text("\(selected.count)/\(maxCount) sélectionnés")
                        .font(.system(size: 11))
                        .foregroundStyle(Theme.muted)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, 12)
                        .padding(.bottom, 2)

                    List {
                        ForEach(filtered, id: \.self) { tag in
                            let on = selected.contains(tag)
                            let isSug = suggested.contains(tag)
                            Button {
                                if on {
                                    selected.remove(tag)
                                } else if selected.count < maxCount {
                                    selected.insert(tag)
                                }
                            } label: {
                                HStack(spacing: 8) {
                                    Text(tag)
                                        .font(.system(size: 14))
                                        .foregroundStyle(Theme.text)
                                        .lineLimit(1)
                                    if isSug {
                                        Text("Vivino")
                                            .font(.system(size: 9, weight: .bold))
                                            .foregroundStyle(Theme.star)
                                            .padding(.horizontal, 5)
                                            .padding(.vertical, 1)
                                            .overlay(Capsule().stroke(Theme.star.opacity(0.5), lineWidth: 0.5))
                                    }
                                    Spacer(minLength: 0)
                                    Image(systemName: on ? "checkmark.circle.fill" : "circle")
                                        .font(.system(size: 16))
                                        .foregroundStyle(on ? Theme.accent : Theme.muted)
                                }
                            }
                            .listRowInsets(EdgeInsets(top: 6, leading: 12, bottom: 6, trailing: 12))
                            .listRowBackground(Theme.card)
                        }
                    }
                    .listStyle(.plain)
                    .scrollContentBackground(.hidden)
                    .environment(\.defaultMinListRowHeight, 36)
                }
                .background(Theme.bg)
                .navigationTitle("Tags")
                .navigationBarTitleDisplayMode(.inline)
                .toolbar {
                    ToolbarItem(placement: .confirmationAction) {
                        Button("OK") { open = false }
                            .fontWeight(.semibold)
                            .foregroundStyle(Theme.accent)
                    }
                }
                .preferredColorScheme(.dark)
            }
            // Téléphone : demi-écran compact par défaut (pas full-screen)
            // Note: pas de presentationContentInteraction — API iOS 16.4+ alors que
            // deployment target = 16.0 (CI xcodebuild échoue sinon).
            .presentationDetents([.height(280), .height(380), .fraction(0.55)], selection: $sheetDetent)
            .presentationDragIndicator(.visible)
            .onAppear {
                filter = ""
                sheetDetent = .height(280)
            }
        }
    }
}

struct WeenoSelectField: View {
    let label: String
    let value: String
    let options: [(String, String)]
    let onSelect: (String) -> Void

    private var display: String {
        options.first(where: { $0.0 == value })?.1 ?? "—"
    }

    var body: some View {
        WeenoFilterLabel(label: label) {
            Menu {
                ForEach(options, id: \.0) { opt in
                    Button(opt.1) { onSelect(opt.0) }
                }
            } label: {
                HStack(spacing: 4) {
                    Text(display)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                    Spacer(minLength: 0)
                    Image(systemName: "chevron.down")
                        .font(.system(size: 9, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                }
                .font(.system(size: 12.5))
                .foregroundStyle(Theme.text)
                .padding(.horizontal, 6)
                .padding(.vertical, 7)
                .frame(maxWidth: .infinity)
                .background(Theme.fieldBg)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                .clipShape(RoundedRectangle(cornerRadius: 8))
            }
        }
    }
}

struct WeenoHistoryFiltersRow: View {
    @Binding var filterStyle: String
    @Binding var filterRating: Double
    @Binding var filterPeriod: String
    let styles: [StyleOption]

    private var styleOptions: [(String, String)] {
        var opts: [(String, String)] = [("", "Tous styles")]
        opts.append(contentsOf: styles.filter { !$0.value.isEmpty }.map { ($0.value, $0.label) })
        return opts
    }

    private var ratingOptions: [(String, String)] {
        var opts: [(String, String)] = [("0", "Toutes")]
        for val in [0.25, 0.5, 1.0, 2.0, 3.0, 4.0, 5.0] {
            let key = val == floor(val) ? String(Int(val)) : String(val)
            let label = (val == floor(val) ? String(format: "%.0f", val) : String(format: "%.2f", val))
                .replacingOccurrences(of: ".00", with: "")
                .replacingOccurrences(of: ".50", with: ".5") + " ★+"
            opts.append((key, label))
        }
        return opts
    }

    private var ratingKey: String {
        let v = filterRating
        if v == 0 { return "0" }
        return v == floor(v) ? String(Int(v)) : String(v)
    }

    private var periodOptions: [(String, String)] {
        [("", "Tout"), ("week", "7 jours"), ("month", "30 jours"), ("year", "1 an")]
    }

    var body: some View {
        HStack(alignment: .bottom, spacing: 6) {
            WeenoSelectField(label: "Style", value: filterStyle, options: styleOptions) { filterStyle = $0 }
            WeenoSelectField(
                label: "Note min",
                value: ratingKey,
                options: ratingOptions
            ) { filterRating = Double($0) ?? 0 }
            WeenoSelectField(label: "Période", value: filterPeriod, options: periodOptions) { filterPeriod = $0 }
        }
        .padding(.vertical, 8)
    }
}

struct WeenoGiftsFiltersRow: View {
    @Binding var search: String
    @Binding var filterStyle: String
    @Binding var minRating: Double
    let styleOptions: [String]

    private var styles: [(String, String)] {
        var opts: [(String, String)] = [("", "Tous styles")]
        opts.append(contentsOf: styleOptions.map { ($0, $0) })
        return opts
    }

    private var ratingOptions: [(String, String)] {
        [("0", "Toutes"), ("4", "≥4★"), ("4.5", "≥4.5★"), ("5", "=5★")]
    }

    var body: some View {
        HStack(alignment: .bottom, spacing: 6) {
            WeenoFilterLabel(label: "Recherche") {
                TextField("nom, producteur...", text: $search)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(.system(size: 12.5))
                    .padding(.horizontal, 6)
                    .padding(.vertical, 7)
                    .background(Theme.fieldBg)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .foregroundStyle(Theme.text)
            }
            WeenoSelectField(label: "Style", value: filterStyle, options: styles) { filterStyle = $0 }
            WeenoSelectField(
                label: "Note min",
                value: minRating == 5 ? "5" : (minRating == 4.5 ? "4.5" : (minRating >= 4 ? "4" : "0")),
                options: ratingOptions
            ) { minRating = Double($0) ?? 0 }
        }
        .padding(.vertical, 8)
    }
}

struct WeenoHistorySearchField: View {
    @Binding var text: String

    var body: some View {
        WeenoFilterLabel(label: "Rechercher") {
            TextField("nom, producteur, style…", text: $text)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.system(size: 12.5))
                .padding(.horizontal, 6)
                .padding(.vertical, 7)
                .background(Theme.fieldBg)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .foregroundStyle(Theme.text)
        }
        .padding(.bottom, 10)
    }
}

// MARK: - Admin référentiels (admin-ref-card PWA)

struct WeenoAdminReferentialsCard: View {
    @Binding var tab: Int
    let colors: [ReferentialEntry]
    let flavors: [ReferentialEntry]
    let regions: [ReferentialEntry]
    @Binding var filter: String
    @Binding var newName: String
    let onAdd: () -> Void
    let onDelete: (ReferentialEntry) -> Void

    private var activeList: [ReferentialEntry] {
        let list: [ReferentialEntry]
        switch tab {
        case 1: list = flavors
        case 2: list = regions
        default: list = colors
        }
        guard !filter.isEmpty else { return list }
        let q = WineFormatters.normalizeSearch(filter)
        return list.filter { WineFormatters.normalizeSearch($0.name).contains(q) }
    }

    private var addPlaceholder: String {
        switch tab {
        case 1: return "Nouvel arôme / structure"
        case 2: return "Nouvelle région"
        default: return "" // couleurs = presets only
        }
    }

    private var canAdd: Bool { tab == 1 || tab == 2 }

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack(spacing: 6) {
                refTabButton("Cépages (\(colors.count))", index: 0)
                refTabButton("Arômes (\(flavors.count))", index: 1)
                refTabButton("Régions (\(regions.count))", index: 2)
            }

            Text("Filtre pour naviguer. Badge preset = base — seuls les custom se suppriment.")
                .font(.system(size: 11))
                .foregroundStyle(Theme.muted)

            if canAdd {
            HStack(spacing: 6) {
                TextField(addPlaceholder, text: $newName)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                    .font(.system(size: 12.5))
                    .padding(.horizontal, 8)
                    .padding(.vertical, 8)
                    .background(Theme.fieldBg)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                    .clipShape(RoundedRectangle(cornerRadius: 8))
                    .foregroundStyle(Theme.text)
                    .onSubmit(onAdd)
                Button("+", action: onAdd)
                    .font(.system(size: 16, weight: .bold))
                    .frame(width: 36, height: 36)
                    .background(Theme.card)
                    .foregroundStyle(Theme.text)
                    .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
            }
            }

            TextField("Filtrer…", text: $filter)
                .textInputAutocapitalization(.never)
                .autocorrectionDisabled()
                .font(.system(size: 12.5))
                .padding(.horizontal, 8)
                .padding(.vertical, 7)
                .background(Theme.fieldBg)
                .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
                .clipShape(RoundedRectangle(cornerRadius: 8))
                .foregroundStyle(Theme.text)

            ScrollView {
                LazyVStack(spacing: 2) {
                    if activeList.isEmpty {
                        Text("Aucun")
                            .font(.system(size: 12))
                            .foregroundStyle(Theme.muted)
                            .frame(maxWidth: .infinity, alignment: .leading)
                            .padding(.vertical, 8)
                            .padding(.horizontal, 6)
                    } else {
                        ForEach(activeList) { entry in
                            HStack(spacing: 8) {
                                Text(entry.name)
                                    .font(.system(size: 12.5))
                                    .foregroundStyle(Theme.text)
                                    .lineLimit(2)
                                if entry.preset == true {
                                    Text("preset")
                                        .font(.system(size: 10))
                                        .foregroundStyle(Theme.muted)
                                        .padding(.horizontal, 6)
                                        .padding(.vertical, 2)
                                        .overlay(Capsule().stroke(Theme.border))
                                }
                                Spacer(minLength: 4)
                                if entry.preset != true, entry.refId != nil {
                                Button("Suppr") { onDelete(entry) }
                                    .font(.system(size: 11, weight: .semibold))
                                    .foregroundStyle(Theme.error)
                                }
                            }
                            .padding(.horizontal, 8)
                            .padding(.vertical, 6)
                            .background(Color.clear)
                            .clipShape(RoundedRectangle(cornerRadius: 4))
                        }
                    }
                }
            }
            .frame(maxHeight: 200)
            .background(Theme.fieldBg)
            .overlay(RoundedRectangle(cornerRadius: 8).stroke(Theme.border))
            .clipShape(RoundedRectangle(cornerRadius: 8))
        }
        .padding(.horizontal, 12)
        .padding(.vertical, 10)
        .background(Theme.card)
        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
        .clipShape(RoundedRectangle(cornerRadius: 12))
    }

    private func refTabButton(_ title: String, index: Int) -> some View {
        Button {
            tab = index
            filter = ""
            newName = ""
        } label: {
            Text(title)
                .font(.system(size: 11.5, weight: .semibold))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(maxWidth: .infinity)
                .padding(.vertical, 7)
                .background(tab == index ? Theme.accent.opacity(0.12) : Theme.fieldBg)
                .foregroundStyle(tab == index ? Theme.text : Theme.muted)
                .overlay(
                    RoundedRectangle(cornerRadius: 8)
                        .stroke(tab == index ? Theme.accent : Theme.border)
                )
                .clipShape(RoundedRectangle(cornerRadius: 8))
        }
    }
}

// MARK: - Admin sections

struct WeenoAdminSub: View {
    let title: String
    var trailing: (() -> AnyView)?

    var body: some View {
        HStack {
            Text(title)
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Theme.muted)
            Spacer()
            if let trailing { trailing() }
        }
        .padding(.top, 12)
        .padding(.bottom, 4)
    }
}

struct WeenoAdminCard<Content: View>: View {
    @ViewBuilder let content: () -> Content

    var body: some View {
        content()
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Theme.card)
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(Theme.border))
            .clipShape(RoundedRectangle(cornerRadius: 14))
    }
}

// MARK: - Boutons compacts (history-card__actions)

struct WeenoCompactButton: View {
    let title: String
    var primary = false
    var destructive = false
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: 11.5, weight: .semibold))
                .padding(.horizontal, 9)
                .padding(.vertical, 6)
                .background(primary ? AnyShapeStyle(Theme.primaryGradient) : AnyShapeStyle(Theme.card))
                .foregroundStyle(
                    primary ? Theme.btnPrimaryText : (destructive ? Theme.error : Theme.text)
                )
                .overlay(
                    RoundedRectangle(cornerRadius: 10)
                        .stroke(destructive ? Theme.error.opacity(0.45) : Theme.border)
                )
                .clipShape(RoundedRectangle(cornerRadius: 10))
        }
    }
}

struct WeenoLoadMoreButton: View {
    let title: String
    let action: () -> Void

    var body: some View {
        Button(action: action) {
            Text(title)
                .font(.system(size: Theme.Font.btn, weight: .semibold))
                .frame(maxWidth: 280)
                .padding(.vertical, 13)
                .background(Theme.card)
                .foregroundStyle(Theme.text)
                .overlay(RoundedRectangle(cornerRadius: Theme.Radius.btn).stroke(Theme.border))
                .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.btn))
        }
        .frame(maxWidth: .infinity)
        .padding(.top, 10)
    }
}

// MARK: - Panneau latéral (patch notes, IP invités)

struct WeenoSidePanel<Content: View>: View {
    let title: String
    let onClose: () -> Void
    @ViewBuilder let content: () -> Content

    var body: some View {
        VStack(spacing: 0) {
            HStack {
                Text(title)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Theme.text)
                Spacer()
                Button(action: onClose) {
                    Text("×")
                        .font(.system(size: 22, weight: .medium))
                        .foregroundStyle(Theme.muted)
                        .padding(.horizontal, 4)
                }
            }
            .padding(.horizontal, 16)
            .padding(.vertical, 12)
            .background(Theme.bg)
            .overlay(alignment: .bottom) { Rectangle().fill(Theme.border).frame(height: 1) }

            ScrollView {
                content()
                    .padding(16)
            }
            WeenoSecondaryButton(title: "Fermer", action: onClose)
                .padding(.horizontal, 16)
                .padding(.bottom, 16)
        }
        .background(Theme.bg)
        .preferredColorScheme(.dark)
    }
}

// MARK: - Détail dégustation (detail-head)

struct WeenoDetailHead: View {
    let onClose: () -> Void
    let onRetaste: () -> Void
    let onEdit: () -> Void
    var showHide: Bool = false
    var onHide: (() -> Void)?

    var body: some View {
        HStack(spacing: 8) {
            WeenoGhostButton("Fermer", action: onClose)
            Spacer()
            if showHide, let onHide {
                WeenoGhostButton("Masquer", action: onHide)
            }
            Button(action: onRetaste) {
                Text("Noter à nouveau")
                    .font(.system(size: Theme.Font.ghost, weight: .semibold))
                    .padding(.horizontal, 12)
                    .padding(.vertical, 8)
                    .background(Theme.primaryGradient)
                    .foregroundStyle(Theme.btnPrimaryText)
                    .clipShape(RoundedRectangle(cornerRadius: Theme.Radius.btn))
            }
            WeenoGhostButton("Modifier", action: onEdit)
        }
        .padding(.horizontal, 16)
        .padding(.vertical, 10)
        .background(Theme.bg)
    }
}