package net.corda.node.services

import net.corda.core.bufferUntilSubscribed
import net.corda.core.contracts.POUNDS
import net.corda.core.contracts.issuedBy
import net.corda.core.crypto.Party
import net.corda.core.messaging.CordaRPCOps
import net.corda.core.messaging.StateMachineUpdate
import net.corda.core.messaging.startFlow
import net.corda.core.node.NodeInfo
import net.corda.core.serialization.OpaqueBytes
import net.corda.flows.CashCommand
import net.corda.flows.CashFlow
import net.corda.flows.CashFlowResult
import net.corda.node.driver.driver
import net.corda.node.services.config.configureTestSSL
import net.corda.node.services.messaging.ArtemisMessagingComponent
import net.corda.node.services.messaging.CordaRPCClient
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.testing.expect
import net.corda.testing.expectEvents
import net.corda.testing.replicate
import org.junit.Test
import rx.Observable
import java.net.Inet6Address
import java.net.InetAddress
import java.util.*
import kotlin.test.assertEquals

class RaftValidatingNotaryServiceTests {
    @Test
    fun `notarisation requests are distributed evenly in raft cluster`() {
        driver {
            // Start Alice and 3 raft notaries
            val clusterSize = 3
            val testUser = User("test", "test", permissions = setOf(startFlowPermission<CashFlow>()))
            val aliceFuture = startNode("Alice", rpcUsers = listOf(testUser))
            val notariesFuture = startNotaryCluster(
                    "Notary",
                    rpcUsers = listOf(testUser),
                    clusterSize = clusterSize,
                    type = RaftValidatingNotaryService.type
            )

            val alice = aliceFuture.get().nodeInfo
            val (raftNotaryIdentity, notaries) = notariesFuture.get()

            assertEquals(notaries.size, clusterSize)
            assertEquals(notaries.size, notaries.map { it.nodeInfo.legalIdentity }.toSet().size)

            // Connect to Alice and the notaries
            fun connectRpc(node: NodeInfo): CordaRPCOps {
                val client = CordaRPCClient(ArtemisMessagingComponent.toHostAndPort(node.address), configureTestSSL())
                client.start("test", "test")
                return client.proxy()
            }
            val aliceProxy = connectRpc(alice)
            val notaryProxies = notaries.map { connectRpc(it.nodeInfo) }
            val notaryStateMachines = Observable.from(notaryProxies.map { proxy ->
                proxy.stateMachinesAndUpdates().second.map { Pair(proxy.nodeIdentity(), it) }
            }).flatMap { it }

            // Issue 100 pounds, then pay ourselves 50x2 pounds
            val issueHandle = aliceProxy.startFlow(::CashFlow, CashCommand.IssueCash(100.POUNDS, OpaqueBytes.of(0), alice.legalIdentity, raftNotaryIdentity))
            require(issueHandle.returnValue.toBlocking().first() is CashFlowResult.Success)
            for (i in 1 .. 50) {
                val payHandle = aliceProxy.startFlow(::CashFlow, CashCommand.PayCash(2.POUNDS.issuedBy(alice.legalIdentity.ref(0)), alice.legalIdentity))
                require(payHandle.returnValue.toBlocking().first() is CashFlowResult.Success)
            }

            // The state machines added in the notaries should map one-to-one to notarisation requests
            val notarisationsPerNotary = HashMap<Party, Int>()
            notaryStateMachines.expectEvents(isStrict = false) {
                replicate<Pair<NodeInfo, StateMachineUpdate>>(50) {
                    expect(match = { it.second is StateMachineUpdate.Added }) {
                        val (notary, update) = it
                        update as StateMachineUpdate.Added
                        notarisationsPerNotary.compute(notary.legalIdentity) { _key, number -> number?.plus(1) ?: 1 }
                    }
                }
            }

            // The distribution of requests should be very close to sg like 16/17/17 as by default artemis does round robin
            println("Notarisation distribution: $notarisationsPerNotary")
            require(notarisationsPerNotary.size == 3)
            // We allow some leeway for artemis as it doesn't always produce perfect distribution
            require(notarisationsPerNotary.values.all { it > 10 })
        }
    }

    @Test
    fun `cluster survives if a notary is killed`() {
        driver {
            // Start Alice and 3 raft notaries
            val clusterSize = 3
            val testUser = User("test", "test", permissions = setOf(startFlowPermission<CashFlow>()))
            val aliceFuture = startNode("Alice", rpcUsers = listOf(testUser))
            val notariesFuture = startNotaryCluster(
                    "Notary",
                    rpcUsers = listOf(testUser),
                    clusterSize = clusterSize,
                    type = RaftValidatingNotaryService.type
            )

            val alice = aliceFuture.get().nodeInfo
            val (raftNotaryIdentity, notaries) = notariesFuture.get()

            assertEquals(notaries.size, clusterSize)
            assertEquals(notaries.size, notaries.map { it.nodeInfo.legalIdentity }.toSet().size)

            // Connect to Alice and the notaries
            fun connectRpc(node: NodeInfo): CordaRPCOps {
                val client = CordaRPCClient(ArtemisMessagingComponent.toHostAndPort(node.address), configureTestSSL())
                client.start("test", "test")
                return client.proxy()
            }
            val aliceProxy = connectRpc(alice)
            val notaryProxies = notaries.map { connectRpc(it.nodeInfo) }
            val notaryStateMachines = Observable.from(notaryProxies.map { proxy ->
                proxy.stateMachinesAndUpdates().second.map { Pair(proxy.nodeIdentity(), it) }
            }).flatMap { it.onErrorResumeNext(Observable.empty()) }.bufferUntilSubscribed()

            // Issue 100 pounds, then pay ourselves 10x5 pounds
            val issueHandle = aliceProxy.startFlow(::CashFlow, CashCommand.IssueCash(100.POUNDS, OpaqueBytes.of(0), alice.legalIdentity, raftNotaryIdentity))
            require(issueHandle.returnValue.toBlocking().first() is CashFlowResult.Success)
            for (i in 1 .. 10) {
                val payHandle = aliceProxy.startFlow(::CashFlow, CashCommand.PayCash(5.POUNDS.issuedBy(alice.legalIdentity.ref(0)), alice.legalIdentity))
                require(payHandle.returnValue.toBlocking().first() is CashFlowResult.Success)
            }

            // Now kill a notary
            notaries[0].process.apply {
                destroy()
                waitFor()
            }

            // Pay ourselves another 10x5 pounds
            for (i in 1 .. 10) {
                val payHandle = aliceProxy.startFlow(::CashFlow, CashCommand.PayCash(5.POUNDS.issuedBy(alice.legalIdentity.ref(0)), alice.legalIdentity))
                require(payHandle.returnValue.toBlocking().first() is CashFlowResult.Success)
            }

            // Artemis still dispatches some requests to the dead notary but all others should go through.
            val notarisationsPerNotary = HashMap<Party, Int>()
            notaryStateMachines.expectEvents(isStrict = false) {
                replicate<Pair<NodeInfo, StateMachineUpdate>>(15) {
                    expect(match = { it.second is StateMachineUpdate.Added }) {
                        val (notary, update) = it
                        update as StateMachineUpdate.Added
                        notarisationsPerNotary.compute(notary.legalIdentity) { _key, number -> number?.plus(1) ?: 1 }
                    }
                }
            }

            println("Notarisation distribution: $notarisationsPerNotary")
            require(notarisationsPerNotary.size == 3)
        }
    }
}