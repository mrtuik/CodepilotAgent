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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.ui.theme.BorderLight
import com.example.ui.theme.PureWhite
import com.example.ui.theme.Zinc100
import com.example.ui.theme.Zinc700
import com.example.ui.viewmodel.CodePilotUiState
import com.example.ui.viewmodel.Screen

class AiStudioWebBridge(
    private val onResponseDetected: (String) -> Unit,
    private val onPromptSent: (String) -> Unit = {},
    private val onGenerationStartedCallback: () -> Unit = {}
) {
    @JavascriptInterface
    fun postAiResponse(content: String) {
        if (content.isNotBlank()) {
            onResponseDetected(content)
        }
    }

    @JavascriptInterface
    fun postPromptSent(text: String) {
        if (text.isNotBlank()) {
            onPromptSent(text)
        }
    }

    @JavascriptInterface
    fun onGenerationStarted() {
        onGenerationStartedCallback()
    }
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AiStudioWorkspaceScreen(
    uiState: CodePilotUiState,
    bottomClearance: Dp,
    onDetectedResponseFromBridge: (String) -> Unit,
    onPromptSentFromBridge: (String) -> Unit = {},
    onGenerationStartedFromBridge: () -> Unit = {}
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
                }
            }
        }

        val imeBottom = WindowInsets.ime.getBottom(LocalDensity.current)
        val isImeVisible = imeBottom > 0
        // Measured floating-nav clearance passed from MainActivity; reclaimed while the keyboard is visible
        val effectiveBottomClearance = if (isImeVisible) 0.dp else bottomClearance

        // Dominant AI Studio Area (occupies full available height with bottom clearance so floating nav never overlaps input)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .imePadding()
                .padding(bottom = effectiveBottomClearance)
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
                        // AI Studio ships its own viewport meta tag; wide-viewport scaling
                        // introduces blank space under the page, so keep both disabled.
                        settings.useWideViewPort = false
                        settings.loadWithOverviewMode = false
                        settings.layoutAlgorithm = WebSettings.LayoutAlgorithm.NORMAL
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

                        val bridge = AiStudioWebBridge(
                            onResponseDetected = { capturedText -> onDetectedResponseFromBridge(capturedText) },
                            onPromptSent = { promptText -> onPromptSentFromBridge(promptText) },
                            onGenerationStartedCallback = { onGenerationStartedFromBridge() }
                        )
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

                                // Automated bridge: structured, streaming-aware capture
                                view?.evaluateJavascript(
                                    """
                                    (function() {
                                        if (window.__codepilot_observer_active) return;
                                        window.__codepilot_observer_active = true;

                                        var lastPayloadHash = "";
                                        var debounceTimer = null;
                                        var QUIET_MS = 1800;

                                        function collectRoots(root, acc) {
                                            acc.push(root);
                                            try {
                                                var all = root.querySelectorAll('*');
                                                for (var i = 0; i < all.length; i++) {
                                                    if (all[i].shadowRoot) {
                                                        collectRoots(all[i].shadowRoot, acc);
                                                    }
                                                }
                                            } catch(e) {}
                                            return acc;
                                        }

                                        function keepLightTheme() {
                                            try {
                                                if (document.documentElement && document.documentElement.getAttribute('data-theme') !== 'light') {
                                                    document.documentElement.style.colorScheme = 'light';
                                                    document.documentElement.setAttribute('data-theme', 'light');
                                                    document.documentElement.classList.remove('dark', 'dark-theme');
                                                }
                                            } catch(e) {}
                                        }

                                        function looksLikeCode(txt) {
                                            if (!txt || txt.length < 30) return false;
                                            if (txt.indexOf('```') !== -1) return true;
                                            if (txt.indexOf('<!DOCTYPE') !== -1 || txt.indexOf('<html') !== -1) return true;
                                            if (/<\/[a-zA-Z][\w-]*>/.test(txt)) return true;
                                            var markers = ['function ', 'const ', 'let ', 'var ', 'class ', 'import ', 'export ', 'def ', 'public ', 'private ', '=>', '{', '};', 'package '];
                                            var hits = 0;
                                            for (var i = 0; i < markers.length; i++) {
                                                if (txt.indexOf(markers[i]) !== -1) hits++;
                                            }
                                            return hits >= 2;
                                        }

                                        // True while AI Studio is still streaming the model response
                                        function isGenerating() {
                                            var roots = collectRoots(document, []);
                                            var stopSel = 'button[aria-label*="Stop" i], button[aria-label*="stop generating" i], button[title*="Stop" i], [data-test-id*="stop"], .stop-generating';
                                            var streamSel = '.streaming, .is-streaming, .typing-indicator, .loading-indicator, .blinking-cursor, [aria-busy="true"]';
                                            for (var r = 0; r < roots.length; r++) {
                                                try { if (roots[r].querySelector(stopSel)) return true; } catch(e) {}
                                                try { if (roots[r].querySelector(streamSel)) return true; } catch(e) {}
                                            }
                                            return false;
                                        }

                                        function cleanName(raw) {
                                            if (!raw) return null;
                                            var s = String(raw).trim();
                                            if (!s) return null;
                                            s = s.split('\n')[0].trim();
                                            s = s.replace(/^[#*`\-\s]+/, '').replace(/[#*`\s:]+$/, '');
                                            if (!s || s.length > 100 || s.indexOf(' ') !== -1) return null;
                                            if (!/^[a-zA-Z0-9_.\/-]+\.[a-zA-Z0-9]+$/.test(s)) return null;
                                            if (s.indexOf('..') !== -1) return null;
                                            return s;
                                        }

                                        var JUNK_SEL = 'button, [role="button"], mat-icon, .material-symbols-outlined, .material-icons, [aria-label*="download" i], [aria-label*="copy" i], [aria-label*="expand" i], [aria-label*="collapse" i], [aria-label*="edit" i], .code-block-toolbar, .toolbar, footer';
                                        var CODE_SEL = 'pre, code, .cm-content, .view-lines, .code-container, ms-code-block';
                                        var LINE_SEL = '.view-line, .cm-line, .code-line, .line, span[class*="line-"]';

                                        // Clone the code container, drop icon / button chrome, then read the text
                                        function extractText(el) {
                                            try {
                                                var clone = el.cloneNode(true);
                                                var junk = clone.querySelectorAll(JUNK_SEL);
                                                for (var i = 0; i < junk.length; i++) {
                                                    if (junk[i].parentNode) junk[i].parentNode.removeChild(junk[i]);
                                                }
                                                var all = clone.querySelectorAll('*');
                                                for (var j = 0; j < all.length; j++) {
                                                    try {
                                                        var fam = (all[j].style && all[j].style.fontFamily) || '';
                                                        if (/material/i.test(fam) && all[j].parentNode) {
                                                            all[j].parentNode.removeChild(all[j]);
                                                        }
                                                    } catch(e) {}
                                                }
                                                var lines = clone.querySelectorAll(LINE_SEL);
                                                if (lines.length > 1) {
                                                    var parts = [];
                                                    for (var k = 0; k < lines.length; k++) {
                                                        parts.push(lines[k].textContent || '');
                                                    }
                                                    return parts.join('\n');
                                                }
                                                return clone.textContent || '';
                                            } catch(e) {
                                                return el.textContent || '';
                                            }
                                        }

                                        // Innermost code container inside (or after) a scope element
                                        function codeContainerIn(scope) {
                                            if (!scope || !scope.querySelectorAll) return null;
                                            var found = null;
                                            var nodes = [];
                                            try { nodes = scope.querySelectorAll(CODE_SEL); } catch(e) {}
                                            for (var i = 0; i < nodes.length; i++) {
                                                // prefer the innermost element (no nested code container)
                                                var inner = null;
                                                try { inner = nodes[i].querySelector(CODE_SEL); } catch(e) {}
                                                if (inner) continue;
                                                var txt = extractText(nodes[i]);
                                                if (!found || txt.length > found.len) found = { el: nodes[i], len: txt.length, text: txt };
                                            }
                                            return found;
                                        }

                                        function nextCodeAfter(el) {
                                            var node = el;
                                            for (var up = 0; up < 5 && node; up++) {
                                                var sib = node.nextElementSibling;
                                                var guard = 0;
                                                while (sib && guard < 6) {
                                                    var hit = codeContainerIn(sib);
                                                    if (hit && hit.len > 20) return hit;
                                                    try {
                                                        if (sib.matches && sib.matches(CODE_SEL)) {
                                                            var t = extractText(sib);
                                                            if (t.length > 20) return { el: sib, len: t.length, text: t };
                                                        }
                                                    } catch(e) {}
                                                    sib = sib.nextElementSibling;
                                                    guard++;
                                                }
                                                node = node.parentElement;
                                            }
                                            return null;
                                        }

                                        var NUMBERED = /^\s*\d+\.\s*([a-zA-Z0-9_.\-\/]+\.[a-zA-Z0-9]+)\s*$/;

                                        // Pair "1. index.html" style headings with the code block that follows them
                                        function collectNumberedFiles(roots) {
                                            var out = [];
                                            var seen = {};
                                            for (var r = 0; r < roots.length; r++) {
                                                var els = [];
                                                try { els = roots[r].querySelectorAll('li, p, h1, h2, h3, h4, h5, h6, strong, b, span, div'); } catch(e) {}
                                                for (var i = 0; i < els.length; i++) {
                                                    var el = els[i];
                                                    var head = '';
                                                    try {
                                                        // first text line of the element only
                                                        head = (el.textContent || '').split('\n')[0].trim();
                                                    } catch(e) {}
                                                    if (!head || head.length > 80) continue;
                                                    var m = head.match(NUMBERED);
                                                    if (!m) continue;
                                                    var name = cleanName(m[1]);
                                                    if (!name || seen[name]) continue;

                                                    var hit = codeContainerIn(el) || nextCodeAfter(el);
                                                    if (!hit || !hit.text || hit.text.trim().length < 20) continue;
                                                    seen[name] = true;
                                                    out.push({ filename: name, content: hit.text.replace(/\s+$/, '') });
                                                }
                                            }
                                            return out;
                                        }

                                        // Fallback: every standalone code container, cleaned of chrome
                                        function collectPlainBlocks(roots) {
                                            var blocks = [];
                                            for (var r = 0; r < roots.length; r++) {
                                                var elements = [];
                                                try { elements = roots[r].querySelectorAll(CODE_SEL); } catch(e) {}
                                                for (var i = 0; i < elements.length; i++) {
                                                    var inner = null;
                                                    try { inner = elements[i].querySelector(CODE_SEL); } catch(e) {}
                                                    if (inner) continue;
                                                    var txt = extractText(elements[i]);
                                                    if (!looksLikeCode(txt)) continue;
                                                    blocks.push({ filename: null, content: txt.replace(/\s+$/, '') });
                                                }
                                            }
                                            var out = [];
                                            for (var a = 0; a < blocks.length; a++) {
                                                var contained = false;
                                                for (var b = 0; b < blocks.length; b++) {
                                                    if (a === b) continue;
                                                    if (blocks[b].content.length > blocks[a].content.length && blocks[b].content.indexOf(blocks[a].content) !== -1) {
                                                        contained = true;
                                                        break;
                                                    }
                                                }
                                                if (!contained) {
                                                    var dup = false;
                                                    for (var c = 0; c < out.length; c++) {
                                                        if (out[c].content === blocks[a].content) { dup = true; break; }
                                                    }
                                                    if (!dup) out.push(blocks[a]);
                                                }
                                            }
                                            return out;
                                        }

                                        function collectBlocks() {
                                            var roots = collectRoots(document, []);
                                            var numbered = collectNumberedFiles(roots);
                                            if (numbered.length > 0) return numbered;
                                            return collectPlainBlocks(roots);
                                        }

                                        function checkDomForAiCode() {
                                            try {
                                                keepLightTheme();
                                                var blocks = collectBlocks();

                                                if (blocks.length === 0) {
                                                    var bodyText = document.body ? document.body.innerText : "";
                                                    var matches = bodyText.match(/```[\s\S]*?```/g);
                                                    if (matches && matches.length > 0) {
                                                        for (var m = 0; m < matches.length; m++) {
                                                            blocks.push({ filename: null, content: matches[m] });
                                                        }
                                                    } else if (looksLikeCode(bodyText)) {
                                                        blocks.push({ filename: null, content: bodyText });
                                                    }
                                                }

                                                if (blocks.length === 0) return;

                                                var payload = JSON.stringify(blocks);
                                                var hash = payload.length + "_" + payload.substring(0, 30) + "_" + payload.substring(payload.length - 30);
                                                if (hash === lastPayloadHash) return;
                                                lastPayloadHash = hash;
                                                if (window.CodePilotBridge) {
                                                    window.CodePilotBridge.postAiResponse(payload);
                                                }
                                            } catch(e) {}
                                        }

                                        // Only capture once generation has stopped AND the DOM has been quiet
                                        function maybeCapture() {
                                            keepLightTheme();
                                            if (isGenerating()) {
                                                clearTimeout(debounceTimer);
                                                debounceTimer = setTimeout(maybeCapture, QUIET_MS);
                                                return;
                                            }
                                            checkDomForAiCode();
                                        }

                                        function scheduleCapture() {
                                            clearTimeout(debounceTimer);
                                            debounceTimer = setTimeout(maybeCapture, QUIET_MS);
                                        }

                                        var observer = new MutationObserver(function() {
                                            scheduleCapture();
                                        });

                                        observer.observe(document.body || document.documentElement, {
                                            childList: true,
                                            subtree: true,
                                            characterData: true
                                        });

                                        // Immediate capture on copy event (plain text payload)
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

                                        // Keyboard fallback: keep the focused input visible above the on-screen keyboard
                                        document.addEventListener('focusin', function(event) {
                                            try {
                                                var t = event.target;
                                                if (!t || !t.tagName) return;
                                                var tag = t.tagName.toUpperCase();
                                                var editable = t.getAttribute && t.getAttribute('contenteditable') !== null && t.getAttribute('contenteditable') !== 'false';
                                                if (tag === 'INPUT' || tag === 'TEXTAREA' || editable) {
                                                    setTimeout(function() {
                                                        try { t.scrollIntoView({ block: 'center', behavior: 'smooth' }); } catch(e) {}
                                                    }, 250);
                                                }
                                            } catch(e) {}
                                        });


                                        // ---- Outgoing prompt capture (Enter without Shift, or Run/Send button) ----
                                        var PROMPT_SEL = 'textarea, [contenteditable="true"], [role="textbox"]';

                                        function currentPromptText() {
                                            var roots = collectRoots(document, []);
                                            var best = '';
                                            for (var r = 0; r < roots.length; r++) {
                                                var nodes = [];
                                                try { nodes = roots[r].querySelectorAll(PROMPT_SEL); } catch(e) {}
                                                for (var i = 0; i < nodes.length; i++) {
                                                    var n = nodes[i];
                                                    var v = '';
                                                    try {
                                                        v = (typeof n.value === 'string') ? n.value : (n.innerText || n.textContent || '');
                                                    } catch(e) {}
                                                    if (v) {
                                                        v = v.trim();
                                                        if (v.length > best.length) best = v;
                                                    }
                                                }
                                            }
                                            return best;
                                        }

                                        var lastPromptSent = '';
                                        function capturePromptNow() {
                                            try {
                                                var txt = currentPromptText();
                                                if (!txt) return;
                                                lastPromptSent = txt;
                                                if (window.CodePilotBridge && window.CodePilotBridge.postPromptSent) {
                                                    window.CodePilotBridge.postPromptSent(txt.substring(0, 2000));
                                                }
                                            } catch(e) {}
                                        }

                                        document.addEventListener('keydown', function(event) {
                                            try {
                                                if (event.key !== 'Enter' || event.shiftKey) return;
                                                var t = event.target;
                                                if (!t || !t.tagName) return;
                                                var tag = t.tagName.toUpperCase();
                                                var editable = t.getAttribute && t.getAttribute('contenteditable') !== null && t.getAttribute('contenteditable') !== 'false';
                                                var textbox = t.getAttribute && t.getAttribute('role') === 'textbox';
                                                if (tag === 'TEXTAREA' || editable || textbox) {
                                                    capturePromptNow();
                                                }
                                            } catch(e) {}
                                        }, true);

                                        document.addEventListener('click', function(event) {
                                            try {
                                                var el = event.target;
                                                for (var i = 0; i < 6 && el; i++) {
                                                    var isBtn = (el.tagName && el.tagName.toUpperCase() === 'BUTTON') || (el.getAttribute && el.getAttribute('role') === 'button');
                                                    if (isBtn) {
                                                        var label = '';
                                                        try {
                                                            label = ((el.getAttribute('aria-label') || '') + ' ' + (el.getAttribute('title') || '') + ' ' + (el.textContent || '')).trim();
                                                        } catch(e) {}
                                                        if (/run|send/i.test(label)) capturePromptNow();
                                                        break;
                                                    }
                                                    el = el.parentElement;
                                                }
                                            } catch(e) {}
                                        }, true);

                                        // ---- Lightweight generation edge detector (starts the live Plan run) ----
                                        var wasGenerating = false;
                                        function checkGeneratingEdge() {
                                            try {
                                                var now = isGenerating();
                                                if (now && !wasGenerating) {
                                                    if (window.CodePilotBridge && window.CodePilotBridge.onGenerationStarted) {
                                                        window.CodePilotBridge.onGenerationStarted();
                                                    }
                                                }
                                                // true -> false edge is handled by the existing scheduleCapture/maybeCapture quiet-period logic
                                                wasGenerating = now;
                                            } catch(e) {}
                                        }
                                        setInterval(checkGeneratingEdge, 400);

                                        // Light theme keeper only (no unconditional capture polling)
                                        setInterval(keepLightTheme, 2000);
                                        setTimeout(scheduleCapture, 800);
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
