package net.corda.testing.node

import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

object DatabaseSnapshot {
    private val previousCordaVersion: String = "4.5.1"

    private fun getDatabaseSnapshotStream(): InputStream {
        var fileName = "/databasesnapshots/${previousCordaVersion}/persistence.mv.db"
        println("Copying from resource $fileName")
        val resourceUri = this::class.java.getResource(fileName)
        var stream: InputStream? = null
        try {
            stream = resourceUri.openStream()
        } catch (e: IOException) {
            println("IOException occurred getting resource: ${e.message}")
            println(e.toString())
            e.printStackTrace()
        }
        return stream!!
    }

    fun copyDatabaseSnapshot(baseDirectory: Path) {
        val stream = getDatabaseSnapshotStream()
        if (!Files.exists(baseDirectory)) {
            Files.createDirectories(baseDirectory)
        }
        val path = baseDirectory.resolve("persistence.mv.db")
        println("Copying stream to path: $path")
        try {
            Files.copy(stream, path)
        } catch (e: IOException) {
            println("IOException occurred copying snapshot: ${e.message}")
            println(e.toString())
            e.printStackTrace()
        }
    }
}