package com.example.engine

import org.json.JSONArray
import org.json.JSONException
import java.util.regex.Pattern

data class ParsedCodeCandidate(
    val path: String,
    val name: String,
    val language: String,
    val content: String,
    val confidence: Double,
    val ambiguity: Boolean,
    val isIncomplete: Boolean,
    val sourceHeader: String = ""
)

object ResponseParser {
    // Matches ```[language][ :filepath]
    private val CODE_FENCE_REGEX = Pattern.compile(
        "(?:^|\\n)```([a-zA-Z0-9_+#\\-\\.]+)?(?:[ :\\t]+([^\\r\\n`]+))?\\r?\\n([\\s\\S]*?)(?:\\r?\\n```|\\Z)"
    )

    fun parse(rawResponse: String): List<ParsedCodeCandidate> {
        val candidates = mutableListOf<ParsedCodeCandidate>()
        if (rawResponse.isBlank()) return candidates

        // Structured payload from the WebView bridge: [{"filename": "...", "content": "..."}, ...]
        val structured = parseStructuredPayload(rawResponse)
        if (structured != null) return structured

        val matcher = CODE_FENCE_REGEX.matcher(rawResponse)
        var foundAny = false

        while (matcher.find()) {
            foundAny = true
            val langTag = matcher.group(1)?.trim() ?: ""
            val inlinePath = matcher.group(2)?.trim() ?: ""
            val codeBody = matcher.group(3) ?: ""
            val fullMatch = matcher.group(0) ?: ""
            val isUnclosed = !fullMatch.trimEnd().endsWith("```")

            val candidate = analyzeBlock(rawResponse, matcher.start(), langTag, inlinePath, codeBody, isUnclosed)
            candidates.add(candidate)
        }

        // If no code fence matched, check if entire text is raw code (e.g. single HTML/JS file pasted)
        if (!foundAny && (rawResponse.contains("<!DOCTYPE") || rawResponse.contains("<html") || rawResponse.contains("function ") || rawResponse.contains("import "))) {
            val inferred = inferFromContentOnly(rawResponse)
            candidates.add(inferred)
        }

        return candidates
    }


