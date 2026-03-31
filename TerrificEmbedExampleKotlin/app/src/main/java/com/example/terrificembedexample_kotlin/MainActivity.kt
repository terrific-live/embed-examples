package com.example.terrificembedexample_kotlin

import android.annotation.SuppressLint
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.webkit.ConsoleMessage
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.terrificembedexample_kotlin.ui.theme.TerrificEmbedExampleKothlinTheme
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            TerrificEmbedExampleKothlinTheme {
                MainScreen()
            }
        }
    }
}

@Composable
fun MainScreen() {
    var selectedTab by remember { mutableIntStateOf(0) }

    Scaffold(
        bottomBar = {
            NavigationBar {
                NavigationBarItem(
                    icon = { Text("Terrific") },
                    label = { Text("Home") },
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 }
                )
                NavigationBarItem(
                    icon = { Text("Empty") },
                    label = { Text("Other") },
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            when (selectedTab) {
                0 -> TerrificScreen()
                1 -> EmptyScreen()
            }
        }
    }
}

@Composable
fun TerrificScreen() {
    val context = LocalContext.current
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current

    var isWebViewFullscreen by remember { mutableStateOf(false) }

    val collapsedWebViewHeightDp = 450.dp
    val screenHeightDp = configuration.screenHeightDp.dp

    val collapsedWebViewHeightPx = with(density) { collapsedWebViewHeightDp.roundToPx() }
    val expandedWebViewHeightPx = with(density) { screenHeightDp.roundToPx() }

    var webViewHeightPx by remember { mutableIntStateOf(collapsedWebViewHeightPx) }

    var previousIndex by remember { mutableIntStateOf(0) }
    var previousOffset by remember { mutableIntStateOf(0) }
    val webViewItemIndex = 2 // Terrific est en 3ème position (index 2)

    val webView = remember { WebView(context) }
    var webViewReady by remember { mutableStateOf(false) }
    var pendingWebViewHeightPx by remember { mutableStateOf<Int?>(null) }
    var pendingFullscreenState by remember { mutableStateOf<Boolean?>(null) }

    val enterFullscreen: () -> Unit = {
        isWebViewFullscreen = true
        coroutineScope.launch {
            previousIndex = listState.firstVisibleItemIndex
            previousOffset = listState.firstVisibleItemScrollOffset
            listState.animateScrollToItem(webViewItemIndex)
        }
    }

    val exitFullscreen: () -> Unit = {
        isWebViewFullscreen = false
        coroutineScope.launch {
            listState.animateScrollToItem(previousIndex, previousOffset)
        }
    }

    LaunchedEffect(Unit) {
        configureTerrificWebView(
            webView = webView,
            collapsedHeightPx = collapsedWebViewHeightPx,
            expandedHeightPx = expandedWebViewHeightPx,
            onUpdateWebViewHeight = { newHeightPx ->
                if (webViewReady) {
                    webViewHeightPx = newHeightPx
                } else {
                    // Defer height jumps until we have the first visual frame.
                    pendingWebViewHeightPx = newHeightPx
                }
            },
            onEnterFullscreen = {
                if (webViewReady) {
                    enterFullscreen()
                } else {
                    pendingFullscreenState = true
                }
            },
            onExitFullscreen = {
                if (webViewReady) {
                    exitFullscreen()
                } else {
                    pendingFullscreenState = false
                }
            },
            onFirstVisualCommit = {
                if (!webViewReady) {
                    webViewReady = true
                }

                pendingWebViewHeightPx?.let { pendingHeight ->
                    webViewHeightPx = pendingHeight
                    pendingWebViewHeightPx = null
                }

                pendingFullscreenState?.let { pending ->
                    pendingFullscreenState = null
                    if (pending) enterFullscreen() else exitFullscreen()
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
        // Item 1
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(8.dp)
                    .background(Color(0xFFFFEBEE)),
                contentAlignment = Alignment.Center
            ) {
                Text("Item 1")
            }
        }

        // Item 2
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(8.dp)
                    .background(Color(0xFFFFF3E0)),
                contentAlignment = Alignment.Center
            ) {
                Text("Item 2")
            }
        }

        // Item 3 - Terrific WebView
        item {
            TerrificWebView(
                modifier = Modifier.fillMaxWidth(),
                webView = webView,
                webViewHeightPx = webViewHeightPx,
                isReady = webViewReady
            )
        }

        // Items restants
        items(20) { index ->
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(100.dp)
                    .padding(8.dp)
                    .background(Color(0xFFE3F2FD)),
                contentAlignment = Alignment.Center
            ) {
                Text("Item ${index + 4}")
            }
        }
    }
}

