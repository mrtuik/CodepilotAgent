package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.components.CompactAgentBar
import com.example.ui.theme.BorderLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Zinc100
import com.example.ui.theme.Zinc700
import com.example.ui.viewmodel.CodePilotUiState
import com.example.ui.viewmodel.Screen

class AiStudioWebBridge(
    private val onResponseDetected: (String) -> Unit
) {
    @JavascriptInterface
    fun postAiResponse(content: String) {
        if (content.isNotBlank()) {
            onResponseDetected(content)
        }
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AiStudioWorkspaceScreen(
    uiState: CodePilotUiState,
    onAnalyseResponse: () -> Unit,
    onDetectedResponseFromBridge: (String) -> Unit
) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageUrl by remember { mutableStateOf("https://aistudio.google.com/prompts/new_chat") }
    var isLoading by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(PureWhite)
    ) {
        // Slim browser control strip
        Surface(
            color = Zinc100,
            modifier = Modifier
                .fillMaxWidth()
                .border(1.dp, BorderLight)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(
                        onClick = { webViewInstance?.goBack() },
                        enabled = webViewInstance?.canGoBack() == true,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { webViewInstance?.goForward() },
                        enabled = webViewInstance?.canGoForward() == true,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward", modifier = Modifier.size(16.dp))
                    }
                    IconButton(
                        onClick = { webViewInstance?.reload() },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.Refresh, contentDescription = "Reload", modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "Google AI Studio",
                        fontSize = 11.sp,
                        color = Zinc700
                    )
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (isLoading) {
                        CircularProgressIndicator(modifier = Modifier.size(14.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(6.dp))
                    }

                    // Open in Chrome / external browser
                    IconButton(
                        onClick = {
                            try {
                                val currentUrl = webViewInstance?.url ?: pageUrl
                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(currentUrl))
                                context.startActivity(intent)
                            } catch (e: Exception) {
                                // ignore
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = "Open in Browser", modifier = Modifier.size(16.dp))
                    }

                    // Fallback Paste button
                    IconButton(
                        onClick = onAnalyseResponse,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Default.ContentPaste, contentDescription = "Paste Response", modifier = Modifier.size(16.dp))
                    }
                }
            }
        }

        val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
        val isImeVisible = imeBottom > 0
        val bottomClearance = if (isImeVisible) 0.dp else if (uiState.isBottomNavExpanded) 110.dp else 64.dp

        // Dominant AI Studio Area (occupies full available height with bottom clearance so floating nav never overlaps input)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = bottomClearance)
                .testTag("ai_studio_webview_container")
        ) {
            AndroidView(
                modifier = Modifier.fillMaxSize(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        layoutParams = ViewGroup.LayoutParams(
                            ViewGroup.LayoutParams.MATCH_PARENT,
                            ViewGroup.LayoutParams.MATCH_PARENT
                        )
                        setBackgroundColor(android.graphics.Color.WHITE)
                        isFocusable = true
                        isFocusableInTouchMode = true
                        setOnTouchListener { v, _ ->
                            if (!v.hasFocus()) {
                                v.requestFocus()
                            }
                            false
                        }

                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.useWideViewPort = true
                        settings.loadWithOverviewMode = true
                        settings.setSupportZoom(true)
                        settings.builtInZoomControls = true
                        settings.displayZoomControls = false

                        // Ensure default white / light theme (no force dark)
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                            @Suppress("DEPRECATION")
                            settings.forceDark = WebSettings.FORCE_DARK_OFF
                        }

                        // Google Sign-in User-Agent tweak: remove Android WebView signature (; wv)
                        val defaultUa = settings.userAgentString
                        settings.userAgentString = defaultUa.replace("; wv", "").replace("Version/4.0 ", "")

                        CookieManager.getInstance().setAcceptCookie(true)
                        CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

                        webChromeClient = WebChromeClient()

                        val bridge = AiStudioWebBridge { capturedText ->
                            onDetectedResponseFromBridge(capturedText)
                        }
                        addJavascriptInterface(bridge, "CodePilotBridge")

                        val lightThemeJs = """
                            (function() {
                                try {
                                    if (!window.__codepilot_light_override) {
                                        window.__codepilot_light_override = true;
                                        var origMatchMedia = window.matchMedia;
                                        if (origMatchMedia) {
                                            window.matchMedia = function(query) {
                                                var res = origMatchMedia.call(window, query);
                                                if (query && query.indexOf('prefers-color-scheme: dark') !== -1) {
                                                    return {
                                                        matches: false,
                                                        media: query,
                                                        onchange: null,
                                                        addListener: function() {},
                                                        removeListener: function() {},
                                                        addEventListener: function() {},
                                                        removeEventListener: function() {},
                                                        dispatchEvent: function() { return false; }
                                                    };
                                                }
                                                if (query && query.indexOf('prefers-color-scheme: light') !== -1) {
                                                    return {
                                                        matches: true,
                                                        media: query,
                                                        onchange: null,
                                                        addListener: function() {},
                                                        removeListener: function() {},
                                                        addEventListener: function() {},
                                                        removeEventListener: function() {},
                                                        dispatchEvent: function() { return true; }
                                                    };
                                                }
                                                return res;
                                            };
                                        }
                                    }
                                    if (document.documentElement) {
                                        document.documentElement.style.colorScheme = 'light';
                                        document.documentElement.setAttribute('data-theme', 'light');
                                        document.documentElement.classList.remove('dark', 'dark-theme', 'theme-dark');
                                        document.documentElement.classList.add('light', 'light-theme', 'theme-light');
                                    }
                                    if (document.body) {
                                        document.body.style.colorScheme = 'light';
                                        document.body.classList.remove('dark', 'dark-theme', 'theme-dark');
                                        document.body.classList.add('light', 'light-theme', 'theme-light');
                                    }
                                    try {
                                        localStorage.setItem('theme', 'light');
                                        localStorage.setItem('theme-mode', 'light');
                                        localStorage.setItem('ui_theme', 'light');
                                        localStorage.setItem('colorMode', 'light');
                                    } catch(e) {}
                                    if (!document.getElementById('__codepilot_light_style')) {
                                        var style = document.createElement('style');
                                        style.id = '__codepilot_light_style';
                                        style.textContent = ':root { color-scheme: light !important; --color-scheme: light !important; }';
                                        (document.head || document.documentElement).appendChild(style);
                                    }
                                } catch(e) {}
                            })();
                        """.trimIndent()

                        webViewClient = object : WebViewClient() {
                            override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                                super.onReceivedError(view, request, error)
                                if (request?.isForMainFrame == true) {
                                    isLoading = false
                                }
                            }

                            override fun onRenderProcessGone(view: WebView?, detail: RenderProcessGoneDetail?): Boolean {
                                // Prevent app crash if renderer process fails due to emulator/driver issues
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                    if (detail?.didCrash() == true) {
                                        view?.destroy()
                                    }
                                }
                                return true
                            }

                            override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                                val url = request?.url?.toString() ?: return false
                                if (url.contains("google.com") || url.contains("aistudio") || url.contains("gstatic.com") || url.contains("accounts.google")) {
                                    return false
                                }
                                return try {
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                                    context.startActivity(intent)
                                    true
                                } catch (e: Exception) {
                                    false
                                }
                            }

                            override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                                isLoading = true
                                view?.evaluateJavascript(lightThemeJs, null)
                            }

                            override fun onPageFinished(view: WebView?, url: String?) {
                                isLoading = false
                                view?.evaluateJavascript(lightThemeJs, null)

                                // Advanced automated MutationObserver bridge
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        if (window.__codepilot_observer_active) return;
                                        window.__codepilot_observer_active = true;

                                        var lastPayloadHash = "";
                                        var debounceTimer = null;

                                        function checkDomForAiCode() {
                                            try {
                                                // Keep light theme active
                                                if (document.documentElement && document.documentElement.getAttribute('data-theme') !== 'light') {
                                                    document.documentElement.style.colorScheme = 'light';
                                                    document.documentElement.setAttribute('data-theme', 'light');
                                                    document.documentElement.classList.remove('dark', 'dark-theme');
                                                }

                                                var elements = document.querySelectorAll('pre, code, div[data-content], ms-chat-turn, div.model-response');
                                                var collected = [];
                                                elements.forEach(function(el) {
                                                    var txt = el.innerText || el.textContent || "";
                                                    if (txt.indexOf('```') !== -1 || (txt.indexOf('<') !== -1 && txt.indexOf('>') !== -1) || txt.indexOf('function') !== -1 || txt.indexOf('export ') !== -1 || txt.indexOf('import ') !== -1) {
                                                        if (txt.length > 30) {
                                                            collected.push(txt);
                                                        }
                                                    }
                                                });

                                                if (collected.length === 0) {
                                                    var bodyText = document.body ? document.body.innerText : "";
                                                    var matches = bodyText.match(/```[\s\S]*?```/g);
                                                    if (matches && matches.length > 0) {
                                                        collected = matches;
                                                    }
                                                }

                                                if (collected.length > 0) {
                                                    var payload = collected.join("\n\n");
                                                    var hash = payload.length + "_" + payload.substring(0, 30) + "_" + payload.substring(payload.length - 30);
                                                    if (hash !== lastPayloadHash) {
                                                        lastPayloadHash = hash;
                                                        if (window.CodePilotBridge) {
                                                            window.CodePilotBridge.postAiResponse(payload);
                                                        }
                                                    }
                                                }
                                            } catch(e) {}
                                        }

                                        // Observe live stream response modifications
                                        var observer = new MutationObserver(function(mutations) {
                                            clearTimeout(debounceTimer);
                                            debounceTimer = setTimeout(checkDomForAiCode, 1200);
                                        });

                                        observer.observe(document.body || document.documentElement, {
                                            childList: true,
                                            subtree: true,
                                            characterData: true
                                        });

                                        // Immediate capture on copy event
                                        document.addEventListener('copy', function() {
                                            setTimeout(function() {
                                                try {
                                                    var sel = window.getSelection().toString();
                                                    if (sel && sel.length > 30 && (sel.indexOf('```') !== -1 || sel.indexOf('function') !== -1 || sel.indexOf('<') !== -1)) {
                                                        if (window.CodePilotBridge) {
                                                            window.CodePilotBridge.postAiResponse(sel);
                                                        }
                                                    }
                                                } catch(e) {}
                                            }, 100);
                                        });

                                        // Periodic check
                                        setInterval(checkDomForAiCode, 4000);
                                    })();
                                    """.trimIndent(),
                                    null
                                )
                            }
                        }

                        loadUrl(pageUrl)
                        webViewInstance = this
                    }
                },
                update = { webView ->
                    webViewInstance = webView
                }
            )
        }
    }
}
