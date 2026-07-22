import SwiftUI

@main
struct WineNativeApp: App {
    var body: some Scene {
        WindowGroup {
            // Strictement la webapp Weeno (WebView) — pas d'UI native Beer.
            WeenoWebRootView()
        }
    }
}
