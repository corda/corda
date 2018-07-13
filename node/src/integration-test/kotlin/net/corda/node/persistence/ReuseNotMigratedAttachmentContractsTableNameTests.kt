package net.corda.node.persistence

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.StateAndRef
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
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

class ReuseNotMigratedAttachmentContractsTableNameTests {
    @Test
    fun `test node is backward campatible with table name in version 3 dot 0`() {
        `node uses exisitng table name different from mapping`("NODE_ATTACHMENTS_CONTRACTS", "NODE_ATTACHMENTS_CONTRACT_CLASS_NAME")
    }

    @Test
    fun `test node is backward campatible with table name in version 3 dot 1`() {
        `node uses exisitng table name different from mapping`("NODE_ATTACHMENTS_CONTRACTS", "NODE_ATTCHMENTS_CONTRACTS")
    }

    fun `node uses exisitng table name different from mapping`(tableNameFromMapping: String, tableNameInDB: String) {
        val user = User("mark", "dadada", setOf(Permissions.startFlow<SendMessageFlow>(), Permissions.invokeRpc("vaultQuery")))
        val message = Message("Hello world!")
        val (stateAndRef: StateAndRef<MessageState>?, baseDir: Path) = driver(DriverParameters(inMemoryDB = false, startNodesInProcess = isQuasarAgentSpecified(),
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
            val nodeHandle = startNode(providedName = nodeName, rpcUsers = listOf(user)).getOrThrow()
            val result = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                val page = it.proxy.vaultQuery(MessageState::class.java)
                page.states.singleOrNull()
            }
            nodeHandle.stop()
            Pair(result, baseDir)
        }
        assertNotNull(stateAndRef)
        val retrievedMessage = stateAndRef!!.state.data.message
        assertEquals(message, retrievedMessage)

        // check that the node didn't recreated the correct table matching it's entity mapping and continou using table name from the former release
        val (hasTableFromMapping, hasTableFromDB) = DriverManager.getConnection("jdbc:h2:file://$baseDir/persistence", "sa", "").use {
            Pair(it.metaData.getTables(null, null, tableNameFromMapping, null).next(),
                    it.metaData.getTables(null, null, tableNameInDB, null).next())
        }
        assertFalse(hasTableFromMapping)
        assertTrue(hasTableFromDB)
    }
}