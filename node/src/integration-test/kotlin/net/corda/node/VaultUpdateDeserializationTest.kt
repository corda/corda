package net.corda.node

import net.corda.contracts.incompatible.version1.AttachmentContract
import net.corda.contracts.incompatible.version2.AttachmentContract as AttachmentContractV2
import net.corda.core.internal.InputStreamAndHash
import net.corda.core.internal.deleteRecursively
import net.corda.core.internal.div
import net.corda.core.messaging.startFlow
import net.corda.core.messaging.vaultQueryBy
import net.corda.core.utilities.getOrThrow
import net.corda.core.utilities.seconds
import net.corda.flows.incompatible.version1.AttachmentFlow
import net.corda.node.services.Permissions
import net.corda.testing.core.ALICE_NAME
import net.corda.testing.core.BOB_NAME
import net.corda.testing.core.DUMMY_NOTARY_NAME
import net.corda.testing.core.singleIdentity
import net.corda.testing.driver.DriverParameters
import net.corda.testing.driver.NodeParameters
import net.corda.testing.driver.OutOfProcess
import net.corda.testing.driver.driver
import net.corda.testing.driver.internal.incrementalPortAllocation
import net.corda.testing.node.NotarySpec
import net.corda.testing.node.TestCordapp
import net.corda.testing.node.User
import net.corda.testing.node.internal.cordappWithPackages
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.concurrent.TimeoutException

@Suppress("FunctionName")
class VaultUpdateDeserializationTest {
    companion object {
        val user = User("u", "p", setOf(Permissions.all()))
        val flowVersion1 = cordappWithPackages("net.corda.flows.incompatible.version1")
        val flowVersion2 = cordappWithPackages("net.corda.flows.incompatible.version2")
        val contractVersion1 = cordappWithPackages("net.corda.contracts.incompatible.version1")
        val contractVersion2 = cordappWithPackages("net.corda.contracts.incompatible.version2")

        fun driverParameters(cordapps: List<TestCordapp>, runInProcess: Boolean = false,
                             ignoreTransactionDeserializationErrors: Boolean = false): DriverParameters {
            return DriverParameters(
                portAllocation = incrementalPortAllocation(),
                startNodesInProcess = runInProcess,
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY_NAME)),
                cordappsForAllNodes = cordapps,
                systemProperties = mapOf("net.corda.vaultupdate.ignore.transaction.deserialization.errors" to ignoreTransactionDeserializationErrors.toString())
            )
        }
    }

    /*
     * Test that a deserialization error is raised where the receiver node of a finality flow has an incompatible contract jar.
     * The ledger will be temporarily inconsistent until the correct contract jar version is installed and the receiver node is re-started.
     */
    @Test(timeout=300_000)
	fun `receiver flow throws deserialization error when using incompatible contract jar`() {
        driver(driverParameters(emptyList(), false)) {
            val alice = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = ALICE_NAME, rpcUsers = listOf(user)).getOrThrow()
            val bob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion2)),
                    providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()

            val (inputStream, hash) = InputStreamAndHash.createInMemoryTestZip(1024, 0)
            alice.rpc.uploadAttachment(inputStream)

            // ISSUE: exception is not propagating from Receiver
//            val ex = assertFailsWith<TransactionDeserialisationException> {
//            val ex = assertFailsWith<FlowException> {
            println("Alice: Start AttachmentFlow")
            try {
                alice.rpc.startFlow(::AttachmentFlow, bob.nodeInfo.singleIdentity(), defaultNotaryIdentity, hash).returnValue.getOrThrow(30.seconds)
            }
            catch(e: TimeoutException) {
                println("Alice: Timeout awaiting flow completion.")
            }
//            }
//            println(ex.message)
//            assertThat(ex).hasMessageContaining("Invalid data: $data")

            println("Bob: Check vault")
            assertThat(bob.rpc.vaultQueryBy<AttachmentContractV2.State>().states).hasSize(0)

            // restart Bob with correct contract jar version
            println("Bob: stop and remove cordapps")
            (bob as OutOfProcess).process.destroyForcibly()
            bob.stop()
            (baseDirectory(BOB_NAME) / "cordapps").deleteRecursively()

            println("Bob: re-start")
            val restartedBob = startNode(NodeParameters(additionalCordapps = listOf(flowVersion1, contractVersion1)),
                    providedName = BOB_NAME, rpcUsers = listOf(user)).getOrThrow()
            // ISSUE: receiver is not re-processing hospitalized flow
            println("Bob: Re-check vault")
            assertThat(restartedBob.rpc.vaultQueryBy<AttachmentContract.State>().states).hasSize(1)
        }
    }
}