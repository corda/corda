package net.corda.node.services.persistence

import com.typesafe.config.ConfigFactory
import com.typesafe.config.ConfigValueFactory
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.sql.Connection
import java.sql.DriverManager
import java.sql.SQLException
import kotlin.test.assertEquals

/**
 * ENT-17353: with WRITE_DELAY=0 (Corda's default H2 URL) a commit must be on disk once it returns, so it has to survive the node
 * being killed or exiting without closing the database.
 *
 * H2 2.5.250 / 2.5.252 broke this for existing database files (https://github.com/h2database/h2database/issues/4446): when a new chunk
 * is written into re-used free space, the store header is rewritten pointing at the previous chunk instead of the new one, so recovery
 * after a crash cannot find any chunk written since. The node then restarts from older data (lost checkpoints, stale Liquibase lock).
 *
 * `SHUTDOWN IMMEDIATELY` closes the database files without writing anything else, which is what a `kill -9` leaves on disk.
 */
class H2CrashDurabilityTest {
    @Rule
    @JvmField
    val tempFolder = TemporaryFolder()

    private val url: String by lazy {
        ConfigFactory.empty()
                .withValue("baseDirectory", ConfigValueFactory.fromAnyRef(tempFolder.root.absolutePath))
                .withFallback(ConfigFactory.parseResources("corda-reference.conf"))
                .resolve()
                .getString("dataSourceProperties.dataSource.url")
    }

    private fun <T> connect(block: (Connection) -> T): T = DriverManager.getConnection(url, "sa", "").use(block)

    private fun Connection.execute(sql: String) = createStatement().use { it.execute(sql) }

    private fun Connection.count(table: String): Int = createStatement().use { statement ->
        statement.executeQuery("SELECT COUNT(*) FROM $table").use { it.next(); it.getInt(1) }
    }

    /** Commits [rows] rows to a new [table], then crashes the database. */
    private fun commitThenCrash(table: String, rows: Int) {
        connect { connection ->
            connection.execute("CREATE TABLE $table(ID INT PRIMARY KEY, V INT)")
            (0 until rows).forEach { connection.execute("INSERT INTO $table VALUES($it, 0)") }
            (0 until rows).forEach { connection.execute("UPDATE $table SET V = 1 WHERE ID = $it") }
            try {
                connection.execute("SHUTDOWN IMMEDIATELY")
            } catch (e: SQLException) {
                // Expected: the connection is closed under us.
            }
        }
    }

    @Test(timeout = 300_000)
    fun `default H2 url keeps committed data on an existing database file after a crash`() {
        // Every real node database is an existing, populated file. Populate one and close it cleanly; the deletes leave free space
        // behind that later chunks re-use.
        connect { connection ->
            connection.execute("CREATE TABLE FILLER(ID INT PRIMARY KEY, V VARCHAR(4000))")
            (0 until 2000).forEach { connection.execute("INSERT INTO FILLER VALUES($it, REPEAT('x', 2000))") }
            (0 until 2000 step 2).forEach { connection.execute("DELETE FROM FILLER WHERE ID = $it") }
        }

        // Several crash cycles: each recovery must see everything committed before every earlier crash.
        (1..3).forEach { cycle ->
            commitThenCrash("CRASH_$cycle", rows = 5)
            connect { connection ->
                (1..cycle).forEach {
                    val rows = try {
                        connection.count("CRASH_$it")
                    } catch (e: SQLException) {
                        throw AssertionError("Data committed before crash $it was lost after crash $cycle: ${e.message}", e)
                    }
                    assertEquals(5, rows, "rows committed before crash $it, read after crash $cycle")
                }
                assertEquals(1000, connection.count("FILLER"))
            }
        }
    }
}
