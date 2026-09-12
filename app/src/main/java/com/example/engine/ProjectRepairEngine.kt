package com.example.engine

import com.example.data.db.FileEntity
import java.util.regex.Pattern

data class RepairProposal(
    val id: String,
    val targetPath: String,
    val sourceFile: String,
    val problem: String,
    val proposedAction: String, // CREATE_STUB, FIX_REFERENCE, CLEANUP
    val proposedContent: String,
    val reason: String
)

object ProjectRepairEngine {

    fun analyzeAndPropose(files: List<FileEntity>): List<RepairProposal> {
        val proposals = mutableListOf<RepairProposal>()
        val existingPaths = files.map { it.path }.toSet()

        for (file in files) {
            when (file.language) {
                "html" -> analyzeHtml(file, existingPaths, proposals)
                "css" -> analyzeCss(file, existingPaths, proposals)
                "javascript", "typescript", "jsx", "tsx" -> analyzeJsTs(file, existingPaths, proposals)
            }
        }

        return proposals
    }

    private fun analyzeHtml(
        file: FileEntity,
        existingPaths: Set<String>,
        proposals: MutableList<RepairProposal>
    ) {
        val content = file.content
        // Check script src
        val scriptMatcher = Pattern.compile("<script[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(content)
        while (scriptMatcher.find()) {
            val src = scriptMatcher.group(1) ?: continue
            checkReference(file.path, src, "script", existingPaths, proposals)
        }

        // Check link href (stylesheets)
        val linkMatcher = Pattern.compile("<link[^>]+href=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(content)
        while (linkMatcher.find()) {
            val href = linkMatcher.group(1) ?: continue
            if (href.endsWith(".css") || href.contains("stylesheet")) {
                checkReference(file.path, href, "stylesheet", existingPaths, proposals)
            }
        }

        // Check img src
        val imgMatcher = Pattern.compile("<img[^>]+src=[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(content)
        while (imgMatcher.find()) {
            val src = imgMatcher.group(1) ?: continue
            checkReference(file.path, src, "image", existingPaths, proposals)
        }
    }

    private fun analyzeCss(
        file: FileEntity,
        existingPaths: Set<String>,
        proposals: MutableList<RepairProposal>
    ) {
        val content = file.content
        val importMatcher = Pattern.compile("@import\\s+[\"']([^\"']+)[\"']", Pattern.CASE_INSENSITIVE).matcher(content)
        while (importMatcher.find()) {
            val ref = importMatcher.group(1) ?: continue
            checkReference(file.path, ref, "css import", existingPaths, proposals)
        }
    }

    private fun analyzeJsTs(
        file: FileEntity,
        existingPaths: Set<String>,
        proposals: MutableList<RepairProposal>
    ) {
        val content = file.content
        // import ... from './something'
        val importMatcher = Pattern.compile("(?:import|from)\\s+[\"'](\\.[^\"']+)[\"']").matcher(content)
        while (importMatcher.find()) {
            val ref = importMatcher.group(1) ?: continue
            checkJsReference(file.path, ref, existingPaths, proposals)
        }
        // require('./something')
        val requireMatcher = Pattern.compile("require\\([\"'](\\.[^\"']+)[\"']\\)").matcher(content)
        while (requireMatcher.find()) {
            val ref = requireMatcher.group(1) ?: continue
            checkJsReference(file.path, ref, existingPaths, proposals)
        }
    }

    private fun checkReference(
        sourcePath: String,
        ref: String,
        type: String,
        existingPaths: Set<String>,
        proposals: MutableList<RepairProposal>
    ) {
        // Skip external URLs and inline data
        if (ref.startsWith("http://") || ref.startsWith("https://") || ref.startsWith("//") || ref.startsWith("data:")) {
            return
        }

        val resolved = resolveRelativePath(sourcePath, ref)
        if (!existingPaths.contains(resolved) && !existingPaths.contains(ref.trimStart('/'))) {
            val targetPath = resolved
            val stubContent = generateStubContent(targetPath, type)
            proposals.add(
                RepairProposal(
                    id = "repair_${targetPath.hashCode()}",
                    targetPath = targetPath,
                    sourceFile = sourcePath,
                    problem = "$sourcePath references missing $type '$ref'",
                    proposedAction = "CREATE_STUB",
                    proposedContent = stubContent,
                    reason = "Repaired because $sourcePath references missing $type file at $targetPath."
                )
            )
        }
    }

    private fun checkJsReference(
        sourcePath: String,
        ref: String,
        existingPaths: Set<String>,
        proposals: MutableList<RepairProposal>
    ) {
        val resolvedBase = resolveRelativePath(sourcePath, ref)
        val possibleExtensions = listOf("", ".js", ".ts", ".jsx", ".tsx", "/index.js", "/index.ts")
        val exists = possibleExtensions.any { existingPaths.contains(resolvedBase + it) }
        if (!exists) {
            val targetPath = if (resolvedBase.contains('.')) resolvedBase else "$resolvedBase.js"
            proposals.add(
                RepairProposal(
                    id = "repair_${targetPath.hashCode()}",
                    targetPath = targetPath,
                    sourceFile = sourcePath,
                    problem = "$sourcePath imports missing module '$ref'",
                    proposedAction = "CREATE_STUB",
                    proposedContent = "// Module stub for $ref\nexport default {};\n",
                    reason = "Repaired because $sourcePath imports non-existent module '$ref'."
                )
            )
        }
    }

    fun resolveRelativePath(basePath: String, targetRef: String): String {
        val cleanRef = targetRef.replace('\\', '/')
        if (!cleanRef.startsWith(".")) {
            return cleanRef.trimStart('/')
        }

        val baseDir = if (basePath.contains('/')) basePath.substringBeforeLast('/') else ""
        val parts = (if (baseDir.isBlank()) mutableListOf() else baseDir.split('/').toMutableList())

        val refParts = cleanRef.split('/')
        for (part in refParts) {
            when (part) {
                "", "." -> { /* stay */ }
                ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.size - 1)
                else -> parts.add(part)
            }
        }
        return parts.joinToString("/")
    }

    private fun generateStubContent(path: String, type: String): String {
        val ext = path.substringAfterLast('.', "")
        return when (ext) {
            "css" -> "/* Auto-generated stylesheet stub for $path */\n* {\n  box-sizing: border-box;\n}\n"
            "js" -> "// Auto-generated script stub for $path\nconsole.log('$path loaded');\n"
            "json" -> "{\n  \"name\": \"${path.substringAfterLast('/')}\"\n}\n"
            "svg" -> "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"24\" height=\"24\" viewBox=\"0 0 24 24\" fill=\"none\" stroke=\"currentColor\" stroke-width=\"2\"><rect width=\"18\" height=\"18\" x=\"3\" y=\"3\" rx=\"2\"/></svg>"
            else -> "/* Auto-generated stub for $path */\n"
        }
    }
}
