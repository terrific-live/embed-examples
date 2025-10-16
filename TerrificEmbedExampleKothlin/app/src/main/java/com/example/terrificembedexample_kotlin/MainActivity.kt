package com.example.terrificembedexample_kotlin

import android.os.Bundle
import android.annotation.SuppressLint
import android.util.Log
import android.webkit.*
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val webView: WebView = findViewById(R.id.webView)
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webSettings.mediaPlaybackRequiresUserGesture = false

        // Disable fullscreen playback, keep inline
        webSettings.setMediaPlaybackRequiresUserGesture(false)
        webView.webChromeClient = object : WebChromeClient() {
            override fun onConsoleMessage(message: ConsoleMessage): Boolean {
                Log.d("JS", "${message.message()} -- From line ${message.lineNumber()} of ${message.sourceId()}")
                return true
            }
        }

        // Intercept requests to inject custom headers (Origin)
        webView.webViewClient = object : WebViewClient() {
            override fun shouldInterceptRequest(
                view: WebView?,
                request: WebResourceRequest?
            ): WebResourceResponse? {
                val url = request?.url.toString()
                if (url.contains("terrific.live/terrific-sdk.js")) {
                    return try {
                        val connection = java.net.URL(url).openConnection()
                        connection.setRequestProperty("Origin", "https://france.tv")
                        val inputStream = connection.getInputStream()
                        WebResourceResponse("application/javascript", "UTF-8", inputStream)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }
        }

        // Inject JS bridge for console logging
        webView.addJavascriptInterface(JSBridge(), "AndroidBridge")

        val html = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0">
                <script defer src="https://terrific.live/terrific-sdk.js" storeId="nzRdWaBc1JPk2XN3B9bp"></script>
                <script>
                    console.log("JS console log active");
                    console.error("JS console error active");
                    window.onload = () => {
                        console.log("Terrific page loaded");
                    };
                </script>
            </head>
            <body style="margin:0;padding:0;">
                <div data-source="terrific" embedding-id="9iM1LIQ3DHqs06jyxuuq"
                     style="height:400px;background:#fafafa;">
                     <p style="text-align:center;margin-top:50%;">Loading Terrific...</p>
                </div>
            </body>
            </html>
        """.trimIndent()

        webView.loadDataWithBaseURL("https://france.tv", html, "text/html", "UTF-8", null)
    }

    class JSBridge {
        @JavascriptInterface
        fun logFromJS(msg: String) {
            Log.d("JSBridge", msg)
        }
    }
}