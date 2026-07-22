import SwiftUI
import WebKit

/// Coque native = **strictement la webapp Weeno** (même HTML/CSS/JS).
/// URL prod : https://eiter.freeboxos.fr/wine/app
struct WeenoWebShellView: UIViewRepresentable {
    @Binding var pendingURL: URL?

    static let startURL = URL(string: "https://eiter.freeboxos.fr/wine/app")!
    static let lanURL = URL(string: "https://192.168.1.50:8444/wine/app")!
    static let bg = UIColor(red: 0x12 / 255, green: 0x0a / 255, blue: 0x0e / 255, alpha: 1)

    func makeCoordinator() -> Coordinator {
        Coordinator(self)
    }

    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []
        config.websiteDataStore = .default()
        // Cookies / localStorage = session webapp
        let prefs = WKWebpagePreferences()
        prefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = prefs

        let wv = WKWebView(frame: .zero, configuration: config)
        wv.navigationDelegate = context.coordinator
        wv.uiDelegate = context.coordinator
        wv.allowsBackForwardNavigationGestures = true
        wv.scrollView.contentInsetAdjustmentBehavior = .never
        wv.isOpaque = false
        wv.backgroundColor = Self.bg
        wv.scrollView.backgroundColor = Self.bg
        wv.customUserAgent = (wv.value(forKey: "userAgent") as? String ?? "") + " WeenoNativeiOS/0.3 WebViewShell"

        context.coordinator.webView = wv
        wv.load(URLRequest(url: Self.startURL))
        return wv
    }

    func updateUIView(_ webView: WKWebView, context: Context) {
        guard let pending = pendingURL else { return }
        DispatchQueue.main.async {
            pendingURL = nil
        }
        if isWeenoURL(pending) {
            webView.load(URLRequest(url: pending))
        }
    }

    private func isWeenoURL(_ url: URL) -> Bool {
        let s = url.absoluteString
        return s.contains("eiter.freeboxos.fr") || s.contains("192.168.1.") || s.contains("/wine")
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKUIDelegate {
        var parent: WeenoWebShellView
        weak var webView: WKWebView?
        private var triedLanFallback = false

        init(_ parent: WeenoWebShellView) {
            self.parent = parent
        }

        func webView(
            _ webView: WKWebView,
            decidePolicyFor navigationAction: WKNavigationAction,
            decisionHandler: @escaping (WKNavigationActionPolicy) -> Void
        ) {
            guard let url = navigationAction.request.url else {
                decisionHandler(.allow)
                return
            }
            let s = url.absoluteString
            // Weeno / LAN / freebox → stay in WebView
            if s.contains("eiter.freeboxos.fr") || s.contains("192.168.1.") || s.hasPrefix("about:") {
                decisionHandler(.allow)
                return
            }
            // External
            if navigationAction.navigationType == .linkActivated {
                UIApplication.shared.open(url)
                decisionHandler(.cancel)
                return
            }
            decisionHandler(.allow)
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            // Fallback LAN si prod KO (owner à la maison)
            if !triedLanFallback {
                triedLanFallback = true
                webView.load(URLRequest(url: WeenoWebShellView.lanURL))
            }
        }

        func webView(
            _ webView: WKWebView,
            createWebViewWith configuration: WKWebViewConfiguration,
            for navigationAction: WKNavigationAction,
            windowFeatures: WKWindowFeatures
        ) -> WKWebView? {
            // target=_blank → même webview
            if navigationAction.targetFrame == nil, let url = navigationAction.request.url {
                webView.load(URLRequest(url: url))
            }
            return nil
        }
    }
}

/// Root de l'app : full screen webapp + deep links join.
struct WeenoWebRootView: View {
    @State private var pendingURL: URL?

    var body: some View {
        ZStack {
            Color(red: 0x12 / 255, green: 0x0a / 255, blue: 0x0e / 255).ignoresSafeArea()
            WeenoWebShellView(pendingURL: $pendingURL)
                .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onOpenURL { url in
            if url.absoluteString.contains("/wine") {
                pendingURL = url
            }
        }
        .onContinueUserActivity(NSUserActivityTypeBrowsingWeb) { activity in
            if let url = activity.webpageURL, url.absoluteString.contains("/wine") {
                pendingURL = url
            }
        }
    }
}
