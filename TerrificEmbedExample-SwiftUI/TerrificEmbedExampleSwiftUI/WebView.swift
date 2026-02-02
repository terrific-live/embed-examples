import SwiftUI
import WebKit
import os

struct WebView: UIViewRepresentable {
    let storeId: String
    let embeddingId: String
    let onEvent: (TerrificEvent) -> Void
    private let logger = Logger(subsystem: "com.yourapp.terrific", category: "WebView")

    enum TerrificEvent: String {
        case openDisplay = "OPEN_DISPLAY"
        case closeFsrIframe = "CLOSE_FSR_IFRAME"
    }

    init(
        storeId: String,
        embeddingId: String,
        onEvent: @escaping (TerrificEvent) -> Void = { _ in }
    ) {
        self.storeId = storeId
        self.embeddingId = embeddingId
        self.onEvent = onEvent
    }

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
        userContentController.add(context.coordinator, name: "terrificEvent")

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

        // Listen to window.postMessage events and forward key Terrific events to Swift.
        // Mirrors the Kotlin sample logic (string payload OR {type|event|name}).
        let terrificEventBridge = """
        (function() {
            if (window.__terrificEventBridgeInstalled) return;
            window.__terrificEventBridgeInstalled = true;

            function extractType(data) {
                if (typeof data === 'string') return data;
                if (data && typeof data === 'object') {
                    if (data.type) return data.type;
                    if (data.event) return data.event;
                    if (data.name) return data.name;
                }
                return null;
            }

            window.addEventListener('message', function (event) {
                try {
                    var type = extractType(event.data);
                    if (!type) return;

                    var upper = type.toString().toUpperCase();
                    if (upper === 'OPEN_DISPLAY' || upper === 'CLOSE_FSR_IFRAME') {
                        if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.terrificEvent) {
                            window.webkit.messageHandlers.terrificEvent.postMessage({ type: upper });
                        }
                    }
                } catch (e) {
                    // Best-effort: avoid breaking host page if something goes wrong.
                    if (window.webkit && window.webkit.messageHandlers && window.webkit.messageHandlers.logHandler) {
                        window.webkit.messageHandlers.logHandler.postMessage("JS ERROR: Error handling Terrific postMessage " + e);
                    }
                }
            });
        })();
        """
        let terrificEventScript = WKUserScript(source: terrificEventBridge, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        userContentController.addUserScript(terrificEventScript)

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
        Coordinator(logger: logger, onEvent: onEvent)
    }

    // MARK: - Coordinator (Navigation + JS Bridge)
    class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        private let logger: Logger
        private let onEvent: (TerrificEvent) -> Void

        init(logger: Logger, onEvent: @escaping (TerrificEvent) -> Void) {
            self.logger = logger
            self.onEvent = onEvent
        }

        // Called whenever JS posts a message via window.webkit.messageHandlers.logHandler
        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            switch message.name {
            case "logHandler":
                if let msg = message.body as? String {
                    print("🪵 \(msg)")
                    logger.log("🪵 JS: \(msg, privacy: .public)")
                }

            case "terrificEvent":
                guard let type = Self.extractTerrificType(from: message.body) else { return }
                guard let event = TerrificEvent(rawValue: type) else { return }

                logger.log("🎬 Terrific event: \(event.rawValue, privacy: .public)")
                onEvent(event)

            default:
                break
            }
        }

        private static func extractTerrificType(from body: Any) -> String? {
            if let s = body as? String {
                return s.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            }

            if let dict = body as? [String: Any] {
                let raw =
                    (dict["type"] as? String) ??
                    (dict["event"] as? String) ??
                    (dict["name"] as? String)
                return raw?.trimmingCharacters(in: .whitespacesAndNewlines).uppercased()
            }

            return nil
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
