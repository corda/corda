package net.corda.testing.db

import org.assertj.core.api.Assertions.assertThat
import org.slf4j.Logger
import org.slf4j.LoggerFactory
import java.lang.IllegalStateException

class AssertingTestDatabaseContext : TestDatabaseContext {

    companion object {
        private val logger: Logger = LoggerFactory.getLogger(AssertingTestDatabaseContext::class.java)
        private val expectations = mutableMapOf<String, List<String>>()

        fun addExpectations(groupName: String, vararg scripts: String) {
            expectations.compute(groupName) { _, expected ->
                (expected ?: emptyList()) + scripts.toList()
            }
        }
    }

    private lateinit var groupName: String
    private val scriptsRun = mutableListOf<String>()

    override fun initialize(groupName: String) {
        this.groupName = groupName
        scriptsRun += "${groupName}-db-setup.sql"
    }

    override fun beforeClass(setupSql: String) {
        scriptsRun += "$groupName-$setupSql-setup.sql"
    }

    override fun afterClass(teardownSql: String) {
        scriptsRun += "$groupName-$teardownSql-teardown.sql"
    }

    override fun beforeTest(setupSql: String) {
        scriptsRun += "$groupName-$setupSql-setup.sql"
    }

    override fun afterTest(teardownSql: String) {
        scriptsRun += "$groupName-$teardownSql-teardown.sql"
    }

    override fun close() {
        scriptsRun += "${groupName}-db-teardown.sql"

        logger.info("SQL scripts run for group $groupName:\n" + scriptsRun.joinToString("\n"))

        val expectedScripts = (listOf("db-setup") + (expectations[groupName] ?: emptyList()) + listOf("db-teardown"))
                .map { "$groupName-$it.sql" }
                .toTypedArray()

        try {
            assertThat(scriptsRun).containsExactlyInAnyOrder(*expectedScripts)
        } catch (e: AssertionError) {
            throw IllegalStateException("Assertion failed: ${e.message}")
        }
    }

}