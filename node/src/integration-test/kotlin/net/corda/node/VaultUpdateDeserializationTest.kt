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
import org.junit.Ignore
import org.junit.Test
import java.util.concurrent.TimeoutException
import net.corda.contracts.incompatible.version1.AttachmentContract as AttachmentContractV1
import net.corda.contracts.incompatible.version2.AttachmentContract as AttachmentContractV2
import net.corda.flows.incompatible.version1.AttachmentFlow as AttachmentFlowV1
import net.corda.flows.incompatible.version2.AttachmentFlow as AttachmentFlowV2
import net.corda.flows.incompatible.version3.AttachmentFlow as AttachmentFlowV3

class VaultUpdateDeserializationTest {
    companion object {
        // uses ReceiveFinalityFlow
        val flowVersion1 = cordappWithPackages("net.corda.flows.incompatible.version1")
        // uses ReceiveTransactionFlow with signature checking disabled
        val flowVersion2 = cordappWithPackages("net.corda.flows.incompatible.version2")
        // uses ReceiveTransactionFlow with signature checking enabled
        val flowVersion3 = cordappWithPackages("net.corda.flows.incompatible.version3")
        // single state field of type SecureHash.SHA256
        val contractVersion1 = cordappWithPackages("net.corda.contracts.incompatible.version1")
        // single state field of type OpaqueBytes
        val contractVersion2 = cordappWithPackages("net.corda.contracts.incompatible.version2")

        fun driverParameters(cordapps: List<TestCordapp>,
                             ignoreTransactionDeserializationErrors: Boolean = false,
                             disableSignatureVerification: Boolean = false): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                inMemoryDB = false,
                startNodesInProcess = false,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME)),
                cordappsForAllNodes = cordapps,
                systemProperties = mapOf(
                        "net.corda.vaultupdate.ignore.transaction.deserialization.errors" to ignoreTransactionDeserializationErrors.toString(),
                        "net.corda.recordtransaction.signature.verification.disabled" to disableSignatureVerification.toString())
            )
        }
    }

    /*
     * Test that a deserialization error is raised where the receiver node of a finality flow has an incompatible contract jar.
     * The ledger will be temporarily inconsistent until the correct contract jar version is installed and the receiver node is re-started.
     */
    @Test(timeout=300_000)
	fun `receiver flow is hospitalized upon deserialization failure when using incompatible contract jar`() {
        driver(driverParameters(emptyList())) {
            val alice = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion2)),
                    providedName = BOB_NAME).getOrThrow()

            val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
            alice.rpc.uploadAttachment(inputStream)

            // ISSUE: exception is not propagating from Receiver
            try {
                alice.rpc.startFlow(::AttachmentFlowV1, bob.nodeInfo.singleIdentity(), defaultNotaryIdentity, hash).returnValue.getOrThrow(30.seconds)
            }
            catch(e: TimeoutException) {
                println("Alice: Timeout awaiting flow completion.")
            }
            assertEquals(0, bob.rpc.vaultQueryBy<AttachmentContractV2.State>().states.size)
            // check transaction records
            assertTrue(alice.rpc.internalVerifiedTransactionsSnapshot().isNotEmpty())
            assertTrue(bob.rpc.internalVerifiedTransactionsSnapshot().isEmpty())

            // restart Bob with correct contract jar version
            (bob as OutOfProcess).process.destroyForcibly()
            bob.stop()
            (baseDirectory(BOB_NAME) / "cordapps").deleteRecursively()

            val restartedBob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = BOB_NAME).getOrThrow()
            // original hospitalized transaction should now have been re-processed with correct contract jar
            assertEquals(1, waitForVaultUpdate(restartedBob))
            assertTrue(restartedBob.rpc.internalVerifiedTransactionsSnapshot().isNotEmpty())
        }
    }

    /*
     * Test original deserialization failure behaviour by setting a new configurable java system property.
     * The ledger will enter an inconsistent state from which is cannot auto-recover.
     */
    @Ignore("This test will only succeed if transaction verification is removed from ReceiveFinalityFlow. Otherwise it will throw an UntrustedAttachmentsException error.")
    @Test(timeout = 300_000)
    fun `receiver flow ignores deserialization failure when using incompatible contract jar and overriden system property`() {
        driver(driverParameters(emptyList(),
                ignoreTransactionDeserializationErrors = true,
                disableSignatureVerification = true)) {
            val alice = startNode(NodeParameters(additionalCordapps = listOf(flowVersion2, contractVersion2)),
                    providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion2, contractVersion1)),
                    providedName = BOB_NAME).getOrThrow()

            val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
            alice.rpc.uploadAttachment(inputStream)

            // Note: TransactionDeserialisationException is swallowed on the receiver node (without updating the vault).
            val stx = alice.rpc.startFlow(::AttachmentFlowV2, bob.nodeInfo.singleIdentity(), defaultNotaryIdentity, hash).returnValue.getOrThrow(30.seconds)
            println("Alice txId: ${stx.id}")

            waitForAllFlowsToComplete(bob)
            val txId = bob.rpc.stateMachineRecordedTransactionMappingSnapshot().single().transactionId
            println("Bob txId: $txId")

            assertEquals(0, bob.rpc.vaultQueryBy<AttachmentContractV1.State>().states.size)

            // restart Bob with correct contract jar version
            (bob as OutOfProcess).process.destroyForcibly()
            bob.stop()
            (baseDirectory(BOB_NAME) / "cordapps").deleteRecursively()

            val restartedBob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion2, contractVersion2)),
                    providedName = BOB_NAME).getOrThrow()
            // transaction recorded
            assertNotNull(restartedBob.rpc.internalFindVerifiedTransaction(txId))
            // but vault states not updated
            assertEquals(0, restartedBob.rpc.vaultQueryBy<AttachmentContractV2.State>().states.size)
        }
    }

    @Test(timeout = 300_000)
    fun `receiver flow propagates error upon deserialization failure using incompatible contract jar`() {
        driver(driverParameters(emptyList(),
                ignoreTransactionDeserializationErrors = true,
                disableSignatureVerification = true)) {
            val alice = startNode(NodeParameters(additionalCordapps = listOf(flowVersion3, contractVersion1)),
                    providedName = ALICE_NAME).getOrThrow()
            val bob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion3, contractVersion2)),
                    providedName = BOB_NAME).getOrThrow()

            val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
            alice.rpc.uploadAttachment(inputStream)

            try {
                alice.rpc.startFlow(::AttachmentFlowV3, bob.nodeInfo.singleIdentity(), defaultNotaryIdentity, hash).returnValue.getOrThrow(30.seconds)
            }
            catch (e: UnexpectedFlowEndException) {
                println("Alice: Caught flow propagation error from peer.")
            }
            // check transaction records
            assertTrue(alice.rpc.internalVerifiedTransactionsSnapshot().isNotEmpty())
            assertTrue(bob.rpc.internalVerifiedTransactionsSnapshot().isEmpty())

            // restart Bob with correct contract jar version
            (bob as OutOfProcess).process.destroyForcibly()
            bob.stop()
            (baseDirectory(BOB_NAME) / "cordapps").deleteRecursively()

            val restartedBob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion3, contractVersion1)),
                    providedName = BOB_NAME).getOrThrow()
            // NOTE: flow is not re-tried as it was never sent to flow hospital.
            assertEquals(0, restartedBob.rpc.vaultQueryBy<AttachmentContractV1.State>().states.size)
            // no transaction recorded
            assertTrue(restartedBob.rpc.internalVerifiedTransactionsSnapshot().isEmpty())
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

