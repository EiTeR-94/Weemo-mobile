import SwiftUI

enum Theme {
    enum Font {
        static let h1: CGFloat = 21.6       // 1.35rem
        static let sub: CGFloat = 12.8      // 0.8rem
        static let pill: CGFloat = 11.5     // 0.72rem
        static let search: CGFloat = 12.5   // 0.78rem
        static let ghost: CGFloat = 12.8    // 0.8rem
        static let step: CGFloat = 11.5     // 0.72rem
        static let lead: CGFloat = 14.4     // 0.9rem
        static let btn: CGFloat = 16        // 1rem
        static let field: CGFloat = 13      // label
        static let tag: CGFloat = 12.8     // 0.8rem
        static let tagTitle: CGFloat = 13.6 // 0.85rem
    }

    enum Radius {
        static let btn: CGFloat = 12
        static let card: CGFloat = 14
        static let field: CGFloat = 10
        static let step: CGFloat = 999
    }

    // Palette webapp Weeno (style.css dark)
    static let bg = Color(hex: 0x120a0e)
    static let fieldBg = Color(hex: 0x120a0e)
    static let card = Color(hex: 0x1c1016)
    static let text = Color(hex: 0xf5e6e8)
    static let muted = Color(hex: 0xa89a9e)
    static let accent = Color(hex: 0xc45c7a)
    static let accent2 = Color(hex: 0x8b1e3f)
    static let ok = Color(hex: 0x6bbf8a)
    static let border = Color(hex: 0x3d2430)
    static let star = Color(hex: 0xe8c56a)
    static let starOff = Color(hex: 0x5a4450)
    static let photoBg = Color(hex: 0x0a0608)
    static let btnPrimaryText = Color(hex: 0xfff5f7)
    static let error = Color(hex: 0xf87171)

    static var primaryGradient: LinearGradient {
        LinearGradient(colors: [accent, accent2], startPoint: .topLeading, endPoint: .bottomTrailing)
    }
}

extension Color {
    init(hex: UInt32) {
        let r = Double((hex >> 16) & 0xff) / 255
        let g = Double((hex >> 8) & 0xff) / 255
        let b = Double(hex & 0xff) / 255
        self.init(red: r, green: g, blue: b)
    }
}

struct CardStyle: ViewModifier {
    func body(content: Content) -> some View {
        content
            .padding(12)
            .background(Theme.card)
            .overlay(RoundedRectangle(cornerRadius: 12).stroke(Theme.border, lineWidth: 0.5))
            .clipShape(RoundedRectangle(cornerRadius: 12))
            .beerShadow(radius: 3, y: 1)
    }
}

extension View {
    func beerCard() -> some View { modifier(CardStyle()) }

    func beerShadow(radius: CGFloat = 3, y: CGFloat = 1) -> some View {
        shadow(color: .black.opacity(0.18), radius: radius, x: 0, y: y)
    }

    func beerSheetChrome() -> some View {
        presentationDetents([.large])
            .presentationDragIndicator(.visible)
            .preferredColorScheme(.dark)
    }
}
