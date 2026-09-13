package com.example.engine

import com.example.data.db.FileEntity

data class ValidationCheck(
    val title: String,
    val isPassed: Boolean,
    val detail: String
)

data class ValidationResult(
    val status: String, // "ready", "warning", "error"
    val checks: List<ValidationCheck>,
    val warnings: List<String>,
    val errors: List<String>
)

object ValidationEngine {

    fun validateProject(files: List<FileEntity>): ValidationResult {
        val checks = mutableListOf<ValidationCheck>()
        val warnings = mutableListOf<String>()
        val errors = mutableListOf<String>()

        if (files.isEmpty()) {
            return ValidationResult(
                status = "ready",
                checks = listOf(ValidationCheck("Project Empty", true, "No files currently in project.")),
                warnings = listOf("Project has no files yet. Import or generate code to begin."),
                errors = emptyList()
            )
        }

        // 1. Check path validity & traversal safety
        var invalidPathsCount = 0
        for (f in files) {
            if (f.path.contains("..") || f.path.startsWith("/") || f.path.contains("\\") || f.path.contains("?")) {
                invalidPathsCount++
                errors.add("Unsafe path format detected in '${f.path}'.")
            }
        }
        checks.add(
            ValidationCheck(
                title = "Path Containment & Security",
                isPassed = invalidPathsCount == 0,
                detail = if (invalidPathsCount == 0) "All file paths pass relative directory containment rules."
                else "$invalidPathsCount file(s) contain invalid or escaping directory paths."
            )
        )

        // 2. Duplicate paths
        val duplicates = files.groupBy { it.path }.filter { it.value.size > 1 }
        val hasDuplicates = duplicates.isNotEmpty()
        if (hasDuplicates) {
            errors.add("Duplicate file paths detected: ${duplicates.keys.joinToString(", ")}")
        }
        checks.add(
            ValidationCheck(
                title = "Path Uniqueness",
                isPassed = !hasDuplicates,
                detail = if (!hasDuplicates) "No duplicate file paths found."
                else "Found ${duplicates.size} duplicate path entries."
            )
        )

        // 3. Empty files check
        val emptyFiles = files.filter { it.content.trim().isEmpty() }
        if (emptyFiles.isNotEmpty()) {
            warnings.add("${emptyFiles.size} empty file(s) found: ${emptyFiles.take(3).joinToString { it.name }}")
        }
        checks.add(
            ValidationCheck(
                title = "File Content Integrity",
                isPassed = emptyFiles.isEmpty(),
                detail = if (emptyFiles.isEmpty()) "All files contain valid content."
                else "${emptyFiles.size} file(s) are 0 bytes."
            )
        )

        // 4. Incomplete response / syntax markers
        val incompleteMarkers = listOf("/* TODO */", "// ...", "<!-- ... -->", "[Code omitted]")
        var incompleteCount = 0
        for (f in files) {
            for (marker in incompleteMarkers) {
                if (f.content.contains(marker)) {
                    incompleteCount++
                    warnings.add("Possible omitted or truncated snippet in '${f.name}' ($marker).")
                    break
                }
            }
        }
        checks.add(
            ValidationCheck(
                title = "AI Completeness Check",
                isPassed = incompleteCount == 0,
                detail = if (incompleteCount == 0) "No incomplete or truncated code patterns detected."
                else "$incompleteCount file(s) contain ellipsis or truncated markers."
            )
        )

        // 5. Reference consistency
        val repairProposals = ProjectRepairEngine.analyzeAndPropose(files)
        val missingRefsCount = repairProposals.size
        if (missingRefsCount > 0) {
            warnings.add("$missingRefsCount referenced file(s) are missing from the project.")
        }
        checks.add(
            ValidationCheck(
                title = "Reference & Import Consistency",
                isPassed = missingRefsCount == 0,
                detail = if (missingRefsCount == 0) "All local scripts, stylesheets, and imports exist."
                else "$missingRefsCount unresolved relative reference(s) detected. Run Repair to resolve."
            )
        )

        val status = when {
            errors.isNotEmpty() -> "error"
            warnings.isNotEmpty() -> "warning"
            else -> "ready"
        }

        return ValidationResult(
            status = status,
            checks = checks,
            warnings = warnings,
            errors = errors
        )
    }
}
