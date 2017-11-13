package net.corda.testing

import net.corda.testing.database.DbScriptRunner.runDbScript
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass

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

        @BeforeClass
        @JvmStatic
        fun globalSetUp() {
            if (dbProvider.isNotEmpty()) {
                runDbScript(dbProvider,"$testDbScriptDir/db-global-setup-${this::class.simpleName}.sql")
            }
        }
        @AfterClass
        @JvmStatic
        fun globalTearDown() {
            if (dbProvider.isNotEmpty()) {
                runDbScript(dbProvider,"$testDbScriptDir/db-global-cleanup-${this::class.simpleName}.sql")
            }
        }
    }

    @Before
    @Throws(Exception::class)
    open fun setUp() {
        if (dbProvider.isNotEmpty()) {
            runDbScript(dbProvider,"$testDbScriptDir/db-setup-${this::class.simpleName}.sql")
        }
    }

    @After
    open fun tearDown() {
        if (dbProvider.isNotEmpty()) {
            runDbScript(dbProvider,"$testDbScriptDir/db-cleanup-${this::class.simpleName}.sql")
        }
    }

}