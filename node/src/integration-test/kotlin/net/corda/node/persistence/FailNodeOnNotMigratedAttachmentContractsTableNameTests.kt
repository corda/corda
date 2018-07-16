package net.corda.node.persistence

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.internal.packageName
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.services.Permissions
import net.corda.testMessage.Message
import net.corda.testMessage.MessageState
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.RandomFree
import net.corda.testing.node.User
import org.junit.Test
import java.nio.file.Path
import java.sql.DriverManager
import kotlin.test.*

class FailNodeOnNotMigratedAttachmentContractsTableNameTests {
    @Test
    fun `node fails when detecting table name not migrated from version 3 dot 0`() {
        `node fails when not detecting compatible table name`("NODE_ATTACHMENTS_CONTRACTS", "NODE_ATTACHMENTS_CONTRACT_CLASS_NAME")
    }

    @Test
    fun `node fails when detecting table name not migrated from version 3 dot 1`() {
        `node fails when not detecting compatible table name`("NODE_ATTACHMENTS_CONTRACTS", "NODE_ATTCHMENTS_CONTRACTS")
    }

    fun `node fails when not detecting compatible table name`(tableNameFromMapping: String, tableNameInDB: String) {
        val user = User("mark", "dadada", setOf(Permissions.startFlow<SendMessageFlow>(), Permissions.invokeRpc("vaultQuery")))
        val message = Message("Hello world!")
        val baseDir: Path = driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(),
                portAllocation = RandomFree, extraCordappPackagesToScan = listOf(MessageState::class.packageName))) {
            val (nodeName, baseDir) = {
                val nodeHandle = startNode(rpcUsers = listOf(user)).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::SendMessageFlow, message, defaultNotaryIdentity).returnValue.getOrThrow()
                }
                nodeHandle.stop()
                Pair(nodeName, nodeHandle.baseDirectory)
            }()

            // replace the correct table name with one from the former release
            DriverManager.getConnection("jdbc:h2:file://$baseDir/persistence", "sa", "").use {
                it.createStatement().execute("ALTER TABLE $tableNameFromMapping RENAME TO $tableNameInDB")
                it.commit()
            }
            assertFailsWith(net.corda.nodeapi.internal.persistence.DatabaseIncompatibleException::class) {
                val nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user)).getOrThrow()
                nodeHandle.stop()
            }
             baseDir
        }

        // check that the node didn't recreated the correct table matching it's entity mapping
        val (hasTableFromMapping, hasTableFromDB) = DriverManager.getConnection("jdbc:h2:file://$baseDir/persistence", "sa", "").use {
            Pair(it.metaData.getTables(null, null, tableNameFromMapping, null).next(),
                    it.metaData.getTables(null, null, tableNameInDB, null).next())
        }
        assertFalse(hasTableFromMapping)
        assertTrue(hasTableFromDB)
    }
}