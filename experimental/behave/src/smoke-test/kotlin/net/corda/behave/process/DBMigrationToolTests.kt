package net.corda.behave.process

import net.corda.behave.node.Distribution
import net.corda.core.CordaRuntimeException
import net.corda.core.internal.div
import net.corda.core.utilities.minutes
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.nio.file.Paths

class DBMigrationToolTests {

    /**
     * Commands used to perform Database initialisation and migration as per:
     * http://docs.corda.r3.com/website/releases/docs_head/api-persistence.html#database-migration
     */

    // Set corresponding Java properties to point to valid Corda node configurations
    // eg. -DNODE_DIR=<location of node configuration directory> -DJDBC_DRIVER=postgresql-42.1.4.jar
    private val nodeRunDir = Paths.get(System.getProperty("NODE_DIR") ?: throw CordaRuntimeException("Please set NODE_DIR to point to valid Node configuration"))
    private val jdbcDriver = nodeRunDir / ".." / "libs" / (System.getProperty("JDBC_DRIVER") ?: throw CordaRuntimeException("Please set JDBC_DRIVER to point to valid JDBC driver jar file located under $nodeRunDir\\..\\libs"))

    private val migrationToolMain = "com.r3.corda.dbmigration.DBMigration"

    @Test
    fun `dry run migration`() {
        println(nodeRunDir)
        val command = JarCommandWithMain(listOf(Distribution.R3_MASTER.dbMigrationJar, jdbcDriver),
                migrationToolMain,
                arrayOf("--base-directory", "$nodeRunDir", "--dry-run"),
                nodeRunDir, 2.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    @Test
    fun `execute migration`() {
        println(nodeRunDir)
        val command = JarCommandWithMain(listOf(Distribution.R3_MASTER.dbMigrationJar, jdbcDriver),
                migrationToolMain,
                arrayOf("--base-directory", "$nodeRunDir", "--execute-migration"),
                nodeRunDir, 2.minutes)
        assertThat(command.run()).isEqualTo(0)
    }
}