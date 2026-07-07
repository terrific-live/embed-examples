import SwiftUI
import WebKit
import os

struct WebView: UIViewRepresentable {
    let storeId: String
    let embeddingId: String
    @Binding var isCarouselVisibleInHost: Bool

    private let logger = Logger(subsystem: "com.yourapp.terrific", category: "WebView")

    func makeUIView(context: Context) -> WKWebView {
        logger.log("Creating WKWebView with Terrific host visibility bridge...")

        let config = WKWebViewConfiguration()
        config.setURLSchemeHandler(CustomSchemeHandler(), forURLScheme: "terrific")
        config.allowsInlineMediaPlayback = true
        config.mediaTypesRequiringUserActionForPlayback = []

        let pagePrefs = WKWebpagePreferences()
        pagePrefs.allowsContentJavaScript = true
        config.defaultWebpagePreferences = pagePrefs

        let userContentController = WKUserContentController()
        userContentController.add(context.coordinator, name: "logHandler")
        userContentController.add(context.coordinator, name: "terrificBridge")

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
        userContentController.addUserScript(
            WKUserScript(source: jsBridge, injectionTime: .atDocumentStart, forMainFrameOnly: false)
        )

        let terrificBridgeScript = """
        (function() {
            window.addEventListener('message', function(event) {
                if (!window.webkit?.messageHandlers?.terrificBridge) return;
                const data = event.data;
                if (!data || typeof data !== 'object') return;
                window.webkit.messageHandlers.terrificBridge.postMessage(data);
            });
        })();
        """
        userContentController.addUserScript(
            WKUserScript(source: terrificBridgeScript, injectionTime: .atDocumentStart, forMainFrameOnly: true)
        )

        config.userContentController = userContentController

        let webView = WKWebView(frame: .zero, configuration: config)
        webView.scrollView.bounces = false
        webView.navigationDelegate = context.coordinator
        context.coordinator.webView = webView
        context.coordinator.isCarouselVisibleInHost = isCarouselVisibleInHost

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

        logger.log("Loading Terrific embed HTML into WebView")
        webView.loadHTMLString(html, baseURL: URL(string: "https://<your domain>")!)

        return webView
    }

    func updateUIView(_ uiView: WKWebView, context: Context) {
        context.coordinator.updateHostVisibility(isCarouselVisibleInHost)
    }

    func makeCoordinator() -> Coordinator {
        Coordinator(logger: logger)
    }

    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        private let logger: Logger
        weak var webView: WKWebView?
        var isCarouselVisibleInHost = false
        private var isCarouselIframeReady = false
        private var hostVisibilityReportingStarted = false

        init(logger: Logger) {
            self.logger = logger
        }

        func userContentController(_ userContentController: WKUserContentController, didReceive message: WKScriptMessage) {
            if message.name == "logHandler", let msg = message.body as? String {
                logger.log("JS: \(msg, privacy: .public)")
                return
            }

            guard message.name == "terrificBridge", let payload = message.body as? [String: Any] else {
                return
            }

            handleTerrificMessage(payload)
        }

        func updateHostVisibility(_ isVisible: Bool) {
            isCarouselVisibleInHost = isVisible
            guard isCarouselIframeReady else { return }
            notifyCarouselHostVisible(isVisible)
        }

        private func handleTerrificMessage(_ payload: [String: Any]) {
            guard let type = payload["type"] as? String else { return }

            if type == TerrificHostMessages.iframeReady,
               payload["id"] as? String == TerrificHostMessages.timelineIframeId {
                logger.log("Received IFRAME_READY — carousel React tree is mounted")
                isCarouselIframeReady = true
                startHostVisibilityReportingIfNeeded()
                return
            }

            logger.log("Terrific message: \(type, privacy: .public)")
        }

        private func startHostVisibilityReportingIfNeeded() {
            guard !hostVisibilityReportingStarted else { return }
            hostVisibilityReportingStarted = true

            // Defer one tick so polls impression gate listener is attached.
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.05) { [weak self] in
                guard let self else { return }
                self.logger.log("Reporting initial host visibility: \(self.isCarouselVisibleInHost)")
                self.notifyCarouselHostVisible(self.isCarouselVisibleInHost)
            }
        }

        private func notifyCarouselHostVisible(_ isVisible: Bool) {
            guard let webView else { return }

            let script = "\(TerrificHostMessages.notifyCarouselHostVisibleScript)(\(isVisible));"
            webView.evaluateJavaScript(script) { _, error in
                if let error {
                    self.logger.error("Failed to post CAROUSEL_HOST_VISIBLE: \(error.localizedDescription)")
                }
            }
        }

        func webView(_ webView: WKWebView, didStartProvisionalNavigation navigation: WKNavigation!) {
            logger.log("Navigation started to: \(webView.url?.absoluteString ?? "unknown")")
        }

        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) {
            logger.log("Navigation finished successfully.")
        }

        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            logger.error("Navigation failed: \(error.localizedDescription)")
        }

        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            logger.error("Provisional navigation failed: \(error.localizedDescription)")
        }
    }
}
