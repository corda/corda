package net.corda.testing.node

import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.util.*

object DatabaseSnapshot {
    private const val previousCordaVersion: String = "4.5.1"
    private const val DATABASE_POSTFIX: String = ".mv.db"
    private const val DATABASE_FILENAME: String = "persistence$DATABASE_POSTFIX"

    private fun getDatabaseSnapshotStream(): InputStream {
        val fileName = "/databasesnapshots/$previousCordaVersion/$DATABASE_FILENAME"
        return this::class.java.getResource(fileName).openStream()
    }

    fun copyDatabaseSnapshot(baseDirectory: Path): Path {
        val stream = getDatabaseSnapshotStream()
        val databaseFilePrefix = UUID.randomUUID().toString()
        Files.createDirectories(baseDirectory)
        val path = baseDirectory.resolve("$databaseFilePrefix.mv.db")
        Files.copy(stream, path)
        return path
    }
}