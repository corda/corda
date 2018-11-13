package net.corda.healthsurvey.output

import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class Report(val path: Path) {

    class ReportFile(val fileName: String) {

        private var byteArray: ByteArray? = null

        private val stringContent = StringBuilder()

        val bytes: ByteArray
            get() = byteArray ?: stringContent.toString().toByteArray()

        fun withContent(content: String) = this.apply {
            stringContent.append(content)
        }

        fun asCopyOfFile(path: Path? = null) = this.apply {
            byteArray = Files.readAllBytes(path ?: Paths.get(fileName))
        }

    }

    private val files = mutableListOf<ReportFile>()

    fun addFile(fileName: String) = ReportFile(fileName).apply { files.add(this) }

    fun export() = ZipOutputStream(FileOutputStream(path.toFile())).use { zip ->
        for (file in files) {
            val e = ZipEntry(file.fileName)
            zip.putNextEntry(e)
            val bytes = file.bytes
            zip.write(bytes, 0, bytes.size)
            zip.closeEntry()
        }
    }

}