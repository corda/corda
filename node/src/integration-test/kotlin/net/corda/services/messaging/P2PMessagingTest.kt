package net.corda.services.messaging

import co.paralleluniverse.fibers.Suspendable
import com.google.common.util.concurrent.Futures
import net.corda.core.crypto.Party
import net.corda.core.div
import net.corda.core.flows.FlowLogic
import net.corda.core.future
import net.corda.core.getOrThrow
import net.corda.core.node.services.ServiceInfo
import net.corda.core.seconds
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.testing.freeLocalHostAndPort
import net.corda.testing.node.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.junit.Test
import java.util.*

class P2PMessagingTest : NodeBasedTest() {
    @Test
    fun `network map will work after restart`() {
        fun startNodes() {
            Futures.allAsList(startNode("NodeA"), startNode("NodeB"), startNode("Notary")).getOrThrow()
        }

        startNodes()
        // Start the network map second time, this will restore message queues from the journal.
        // This will hang and fail prior the fix. https://github.com/corda/corda/issues/37
        stopAllNodes()
        future {
            startNodes()
        }.getOrThrow(30.seconds)
    }

    // https://github.com/corda/corda/issues/71
    @Test
    fun `sending message to a service running on the network map node`() {
        startNetworkMapNode(advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
        networkMapNode.services.registerFlowInitiator(ReceiveFlow::class) { SendFlow(it, "Hello") }
        val serviceParty = networkMapNode.services.networkMapCache.getAnyNotary()!!
        val alice = startNode("Alice").getOrThrow()
        val received = alice.services.startFlow(ReceiveFlow(serviceParty)).resultFuture.getOrThrow(10.seconds)
        assertThat(received).isEqualTo("Hello")
    }

    @Test
    fun `sending message to a distributed service which the network map node is part of`() {
        val serviceName = "DistributedService"

        val root = tempFolder.root.toPath()
        ServiceIdentityGenerator.generateToDisk(
                listOf(
                        root / "NetworkMap",
                        root / "Alice"),
                RaftValidatingNotaryService.type.id,
                serviceName)

        val distributedService = ServiceInfo(RaftValidatingNotaryService.type, serviceName)
        val notaryClusterAddress = freeLocalHostAndPort()
        startNetworkMapNode(
                "NetworkMap",
                advertisedServices = setOf(distributedService),
                configOverrides = mapOf("notaryNodeAddress" to notaryClusterAddress.toString()))
        val (alice, bob) = Futures.allAsList(
            startNode(
                "Alice",
                advertisedServices = setOf(distributedService),
                configOverrides = mapOf(
                        "notaryNodeAddress" to freeLocalHostAndPort().toString(),
                        "notaryClusterAddresses" to listOf(notaryClusterAddress.toString()))),
            startNode("Bob")
        ).getOrThrow()

        // Setup each node in the distributed service to return back it's Party so that we can know which node is being used
        val serviceNodes = listOf(networkMapNode, alice)
        serviceNodes.forEach { node ->
            node.services.registerFlowInitiator(ReceiveFlow::class) { SendFlow(it, node.info.legalIdentity) }
        }

        val serviceParty = networkMapNode.services.networkMapCache.getNotary(serviceName)!!
        val participatingParties = HashSet<Any>()
        // Try up to 4 times so that we can be fairly sure that any node not participating is not due to Artemis' selection strategy
        for (it in 1..5) {
            participatingParties += bob.services.startFlow(ReceiveFlow(serviceParty)).resultFuture.getOrThrow(10.seconds)
            if (participatingParties.size == 2) {
                break
            }
        }
        assertThat(participatingParties).containsOnlyElementsOf(serviceNodes.map { it.info.legalIdentity })
    }

    private class SendFlow(val otherParty: Party, val payload: Any) : FlowLogic<Unit>() {
        @Suspendable
        override fun call() = send(otherParty, payload)
    }

    private class ReceiveFlow(val otherParty: Party) : FlowLogic<Any>() {
        @Suspendable
        override fun call() = receive<Any>(otherParty).unwrap { it }
    }
}