package com.example.engine

import com.example.data.db.FileEntity
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

data class ImportedFileItem(
    val path: String,
    val name: String,
    val content: String,
    val size: Long
)

object ZipManager {

    fun createZipArchive(files: List<FileEntity>): ByteArray {
        val bos = ByteArrayOutputStream()
        ZipOutputStream(bos).use { zos ->
            for (file in files) {
                val cleanPath = file.path.trimStart('/')
                val entry = ZipEntry(cleanPath)
                zos.putNextEntry(entry)
                val bytes = file.content.toByteArray(Charsets.UTF_8)
                zos.write(bytes)
                zos.closeEntry()
            }
        }
        return bos.toByteArray()
    }

    fun parseZipArchive(inputStream: InputStream): List<ImportedFileItem> {
        val result = mutableListOf<ImportedFileItem>()
        ZipInputStream(inputStream).use { zis ->
            var entry: ZipEntry? = zis.nextEntry
            while (entry != null) {
                if (!entry.isDirectory) {
                    val rawName = entry.name.replace('\\', '/')
                    // Security check: Zip Slip vulnerability defense
                    if (!rawName.contains("..") && !rawName.startsWith("/")) {
                        val sanitizedPath = rawName.replace(Regex("^/+"), "")
                        val content = zis.readBytes().toString(Charsets.UTF_8)
                        val fileName = sanitizedPath.substringAfterLast('/')
                        result.add(
                            ImportedFileItem(
                                path = sanitizedPath,
                                name = fileName,
                                content = content,
                                size = content.toByteArray().size.toLong()
                            )
                        )
                    }
                }
                zis.closeEntry()
                entry = zis.nextEntry
            }
        }
        return result
    }
}
