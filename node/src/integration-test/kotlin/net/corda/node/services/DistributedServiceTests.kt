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
import net.corda.node.services.Permissions.Companion.invokeRpc
import net.corda.node.services.Permissions.Companion.startFlow
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.nodeapi.internal.config.User
import net.corda.testing.*
import net.corda.testing.driver.NodeHandle
import net.corda.testing.driver.driver
import net.corda.testing.node.ClusterSpec
import net.corda.testing.node.NotarySpec
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import rx.Observable
import java.util.*

class DistributedServiceTests {
    private lateinit var alice: NodeHandle
    private lateinit var notaryNodes: List<NodeHandle.OutOfProcess>
    private lateinit var aliceProxy: CordaRPCOps
    private lateinit var raftNotaryIdentity: Party
    private lateinit var notaryStateMachines: Observable<Pair<Party, StateMachineUpdate>>

    private fun setup(testBlock: () -> Unit) {
        val testUser = User("test", "test", permissions = setOf(
                startFlow<CashIssueFlow>(),
                startFlow<CashPaymentFlow>(),
                invokeRpc(CordaRPCOps::nodeInfo),
                invokeRpc(CordaRPCOps::stateMachinesFeed))
        )

        driver(
                extraCordappPackagesToScan = listOf("net.corda.finance.contracts"),
                notarySpecs = listOf(NotarySpec(DUMMY_NOTARY.name.copy(commonName = RaftValidatingNotaryService.id), rpcUsers = listOf(testUser), cluster = ClusterSpec.Raft(clusterSize = 3))))
        {
            alice = startNode(providedName = ALICE.name, rpcUsers = listOf(testUser)).getOrThrow()
            raftNotaryIdentity = defaultNotaryIdentity
            notaryNodes = defaultNotaryHandle.nodeHandles.getOrThrow().map { it as NodeHandle.OutOfProcess }

            assertThat(notaryNodes).hasSize(3)

            for (notaryNode in notaryNodes) {
                assertThat(notaryNode.nodeInfo.legalIdentities).contains(raftNotaryIdentity)
            }

            // Check that each notary has different identity as a node.
            assertThat(notaryNodes.flatMap { it.nodeInfo.legalIdentities - raftNotaryIdentity }.toSet()).hasSameSizeAs(notaryNodes)

            // Connect to Alice and the notaries
            fun connectRpc(node: NodeHandle): CordaRPCOps {
                val client = node.rpcClientToNode()
                return client.start("test", "test").proxy
            }
            aliceProxy = connectRpc(alice)
            val rpcClientsToNotaries = notaryNodes.map(::connectRpc)
            notaryStateMachines = Observable.from(rpcClientsToNotaries.map { proxy ->
                proxy.stateMachinesFeed().updates.map { Pair(proxy.nodeInfo().chooseIdentity(), it) }
            }).flatMap { it.onErrorResumeNext(Observable.empty()) }.bufferUntilSubscribed()

            testBlock()
        }
    }

    // TODO Use a dummy distributed service rather than a Raft Notary Service as this test is only about Artemis' ability
    // to handle distributed services
    @Test
    fun `requests are distributed evenly amongst the nodes`() = setup {
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
    fun `cluster survives if a notary is killed`() = setup {
        // Issue 100 pounds, then pay ourselves 10x5 pounds
        issueCash(100.POUNDS)

        for (i in 1..10) {
            paySelf(5.POUNDS)
        }

        // Now kill a notary node
        with(notaryNodes[0].process) {
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