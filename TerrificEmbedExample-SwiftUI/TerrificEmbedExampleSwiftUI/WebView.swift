import SwiftUI
import WebKit
import os

struct WebView: UIViewRepresentable {
    let storeId: String
    let embeddingId: String
    private let logger = Logger(subsystem: "com.yourapp.terrific", category: "WebView")

    func makeUIView(context: Context) -> WKWebView {
        logger.log("🚀 Creating WKWebView with JS console bridge...")

        // Configuration with custom scheme handler
        let config = WKWebViewConfiguration()
        config.setURLSchemeHandler(CustomSchemeHandler(), forURLScheme: "terrific")
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        // ✅ Enable JavaScript (modern API)
        let pagePrefs = WKWebpagePreferences()
        pagePrefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = pagePrefs

        // ✅ Set up JavaScript-to-Swift logging bridge
        let userContentController = WKUserContentController()
        userContentController.add(context.coordinator, name: "logHandler")

        // Override console.log to forward messages to Swift
        let jsBridge = """
        (function() {
            const oldLog = console.log;
            console.log = function(...args) {
                window.webkit.messageHandlers.logHandler.postMessage("JS LOG: " + args.join(' '));
                oldLog.apply(console, args);
            };
            const oldError = console.error;
            console.error = function(...args) {
                window.webkit.messageHandlers.logHandler.postMessage("JS ERROR: " + args.join(' '));
                oldError.apply(console, args);
            };
            const oldWarn = console.warn;
            console.warn = function(...args) {
                window.webkit.messageHandlers.logHandler.postMessage("JS WARN: " + args.join(' '));
                oldWarn.apply(console, args);
            };
        })();
        """
        let script = WKUserScript(source: jsBridge, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        userContentController.addUserScript(script)

        config.userContentController = userContentController

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.scrollView.bounces = false
        webView.navigationDelegate = context.coordinator

        // ✅ Load Terrific embed HTML
        let html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8" />
            <meta name="viewport" content="width=device-width, initial-scale=1.0" />
            <title>Terrific Experience</title>
            <script defer src="https://terrific.live/terrific-sdk.js" storeId="\(storeId)"></script>
        </head>
        <body style="margin:0; padding:0; overflow:hidden;">
            <div data-source="terrific" embedding-id="\(embeddingId)"
                style="height:400px; background-color:#fafafa;">
            </div>
        </body>
        </html>
        """

        logger.log("📄 Loading HTML into WebView")
        webView.loadHTMLString(html, baseURL: URL(string: "https://france.tv")!)

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {}

    func makeCoordinator() -> Coordinator {
        Coordinator(logger: logger)
    }

    // MARK: - Coordinator (Navigation + JS Bridge)
    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        private let logger: Logger
        init(logger: Logger) { self.logger = logger }

        // Called whenever JS posts a message via window.webkit.messageHandlers.logHandler
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            if let msg = message.body as? String {
                print("🪵 \(msg)")
                logger.log("🪵 JS: \(msg, privacy: .public)")
            }
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            logger.log("📡 Navigation started to: \(webView.url?.absoluteString ?? "unknown")")
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            logger.log("✅ Navigation finished successfully.")
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            logger.error("❌ Navigation failed: \(error.localizedDescription)")
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            logger.error("❌ Provisional navigation failed: \(error.localizedDescription)")
        }
    }
}
