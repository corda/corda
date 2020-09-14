package net.corda.node.persistence

import net.corda.core.utilities.getOrThrow
import net.corda.node.flows.isQuasarAgentSpecified
import net.corda.nodeapi.internal.persistence.CouldNotCreateDataSourceException
import net.corda.nodeapi.internal.persistence.HibernateSchemaChangeException
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.driver
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.internal.startNode
import org.assertj.core.api.Assertions.assertThatExceptionOfType
import org.junit.Test
import kotlin.test.assertFailsWith

class DbSchemaInitialisationTest {
    @Test(timeout = 300_000)
    fun `database initialisation not allowed in config`() {
        driver(DriverParameters(startNodesInProcess = isQuasarAgentSpecified(), cordappsForAllNodes = emptyList())) {
            assertFailsWith(IllegalStateException::class) {
                startNode(NodeParameters(customOverrides = mapOf("database.initialiseSchema" to "false"))).getOrThrow()
            }
        }
    }

    @Test(timeout = 300_000)
    fun `app migration resource is only mandatory when not in dev mode`() {
        driver(DriverParameters(startNodesInProcess = true,
                cordappsForAllNodes = emptyList(),
                allowHibernateToManageAppSchema = false)) {
            // in dev mode, it fails because the schema of our test CorDapp is missing
            assertThatExceptionOfType(HibernateSchemaChangeException::class.java)
                    .isThrownBy {
                        startNode(NodeParameters(additionalCordapps = listOf(TestCordapp.findCordapp("net.corda.testing.missingmigrationcordapp")))).getOrThrow()
                    }
                    .withMessage("Incompatible schema change detected. Please run schema migration scripts (node with sub-command run-migration-scripts). Reason: Schema-validation: missing table [test_table]")

            // without devMode, it doesn't even get this far as it complains about the schema migration missing.
            assertThatExceptionOfType(CouldNotCreateDataSourceException::class.java)
                    .isThrownBy {
                        startNode(
                                ALICE_NAME,
                                false,
                                NodeParameters(additionalCordapps = listOf(TestCordapp.findCordapp("net.corda.testing.missingmigrationcordapp")))).getOrThrow()
                    }
                    .withMessage("Could not create the DataSource: No migration defined for schema: net.corda.testing.missingmigrationcordapp.MissingMigrationSchema v1")
        }
    }
}