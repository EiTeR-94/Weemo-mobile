import PhotosUI
import SwiftUI

struct CheckinEditView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss

    let item: CheckinItem
    let onSaved: () -> Void

    @State private var rating: Double
    @State private var rebuy: String?
    @State private var comment: String
    @State private var location: String
    @State private var flavors = Set<String>()
    @State private var flavorTags: [String] = []
    @State private var customFlavorInput = ""
    @State private var hidden = false
    @State private var photoItem: PhotosPickerItem?
    @State private var newPhoto: Data?
    @State private var removePhoto = false
    @State private var busy = false
    @State private var message: String?

    init(item: CheckinItem, onSaved: @escaping () -> Void) {
        self.item = item
        self.onSaved = onSaved
        _rating = State(initialValue: item.rating)
        _rebuy = State(initialValue: item.rebuy)
        _comment = State(initialValue: item.comment ?? "")
        _location = State(initialValue: item.location ?? "")
        _flavors = State(initialValue: Set(item.flavors ?? []))
        _hidden = State(initialValue: item.hiddenFromPartner == true)
    }

    var body: some View {
        WeenoOverlayScreen(title: "Modifier la dégustation", onClose: { dismiss() }) {
            VStack(spacing: 14) {
                Text("\(item.producer ?? "—") · \(item.style ?? "?") · \(WineFormatters.formatDate(item.createdAt))")
                    .font(.system(size: 13))
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)

                PhotosPicker(selection: $photoItem, matching: .images) {
                    ZStack {
                        RoundedRectangle(cornerRadius: 16)
                            .stroke(Theme.border, style: StrokeStyle(lineWidth: 2, dash: [6]))
                            .background(Theme.card)
                            .frame(minHeight: 140)
                        if let path = item.resolvedPhoto, !removePhoto, newPhoto == nil {
                            WineImage(path: path)
                                .scaledToFit()
                                .frame(maxHeight: 200)
                                .padding(8)
                        } else {
                            Text("📷 Prendre ou choisir une photo")
                                .font(.system(size: Theme.Font.lead))
                                .foregroundStyle(Theme.muted)
                        }
                    }
                }
                if item.resolvedPhoto != nil {
                    WeenoSecondaryButton(title: "Retirer la photo") { removePhoto = true; newPhoto = nil }
                }

                VivinoRatingSlider(rating: $rating)

                VStack(alignment: .leading, spacing: 8) {
                    Text("Je rachèterais ?")
                        .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                        .foregroundStyle(Theme.text)
                    RebuyChoiceRow(value: $rebuy)
                }

                VStack(alignment: .leading, spacing: 8) {
                    Text("Arômes & structure")
                        .font(.system(size: Theme.Font.tagTitle, weight: .semibold))
                        .foregroundStyle(Theme.text)
                    FlavorSuggestInput(
                        placeholder: "ex. pierre chaude, salin…",
                        input: $customFlavorInput,
                        selected: $flavors,
                        maxCount: 12,
                        allTags: flavorTags
                    )
                }

                if app.isAdmin {
                    Toggle("Masquer cette dégustation pour les autres", isOn: $hidden)
                        .font(.system(size: 14))
                        .foregroundStyle(Theme.muted)
                        .tint(Theme.accent)
                }

                WeenoField(label: "Commentaire", text: $comment)

                WeenoField(
                    label: "Lieu ou lien",
                    text: $location,
                    placeholder: "ex. Chez nous · https://maps.app.goo.gl/…"
                )

                if let message {
                    Text(message).font(.footnote).foregroundStyle(Theme.error)
                }

                WeenoSecondaryButton(title: "Annuler") { dismiss() }
                WeenoPrimaryButton(title: busy ? "Enregistrement…" : "Enregistrer", busy: busy) {
                    Task { await save() }
                }
            }
        }
        .onChange(of: photoItem, perform: { p in Task { await loadPhoto(p) } })
        .task { await loadTags() }
    }

    private func loadTags() async {
        if let n = try? await app.api.flavors(style: item.style ?? "Unknown", description: "") {
            flavorTags = n.flavors ?? []
        } else if let tags = try? await app.api.configFlavors() {
            flavorTags = tags
        }
    }

    private func loadPhoto(_ item: PhotosPickerItem?) async {
        guard let item else { return }
        if let raw = try? await item.loadTransferable(type: Data.self) {
            newPhoto = WineImageUtils.compressJPEG(raw)
            removePhoto = false
        }
    }

    private func save() async {
        busy = true
        message = nil
        defer { busy = false }
        do {
            try await app.api.updateCheckin(
                id: item.id,
                rating: rating,
                flavors: Array(flavors),
                hops: [],
                comment: String(comment.prefix(120)),
                hiddenFromPartner: app.isAdmin ? hidden : nil,
                location: String(location.prefix(300)),
                rebuy: rebuy
            )
            if removePhoto { try await app.api.removeCheckinPhoto(id: item.id) }
            else if let newPhoto { try await app.api.replaceCheckinPhoto(id: item.id, jpeg: newPhoto) }
            onSaved()
            dismiss()
        } catch let err {
            message = err.localizedDescription
        }
    }
}