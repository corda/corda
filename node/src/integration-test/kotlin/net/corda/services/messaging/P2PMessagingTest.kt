package net.corda.services.messaging

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.*
import net.corda.core.crypto.X509Utilities
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.messaging.createMessage
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.node.services.ServiceInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.core.utilities.*
import net.corda.flows.ServiceRequestMessage
import net.corda.flows.sendRequest
import net.corda.node.internal.Node
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.testing.freeLocalHostAndPort
import net.corda.testing.node.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.util.*

class P2PMessagingTest : NodeBasedTest() {
    private companion object {
        val DISTRIBUTED_SERVICE_NAME = X509Utilities.getDevX509Name("DistributedService")
        val SERVICE_2_NAME = X509Utilities.getDevX509Name("Service Node 2")
    }

    @Test
    fun `network map will work after restart`() {
        val identities = listOf(DUMMY_BANK_A, DUMMY_BANK_B, DUMMY_NOTARY)
        fun startNodes() = Futures.allAsList(identities.map { startNode(it.name) })

        val startUpDuration = elapsedTime { startNodes().getOrThrow() }
        // Start the network map a second time - this will restore message queues from the journal.
        // This will hang and fail prior the fix. https://github.com/corda/corda/issues/37
        stopAllNodes()
        startNodes().getOrThrow(timeout = startUpDuration.multipliedBy(3))
    }

    // https://github.com/corda/corda/issues/71
    @Test
    fun `communicating with a service running on the network map node`() {
        startNetworkMapNode(advertisedServices = setOf(ServiceInfo(SimpleNotaryService.type)))
        networkMapNode.respondWith("Hello")
        val alice = startNode(ALICE.name).getOrThrow()
        val serviceAddress = alice.services.networkMapCache.run {
            alice.net.getAddressOfParty(getPartyInfo(getAnyNotary()!!)!!)
        }
        val received = alice.receiveFrom(serviceAddress).getOrThrow(10.seconds)
        assertThat(received).isEqualTo("Hello")
    }

    // TODO Use a dummy distributed service
    @Test
    fun `communicating with a distributed service which the network map node is part of`() {

        val root = tempFolder.root.toPath()
        ServiceIdentityGenerator.generateToDisk(
                listOf(root / DUMMY_MAP.name.toString(), root / SERVICE_2_NAME.toString()),
                RaftValidatingNotaryService.type.id,
                DISTRIBUTED_SERVICE_NAME)

        val distributedService = ServiceInfo(RaftValidatingNotaryService.type, DISTRIBUTED_SERVICE_NAME)
        val notaryClusterAddress = freeLocalHostAndPort()
        startNetworkMapNode(
                DUMMY_MAP.name,
                advertisedServices = setOf(distributedService),
                configOverrides = mapOf("notaryNodeAddress" to notaryClusterAddress.toString()))
        val (serviceNode2, alice) = Futures.allAsList(
                startNode(
                        SERVICE_2_NAME,
                        advertisedServices = setOf(distributedService),
                        configOverrides = mapOf(
                                "notaryNodeAddress" to freeLocalHostAndPort().toString(),
                                "notaryClusterAddresses" to listOf(notaryClusterAddress.toString()))),
                startNode(ALICE.name)
        ).getOrThrow()

        assertAllNodesAreUsed(listOf(networkMapNode, serviceNode2), DISTRIBUTED_SERVICE_NAME, alice)
    }

    @Test
    fun `communicating with a distributed service which we're part of`() {
        val distributedService = startNotaryCluster(DISTRIBUTED_SERVICE_NAME, 2).getOrThrow()
        assertAllNodesAreUsed(distributedService, DISTRIBUTED_SERVICE_NAME, distributedService[0])
    }

    private fun assertAllNodesAreUsed(participatingServiceNodes: List<Node>, serviceName: X500Name, originatingNode: Node) {
        // Setup each node in the distributed service to return back it's NodeInfo so that we can know which node is being used
        participatingServiceNodes.forEach { node ->
            node.respondWith(node.info)
        }
        val serviceAddress = originatingNode.services.networkMapCache.run {
            originatingNode.net.getAddressOfParty(getPartyInfo(getNotary(serviceName)!!)!!)
        }
        val participatingNodes = HashSet<Any>()
        // Try several times so that we can be fairly sure that any node not participating is not due to Artemis' selection
        // strategy. 3 attempts for each node seems to be sufficient.
        // This is not testing the distribution of the requests - DistributedServiceTests already does that
        for (it in 1..participatingServiceNodes.size * 3) {
            participatingNodes += originatingNode.receiveFrom(serviceAddress).getOrThrow(10.seconds)
            if (participatingNodes.size == participatingServiceNodes.size) {
                break
            }
        }
        assertThat(participatingNodes).containsOnlyElementsOf(participatingServiceNodes.map(Node::info))
    }

    private fun Node.respondWith(message: Any) {
        net.addMessageHandler(javaClass.name, DEFAULT_SESSION_ID) { netMessage, _ ->
            val request = netMessage.data.deserialize<TestRequest>()
            val response = net.createMessage(javaClass.name, request.sessionID, message.serialize().bytes)
            net.send(response, request.replyTo)
        }
    }

    private fun Node.receiveFrom(target: MessageRecipients): ListenableFuture<Any> {
        val request = TestRequest(replyTo = net.myAddress)
        return net.sendRequest<Any>(javaClass.name, request, target)
    }

    @CordaSerializable
    private data class TestRequest(override val sessionID: Long = random63BitValue(),
                                   override val replyTo: SingleMessageRecipient) : ServiceRequestMessage
}