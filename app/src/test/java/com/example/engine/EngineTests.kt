package com.example.engine

import com.example.data.db.FileEntity
import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayInputStream

class EngineTests {

    @Test
    fun testResponseParser_standardFences() {
        val aiResponse = """
            Here is the requested web component:
            
            ```html index.html
            <!DOCTYPE html>
            <html>
            <head><title>Test</title></head>
            <body><h1>Hello</h1></body>
            </html>
            ```
            
            And the style:
            
            ```css styles.css
            body { background: #fff; }
            ```
        """.trimIndent()

        val candidates = ResponseParser.parse(aiResponse)
        assertEquals(2, candidates.size)
        assertEquals("index.html", candidates[0].path)
        assertEquals("html", candidates[0].language)
        assertEquals("styles.css", candidates[1].path)
        assertEquals("css", candidates[1].language)
    }

    @Test
    fun testResponseParser_commentBasedPaths() {
        val aiResponse = """
            ```javascript
            // filename: src/utils/format.js
            export function format(val) { return val.trim(); }
            ```
        """.trimIndent()

        val candidates = ResponseParser.parse(aiResponse)
        assertEquals(1, candidates.size)
        assertEquals("src/utils/format.js", candidates[0].path)
        assertEquals("javascript", candidates[0].language)
    }

    @Test
    fun testFileOperationPlanner_createUpdateNoChange() {
        val existing = listOf(
            FileEntity(
                id = "1",
                projectId = "p1",
                path = "index.html",
                name = "index.html",
                extension = "html",
                language = "html",
                content = "<h1>Original</h1>",
                size = 17
            ),
            FileEntity(
                id = "2",
                projectId = "p1",
                path = "style.css",
                name = "style.css",
                extension = "css",
                language = "css",
                content = "body { margin: 0; }",
                size = 19
            )
        )

        val candidates = listOf(
            ParsedCodeCandidate(
                path = "index.html",
                name = "index.html",
                language = "html",
                content = "<h1>Original</h1>", // Identical -> NO_CHANGE
                confidence = 1.0,
                ambiguity = false,
                isIncomplete = false
            ),
            ParsedCodeCandidate(
                path = "style.css",
                name = "style.css",
                language = "css",
                content = "body { margin: 0; padding: 0; }", // Modified -> UPDATE
                confidence = 0.9,
                ambiguity = false,
                isIncomplete = false
            ),
            ParsedCodeCandidate(
                path = "script.js",
                name = "script.js",
                language = "javascript",
                content = "console.log('hi');", // New -> CREATE
                confidence = 0.95,
                ambiguity = false,
                isIncomplete = false
            ),
            ParsedCodeCandidate(
                path = "../escaped.js",
                name = "escaped.js",
                language = "javascript",
                content = "evil();", // Security violation -> AMBIGUOUS
                confidence = 0.5,
                ambiguity = false,
                isIncomplete = false
            )
        )

        val planned = FileOperationPlanner.planOperations(candidates, existing)
        assertEquals(4, planned.size)
        assertEquals("NO_CHANGE", planned[0].operation)
        assertEquals("UPDATE", planned[1].operation)
        assertEquals("CREATE", planned[2].operation)
        assertEquals("AMBIGUOUS", planned[3].operation)
    }

    @Test
    fun testDiffEngine_accurateLineTracking() {
        val before = "Line 1\nLine 2\nLine 3"
        val after = "Line 1\nLine 2 modified\nLine 3\nLine 4"

        val diff = DiffEngine.computeDiff(before, after, "sample.txt")
        assertTrue(diff.addedCount >= 1)
        assertTrue(diff.removedCount >= 1)
        assertEquals("sample.txt", diff.path)
    }

    @Test
    fun testProjectRepairEngine_detectsMissingFiles() {
        val files = listOf(
            FileEntity(
                id = "1",
                projectId = "p1",
                path = "index.html",
                name = "index.html",
                extension = "html",
                language = "html",
                content = """
                    <!DOCTYPE html>
                    <html>
                    <head>
                        <link rel="stylesheet" href="missing.css">
                        <script src="js/missing_script.js"></script>
                    </head>
                    <body></body>
                    </html>
                """.trimIndent(),
                size = 150
            )
        )

        val proposals = ProjectRepairEngine.analyzeAndPropose(files)
        assertEquals(2, proposals.size)
        val paths = proposals.map { it.targetPath }
        assertTrue(paths.contains("missing.css"))
        assertTrue(paths.contains("js/missing_script.js"))
    }

    @Test
    fun testValidationEngine_detectsIntegrityIssues() {
        val emptyFile = FileEntity(
            id = "1",
            projectId = "p1",
            path = "empty.txt",
            name = "empty.txt",
            extension = "txt",
            language = "text",
            content = "",
            size = 0
        )
        val incompleteFile = FileEntity(
            id = "2",
            projectId = "p1",
            path = "code.js",
            name = "code.js",
            extension = "js",
            language = "javascript",
            content = "function test() {\n  // ...\n}",
            size = 25
        )

        val result = ValidationEngine.validateProject(listOf(emptyFile, incompleteFile))
        assertTrue(result.warnings.isNotEmpty())
        assertTrue(result.warnings.any { it.contains("empty") })
        assertTrue(result.warnings.any { it.contains("ellipsis") || it.contains("truncated") || it.contains("omitted") })
    }

    @Test
    fun testZipManager_roundTripArchive() {
        val files = listOf(
            FileEntity("1", "p1", "index.html", "index.html", "html", "html", "<h1>Test</h1>", 13),
            FileEntity("2", "p1", "css/style.css", "style.css", "css", "css", "body{}", 6)
        )

        val zipBytes = ZipManager.createZipArchive(files)
        assertTrue(zipBytes.isNotEmpty())

        val parsed = ZipManager.parseZipArchive(ByteArrayInputStream(zipBytes))
        assertEquals(2, parsed.size)
        assertEquals("index.html", parsed[0].path)
        assertEquals("<h1>Test</h1>", parsed[0].content)
        assertEquals("css/style.css", parsed[1].path)
        assertEquals("body{}", parsed[1].content)
    }
}
