import SwiftUI

// Feedback (compose + réponse admin) — utilisées uniquement depuis MainView.

/// Feedback compact (demi-feuille) + clavier dismissible.
struct FeedbackSheetView: View {
    @EnvironmentObject private var app: AppModel
    @Environment(\.dismiss) private var dismiss
    @FocusState private var messageFocused: Bool
    @State private var message = ""
    @State private var category = "general"
    @State private var sending = false

    private let categories: [(String, String)] = [
        ("general", "Avis général"),
        ("bug", "Bug"),
        ("idea", "Idée"),
        ("ux", "Interface"),
        ("rpg", "RPG"),
        ("other", "Autre"),
    ]

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 12) {
                    Text("Dis-nous ce qui va, ce qui coince ou une idée. Seul l’admin le lit.")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)

                    // Catégories en chips (compact)
                    Text("C’est plutôt…")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Theme.muted)
                    LazyVGrid(columns: [GridItem(.flexible()), GridItem(.flexible()), GridItem(.flexible())], spacing: 6) {
                        ForEach(categories, id: \.0) { key, label in
                            Button {
                                category = key
                                KeyboardDismiss.endEditing()
                            } label: {
                                Text(label)
                                    .font(.system(size: 12, weight: category == key ? .bold : .semibold))
                                    .foregroundStyle(category == key ? Color(red: 0.07, green: 0.07, blue: 0.07) : Theme.text)
                                    .frame(maxWidth: .infinity)
                                    .padding(.vertical, 8)
                                    .background {
                                        if category == key {
                                            LinearGradient(colors: [Theme.accent, Color.orange], startPoint: .leading, endPoint: .trailing)
                                        } else {
                                            Theme.card
                                        }
                                    }
                                    .overlay(RoundedRectangle(cornerRadius: 10).stroke(category == key ? Theme.accent : Theme.border))
                                    .clipShape(RoundedRectangle(cornerRadius: 10))
                            }
                            .buttonStyle(.plain)
                        }
                    }

                    Text("Ton message")
                        .font(.system(size: 11, weight: .bold))
                        .foregroundStyle(Theme.muted)
                    ZStack(alignment: .topLeading) {
                        if message.isEmpty && !messageFocused {
                            Text("Écris librement…")
                                .font(.system(size: 14))
                                .foregroundStyle(Theme.muted.opacity(0.7))
                                .padding(.horizontal, 10)
                                .padding(.vertical, 10)
                        }
                        TextEditor(text: $message)
                            .focused($messageFocused)
                            .scrollContentBackground(.hidden)
                            .foregroundStyle(Theme.text)
                            .frame(minHeight: 88, maxHeight: 120)
                            .padding(6)
                    }
                    .background(Theme.fieldBg)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(messageFocused ? Theme.accent : Theme.border))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                    HStack {
                        if messageFocused {
                            Button("Masquer clavier") {
                                messageFocused = false
                                KeyboardDismiss.endEditing()
                            }
                            .font(.caption.weight(.semibold))
                            .foregroundStyle(Theme.accent)
                        }
                        Spacer()
                        Text("\(min(message.count, 1200))/1200")
                            .font(.caption2)
                            .foregroundStyle(Theme.muted)
                    }

                    HStack(spacing: 10) {
                        Button("Annuler") {
                            KeyboardDismiss.endEditing()
                            dismiss()
                        }
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(Theme.muted)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 11)
                        .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                        .disabled(sending)

                        Button {
                            Task {
                                messageFocused = false
                                KeyboardDismiss.endEditing()
                                sending = true
                                let msg = String(message.trimmingCharacters(in: .whitespacesAndNewlines).prefix(1200))
                                let ok = await app.sendFeedback(message: msg, category: category)
                                sending = false
                                if ok { dismiss() }
                            }
                        } label: {
                            Text(sending ? "…" : "Envoyer")
                                .font(.system(size: 14, weight: .bold))
                                .foregroundStyle(Color(red: 0.07, green: 0.07, blue: 0.07))
                                .frame(maxWidth: .infinity)
                                .padding(.vertical, 11)
                                .background(LinearGradient(colors: [Theme.accent, Color.orange], startPoint: .leading, endPoint: .trailing))
                                .clipShape(RoundedRectangle(cornerRadius: 12))
                        }
                        .disabled(sending || message.trimmingCharacters(in: .whitespacesAndNewlines).count < 3)
                        .opacity(message.trimmingCharacters(in: .whitespacesAndNewlines).count < 3 ? 0.5 : 1)
                    }
                }
                .padding(14)
            }
            .scrollDismissesKeyboard(.interactively)
            .background(Theme.bg)
            .navigationTitle("💬 Feedback")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .topBarTrailing) {
                    Button("Fermer") {
                        messageFocused = false
                        KeyboardDismiss.endEditing()
                        dismiss()
                    }
                    .disabled(sending)
                }
                ToolbarItemGroup(placement: .keyboard) {
                    Spacer()
                    Button("OK") {
                        messageFocused = false
                        KeyboardDismiss.endEditing()
                    }
                    .fontWeight(.semibold)
                }
            }
            .simultaneousGesture(
                TapGesture().onEnded {
                    messageFocused = false
                    KeyboardDismiss.endEditing()
                }
            )
        }
    }
}

// MARK: - Popup réponse admin (feedback)

struct FeedbackReplyKey: Identifiable {
    let item: AdminFeedbackItem
    var id: Int { item.stableId }
}

struct FeedbackReplyPopup: View {
    let item: AdminFeedbackItem
    let index: Int
    let total: Int
    let onNext: () -> Void

    var body: some View {
        NavigationStack {
            VStack(alignment: .leading, spacing: 14) {
                Text(item.isRejected ? "Feedback refusé" : "Feedback mis en place")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(Theme.text)

                Text(item.displayStatus + (item.resolvedAt.map { " · \(WineFormatters.formatDate($0))" } ?? ""))
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(item.isRejected ? Theme.error : Theme.accent)

                if let msg = item.message, !msg.isEmpty {
                    Text("Tu avais écrit : « \(String(msg.prefix(220)))\(msg.count > 220 ? "…" : "") »")
                        .font(.system(size: 12))
                        .foregroundStyle(Theme.muted)
                        .italic()
                }

                Text(item.adminReply ?? (item.isRejected ? "Ta demande n'a pas été retenue." : "Ta demande a été prise en compte."))
                    .font(.system(size: 14))
                    .foregroundStyle(Theme.text)
                    .padding(12)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .background(Theme.card)
                    .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.accent.opacity(0.35)))
                    .clipShape(RoundedRectangle(cornerRadius: 12))

                if total > 1 {
                    Text("\(index + 1) / \(total)")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Theme.muted)
                }

                Spacer(minLength: 0)

                Button(action: onNext) {
                    Text(index + 1 < total ? "Suivant" : "Compris")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(Color(red: 0.07, green: 0.07, blue: 0.07))
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 12)
                        .background(LinearGradient(colors: [Theme.accent, Color.orange], startPoint: .leading, endPoint: .trailing))
                        .clipShape(RoundedRectangle(cornerRadius: 12))
                }
                .buttonStyle(.plain)
            }
            .padding(16)
            .background(Theme.bg)
            .navigationBarTitleDisplayMode(.inline)
        }
    }
}
