package net.corda.contracts

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.contracts.HashAttachmentConstraint
import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.contracts.StateAndRef
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.flows.isQuasarAgentSpecified
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.internal.internalDriver
import org.junit.Assume.assumeFalse
import org.junit.Ignore
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

open class SignatureConstraintMigrationFromHashConstraintsTests : SignatureConstraintVersioningTests() {

    @Test(timeout=300_000)
	fun `can evolve from lower contract class version to higher one`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.

        val stateAndRef: StateAndRef<MessageState>? = internalDriver(
                inMemoryDB = false,
                startNodesInProcess = isQuasarAgentSpecified(),
                networkParameters = testNetworkParameters(notaries = emptyList(), minimumPlatformVersion = 4)
        ) {
            val nodeName = {
                val nodeHandle = startNode(NodeParameters(rpcUsers = listOf(user), additionalCordapps = listOf(oldCordapp))).getOrThrow()
                val nodeName = nodeHandle.nodeInfo.singleIdentity().name
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::CreateMessage, message, defaultNotaryIdentity).returnValue.getOrThrow()
                }
                nodeHandle.stop()
                nodeName
            }()
            val result = {
                (baseDirectory(nodeName) / "cordapps").deleteRecursively()
                val nodeHandle = startNode(
                        NodeParameters(
                                providedName = nodeName,
                                rpcUsers = listOf(user),
                                additionalCordapps = listOf(newCordapp)
                        )
                ).getOrThrow()
                var result: StateAndRef<MessageState>? = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    val page = it.proxy.vaultQuery(MessageState::class.java)
                    page.states.singleOrNull()
                }
                CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    it.proxy.startFlow(::ConsumeMessage, result!!, defaultNotaryIdentity, false, false).returnValue.getOrThrow()
                }
                result = CordaRPCClient(nodeHandle.rpcAddress).start(user.username, user.password).use {
                    val page = it.proxy.vaultQuery(MessageState::class.java)
                    page.states.singleOrNull()
                }
                nodeHandle.stop()
                result
            }()
            result
        }
        assertNotNull(stateAndRef)
        assertEquals(transformedMessage, stateAndRef!!.state.data.message)
    }

    @Test(timeout=300_000)
	fun `auto migration from HashConstraint to SignatureConstraint`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
                startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is SignatureAttachmentConstraint)
    }

    @Test(timeout=300_000)
	fun `HashConstraint cannot be migrated if 'disableHashConstraints' system property is not set to true`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = emptyMap(),
                startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is HashAttachmentConstraint)
    }

    @Test(timeout=300_000)
	fun `HashConstraint cannot be migrated to SignatureConstraint if new jar is not signed`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newUnsignedCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
                startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is HashAttachmentConstraint)
    }

    @Test(timeout=300_000)
	fun `HashConstraint cannot be migrated to SignatureConstraint if platform version is not 4 or greater`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
                startNodesInProcess = false,
                minimumPlatformVersion = 3
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is HashAttachmentConstraint)
    }

    @Ignore("ENT-5676: Disabling to isolate Gradle process death cause")
    @Test(timeout=300_000)
	fun `HashConstraint cannot be migrated to SignatureConstraint if a HashConstraint is specified for one state and another uses an AutomaticPlaceholderConstraint`() {
        assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = emptyMap(),
                systemProperties = mapOf("net.corda.node.disableHashConstraints" to true.toString()),
                startNodesInProcess = false,
                specifyExistingConstraint = true,
                addAnotherAutomaticConstraintState = true
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is HashAttachmentConstraint)
        assertEquals(2, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs[0].constraint is HashAttachmentConstraint)
        assertTrue(consumingTransaction.outputs[1].constraint is HashAttachmentConstraint)
        assertEquals(
                issuanceTransaction.outputs.single().constraint,
                consumingTransaction.outputs.first().constraint,
                "The constraint from the issuance transaction should be the same constraint used in the consuming transaction"
        )

        assertEquals(
                consumingTransaction.outputs[0].constraint,
                consumingTransaction.outputs[1].constraint,
                "The AutomaticPlaceholderConstraint of the second state should become the same HashConstraint used in other state"
        )
    }
}