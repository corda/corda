package net.corda.testing.node

import net.corda.core.internal.deleteIfExists
import net.corda.node.internal.DataSourceFactory
import net.corda.node.services.config.NodeConfiguration
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path

object H2DatabaseTools {
    private const val previousCordaVersion: String = "4.5.1"
    const val DATABASE_FILENAME: String = "persistence.mv.db"

    private fun getDatabaseSnapshotStream(): InputStream {
        val resourceUri = this::class.java.getResource("/databasesnapshots/${previousCordaVersion}/$DATABASE_FILENAME")
        return resourceUri.openStream()
    }

    fun databaseFilename(baseDirectory: Path) = baseDirectory.resolve(DATABASE_FILENAME)

    fun copyDatabaseSnapshot(baseDirectory: Path) {
        getDatabaseSnapshotStream().use { stream ->
            Files.createDirectories(baseDirectory)
            Files.copy(stream, databaseFilename(baseDirectory))
        }
    }

    fun deleteDatabase(config: NodeConfiguration) {
        databaseFilename(config.baseDirectory).deleteIfExists()
    }

    fun shutdownAndDeleteDatabase(config: NodeConfiguration) {
        DataSourceFactory.createDataSource(config.dataSourceProperties).also { dataSource ->
            dataSource.connection.use { connection ->
                connection.createStatement().use { statement ->
                    statement.execute("SHUTDOWN")
                }
            }
        }
        deleteDatabase(config)
    }
}