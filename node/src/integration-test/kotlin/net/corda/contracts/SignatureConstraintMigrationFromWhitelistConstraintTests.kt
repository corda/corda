package net.corda.contracts

import net.corda.client.rpc.CordaRPCClient
import net.corda.core.CordaRuntimeException
import net.corda.core.contracts.SignatureAttachmentConstraint
import net.corda.core.contracts.StateAndRef
import net.corda.core.contracts.WhitelistedByZoneAttachmentConstraint
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.getOrThrow
import net.corda.node.flows.isQuasarAgentSpecified
import net.corda.testing.common.internal.testNetworkParameters
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.NodeParameters
import net.corda.testing.node.internal.internalDriver
import org.assertj.core.api.Assertions
import org.junit.Assume
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

open class SignatureConstraintMigrationFromWhitelistConstraintTests  : SignatureConstraintVersioningTests() {


    @Test
    fun `can evolve from lower contract class version to higher one`() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.

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

    @Test
    fun `auto migration from WhitelistConstraint to SignatureConstraint`() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = mapOf(
                        TEST_MESSAGE_CONTRACT_PROGRAM_ID to listOf(
                                oldUnsignedCordapp,
                                newCordapp
                        )
                ),
                systemProperties = emptyMap(),
                startNodesInProcess = false
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is WhitelistedByZoneAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is SignatureAttachmentConstraint)
    }

    @Test
    fun `WhitelistConstraint cannot be migrated to SignatureConstraint if platform version is not 4 or greater`() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = mapOf(
                        TEST_MESSAGE_CONTRACT_PROGRAM_ID to listOf(
                                oldUnsignedCordapp,
                                newCordapp
                        )
                ),
                systemProperties = emptyMap(),
                startNodesInProcess = false,
                minimumPlatformVersion = 3
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is WhitelistedByZoneAttachmentConstraint)
        assertEquals(1, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs.single().constraint is WhitelistedByZoneAttachmentConstraint)
    }

    @Test
    fun `WhitelistConstraint cannot be migrated to SignatureConstraint if signed JAR is not whitelisted`() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        Assertions.assertThatExceptionOfType(CordaRuntimeException::class.java).isThrownBy {
            upgradeCorDappBetweenTransactions(
                    cordapp = oldUnsignedCordapp,
                    newCordapp = newCordapp,
                    whiteListedCordapps = mapOf(TEST_MESSAGE_CONTRACT_PROGRAM_ID to emptyList()),
                    systemProperties = emptyMap(),
                    startNodesInProcess = true
            )
        }
                .withMessageContaining("Selected output constraint: $WhitelistedByZoneAttachmentConstraint not satisfying")
    }

    @Test
    fun `auto migration from WhitelistConstraint to SignatureConstraint will only transition states that do not have a constraint specified`() {
        Assume.assumeFalse(System.getProperty("os.name").toLowerCase().startsWith("win")) // See NodeStatePersistenceTests.kt.
        val (issuanceTransaction, consumingTransaction) = upgradeCorDappBetweenTransactions(
                cordapp = oldUnsignedCordapp,
                newCordapp = newCordapp,
                whiteListedCordapps = mapOf(
                        TEST_MESSAGE_CONTRACT_PROGRAM_ID to listOf(
                                oldUnsignedCordapp,
                                newCordapp
                        )
                ),
                systemProperties = emptyMap(),
                startNodesInProcess = true,
                specifyExistingConstraint = true,
                addAnotherAutomaticConstraintState = true
        )
        assertEquals(1, issuanceTransaction.outputs.size)
        assertTrue(issuanceTransaction.outputs.single().constraint is WhitelistedByZoneAttachmentConstraint)
        assertEquals(2, consumingTransaction.outputs.size)
        assertTrue(consumingTransaction.outputs[0].constraint is WhitelistedByZoneAttachmentConstraint)
        assertTrue(consumingTransaction.outputs[1].constraint is SignatureAttachmentConstraint)
        assertEquals(
                issuanceTransaction.outputs.single().constraint,
                consumingTransaction.outputs.first().constraint,
                "The constraint from the issuance transaction should be the same constraint used in the consuming transaction for the first state"
        )
    }

}