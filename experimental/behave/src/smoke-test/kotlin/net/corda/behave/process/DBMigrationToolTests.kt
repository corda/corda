package net.corda.behave.process

import net.corda.behave.file.currentDirectory
import net.corda.behave.file.div
import net.corda.behave.node.Distribution
import net.corda.core.utilities.minutes
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test

class DBMigrationToolTests {

    /**
     * Commands used to perform Database initialisation and migration as per:
     * http://docs.corda.r3.com/website/releases/docs_head/api-persistence.html#database-migration
     */
    private val nodeRunDir = currentDirectory / "build/runs/20180379-164247/PartyA"
    private val jdbcDriverSQLServer = nodeRunDir / "../libs/mssql-jdbc-6.2.2.jre8.jar"

    private val migrationToolMain = "com.r3.corda.dbmigration.DBMigration"

    @Test
    fun `dry run migration`() {
        println(nodeRunDir)
        val command = JarCommandWithMain(listOf(Distribution.R3_MASTER.dbMigrationJar, jdbcDriverSQLServer),
                migrationToolMain,
                arrayOf("--base-directory", "$nodeRunDir", "--dry-run"),
                nodeRunDir, 2.minutes)
        assertThat(command.run()).isEqualTo(0)
    }

    @Test
    fun `execute migration`() {
        println(nodeRunDir)
        val command = JarCommandWithMain(listOf(Distribution.R3_MASTER.dbMigrationJar, jdbcDriverSQLServer),
                migrationToolMain,
                arrayOf("--base-directory", "$nodeRunDir", "--execute-migration"),
                nodeRunDir, 2.minutes)
        assertThat(command.run()).isEqualTo(0)
    }
}