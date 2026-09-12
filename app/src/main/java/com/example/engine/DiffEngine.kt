package com.example.engine

data class DiffLine(
    val type: DiffLineType,
    val text: String,
    val oldLineNum: Int?,
    val newLineNum: Int?
)

enum class DiffLineType {
    ADDED,
    REMOVED,
    UNCHANGED
}

data class DiffResult(
    val path: String,
    val addedCount: Int,
    val removedCount: Int,
    val unchangedCount: Int,
    val lines: List<DiffLine>
)

object DiffEngine {

    fun computeDiff(before: String, after: String, path: String): DiffResult {
        val oldLines = if (before.isEmpty()) emptyList() else before.lines()
        val newLines = if (after.isEmpty()) emptyList() else after.lines()

        if (oldLines.isEmpty() && newLines.isEmpty()) {
            return DiffResult(path, 0, 0, 0, emptyList())
        }

        if (oldLines.isEmpty()) {
            val lines = newLines.mapIndexed { idx, line ->
                DiffLine(DiffLineType.ADDED, line, null, idx + 1)
            }
            return DiffResult(path, newLines.size, 0, 0, lines)
        }

        if (newLines.isEmpty()) {
            val lines = oldLines.mapIndexed { idx, line ->
                DiffLine(DiffLineType.REMOVED, line, idx + 1, null)
            }
            return DiffResult(path, 0, oldLines.size, 0, lines)
        }

        // Standard dynamic programming / Myers-like LCS for crisp diff
        val lcs = computeLcs(oldLines, newLines)
        val resultLines = mutableListOf<DiffLine>()
        var i = 0
        var j = 0
        var oldLineNum = 1
        var newLineNum = 1
        var addedCount = 0
        var removedCount = 0
        var unchangedCount = 0

        for (match in lcs) {
            // Lines deleted before match
            while (i < match.first) {
                resultLines.add(DiffLine(DiffLineType.REMOVED, oldLines[i], oldLineNum++, null))
                removedCount++
                i++
            }
            // Lines added before match
            while (j < match.second) {
                resultLines.add(DiffLine(DiffLineType.ADDED, newLines[j], null, newLineNum++))
                addedCount++
                j++
            }
            // Matching unchanged line
            resultLines.add(DiffLine(DiffLineType.UNCHANGED, oldLines[i], oldLineNum++, newLineNum++))
            unchangedCount++
            i++
            j++
        }

        // Remaining deletions
        while (i < oldLines.size) {
            resultLines.add(DiffLine(DiffLineType.REMOVED, oldLines[i], oldLineNum++, null))
            removedCount++
            i++
        }
        // Remaining additions
        while (j < newLines.size) {
            resultLines.add(DiffLine(DiffLineType.ADDED, newLines[j], null, newLineNum++))
            addedCount++
            j++
        }

        return DiffResult(path, addedCount, removedCount, unchangedCount, resultLines)
    }

    private fun computeLcs(a: List<String>, b: List<String>): List<Pair<Int, Int>> {
        val m = a.size
        val n = b.size
        // Cap comparison size to keep responsive on huge files (> 2000 lines)
        if (m > 2000 || n > 2000) {
            // Simplified line matching
            return emptyList()
        }

        val dp = Array(m + 1) { IntArray(n + 1) }
        for (i in 0 until m) {
            for (j in 0 until n) {
                if (a[i] == b[j]) {
                    dp[i + 1][j + 1] = dp[i][j] + 1
                } else {
                    dp[i + 1][j + 1] = maxOf(dp[i + 1][j], dp[i][j + 1])
                }
            }
        }

        val matches = mutableListOf<Pair<Int, Int>>()
        var i = m
        var j = n
        while (i > 0 && j > 0) {
            if (a[i - 1] == b[j - 1]) {
                matches.add(Pair(i - 1, j - 1))
                i--
                j--
            } else if (dp[i - 1][j] >= dp[i][j - 1]) {
                i--
            } else {
                j--
            }
        }
        matches.reverse()
        return matches
    }
}