@Composable
fun EmptyScreen() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text("Empty Screen")
    }
}

@Composable
private fun TerrificWebView(
    modifier: Modifier,
    webView: WebView,
    webViewHeightPx: Int,
    isReady: Boolean
) {
    val density = LocalDensity.current
    val webViewHeightDp = with(density) { webViewHeightPx.toDp() }

    val webViewAlpha by animateFloatAsState(
        targetValue = if (isReady) 1f else 0f,
        animationSpec = tween(durationMillis = 200),
        label = "webViewAlpha"
    )

    val overlayAlpha by animateFloatAsState(
        targetValue = if (isReady) 0f else 1f,
        animationSpec = tween(durationMillis = 200),
        label = "overlayAlpha"
    )

    Box(
        modifier = modifier
            .height(webViewHeightDp)
    ) {
        AndroidView(
            modifier = Modifier.fillMaxSize(),
            factory = {
                webView.apply {
                    // Keep the WebView mounted and loading, but don't show intermediate blank paints.
                    alpha = 0f
                }
            },
            update = { view ->
                view.alpha = webViewAlpha
                val lp = view.layoutParams
                if (lp != null && lp.height != webViewHeightPx) {
                    lp.height = webViewHeightPx
                    view.layoutParams = lp
                }
            }
        )

        // Overlay until (and slightly through) first paint; fade it out to avoid 1-frame "pop".
        if (overlayAlpha > 0.01f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .alpha(overlayAlpha),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = Color.White)
            }
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
private fun configureTerrificWebView(
    webView: WebView,
    collapsedHeightPx: Int,
    expandedHeightPx: Int,
    onUpdateWebViewHeight: (Int) -> Unit,
    onEnterFullscreen: () -> Unit,
    onExitFullscreen: () -> Unit,
    onFirstVisualCommit: () -> Unit
) {
    var firstVisualCommitSent = false
    fun requestFirstVisualCommitCallback(view: WebView?) {
        val target = view ?: webView
        if (firstVisualCommitSent) return
        target.postVisualStateCallback(
            0L,
            object : WebView.VisualStateCallback() {
                override fun onComplete(requestId: Long) {
                    if (firstVisualCommitSent) return
                    firstVisualCommitSent = true
                    onFirstVisualCommit()
                }
            }
        )
    }

    val webSettings: WebSettings = webView.settings
    webSettings.javaScriptEnabled = true
    webSettings.domStorageEnabled = true
    webSettings.mediaPlaybackRequiresUserGesture = false

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

    webView.webViewClient = object : WebViewClient() {
        override fun onPageCommitVisible(view: WebView?, url: String?) {
            super.onPageCommitVisible(view, url)
            // Commit-visible does not guarantee Terrific content has painted yet.
            // Wait for the first visual state callback (i.e. first draw).
            requestFirstVisualCommitCallback(view)
        }

        override fun onPageFinished(view: WebView?, url: String?) {
            super.onPageFinished(view, url)
            Log.d("WebView", "Page finished loading")
            // Fallback in case commit-visible isn't fired for some reason.
            requestFirstVisualCommitCallback(view)
        }
    }

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

class JSBridge(private val onFullscreenChanged: (Boolean) -> Unit) {
    @JavascriptInterface
    fun logFromJS(msg: String) {
        Log.d("JSBridge", msg)
    }

    @JavascriptInterface
    fun setFullscreen(enabled: Boolean) {
        onFullscreenChanged(enabled)
        Log.d("JSBridge", "Fullscreen state changed: $enabled")
    }
}

private fun buildTerrificHtml(): String {
    return """
        <!DOCTYPE html>
        <html>
        <head>
            <meta name="viewport" content="width=device-width, initial-scale=1.0">
            <script defer src="https://terrific.live/terrific-sdk.js" storeId="nzRdWaBc1JPk2XN3B9bp"></script>
            <script>
                console.log("JS console log active");
                window.onload = () => {
                    console.log("Terrific page loaded");
                };

                function setAndroidFullscreen(isFullscreen) {
                    if (window.AndroidBridge && AndroidBridge.setFullscreen) {
                        AndroidBridge.setFullscreen(isFullscreen);
                    }
                }

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