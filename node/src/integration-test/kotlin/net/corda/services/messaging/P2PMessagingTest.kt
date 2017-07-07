package net.corda.services.messaging

import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import net.corda.core.*
import net.corda.core.crypto.random63BitValue
import net.corda.core.messaging.MessageRecipients
import net.corda.core.messaging.SingleMessageRecipient
import net.corda.core.node.services.DEFAULT_SESSION_ID
import net.corda.core.node.services.ServiceInfo
import net.corda.core.serialization.CordaSerializable
import net.corda.core.serialization.deserialize
import net.corda.core.serialization.serialize
import net.corda.node.internal.Node
import net.corda.node.services.messaging.*
import net.corda.node.services.transactions.RaftValidatingNotaryService
import net.corda.node.services.transactions.SimpleNotaryService
import net.corda.node.utilities.ServiceIdentityGenerator
import net.corda.testing.*
import net.corda.testing.node.NodeBasedTest
import org.assertj.core.api.Assertions.assertThat
import org.bouncycastle.asn1.x500.X500Name
import org.junit.Test
import java.util.*
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class P2PMessagingTest : NodeBasedTest() {
    private companion object {
        val DISTRIBUTED_SERVICE_NAME = getTestX509Name("DistributedService")
        val SERVICE_2_NAME = getTestX509Name("Service Node 2")
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
            alice.network.getAddressOfParty(getPartyInfo(getAnyNotary()!!)!!)
        }
        val received = alice.receiveFrom(serviceAddress).getOrThrow(10.seconds)
        assertThat(received).isEqualTo("Hello")
    }

    // TODO Use a dummy distributed service
    @Test
    fun `communicating with a distributed service which the network map node is part of`() {
        ServiceIdentityGenerator.generateToDisk(
                listOf(DUMMY_MAP.name, SERVICE_2_NAME).map { baseDirectory(it) },
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

    @Test
    fun `distributed service requests are retried if one of the nodes in the cluster goes down without sending a response`() {
        val distributedServiceNodes = startNotaryCluster(DISTRIBUTED_SERVICE_NAME, 2).getOrThrow()
        val alice = startNode(ALICE.name, configOverrides = mapOf("messageRedeliveryDelaySeconds" to 1)).getOrThrow()
        val serviceAddress = alice.services.networkMapCache.run {
            alice.network.getAddressOfParty(getPartyInfo(getAnyNotary()!!)!!)
        }

        val dummyTopic = "dummy.topic"
        val responseMessage = "response"

        simulateCrashingNode(distributedServiceNodes, dummyTopic, responseMessage)

        // Send a single request with retry
        val response = with(alice.network) {
            val request = TestRequest(replyTo = myAddress)
            val responseFuture = onNext<Any>(dummyTopic, request.sessionID)
            val msg = createMessage(TopicSession(dummyTopic), data = request.serialize().bytes)
            send(msg, serviceAddress, retryId = request.sessionID)
            responseFuture
        }.getOrThrow(10.seconds)

        assertThat(response).isEqualTo(responseMessage)
    }

    @Test
    fun `distributed service request retries are persisted across client node restarts`() {
        val distributedServiceNodes = startNotaryCluster(DISTRIBUTED_SERVICE_NAME, 2).getOrThrow()
        val alice = startNode(ALICE.name, configOverrides = mapOf("messageRedeliveryDelaySeconds" to 1)).getOrThrow()
        val serviceAddress = alice.services.networkMapCache.run {
            alice.network.getAddressOfParty(getPartyInfo(getAnyNotary()!!)!!)
        }

        val dummyTopic = "dummy.topic"
        val responseMessage = "response"

        val (firstRequestReceived, requestsReceived) = simulateCrashingNode(distributedServiceNodes, dummyTopic, responseMessage)

        val sessionId = random63BitValue()

        // Send a single request with retry
        with(alice.network) {
            val request = TestRequest(sessionId, myAddress)
            val msg = createMessage(TopicSession(dummyTopic), data = request.serialize().bytes)
            send(msg, serviceAddress, retryId = request.sessionID)
        }

        // Wait until the first request is received
        firstRequestReceived.await(5, TimeUnit.SECONDS)
        // Stop alice's node before the request is redelivered – the first request is ignored
        alice.stop()
        assertThat(requestsReceived.get()).isEqualTo(1)

        // Restart the node and expect a response
        val aliceRestarted = startNode(ALICE.name, configOverrides = mapOf("messageRedeliveryDelaySeconds" to 1)).getOrThrow()
        val response = aliceRestarted.network.onNext<Any>(dummyTopic, sessionId).getOrThrow(5.seconds)

        assertThat(requestsReceived.get()).isGreaterThanOrEqualTo(2)
        assertThat(response).isEqualTo(responseMessage)
    }

    /**
     * Sets up the [distributedServiceNodes] to respond to [dummyTopic] requests. The first node in the service to
     * receive a request will ignore it and all subsequent requests. This simulates the scenario where a node receives
     * a request message, but crashes before sending back a response. The other nodes will respond to _all_ requests.
     */
    private fun simulateCrashingNode(distributedServiceNodes: List<Node>, dummyTopic: String, responseMessage: String): Pair<CountDownLatch, AtomicInteger> {
        val firstToReceive = AtomicBoolean(true)
        val requestsReceived = AtomicInteger(0)
        val firstRequestReceived = CountDownLatch(1)
        distributedServiceNodes.forEach {
            val nodeName = it.info.legalIdentity.name
            var ignoreRequests = false
            it.network.addMessageHandler(dummyTopic, DEFAULT_SESSION_ID) { netMessage, _ ->
                requestsReceived.incrementAndGet()
                firstRequestReceived.countDown()
                // The node which receives the first request will ignore all requests
                if (firstToReceive.getAndSet(false)) ignoreRequests = true
                print("$nodeName: Received request - ")
                if (ignoreRequests) {
                    println("ignoring")
                    // Requests are ignored to simulate a service node crashing before sending back a response.
                    // A retry by the client will result in the message being redelivered to another node in the service cluster.
                } else {
                    println("sending response")
                    val request = netMessage.data.deserialize<TestRequest>()
                    val response = it.network.createMessage(dummyTopic, request.sessionID, responseMessage.serialize().bytes)
                    it.network.send(response, request.replyTo)
                }
            }
        }
        return Pair(firstRequestReceived, requestsReceived)
    }

    private fun assertAllNodesAreUsed(participatingServiceNodes: List<Node>, serviceName: X500Name, originatingNode: Node) {
        // Setup each node in the distributed service to return back it's NodeInfo so that we can know which node is being used
        participatingServiceNodes.forEach { node ->
            node.respondWith(node.info)
        }
        val serviceAddress = originatingNode.services.networkMapCache.run {
            originatingNode.network.getAddressOfParty(getPartyInfo(getNotary(serviceName)!!)!!)
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
        network.addMessageHandler(javaClass.name, DEFAULT_SESSION_ID) { netMessage, _ ->
            val request = netMessage.data.deserialize<TestRequest>()
            val response = network.createMessage(javaClass.name, request.sessionID, message.serialize().bytes)
            network.send(response, request.replyTo)
        }
    }

    private fun Node.receiveFrom(target: MessageRecipients): ListenableFuture<Any> {
        val request = TestRequest(replyTo = network.myAddress)
        return network.sendRequest<Any>(javaClass.name, request, target)
    }

    @CordaSerializable
    private data class TestRequest(override val sessionID: Long = random63BitValue(),
                                   override val replyTo: SingleMessageRecipient) : ServiceRequestMessage
}