/*
 * R3 Proprietary and Confidential
 *
 * Copyright (c) 2018 R3 Limited.  All rights reserved.
 *
 * The intellectual and technical concepts contained herein are proprietary to R3 and its suppliers and are protected by trade secret law.
 *
 * Distribution of this file or any portion thereof via any medium without the express permission of R3 is strictly prohibited.
 */

package net.corda.testing.internal

import net.corda.core.identity.CordaX500Name
import net.corda.testing.database.DbScriptRunner.runDbScript
import org.junit.After
import org.junit.AfterClass
import org.junit.Before
import org.junit.BeforeClass
import org.junit.rules.ExternalResource

sealed class DatabaseRule(val databaseSchemas: List<String>, val dbScriptPrefix: String) : ExternalResource() {

    private val DATABASE_PROVIDER = "custom.databaseProvider"
    private val dbProvider = System.getProperty(DATABASE_PROVIDER, "")
    private val TEST_DB_SCRIPT_DIR = "test.db.script.dir"
    private val testDbScriptDir = System.getProperty(TEST_DB_SCRIPT_DIR, "database-scripts")

    public override fun before() {
        if (dbProvider.isNotEmpty()) {
            runDbScript(dbProvider,"$testDbScriptDir/$dbScriptPrefix-cleanup.sql", databaseSchemas)
            runDbScript(dbProvider,"$testDbScriptDir/$dbScriptPrefix-setup.sql", databaseSchemas)
        }
    }
    public override fun after() {
        if (dbProvider.isNotEmpty()) {
            runDbScript(dbProvider,"$testDbScriptDir/$dbScriptPrefix-cleanup.sql", databaseSchemas)
        }
    }
}

class GlobalDatabaseRule(databaseSchemas: List<String> = emptyList()) : DatabaseRule(databaseSchemas, "db-global")
class LocalDatabaseRule(databaseSchemas: List<String> = emptyList()) : DatabaseRule(databaseSchemas, "db")

/**
 * Base class for all integration tests that require common setup and/or teardown.
 * eg. serialization, database schema creation and data population / clean-up
 */
abstract class IntegrationTest {
    // System properties set in main 'corda-project' build.gradle
    // Note: the database provider configuration file for integration tests should specify:
    // dataSource.user = ${custom.nodeOrganizationName}
    // dataSource.password = [PASSWORD]
    //    where [PASSWORD] must be the same for all ${custom.nodeOrganizationName}
    companion object {
        private val DATABASE_PROVIDER = "custom.databaseProvider"
        private val dbProvider = System.getProperty(DATABASE_PROVIDER, "")
        private val TEST_DB_SCRIPT_DIR = "test.db.script.dir"
        private val testDbScriptDir = System.getProperty(TEST_DB_SCRIPT_DIR, "database-scripts")
        var databaseSchemas = mutableListOf<String>()

        @BeforeClass
        @JvmStatic
        fun globalSetUp() {
            if (isRemoteDatabaseMode()) {
                runDbScript(dbProvider,"$testDbScriptDir/db-global-cleanup.sql", databaseSchemas)
                runDbScript(dbProvider,"$testDbScriptDir/db-global-setup.sql", databaseSchemas)
            }
        }
        @AfterClass
        @JvmStatic
        fun globalTearDown() {
            if (isRemoteDatabaseMode()) {
                runDbScript(dbProvider,"$testDbScriptDir/db-global-cleanup.sql", databaseSchemas)
            }
        }

        fun isRemoteDatabaseMode() = dbProvider.isNotEmpty()
    }

    @Before
    @Throws(Exception::class)
    open fun setUp() {
        if (isRemoteDatabaseMode()) {
            runDbScript(dbProvider,"$testDbScriptDir/db-setup.sql", databaseSchemas)
        }
    }

    @After
    open fun tearDown() {
        if (isRemoteDatabaseMode()) {
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
