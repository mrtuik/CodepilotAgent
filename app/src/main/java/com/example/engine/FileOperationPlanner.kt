package com.example.engine

import com.example.data.db.FileEntity
import com.example.data.repository.PlannedOperationData

object FileOperationPlanner {

    fun planOperations(
        candidates: List<ParsedCodeCandidate>,
        existingFiles: List<FileEntity>
    ): List<PlannedOperationData> {
        val planned = mutableListOf<PlannedOperationData>()
        val existingByPath = existingFiles.associateBy { it.path }

        // Track paths to detect collisions within same response
        val seenPaths = mutableSetOf<String>()

        for (candidate in candidates) {
            if (candidate.ambiguity || candidate.path.isBlank()) {
                planned.add(
                    PlannedOperationData(
                        operation = "AMBIGUOUS",
                        path = candidate.path,
                        content = candidate.content,
                        reason = "Path could not be safely inferred from code comments or headers.",
                        confidence = candidate.confidence,
                        ambiguityReason = "No explicit filename found. Please specify target path."
                    )
                )
                continue
            }

            val path = candidate.path

            // Security check: reject .. traversal
            if (path.contains("..") || path.startsWith("/") || path.contains("\\")) {
                planned.add(
                    PlannedOperationData(
                        operation = "AMBIGUOUS",
                        path = path,
                        content = candidate.content,
                        reason = "Security rejection: Path contains directory traversal or invalid format.",
                        confidence = 0.0,
                        ambiguityReason = "Path '$path' violates project containment rules."
                    )
                )
                continue
            }

            if (seenPaths.contains(path)) {
                planned.add(
                    PlannedOperationData(
                        operation = "AMBIGUOUS",
                        path = path,
                        content = candidate.content,
                        reason = "Multiple code blocks in AI response target the same path.",
                        confidence = 0.5,
                        ambiguityReason = "Duplicate block detected for $path."
                    )
                )
                continue
            }
            seenPaths.add(path)

            val existing = existingByPath[path]
            if (existing == null) {
                val reason = if (candidate.sourceHeader.isNotBlank()) {
                    "Created because AI Studio response specified new file ($path)."
                } else {
                    "Created new ${candidate.language.uppercase()} component for project."
                }
                planned.add(
                    PlannedOperationData(
                        operation = "CREATE",
                        path = path,
                        content = candidate.content,
                        reason = reason,
                        confidence = candidate.confidence
                    )
                )
            } else {
                if (existing.content.trim() == candidate.content.trim()) {
                    planned.add(
                        PlannedOperationData(
                            operation = "NO_CHANGE",
                            path = path,
                            content = candidate.content,
                            reason = "Generated code matches existing file identically.",
                            confidence = 1.0
                        )
                    )
                } else {
                    val changeSummary = generateChangeSummary(existing.content, candidate.content, path)
                    planned.add(
                        PlannedOperationData(
                            operation = "UPDATE",
                            path = path,
                            content = candidate.content,
                            reason = changeSummary,
                            confidence = candidate.confidence
                        )
                    )
                }
            }
        }

        return planned
    }

    private fun generateChangeSummary(before: String, after: String, path: String): String {
        val beforeLines = before.lines().size
        val afterLines = after.lines().size
        val diff = afterLines - beforeLines
        return when {
            diff > 0 -> "Updated $path with $diff additional line(s) from AI Studio response."
            diff < 0 -> "Updated $path with streamlined implementation (${-diff} line(s) reduced)."
            else -> "Updated $path with modified implementation from AI Studio."
        }
    }
}
