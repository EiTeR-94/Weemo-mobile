import SwiftUI

struct WishlistSheetView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss

    @State private var items: [WishlistItem] = []
    @State private var name = ""
    @State private var producer = ""
    @State private var error: String?

    var body: some View {
        WeenoOverlayScreen(title: "À boire", onClose: { dismiss() }) {
            VStack(spacing: 12) {
                Text("Tes souhaits personnels (vins à goûter).")
                    .font(.system(size: Theme.Font.lead * 0.94))
                    .foregroundStyle(Theme.muted)
                    .frame(maxWidth: .infinity, alignment: .leading)

                WeenoAdminCard {
                    VStack(spacing: 0) {
                        WeenoField(label: "Nom", text: $name, placeholder: "ex. Mama Whipa")
                        WeenoField(label: "Producteur", text: $producer, placeholder: "optionnel")
                            .padding(.top, 10)
                        WeenoPrimaryButton(title: "Ajouter", disabled: name.count < 2) {
                            Task { await add() }
                        }
                    }
                }

                if let error {
                    Text(error).foregroundStyle(Theme.error).font(.footnote)
                }

                LazyVStack(spacing: 10) {
                    ForEach(items) { item in
                        HStack(alignment: .center, spacing: 10) {
                            VStack(alignment: .leading, spacing: 2) {
                                Text(item.wineName)
                                    .font(.system(size: 15, weight: .semibold))
                                    .foregroundStyle(Theme.text)
                                Text(item.producer ?? "—")
                                    .font(.system(size: 12))
                                    .foregroundStyle(Theme.muted)
                            }
                            Spacer()
                            WeenoCompactButton(title: "Goûter", primary: true) {
                                dismiss()
                                app.startWishlistTaste(item)
                            }
                            WeenoCompactButton(title: "Suppr.", destructive: true) {
                                Task { await remove(item) }
                            }
                        }
                        .padding(12)
                        .background(Theme.card)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                    }
                }
            }
        }
        .refreshable { await load() }
        .task { await load() }
    }

    private func load() async {
        items = (try? await app.api.wishlist()) ?? []
    }

    private func add() async {
        do {
            try await app.api.addWishlist(wineName: name, producer: producer)
            name = ""
            producer = ""
            await load()
        } catch let err {
            error = err.localizedDescription
        }
    }

    private func remove(_ item: WishlistItem) async {
        try? await app.api.deleteWishlist(id: item.id)
        await load()
    }
}