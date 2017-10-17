package net.corda.node.services

import net.corda.core.contracts.Amount
import net.corda.core.identity.Party
import net.corda.core.internal.bufferUntilSubscribed
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.utilities.OpaqueBytes
import net.corda.core.utilities.getOrThrow
import net.corda.finance.POUNDS
import net.corda.finance.flows.CashIssueFlow
import net.corda.finance.flows.CashPaymentFlow
import net.corda.node.services.FlowPermissions.Companion.startFlowPermission
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.nodeapi.User
import net.corda.testing.*
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.DriverBasedTest
import org.junit.Test
import rx.Observable
import java.util.*
import kotlin.test.assertEquals

class DistributedServiceTests : DriverBasedTest() {
    lateinit var alice: NodeHandle
    lateinit var notaries: List<NodeHandle.OutOfProcess>
    lateinit var aliceProxy: CordaRPCOps
    lateinit var raftNotaryIdentity: Party
    lateinit var notaryStateMachines: Observable<Pair<Party, StateMachineUpdate>>

    override fun setup() = driver(extraCordappPackagesToScan = listOf("net.corda.finance.contracts")) {
        // Start Alice and 3 notaries in a RAFT cluster
        val clusterSize = 3
        val testUser = User("test", "test", permissions = setOf(
                startFlowPermission<CashIssueFlow>(),
                startFlowPermission<CashPaymentFlow>())
        )
        val aliceFuture = startNode(providedName = ALICE.name, rpcUsers = listOf(testUser))
        val notariesFuture = startNotaryCluster(
                DUMMY_NOTARY.name.copy(commonName = RaftValidatingNotaryService.id),
                rpcUsers = listOf(testUser),
                clusterSize = clusterSize
        )

        alice = aliceFuture.get()
        val (notaryIdentity, notaryNodes) = notariesFuture.get()
        raftNotaryIdentity = notaryIdentity
        notaries = notaryNodes.map { it as NodeHandle.OutOfProcess }

        assertEquals(notaries.size, clusterSize)
        // Check that each notary has different identity as a node.
        assertEquals(notaries.size, notaries.map { it.nodeInfo.chooseIdentity() }.toSet().size)
        // Connect to Alice and the notaries
        fun connectRpc(node: NodeHandle): CordaRPCOps {
            val client = node.rpcClientToNode()
            return client.start("test", "test").proxy
        }
        aliceProxy = connectRpc(alice)
        val rpcClientsToNotaries = notaries.map(::connectRpc)
        notaryStateMachines = Observable.from(rpcClientsToNotaries.map { proxy ->
            proxy.stateMachinesFeed().updates.map { Pair(proxy.nodeInfo().chooseIdentity(), it) }
        }).flatMap { it.onErrorResumeNext(Observable.empty()) }.bufferUntilSubscribed()

        runTest()
    }

    // TODO Use a dummy distributed service rather than a Raft Notary Service as this test is only about Artemis' ability
    // to handle distributed services
    @Test
    fun `requests are distributed evenly amongst the nodes`() {
        // Issue 100 pounds, then pay ourselves 50x2 pounds
        issueCash(100.POUNDS)

        for (i in 1..50) {
            paySelf(2.POUNDS)
        }

        // The state machines added in the notaries should map one-to-one to notarisation requests
        val notarisationsPerNotary = HashMap<Party, Int>()
        notaryStateMachines.expectEvents(isStrict = false) {
            replicate<Pair<Party, StateMachineUpdate>>(50) {
                expect(match = { it.second is StateMachineUpdate.Added }) { (notary, update) ->
                    update as StateMachineUpdate.Added
                    notarisationsPerNotary.compute(notary) { _, number -> number?.plus(1) ?: 1 }
                }
            }
        }

        // The distribution of requests should be very close to sg like 16/17/17 as by default artemis does round robin
        println("Notarisation distribution: $notarisationsPerNotary")
        require(notarisationsPerNotary.size == 3)
        // We allow some leeway for artemis as it doesn't always produce perfect distribution
        require(notarisationsPerNotary.values.all { it > 10 })
    }

    // TODO This should be in RaftNotaryServiceTests
    @Test
    fun `cluster survives if a notary is killed`() {
        // Issue 100 pounds, then pay ourselves 10x5 pounds
        issueCash(100.POUNDS)

        for (i in 1..10) {
            paySelf(5.POUNDS)
        }

        // Now kill a notary
        with(notaries[0].process) {
            destroy()
            waitFor()
        }

        // Pay ourselves another 20x5 pounds
        for (i in 1..20) {
            paySelf(5.POUNDS)
        }

        val notarisationsPerNotary = HashMap<Party, Int>()
        notaryStateMachines.expectEvents(isStrict = false) {
            replicate<Pair<Party, StateMachineUpdate>>(30) {
                expect(match = { it.second is StateMachineUpdate.Added }) { (notary, update) ->
                    update as StateMachineUpdate.Added
                    notarisationsPerNotary.compute(notary) { _, number -> number?.plus(1) ?: 1 }
                }
            }
        }

        println("Notarisation distribution: $notarisationsPerNotary")
        require(notarisationsPerNotary.size == 3)
    }

    private fun issueCash(amount: Amount<Currency>) {
        aliceProxy.startFlow(::CashIssueFlow, amount, OpaqueBytes.of(0), raftNotaryIdentity).returnValue.getOrThrow()
    }

    private fun paySelf(amount: Amount<Currency>) {
        aliceProxy.startFlow(::CashPaymentFlow, amount, alice.nodeInfo.chooseIdentity()).returnValue.getOrThrow()
    }
}
