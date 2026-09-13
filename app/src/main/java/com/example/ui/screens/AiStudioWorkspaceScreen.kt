package com.example.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.view.ViewGroup
import android.webkit.*
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
    private val onGenerationStartedCallback: () -> Unit = {},
    private val onStreamingTextCallback: (String) -> Unit = {},
    private val onChatChangedCallback: (String) -> Unit = {}
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

    @JavascriptInterface
    fun postStreamingText(text: String) {
        onStreamingTextCallback(text)
    }

    /** Fired when AI Studio switches to a different chat (new chat, history item, URL change). */
    @JavascriptInterface
    fun postChatChanged(chatId: String) {
        if (chatId.isNotBlank()) {
            onChatChangedCallback(chatId)
        }
    }
}


@SuppressLint("SetJavaScriptEnabled")
@Composable
fun AiStudioWorkspaceScreen(
    uiState: CodePilotUiState,
    bottomClearance: Dp,
    onDetectedResponseFromBridge: (String) -> Unit,
    onPromptSentFromBridge: (String) -> Unit = {},
    onGenerationStartedFromBridge: () -> Unit = {},
    onStreamingTextFromBridge: (String) -> Unit = {},
    onChatChangedFromBridge: (String) -> Unit = {}

) {
    val context = LocalContext.current
    var webViewInstance by remember { mutableStateOf<WebView?>(null) }
    var pageUrl by remember { mutableStateOf("https://aistudio.google.com/prompts/new_chat") }
    var isLoading by remember { mutableStateOf(false) }
    var fileChooserCallback by remember { mutableStateOf<ValueCallback<Array<Uri>>?>(null) }

    val fileChooserLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val callback = fileChooserCallback
        fileChooserCallback = null
        if (callback != null) {
            val uris = if (result.resultCode == android.app.Activity.RESULT_OK) {
                WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
            } else {
                null
            }
            callback.onReceiveValue(uris)
        }
    }

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

                        webChromeClient = object : WebChromeClient() {
                            override fun onShowFileChooser(
                                webView: WebView?,
                                filePathCallback: ValueCallback<Array<Uri>>?,
                                fileChooserParams: FileChooserParams?
                            ): Boolean {
                                fileChooserCallback?.onReceiveValue(null)
                                fileChooserCallback = filePathCallback
                                return try {
                                    val intent = fileChooserParams?.createIntent()
                                    if (intent == null) {
                                        fileChooserCallback = null
                                        false
                                    } else {
                                        fileChooserLauncher.launch(intent)
                                        true
                                    }
                                } catch (e: Exception) {
                                    fileChooserCallback = null
                                    false
                                }
                            }
                        }

                        val bridge = AiStudioWebBridge(
                            onResponseDetected = { capturedText -> onDetectedResponseFromBridge(capturedText) },
                            onPromptSent = { promptText -> onPromptSentFromBridge(promptText) },
                            onGenerationStartedCallback = { onGenerationStartedFromBridge() },
                            onStreamingTextCallback = { partial -> onStreamingTextFromBridge(partial) },
                            onChatChangedCallback = { chatId -> onChatChangedFromBridge(chatId) }
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

                                        // ---- Playground gate: nothing observes until the real chat UI exists ----
                                        // Anything reachable on the login / consent / account-chooser page must NEVER
                                        // satisfy this check.
                                        var PROMPT_INPUT_SEL = 'ms-prompt-input-wrapper, ms-chunk-input, ms-autosize-textarea, textarea[aria-label*="prompt" i], textarea[placeholder*="prompt" i], [role="textbox"][aria-label*="prompt" i]';
                                        var RESPONSE_SEL = 'ms-chat-turn, ms-model-response, ms-prompt-chunk, .chat-turn-container, [data-turn-role="Model"]';

                                        function onAuthPage() {
                                            try {
                                                var h = (location.hostname || '');
                                                if (h.indexOf('accounts.google') !== -1) return true;
                                                var pth = (location.pathname || '');
                                                if (pth.indexOf('/signin') !== -1 || pth.indexOf('/ServiceLogin') !== -1 || pth.indexOf('/oauth') !== -1) return true;
                                            } catch(e) {}
                                            return false;
                                        }

                                        function queryAnyRoot(sel) {
                                            var roots = collectRoots(document, []);
                                            for (var r = 0; r < roots.length; r++) {
                                                try { var hit = roots[r].querySelector(sel); if (hit) return hit; } catch(e) {}
                                            }
                                            return null;
                                        }

                                        // Confirmed only when the prompt input (or an existing conversation turn) is
                                        // present on an aistudio prompts URL - never on the login page.
                                        function isPlaygroundReady() {
                                            try {
                                                if (onAuthPage()) return false;
                                                if ((location.hostname || '').indexOf('aistudio.google.com') === -1) return false;
                                                var pth = (location.pathname || '');
                                                if (pth.indexOf('/prompts') === -1 && pth.indexOf('/apps') === -1) return false;
                                                if (queryAnyRoot(PROMPT_INPUT_SEL)) return true;
                                                if (queryAnyRoot(RESPONSE_SEL)) return true;
                                            } catch(e) {}
                                            return false;
                                        }

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

                                        // True while AI Studio is still streaming the model response.
                                        // Gated on the playground being present so login-page spinners can't trip it.
                                        function isGenerating() {
                                            if (!isPlaygroundReady()) return false;
                                            var roots = collectRoots(document, []);
                                            var stopSel = 'button[aria-label*="Stop" i], button[aria-label*="stop generating" i], button[title*="Stop" i], [data-test-id*="stop"], .stop-generating';
                                            var streamSel = 'ms-chat-turn .streaming, .is-streaming, .typing-indicator, .blinking-cursor, ms-model-response[aria-busy="true"]';
                                            for (var r = 0; r < roots.length; r++) {
                                                try { if (roots[r].querySelector(stopSel)) return true; } catch(e) {}
                                                try { if (roots[r].querySelector(streamSel)) return true; } catch(e) {}
                                            }
                                            return false;
                                        }

                                        // ---- UI-chrome filtering for streamed text ----
                                        // AI Studio renders material icon ligatures ("code", "play_circle",
                                        // "content_copy", "expand_less", ...) as ordinary text nodes and puts
                                        // toolbar button labels right above code blocks. innerText therefore
                                        // mixes them into the model reply, so they are skipped structurally
                                        // (icon fonts / buttons / aria-hidden chrome) and then, as a last
                                        // resort, by ligature-token line matching.
                                        var LIGATURES = {
                                            'code': 1, 'code_blocks': 1, 'play_circle': 1, 'play_arrow': 1, 'stop_circle': 1,
                                            'download': 1, 'upload': 1, 'content_copy': 1, 'content_paste': 1, 'copy_all': 1,
                                            'expand_less': 1, 'expand_more': 1, 'expand_content': 1, 'unfold_more': 1, 'unfold_less': 1,
                                            'keyboard_arrow_down': 1, 'keyboard_arrow_up': 1, 'keyboard_arrow_left': 1, 'keyboard_arrow_right': 1,
                                            'more_vert': 1, 'more_horiz': 1, 'edit': 1, 'edit_note': 1, 'delete': 1, 'refresh': 1,
                                            'restart_alt': 1, 'tune': 1, 'settings': 1, 'close': 1, 'add': 1, 'remove': 1, 'check': 1,
                                            'done': 1, 'thumb_up': 1, 'thumb_down': 1, 'share': 1, 'save': 1, 'open_in_new': 1,
                                            'open_in_full': 1, 'fullscreen': 1, 'fullscreen_exit': 1, 'visibility': 1, 'visibility_off': 1,
                                            'auto_awesome': 1, 'spark': 1, 'terminal': 1, 'description': 1, 'preview': 1, 'image': 1,
                                            'mic': 1, 'volume_up': 1, 'drag_indicator': 1, 'arrow_downward': 1, 'arrow_upward': 1,
                                            'folder': 1, 'insert_drive_file': 1, 'attach_file': 1, 'sync': 1, 'history': 1, 'menu': 1,
                                            'search': 1, 'send': 1, 'wrap_text': 1, 'format_align_left': 1, 'text_snippet': 1,
                                            'difference': 1, 'compare_arrows': 1, 'chevron_left': 1, 'chevron_right': 1
                                        };
                                        var BLOCK_TAGS = /^(DIV|P|PRE|LI|UL|OL|TR|SECTION|ARTICLE|H1|H2|H3|H4|H5|H6|BLOCKQUOTE|TABLE|HEADER|FOOTER|MS-CODE-BLOCK|MS-TEXT-CHUNK|MS-PROMPT-CHUNK)$/;

                                        // True for icon / button / toolbar chrome that must never reach the Plan tab
                                        function isChromeNode(el) {
                                            try {
                                                var tag = (el.tagName || '').toUpperCase();
                                                if (tag === 'BUTTON' || tag === 'SCRIPT' || tag === 'STYLE' || tag === 'MAT-ICON' ||
                                                    tag === 'MAT-TOOLTIP' || tag === 'MAT-ICON-BUTTON' || tag === 'MS-TOOLBAR') return true;
                                                var role = (el.getAttribute && el.getAttribute('role')) || '';
                                                if (role === 'button' || role === 'toolbar' || role === 'menu' ||
                                                    role === 'menuitem' || role === 'tooltip') return true;
                                                if (el.getAttribute && el.getAttribute('aria-hidden') === 'true') return true;
                                                var cls = '';
                                                try { cls = (el.getAttribute && el.getAttribute('class')) || ''; } catch(e) {}
                                                if (/material-symbols|material-icons|mat-icon|mdc-button|mat-mdc-button|mat-ripple|toolbar|icon-button|cdk-visually-hidden|notranslate/i.test(cls)) return true;
                                                var fam = '';
                                                try { fam = (window.getComputedStyle(el).fontFamily || ''); } catch(e) {}
                                                if (/material/i.test(fam)) return true;
                                            } catch(e) {}
                                            return false;
                                        }

                                        // Reads text straight off the live nodes (no clone) so it stays in sync
                                        // with what AI Studio has painted, while skipping chrome subtrees.
                                        function readableText(node) {
                                            if (!node) return '';
                                            if (node.nodeType === 3) return node.nodeValue || '';
                                            if (node.nodeType !== 1) return '';
                                            var tag = (node.tagName || '').toUpperCase();
                                            if (tag === 'BR') return '\n';
                                            if (isChromeNode(node)) return '';
                                            var out = '';
                                            var kids = node.childNodes;
                                            for (var i = 0; i < kids.length; i++) {
                                                out += readableText(kids[i]);
                                            }
                                            if (BLOCK_TAGS.test(tag)) out = '\n' + out + '\n';
                                            return out;
                                        }

                                        function stripLigatureLines(txt) {
                                            var lines = String(txt || '').split('\n');
                                            var kept = [];
                                            for (var i = 0; i < lines.length; i++) {
                                                var line = lines[i];
                                                var probe = line.trim();
                                                if (probe && /^[a-z][a-z0-9_]*$/.test(probe) && LIGATURES[probe]) continue;
                                                kept.push(line.replace(/\s+$/, ''));
                                            }
                                            return kept.join('\n').replace(/\n{3,}/g, '\n\n').trim();
                                        }

                                        // Live partial text currently visible in the newest model response box
                                        function currentStreamingText() {
                                            try {
                                                var roots = collectRoots(document, []);
                                                var last = null;
                                                for (var r = 0; r < roots.length; r++) {
                                                    var turns = [];
                                                    try { turns = roots[r].querySelectorAll(RESPONSE_SEL); } catch(e) {}
                                                    if (turns.length) last = turns[turns.length - 1];
                                                }
                                                if (!last) return '';
                                                var txt = stripLigatureLines(readableText(last));
                                                if (!txt) txt = stripLigatureLines(last.innerText || last.textContent || '');
                                                return txt;
                                            } catch(e) { return ''; }
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
                                                    return stripLigatureLines(parts.join('\n'));
                                                }
                                                return stripLigatureLines(clone.textContent || '');
                                            } catch(e) {
                                                return stripLigatureLines(el.textContent || '');
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

                                        // Attached only after the playground is confirmed (see startObserving)
                                        var captureObserver = null;
                                        function attachCaptureObserver() {
                                            if (captureObserver) return;
                                            captureObserver = new MutationObserver(function() {
                                                scheduleCapture();
                                                checkGeneratingEdge();
                                            });
                                            captureObserver.observe(document.body || document.documentElement, {
                                                childList: true,
                                                subtree: true,
                                                characterData: true
                                            });
                                        }

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

                                        // Keyboard fallback: keep the focused input visible above the on-screen keyboard.
                                        // The WebView resizes (adjustResize) when the IME opens, but AI Studio's page
                                        // does not reliably re-scroll inside the embedded WebView, so we track the
                                        // focused editable element and re-scroll it whenever the visual viewport
                                        // shrinks, retrying a few times while the keyboard animation settles.
                                        var __kbFocusedEl = null;
                                        var __kbScrollTimer = null;

                                        function isEditableTarget(t) {
                                            try {
                                                if (!t || !t.tagName) return false;
                                                var tag = t.tagName.toUpperCase();
                                                var editable = t.getAttribute && t.getAttribute('contenteditable') !== null && t.getAttribute('contenteditable') !== 'false';
                                                var textbox = t.getAttribute && t.getAttribute('role') === 'textbox';
                                                return tag === 'INPUT' || tag === 'TEXTAREA' || editable || textbox;
                                            } catch(e) { return false; }
                                        }

                                        // Scrolls so the focused element sits inside the visible viewport:
                                        // manual window.scrollBy correction (works even when ancestors capture
                                        // scrolling) followed by scrollIntoView as a container-level fallback.
                                        function scrollFocusedIntoView() {
                                            try {
                                                var el = __kbFocusedEl;
                                                if (!el || !el.isConnected) return;
                                                var rect = el.getBoundingClientRect();
                                                var visibleH = (window.visualViewport ? window.visualViewport.height : window.innerHeight);
                                                var margin = 12;
                                                var delta = 0;
                                                if (rect.bottom > visibleH - margin) {
                                                    delta = rect.bottom - (visibleH - margin);
                                                } else if (rect.top < margin) {
                                                    delta = rect.top - margin;
                                                }
                                                if (delta !== 0) {
                                                    window.scrollBy(0, delta);
                                                }
                                                el.scrollIntoView({ block: 'center', behavior: 'auto' });
                                            } catch(e) {}
                                        }

                                        // Repeated attempts while the keyboard slide-up animation completes.
                                        function scheduleKeyboardScrolls() {
                                            if (__kbScrollTimer) { clearTimeout(__kbScrollTimer); }
                                            var delays = [100, 300, 600, 1000];
                                            var step = function(i) {
                                                scrollFocusedIntoView();
                                                if (i + 1 < delays.length) {
                                                    __kbScrollTimer = setTimeout(function() { step(i + 1); }, delays[i + 1] - delays[i]);
                                                }
                                            };
                                            __kbScrollTimer = setTimeout(function() { step(0); }, delays[0]);
                                        }

                                        document.addEventListener('focusin', function(event) {
                                            try {
                                                if (!isEditableTarget(event.target)) return;
                                                __kbFocusedEl = event.target;
                                                scheduleKeyboardScrolls();
                                            } catch(e) {}
                                        }, true);

                                        document.addEventListener('focusout', function() {
                                            __kbFocusedEl = null;
                                            if (__kbScrollTimer) { clearTimeout(__kbScrollTimer); __kbScrollTimer = null; }
                                        }, true);

                                        // Re-scroll whenever the visual viewport resizes (keyboard open/close,
                                        // suggestion bar toggling) while an editable element holds focus.
                                        if (window.visualViewport) {
                                            window.visualViewport.addEventListener('resize', function() {
                                                try {
                                                    if (__kbFocusedEl && __kbFocusedEl.isConnected) {
                                                        scheduleKeyboardScrolls();
                                                    } else {
                                                        var ae = document.activeElement;
                                                        if (isEditableTarget(ae)) {
                                                            __kbFocusedEl = ae;
                                                            scheduleKeyboardScrolls();
                                                        }
                                                    }
                                                } catch(e) {}
                                            });
                                        }


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

                                        // ---- Immediate generation-start detector (opens the Plan tab right away) ----
                                        // Deliberately separate from the debounced maybeCapture() quiet-period logic,
                                        // which still only grabs the FINAL response text for parsing.
                                        var wasGenerating = false;
                                        var streamPollTimer = null;
                                        var lastStreamSent = '';

                                        function pushStreamingPreview() {
                                            try {
                                                var txt = currentStreamingText();
                                                if (!txt || txt === lastStreamSent) return;
                                                lastStreamSent = txt;
                                                if (window.CodePilotBridge && window.CodePilotBridge.postStreamingText) {
                                                    // Send the whole visible reply, unclipped, on every frame.
                                                    window.CodePilotBridge.postStreamingText(txt.length > 200000 ? txt.substring(txt.length - 200000) : txt);
                                                }
                                            } catch(e) {}
                                        }

                                        // Real-time push: every DOM mutation inside the response schedules a
                                        // frame-aligned push, so the Plan tab paints the same chunk in the same
                                        // frame AI Studio does. No debounce, no batching window.
                                        var streamFrame = null;
                                        var streamObserver = null;

                                        function scheduleStreamPush() {
                                            if (streamFrame !== null) return;
                                            var raf = window.requestAnimationFrame || function(cb) { return setTimeout(cb, 0); };
                                            streamFrame = raf(function() {
                                                streamFrame = null;
                                                pushStreamingPreview();
                                            });
                                        }

                                        function startStreamPolling() {
                                            if (!streamObserver) {
                                                try {
                                                    streamObserver = new MutationObserver(scheduleStreamPush);
                                                    streamObserver.observe(document.body || document.documentElement, {
                                                        childList: true,
                                                        subtree: true,
                                                        characterData: true
                                                    });
                                                } catch(e) {}
                                            }
                                            scheduleStreamPush();
                                            if (streamPollTimer) return;
                                            // Safety net only: catches text painted inside shadow roots or canvases
                                            // that the observer above cannot see.
                                            streamPollTimer = setInterval(function() {
                                                pushStreamingPreview();
                                                if (!isGenerating()) {
                                                    // Final flush, then stop the fallback ticker (observer stays live).
                                                    pushStreamingPreview();
                                                    clearInterval(streamPollTimer);
                                                    streamPollTimer = null;
                                                }
                                            }, 60);
                                        }

                                        // Second immediate signal: a brand new model response container appearing.
                                        var lastTurnCount = -1;
                                        function newResponseContainerAppeared() {
                                            try {
                                                var roots = collectRoots(document, []);
                                                var count = 0;
                                                for (var r = 0; r < roots.length; r++) {
                                                    try { count += roots[r].querySelectorAll(RESPONSE_SEL).length; } catch(e) {}
                                                }
                                                var grew = (lastTurnCount >= 0 && count > lastTurnCount);
                                                lastTurnCount = count;
                                                return grew;
                                            } catch(e) { return false; }
                                        }

                                        function checkGeneratingEdge() {
                                            try {
                                                if (!isPlaygroundReady()) return;
                                                var appeared = newResponseContainerAppeared();
                                                var now = isGenerating() || appeared;
                                                if (now && !wasGenerating) {
                                                    lastStreamSent = '';
                                                    if (window.CodePilotBridge && window.CodePilotBridge.onGenerationStarted) {
                                                        window.CodePilotBridge.onGenerationStarted();
                                                    }
                                                    startStreamPolling();
                                                }
                                                // true -> false edge stays with the existing quiet-period capture logic
                                                wasGenerating = now;
                                            } catch(e) {}
                                        }

                                        // ---- Chat / history change detection ----
                                        // Every distinct AI Studio chat maps to its own CodePilot project,
                                        // so its plan timeline and artifacts zip stay separate.
                                        var lastChatKey = '';

                                        function currentChatKey() {
                                            try {
                                                if (onAuthPage()) return '';
                                                var h = (location.hostname || '');
                                                if (h.indexOf('aistudio.google.com') === -1) return '';
                                                var pth = (location.pathname || '');
                                                if (pth.indexOf('/prompts') === -1 && pth.indexOf('/apps') === -1) return '';
                                                if (/new_chat|new-chat|\/prompts\/?$/.test(pth)) return 'new_chat';
                                                return pth;
                                            } catch(e) { return ''; }
                                        }

                                        function checkChatChanged() {
                                            try {
                                                var key = currentChatKey();
                                                if (!key || key === lastChatKey) return;
                                                var previous = lastChatKey;
                                                lastChatKey = key;
                                                // A fresh "new chat" screen is a brand new conversation every time
                                                var chatId = (key === 'new_chat') ? ('new_chat:' + Date.now()) : ('chat:' + key);
                                                // Reset per-chat capture state so the new chat is observed from scratch
                                                lastPayloadHash = '';
                                                lastTurnCount = -1;
                                                wasGenerating = false;
                                                lastStreamSent = '';
                                                lastPromptSent = '';
                                                if (window.CodePilotBridge && window.CodePilotBridge.postChatChanged) {
                                                    window.CodePilotBridge.postChatChanged(chatId);
                                                }
                                            } catch(e) {}
                                        }

                                        // ---- Startup: theme keeper runs always; DOM observation waits for the real
                                        // playground UI so the login screen can never trigger a run ----
                                        setInterval(keepLightTheme, 2000);
                                        setInterval(checkChatChanged, 600);


                                        var started = false;
                                        function startObserving() {
                                            if (started) return;
                                            started = true;
                                            checkChatChanged();
                                            attachCaptureObserver();
                                            setInterval(checkGeneratingEdge, 120);
                                            setTimeout(scheduleCapture, 800);
                                        }

                                        var gateTimer = setInterval(function() {
                                            if (isPlaygroundReady()) {
                                                clearInterval(gateTimer);
                                                startObserving();
                                            }
                                        }, 500);
                                        if (isPlaygroundReady()) {
                                            clearInterval(gateTimer);
                                            startObserving();
                                        }
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
