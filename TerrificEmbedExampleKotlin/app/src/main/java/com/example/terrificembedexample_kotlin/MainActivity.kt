package com.example.terrificembedexample_kotlin

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.webkit.*
import android.widget.ScrollView
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONObject
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var webView: WebView
    private lateinit var scrollView: ScrollView
    private var lastY: Float = 0f
    private var isWebViewFullscreen: Boolean = false
    private var collapsedWebViewHeightPx: Int = 0
    private var expandedWebViewHeightPx: Int = 0

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        scrollView = findViewById(R.id.rootScrollView)
        webView = findViewById(R.id.webView)
        // Configure two WebView heights:
        // - collapsed: initial height inside the scroll (e.g. 800dp)
        // - expanded: full screen height when Terrific opens fullscreen
        val displayMetrics = resources.displayMetrics
        val screenHeight = displayMetrics.heightPixels
        expandedWebViewHeightPx = screenHeight
        collapsedWebViewHeightPx = (800 * displayMetrics.density).toInt()

        // Start in collapsed mode so the WebView is NOT full-screen initially.
        webView.layoutParams = webView.layoutParams.apply {
            height = collapsedWebViewHeightPx
        }

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
                        connection.setRequestProperty("Origin", "https://<your domain>")
                        val inputStream = connection.getInputStream()
                        WebResourceResponse("application/javascript", "UTF-8", inputStream)
                    } catch (e: Exception) {
                        e.printStackTrace()
                        null
                    }
                }
                return super.shouldInterceptRequest(view, request)
            }

            override fun onPageFinished(view: WebView?, url: String?) {
                super.onPageFinished(view, url)

                // Build a JSON context object with native app info for Piano (or other analytics).
                val contextJson = buildNativeAppContextJson()

                // Expose it into the WebView as a global + dispatch a custom event.
                // Web side (e.g. Piano integration) can do:
                //   window.addEventListener('nativeAppContextReady', (e) => {
                //       const ctx = e.detail; // same JSON we built here
                //       // attach ctx to Piano events / metadata
                //   });
                val js = """
                    (function() {
                        try {
                            window.__NATIVE_APP_CONTEXT__ = $contextJson;
                            window.dispatchEvent(new CustomEvent('nativeAppContextReady', { detail: window.__NATIVE_APP_CONTEXT__ }));
                        } catch (e) {
                            console.error('Failed to inject native app context', e);
                        }
                    })();
                """.trimIndent()

                view?.evaluateJavascript(js, null)
            }
        }

        // Inject JS bridge so web content can talk to native (logging + fullscreen state)
        webView.addJavascriptInterface(JSBridge { fullscreen ->
            isWebViewFullscreen = fullscreen
            // Resize the WebView according to fullscreen state.
            webView.post {
                val lp = webView.layoutParams
                lp.height = if (fullscreen) expandedWebViewHeightPx else collapsedWebViewHeightPx
                webView.layoutParams = lp

                // When fullscreen opens, scroll the parent so the WebView fills the screen
                // (no native header/footer visible).
                if (fullscreen) {
                    scrollView.smoothScrollTo(0, webView.top)
                }
            }
            // When fullscreen is closed, immediately allow the parent ScrollView
            // to intercept again on the next gestures, so app scrolling feels normal.
            if (!fullscreen) {
                webView.parent?.requestDisallowInterceptTouchEvent(false)
            }
        }, "AndroidBridge")

        // Coordinate scroll between WebView and parent ScrollView:
        // - While WebView can scroll in the gesture direction, keep events inside WebView.
        // - When WebView hits top/bottom and user keeps dragging, let parent ScrollView take over.
        webView.setOnTouchListener { v: View, event: MotionEvent ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    lastY = event.y
                    v.parent?.requestDisallowInterceptTouchEvent(true)
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.y - lastY
                    lastY = event.y

                    val goingDown = dy > 0       // finger moving down
                    val goingUp = dy < 0         // finger moving up

                    val canScrollUp = webView.canScrollVertically(-1)
                    val canScrollDown = webView.canScrollVertically(1)

                    val atTop = !canScrollUp
                    val atBottom = !canScrollDown

                    val disallowParent = if (isWebViewFullscreen) {
                        // While fullscreen overlay is open, always keep scroll inside WebView
                        true
                    } else {
                        when {
                            // At top and pulling down -> let parent scroll
                            atTop && goingDown -> false
                            // At bottom and pushing up -> let parent scroll
                            atBottom && goingUp -> false
                            // Otherwise keep events in WebView
                            else -> true
                        }
                    }
                    v.parent?.requestDisallowInterceptTouchEvent(disallowParent)
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    v.parent?.requestDisallowInterceptTouchEvent(false)
                }
            }
            // Let WebView handle the event as usual
            false
        }

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

                    function setAndroidFullscreen(isFullscreen) {
                        if (window.AndroidBridge && AndroidBridge.setFullscreen) {
                            AndroidBridge.setFullscreen(isFullscreen);
                        }
                    }

                    // Listen for Terrific SDK OPEN_DISPLAY / DISPLAY_CLOSED messages on window
                    // and map them to Android fullscreen state.
                    window.addEventListener('message', function (event) {
                        try {
                            var data = event.data;
                            var type = null;

                            if (typeof data === 'string') {
                                type = data;
                            } else if (data && typeof data === 'object') {
                                if (data.type) {
                                    type = data.type;
                                } else if (data.event) {
                                    type = data.event;
                                } else if (data.name) {
                                    type = data.name;
                                }
                            }

                            if (!type) return;

                            // Normalize to uppercase to be safe
                            var upper = type.toString().toUpperCase();
                            if (upper === 'OPEN_DISPLAY') {
                                console.log('Terrific OPEN_DISPLAY received');
                                setAndroidFullscreen(true);
                            } else if (upper === 'CLOSE_FSR_IFRAME') {
                                console.log('Terrific CLOSE_FSR_IFRAME received');
                                setAndroidFullscreen(false);
                            }
                        } catch (e) {
                            console.error('Error handling Terrific postMessage', e);
                        }
                    });
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

        if (savedInstanceState != null) {
            // Restore previous WebView state so the carousel / scroll position aren't reset
            webView.restoreState(savedInstanceState)
        } else {
            // Initial load
            webView.loadDataWithBaseURL("https://france.tv", html, "text/html", "UTF-8", null)
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        // Persist WebView state across configuration changes / backgrounding
        if (::webView.isInitialized) {
            webView.saveState(outState)
        }
    }

    /**
     * Build a JSON object that represents the native app context
     * and can be consumed by Piano inside the WebView.
     *
     * Keep this payload stable and privacy-safe; extend as needed.
     */
    private fun buildNativeAppContextJson(): String {
        return try {
            val json = JSONObject()
            json.put("platform", "android")
            // For this sample we hard-code version/buildType. In a real app you can
            // wire these from your own config or PackageInfo.
            json.put("appVersion", "1.0.0")
            json.put("buildType", "debug")
            json.put("deviceModel", Build.MODEL ?: "")
            json.put("deviceManufacturer", Build.MANUFACTURER ?: "")
            json.put("osVersion", Build.VERSION.RELEASE ?: "")
            json.put("sdkInt", Build.VERSION.SDK_INT)
            json.put("locale", Locale.getDefault().toLanguageTag())
            json.toString()
        } catch (e: Exception) {
            Log.e("MainActivity", "Failed to build native app context JSON", e)
            "{}"
        }
    }

    class JSBridge(private val onFullscreenChanged: (Boolean) -> Unit) {
        @JavascriptInterface
        fun logFromJS(msg: String) {
            Log.d("JSBridge", msg)
        }

        // Called from JS when the carousel / content enters or exits its fullscreen experience.
        @JavascriptInterface
        fun setFullscreen(enabled: Boolean) {
            onFullscreenChanged(enabled)
            Log.d("JSBridge", "Fullscreen state changed: $enabled")
        }
    }
}