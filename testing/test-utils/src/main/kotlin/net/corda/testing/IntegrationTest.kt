package net.corda.testing

import net.corda.core.identity.CordaX500Name
import net.corda.core.identity.Party
import net.corda.testing.database.DbScriptRunner.runDbScript
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.rules.ExternalResource

/**
 * Base class for all integration tests that require common setup and/or teardown.
 * eg. serialization, database schema creation and data population / clean-up
 */
abstract class IntegrationTest {
    // System properties set in main 'corda-project' build.gradle
    // Note: the database provider configuration file for integration tests should specify:
    // dataSource.user = ${nodeOrganizationName}
    // dataSource.password = [PASSWORD]
    //    where [PASSWORD] must be the same for all ${nodeOrganizationName}
    companion object {
        private val DATABASE_PROVIDER = "databaseProvider"
        private val dbProvider = System.getProperty(DATABASE_PROVIDER, "")
        private val TEST_DB_SCRIPT_DIR = "test.db.script.dir"
        private val testDbScriptDir = System.getProperty(TEST_DB_SCRIPT_DIR, "database-scripts")
        var databaseSchemas = mutableListOf<String>()

        @BeforeClass
        @JvmStatic
        fun globalSetUp() {
            if (dbProvider.isNotEmpty()) {
                runDbScript(dbProvider,"$testDbScriptDir/db-global-setup.sql", databaseSchemas)
            }
        }
        @AfterClass
        @JvmStatic
        fun globalTearDown() {
            if (dbProvider.isNotEmpty()) {
                runDbScript(dbProvider,"$testDbScriptDir/db-global-cleanup.sql", databaseSchemas)
            }
        }
    }

    @Before
    @Throws(Exception::class)
    open fun setUp() {
        if (dbProvider.isNotEmpty()) {
            runDbScript(dbProvider,"$testDbScriptDir/db-setup.sql", databaseSchemas)
        }
    }

    @After
    open fun tearDown() {
        if (dbProvider.isNotEmpty()) {
           runDbScript(dbProvider,"$testDbScriptDir/db-cleanup.sql", databaseSchemas)
        }
    }
}

class IntegrationTestSchemas(vararg var list : String) : ExternalResource() {

    override fun before() {
        IntegrationTest.databaseSchemas.addAll(list)
    }
    override fun after() {
        IntegrationTest.databaseSchemas.clear()
    }
}

fun CordaX500Name.toDatabaseSchemaName() = this.organisation.replace(" ", "").replace("-", "_")

fun CordaX500Name.toDatabaseSchemaNames(vararg postfixes: String): List<String> {
    val nodeName = this.toDatabaseSchemaName()
    return postfixes.map { "$nodeName$it" }
}