    /**
     * Attempts to read the JSON array payload produced by the AI Studio bridge.
     * Returns null when the payload isn't that shape, so plain-text parsing can take over.
     */
    private fun parseStructuredPayload(rawResponse: String): List<ParsedCodeCandidate>? {
        val trimmed = rawResponse.trim()
        if (!trimmed.startsWith("[")) return null
        val array = try {
            JSONArray(trimmed)
        } catch (e: JSONException) {
            return null
        }
        if (array.length() == 0) return null

        val results = mutableListOf<ParsedCodeCandidate>()
        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: return null
            if (!obj.has("content")) return null
            val content = obj.optString("content", "")
            if (content.isBlank()) continue
            val filename = if (obj.isNull("filename")) "" else obj.optString("filename", "").trim()

            if (filename.isNotBlank() && isLikelyPath(filename)) {
                val path = sanitizePath(filename)
                val body = stripFences(content)
                results.add(
                    ParsedCodeCandidate(
                        path = path,
                        name = path.substringAfterLast('/'),
                        language = normalizeLang("", path, body),
                        content = body.trimEnd(),
                        confidence = 0.95,
                        ambiguity = false,
                        isIncomplete = isIncompleteBody(content, body),
                        sourceHeader = filename
                    )
                )
            } else {
                // No usable filename label: fall back to the existing fence/comment/content heuristics
                val inner = parseUnlabeledBlock(content)
                if (inner != null) results.add(inner)
            }
        }
        return if (results.isEmpty()) null else results
    }

    private fun parseUnlabeledBlock(content: String): ParsedCodeCandidate? {
        val matcher = CODE_FENCE_REGEX.matcher(content)
        if (matcher.find()) {
            val langTag = matcher.group(1)?.trim() ?: ""
            val inlinePath = matcher.group(2)?.trim() ?: ""
            val codeBody = matcher.group(3) ?: ""
            val fullMatch = matcher.group(0) ?: ""
            val isUnclosed = !fullMatch.trimEnd().endsWith("```")
            return analyzeBlock(content, matcher.start(), langTag, inlinePath, codeBody, isUnclosed)
        }
        return analyzeBlock(content, 0, "", "", content, false)
    }

    private fun stripFences(content: String): String {
        val matcher = CODE_FENCE_REGEX.matcher(content)
        return if (matcher.find()) (matcher.group(3) ?: content) else content
    }

    private fun isIncompleteBody(original: String, body: String): Boolean {
        val unclosed = original.contains("```") && original.trimEnd().let { !it.endsWith("```") }
        return unclosed || body.contains("/* TODO */") || body.trimEnd().endsWith("// ...") || body.trimEnd().endsWith("<!-- ... -->")
    }

    private fun analyzeBlock(
        fullText: String,
        fenceStart: Int,
        langTag: String,
        inlinePath: String,
        codeBody: String,
        isUnclosed: Boolean
    ): ParsedCodeCandidate {
        var path = ""
        var confidence = 0.5
        var sourceHeader = ""

        // 1. Check inline path on code fence: ```html index.html or ```js:src/app.js
        if (inlinePath.isNotBlank() && isLikelyPath(inlinePath)) {
            path = sanitizePath(inlinePath)
            confidence = 0.95
            sourceHeader = inlinePath
        }

        // 2. Check preceding lines before code fence for headers like "### index.html" or "**src/styles.css**"
        if (path.isBlank() && fenceStart > 0) {
            val precedingText = fullText.substring(maxOf(0, fenceStart - 150), fenceStart).trim()
            val lastLine = precedingText.lines().lastOrNull()?.trim() ?: ""
            val cleanedLine = lastLine.replace(Regex("^[#*`\\-\\s]+|[#*`\\-\\s:]+$"), "").trim()
            if (isLikelyPath(cleanedLine)) {
                path = sanitizePath(cleanedLine)
                confidence = 0.9
                sourceHeader = lastLine
            }
        }

        // 3. Check early lines of code body for comments: "// src/app.js", "<!-- index.html -->", "/* style.css */", "# main.py"
        if (path.isBlank()) {
            val candidateLines = codeBody.lines().map { it.trim() }.filter { it.isNotEmpty() }.take(5)
            for (line in candidateLines) {
                val commentPath = extractPathFromComment(line)
                if (commentPath != null && isLikelyPath(commentPath)) {
                    path = sanitizePath(commentPath)
                    confidence = 0.85
                    sourceHeader = line
                    break
                }
            }
        }

        // 4. Infer from language and content patterns
        val detectedLang = normalizeLang(langTag, path, codeBody)
        if (path.isBlank()) {
            val defaultPath = inferDefaultPath(detectedLang, codeBody)
            if (defaultPath != null) {
                path = defaultPath
                confidence = 0.7
            }
        }

        val ambiguity = path.isBlank()
        val name = if (path.isNotBlank()) path.substringAfterLast('/') else "untitled"

        // Check if content appears incomplete
        val isIncomplete = isUnclosed || codeBody.contains("/* TODO */") || codeBody.endsWith("// ...") || codeBody.endsWith("<!-- ... -->")

        return ParsedCodeCandidate(
            path = path,
            name = name,
            language = detectedLang,
            content = codeBody.trimEnd(),
            confidence = confidence,
            ambiguity = ambiguity,
            isIncomplete = isIncomplete,
            sourceHeader = sourceHeader
        )
    }

    private fun extractPathFromComment(line: String): String? {
        val trimmed = line.trim()
        val prefixRegex = Regex("""^(?://|/\*|<!--|#)\s*(?:filename|file|filepath|path)\s*[:=]?\s*([a-zA-Z0-9_./-]+\.[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
        val prefixMatch = prefixRegex.find(trimmed)
        if (prefixMatch != null) {
            return prefixMatch.groupValues[1]
        }
        val directRegex = Regex("""^(?://|/\*|<!--|#)\s*([a-zA-Z0-9_./-]+\.[a-zA-Z0-9]+)""", RegexOption.IGNORE_CASE)
        val directMatch = directRegex.find(trimmed)
        if (directMatch != null) {
            return directMatch.groupValues[1]
        }
        return null
    }

    private fun isLikelyPath(str: String): Boolean {
        if (str.length > 100 || str.contains(" ") || str.contains("\n")) return false
        val hasExt = str.contains(".") && !str.endsWith(".")
        val allowedChars = str.matches(Regex("^[a-zA-Z0-9_./-]+$"))
        return hasExt && allowedChars && !str.contains("..")
    }

    private fun sanitizePath(str: String): String {
        return str.replace('\\', '/')
            .replace(Regex("^/+"), "") // remove leading slash
            .replace(Regex("/+"), "/") // remove double slashes
    }

    private fun normalizeLang(langTag: String, path: String, content: String): String {
        val tag = langTag.lowercase().trim()
        if (tag.isNotBlank()) {
            return when (tag) {
                "html", "htm" -> "html"
                "css" -> "css"
                "javascript", "js" -> "javascript"
                "typescript", "ts" -> "typescript"
                "jsx" -> "jsx"
                "tsx" -> "tsx"
                "json" -> "json"
                "kotlin", "kt" -> "kotlin"
                "java" -> "java"
                "xml" -> "xml"
                "markdown", "md" -> "markdown"
                "yaml", "yml" -> "yaml"
                "python", "py" -> "python"
                "sql" -> "sql"
                else -> tag
            }
        }
        val ext = path.substringAfterLast('.', "").lowercase()
        if (ext.isNotBlank()) {
            return when (ext) {
                "html" -> "html"
                "css" -> "css"
                "js" -> "javascript"
                "ts" -> "typescript"
                "json" -> "json"
                "kt" -> "kotlin"
                "java" -> "java"
                "xml" -> "xml"
                else -> "text"
            }
        }
        if (content.contains("<!DOCTYPE") || content.contains("<html")) return "html"
        if (content.contains("{") && content.contains(":") && !content.contains("function")) return "json"
        return "text"
    }

    private fun inferDefaultPath(lang: String, content: String): String? {
        return when (lang) {
            "html" -> "index.html"
            "css" -> "styles.css"
            "javascript" -> if (content.contains("addEventListener")) "script.js" else "app.js"
            "typescript" -> "src/main.ts"
            "json" -> if (content.contains("\"dependencies\"")) "package.json" else "data.json"
            "kotlin" -> "MainActivity.kt"
            "xml" -> if (content.contains("<manifest")) "AndroidManifest.xml" else "res/values/strings.xml"
            else -> null
        }
    }

    private fun inferFromContentOnly(content: String): ParsedCodeCandidate {
        val path = if (content.contains("<!DOCTYPE") || content.contains("<html")) "index.html" else "script.js"
        val lang = if (path == "index.html") "html" else "javascript"
        return ParsedCodeCandidate(
            path = path,
            name = path,
            language = lang,
            content = content.trim(),
            confidence = 0.8,
            ambiguity = false,
            isIncomplete = false,
            sourceHeader = "Raw Document"
        )
    }
}
