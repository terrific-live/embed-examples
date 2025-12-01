package com.example.terrificembedexample_kotlin

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.terrificembedexample_kotlin.ui.theme.TerrificEmbedExampleKothlinTheme
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class MainActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            TerrificEmbedExampleKothlinTheme {
                TerrificEmbedScreen()
            }
        }
    }
}

/**
 * Top-level composable that recreates the old XML layout using Jetpack Compose:
 * - Scrollable LazyColumn
 * - Native header/body/footer blocks
 * - Embedded WebView for the Terrific carousel
 */
@Composable
private fun TerrificEmbedScreen() {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    // Track whether the Terrific carousel is in fullscreen mode.
    var isWebViewFullscreen by remember { mutableStateOf(false) }

    // Heights in dp – keep content tall enough to scroll, but lighter for WebView/Chromium tiling.
    // Using 400dp instead of 800dp reduces GPU tile memory pressure, especially on emulators.
    val headerHeightDp = 600.dp
    val bodyHeightDp = 600.dp
    val collapsedWebViewHeightDp = 450.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    // Store heights and scroll offsets in pixels so they line up with WebView/layout
    val collapsedWebViewHeightPx = with(density) { collapsedWebViewHeightDp.roundToPx() }
    val expandedWebViewHeightPx = with(density) { screenHeightDp.roundToPx() }

    var webViewHeightPx by remember { mutableIntStateOf(collapsedWebViewHeightPx) }

    // Remember the list position before entering fullscreen so we can restore it.
    var previousIndex by remember { mutableIntStateOf(0) }
    var previousOffset by remember { mutableIntStateOf(0) }
    // Index of the LazyColumn item that hosts the WebView (header=0, body=1, webview=2, footer=3).
    val webViewItemIndex = 2

    // Persistent WebView instance for this screen, independent of LazyColumn item recycling.
    val webView = remember { WebView(context) }

    // Configure WebView and load HTML once.
    LaunchedEffect(Unit) {
        configureTerrificWebView(
            webView = webView,
            collapsedHeightPx = collapsedWebViewHeightPx,
            expandedHeightPx = expandedWebViewHeightPx,
            onUpdateWebViewHeight = { newHeightPx ->
                webViewHeightPx = newHeightPx
            },
            onEnterFullscreen = {
                isWebViewFullscreen = true
                coroutineScope.launch {
                    previousIndex = listState.firstVisibleItemIndex
                    previousOffset = listState.firstVisibleItemScrollOffset
                    listState.animateScrollToItem(webViewItemIndex)
                }
            },
            onExitFullscreen = {
                isWebViewFullscreen = false
                coroutineScope.launch {
                    listState.animateScrollToItem(previousIndex, previousOffset)
                }
            }
        )

        val html = buildTerrificHtml()
        webView.loadDataWithBaseURL(
            "https://france.tv",
            html,
            "text/html",
            "UTF-8",
            null
        )
    }

    // Destroy WebView when the screen is disposed to avoid leaks.
    DisposableEffect(Unit) {
        onDispose {
            webView.destroy()
        }
    }

    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        userScrollEnabled = !isWebViewFullscreen
    ) {
        // Native header title
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(headerHeightDp)
                    .background(Color(0xFFFFECE4))
                    .padding(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Native header above WebView",
                    fontSize = 28.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // Native header body
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(bodyHeightDp)
                    .background(Color(0xFFE4F2FF))
                    .padding(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Scroll down to reach the embedded Terrific carousel. This area is pure native UI on top of the WebView.",
                    fontSize = 18.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }

        // WebView embedded inside LazyColumn; this item may be recycled, but the WebView instance is persistent.
        item {
            TerrificWebView(
                modifier = Modifier.fillMaxWidth(),
                webView = webView,
                webViewHeightPx = webViewHeightPx
            )
        }

        // Native footer
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(400.dp)
                    .background(Color(0xFFE8FFE4))
                    .padding(24.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text(
                    text = "Native footer below WebView",
                    fontSize = 16.sp,
                    color = MaterialTheme.colorScheme.onBackground
                )
            }
        }
    }
}

/**
 * A composable that hosts the Terrific WebView using AndroidView.
 * It keeps the same JS bridge, header injection, and fullscreen behavior as the View-based version.
 */
@Composable
private fun TerrificWebView(
    modifier: Modifier,
    webView: WebView,
    webViewHeightPx: Int
) {
    val density = LocalDensity.current
    val webViewHeightDp = with(density) { webViewHeightPx.toDp() }

    AndroidView(
        modifier = modifier.height(webViewHeightDp),
        factory = { webView },
        update = { view ->
            // Keep layout params in sync with current height
            val lp = view.layoutParams
            if (lp != null && lp.height != webViewHeightPx) {
                lp.height = webViewHeightPx
                view.layoutParams = lp
            }
        }
    )
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureTerrificWebView(
    webView: WebView,
    collapsedHeightPx: Int,
    expandedHeightPx: Int,
    onUpdateWebViewHeight: (Int) -> Unit,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit
) {
    val webSettings: WebSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.domStorageEnabled = true
    webSettings.mediaPlaybackRequiresUserGesture = false

    // Disable fullscreen playback, keep inline
    webSettings.setMediaPlaybackRequiresUserGesture(false)

    webView.layoutParams = webView.layoutParams?.apply {
        height = collapsedHeightPx
    } ?: android.view.ViewGroup.LayoutParams(
        android.view.ViewGroup.LayoutParams.MATCH_PARENT,
        collapsedHeightPx
    )

    webView.webChromeClient = object : WebChromeClient() {
        override fun onConsoleMessage(message: ConsoleMessage): Boolean {
            Log.d(
                "JS",
                "${message.message()} -- From line ${message.lineNumber()} of ${message.sourceId()}"
            )
            return true
        }
    }

    // Basic WebViewClient: keep default network behavior and just inject our context JSON.
    webView.webViewClient = object : WebViewClient() {
        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)

            // Build a JSON context object with native app info for Piano (or other analytics).
            val contextJson = buildNativeAppContextJson()

            // Expose it into the WebView as a global + dispatch a custom event.
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
    webView.addJavascriptInterface(
        JSBridge { fullscreen ->
            if (fullscreen) {
                onUpdateWebViewHeight(expandedHeightPx)
                onEnterFullscreen()
            } else {
                onUpdateWebViewHeight(collapsedHeightPx)
                onExitFullscreen()
            }
        },
        "AndroidBridge"
    )
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

/**
 * JS bridge used by the Terrific integration to control fullscreen state.
 */
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

/**
 * Inline HTML used to bootstrap the Terrific carousel inside the WebView.
 * This mirrors the original implementation.
 */
private fun buildTerrificHtml(): String {
    return """
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
            <div data-source="terrific"
                 embedding-id="9iM1LIQ3DHqs06jyxuuq"
                 num-of-items="10"
                 style="height: 450px">
            </div>
        </body>
        </html>
    """.trimIndent()
}