package net.corda.node

import co.paralleluniverse.strands.Strand
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertNotNull
import junit.framework.TestCase.assertTrue
import net.corda.core.flows.UnexpectedFlowEndException
import net.corda.core.internal.InputStreamAndHash
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.flows.incompatible.version1.AttachmentIssueFlow
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.flows.waitForAllFlowsToComplete
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.internal.cordappWithPackages
import org.junit.Test
import java.util.concurrent.TimeoutException
import net.corda.contracts.incompatible.version1.AttachmentContract as AttachmentContractV1
import net.corda.flows.incompatible.version1.AttachmentFlow as AttachmentFlowV1

class VaultUpdateDeserializationTest {
    companion object {
        // uses ReceiveFinalityFlow
        val flowVersion1 = cordappWithPackages("net.corda.flows.incompatible.version1")
        // single state field of type SecureHash.SHA256 with system property driven run-time behaviour:
        // -force contract verify failure: -Dnet.corda.contracts.incompatible.AttachmentContract.fail.verify=true
        // -force contract state init failure: -Dnet.corda.contracts.incompatible.AttachmentContract.fail.state=true
        val contractVersion1 = cordappWithPackages("net.corda.contracts.incompatible.version1")

        fun driverParameters(cordapps: List<TestCordapp>): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                inMemoryDB = false,
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME)),
                cordappsForAllNodes = cordapps
            )
        }
    }

    /*
 * Transaction sent from A -> B with Notarisation
 * Test that a deserialization error is raised where the receiver node of a transaction has an incompatible contract jar.
 * In the case of a notarised transaction, a deserialisation error is thrown in the receiver SignTransactionFlow (before finality)
 * upon receiving the transaction to be signed and attempting to record its dependencies.
 * The ledger will not record any transactions, and the flow must be retried by the sender upon installing the correct contract jar
 * version at the receiver and re-starting the node.
 */
    @Test(timeout=300_000)
    fun `Notarised transaction fails completely upon receiver deserialization failure collecting signatures when using incompatible contract jar`() {
        driver(driverParameters(listOf(flowVersion1, contractVersion1))) {
            val alice = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1),
                    systemProperties = mapOf("net.corda.contracts.incompatible.AttachmentContract.fail.state" to "true")),
                    providedName = BOB_NAME).getOrThrow()

            val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
            alice.rpc.uploadAttachment(inputStream)

            val stx = alice.rpc.startFlow(::AttachmentIssueFlow, hash, defaultNotaryIdentity).returnValue.getOrThrow(30.seconds)
            val spendableState = stx.coreTransaction.outRef<AttachmentContractV1.State>(0)

            // NOTE: exception is propagated from Receiver
            try {
                alice.rpc.startFlow(::AttachmentFlowV1, bob.nodeInfo.singleIdentity(), defaultNotaryIdentity, hash, spendableState).returnValue.getOrThrow(30.seconds)
            }
            catch(e: UnexpectedFlowEndException) {
                println("Bob fails to deserialise transaction upon receipt of transaction for signing.")
            }
            assertEquals(0, bob.rpc.vaultQueryBy<AttachmentContractV1.State>().states.size)
            assertEquals(1, alice.rpc.vaultQueryBy<AttachmentContractV1.State>().states.size)
            // check transaction records
            @Suppress("DEPRECATION")
            assertEquals(1, alice.rpc.internalVerifiedTransactionsSnapshot().size)  // issuance only
            @Suppress("DEPRECATION")
            assertTrue(bob.rpc.internalVerifiedTransactionsSnapshot().isEmpty())

            // restart Bob with correct contract jar version
            (bob as OutOfProcess).process.destroyForcibly()
            bob.stop()
            (baseDirectory(BOB_NAME) / "cordapps").deleteRecursively()

            val restartedBob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = BOB_NAME).getOrThrow()
            // re-run failed flow
            alice.rpc.startFlow(::AttachmentFlowV1, restartedBob.nodeInfo.singleIdentity(), defaultNotaryIdentity, hash, spendableState).returnValue.getOrThrow(30.seconds)

            assertEquals(1, waitForVaultUpdate(restartedBob))
            assertEquals(1, alice.rpc.vaultQueryBy<AttachmentContractV1.State>().states.size)
            @Suppress("DEPRECATION")
            assertTrue(restartedBob.rpc.internalVerifiedTransactionsSnapshot().isNotEmpty())
        }
    }

    /*
     * Transaction sent from A -> B without Notarisation
     * Test that a deserialization error is raised where the receiver node of a finality flow has an incompatible contract jar.
     * The ledger will be temporarily inconsistent until the correct contract jar version is installed and the receiver node is re-started.
     */
    @Test(timeout=300_000)
	fun `un-notarised transaction is hospitalized at receiver upon deserialization failure in vault update when using incompatible contract jar`() {
        driver(driverParameters(emptyList())) {
            val alice = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1),
                    systemProperties = mapOf("net.corda.contracts.incompatible.AttachmentContract.fail.state" to "true")),
                    providedName = BOB_NAME).getOrThrow()

            val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
            alice.rpc.uploadAttachment(inputStream)

            // ISSUE: exception is not propagating from Receiver
            try {
                alice.rpc.startFlow(::AttachmentFlowV1, bob.nodeInfo.singleIdentity(), defaultNotaryIdentity, hash, null).returnValue.getOrThrow(30.seconds)
            }
            catch(e: TimeoutException) {
                println("Alice: Timeout awaiting flow completion.")
            }
            assertEquals(0, bob.rpc.vaultQueryBy<AttachmentContractV1.State>().states.size)
            // check transaction records
            @Suppress("DEPRECATION")
            assertTrue(alice.rpc.internalVerifiedTransactionsSnapshot().isNotEmpty())
            @Suppress("DEPRECATION")
            assertTrue(bob.rpc.internalVerifiedTransactionsSnapshot().isEmpty())

            // restart Bob with correct contract jar version
            (bob as OutOfProcess).process.destroyForcibly()
            bob.stop()
            (baseDirectory(BOB_NAME) / "cordapps").deleteRecursively()

            val restartedBob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = BOB_NAME).getOrThrow()
            // original hospitalized transaction should now have been re-processed with correct contract jar
            assertEquals(1, waitForVaultUpdate(restartedBob))
            @Suppress("DEPRECATION")
            assertTrue(restartedBob.rpc.internalVerifiedTransactionsSnapshot().isNotEmpty())
        }
    }

    /*
     * Transaction sent from A -> B without Notarisation
     * Test original deserialization failure behaviour by setting a new configurable java system property.
     * The ledger will enter an inconsistent state from which is cannot auto-recover.
     */
    @Test(timeout = 300_000)
    fun `un-notarised transaction ignores deserialization failure in vault update when using incompatible contract jar and overriden system property`() {
        driver(driverParameters(emptyList())) {
            val alice = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1),
                    systemProperties = mapOf(
                            "net.corda.contracts.incompatible.AttachmentContract.fail.state" to "true",
                            "net.corda.vaultupdate.ignore.transaction.deserialization.errors" to "true",
                            "net.corda.recordtransaction.signature.verification.disabled" to "true")),
                    providedName = BOB_NAME).getOrThrow()

            val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
            alice.rpc.uploadAttachment(inputStream)

            // Note: TransactionDeserialisationException is swallowed on the receiver node (without updating the vault).
            val stx = alice.rpc.startFlow(::AttachmentFlowV1, bob.nodeInfo.singleIdentity(), defaultNotaryIdentity, hash, null).returnValue.getOrThrow(30.seconds)
            println("Alice txId: ${stx.id}")

            waitForAllFlowsToComplete(bob)
            val txId = bob.rpc.stateMachineRecordedTransactionMappingSnapshot().single().transactionId
            println("Bob txId: $txId")

            assertEquals(0, bob.rpc.vaultQueryBy<AttachmentContractV1.State>().states.size)

            // restart Bob with correct contract jar version
            (bob as OutOfProcess).process.destroyForcibly()
            bob.stop()
            (baseDirectory(BOB_NAME) / "cordapps").deleteRecursively()

            val restartedBob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = BOB_NAME).getOrThrow()
            // transaction recorded
            @Suppress("DEPRECATION")
            assertNotNull(restartedBob.rpc.internalFindVerifiedTransaction(txId))
            // but vault states not updated
            assertEquals(0, restartedBob.rpc.vaultQueryBy<AttachmentContractV1.State>().states.size)
        }
    }

    private fun waitForVaultUpdate(nodeHandle: NodeHandle, maxIterations: Int = 5, iterationDelay: Long = 500): Int {
        repeat((0..maxIterations).count()) {
            val count = nodeHandle.rpc.vaultQueryBy<AttachmentContractV1.State>().states
            if (count.isNotEmpty()) {
                return count.size
            }
            Strand.sleep(iterationDelay)
        }
        return 0
    }
}

